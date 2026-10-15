# BE deployment status — 09/10/2026

> Historical preparation record, superseded by `RENDER_DEPLOYMENT.md`. Current BE is deployed at https://stemotion-be.onrender.com from `deploy/render-staging`; FE is https://stemotion.vercel.app. Worker deployment is postponed by the owner (2026-10-11). The remaining text preserves observations from 2026-10-09 and must not be interpreted as current deployment status.

**Prepared and locally verified; not deployed to Cloud Run.**

Target: `stemotion-511107`, service `stemotion-be-staging`, proposed region `asia-southeast1`. User confirmed the existing Supabase database and deferred Vercel deployment.

## Verification completed

| Check | Result |
|---|---|
| Backend unit tests | 89 passed, 0 failures/errors |
| Spring Boot JAR packaging | Passed; final executable JAR created |
| Cloud profile + existing Supabase | Started; Hibernate schema validation passed |
| Readiness | `UP` |
| Protected API without JWT | HTTP 401 |
| CORS for localhost:3000 | HTTP 200; expected origin allowed |
| CORS for untrusted origin | HTTP 403 |
| Deployment PowerShell syntax | Passed |
| Billing guard, simulated disabled billing | Exited after one billing lookup, before mutations |
| Cloud profile / Cloud Build / workflow YAML parsing | Passed |
| Diff whitespace checks | Passed |

Local JVM was Java 22 with source compilation targeting Java 17. Docker and GitHub workflow use Java 17; their remote executions are still outstanding. The temporary local BE used only schema validation/read-only smoke checks; it did not run migrations or modify shared user data. That server was stopped after verification.

## Blocking condition

Google Cloud project reports `billingEnabled=false`; no open billing account is accessible to the signed-in account. User encountered `OR_BACR2_59` when setting up billing. Cloud Run/Artifact Registry APIs are not yet enabled.

No cloud resources, secret uploads, Docker image builds, WIF bindings or online backend URL have been created. The workflow is prepared locally and has not been pushed or run. No FE/Render source changes were made.

## Remaining work

1. Resolve Google Payments/Cloud Billing setup and link an active billing account to this project.
2. Run `scripts/deploy-cloud-run.ps1` after Maven verification; it provisions/builds/deploys and checks readiness/JWT before allowing public invocation.
3. Record real Cloud Build ID, image digest, Cloud Run revision and service URL.
4. Smoke normal login and project/script/review flows with a test account. Verify Gemini with the actual configured model/key and a bounded request.
5. Configure budgets, storage retention and WIF/GitHub environment variables; run the staged-revision deployment workflow.

See [deployment runbook](CLOUD_RUN_DEPLOYMENT.md) for commands and configuration. Existing user changes to UUID IDs and the development Hikari/DDL settings were preserved; cloud overrides disable automatic schema writes.

## Urgent Render alternative

Prepared `render.yaml` for Render Free in Singapore using the existing Dockerfile and cloud profile. YAML parsing and diff whitespace checks passed. Java heap/metaspace, connection pool and Tomcat threads are bounded for the 512 MB instance; actual container startup/load verification remains outstanding. See `docs/RENDER_DEPLOYMENT.md`.

Render dashboard currently shows the sign-in page. No Render service or online URL has been created. First deployment requires the user's Render login, private GitHub organization repository authorization, and publishing the prepared deployment branch. Cloud Run billing is no longer a prerequisite for this route.
