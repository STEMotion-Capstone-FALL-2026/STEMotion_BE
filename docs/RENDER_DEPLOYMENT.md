# Urgent backend deployment on Render Free

Use the existing Supabase database. `render.yaml` explicitly selects the free plan and Singapore region. This deploys the Spring Boot API, not the Remotion video worker.

## First deployment

1. Push the reviewed backend deployment changes to a deployment branch in `STEMotion-Capstone-FALL-2026/STEMotion_BE`.
2. Sign in at https://dashboard.render.com with GitHub. Authorize Render to read this private organization repository; an organization owner may need to approve the GitHub App.
3. Choose New > Blueprint, select the repository and the deployment branch, and use `render.yaml`. Confirm the service is **Free** before creating it.
4. Supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `GEMINI_API_KEY` from the existing local environment. Do not commit credentials. Retain the configured Gemini model if it differs from the Blueprint default.
5. The JDBC URL must retain `sslmode=require` and `prepareThreshold=0` for the existing Supabase transaction pooler. The `cloud` profile validates the schema and does not migrate it.
6. After the Docker build and service startup finish, check `https://<actual-service-host>/actuator/health/readiness` returns `UP`, and `/api/auth/me` without a JWT returns 401. Verify login using a test account.
7. Set `CORS_ORIGINS` to the real frontend origins when available. Set `EMBED_BASE_URL` for published embeds once the frontend is deployed.

The video worker URL is intentionally unset until the separate Remotion service is deployed. Video enqueue requests then return a clear unavailable response. API login/data features can be deployed independently.

## Releases

Auto-deploy is initially disabled. Run Maven verification before each release, then use Manual Deploy for the verified commit. After a GitHub CI workflow is connected and verified, change `autoDeployTrigger` to `checksPass`. Do not enable the Google Cloud deployment workflow for Render.

## Resource limits

Render Free provides 512 MB RAM and 0.1 CPU. The Blueprint bounds Java heap at 256 MB, metaspace at 128 MB, database connections at three, and Tomcat request threads at twenty. Native memory also counts toward the platform limit; these settings require verification in the actual container under load.

Free services sleep after 15 minutes without traffic and have cold starts. This is suitable for the current capstone API demo/staging; warm it by opening the health endpoint before a scheduled demo. Keep files in external storage because the instance filesystem is ephemeral.

References: https://render.com/docs/free, https://render.com/docs/compute-plans, https://render.com/docs/blueprint-spec, https://render.com/docs/git-provider.
