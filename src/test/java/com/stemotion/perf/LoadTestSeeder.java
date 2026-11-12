package com.stemotion.perf;

import com.stemotion.core.enums.StemEnums.MemberStatus;
import com.stemotion.core.enums.StemEnums.SceneType;
import com.stemotion.core.enums.StemEnums.ScriptStatus;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.enums.StemEnums.VideoStatus;
import com.stemotion.domain.auth.entity.SystemRole;
import com.stemotion.domain.auth.entity.User;
import com.stemotion.domain.auth.repository.UserRepository;
import com.stemotion.domain.project.entity.ClipProject;
import com.stemotion.domain.project.repository.ClipProjectRepository;
import com.stemotion.domain.script.entity.Scene;
import com.stemotion.domain.script.entity.ScriptVersion;
import com.stemotion.domain.script.repository.SceneRepository;
import com.stemotion.domain.script.repository.ScriptVersionRepository;
import com.stemotion.domain.workspace.entity.Workspace;
import com.stemotion.domain.workspace.entity.WorkspaceMember;
import com.stemotion.domain.workspace.entity.WorkspaceRole;
import com.stemotion.domain.workspace.repository.WorkspaceMemberRepository;
import com.stemotion.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic seeder for RF-02 load testing baseline (NFR-01 to NFR-04).
 * Creates exactly:
 * - 200 users (1 admin + 199 users, password 'LoadTest-fixture-123')
 * - 3 workspaces (Physics, Chemistry, Biology)
 * - 50 projects per workspace (150 projects total)
 * - 1 current script version per project (150 versions)
 * - 10 scenes per version (1500 scenes total)
 * All records carry TEST_ONLY prefixes and reproducible keys.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadTestSeeder {

    public static final String SEED_PASSWORD = "LoadTest-fixture-123";
    public static final int TARGET_USERS = 200;
    public static final int WORKSPACES_COUNT = 3;
    public static final int PROJECTS_PER_WORKSPACE = 50;
    public static final int SCENES_PER_PROJECT = 10;

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ClipProjectRepository projectRepository;
    private final ScriptVersionRepository versionRepository;
    private final SceneRepository sceneRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;

    public void seed() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(txStatus -> {
            log.info("[LoadTestSeeder] Starting data seed for RF-02 baseline...");
            long start = System.currentTimeMillis();

            // 1. Create or load admin
            User admin = userRepository.findByUsername("e2e-admin").orElseGet(() ->
                userRepository.save(User.builder()
                    .username("e2e-admin")
                    .fullName("TEST_ONLY Administrator")
                    .email("e2e-admin@stemotion.test")
                    .passwordHash(passwordEncoder.encode("E2e-fixture-only-123"))
                    .systemRole(SystemRole.ADMIN)
                    .status(User.UserStatus.ACTIVE)
                    .mustChangePassword(false)
                    .build())
            );

            // 2. Create 200 regular users (user001 .. user200)
            String encodedPassword = passwordEncoder.encode(SEED_PASSWORD);
            List<User> regularUsers = new ArrayList<>();
            for (int i = 1; i <= TARGET_USERS; i++) {
                String username = String.format("user%03d", i);
                String email = String.format("user%03d@test.invalid", i);
                final int idx = i;
                User u = userRepository.findByUsername(username).orElseGet(() ->
                    userRepository.save(User.builder()
                        .username(username)
                        .fullName("TEST_ONLY User " + idx)
                        .email(email)
                        .passwordHash(encodedPassword)
                        .systemRole(SystemRole.USER)
                        .status(User.UserStatus.ACTIVE)
                        .mustChangePassword(false)
                        .build())
                );
                regularUsers.add(u);
            }
            log.info("[LoadTestSeeder] Provisioned {} users.", regularUsers.size() + 1);

            // 3. Create 3 Workspaces
            String[] wsNames = {"TEST_ONLY Physics Department", "TEST_ONLY Chemistry Department", "TEST_ONLY Biology Department"};
            StemSubject[] subjects = {StemSubject.Physics, StemSubject.Chemistry, StemSubject.Biology};
            List<Workspace> workspaces = new ArrayList<>();
            for (int w = 0; w < WORKSPACES_COUNT; w++) {
                final int wsIndex = w;
                Workspace ws = workspaceRepository.findAll().stream()
                    .filter(x -> wsNames[wsIndex].equals(x.getName()))
                    .findFirst()
                    .orElseGet(() ->
                        workspaceRepository.save(Workspace.builder()
                            .name(wsNames[wsIndex])
                            .department(subjects[wsIndex].name())
                            .createdBy(admin)
                            .build())
                    );
                workspaces.add(ws);

                // Assign members to workspace
                // Assign first 65 users as writers/reviewers/producers
                for (int uIdx = 0; uIdx < regularUsers.size(); uIdx++) {
                    User u = regularUsers.get(uIdx);
                    WorkspaceRole role = (wsIndex == 0) ? WorkspaceRole.WRITER :
                                         (wsIndex == 1) ? WorkspaceRole.REVIEWER : WorkspaceRole.PRODUCER;
                    boolean isLeader = (uIdx == wsIndex);
                    if (memberRepository.findByWorkspaceIdAndUserId(ws.getId(), u.getId()).isEmpty()) {
                        memberRepository.save(WorkspaceMember.builder()
                            .workspace(ws)
                            .user(u)
                            .role(role)
                            .isLeader(isLeader)
                            .status(MemberStatus.ACTIVE)
                            .build());
                    }
                }
            }
            log.info("[LoadTestSeeder] Provisioned {} workspaces with memberships.", workspaces.size());

            // 4. Create 50 projects per workspace (150 total)
            int totalProjects = 0;
            int totalScenes = 0;
            for (int w = 0; w < WORKSPACES_COUNT; w++) {
                Workspace ws = workspaces.get(w);
                StemSubject subject = subjects[w];

                for (int p = 1; p <= PROJECTS_PER_WORKSPACE; p++) {
                    String title = String.format("TEST_ONLY %s Clip %02d", subject.name(), p);
                    User owner = regularUsers.get((w * 50 + p) % regularUsers.size());
                    ScriptStatus status = (p % 4 == 0) ? ScriptStatus.APPROVED :
                                          (p % 4 == 1) ? ScriptStatus.IN_REVIEW :
                                          (p % 4 == 2) ? ScriptStatus.DRAFT : ScriptStatus.APPROVED;
                    VideoStatus videoStatus = (status == ScriptStatus.APPROVED) ? VideoStatus.PUBLISHED : VideoStatus.NOT_RENDERED;

                    ClipProject project = projectRepository.save(ClipProject.builder()
                        .title(title)
                        .subject(subject)
                        .gradeLevel(String.valueOf(6 + (p % 7)))
                        .fps(30)
                        .totalDurationSeconds(60)
                        .scriptStatus(status)
                        .videoStatus(videoStatus)
                        .archived(false)
                        .owner(owner)
                        .workspace(ws)
                        .build());
                    totalProjects++;

                    // 1 Current ScriptVersion
                    ScriptVersion version = versionRepository.save(ScriptVersion.builder()
                        .project(project)
                        .title(title)
                        .versionNo(1)
                        .status(status)
                        .createdBy(owner)
                        .isCurrent(true)
                        .draftRevision(1)
                        .build());

                    // 10 Scenes per project
                    List<Scene> scenes = new ArrayList<>();
                    for (int s = 1; s <= SCENES_PER_PROJECT; s++) {
                        scenes.add(Scene.builder()
                            .scriptVersion(version)
                            .sceneType(SceneType.TITLE_HERO)
                            .title(String.format("Phân cảnh %d: Giải thích hiện tượng %s", s, subject.name()))
                            .narration(String.format("Chào các em, hôm nay chúng ta sẽ cùng tìm hiểu về bài học %s phần thứ %d.", subject.name(), s))
                            .durationInFrames(180)
                            .orderNo(s)
                            .orderIndex(s)
                            .props("{\"subtitle\":\"Khái niệm cơ bản\",\"subject\":\"" + subject.name() + "\"}")
                            .build());
                        totalScenes++;
                    }
                    sceneRepository.saveAll(scenes);
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("[LoadTestSeeder] Data seeding COMPLETED in {} ms: {} users, {} workspaces, {} projects, {} scenes.",
                elapsed, TARGET_USERS, WORKSPACES_COUNT, totalProjects, totalScenes);
        });
    }
}
