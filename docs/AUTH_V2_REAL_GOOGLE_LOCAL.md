# Real Google smoke check with an isolated database

The owner's Client ID stays in the existing BE/FE .env files. Never print or copy the files into logs.
Normal .env rollout flags remain false; this manual smoke environment overrides them only in its
own process and uses embedded PostgreSQL, not the configured/shared datasource.

The test-classpath `E2eServer` supports `--e2e.google.fake=false`. In that mode the test fake verifier
bean is absent and the normal production GoogleTokenVerifier checks Google-signed tokens. Pass the
Spring placeholder `--app.integration.google.client-id=${GOOGLE_CLIENT_ID:}` literally so Spring
resolves the owner's BE .env value, rather than the E2E default empty client ID. Keep this switch
out of the automated fake-Google E2E command, which intentionally exercises controlled identities.

The launcher removes overridden defaults before forwarding arguments: Spring concatenates repeated
command-line options with commas rather than applying last-value-wins. In particular, forwarding
both the empty fixture Client ID and the real override causes every Google audience check to fail.

Manual local environment:

- FE: http://localhost:3000, backend override http://localhost:18080/api/v1, real VITE_GOOGLE_CLIENT_ID.
- BE: 127.0.0.1:18080 only, embedded temporary PostgreSQL, auth flags true for this process.
- AI/render remain test doubles; do not use this environment to claim actual rendering/provider tests.
- Temporary admin: the E2E fixture defaults, e2e-admin / E2e-fixture-only-123. These are local-only
  fixture credentials and must never be used for production. The listener is restricted to loopback.

Use the admin account to add the intended Gmail under the Whitelist Google tab. Adding Gmail here
does not update any shared/production whitelist. Log out, then try the real Google button. A Gmail
outside the list must be refused. A Gmail in the list creates a Google-only user without a password
change prompt. To test linking instead, provision a username account, sign in, change its temporary
password and use Profile settings to link BEFORE first Google login with that Gmail.

Whitelist, usernames and links in this temporary database disappear when the server is stopped.
The initial smoke checks can verify enabled=true and rejection of an E2E fake token; real consent,
popup and ID-token success require the owner to select their Google account in the browser.

Google Authorized JavaScript origins must include http://localhost and http://localhost:3000.
Use localhost consistently rather than opening 127.0.0.1 unless that origin is also configured.
