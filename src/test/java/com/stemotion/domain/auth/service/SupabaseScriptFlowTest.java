package com.stemotion.domain.auth.service;

import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import com.stemotion.domain.auth.entity.User.UserRole;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.WorkspaceRequest;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.AddMemberRequest;
import com.stemotion.domain.workspace.service.WorkspaceService;
import com.stemotion.domain.script.dto.SceneDtos.SceneRequest;
import com.stemotion.domain.script.dto.ScriptDtos.CreateScriptRequest;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.review.dto.FeedbackDtos.FeedbackRequest;
import com.stemotion.domain.review.service.FeedbackService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real schema smoke test; all fixture writes are rolled back. */
@SpringBootTest(properties = {
    "app.cors.allowed-origins=http://localhost:3000",
    "app.integration.render.base-url=", "app.integration.render.callback-url=",
    "app.publish.embed-base-url="
})
@ActiveProfiles("cloud")
@EnabledIfEnvironmentVariable(named = "STEMOTION_DB_SMOKE", matches = "true")
@Transactional
@org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
class SupabaseScriptFlowTest {
    @Autowired AuthService auth;
    @Autowired UserService provisioning;
    @Autowired WorkspaceService workspaces;
    @Autowired ScriptService scripts;
    @Autowired FeedbackService feedback;
    @Autowired SceneRepository scenes;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired com.stemotion.domain.production.repository.RenderJobRepository jobs;
    @Autowired com.stemotion.domain.production.repository.ClipRepository clips;
    @Autowired com.stemotion.domain.production.repository.PublicationRepository publications;
    @Autowired com.stemotion.domain.production.service.ClipArtifactService artifacts;
    @Autowired com.stemotion.domain.production.service.PublishService publishing;
    @Autowired com.stemotion.domain.project.service.StatusHistoryService history;
    @Autowired com.stemotion.domain.ai.repository.AiRequestRepository aiRequests;
    private String email;

    /** A reviewer and a producer, so the workspace has the three roles it needs (master update plan M11). */
    private List<AddMemberRequest> crew() {
        return List.of(UserRole.REVIEWER, UserRole.PRODUCER).stream().map(role -> new AddMemberRequest(
            provisioning.create(new RegisterRequest("Crew " + role, "crew-" + UUID.randomUUID() + "@example.invalid",
                UUID.randomUUID().toString(), role)).id(), role, false)).toList();
    }

    @Test void artifactReviewPublicationAndFailedAiRoundTrip() {
        email="phase1-smoke-"+UUID.randomUUID()+"@example.invalid";
        var actor=provisioning.create(new RegisterRequest("Phase1 smoke",email,UUID.randomUUID().toString(),UserRole.ADMIN));
        var workspace=workspaces.create(new WorkspaceRequest("Phase1 smoke","CS", actor.id(), UserRole.WRITER, crew()), actor.id());
        var scene=new SceneRequest();scene.setType(SceneType.TITLE_HERO);scene.setTitle("Phase1 smoke");
        scene.setDurationInFrames(600);
        Map.of("subtitle","Test","subject","ComputerScience","gradeLevel","10","badgeText","CS").forEach(scene::putProp);
        var draft=scripts.create(workspace.id(),new CreateScriptRequest("Phase1 smoke",StemSubject.ComputerScience,"10",30,List.of(scene)),actor.id());
        scripts.submitForReview(draft.id(),actor.id());scripts.approve(draft.id(),null,actor.id());
        var project=scripts.getOrThrow(draft.id());var version=scripts.currentVersion(project);
        var job=jobs.save(com.stemotion.domain.production.entity.RenderJob.builder().project(project)
            .scriptVersion(version).requestedBy(auth.findUserOrThrow(actor.id())).status(RenderStatus.COMPLETED)
            .videoUrl("https://storage.example/phase1-fixture.mp4").progress(100).build());
        var clip=artifacts.recordCompleted(job);
        project.setVideoUrl(clip.getVideoUrl());project.setVideoStatus(VideoStatus.IN_QA);
        em.flush();
        feedback.approveVideo(project.getId(),actor.id(),"Artifact smoke approval");
        var result=publishing.publish(project.getId(),new com.stemotion.domain.production.dto.ProductionDtos.PublishRequest(
            List.of(PublishTarget.INTERNAL_LIBRARY,PublishTarget.LMS),"Phase1 smoke",null),actor.id());
        assertTrue(result.lmsEmbedCode().contains("https://storage.example/phase1-fixture.mp4"));
        var request=aiRequests.save(com.stemotion.domain.ai.entity.AiRequest.builder().requester(auth.findUserOrThrow(actor.id()))
            .requestType(com.stemotion.domain.ai.entity.AiRequest.RequestType.SEGMENTATION).model("fixture-model")
            .callStatus(com.stemotion.domain.ai.entity.AiRequest.CallStatus.FAILED)
            .responseData("{\"error\":\"provider failure fixture\"}").build());
        em.flush();em.clear();
        assertEquals(clip.getId(),publications.findByProjectIdAndPlatform(project.getId(),
            com.stemotion.domain.production.entity.Publication.Platform.INTERNAL_LIBRARY).orElseThrow().getClip().getId());
        assertNull(clips.findById(clip.getId()).orElseThrow().getDurationSec());
        assertNull(aiRequests.findById(request.getId()).orElseThrow().getMonetaryCost());
        var review=history.findByProject(project.getId()).stream().filter(h->h.getTargetId().equals(clip.getId())).findFirst().orElseThrow();
        assertEquals(review.getFromStatus(),review.getToStatus());
        assertEquals(com.stemotion.domain.project.entity.StatusHistory.Decision.APPROVED,review.getDecision());
        assertThrows(com.stemotion.core.exception.ApiException.class,()->publishing.publish(project.getId(),
            new com.stemotion.domain.production.dto.ProductionDtos.PublishRequest(List.of(PublishTarget.INTERNAL_LIBRARY),"Again",null),actor.id()));
        scripts.delete(project.getId(),actor.id());em.flush();
        assertTrue(scripts.getOrThrow(project.getId()).getArchived());
        assertTrue(clips.existsById(clip.getId()));
        assertFalse(scripts.findByWorkspace(workspace.id()).stream().anyMatch(p->p.id().equals(project.getId())));
    }

