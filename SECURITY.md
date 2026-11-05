# Security policy

NAS Watchdog intentionally runs with host-level visibility and access to the Docker socket. Treat the service and its API token as privileged infrastructure.

Please report suspected vulnerabilities privately through the repository’s GitHub Security Advisory workflow rather than including exploit details in a public issue: **Security → Advisories → Report a vulnerability**. If private reporting is not enabled, ask for a private contact path in a minimal public issue without sharing the sensitive details.

Include the affected version/configuration and a safe reproduction. Never include your real API token, notification secrets/URLs, private key, hostnames/IPs, drive serials, or raw logs.

Fixes should be developed and validated against the current source. Only the latest maintained release/source is expected to receive fixes unless the project states otherwise.

## Deployment security checklist

- Use a unique token from `openssl rand -hex 32`; leave `.env` out of Git and issue attachments.
- Keep the service off the public internet; prefer a trusted LAN or existing VPN.
- Leave `WD_ALLOW_ACTIONS=false` and `WD_ALLOW_REBOOT=false` unless explicitly needed.
- Review updates before rebuilding a privileged host container.
- Understand that host mounts, raw SMART devices, host namespaces, and the Docker socket provide much broader access than an ordinary monitoring container.
- Redact hardware reports and logs even though the supplied diagnostics script avoids deliberately collecting SMART serials, complete stats, or environment variables.
- The Android app uses a no-backup telemetry directory and explicit cloud/device-transfer exclusions for API tokens and cached host data. Treat an unlocked or compromised phone as a credential boundary just like an SSH client.
