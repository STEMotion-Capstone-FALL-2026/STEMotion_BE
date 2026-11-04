# Auth v2 local design sources

These diagrams describe the local auth-v2 source change, not a deployed feature. They supplement
the historical diagrams in the parent directory until SRS/SDD publication and isolated verification.
Do not relabel Word report figures as verified. No diagram renderer or build has been run.

- `identity.mmd`: user/whitelist/identity/challenge relationships.
- `login-link.mmd`: Google login and explicit profile link, with ownership conflicts.
- `session.mmd`: session gating and whitelist revocation.

Username/password login continues to use BCrypt and `users.id` as the JWT subject. The session
method and credential version now determine first-password-change gating. No email-based merge.
