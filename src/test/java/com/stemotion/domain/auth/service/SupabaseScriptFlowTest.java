package com.stemotion.domain.auth.service;

import com.stemotion.core.enums.StemEnums.*;
import com.stemotion.domain.auth.dto.AuthDtos.RegisterRequest;
import com.stemotion.domain.auth.entity.User.UserRole;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.workspace.dto.WorkspaceDtos.WorkspaceRequest;
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
class SupabaseScriptFlowTest {
    @Autowired AuthService auth;
    @Autowired WorkspaceService workspaces;
    @Autowired ScriptService scripts;
    @Autowired FeedbackService feedback;
    @Autowired SceneRepository scenes;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    private String email;

    @Test void scriptSceneAndBothFeedbackTargetsRoundTrip() {
        email = "schema-smoke-" + UUID.randomUUID() + "@example.invalid";
        var user = auth.register(new RegisterRequest("Schema smoke", email,
            UUID.randomUUID().toString(), UserRole.WRITER)).user();
        var workspace = workspaces.create(new WorkspaceRequest("Schema smoke", "CS"), user.id());
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
    }

    @AfterTransaction void fixtureWasRolledBack() {
        assertFalse(users.existsByEmail(email));
    }
}
