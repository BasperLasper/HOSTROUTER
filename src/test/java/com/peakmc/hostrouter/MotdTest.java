package com.peakmc.hostrouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import net.kyori.adventure.text.Component;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
class MotdTest {
    @TempDir Path dir;
    @Test void backendMotdIsPreservedWhileProxyVersionAndProtocolRemainAndPlayerSampleIsPrivate()throws Exception {
        ProxyServer proxy=mock(ProxyServer.class);HostRouter p=new HostRouter(proxy,LoggerFactory.getLogger("test"),dir);p.config=new Configuration(dir);p.config.load();p.webhooks=mock(Webhooks.class);
        try {
            RegisteredServer backend=mock(RegisteredServer.class);when(proxy.getServer(anyString())).thenReturn(Optional.of(backend));
            Component description=Component.text("Backend MOTD").color(net.kyori.adventure.text.format.NamedTextColor.GOLD);
            ServerPing response=ServerPing.builder().version(new ServerPing.Version(777,"Backend version")).description(description).onlinePlayers(99).maximumPlayers(100).samplePlayers(new ServerPing.SamplePlayer("private-name",UUID.randomUUID())).build();
            when(backend.ping()).thenReturn(CompletableFuture.completedFuture(response));
            BackendMonitor monitor=new BackendMonitor(p);monitor.refresh();
            InboundConnection inbound=mock(InboundConnection.class);when(inbound.getVirtualHost()).thenReturn(Optional.of(InetSocketAddress.createUnresolved("play.peaksmp.us",25565)));
            ProxyPingEvent event=new ProxyPingEvent(inbound,ServerPing.builder().version(new ServerPing.Version(1,"Proxy")).description(Component.text("Proxy MOTD")).build());
            monitor.ping(event);assertEquals(description,event.getPing().getDescriptionComponent());assertEquals(1,event.getPing().getVersion().getProtocol());assertEquals("Proxy",event.getPing().getVersion().getName());assertEquals(200,event.getPing().getPlayers().orElseThrow().getMax());assertEquals(0,event.getPing().getPlayers().orElseThrow().getOnline());assertTrue(event.getPing().getPlayers().orElseThrow().getSample().isEmpty());
            // Failed refresh retains the last usable response.
            when(backend.ping()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));monitor.refresh();monitor.ping(event);assertEquals(description,event.getPing().getDescriptionComponent());
            when(backend.ping()).thenReturn(CompletableFuture.completedFuture(response.asBuilder().nullPlayers().build()));
            monitor.refresh();monitor.ping(event);
            assertEquals(200,event.getPing().getPlayers().orElseThrow().getMax());
            monitor.close();
        }finally{p.administration.shutdownNow();}
    }
}
