package com.peakmc.hostrouter;

import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackendMonitor {
    private static final class State {
        volatile ServerPing ping; volatile long updated; volatile int failures; volatile boolean offline;
        final AtomicBoolean pending=new AtomicBoolean();
    }
    private final HostRouter plugin;
    private final ConcurrentMap<String,State> states=new ConcurrentHashMap<>();
    private volatile Map<String,Integer> counts=Map.of();
    private volatile int globalCount;
    private volatile boolean closed;
    public BackendMonitor(HostRouter plugin){this.plugin=plugin;}
    public void refresh() {
        Set<String> needed=new HashSet<>();
        for(Network n:plugin.config.get().networks().values()){needed.add(n.initial());needed.add(n.destination());needed.add(n.motd());if(!n.backup().isBlank())needed.add(n.backup());}
        states.keySet().retainAll(needed);
        for(String name:needed) {
            State state=states.computeIfAbsent(name,k->new State());
            if(!state.pending.compareAndSet(false,true))continue;
            Optional<RegisteredServer> server=plugin.proxy.getServer(name);
            if(server.isEmpty()){complete(name,state,null,new IllegalStateException("Unregistered backend"));continue;}
            server.get().ping().orTimeout(plugin.config.get().integer("ping-timeout-seconds",4),TimeUnit.SECONDS)
                    .whenComplete((ping,error)->complete(name,state,ping,error));
        }
    }
    private void complete(String name,State state,ServerPing ping,Throwable error) {
        try {
            if(closed)return;
            if(error==null) {
                state.ping=ping;state.updated=System.currentTimeMillis();state.failures=0;
                if(state.offline){state.offline=false;plugin.webhooks.emit("server-status","Backend online; recovery completed: "+name);}
            } else if(++state.failures>=plugin.config.get().integer("health-failure-threshold",3) && !state.offline) {
                state.offline=true;plugin.logger.warn("HostRouter backend unavailable: {}",name);plugin.webhooks.emit("server-status","Backend offline: "+name);
            }
        } finally {state.pending.set(false);}
    }
    public boolean usable(String name) {State s=states.get(name);return plugin.proxy.getServer(name).isPresent() && (s==null || !s.offline);}
    public String status(String name) {State s=states.get(name);return plugin.proxy.getServer(name).isEmpty()?"UNREGISTERED":s==null || s.updated==0 && !s.offline?"CHECKING":s.offline?"OFFLINE":"ONLINE";}
    public void count() {
        Map<String,Integer> next=new HashMap<>(); globalCount=plugin.proxy.getPlayerCount();
        plugin.sessions.forEach((uuid,s)->{if(plugin.proxy.getPlayer(uuid).filter(Player::isActive).isPresent())next.merge(s.network,1,Integer::sum);});
        for(RegisteredServer server:plugin.proxy.getAllServers())next.put("backend:"+server.getServerInfo().getName(),server.getPlayersConnected().size());
        counts=Map.copyOf(next);
    }
    public int count(Network n) {return switch(n.countMode()){case "global"->globalCount;case "backend"->counts.getOrDefault("backend:"+n.destination(),0);default->counts.getOrDefault(n.name(),0);};}
    public void ping(ProxyPingEvent event) {
        String original=event.getConnection().getVirtualHost().map(a->a.getHostString()).orElse("");
        var match=plugin.match(original); if(!match.matched())return;
        Network n=match.network(); State s=states.get(n.motd());
        ServerPing source=s!=null && s.ping!=null && System.currentTimeMillis()-s.updated<=plugin.config.get().integer("ping-stale-seconds",300)*1000L?s.ping:event.getPing();
        // asBuilder retains nullPlayers internally; setters cannot restore counts on a backend hiding its player list.
        ServerPing counted=new ServerPing(event.getPing().getVersion(),new ServerPing.Players(count(n),
                n.countMode().equals("global")?plugin.config.get().integer("global-max-players",500):n.maxPlayers(),List.of()),
                source.getDescriptionComponent(),source.getFavicon().orElse(null),source.getModinfo().orElse(null));
        var builder=counted.asBuilder();
        if(!n.enabled())builder.description(plugin.message("disabled"));
        else if(n.maintenance())builder.description(net.kyori.adventure.text.Component.text(n.maintenanceMessage()));
        event.setPing(builder.build());
    }
    public void close(){closed=true;states.clear();}
}
