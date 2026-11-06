package com.peakmc.hostrouter;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.*;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.*;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@Plugin(id="hostrouter",name="HostRouter",version="1.0.6",authors={"PeakMC"},description="Hostname-aware multi-network routing",dependencies={@Dependency(id="peaklogin",optional=true)})
public final class HostRouter {
    public final ProxyServer proxy;
    public final Logger logger;
    private final Path directory;
    public Configuration config;
    public Authentication auth;
    public Webhooks webhooks;
    public Statistics stats;
    public BackendMonitor monitor;
    public final ConcurrentMap<UUID,Session> sessions=new ConcurrentHashMap<>();
    java.util.function.LongSupplier clock=System::currentTimeMillis;
    private final HostnameMatcher matcher=new HostnameMatcher();
    public final ExecutorService administration=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),Thread.ofPlatform().name("HostRouter-admin").daemon().factory(),new ThreadPoolExecutor.AbortPolicy());
    private ScheduledTask task;
    private volatile boolean ready,stopping;
    private long lastPing,lastCount,lastAuth,lastAnalytics;
    @Inject public HostRouter(ProxyServer proxy,Logger logger,@DataDirectory Path directory){this.proxy=proxy;this.logger=logger;this.directory=directory;}
    @Subscribe public void initialize(ProxyInitializeEvent event) {
        try {
            config=new Configuration(directory);config.load();auth=new Authentication(proxy);webhooks=new Webhooks(config,logger);monitor=new BackendMonitor(this);
            try{stats=new Statistics(directory.resolve("database"),config,logger);}catch(Exception e){logger.error("Statistics unavailable; routing will continue",e);}
            proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("hostrouter").aliases("hr").plugin(this).build(),new RouterCommand(this));
            ready=true;task=proxy.getScheduler().buildTask(this,this::tick).repeat(100,TimeUnit.MILLISECONDS).schedule();
            logger.info("HostRouter enabled. {}. Login backend: {}",auth.status(),auth.loginServer());
            validateBackends();
        }catch(Exception e){logger.error("HostRouter startup failed; new connections will be denied",e);}
    }
    public void validateBackends() {
        for(Network n:config.get().networks().values()) {
            for(String backend:List.of(n.initial(),n.destination(),n.motd()))if(proxy.getServer(backend).isEmpty())logger.warn("Network {} references unregistered backend {}",n.name(),backend);
            if(n.requireAuth() && !auth.loginServer().isBlank() && !auth.loginServer().equals(n.initial()))logger.warn("Network {} initial server must equal PeakLogin login-server ({})",n.name(),auth.loginServer());
        }
    }
    public HostnameMatcher.Match match(String original) {
        var m=matcher.match(original,config.get().networks().values());
        if(config.get().debug())logger.info("HostRouter match normalized={} rule={} network={} reason={}",m.normalized(),m.rule(),m.matched()?m.network().name():"none",m.error());
        return m;
    }
    @Subscribe(order=PostOrder.LAST) public void preLogin(PreLoginEvent event) {
        if(!ready){event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text("HostRouter is unavailable")));return;}
        if(!event.getResult().isAllowed())return;
        var m=match(event.getConnection().getVirtualHost().map(a->a.getHostString()).orElse(""));
        if(!m.matched() || !m.network().enabled()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(message(m.matched()?"disabled":"unknown-host")));
            webhooks.emit("security","Connection rejected: "+(m.matched()?"disabled network "+m.network().name():m.error())+"; host="+safe(m.original()));
        }
    }
    @Subscribe(order=PostOrder.LAST) public void initial(PlayerChooseInitialServerEvent event) {
        Player p=event.getPlayer();if(!ready){p.disconnect(Component.text("HostRouter unavailable"));return;}
        var m=match(p.getVirtualHost().map(a->a.getHostString()).orElse(""));
        if(!m.matched()){p.disconnect(message("unknown-host"));return;}
        Network n=m.network();
        if(!n.enabled()){p.disconnect(message("disabled"));return;}
        if(n.maintenance() && !p.hasPermission("hostrouter.maintenance.bypass")){p.disconnect(Component.text(n.maintenanceMessage()));return;}
        // PeakLogin's own pre-connect guard is authoritative. Never select a different unauthenticated backend.
        if(n.requireAuth() && (!auth.status().equals("PeakLogin public API available") || (!auth.loginServer().isBlank() && !auth.loginServer().equals(n.initial())))) {
            p.disconnect(message("auth-unavailable"));return;
        }
        Optional<RegisteredServer> initial=proxy.getServer(n.initial());
        if(initial.isEmpty() || !monitor.usable(n.initial())){p.disconnect(message("initial-unavailable"));return;}
        Session session=new Session(m);sessions.put(p.getUniqueId(),session);
        event.setInitialServer(initial.get());
    }
    @Subscribe public void connected(ServerPostConnectEvent event) {
        Player p=event.getPlayer();Session s=sessions.get(p.getUniqueId());if(s==null)return;
        String previous=s.backend;s.backend=p.getCurrentServer().map(c->c.getServerInfo().getName()).orElse("");
        Network n=config.get().networks().get(s.network);if(n==null)return;
        if(previous.isBlank()) {
            if(s.connectedAt==0)s.connectedAt=clock.getAsLong();
            JoinAlerts.announce(config,proxy.getAllPlayers(),p,s);
            s.collecting=stats!=null && stats.enabled(n);if(s.collecting)stats.join(s,p.getUniqueId());
            emit(n,"joins",p.getUsername()+" joined; host="+s.original+"; network="+s.network+"; initial="+s.backend);
        } else if(!previous.equals(s.backend))emit(n,"transfers",p.getUsername()+"; "+previous+" → "+s.backend+"; host="+s.original+"; network="+s.network);
        route(p,s);
    }
    private void tick() {
        if(!ready || stopping)return;
        try {
            long now=System.currentTimeMillis();
            if(now-lastAnalytics>=3_600_000L){
                lastAnalytics=now;
                if(stats!=null && stats.available() && Configuration.bool(Configuration.map(Configuration.map(config.get().file("webhooks").get("events")).get("analytics")),"enabled",false))
                    stats.report("","daily").thenAccept(report->{if(!stopping)webhooks.emit("analytics",report);}).exceptionally(error->{logger.warn("HostRouter analytics report unavailable");return null;});
            }
            if(now-lastPing>=config.get().integer("ping-refresh-seconds",15)*1000L){lastPing=now;monitor.refresh();}
            if(now-lastCount>=config.get().integer("count-refresh-seconds",2)*1000L){lastCount=now;monitor.count();}
            if(now-lastAuth>=config.get().integer("auth-check-milliseconds",500)) {
                lastAuth=now;sessions.forEach((id,s)->proxy.getPlayer(id).ifPresent(p->route(p,s)));
            }
        }catch(Exception e){logger.error("HostRouter maintenance tick failed",e);}
    }
    private void route(Player p,Session s) {
        if(stopping || !p.isActive() || sessions.get(p.getUniqueId())!=s || p.getCurrentServer().isEmpty() || s.routed.get())return;
        Network n=config.get().networks().get(s.network);
        if(n==null || !n.enabled() || n.maintenance() && !p.hasPermission("hostrouter.maintenance.bypass")){s.action="Routing paused by network configuration";return;}
        // The player left the initial server on their own (e.g. /server smp) while we were
        // waiting for their client to load. Never yank them back afterwards.
        String now=p.getCurrentServer().get().getServerInfo().getName();
        if(!now.equals(n.initial())){
            if(s.routed.compareAndSet(false,true))s.action=now.equals(n.destination())?"Destination already reached":"Player moved to "+now+"; automatic routing cancelled";
            return;
        }
        if(n.requireAuth() && !auth.authenticated(p)){s.action="Waiting for PeakLogin authentication";return;}
        if(!clientLoaded(p,s)){s.action="Waiting for client to finish loading";return;}
        if(!s.routed.compareAndSet(false,true))return;
        String current=p.getCurrentServer().get().getServerInfo().getName();
        if(current.equals(n.destination())){s.action="Destination already reached";return;}
        if(!monitor.usable(n.destination())){failed(p,s,n,"Destination unavailable");return;}
        RegisteredServer target=proxy.getServer(n.destination()).orElse(null);
        if(target==null){failed(p,s,n,"Destination unregistered");return;}
        s.action="Connecting to "+n.destination();
        p.createConnectionRequest(target).connect().whenComplete((result,error)->{
            if(stopping || !p.isActive() || sessions.get(p.getUniqueId())!=s)return;
            if(error==null && result.isSuccessful()){s.action="Transferred to "+n.destination();if(s.collecting && stats!=null)stats.transfer(s,true);}
            else failed(p,s,n,"Destination connection failed");
        });
    }
    /**
     * Moving a player while their client is still loading the lobby crashes it
     * ("unknown packet id"). Premium players are authenticated instantly, so wait until
     * PeakLogin reports the client joined, plus a short grace period. If PeakLogin is too
     * old to report it, use a fixed delay; if the signal never comes, route after a timeout.
     */
    boolean clientLoaded(Player p,Session s) {
        long now=clock.getAsLong();
        if(s.connectedAt==0)return false;
        var c=config.get();
        Authentication.Ready ready=auth.ready(p);
        if(ready==Authentication.Ready.READY && s.readyAt==0)s.readyAt=now;
        if(s.readyAt!=0)return now-s.readyAt>=c.integer("route-delay-milliseconds",1000);
        if(ready==Authentication.Ready.LOADING)return now-s.connectedAt>=c.integer("route-ready-timeout-milliseconds",15000);
        return now-s.connectedAt>=c.integer("route-fallback-delay-milliseconds",3000);
    }
    private void failed(Player p,Session s,Network n,String reason) {
        s.action=reason+"; no automatic destination retry";logger.warn("HostRouter routing failed for {} on {}: {}",p.getUsername(),n.name(),reason);
        p.sendMessage(message("unavailable"));if(s.collecting && stats!=null)stats.transfer(s,false);
        emit(n,"security",p.getUsername()+"; network="+n.name()+"; "+reason);
        if(n.requireAuth() && !auth.authenticated(p))return;
        if(!s.fallbackStarted.compareAndSet(false,true))return;
        fallback(p,n,Set.of(n.destination())).ifPresent(server->{
            if(p.getCurrentServer().map(c->c.getServer().equals(server)).orElse(false))return;
            s.action+="; trying fallback "+server.getServerInfo().getName();
            p.createConnectionRequest(server).connect().whenComplete((r,e)->{
                if(stopping || !p.isActive() || sessions.get(p.getUniqueId())!=s)return;
                if(e==null && r.isSuccessful()){s.action="Fallback active: "+server.getServerInfo().getName();emit(n,"server-status","Backup server activated: "+server.getServerInfo().getName()+"; network="+n.name());}
                else {s.action="Fallback failed; current connection retained";logger.warn("HostRouter fallback failed on {}",n.name());}
            });
        });
    }
    private Optional<RegisteredServer> fallback(Player p,Network n,Set<String> excluded) {
        boolean unauth=n.requireAuth() && !auth.authenticated(p);
        for(String name:unauth?List.of(n.initial()):List.of(n.backup(),n.initial())) {
            if(name.isBlank() || excluded.contains(name) || !monitor.usable(name))continue;
            return proxy.getServer(name);
        }
        return Optional.empty();
    }
    @Subscribe(order=PostOrder.LATE) public void kicked(KickedFromServerEvent event) {
        if(!ready)return;
        Player p=event.getPlayer();Session s=sessions.get(p.getUniqueId());if(s==null)return;
        Network n=config.get().networks().get(s.network);if(n==null)return;
        // Do not turn bans or policy kicks into a fallback bypass.
        if(!event.kickedDuringServerConnect() && monitor.usable(event.getServer().getServerInfo().getName()))return;
        fallback(p,n,Set.of(event.getServer().getServerInfo().getName())).ifPresent(server->{
            if(!s.fallbackStarted.compareAndSet(false,true))return;
            s.routed.set(true);s.action="Kick fallback to "+server.getServerInfo().getName();
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(server,message("unavailable")));
            emit(n,"server-status","Fallback activated for "+p.getUsername()+" to "+server.getServerInfo().getName());
        });
    }
    @Subscribe public void disconnected(DisconnectEvent event) {
        Session s=sessions.remove(event.getPlayer().getUniqueId());if(s==null)return;
        if(s.collecting && stats!=null)stats.end(s);
        Network n=config.get().networks().get(s.network);if(n!=null)emit(n,"disconnects",event.getPlayer().getUsername()+"; host="+s.original+"; network="+s.network+"; backend="+s.backend+"; duration="+(System.currentTimeMillis()-s.started)/1000+"s");
    }
    @Subscribe(order=PostOrder.LAST) public void ping(ProxyPingEvent event){if(ready)monitor.ping(event);}
    public Component message(String key){return Component.text(config.get().message(key));}
    public void emit(Network n,String event,String text){if(n.discord())webhooks.emit(event,text);}
    private static String safe(String value){return value==null?"missing":value.replaceAll("[\\p{Cntrl}]","?").substring(0,Math.min(253,value.length()));}
    @Subscribe public void shutdown(ProxyShutdownEvent event) {
        stopping=true;ready=false;if(task!=null)task.cancel();
        administration.shutdown();try{if(!administration.awaitTermination(5,TimeUnit.SECONDS))administration.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();}
        if(monitor!=null)monitor.close();
        if(stats!=null){sessions.values().forEach(s->{if(s.collecting)stats.end(s);});stats.close();}
        sessions.clear();if(webhooks!=null)webhooks.close();
    }
}
