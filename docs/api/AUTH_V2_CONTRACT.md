# Auth v2 local contract

02 November 2026. Implemented and verified locally using unit/JPA tests and an isolated E2E backend;
not migrated to the shared database or deployed. See the workspace auth-v2 test report for scope/results.
This supersedes the auth portions of the older generated OpenAPI snapshot for this local branch.
[Local runtime Springdoc export](auth-v2-local-openapi.json) was generated from the isolated backend,
not production. The E2E Google verifier substitutes identity issuance only; real RSA signature checks
are tested separately and real Google Cloud configuration still needs staging verification.

All routes use `/api/v1`. Public authentication requests carry no previous Bearer token.
Protected requests keep the existing JWT subject as `users.id`, never username or Gmail.

| Route | Request | Success / policy |
|---|---|---|
| POST auth/login | username, password | AuthResponse; no email login alias |
| GET auth/google/enabled | — | enabled; both rollout flags and client configuration required |
| POST auth/google/challenge | — | challengeId, nonce, challengeSecret, expiresInSeconds |
| POST auth/google | credential, challengeId, challengeSecret | AuthResponse; whitelist ACTIVE required |
| GET auth/me | Bearer | user + session; allowed before first password change |
| POST auth/change-password | currentPassword, newPassword | AuthResponse with replacement token |
| GET profile | Bearer | user, session, googleLinked, googleEmail, googleLoginAllowed, canLinkGoogle |
| PATCH profile | fullName only | ProfileResponse; current user only |
| POST profile/google/link-challenge | currentPassword | LINK challenge bound to current user/version |
| POST profile/google/link | credential, challengeId, challengeSecret | AuthResponse; current local user retained |
| POST users | fullName, username, password, systemRole | UserResponse, ADMIN only; USER default, ADMIN optional; never a new user's JWT |
| PATCH users/{id} | fullName, systemRole, status, password; If-Match header = current numeric version | UserResponse; access changes revoke old sessions; stale version 409 |
| DELETE users/{id} | If-Match header = current numeric version | 204; last usable Admin login protected; Google-linked users cannot be physically deleted |
| GET admin/google-whitelist | page, size, query, status | Array + existing X-Total-Count/X-Total-Pages/X-Page/X-Size headers |
| POST admin/google-whitelist | email | 201 WhitelistRow; ADMIN only; does not create a user |
| PATCH admin/google-whitelist/{id} | status, version | WhitelistRow; ACTIVE/REVOKED; no physical deletion |
| POST admin/google-whitelist/{id}/notifications/resend | version | 202; ADMIN only, ACTIVE grant, cooldown and daily resend limit |

AuthResponse: `{accessToken,tokenType,expiresInMs,user,session}`.
UserResponse retains legacy contact `email` and admin credential flag `mustChangePassword`, and adds
`username`, `hasLocalPassword`, `googleLinked`, `googleEmail`, `systemRole: ADMIN|USER`, `version`.
The old user-level `role` field is removed. Email/username/Google email may be null.
FE must use `session.requiresPasswordChange`, not the admin credential flag, to gate the session.
Session: `{authMethod: PASSWORD|GOOGLE,requiresPasswordChange}`.
WhitelistRow: `{id,email,status,version,createdAt,boundUserId,notification}`.
Notification: `{status,lastAttemptAt,nextAttemptAt,canResend}`. Status is DISABLED, NONE, PENDING,
SENDING, PREVIEW, ACCEPTED, RETRY, FAILED, CANCELLED or UNKNOWN. PREVIEW is local-only; ACCEPTED
does not claim inbox delivery. Mail failure never revokes the whitelist or rolls back the grant.

