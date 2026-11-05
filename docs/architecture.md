# Architecture and API investigation

- `HostRouter`: Velocity lifecycle/events, initial admission, immutable hostname assignments, authentication gate, single destination attempt and bounded fallback.
- `HostnameMatcher`: pure IDNA/DNS normalization and deterministic alias/suffix/label resolution. No network operations.
- `Network`, `Configuration`: immutable network definitions and validated snapshots. One administration executor serializes command changes; synchronized storage operations validate before publishing and use atomic file replacement. Up to 30 YAML backup snapshots are retained.
- `Authentication`: optional public-method bridge to inspected PeakLogin source; no auth messages from clients/backends are trusted.
- `BackendMonitor`: bounded registered-backend cache, asynchronous ping/health refresh, offline/recovery edges and count snapshots.
- `Session`: original host, normalized host, network, connection time and atomic routing guards. Assignments survive backend changes and are removed on disconnect.
- `RouterCommand`: permissions, persistent visibility preferences, command mutations and context-aware suggestions. No database/network/disk blocking on a player event thread.
- `Webhooks`: bounded in-memory delivery queue and async Java HTTP client with endpoint restrictions, rate pacing and bounded retry. URL values never appear in plugin responses or logs.
- `Statistics`: bounded single-worker SQLite writer, salted player pseudonyms, session-level aggregates and async reports. Failures disable new collection without stopping routing.

## Official Velocity API findings

The selected API is the same `com.velocitypowered:velocity-api:4.2.0` used by PeakLogin. Local class inspection verified class-file major version 69 (Java 25). The plugin therefore builds against Java 25 rather than Java 21.

- [InboundConnection](https://jd.papermc.io/velocity/4.2.0/com/velocitypowered/api/proxy/InboundConnection.html) exposes `getVirtualHost()`, the handshake host; `getHostString()` avoids DNS lookup. Velocity's API does not promise access to the original wire byte representation; “original” means the unnormalized string exposed by this method. The protocol's separate port is not included in session hostname analytics.
- [RegisteredServer](https://jd.papermc.io/velocity/4.2.0/com/velocitypowered/api/proxy/server/RegisteredServer.html) exposes `CompletableFuture<ServerPing> ping()`. The proxy ping event does not automatically contain an arbitrary selected backend's MOTD. HostRouter explicitly calls the async backend ping API outside ping-event handling.
- [ServerPing](https://jd.papermc.io/velocity/4.2.0/com/velocitypowered/api/proxy/server/ServerPing.html) supports `asBuilder()`. The backend description, favicon and mod data are preserved; the incoming Velocity response's version name and protocol are retained, counts are replaced and player samples cleared. Unknown protocol JSON properties are not exposed by this API and cannot be forwarded through its model.
- [ConnectionRequestBuilder](https://jd.papermc.io/velocity/4.2.0/com/velocitypowered/api/proxy/ConnectionRequestBuilder.html) exposes async `connect()`. HostRouter observes its result rather than blindly firing repeated requests.
- [Velocity configuration](https://docs.papermc.io/velocity/configuration/) supports built-in ping passthrough/forced hosts; disable overlapping routing/ping configuration when using HostRouter.

Scheduled refresh, statistics and administration run outside incoming packet processing. Startup YAML/database initialization and bounded shutdown flushing intentionally occur during lifecycle events. Ping callbacks never block on I/O. Shutdown cancels periodic scheduling, rejects new administration work, flushes queued statistics for up to five seconds, closes the database and drops pending webhook deliveries.

The source tree uses a compact package with separate responsibility classes; no extra dependency is added to PeakLogin. Gradle bundles only SnakeYAML, SQLite JDBC and their runtime requirements. Velocity and Adventure are supplied by the proxy.
