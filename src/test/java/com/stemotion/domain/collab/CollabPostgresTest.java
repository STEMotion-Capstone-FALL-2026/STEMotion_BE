package com.stemotion.domain.collab;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.exception.ApiException;
import com.stemotion.core.security.WorkspaceAccess;
import com.stemotion.domain.ai.repository.AiRequestRepository;
import com.stemotion.domain.auth.entity.SystemRole;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.auth.service.AuthService;
import com.stemotion.domain.collab.dto.CollabDtos.AiSettingsRequest;
import com.stemotion.domain.collab.dto.CollabDtos.ProposeRequest;
import com.stemotion.domain.collab.entity.CollabEnums.ContributionType;
import com.stemotion.domain.collab.entity.CollabEnums.LeaseResource;
import com.stemotion.domain.collab.repository.AiProviderSettingsRepository;
import com.stemotion.domain.collab.repository.ContributionEventRepository;
import com.stemotion.domain.collab.repository.EditLeaseRepository;
import com.stemotion.domain.collab.repository.ProjectJoinRequestRepository;
import com.stemotion.domain.collab.repository.ProjectMemberRepository;
import com.stemotion.domain.collab.repository.ProjectProposalRepository;
import com.stemotion.domain.collab.service.AiSettingsService;
import com.stemotion.domain.collab.service.ContributionService;
import com.stemotion.domain.collab.service.EditLeaseService;
import com.stemotion.domain.collab.service.LeaderDashboardService;
import com.stemotion.domain.collab.service.ProjectCollabService;
import com.stemotion.domain.collab.service.ProjectTeamRules;
import com.stemotion.domain.production.repository.RenderJobRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.project.service.StatusHistoryService;
import com.stemotion.domain.review.repository.FeedbackCommentRepository;
import com.stemotion.domain.script.mapper.SceneMapper;
import com.stemotion.domain.script.mapper.ScenePropsValidator;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.script.service.ScriptService;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import com.stemotion.domain.workspace.service.WorkspaceService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UC-29 to UC-34 on a real Postgres built by the Flyway migrations up to V28: proposals, project
 * roles with the workspace-role fallback, join requests and revocation, role coverage, edit leases,
 * contributions, the Leader dashboard, AI budgets and the D-ADM rule (decisions of 22/11/2026).
 */
