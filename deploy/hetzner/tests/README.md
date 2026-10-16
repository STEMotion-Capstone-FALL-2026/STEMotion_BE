# Phase A verification boundaries

`setup-simulation.sh` must run only inside a disposable Ubuntu 24.04 container. It runs setup twice with package/network/systemd/firewall/swap commands mocked, checks ownership/modes, nonduplicate SSH/fstab and non-22 refusal. This is not proof of actual UFW, fail2ban, package installation, kernel swap or a live SSH reconnect.

`python deploy/hetzner/smoke.py --worker-image stemotion-render:gcp-20261011` creates its own project and deletes only that project's containers/volumes/network afterward. It uses random test keys, blank provider keys, an empty weight mount and HTTP on free loopback ports. No rendering, TTS, OpenAI, model download, TLS issuance or cloud API calls occur. It verifies real worker BullMQ authentication via the deployment bootstrap and Redis restart persistence.

If no real worker image exists, `--mock-worker` explicitly uses nginx. That mode verifies routing only and cannot validate BullMQ/bootstrap or real rendering. Do not report it as a real worker acceptance.

Production Caddy syntax was validated with Docker `--network none`; no ACME issuance was performed. Source worker build currently fails because the frozen RENDER package manifest requires Remotion 4.0.526 while its lock resolves 4.0.527. A prior local worker image was used for the full topology test, with no assertion that it matches current source.
