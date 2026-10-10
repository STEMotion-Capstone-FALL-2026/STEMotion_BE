# BE environment handoff — 10 October 2026

## Gửi cho teammate

- Team BE: gửi file mẫu `BE_RENDER_ENV.example`; điền DB password, JWT secret
  và Gemini key qua kênh riêng. Không gửi file `.env` thật trong nhóm chung.
- Team FE: chỉ cần `VITE_API_BASE_URL=https://stemotion-be.onrender.com/api/v1`.
- Env local đủ để kết nối DB/Gemini nhưng cần chỉnh JWT, CORS và profile khi
  dùng trên Render. Chưa kiểm tra trực tiếp các giá trị trên dashboard Render.
- Worker chưa deploy: để trống `RENDER_SERVICE_URL` và `RENDER_SERVICE_KEY`;
  hệ thống chưa thể tạo video. Khi có worker, cấu hình URL HTTPS và shared key
  giống nhau giữa BE/worker; callback trỏ về BE như trong file mẫu.

Import docs/BE_RENDER_ENV.example into Render, replacing placeholders privately.
This file contains no secrets. Do not send the real BE .env to the FE team;
the FE needs only VITE_API_BASE_URL=https://stemotion-be.onrender.com/api/v1.

## Required for the deployed backend

| Variable | Value or source |
|---|---|
| SPRING_PROFILES_ACTIVE | cloud; keeps Flyway off and Hibernate validate |
| DB_URL | Supabase JDBC transaction pooler URL, port 6543, sslmode=require and prepareThreshold=0 |
| DB_USERNAME / DB_PASSWORD | Existing Supabase pooler username and database password, transferred privately |
| JWT_SECRET | Random value of at least 32 bytes; do not reuse the local development placeholder |
| CORS_ORIGINS | https://stemotion.vercel.app and any specific local/legacy FE origins needed |
| GEMINI_API_KEY | Existing valid Google AI Studio key; required for AI features |
| GEMINI_MODEL | gemini-3.5-flash-lite |

Changing JWT_SECRET invalidates existing login tokens. Generate a new value
locally, for example with Node crypto.randomBytes(48).toString('base64'), and
paste it into Render privately. Do not put it into VITE variables or Git.

## Worker and optional variables

Without the worker, leave RENDER_SERVICE_URL and RENDER_SERVICE_KEY empty.
Set RENDER_CALLBACK_URL to the backend's HTTPS /api/v1/render/callback URL.
When the worker is available, use its public HTTPS base URL and a matching
shared RENDER_SERVICE_KEY on both services. A local worker URL on Render points
to the backend's own container, not a teammate's machine.

DB_POOL_SIZE=3 and the memory/thread settings in the template are recommendations
for Render Free, not missing functional credentials. Render supplies PORT.
EMBED_BASE_URL is optional while external/LMS publishing remains unfinished.
TTS/Redis/MinIO keys belong to the worker, not the BE environment.

## What was verified

Local .env contains DB credentials and Gemini credentials/model. Its JDBC URL
uses the transaction pooler, SSL and prepareThreshold=0. Local JWT is still a
development placeholder, local CORS lacks the new production origin, profile
cloud is not set there, worker URL is local and its key/callback are missing.

These are findings about the local file. The Render dashboard environment was
not directly inspected. Public checks previously confirmed readiness UP and
CORS accepting https://stemotion.vercel.app; they do not prove every production
secret or optional variable is configured. Check Render Environment against
the template before sharing an environment handoff.
