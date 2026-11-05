# Troubleshooting

| Symptom | Check |
|---|---|
| `UnsupportedClassVersionError` | Run Java 25 and Velocity 4.2.0. Java 21 cannot load this build or the supplied PeakLogin build. |
| HostRouter rejects all new connections | Read startup error, check YAML syntax/validation, and restart after fixing startup failure. A failed startup intentionally fails closed. A failed live reload retains the previous active configuration. |
| Unknown/ambiguous hostname | `/hr hostname test <host>`. Use whole-label identifiers, exact aliases, domain suffix identifiers or different network priorities. Do not use unsafe substring rules. |
| Authentication integration unavailable | Check `/hr status`, installed plugin ID `peaklogin`, PeakLogin startup success, and the deployed JAR's public accessors. |
| Initial server mismatch | Match every required-auth network's `initial-server` to PeakLogin's `login-server`, normally `lobby`. |
| Still in lobby after login | `/hr debug player <name>`, `/hr servers`, destination registration, enabled/maintenance flags. A failed destination is attempted only once; reconnect after fixing it. PeakMC intentionally stays in lobby. |
| Auth-free network cannot leave lobby | PeakLogin still enforces its own server guard. HostRouter's flag cannot override it. |
| Login server offline | Authentication requires that server. HostRouter rejects new connections instead of sending unauthenticated players to a different backend. |
| Backend MOTD absent/old | **Set `motd=` in every backend's own `server.properties`, keep `enable-status=true`, and restart that backend.** Ensure `motd-source` matches a registered reachable backend, wait one refresh interval, check backend status, change its `server.properties` MOTD and restart/reload that backend as needed. Stale data expires after the configured lifetime. |
| Version display | HostRouter always retains the incoming Velocity response version name and protocol, including during backend outages. Backend versions are never forwarded. |
| Wrong network/global count | Network counts follow original-host assignments across transfers; global counts include all proxy players. Allow `count-refresh-seconds` for snapshots. Backend mode counts destination-server occupants. Maximum values only affect display. |
| Host/IP absent in player debug | Explicit sensitive info permission and corresponding persistent toggle must both allow it. `hostrouter.admin` alone never reveals either. |
| Command denied before login | PeakLogin blocks non-auth commands for unauthenticated players. Authenticate or use console; HostRouter does not bypass this. |
| Webhook added but no events | Bind it using `/hr webhook bind <name> <event>`, enable the event in YAML, reload and test. URLs are intentionally hidden. No per-network player events with `discord: false`. |
| Webhook HTTP 429/failures | Delivery retries are bounded and queue contents are not durable. A long outage/full queue drops events; routing continues. |
| Statistics unavailable | Database file permissions/disk capacity, SQLite native access, JDBC startup errors. Routing continues without analytics; restart after resolving database failures. |
| Invalid config cannot be edited with commands | Fix the file or restore a YAML snapshot from `backups`; active memory may differ from invalid files after a failed reload. |
| Atomic write rejected | Use a filesystem supporting atomic replacement in the plugin data directory. Active settings remain unchanged. |
| Server kicks disconnect instead of redirect | Online server policy kicks are deliberately respected. Failed server connections/offline-backend kicks can redirect once; a failed fallback cannot loop. |

## Live acceptance checks

Use a staging proxy with the actual PeakLogin JAR and all three registered backends:

1. Ping all example root/subdomain/affiliate addresses. Compare MOTD formatting, protocol, favicon and counts with backend responses.
2. Confirm unknown, malformed and equally prioritized ambiguous hostnames reject login. Test an explicit alias override and disabled network.
3. Join each hostname with an unauthenticated account. Confirm it starts on lobby, remains there before login, and transfers once after correct login/register. Repeat with remembered, premium and Floodgate paths where enabled.
4. Move an authenticated player between servers; its original network count and hostname must stay assigned. Disconnect and confirm removal after count refresh.
5. Stop a destination. Confirm cached MOTD, offline notification after the threshold, one failed route and fallback/current-lobby retention. Restart it and confirm recovery notification without an automatic repeat transfer.
6. Enable maintenance for one network; verify normal admissions are rejected, explicit bypass works, and other networks remain available.
7. Verify unauthorized commands and suggestions are blocked. Grant info/debug without sensitive nodes and confirm hostname/IP redaction; toggle preferences, restart and verify persistence.
8. Bind test Discord events and inspect join/transfer/disconnect/security/admin/health embeds, including no IP data or unwanted mentions.
9. Reconnect the same account, inspect daily/week/month reports and restart to confirm persistence. Corrupt a YAML file and confirm reload retains working settings.

Only local build and automated tests were run for this delivery. Backend, Discord and live authentication checks above require your deployment environment.
