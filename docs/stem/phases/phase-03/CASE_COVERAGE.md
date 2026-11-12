# P3 — Mapping52ca review P2, không phải52test

05/11/2026. Trạng thái dưới chỉ cho phần P3. PASS_SUBCASE không kết luận toàn ca P2 PASS, PARTIAL không che phần chưa chạy. Đúng38test P3 trong439reported tại lượt handoff; không cộng lặp.

| Case P2 | Actual phạm vi P3 | Evidence / phần chưa làm |
|---|---|---|
| I01 | DOCUMENT_REVIEW | Registry UC01–42/FR01–55 và candidate giữ nguyên; PRESERVATION_REVIEW |
| R01 | PASS_SUBCASE | catalogResponseIsTypedJsonAndUnverified; outcomeGradeMismatchIsRejectedWithoutPromotingContent; chưa xác minh provenance chính thức |
| C01 | PARTIAL | Draft/null/schema/unknown-fields đã test; positive đủ mọi secondary variant/cardinality và đánh giá phù hợp sư phạm chưa có ca độc lập đầy đủ |
| C02 | PASS_SUBCASE | channelCannotBePrimary |
| C03 | PARTIAL | Draft/null/schema/unknown-fields đã test; positive đủ mọi secondary variant/cardinality và đánh giá phù hợp sư phạm chưa có ca độc lập đầy đủ |
| C04 | PASS_SUBCASE | legacyDepartmentLibrarySuggestsChannelWithoutInferringPrimary |
| C05 | PARTIAL | evidenceNeedsATargetAndMatchingTask kiểm refs; chưa có chuyên môn kết luận task/an toàn mâu thuẫn |
| G01 | PARTIAL | draftNullPrimaryIsAllowedButIncomplete; nullDraftReplayCasAndUnknownFieldApi; legacyAdminApprovalWithoutAssessmentStaysLegacy; OFF/ADVISORY evaluator; không UI preview/end-to-end |
| G02 | PARTIAL | draftNullPrimaryIsAllowedButIncomplete; nullDraftReplayCasAndUnknownFieldApi; legacyAdminApprovalWithoutAssessmentStaysLegacy; OFF/ADVISORY evaluator; không UI preview/end-to-end |
| G03 | PASS_SUBCASE | strictIsOnlyPureNegativeEvaluator; aiWarningAloneIsNotAHardGate; không persisted STRICT |
| G04 | PASS_SUBCASE | strictIsOnlyPureNegativeEvaluator; aiWarningAloneIsNotAHardGate; không persisted STRICT |
| G05 | PARTIAL | ClipQaService giữ nguyên, regression BE xanh; không mới render/media QA integration |
| G06 | PASS_SUBCASE | staleModeAssessmentAndLegacyConflictRequireExplicitSelection |
| A01 | PASS_SUBCASE | wrongActorAndAdminWithoutReviewerCannotAssess; inactiveReviewerCannotWriteAssessment; crossWorkspaceOrVersionCannotBeReadThroughApi; nullDraftReplayCasAndUnknownFieldApi |
| A02 | PASS_SUBCASE | wrongActorAndAdminWithoutReviewerCannotAssess; inactiveReviewerCannotWriteAssessment; crossWorkspaceOrVersionCannotBeReadThroughApi; nullDraftReplayCasAndUnknownFieldApi |
| A03 | PASS_SUBCASE | wrongActorAndAdminWithoutReviewerCannotAssess; inactiveReviewerCannotWriteAssessment; crossWorkspaceOrVersionCannotBeReadThroughApi; nullDraftReplayCasAndUnknownFieldApi |
| A04 | PARTIAL | staleApproveWritesNeitherDecisionNorAssessment; semantic fingerprint phần P5 NOT_RUN |
| V01 | PASS_SUBCASE | requestChangesCopiesEvidenceByIdentityNotOrder; keyedRequestChangesRetryCannotDuplicateVersion |
| V02 | PARTIAL | reorderInsertAndDeleteNeverRetargetExistingEvidence: identity đúng; semantic fingerprint/order proof P5 |
| V03 | PASS_SUBCASE | reorderInsertAndDeleteNeverRetargetExistingEvidence; sceneDeletionPreservesOrphanAndExplicitEvidenceRetirement |
| V04 | PASS_SUBCASE | reorderInsertAndDeleteNeverRetargetExistingEvidence; sceneDeletionPreservesOrphanAndExplicitEvidenceRetirement |
| V05 | PASS_SUBCASE | copyMapWrongRollsBackNewVersion; copyFailureRollsBackWorkflowAndKeepsOriginalInReview |
| F01 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F02 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F03 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F04 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F05 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F06 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F07 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F08 | DOCUMENT_REVIEW | SCENE_TYPE_INVENTORY:13 hiện tại=12 trướcV15+COMPOSED; projector/hash/block meaning NOT_RUN |
| F09 | DOCUMENT_REVIEW | SCENE_TYPE_INVENTORY:13 hiện tại=12 trướcV15+COMPOSED; projector/hash/block meaning NOT_RUN |
| F10 | DOCUMENT_REVIEW | SCENE_TYPE_INVENTORY:13 hiện tại=12 trướcV15+COMPOSED; projector/hash/block meaning NOT_RUN |
| F11 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F12 | PARTIAL | DTO limits/finite-number guard, không canonical/latex/unit hash test P3 |
| F13 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| F14 | PARTIAL | pinnedOldPolicySurvivesAssignmentChange; referenced edition immutable; actual snapshot/withdrawal P5/P8 |
| B01 | PARTIAL | bothVideoBranchesUseSameAssessmentGuardAndRetry[1,2]: real decision/FK, same seat/CAS gate + retry, fake render. Full artifact/snapshot P5 NOT_RUN |
| B02 | PARTIAL | bothVideoBranchesUseSameAssessmentGuardAndRetry[1,2]: real decision/FK, same seat/CAS gate + retry, fake render. Full artifact/snapshot P5 NOT_RUN |
| B03 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| B04 | DOCUMENT_REVIEW | Send-back không thêm STEM pass gate; source/BE regression giữ reason behavior; không STRICT workspace run |
| P01 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| P02 | PARTIAL | OFF/ADVISORY pin/settings code/tests; snapshot/publication/history rollout toàn phần P5/P8 NOT_RUN |
| P03 | DOCUMENT_REVIEW | YouTube503 source giữ nguyên, không nhận RF08 đã xong |
| AI01 | PARTIAL | Chỉ persistence/accept boundary validator và source review; không Java/Python stemContext/provider/exact-lock end-to-end |
| AI02 | PARTIAL | Chỉ persistence/accept boundary validator và source review; không Java/Python stemContext/provider/exact-lock end-to-end |
| L01 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| L02 | NOT_RUN | Phần ngoài P3; xem mapping thẻ P3 §8 |
| M01 | PARTIAL | freshSchemaAndRlsHaveNoVerifiedOrStrictContent; testMigrationRejectsWrongPrerequisiteType; RLS client tests; shared NOT_RUN |
| M02 | PASS_SUBCASE | M-B91000 location,25 migration bootstrap, unchanged9000/9001/source hashes; no production promotion |
| M03 | PARTIAL | freshSchemaAndRlsHaveNoVerifiedOrStrictContent; testMigrationRejectsWrongPrerequisiteType; RLS client tests; shared NOT_RUN |
| D01 | DOCUMENT_REVIEW | Native export chưa xác nhận; cả3cấp DRAFT/UNVERIFIED; không giả phản hồi/chuyên môn |
| E01 | DOCUMENT_REVIEW | Native export chưa xác nhận; cả3cấp DRAFT/UNVERIFIED; không giả phản hồi/chuyên môn |