Username is canonical lowercase ASCII, 3–32 characters matching `[a-z0-9][a-z0-9._-]{2,31}`.
Deployment compatibility update: newly provisioned usernames keep this rule. Migrated local users
may retain their complete canonical legacy email as users.username (up to 254 characters). Password
login accepts that shape but still performs only a username lookup, never an email fallback or
implicit Google link. Canonical duplicates require separate reviewed usernames before migration.
Password is not trimmed or lowercased; new passwords require at least 8 characters and at most
72 UTF-8 bytes (BCrypt limit). Full name is trimmed, 2–100 characters, no control characters.
Gmail is trimmed/lowercased, exact gmail.com domain; dots and +tags are not rewritten.

Google first login creates a USER only after verification + active whitelist; no local
password/username, no workspace grant. Explicit profile linking requires an existing local session,
completed temporary-password change, password reauthentication, active whitelist and single-use LINK
challenge. No implicit matching against contact email or legacy google_id. An identity owned by a
different user is a conflict; user ownership and project/workspace IDs are not merged or changed.

Google sessions bypass only local temporary-password change. They still require ACTIVE user,
matching authVersion, matching Google identity and ACTIVE whitelist on each request. Revoking
whitelist does not revoke PASSWORD sessions. Self password change/link replaces the user's token;
admin reset increments the target user's version but returns only user data to the admin.

SystemRole and WorkspaceRole are independent. Membership `role` and workspace `myRole` use only
WRITER/REVIEWER/PRODUCER. USER/ADMIN are invalid membership values; production roles are invalid
systemRole values. Local account request DTOs reject unknown fields, including the old `role` input.
JWT `systemRole` is informational; server authority is reloaded from the current user, and scoped
actions still check the resource's active membership. Admin bypass behavior in content workflows
is retained; the planned policy to require reviewer membership for Admin decisions is separate.

Whitelist has no systemRole input. Binding or logging in with an existing Google identity preserves
the existing user's systemRole. Promotion is an explicit user-management operation after the user
exists. Role/status changes increment authVersion. At least one ACTIVE Admin with a password or
active Google binding must remain; revoking the last Google-only Admin's only login is refused.

Email is queued transactionally on add/restore and dispatched outside that transaction. ACTIVE to
ACTIVE does not queue again; duplicate add conflicts. Revocation cancels pending/retry grants;
already submitted provider requests cannot be recalled. Existing rows are not bulk-notified on
enable. See [mail setup and delivery semantics](../WHITELIST_EMAIL_LOCAL_SETUP.md).

Errors use `{status,code,message,timestamp?,details?}`. Important codes: INVALID_CREDENTIALS (401),
ACCOUNT_NOT_IN_SYSTEM (403, exact message `Tài khoản không nằm trong hệ thống.`), ACCOUNT_DISABLED (403),
PASSWORD_CHANGE_REQUIRED (403), GOOGLE_CHALLENGE_INVALID (409), GOOGLE_IDENTITY_IN_USE (409),
GOOGLE_ALREADY_LINKED (409), GOOGLE_IDENTITY_CHANGED (409), SESSION_EXPIRED/SESSION_REVOKED (401),
REAUTHENTICATION_FAILED (400), TOO_MANY_ATTEMPTS (429 + Retry-After), GOOGLE_LOGIN_DISABLED and
GOOGLE_VERIFICATION_UNAVAILABLE (503). Database authentication outages return 503, not a logout.
Constraint races currently return generic CONCURRENT_UPDATE (409), avoiding SQL/credential disclosure.
Unreviewed legacy `google_id` bindings return GOOGLE_LEGACY_REVIEW_REQUIRED rather than creating
a duplicate Google-only user. Approved migration and explicit same-owner linking are documented
in the local rollout drafts.

Setup belongs to the environment owner: OAuth Web Client ID and origins/branding on Google Cloud,
same Client ID in GOOGLE_CLIENT_ID and VITE_GOOGLE_CLIENT_ID. No service-account key, API key,
client secret, Google access token or refresh token is required for this ID-token popup flow.

Migration drafts live at `deploy/auth-v2-drafts`, outside automatic Flyway. Current rollout flags
default false. Do not start the new backend against the shared schema before the isolated schema
and mapping have been prepared. E2E's V9000 schema location exists only on the test classpath.
