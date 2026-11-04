# Whitelist notification email setup

The implementation is ready for local preview without a domain, sending account or external key.
Google login's Client ID is unchanged. Email sending uses separate backend-only credentials.

Verification update 02 November 2026: one owner-authorized real Brevo notification was accepted via
an isolated whitelist/outbox/worker smoke test. Inbox delivery still requires recipient confirmation.
The long-running manual E2eServer on port 18080 remains local-only; editing .env does not override
its command-line transport settings. No automatic backlog conversion or bulk email was performed.

## Local preview

Set in BE local .env, after applying the auth/roles/mail schema to a local database:

```dotenv
NOTIFICATION_EMAIL_ENABLED=true
NOTIFICATION_EMAIL_PROVIDER=local
APP_PUBLIC_URL=http://localhost:3000
```

The isolated E2eServer sets these values in its own process and ignores real transport credentials.
Add or restore a whitelist Gmail. The row first says pending and then “Đã tạo bản xem trước email local”.
Preview files are written to ../.audit/mail-preview relative to the BE working directory; their names
are event UUIDs, not email addresses. Open the HTML locally. Nothing is sent to the recipient.
This directory contains recipient data: keep it ignored and do not publish it as test artifacts.

Mail failures do not undo Google access. Permission, identity binding and notification state are
separate. An existing whitelist is not auto-notified when the feature is switched on. Use the
row's notification action if appropriate. Resends have a cooldown and a five-per-day limit.

## Real sending when a provider account is available

1. Create/configure a Brevo account and transactional sending. Verify the sender and any account
   eligibility requirements. For a branded sender, use a domain you control and apply the provider's
   domain/DNS authentication instructions. A Gmail domain cannot be authenticated by this project.
2. Generate a Brevo **API key**, not an SMTP key. Add it only to BE secrets. Do not use GCP OAuth
   client secrets, Gmail passwords, frontend VITE_* variables or Google gmail.send permissions.
3. Configure:

```dotenv
NOTIFICATION_EMAIL_ENABLED=true
NOTIFICATION_EMAIL_PROVIDER=brevo
BREVO_API_KEY=<backend-secret>
NOTIFICATION_EMAIL_FROM_ADDRESS=<verified-sender>
NOTIFICATION_EMAIL_FROM_NAME=STEMotion
APP_PUBLIC_URL=https://<deployed-app-hostname>
```

4. Restart the backend. Missing sender/key or a public HTTP real-mail URL fails validation instead
   of pretending success. Send a controlled test to a Gmail designated for testing; inspect provider
   acceptance and inbox/spam separately. No real email has been sent by the automated tests.

HTTP uses https://api.brevo.com/v3/smtp/email over HTTPS. This avoids the common SMTP-port restriction
on Render Free. Render Free can sleep, so queued jobs resume when the app wakes; it does not provide
an immediate-mail SLA. Use a continuously running worker if that delivery latency matters.

## Delivery semantics

- ACCEPTED means the provider accepted the submission, not that inbox delivery is proven.
- PREVIEW is local-only. Jobs retain their provider; changing provider does not send old local
  pending jobs to the live provider. An explicit Admin resend can choose the current provider.
- 429/5xx use bounded retry/backoff; permanent 4xx fail. Ambiguous network outcomes and expired
  send leases become UNKNOWN and need deliberate resend. Exactly-once external mail is not claimed.
- Revocation cancels waiting work and is rechecked before dispatch. A request already sent to a
  provider cannot be recalled; Google access is still checked at login/request time.
- Content warns existing username account owners to sign in with password and explicitly link
  Google first. No password, OTP, login JWT or auto-merge link is included.
- Recipient-bearing terminal outbox rows are removed after 30 days by the scheduled cleanup;
  notification audit keeps event IDs and actor metadata. Sleeping instances defer cleanup too.

Sources: [Brevo API](https://developers.brevo.com/docs/send-a-transactional-email),
[Brevo domain authentication](https://help.brevo.com/hc/en-us/articles/12163873383186-Authenticate-your-domain-with-Brevo-Brevo-code-DKIM-DMARC),
[Render Free limitations](https://render.com/docs/free).

Local Brevo testing may use APP_PUBLIC_URL=http://localhost:3000 (or another loopback URL). Public
frontend hosts require HTTPS. A localhost link opens the recipient device, so use it only for local
development. The production Render environment should point to https://stemotion.vercel.app.
