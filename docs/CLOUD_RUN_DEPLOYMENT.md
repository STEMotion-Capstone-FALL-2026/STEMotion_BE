# Deploy BE staging — STEMotion FALL 2026

> Historical alternative, prepared 2026-10-09. Active deployment is Render on branch `deploy/render-staging`, not Cloud Run. Do not execute these provisioning commands for the current release. The manual workflow is disabled unless `ENABLE_CLOUD_RUN_STAGING=true` is separately approved/configured. Test counts and billing observations below are historical snapshots; use `RENDER_DEPLOYMENT.md` and the latest release/test report for current evidence.

Target confirmed by the user: project `stemotion-511107`, existing Supabase database, backend first. Region defaults to `asia-southeast1`. FE/Vercel and Render VPS are separate milestones.

## Current verification and blocker

- Cloud profile connects to the configured Supabase transaction pooler on port 6543, with SSL and `prepareThreshold=0`.
- Hibernate `validate` passed against the current database. Flyway and Hibernate schema updates are disabled in the cloud profile.
- Local cloud-profile readiness returned `UP`; JWT rejected unauthenticated requests (401), allowed CORS passed (200), and untrusted CORS was rejected (403).
- Final verification passed 89 tests, including the additional undeployed-render regression test. JAR packaging passed after stopping the temporary preflight server to release the Windows file lock.
- Docker image has not yet been built/run: local Docker engine is stopped and Cloud Build cannot run until billing is enabled.
- Google Cloud project currently reports `billingEnabled=false`. No open billing account is accessible to the signed-in account. User reports billing setup error `OR_BACR2_59`. No paid resources were created.

## Build and initial deployment

Prerequisites: PowerShell 7, authenticated `gcloud`, project permissions to enable APIs/create service accounts/grant the documented IAM roles, an active linked billing account, and the local `.env` with the intended database/API settings.

Run Maven verification before deployment:

```powershell
# Use an installed Maven and JDK 17; CI/image build already use JDK 17.
mvn -B -ntp verify

# From the STEMotion_BE directory:
pwsh -File scripts/deploy-cloud-run.ps1
```

The script stops immediately if billing is disabled. Once enabled, it provisions:

- Required Cloud Run, Cloud Build, Artifact Registry, Secret Manager and IAM APIs.
- Docker repository `stemotion` in the selected region.
- Dedicated runtime identity `stemotion-runtime` with access only to the named secrets.
- Dedicated builder identity `stemotion-builder`, with write access to the registry, read access to the build-source bucket, and Cloud Logging write permission.
- Regional build-source bucket `<project>-stemotion-build-source`. Set an appropriate retention/lifecycle policy after the first successful build to limit accumulated source archives.
- Versioned Secret Manager entries for DB URL, DB username/password and JWT secret; Gemini key is included when present. Values are read locally and never printed by the script.
- Cloud Build Docker image and Cloud Run service `stemotion-be-staging`, 1 vCPU/1 GiB, concurrency 8, min 0/max 2, startup CPU boost, 300-second request timeout to accommodate current Gemini retries.

Cloud upload excludes `.env`, `.env.*`, `.git`, `target`, local agent config and node_modules. Packaged application configuration no longer contains default DB/JWT credentials. Cloud Run gets secret references, not literal credentials in the deployment command.

The first service deployment is private. The script tests readiness and Spring JWT rejection, then permits public Cloud Run invocation and checks readiness again. Public API calls still require Spring JWT/roles except the application's explicit public endpoints. Rerunning the bootstrap script is for controlled initial provisioning/configuration; use the revision workflow for routine updates to an already public staging service.

The existing shared database is **not migrated or updated** by this script. Cloud startup fails if entity/schema validation fails. Review and test schema changes separately before applying them to Supabase. The fresh-database V1 migration still reflects the original varchar IDs; do not claim the current migration history fully reproduces the team's manually updated UUID database until that is reconciled.

Initial CORS allows local FE development on localhost:3000/5173 and 127.0.0.1:3000. Override `-CorsOrigins` when FE is deployed. The command supports `-EnvFile`, `-Region`, `-ServiceName`, `-EmbedBaseUrl` and `-RenderServiceUrl`. Do not supply a localhost Render URL to Cloud Run. Render defaults to unavailable, with an explicit `RENDER_NOT_CONFIGURED` error rather than an outbound call to localhost.

## Routine CI/CD

`.github/workflows/deploy-staging.yml` is an explicit `workflow_dispatch` release. It verifies with Java 17, builds/caches a Docker image, pushes to Artifact Registry, deploys a candidate revision with no normal traffic, smoke tests it, then promotes. Deployments are serialized and never canceled mid-release. The first service and secrets must be provisioned by the script before using this workflow.

Configure GitHub environment `staging` with variables:

| Variable | Required value |
|---|---|
| `GCP_WIF_PROVIDER` | Full Google Workload Identity provider resource name |
| `GCP_DEPLOY_SERVICE_ACCOUNT` | Deployer service account email |
| `CLOUD_RUN_SECRET_BINDINGS` | Comma-separated references, e.g. `DB_URL=stemotion-be-staging-db-url:1,DB_USERNAME=stemotion-be-staging-db-user:1,DB_PASSWORD=stemotion-be-staging-db-password:1,JWT_SECRET=stemotion-be-staging-jwt:1`; include the actual Gemini/render secret versions when enabled |
| `CORS_ORIGINS` | Comma-separated allowed FE origins |
| `GEMINI_MODEL` | Optional model ID already verified for the project's API key |
| `EMBED_BASE_URL` | Optional published FE embed URL |
| `RENDER_SERVICE_URL` | Empty until Render VPS is deployed; HTTPS endpoint afterward |

Restrict the WIF provider's trust to `STEMotion-Capstone-FALL-2026/STEMotion_BE` and the intended release branch/environment. Give the deployer Cloud Run deployment permissions on this project, Artifact Registry Writer on the `stemotion` repository, and Service Account User on the runtime identity. Give GitHub's matching federated principal Workload Identity User on the deployer identity. No long-lived Google service-account JSON key is required. Provision WIF once; it has not been configured while billing setup remains unresolved.

Keep branch protections and CI checks. This workflow intentionally does not change the shared DB or automatically run on every push. The existing CI's fresh-database migration smoke must be reconciled with the UUID changes as a follow-up; passing unit tests is not evidence that V1 recreates the current schema.

## Smoke tests and rollback

After deployment verify:

- `/actuator/health/readiness` returns HTTP 200 and `{"status":"UP"}` without exposing DB details.
- `/api/v1/auth/me` without an application JWT returns 401.
- OPTIONS preflight for the actual FE origin succeeds; unrelated origins are rejected.
- Normal login, workspace listing and script/review operations from FE use the deployed API URL.
- Gemini test uses a bounded request and the existing key/model; do not run paid generation on every PR.

The local preflight against the real DB does not modify user data. Full login/project-writing smoke requires a test account and is still outstanding until a reachable staging service is available.

To rollback, obtain the previously serving revision from Cloud Run history and route traffic to that exact revision:

```powershell
gcloud run services update-traffic stemotion-be-staging --project=stemotion-511107 --region=asia-southeast1 --to-revisions=PREVIOUS_REVISION=100
```

Rollback does not restore a database schema or rotate secrets. Secret versions are pinned to each deployment. Do not delete a version still used by an active/rollback revision.

Once billing is resolved, run initial deployment, record the real Cloud Run URL/revision/build ID, configure budget/usage alerts, and finish authenticated staging smoke tests. The frontend will use `<service-url>/api/v1` when Vercel is deployed.
