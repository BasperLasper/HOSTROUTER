# HostRouter

Velocity plugin for hostname-selected networks, backend MOTDs and authentication-aware routing. Source and build output live entirely in this folder. **PeakLogin has not been modified.**

## Requirements and installation

- Java **25** and Velocity **4.2.0**. The supplied PeakLogin project uses this API and Java version; Velocity 4.2.0 API classes have Java class-file version 69. This artifact cannot run on Java 21 or Velocity 3.x.
- Use the supplied PeakLogin plugin (`peaklogin` plugin ID) for networks requiring authentication. HostRouter contains no authentication implementation.
- Register `lobby`, `smp4`, `skyblock` (or your chosen names) in `velocity.toml`. Secure your backends with Velocity modern forwarding and network access restrictions.

1. Download `HostRouter-1.0.4.jar` from the [GitHub release](https://github.com/BasperLasper/HOSTROUTER/releases/tag/v1.0.4), or build it from source, and copy it to your Velocity `plugins/` directory alongside PeakLoginVelocity.jar.
2. Start Velocity with Java 25. SQLite uses native libraries; add `--enable-native-access=ALL-UNNAMED` before `-jar` to authorize native loading explicitly on Java 25.
3. HostRouter creates `plugins/hostrouter/` with the six example YAML files in `src/main/resources`. Review these before accepting traffic. **All default networks start on `lobby`; PeakLogin's `login-server` must also be `lobby`.**
4. Ensure the public DNS addresses point to your proxy. HostRouter uses the hostname from Velocity's handshake virtual host, with no reverse DNS or hostname resolution.
5. Grant the permissions documented in [commands.md](docs/commands.md), then use `/hr hostname test play.peaksmp.us`, `/hr status` and `/hr servers`.
6. Set Velocity `ping-passthrough = "DISABLED"` to avoid competing MOTD forwarding. Remove conflicting forced-host entries and plugins that select initial servers or replace pings. HostRouter selects the initial server at LAST event order; other handlers at the same order may conflict.
7. Test each network using a real client: login/register, remembered login, verified premium login, disconnect, unavailable destination, and maintenance. Run these checks on a staging proxy before enabling production traffic.

The delivered JAR was compiled and tested locally. Automated tests exercise API behavior with proxy mocks and real SQLite; they do not replace a live Velocity/PeakLogin/client deployment test.

## Default networks

| Identifier | Initial | After authentication | Backend MOTD | Player display |
|---|---|---|---|---|
| peakmc | lobby | lobby | lobby | All proxy players / global maximum 500 |
| peaksmp | lobby | smp4 | smp4 | Assigned PeakSMP sessions / 200 |
| skyboxy | lobby | skyblock | skyblock | Assigned SkyBloxy sessions / 200 |

`peaksmp.us`, `play.peaksmp.us`, `mc.peaksmp.us`, `affiliate.peaksmp.us` and `peaksmp.affiliate-domain.com` match the **whole DNS label** `peaksmp`. `fakepeaksmp.us` does not. Networks can have multiple label or domain identifiers and exact hostname aliases.

Precedence is exact alias, then domain suffix identifier, then whole-label identifier; higher `priority` breaks ties within a rule type. Equal-precedence, equal-priority matches between networks are rejected. Disabled networks participate in matching and then deny connections, so another network cannot capture their names. Unknown/invalid/ambiguous connections are rejected; unknown pings retain Velocity's response.

Hostnames are untrusted routing hints. Anyone can supply a hostname in a Minecraft handshake; it grants no authentication or administrative privilege. Original hostname and normalized hostname stay in the session and are never recomputed on backend transfers. Unicode domains normalize to IDNA ASCII, case is folded, trailing dots and optional valid ports are removed. Malformed labels, control characters, IPv6 literals and single-label hosts are rejected.

## Configuration

All six YAML files are generated from the bundled examples. Changes through commands validate, create a backup, atomically replace the relevant file, then publish an immutable in-memory snapshot. Ordinary changes require no restart. `/hr reload` validates all files before publishing; a failure retains the active snapshot. Fix invalid files on disk before retrying. Atomic replacement requires filesystem support; otherwise the edit fails safely.

- `config.yml`: debug logging; ping/health refresh (15 seconds), stale cache lifetime (300 seconds), ping timeout (4 seconds), failure threshold (3), count refresh (2 seconds), authentication check (500 ms), global maximum.
- `networks.yml`: matching, backend selection, counts, maintenance and per-network analytics/Discord switches.
- `messages.yml`: fallback and rejection messages, plain text. HostRouter provides no MOTD editor. Backend `server.properties` remains the source of the MOTD; maintenance/disabled ping responses use the relevant rejection message.
- `privacy.yml`: persisted visibility preferences keyed by administrator UUID (or `console`). Hostnames show only with explicit `hostrouter.info.hostname`; IPs require explicit `hostrouter.info.ip` **and** opting in via `/hr toggle ip`.
- `webhooks.yml`: secret URLs, event routes, titles, colours and toggles. Add routes using `webhook bind` or bind every event with `/hr webhook bindall <name>` (existing event toggles are preserved); adding a URL alone does not subscribe it to events. Optional analytics reports are emitted hourly when enabled.
- `statistics.yml`: global enable switch and retention, default 365 days. `statistics: false` disables collection for a network's new sessions. A session's collection flag is captured at its first successful backend connection so its final duration remains consistent.

Additional optional network fields:

```yaml
backup-server: lobby
priority: 10
maintenance-message: 'PeakSMP is being updated.'
require-authentication: true
statistics: true
discord: true
```

`player-count-mode` is `global`, `network` or `backend`. Global counts use every connected Velocity player, including players not assigned by HostRouter. Network counts use hostname session assignments even while players wait in the lobby or move servers. Backend counts use connected players on the configured destination. Counts are display values, refreshed periodically; maximums do not impose admission limits.

Up to 1,000 networks, 256 matching rules per network, 100 webhooks, 30 retained configuration backups and 10,000 queued database operations keep resource use bounded. Backend cache entries are restricted to configured backend names, never arbitrary public hostnames. Backups contain webhook secrets: restrict access to the plugin directory. SQLite retention pruning runs hourly when new connections are recorded. Stopped/interrupted sessions with no recorded end are excluded from average session duration.

## Routing, MOTDs and failure handling

See [architecture.md](docs/architecture.md) and [PeakLogin integration](docs/peaklogin.md).

Backend pings are asynchronous `RegisteredServer.ping()` futures. Server-list events use only cached responses and count snapshots, with no network or disk I/O. Responses preserve backend Adventure MOTD formatting, favicon and mod information exposed by Velocity. The version name and protocol remain those of the incoming Velocity ping response; backend versions are never forwarded. Player counts are replaced, and backend sample player names are deliberately removed to avoid leaking another network's players. No raw ping properties outside the official API are copied.

Unavailable backends retain the last good MOTD until `ping-stale-seconds`. With no usable cache, the existing Velocity ping response supplies the fallback description and version. Health uses the same refresh interval as the ping cache; threshold failures mark a backend offline, and a successful ping announces recovery. Health pings use Velocity's default backend ping options rather than creating cache entries per incoming hostname or protocol.

Destination routing waits for PeakLogin's actual authenticated state, then attempts the destination once per session. If it fails, the player remains on the current server or gets one optional backup/lobby fallback attempt. No automatic destination retry occurs after recovery; players can change server through your existing permitted server command. Fallback attempts are bounded to avoid loops. Initial authentication-server failure rejects the connection because moving an unauthenticated player elsewhere would conflict with PeakLogin's isolation policy. Health status is a hint; a successful ping cannot guarantee the next connection succeeds.

Policy kicks from an online current server are left to Velocity. Failed server-connect kicks or kicks from a backend marked offline may redirect once to a safe fallback. HostRouter does not partition backend access after authentication; use an existing server permission system if players must be restricted to their assigned network.

## In-game join alerts

Players with `hostrouter.alerts` (or `hostrouter.admin`) receive join alerts by default. Use `/hr alerts` to toggle, or `/hr alerts on|off` to set your preference. `/hr toggle alerts` is an alias. Preferences survive restarts; hostname details require the separate hostname permission and visibility preference. IPs are never included.

## Discord and analytics

Only HTTPS URLs on `discord.com`, `canary.discord.com`, or `ptb.discord.com` under the Discord webhook API are accepted; redirects, custom ports, query parameters and arbitrary external endpoints are rejected. Embeds disable mentions. A bounded queue sends asynchronously, serializes delivery, paces requests and honors retry headers (capped at 300 seconds) with at most three retries. Failed/dropped deliveries are logged without URLs. Queue contents are memory-only and discarded on shutdown. IP addresses are never included.

Events: `joins`, `transfers`, `disconnects`, `security`, `administration`, `server-status`, `analytics`. Per-network `discord: false` suppresses its player/routing events; shared backend health and administration events remain global. Hostname connection rejections generate security events. Ping-only requests do not create join records or webhook floods. Avoid entering webhook URLs through in-game commands on proxies where command logging plugins are installed; editing `webhooks.yml` keeps secrets out of command history.

SQLite stores a random session ID, salted SHA-256 digest of the player UUID, network, original hostname, start, completed duration and routing success/failure counters. It stores neither usernames nor IPs. Daily is the current UTC day; weekly and monthly are rolling 7/30 days. Reports show retained totals and top 20 networks/hostnames. Successful/failed transfers measure HostRouter destination attempts, while Discord transfer notifications also cover ordinary backend changes. Average duration covers completed sessions only. The retention window bounds historical totals.

## Building and tests

```sh
cd HostRouter
JAVA_HOME=/path/to/jdk-25 ./gradlew clean test jar
```

Build output: `build/libs/HostRouter-1.0.4.jar`. Gradle wrapper is included; first build needs network access for dependencies. The JAR bundles SnakeYAML and SQLite JDBC (with native libraries); Velocity and PeakLogin are not bundled. No Bukkit/Paper server dependencies are used.

Tests cover hostname matching/precedence, invalid input, atomic configuration behavior, privacy permissions, webhook endpoint restrictions, MOTD preservation, routing authentication and duplicate prevention, unavailable destinations, disconnects and persistent SQLite statistics. A live proxy, backend and Discord integration test remains a deployment task.

See [troubleshooting.md](docs/troubleshooting.md) for operational checks and limitations.
