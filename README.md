# HostRouter

Run multiple Minecraft networks behind one Velocity proxy. HostRouter selects a network from the hostname, shows that network's backend MOTD, and routes players after PeakLogin authentication.

**[Download latest JAR](https://github.com/BasperLasper/HOSTROUTER/releases/latest)** · **[Releases and upgrading](docs/releases.md)** · **[Step-by-step installation](docs/installation.md)** · **[Commands and permissions](docs/commands.md)**

> **Set a MOTD on every backend server.** Edit the `motd=` line in each backend's own `server.properties`, then restart that backend. HostRouter reads the MOTD from the network's configured `motd-source`; there is no separate HostRouter MOTD editor.

## Requirements

- **Java 25** and **Velocity 4.2.0**. This build does not run on Java 21 or Velocity 3.x.
- **PeakLogin** on Velocity for networks that require authentication. HostRouter integrates with its existing public methods; PeakLogin remains unchanged. See [integration details](docs/peaklogin.md).
- Registered, reachable backend servers. The defaults use **`lobby`**, **`smp4`** and **`skyblock`**.

## Quick setup

1. [Download the latest release](https://github.com/BasperLasper/HOSTROUTER/releases/latest). Put its HostRouter JAR in Velocity's `plugins/` folder. When upgrading, remove the older HostRouter JAR first.
2. Register `lobby`, `smp4` and `skyblock` in `velocity.toml`, using your backend addresses. Set `ping-passthrough = "DISABLED"` and remove conflicting forced-host entries. Keep your forwarding and backend access restrictions configured.
3. **Set each backend's MOTD in that server's `server.properties`.** For example:

   | Backend | Its own `server.properties` | Used for |
   |---|---|---|
   | `lobby` | `motd=Welcome to PeakMC` | PeakMC |
   | `smp4` | `motd=Welcome to PeakSMP` | PeakSMP |
   | `skyblock` | `motd=Welcome to SkyBoxy` | SkyBoxy |

   Keep `enable-status=true` on the backends so HostRouter can fetch their status. Restart each backend after editing its properties. Configure a MOTD for any additional backend you add, too.
4. Start Velocity to generate `plugins/hostrouter/`. Check `networks.yml` against the table below. **All default networks start on `lobby`, so PeakLogin's `login-server` must also be `lobby`.**
5. Point the hostnames at your proxy. If players use `play.skyboxy.net:9321`, port `9321` must reach the Velocity listener; HostRouter does not open ports.
6. From the proxy console, run:

   ```text
   hr status
   hr servers
   hr hostname test play.peaksmp.us
   hr hostname test play.skyboxy.net:9321
   ```

7. Grant permissions with your Velocity permission provider, then test each network with a real client. Use `/` before commands in-game; omit it in the proxy console.

See the [installation guide](docs/installation.md) for example configuration, permissions and checks.

## Default routing and MOTDs

| Network | Hostname identifier | Initial server | After authentication | MOTD source | Player count |
|---|---|---|---|---|---|
| PeakMC | `peakmc` | `lobby` | `lobby` | `lobby` | All proxy players / 500 |
| PeakSMP | `peaksmp` | `lobby` | `smp4` | `smp4` | Assigned PeakSMP players / 200 |
| SkyBoxy | `skyboxy` | `lobby` | `skyblock` | `skyblock` | Assigned SkyBoxy players / 200 |

Identifiers match whole DNS labels, so `peaksmp.us`, `play.peaksmp.us` and `peaksmp.affiliate-domain.com` match `peaksmp`. `fakepeaksmp.us` does not. Explicit aliases, domain identifiers and priorities support additional networks and affiliate addresses.

**HostRouter forwards the backend MOTD, but retains Velocity's version name and protocol.** It replaces player counts and removes backend sample player names. MOTDs are cached; with the default settings, allow about 15 seconds for a refreshed backend MOTD to appear.

## Common commands

| Task | In-game command |
|---|---|
| Check routing and backends | `/hr status`, `/hr servers` |
| Test a hostname | `/hr hostname test play.skyboxy.net:9321` |
| Set a network's MOTD source | `/hr network set peaksmp motd-source smp4` |
| Reload HostRouter YAML | `/hr reload` |
| Toggle personal join alerts | `/hr alerts`, `/hr alerts on`, `/hr alerts off` |
| Bind a Discord webhook to every event | `/hr webhook bindall <name>` |
| Put one network into maintenance | `/hr maintenance peaksmp on` |

Join alerts are **on by default** for players with `hostrouter.alerts` or `hostrouter.admin`; personal preferences persist. Hostname details require `hostrouter.info.hostname`. IP visibility is independently restricted and off by default.

`/hr reload` reloads HostRouter YAML. It **does not reload backend `server.properties`**; restart the backend after changing its MOTD. Existing HostRouter configuration is preserved when upgrading the JAR. For old `smp`/`skybloxy` settings, follow the [upgrade instructions](docs/upgrade-1.0.1.md).

## Documentation

- [Releases, download and upgrading](docs/releases.md)
- [Installation and per-server MOTDs](docs/installation.md)
- [Commands and permissions](docs/commands.md)
- [Configuration, Discord, statistics and failure handling](docs/operations.md)
- [PeakLogin integration and shared lobby requirement](docs/peaklogin.md)
- [Troubleshooting and live acceptance checks](docs/troubleshooting.md)
- [Architecture and API investigation](docs/architecture.md)

## Build from source

With Java 25 installed:

```sh
git clone https://github.com/BasperLasper/HOSTROUTER.git
cd HOSTROUTER
./gradlew clean test jar
```

Output: `build/libs/HostRouter-1.0.4.jar`. The Gradle wrapper downloads build dependencies on first use. SnakeYAML and SQLite JDBC are bundled; Velocity and PeakLogin are supplied separately. SQLite uses native libraries; on Java 25 you can authorize native access with `--enable-native-access=ALL-UNNAMED` before `-jar` in your Velocity launch command.

The 1.0.4 build passed **28 automated tests**. Live Velocity, backend, PeakLogin and Discord checks remain deployment checks; see the [test checklist](docs/troubleshooting.md#live-acceptance-checks).
