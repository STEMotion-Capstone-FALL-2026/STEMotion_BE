# P0 account security — 2026-10-10

## Implemented

- Public POST /api/v1/auth/register is denied in SecurityConfig for every role. The service also refuses registration if called directly. Guest gets 401; authenticated callers get 403.
- New POST /api/v1/users is ADMIN-only at controller and service level. JSON body: fullName, email, password (at least eight characters), role. Returns 201 UserResponse without JWT/password/hash. Duplicate email returns conflict without overwriting an account. Password is BCrypt-hashed.
- Login and existing account records remain intact. Provisioning does not create workspace membership; invitation/acceptance remains a separate authorized flow.
- FE removes public signup and adds an account form to AdminPortal. Success/error reflect the API; creating an account never replaces the admin token/profile.
- docker-compose.yml no longer contains a DB password fallback, default JWT secret or MinIO password. These values must be supplied privately. BE and worker receive the same required render shared key. The example file lists the MinIO variable with an empty value.
- Opt-in Supabase test fixtures now use administrator provisioning rather than public signup; these tests remain transactional.
- AI placement and approved amendments are recorded in PHASE0_APPROVAL_AND_AI_PLACEMENT.md.

## Credential handling

Git-history scan found the former compose DB password in 13 commit/file pairs, all docker-compose.yml, across locally available refs. The report contains paths/SHAs only. This is not a scan of every remote fork, dangling object or external log. Removing the current literal does not remove historical exposure.

The user chose to rotate Supabase production password and update Render DB_PASSWORD personally. **Awaiting completion confirmation.** No production secret was rotated by this code change, no runtime environment was modified, and no password is included in this report. The local ignored .env still needs private updating after rotation. Do not paste the new password into chat.

Rotation checklist:

1. Reset the database password in Supabase, update DB_PASSWORD in the Render service immediately, and restart/redeploy that service.
2. Privately update every other consumer, including the local ignored .env and any migration runner. Keep DB_URL/username unchanged unless the provider requires otherwise.
3. Confirm health plus a real read/login against BE; health alone may not exercise DB connectivity. Existing application user passwords are unrelated and should not be reset.
4. Confirm the old DB credential can no longer authenticate without logging either credential. Report completion before schema work.

No git-history rewrite/force-push is performed; that requires coordination with clones and branches. Rotation is the operational protection against the exposed old value.

## Verification

- BE tests: 151 total, 147 passed, four opt-in DB cases skipped; zero errors/failures. Guest, ADMIN, WRITER, REVIEWER and PRODUCER HTTP authorization cases pass.
- FE tests: 46 passed. Production build passed.
- Supabase integration cases are skipped because this phase did not connect/write production DB while password rotation is pending.
- No Flyway migration added or applied. Up/down migration tests: not applicable to this commit. No account data update/deletion and no schema backup required for these source-only changes.
- Maven wrapper invocation fails because its jar has no main manifest entry; tests used the installed Maven distribution. This pre-existing wrapper issue is not hidden as a passing check.

## Rollout and outstanding work

Deploy BE before FE so the new provisioning API is available. Public signup closes when the BE commit is actually running, not when it is committed/pushed. This report does not confirm Render/Vercel runtime SHAs. Credential rotation remains a P0 blocker for advancing to schema work.

Scene-ID stability, append-only ReviewDecision binding, ai_requests/publications migrations, specialized AI hooks and worker deployment are subsequent work, not included here. BACKEND_API_SPEC.md gets the full contract refresh in Phase 4.
