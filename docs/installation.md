# Installation

[Back to README](../README.md) · [Download latest](https://github.com/BasperLasper/HOSTROUTER/releases/latest) · [Commands](commands.md)

## 1. Install on Velocity

Use Java **25** and Velocity **4.2.0**. Download the HostRouter JAR from [Releases](https://github.com/BasperLasper/HOSTROUTER/releases) and place it in the **Velocity proxy's** `plugins/` directory. For authentication-aware networks, install your compatible PeakLogin JAR on Velocity as well. HostRouter itself is not installed on your backend servers.

When upgrading, stop Velocity, remove its old HostRouter JAR, add the new one and restart. Keep `plugins/hostrouter/` to preserve your configuration, preferences and database. Back up that folder while the proxy is stopped before upgrading.

## 2. Register the backends

In Velocity's `velocity.toml`, register the exact names you will use in HostRouter. Merge these entries into your existing sections; replace the addresses with your real backend addresses:

```toml
[servers]
lobby = "127.0.0.1:25566"
smp4 = "127.0.0.1:25567"
skyblock = "127.0.0.1:25568"
try = ["lobby"]
```

Set the existing top-level `ping-passthrough` setting to `"DISABLED"`. Remove forced-host rules or plugins that conflict with HostRouter's initial-server selection or ping handling. Retain your existing secure forwarding configuration and backend network restrictions. Restart Velocity after changing its TOML.

## 3. Set a MOTD on every backend

> **Every backend needs its own MOTD in its own `server.properties`. HostRouter reads these existing MOTDs; it does not provide a MOTD text setting.**

Edit the properties file in each server's folder:

| Server | Set these lines in its `server.properties` |
|---|---|
| `lobby` | `motd=Welcome to PeakMC` and `enable-status=true` |
| `smp4` | `motd=Welcome to PeakSMP` and `enable-status=true` |
| `skyblock` | `motd=Welcome to SkyBoxy` and `enable-status=true` |

The two properties belong on **separate lines**, for example on `smp4`:

```properties
motd=Welcome to PeakSMP
enable-status=true
```

Restart each backend after editing its properties. Repeat this setup whenever you add another backend. Backends must be reachable from the proxy so HostRouter can request their status.

The network's `motd-source` selects which backend supplies its MOTD. Defaults are `lobby` for PeakMC, `smp4` for PeakSMP and `skyblock` for SkyBoxy. Changing a different backend's MOTD will not change that network's displayed MOTD.

After a backend restarts, allow about **15 seconds** for the default ping-cache refresh. `/hr reload` only reloads HostRouter YAML; it does not apply a backend's `server.properties` edits. During an outage, the last good cached MOTD may remain visible for up to **300 seconds** by default. Velocity's version name and protocol are retained; the backend version is not forwarded.

## 4. Check HostRouter and PeakLogin

Start Velocity once to generate `plugins/hostrouter/`. In `networks.yml`, ensure every server name matches `velocity.toml`. For example:

```yaml
networks:
  peaksmp:
    enabled: true
    identifiers: [peaksmp]
    initial-server: lobby
    destination-server: smp4
    motd-source: smp4
    player-count-mode: network
    max-players: 200
    require-authentication: true
```

This is a single-network example; keep the other networks from the generated file if you use them. All required-auth networks must start on PeakLogin's configured `login-server`. The default is `lobby`. HostRouter waits for PeakLogin's actual authenticated state before transferring to the destination. See [PeakLogin integration](peaklogin.md).

Point each public hostname to the proxy. For addresses containing a port, such as `play.skyboxy.net:9321`, that port must connect to the Velocity listener. HostRouter does not bind an extra listener.

## 5. Permissions and useful commands

Grant permissions using your Velocity permission provider:

| Access | Permission |
|---|---|
| Operational administration | `hostrouter.admin` |
| Personal in-game join alerts | `hostrouter.alerts` |
| Reveal hostnames in player info and alerts | `hostrouter.info.hostname` |
| Reveal IPs in player info | `hostrouter.info.ip`, plus `/hr toggle ip` |

Join alerts default to on when permission is granted. Players can use `/hr alerts` to toggle, or `/hr alerts on` and `/hr alerts off`. Preferences persist across restarts. IPs are never included in join alerts. The admin node does not grant the sensitive hostname or IP nodes automatically.

Run these checks from the proxy console, without `/`:

```text
hr status
hr servers
hr hostname test play.peakmc.us
hr hostname test play.peaksmp.us
hr hostname test play.skyboxy.net:9321
```

After changing HostRouter YAML, run `hr reload`. Changes through HostRouter commands save and apply immediately. See the [complete command and permission reference](commands.md).

## 6. Test the player experience

Add each network address to a Minecraft client server list. Check its MOTD and player count, then join. Confirm the player starts on `lobby`, authenticates with PeakLogin, and reaches the expected destination once. Also test with a destination offline and with one network in maintenance. The [troubleshooting guide](troubleshooting.md) has a full acceptance checklist.

## Optional: Discord alerts

Configure secret webhook URLs in `plugins/hostrouter/webhooks.yml` and reload. Do not commit real webhook URLs to Git. Alternatively, add one from the proxy console; be aware that command history/logging may capture it.

After adding a webhook named `staff`, bind and test it:

```text
hr webhook bindall staff
hr webhook test staff
```

`bindall` preserves each event's enabled/disabled setting; analytics is disabled by default. Discord `events.joins.enabled` controls Discord join events only. Players control their own in-game notices with `/hr alerts`.
