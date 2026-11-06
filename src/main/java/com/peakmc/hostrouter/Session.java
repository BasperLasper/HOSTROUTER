package com.peakmc.hostrouter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
public final class Session {
    public final UUID id=UUID.randomUUID();
    public final String original, normalized, network;
    public final long started=System.currentTimeMillis();
    public final AtomicBoolean routed=new AtomicBoolean();
    public final AtomicBoolean fallbackStarted=new AtomicBoolean();
    public volatile String action="Waiting for initial connection", backend="";
    public volatile boolean collecting;
    /** When the first backend (lobby) connected, and when the client was first seen ready there. 0 = not yet. */
    public volatile long connectedAt, readyAt;
    public Session(HostnameMatcher.Match match){original=match.original();normalized=match.normalized();network=match.network().name();}
}
