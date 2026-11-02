# Auth v2 proposed report changes

Prepared 02/11/2026 alongside local implementation. Not evidence of executed tests or deployment.
The existing Word reports and BM files have not been rewritten or marked Implemented.

Follow-up implementation: system roles are ADMIN/USER; membership roles are WRITER/REVIEWER/PRODUCER.
Local notification outbox/provider/preview are implemented; real email remains unverified without
a provider account and verified sender. Apply these report changes together with the auth replacements:

- SRS actor mapping: Authorized User is the base account class; Writer/Reviewer/Producer are contextual
  workspace actors; Admin is a system administrator. Leader remains a membership flag. No registered
  production actor or capability is removed by splitting persistence enums.
- FR-03/FR-05: provision/select SystemRole USER or ADMIN; membership assignments are separate;
  explicit version on account administration; access changes revoke old sessions. Keep at least one
  ACTIVE Admin with a usable login, including protection of a Google-only Admin's final whitelist.
- FR-06–09/FR-48 and BR-01–03/BR-20: membership accepts only production roles, scope is the selected
  resource's workspace, and a USER without membership cannot produce content. Preserve role coverage.
- New notification requirement (assign a free FR ID during report integration): automatically queue
  notification on add/restore; no bulk backfill; mail failure does not remove login access; resend is
  Admin-only with cooldown/daily cap. Distinguish provider acceptance, preview and actual inbox delivery.
- SDD: replace shared UserRole with SystemRole/WorkspaceRole; document users.system_role, row version,
  whitelist grant_revision, outbox/provider/audit tables and claim/lease/retry/UNKNOWN semantics.
- Sequence diagrams: account promotion with session invalidation; whitelist/outbox atomic transaction;
  async provider call outside DB transaction; revoke versus in-flight mail. Add email configuration
  and Render sleep/SMTP limitations to deployment. Production role RLS must be reviewed at cutover.
- Record of changes: describe this as role-layer clarification plus new notification behavior. Keep
  Admin content-decision restrictions/project role features labelled Planned until separately built.

Updated contracts and SQL are local artifacts, not a declaration that the Word reports or live DB
already match them. Verification and remaining rollout work are tracked in the workspace implementation report.

SRS replacements:

- FR-01: password login uses canonical username (not email), admin-provisioned credential and JWT.
- FR-02: no public registration; Google login requires verified Google ID token and active Gmail
  whitelist. Unknown/revoked Gmail returns ACCOUNT_NOT_IN_SYSTEM without a request-admin feature.
- FR-03: admin supplies full name, unique username, temporary password and role for local accounts.
- FR-04: temporary local password must be changed in PASSWORD sessions; GOOGLE sessions do not
  require a local password change. Google-only users have no local password.
- FR-05: account status controls all methods; whitelist revocation controls only Google login/sessions.
- Proposed FR-56: admin adds Gmail to separate whitelist, searches/pages it and revokes/restores it.
- Proposed FR-57: user reads own profile and updates only full name; username/role/status immutable there.
- Proposed FR-58: local user explicitly links an unowned whitelisted Google identity after password
  reauthentication; preserve the same user ID, ownership, workspace and role.
- Proposed FR-59: credential version invalidates old sessions after password changes/reset/link.
- Proposed FR-60: Google token validation, purpose/user/version-bound challenges, single-use consumption
  and bounded throttling protect login/link. Test-only verifiers are excluded from production artifacts.

Confirm available FR numbers against the latest agreed SRS before incorporating the proposed IDs.
UC-01 should cover username login and Google login; UC-24 covers local provisioning and whitelist.
Add explicit Edit Profile and Link Google use cases under Authorized User and Manage Google
Whitelist under Admin. Screen flow adds settings and whitelist tab; document no-workspace state
after Google-only first login. Update NFR-08/09, error messages and Record of Changes.

SDD changes: users.username/auth_version, separate GoogleWhitelist/GoogleIdentity/GoogleChallenge,
JWT authMethod/googleIdentityId, left-joined authentication query and request-local cache,
nonce/secret digest storage, fixed lock order and uniqueness, local backfill then CHECK validation.
Update auth class/sequence diagrams for username login, self password change vs admin reset,
Google first/return login, explicit link, whitelist revoke/restore and profile rename. State diagram
for whitelist ACTIVE ↔ REVOKED; membership and content ownership remain user-ID based.

BM01 acceptance should distinguish local source implementation from verified runtime behavior;
BM04 must not record delivery/testing commitments as completed. Report 1/2 and user manual should
explain linking from the local account before first Google login to avoid creating another user.
No automatic account merge, unlink/relink, username rename, request whitelist or YouTube OAuth
has been added. Google Cloud configuration is the environment owner's responsibility.

Word/diagram publication and generated OpenAPI refresh remain a later documentation pass, after
the local contract and isolated verification are accepted. Preserve the original reports meanwhile.
