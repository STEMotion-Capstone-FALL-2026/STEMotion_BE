# Review notes — bridge R2

SQL review-only, chưa apply. Thứ tự candidate: V23 kiểm tra điều kiện, V24 miền STEM, V25 artifact/lineage. Xem [BRIDGE_REVIEW_R2.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/BRIDGE_REVIEW_R2.md>) và [ROLLBACK_PLAN_R2.md](<C:/Users/LeTran/FPT University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/rollout-gate-02/ROLLBACK_PLAN_R2.md>).

| PF | Vị trí trong gói |
|---|---|
| PF01 | Target/owner và review boundary |
| PF02 | Ref disposition trước R4 |
| PF03 | V23 auth guard, review C01 |
| PF04 | V23 ledger guard, review C02 |
| PF05 | V23 parent UUID PK, V24/V25 FK |
| PF06 | V23 collision guard, V24/V25 11 bảng |
| PF07 | V24/V25 ACL và RLS, effective role còn chờ |
| PF08 | Review old writers và hidden consumers |
| PF09 | V25 lineage, checkpoint legacy |
| PF10 | Flags và G06 runtime compatibility |
| PF11 | Restore proof và rollback oracle |
| PF12 | Drain/quarantine và outbox epochs |
| PF13 | R3 candidate requirements, R4/R5 blockers |

| Ca liên quan | Phạm vi evidence R2 |
|---|---|
| P2.CASE.V01 | Tham chiếu version/ledger và schema. NOT_RUN tại R2. |
| P2.CASE.V02 | Tham chiếu version/ledger và schema. NOT_RUN tại R2. |
| P2.CASE.V03 | Tham chiếu version/ledger và schema. NOT_RUN tại R2. |
| P2.CASE.V04 | Tham chiếu version/ledger và schema. NOT_RUN tại R2. |
| P2.CASE.V05 | Tham chiếu version/ledger và schema. NOT_RUN tại R2. |
| P2.CASE.M01 | Tham chiếu version/ledger và schema. NOT_RUN tại R2. |
| P2.CASE.M02 | Tham chiếu version/ledger và schema. NOT_RUN tại R2. |
| P2.CASE.M03 | Tham chiếu version/ledger và schema. NOT_RUN tại R2. |
| P2.CASE.R01 | Giữ provenance, status DRAFT/UNVERIFIED. |
| P2.CASE.I01 | Giữ registry và namespace hiện hành. |
| P2.CASE.D01 | PARTIAL, nguồn sơ đồ native còn blocker. |
| P2.CASE.E01 | PARTIAL, chuyên môn NOT_RECEIVED. |

| AT | Trạng thái kế thừa P7 |
|---|---|
| AT01 | PARTIAL. |
| AT02 | PARTIAL. |
| AT03 | PARTIAL. |
| AT04 | PARTIAL. |
| AT05 | PARTIAL, PACKET_ONLY_NOT_RUN. |
| AT06 | PARTIAL. |
| AT07 | PARTIAL, PACKET_ONLY_NOT_RUN. |
| AT08 | PARTIAL. |
| AT09 | PARTIAL. |
| AT10 | PARTIAL. |
| AT11 | PARTIAL. |
| AT12 | PARTIAL. |
| AT13 | PARTIAL. |
| AT14 | PARTIAL. |
| AT15 | PARTIAL. |
| AT16 | PARTIAL, PACKET_ONLY_NOT_RUN. |
| AT17 | PARTIAL. |
| AT18 | PARTIAL. |
| AT19 | PARTIAL. |
| AT20 | PARTIAL. |
| AT21 | PARTIAL. |
| AT22 | PARTIAL. |
| AT23 | PARTIAL. |
| AT24 | PARTIAL. |

Conflict.C01, Conflict.C02 và Bridge.BR01 dùng namespace rollout. P2.CASE.C01/C02 thuộc context/channel.
