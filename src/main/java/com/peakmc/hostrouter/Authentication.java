package com.peakmc.hostrouter;
import com.velocitypowered.api.proxy.*;
import java.lang.reflect.*;

/** Optional linkage to the actual public methods in the supplied PeakLogin source. No private access. */
public final class Authentication {
    private final ProxyServer proxy;
    public Authentication(ProxyServer proxy) { this.proxy=proxy; }
    private Object instance() { return proxy.getPluginManager().getPlugin("peaklogin").flatMap(p->p.getInstance()).orElse(null); }
    public boolean authenticated(Player player) {
        try {
            Object plugin=instance(); if(plugin==null)return false;
            Object manager=plugin.getClass().getMethod("auth").invoke(plugin); if(manager==null)return false;
            return Boolean.TRUE.equals(manager.getClass().getMethod("isAuthenticated",Player.class).invoke(manager,player));
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
    /** READY: client finished joining its current backend. UNSUPPORTED: PeakLogin too old to tell. */
    public enum Ready { READY, LOADING, UNSUPPORTED }
    public Ready ready(Player player) {
        try {
            Object plugin=instance(); if(plugin==null)return Ready.UNSUPPORTED;
            Object manager=plugin.getClass().getMethod("auth").invoke(plugin); if(manager==null)return Ready.UNSUPPORTED;
            Method method;
            try { method=manager.getClass().getMethod("isClientReady",Player.class); } catch (NoSuchMethodException e) { return Ready.UNSUPPORTED; }
            return Boolean.TRUE.equals(method.invoke(manager,player))?Ready.READY:Ready.LOADING;
        } catch (ReflectiveOperationException | RuntimeException e) { return Ready.UNSUPPORTED; }
    }
    public String loginServer() {
        try {
            Object plugin=instance(); if(plugin==null)return "";
            Object config=plugin.getClass().getMethod("config").invoke(plugin);
            return (String)config.getClass().getMethod("loginServer").invoke(config);
        } catch (ReflectiveOperationException | RuntimeException e) { return ""; }
    }
    public String status() {
        try {
            Object plugin=instance(); if(plugin==null)return "PeakLogin missing (fail closed)";
            Object manager=plugin.getClass().getMethod("auth").invoke(plugin);
            if(manager==null)return "PeakLogin not initialized (fail closed)";
            manager.getClass().getMethod("isAuthenticated",Player.class);
            return "PeakLogin public API available";
        } catch (ReflectiveOperationException | RuntimeException e) { return "PeakLogin API unavailable (fail closed)"; }
    }
}
