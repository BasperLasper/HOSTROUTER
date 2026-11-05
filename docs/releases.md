# Releases

**[Open GitHub Releases](https://github.com/BasperLasper/HOSTROUTER/releases)** · **[Latest release](https://github.com/BasperLasper/HOSTROUTER/releases/latest)** · [Installation guide](installation.md)

## HostRouter 1.0.4

**[Download HostRouter-1.0.4.jar](https://github.com/BasperLasper/HOSTROUTER/releases/download/v1.0.4/HostRouter-1.0.4.jar)** · [Release notes and assets](https://github.com/BasperLasper/HOSTROUTER/releases/tag/v1.0.4)

Requires **Java 25** and **Velocity 4.2.0**. PeakLogin is required for networks configured to wait for authentication. Install the HostRouter JAR on Velocity; the GitHub-generated source ZIP is for development, not installation.

Included in this release:

- Hostname-based networks, backend MOTDs and configurable player counts.
- Authentication-aware routing using the existing PeakLogin public methods.
- Corrected defaults: `smp4` for PeakSMP and `skyboxy` as the SkyBoxy identifier.
- Velocity version/protocol retained in ping responses.
- Discord webhook routing, including `/hr webhook bindall <name>`.
- In-game join alerts enabled by default with permission, controlled by `/hr alerts [on|off]`.
- SQLite statistics, health monitoring, maintenance and documented permissions.

> **Set `motd=` in each backend's own `server.properties`, keep `enable-status=true`, then restart the backend.** The network's `motd-source` selects which server HostRouter reads. See [per-server MOTD setup](installation.md#3-set-a-motd-on-every-backend).

Validation: **28 automated tests passed** for the 1.0.4 build. Live proxy/client, backend and Discord testing remain deployment checks. See the [acceptance checklist](troubleshooting.md#live-acceptance-checks).

SHA-256 of `HostRouter-1.0.4.jar`:

```text
dac12a90bb2d0728c6282bd8a7e2cf66a6c02b65a7431cd8560dbede78f45618
```

## Upgrade an existing installation

1. Stop Velocity and back up `plugins/hostrouter/`, including the database.
2. Remove the previous HostRouter JAR from `plugins/` and copy in the new one. Keep the data folder.
3. Start Velocity and check `hr status` and `hr servers` from the proxy console.
4. Test hostname matches, MOTDs and authentication routing with a client.

Replacing the JAR preserves existing YAML files. If you started with the original `smp` and `skybloxy` defaults, apply the [configuration upgrade steps](upgrade-1.0.1.md). New defaults do not overwrite your existing settings.

## Release history

| Version | Changes |
|---|---|
| 1.0.4 | Personal join alerts, on by default with permission; persistent `/hr alerts [on|off]`. Current downloadable release. |
| 1.0.3 | Added `/hr webhook bindall <name>`. |
| 1.0.2 | Stopped forwarding backend version/protocol; retains Velocity's values. |
| 1.0.1 | Corrected `smp4` and `skyboxy` defaults; explicit-port hostname regression coverage. |
| 1.0.0 | Initial implementation. |

Earlier versions were local builds; only 1.0.4 has a GitHub release asset. These documentation updates do not change the plugin binary or require a new JAR.
