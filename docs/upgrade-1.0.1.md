# Upgrade to 1.0.1

Corrects the default PeakSMP destination and MOTD source to `smp4`, and the SkyBoxy network/hostname identifier to `skyboxy`. The hostname matcher already supports explicit ports; a regression test now verifies `play.skyboxy.net:9321` against the bundled configuration.

Existing YAML files are preserved when replacing the JAR. If you already started 1.0.0 with its original network names, run these commands from the proxy console (without a leading slash), or in-game with `/`:

```text
hr network set peaksmp destination smp4
hr network set peaksmp motd-source smp4
hr hostname add skybloxy skyboxy
hr hostname test play.skyboxy.net:9321
```

Here `skybloxy` is the old stored network key; adding `skyboxy` makes the correct hostname match it. You may retain that internal key, or rename the YAML network key to `skyboxy` while the proxy is stopped. Fresh installations use `skyboxy` throughout.

Register `smp4` in `velocity.toml` before running the server-setting commands. Public port `9321` must be forwarded/bound to the Velocity listener. HostRouter selects a network after the connection reaches Velocity; it does not create additional TCP listeners.
