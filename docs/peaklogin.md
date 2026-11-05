# PeakLogin integration

Inspected source: `../PeakLogin/src/main/java/com/peaksmp/peaklogin/`.

The supplied plugin declares ID `peaklogin`. It exposes these actual public methods:

- `PeakLogin.auth()` returns `AuthManager`.
- `AuthManager.isAuthenticated(Player)` reads the current session's authentication flag.
- `PeakLogin.config()` returns `LoginConfig`.
- `LoginConfig.loginServer()` returns the enforced login backend name.

`AuthManager.authenticate(...)` sets its session's `volatile boolean authenticated` flag. Password login, registration, remembered login, premium verification and Floodgate login all use that state. No public authentication-success event or service registration was found. The `peaklogin:auth` channel sends authentication state **from the proxy to backends**, and the listener only accepts backend `REQUEST` messages. It is not an inbound success notification API for HostRouter.

HostRouter uses the plugin manager's real PeakLogin instance and invokes the public accessors above. Reflection supplies optional linkage so PeakLogin implementation classes do not have to be bundled or modified. It never accesses private fields. The optional Velocity dependency loads PeakLogin before HostRouter when installed.

A periodic check (default 500 ms) observes `isAuthenticated(Player)`. The time interval only controls observation latency; elapsed time never authenticates the player. Initial connection plus an authenticated result are both required before a single compare-and-set guard allows destination routing. Missing plugin, failed startup, changed method signatures, null manager or reflection failure all mean **not authenticated**, and a required-auth network rejects initial connections if the public API is unavailable. `/hr status` and `/hr debug player <name>` expose the integration's state.

PeakLogin enforces `login-server` in its initial-server listener and rejects unauthenticated connections to other backends in its LAST-order pre-connect listener. Therefore **all HostRouter networks requiring PeakLogin must use the same initial server as PeakLogin's configured login server**. The supplied config uses `lobby`, matching HostRouter's defaults. Configuring a different initial server on a required-auth network is rejected at admission; no PeakLogin source change is needed.

`require-authentication: false` means HostRouter itself will not wait for authentication. It does not disable PeakLogin's independent authentication/transfer restrictions, even for that network. Use it only when authentication is deliberately handled elsewhere or PeakLogin is absent; configuring it cannot create a bypass of PeakLogin's server guard.

Integration has been source-inspected and the HostRouter routing gate has automated tests. Actual login/register/premium/remembered/Floodgate behavior must also be checked against your deployed PeakLogin JAR in staging; a different JAR may not expose the same public surface. No PeakLogin source files, generated files or JARs were modified or rebuilt.
