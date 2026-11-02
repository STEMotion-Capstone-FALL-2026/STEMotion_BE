# Local draft rollout for system roles and whitelist notification email

These files are **not** production Flyway migrations. No shared database has been changed.
Apply after the auth-v2 expansion/backfill/legacy-Google review, with old application writers stopped.
The running source now requires users.system_role, users.version, whitelist.grant_revision and the
outbox/audit tables. Do not start this checkout against the old shared schema.

1. Run 00_preflight_read_only.sql against the explicitly selected target. Review enum consumers,
   policies, triggers, views and functions. A domain-specific production policy must be adapted by
   review; the generic migration does not rewrite hidden role checks inside existing RLS/functions.
2. Take a restorable backup, including users and memberships; retain userId → legacyRole → systemRole
   mapping outside Git. Check that an ACTIVE Admin has a usable login method and that the deployment
   DB role can access the new RLS-protected tables. Never expose them to anon/authenticated clients.
3. Rehearse on an isolated database. Existing ADMIN users map to ADMIN; the three production-role
   values map to USER. Membership role, leader, ownership, hashes and Google bindings are preserved.
   Unexpected role values stop migration. The old native enum is retained because users.role can
   still depend on it; the new application never reads it for authorization.
4. Execute 01_expand_roles_and_notifications.sql in **one transaction**, e.g. psql --single-transaction
   --set=ON_ERROR_STOP=1 --file=01_expand_roles_and_notifications.sql, using an explicitly selected
   connection. Do not paste a connection secret into logs or commit a credential-bearing command.
5. Check counts/IDs/FKs, both role constraints, status/leader coverage and effective RLS permissions.
   Coordinate FE and BE deployment. Auth versions are bumped; users sign in again and FE uses the
   v3 profile cache. There must be no old BE instance writing users.role after cutover.
6. Enable login before mail; smoke-test USER without workspace, workspace role boundaries, Admin,
   Google linking, revoke and last-Admin guards. Leave mail provider disabled until configured.
7. Enable local preview or verified Brevo sender deliberately. Enabling mail never bulk-notifies
   old whitelist rows. An Admin can explicitly send for an existing active grant.

Before reopening writes, a failed migration transaction can be rolled back. After writes reopen,
roll back using the reviewed snapshot plus the access-change audit: do not derive a current USER's
old WRITER/REVIEWER/PRODUCER value blindly or restore a removed ADMIN privilege. A downgrade that
does not understand new USER accounts is not a safe rollback. Sent mail cannot be recalled.

Keep users.role until the acceptance window ends; removing it and the old enum requires a separate
dependency-checked cleanup migration. Do not edit V1/V17 or publish the V9001 test fixture as production
history. Assign final Flyway versions only after syncing the team's current migration history.

Verification: RolesMigrationRehearsalTest exercises a shared native enum; RolesAndNotificationsPostgresTest
and the existing Postgres tests exercise the varchar migration baseline. They are not a clone of live RLS.

Build/test output can be isolated from an IDE with -Dstemotion.build-directory=.build-codex. Maven's
default remains target. This avoids IDE-generated error stubs replacing CLI-compiled classes.
