# Commands and permissions

Main command `/hostrouter`, alias `/hr`. All commands are handled on a bounded serial administration executor, including configuration writes and backups. Suggestions respect permissions. There are no implicit player/operator defaults: grant nodes using your Velocity permission provider. Console access follows Velocity's console permission behavior.

`hostrouter.admin` grants operational nodes below, but **never substitutes for the explicit `hostrouter.info.hostname` or `hostrouter.info.ip` permissions**. Maintenance bypass is checked directly and requires its own node.

| Command | Permission |
|---|---|
| `help`, `version`, `status`, `servers` | `hostrouter.status` |
| `reload` | `hostrouter.reload` |
| `backup` | `hostrouter.backup` |
| `list`, `network list`, `network info <name>`, `network aliases <name>` | `hostrouter.network.list` |
| `network create <name> <identifier> <initial-server> <destination>` | `hostrouter.network.create` |
| `network remove <name>` | `hostrouter.network.remove` |
| `network enable <name>`, `network disable <name>` | `hostrouter.network.edit` |
| `network set <name> <field> <value>` | `hostrouter.network.edit` |
| `hostname list` | `hostrouter.hostname.manage` |
| `hostname add <network> <identifier>`, `hostname remove <network> <identifier>` | `hostrouter.hostname.manage` |
| `hostname alias-add <network> <hostname>`, `hostname alias-remove <network> <hostname>` | `hostrouter.hostname.manage` |
| `hostname test <hostname>` | `hostrouter.hostname.manage` |
| `info <player>` | `hostrouter.info` |
| `debug` (toggle console matching logs) | `hostrouter.debug` |
| `debug <hostname>`, `test <hostname>`, `debug player <player>` | `hostrouter.debug` |
| `maintenance <network> on`, `maintenance <network> off` | `hostrouter.maintenance` |
| `stats`, `stats <network>`, `stats <network> daily/weekly/monthly` | `hostrouter.stats` |
| `webhook list`, `webhook add <name> <url>`, `webhook remove <name>`, `webhook test <name>` | `hostrouter.webhook.manage` |
| `webhook bindall <name>` (bind every event; preserve event toggles) | `hostrouter.webhook.manage` |
| `webhook bind <name> <event>`, `webhook unbind <name> <event>` | `hostrouter.webhook.manage` |
| `alerts`, `alerts on`, `alerts off`, `toggle alerts` | `hostrouter.alerts` |
| `toggle hostname` | `hostrouter.toggle.hostname` |
| `toggle ip` | `hostrouter.toggle.ip` |

Player information in both `info` and `debug player` independently checks visibility:

| Node | Behavior |
|---|---|
| `hostrouter.info.hostname` | Reveals original/normalized hostnames if the administrator's hostname toggle is on (default on). |
| `hostrouter.info.ip` | Reveals the player's IP only if the administrator's IP toggle is on (default off). |
| `hostrouter.maintenance.bypass` | Allows the player into networks in maintenance. Does not bypass disabled networks or authentication. |

Network set fields:

- `identifier`: replace identifier list with one identifier; use `hostname add` to append.
- `initial-server`, `destination`, `motd-source`: registered Velocity backend names.
- `backup-server`: registered backend or `none` to clear.
- `player-count-mode`: `global`, `network`, `backend`.
- `max-players`: 1–1,000,000 (global display uses `config.yml`'s `global-max-players`).
- `priority`: integer; higher wins among matching rules of equal specificity.
- `require-authentication`, `statistics`, `discord`: `true` or `false`.
- `maintenance-message`: remaining arguments form the plain text message.

Network names use lowercase letters, digits, `_`, `-` and are at most 48 characters. Creation validates server registrations. Removal requires no assigned sessions and cannot remove the final network. Disabling/maintenance affects new admissions; existing sessions remain online, and pending routing pauses unless the player has maintenance bypass. Successful edits save immediately.

Configuration not exposed as a command field (global intervals, event colours/titles/toggles, global maximum, statistics retention) can be edited in YAML and applied with `/hr reload`. Reload does not reroute completed sessions or reassign hostnames. `/hr backup` backs up YAML only; stop the proxy or use SQLite-aware tooling to back up statistics, including any WAL files. `hostrouter.stats.admin` is not used: no destructive database command is provided.

Debug hostname reports include input/normalized hostname, matched rule/identifier, affiliate/subdomain flag, network, enabled/maintenance state, initial/destination/backup/MOTD backends, count mode/current/maximum, authentication integration and routing readiness. Debug player reports include username, assigned network, backend, intended destination, actual authentication state, last routing action, connection timestamp and elapsed duration, with independently gated host/IP fields.

Personal in-game join alerts default to **on** for players with `hostrouter.alerts` (or `hostrouter.admin`). `/hr alerts` toggles them; `/hr alerts on|off` sets them explicitly. Preferences persist in `privacy.yml`. Notices include username, assigned network and initial backend. Hostnames require explicit `hostrouter.info.hostname` and an enabled hostname visibility preference; IPs are never included. Revoking the alerts permission stops delivery even if the saved preference is on. Discord event toggles do not affect personal in-game alerts.
