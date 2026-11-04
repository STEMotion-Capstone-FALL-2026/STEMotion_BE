# Auth v2 local rollout drafts

Deployment follow-up 02 November 2026: the shared legacy schema can have password_hash NOT NULL;
01_expand.sql now explicitly drops that restriction for Google-only users. Existing email-shaped
identifiers may be retained in users.username after user approval. New provisioning still requires
the ordinary 3–32-character username. Login accepts both shapes but queries only users.username.

Choose exactly ONE backfill: 02_backfill_and_validate.sql for an explicit reviewed mapping, or
02_retain_legacy_email_usernames.sql for complete canonical legacy email usernames. The latter
uses the approved stable duplicate policy: ACTIVE Admin first, then oldest creation time/userId; other accounts receive legacy- plus 24 hex userId characters. Unique conflicts still abort the transaction.
Do not run both scripts. Do not truncate or merge duplicate user accounts to force uniqueness.

**Role/mail follow-up:** finish the username backfill and approved legacy Google import here before
the [system-role cutover](../roles-mail-drafts/README.md). These legacy import checks read users.role;
do not rerun them after the new application switches authorization to users.system_role. Reconcile
any later import against the new role model instead of trusting stale legacy ADMIN values.

These SQL files are deliberately outside production Flyway locations. Expansion, failed/valid username
backfill and approved legacy Google import have been rehearsed on a fresh embedded PostgreSQL via
AuthMigrationRehearsalTest. They have NOT been applied to the shared database or real user mappings.
Do not start this backend against the shared schema. Hibernate now validates rather than updates it.

Before running later: stop legacy user writes, back up the database, inspect real user ID types,
review the username mapping (including every ADMIN), approve legacy Google identities separately,
then rehearse expansion/backfill/constraint validation on an isolated copy. No auto-email linking.

`03_import_approved_legacy_google.sql` is an explicit per-user approval template, not an automatic
import. An unreviewed legacy Google subject cannot create a second user via Google login. A local
user can explicitly link their own legacy subject after reauthentication/whitelist checks; a subject
belonging to a different legacy user is a conflict. Google-only legacy users need a reviewed mapping
before cutover so they are not stranded without a login method.

After schema rehearsal and verification, allocate actual unused Flyway versions with the team.
Enable AUTH_V2_ENABLED only for the prepared schema and matching frontend. Enable GOOGLE_LOGIN_ENABLED
only after OAuth origins/client ID and whitelist have been prepared. Both switches default false.
The switches do not make old and new schema/entities interchangeable. Old JWTs require login again.
Do not revert just the backend after Google-only users have been created; use the reviewed backup or roll forward.

Google uses a Web Client ID (GOOGLE_CLIENT_ID / VITE_GOOGLE_CLIENT_ID). No OAuth secret, service account
or API key is used for ID-token login/link. In-memory rate limiting assumes one instance and trusts
only the container-provided remote address. Configure a trusted proxy/edge limit before public rollout.

The three new public-schema tables enable RLS with no client policies and revoke grants from
PUBLIC and Supabase anon/authenticated roles when those roles exist. They are direct-backend-only
data. Verify the backend's actual database role owns the tables or has the approved BYPASSRLS role;
do not solve access errors by granting anonymous clients access to identity/challenge records.
Review the existing users table's Data API exposure/RLS in preflight without changing it blindly.