@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration,classpath:db/auth-v2-e2e",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CollabPostgresTest {

    private static EmbeddedPostgres postgres;

    private static synchronized EmbeddedPostgres postgres() throws Exception {
        if (postgres == null) postgres = EmbeddedPostgres.builder().start();
        return postgres;
    }

    @AfterAll
    static void stop() throws Exception {
        if (postgres != null) postgres.close();
        postgres = null;
    }

    @TestConfiguration
    static class Pg {
        @Bean
        DataSource dataSource() throws Exception {
            return postgres().getDatabase("postgres", "postgres", Map.of("stringtype", "unspecified"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository seats;
    @Autowired ClipProjectRepository projects;
    @Autowired ScriptVersionRepository versions;
    @Autowired SceneRepository scenes;
    @Autowired RenderJobRepository jobs;
    @Autowired FeedbackCommentRepository comments;
    @Autowired ProjectProposalRepository proposalRepo;
    @Autowired ProjectMemberRepository memberRepo;
    @Autowired ProjectJoinRequestRepository joinRepo;
    @Autowired EditLeaseRepository leaseRepo;
    @Autowired ContributionEventRepository eventRepo;
    @Autowired AiProviderSettingsRepository settingsRepo;
    @Autowired AiRequestRepository aiRequests;

    WorkspaceAccess access;
    ProjectCollabService collab;
    EditLeaseService leases;
    ContributionService contributions;
    AiSettingsService ai;
    LeaderDashboardService dashboard;
    ProjectTeamRules team;

    User admin, leaderWriter, writer2, reviewer, producer, outsider;
    Workspace ws;

    @BeforeEach
    void wire() {
        access = new WorkspaceAccess(users, seats, projects, jobs, comments, memberRepo);
        AuthService auth = mock(AuthService.class);
        when(auth.findUserOrThrow(anyString())).thenAnswer(inv -> users.findById(inv.getArgument(0)).orElseThrow());
        WorkspaceService workspaceService = new WorkspaceService(workspaces, seats, projects, users);
        ScriptService scripts = new ScriptService(projects, versions, scenes,
            new SceneMapper(new com.fasterxml.jackson.databind.ObjectMapper(), new ScenePropsValidator()),
            auth, workspaceService, mock(StatusHistoryService.class), access);
        team = transactional(new ProjectTeamRules(access, users));
        collab = transactional(new ProjectCollabService(proposalRepo, memberRepo, joinRepo, projects, access, transactional(scripts), users, team));
        leases = transactional(new EditLeaseService(leaseRepo, access, users));
        contributions = transactional(new ContributionService(eventRepo, access, users));
        ai = transactional(new AiSettingsService(settingsRepo, aiRequests, access));
        dashboard = transactional(new LeaderDashboardService(access, workspaces, projects, proposalRepo, joinRepo, comments, jobs, users, team));

        admin = user("admin", SystemRole.ADMIN);
        leaderWriter = user("leader-writer", SystemRole.USER);
        writer2 = user("writer2", SystemRole.USER);
        reviewer = user("reviewer", SystemRole.USER);
        producer = user("producer", SystemRole.USER);
        outsider = user("outsider", SystemRole.USER);
        ws = workspaces.save(Workspace.builder().name("Collab " + UUID.randomUUID()).createdBy(admin).build());
        seat(leaderWriter, WorkspaceRole.WRITER, true);
        seat(writer2, WorkspaceRole.WRITER, false);
        seat(reviewer, WorkspaceRole.REVIEWER, false);
        seat(producer, WorkspaceRole.PRODUCER, false);
        seat(outsider, WorkspaceRole.WRITER, false);
    }

    @Test
    @DisplayName("UC-29: a Writer proposes, a Leader approves exactly once and the project starts with the proposer as Writer")
    void proposalApprovedOnce() {
        var proposal = collab.propose(ws.getId(), new ProposeRequest("Đòn bẩy", StemSubject.Physics, "GRADE_8"), writer2.getId());
        assertThat(proposal.status()).isEqualTo("PENDING");

        assertCode(() -> collab.approve(proposal.id(), writer2.getId()), 403, null);
        var approved = collab.approve(proposal.id(), leaderWriter.getId());
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.projectId()).isNotNull();
        ClipProject project = projects.findById(approved.projectId()).orElseThrow();
        assertThat(project.getOwner().getId()).isEqualTo(writer2.getId());
        assertThat(access.projectRole(project.getId(), writer2.getId())).contains(WorkspaceRole.WRITER);

        assertCode(() -> collab.approve(proposal.id(), admin.getId()), 409, "PROPOSAL_ALREADY_DECIDED");
        assertCode(() -> collab.reject(proposal.id(), "late", leaderWriter.getId()), 409, "PROPOSAL_ALREADY_DECIDED");
    }

    @Test
    @DisplayName("D12: a Writer who is a Leader approves their own proposal; a rejection needs a reason")
    void selfApprovalAndRejectionReason() {
        var own = collab.propose(ws.getId(), new ProposeRequest("Tự duyệt", StemSubject.Math, null), leaderWriter.getId());
        assertThat(collab.approve(own.id(), leaderWriter.getId()).status()).isEqualTo("APPROVED");
        var other = collab.propose(ws.getId(), new ProposeRequest("Bị từ chối", StemSubject.Math, null), writer2.getId());
        assertCode(() -> collab.reject(other.id(), " ", leaderWriter.getId()), 400, null);
        assertThat(collab.reject(other.id(), "Trùng chủ đề", leaderWriter.getId()).decisionReason()).isEqualTo("Trùng chủ đề");
    }

    @Test
    @DisplayName("D08/D09, D13, D15: project roles, read access for members only, join, revoke and join again")
    void projectRolesJoinAndRevoke() {
        String projectId = approvedProject(writer2);

        // A workspace WRITER joins this project as its REVIEWER: the project role wins.
        var request = collab.requestJoin(projectId, WorkspaceRole.REVIEWER, outsider.getId());
        assertCode(() -> collab.requestJoin(projectId, WorkspaceRole.REVIEWER, outsider.getId()), 409, "JOIN_REQUEST_PENDING");
        assertCode(() -> access.requireProject(projectId, outsider.getId(), false), 403, null);
        collab.approveJoin(request.id(), leaderWriter.getId());
        assertThat(access.hasProjectRole(projectId, outsider.getId(), "REVIEWER")).isTrue();
        assertThat(access.hasProjectRole(projectId, outsider.getId(), "WRITER")).isFalse();
        access.requireProject(projectId, outsider.getId(), false);

        // Non-members (even with the right workspace role) cannot open it; the Leader can.
        assertThat(access.hasProjectRole(projectId, reviewer.getId(), "REVIEWER")).isFalse();
        assertCode(() -> access.requireProject(projectId, reviewer.getId(), false), 403, null);
        access.requireProject(projectId, leaderWriter.getId(), false);

        // Revoked members keep the history (still listed) and may ask again.
        var teamAfter = collab.revoke(projectId, outsider.getId(), leaderWriter.getId());
        assertThat(teamAfter.members()).anyMatch(m -> m.userId().equals(outsider.getId()) && m.status().equals("REVOKED"));
        assertThat(access.hasProjectRole(projectId, outsider.getId(), "REVIEWER")).isFalse();
        var again = collab.requestJoin(projectId, WorkspaceRole.PRODUCER, outsider.getId());
        assertThat(again.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Projects created before V28 keep workspace roles and refuse join requests")
    void legacyProjectKeepsWorkspaceRoles() {
        String legacy = legacyProject();
        assertThat(access.usesProjectRoles(legacy)).isFalse();
        assertThat(access.hasProjectRole(legacy, reviewer.getId(), "REVIEWER")).isTrue();
        access.requireProject(legacy, outsider.getId(), false);
        assertCode(() -> collab.requestJoin(legacy, WorkspaceRole.REVIEWER, outsider.getId()), 409, "PROJECT_USES_WORKSPACE_ROLES");
        assertThat(team.missingRoles(legacy)).isEmpty();
    }

    @Test
    @DisplayName("D14, A-acct: Reviewer and Producer members are needed before submit; a locked account does not count")
    void roleCoverage() {
        String projectId = approvedProject(writer2);
        assertThat(team.missingRoles(projectId)).containsExactly("REVIEWER", "PRODUCER");
        assertCode(() -> team.requireCoverageBeforeSubmit(projectId), 409, "PROJECT_ROLES_MISSING");
        collab.approveJoin(collab.requestJoin(projectId, WorkspaceRole.REVIEWER, reviewer.getId()).id(), leaderWriter.getId());
        collab.approveJoin(collab.requestJoin(projectId, WorkspaceRole.PRODUCER, producer.getId()).id(), leaderWriter.getId());
        assertThat(team.missingRoles(projectId)).isEmpty();
        team.requireCoverageBeforeSubmit(projectId);

        reviewer.setStatus(User.UserStatus.INACTIVE);
        users.save(reviewer);
        assertThat(team.missingRoles(projectId)).containsExactly("REVIEWER");
    }

    @Test
    @DisplayName("D16/D17: one lease per draft, held by one editor, required on project-role projects, force-released by a Leader")
    void editLeases() {
        String projectId = approvedProject(writer2);
        collab.approveJoin(collab.requestJoin(projectId, WorkspaceRole.WRITER, outsider.getId()).id(), leaderWriter.getId());

        assertCode(() -> leases.requireWritable(LeaseResource.SCRIPT_DRAFT, projectId, writer2.getId()), 409, "EDIT_LEASE_REQUIRED");
        var held = leases.acquire(LeaseResource.SCRIPT_DRAFT, projectId, writer2.getId());
        assertThat(held.mine()).isTrue();
        assertThat(held.expiresAt()).isAfter(LocalDateTime.now().plusSeconds(80));
        leases.requireWritable(LeaseResource.SCRIPT_DRAFT, projectId, writer2.getId());
        assertCode(() -> leases.acquire(LeaseResource.SCRIPT_DRAFT, projectId, outsider.getId()), 409, "EDIT_LEASE_HELD");
        assertCode(() -> leases.requireWritable(LeaseResource.SCRIPT_DRAFT, projectId, outsider.getId()), 409, "EDIT_LEASE_HELD");
        assertCode(() -> leases.acquire(LeaseResource.COMPOSITION, projectId, writer2.getId()), 403, null);

        assertCode(() -> leases.release(LeaseResource.SCRIPT_DRAFT, projectId, outsider.getId(), true), 403, null);
        assertThat(leases.release(LeaseResource.SCRIPT_DRAFT, projectId, leaderWriter.getId(), true).held()).isFalse();
        assertThat(leases.acquire(LeaseResource.SCRIPT_DRAFT, projectId, outsider.getId()).mine()).isTrue();

        // An expired lease (no renewal for 90 s) frees the draft.
        var row = leaseRepo.findByResourceTypeAndResourceId(LeaseResource.SCRIPT_DRAFT, projectId).orElseThrow();
        row.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        leaseRepo.save(row);
        assertThat(leases.acquire(LeaseResource.SCRIPT_DRAFT, projectId, writer2.getId()).mine()).isTrue();

        // A project from before V28 only refuses a write while someone else holds the lease.
        String legacy = legacyProject();
        leases.requireWritable(LeaseResource.SCRIPT_DRAFT, legacy, writer2.getId());
    }

    @Test
    @DisplayName("D18: contributions are recorded with the role held at the time and summarised per member")
    void contributionsPerMember() {
        String projectId = approvedProject(writer2);
        collab.approveJoin(collab.requestJoin(projectId, WorkspaceRole.REVIEWER, reviewer.getId()).id(), leaderWriter.getId());
        contributions.record(projectId, writer2.getId(), ContributionType.SCRIPT_SUBMITTED, null);
        contributions.record(projectId, reviewer.getId(), ContributionType.REVIEW_DECIDED, null);
        contributions.record(projectId, writer2.getId(), ContributionType.SCRIPT_SUBMITTED, null);

        var view = contributions.list(projectId, writer2.getId());
        assertThat(view.events()).hasSize(3);
        assertThat(view.byMember().get(0).actorId()).isEqualTo(writer2.getId());
        assertThat(view.byMember().get(0).byType()).containsEntry("SCRIPT_SUBMITTED", 2L);
        assertThat(view.events()).anyMatch(e -> e.actorId().equals(reviewer.getId()) && e.projectRole().equals("REVIEWER"));
        assertCode(() -> contributions.list(projectId, outsider.getId()), 403, null);
    }

    @Test
    @DisplayName("UC-33: the Leader inbox lists pending proposals and join requests; the board names missing roles")
    void leaderDashboard() {
        String projectId = approvedProject(writer2);
        collab.propose(ws.getId(), new ProposeRequest("Chờ duyệt", StemSubject.Biology, null), writer2.getId());
        collab.requestJoin(projectId, WorkspaceRole.REVIEWER, reviewer.getId());

        var view = dashboard.dashboard(leaderWriter.getId());
        assertThat(view.pendingProposals()).extracting("title").contains("Chờ duyệt");
        assertThat(view.pendingJoinRequests()).anyMatch(r -> r.projectId().equals(projectId));
        assertThat(view.board().values().stream().flatMap(List::stream))
            .anyMatch(c -> c.projectId().equals(projectId) && c.blockers().contains("MISSING_ROLE:REVIEWER"));
        assertCode(() -> dashboard.dashboard(writer2.getId()), 403, null);
    }

    @Test
    @DisplayName("D04/D07: defaults of USD 20 a month and USD 0.50 a clip; over a limit the call is refused before the provider")
    void aiBudgets() {
        var defaults = ai.get(admin.getId());
        assertThat(defaults.monthlyBudgetUsd()).isEqualByComparingTo("20.00");
        assertThat(defaults.perClipCapUsd()).isEqualByComparingTo("0.50");
        assertCode(() -> ai.get(leaderWriter.getId()), 403, null);

        var saved = ai.update(new AiSettingsRequest(defaults.revision(), List.of("openai"), List.of("gpt-4o-mini"),
            List.of("banmai", "leminh"), new BigDecimal("20.00"), new BigDecimal("0.50")), admin.getId());
        assertThat(saved.revision()).isEqualTo(defaults.revision() + 1);
        assertCode(() -> ai.update(new AiSettingsRequest(defaults.revision(), List.of("openai"), List.of(), List.of(),
            BigDecimal.ONE, BigDecimal.ONE), admin.getId()), 409, null);

        ai.requireAllowedVoice("banmai");
        assertCode(() -> ai.requireAllowedVoice("unknown-voice"), 400, "VOICE_NOT_ALLOWED");

        String projectId = approvedProject(writer2);
        ai.checkBudget(projectId);
        aiCall(projectId, "0.60");
        assertCode(() -> ai.checkBudget(projectId), 429, "AI_CLIP_BUDGET_EXCEEDED");
        ai.checkBudget(null);
        aiCall(null, "25.00");
        assertCode(() -> ai.checkBudget(null), 429, "AI_BUDGET_EXCEEDED");
        jdbc.update("delete from ai_requests");
    }

    @Test
    @DisplayName("D-ADM: an Admin approves only as a Reviewer member of the project")
    void adminNeedsReviewerRole() {
        String legacy = legacyProject();
        assertCode(() -> access.requireReviewerNotAdminBypass(legacy, admin.getId()), 403, null);
        access.requireReviewerNotAdminBypass(legacy, reviewer.getId());

        String projectId = approvedProject(writer2);
        seat(admin, WorkspaceRole.WRITER, false);
        collab.approveJoin(collab.requestJoin(projectId, WorkspaceRole.REVIEWER, admin.getId()).id(), leaderWriter.getId());
        access.requireReviewerNotAdminBypass(projectId, admin.getId());
    }

    // ------------------------------------------------------------------ fixtures

    /** The services are built by hand; this applies their @Transactional the way Spring would. */
    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        var factory = new org.springframework.aop.framework.ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    private String approvedProject(User proposer) {
        var p = collab.propose(ws.getId(), new ProposeRequest("Dự án " + UUID.randomUUID(), StemSubject.Physics, null), proposer.getId());
        return collab.approve(p.id(), leaderWriter.getId()).projectId();
    }

    private String legacyProject() {
        return projects.save(ClipProject.builder().title("Cũ " + UUID.randomUUID()).subject(StemSubject.Math)
            .owner(writer2).workspace(ws).build()).getId();
    }

    private void aiCall(String projectId, String usd) {
        jdbc.update("insert into ai_requests (id, request_type, model, request_data, decision, call_status, monetary_cost, cost_currency, project_id, created_at) "
            + "values (?, 'ANALYSIS', 'test', '{}', 'PENDING', 'SUCCEEDED', ?, 'USD', ?, now())",
            UUID.randomUUID().toString(), new BigDecimal(usd), projectId);
    }

    private User user(String name, SystemRole role) {
        return users.save(User.builder().fullName(name).email(name + "-" + UUID.randomUUID() + "@test.invalid")
            .systemRole(role).status(User.UserStatus.ACTIVE).build());
    }

    private WorkspaceMember seat(User user, WorkspaceRole role, boolean leader) {
        return seats.save(WorkspaceMember.builder().workspace(ws).user(user).role(role).isLeader(leader)
            .status(MemberStatus.ACTIVE).build());
    }

    private static void assertCode(Runnable call, int status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus().value()).isEqualTo(status);
            if (code != null) assertThat(ex.getCode()).isEqualTo(code);
        });
    }
}