    @Test void directMemberAssignmentBecomesActiveImmediately() {
        email = "assign-smoke-" + UUID.randomUUID() + "@example.invalid";
        var leader = provisioning.create(new RegisterRequest("Leader user", email,
            UUID.randomUUID().toString(), UserRole.WRITER));
        var workspace = workspaces.create(new WorkspaceRequest("Assign smoke", "CS", leader.id(), UserRole.WRITER, crew()), leader.id());
        String memberEmail = "member-smoke-" + UUID.randomUUID() + "@example.invalid";
        var member = provisioning.create(new RegisterRequest("Member user", memberEmail,
            UUID.randomUUID().toString(), UserRole.PRODUCER));
        em.flush();
        em.clear();
        assertTrue(workspaces.findMine(member.id()).isEmpty());
        var assigned = workspaces.addMember(workspace.id(), new AddMemberRequest(member.id(), UserRole.PRODUCER, false));
        assertEquals(MemberStatus.ACTIVE, assigned.status());
        em.flush();
        em.clear();
        assertEquals(1, workspaces.findMine(member.id()).size());
        assertThrows(com.stemotion.core.exception.ApiException.class,
            () -> workspaces.addMember(workspace.id(), new AddMemberRequest(member.id(), UserRole.PRODUCER, false)));
    }

    @Test void scriptSceneAndBothFeedbackTargetsRoundTrip() {
        email = "schema-smoke-" + UUID.randomUUID() + "@example.invalid";
        var user = provisioning.create(new RegisterRequest("Schema smoke", email,
            UUID.randomUUID().toString(), UserRole.WRITER));
        var workspace = workspaces.create(new WorkspaceRequest("Schema smoke", "CS", user.id(), UserRole.WRITER, crew()), user.id());
        var scene = new SceneRequest();
        scene.setType(SceneType.TITLE_HERO);
        scene.setTitle("Schema smoke");
        scene.setDurationInFrames(90);
        scene.setOrderNo(2);
        Map.of("subtitle", "Test", "subject", "ComputerScience", "gradeLevel", "10",
            "badgeText", "CS").forEach(scene::putProp);
        var script = scripts.create(workspace.id(), new CreateScriptRequest("Schema smoke",
            StemSubject.ComputerScience, "10", 30, List.of(scene)), user.id());
        var sceneId = script.scenes().get(0).getId();
        feedback.create(script.id(), new FeedbackRequest(null, sceneId, "Inline smoke", null), user.id());
        feedback.create(script.id(), new FeedbackRequest(1.5, null, "Video smoke", null), user.id());
        em.flush();
        em.clear();
        assertEquals(StemSubject.ComputerScience, scripts.findById(script.id()).subject());
        var stored = scenes.findById(sceneId).orElseThrow();
        assertEquals(stored.getOrderNo(), stored.getOrderIndex());
        assertEquals(2, feedback.findByProject(script.id(), null).size());
        scene.setId(sceneId);
        scene.setTitle("Edited without changing identity");
        var updated = scripts.updateDraft(script.id(), new com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest(
            null, null, null, null, null, List.of(scene), null));
        em.flush(); em.clear();
        assertEquals(sceneId, updated.scenes().get(0).getId());
        assertEquals(2, feedback.findByProject(script.id(), null).size());
        assertTrue(scenes.existsById(sceneId));
        assertThrows(com.stemotion.core.exception.ApiException.class, () -> scripts.updateDraft(script.id(),
            new com.stemotion.domain.script.dto.ScriptDtos.UpdateScriptRequest(null, null, null, null, null, List.of(), null)));
    }

    @AfterTransaction void fixtureWasRolledBack() {
        assertFalse(users.existsByEmail(email));
    }
}
