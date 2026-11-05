package com.peakmc.hostrouter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
class RoutingTest {
    @TempDir Path dir;
    HostRouter p;Player player;Session session;RegisteredServer lobby,smp;ConnectionRequestBuilder request;
    @BeforeEach void setup()throws Exception {
        ProxyServer proxy=mock(ProxyServer.class);p=new HostRouter(proxy,LoggerFactory.getLogger("test"),dir);p.config=new Configuration(dir);p.config.load();
        p.auth=mock(Authentication.class);p.monitor=mock(BackendMonitor.class);p.webhooks=mock(Webhooks.class);
        player=mock(Player.class);UUID uuid=UUID.randomUUID();when(player.getUniqueId()).thenReturn(uuid);when(player.isActive()).thenReturn(true);when(player.getUsername()).thenReturn("Tester");
        lobby=mock(RegisteredServer.class);smp=mock(RegisteredServer.class);when(lobby.getServerInfo()).thenReturn(new ServerInfo("lobby",new java.net.InetSocketAddress("127.0.0.1",25566)));when(smp.getServerInfo()).thenReturn(new ServerInfo("smp4",new java.net.InetSocketAddress("127.0.0.1",25567)));
        ServerConnection current=mock(ServerConnection.class);ServerInfo lobbyInfo=lobby.getServerInfo();when(current.getServerInfo()).thenReturn(lobbyInfo);when(current.getServer()).thenReturn(lobby);when(player.getCurrentServer()).thenReturn(Optional.of(current));
        when(proxy.getServer("smp4")).thenReturn(Optional.of(smp));when(proxy.getServer("lobby")).thenReturn(Optional.of(lobby));when(p.monitor.usable(anyString())).thenReturn(true);
        request=mock(ConnectionRequestBuilder.class);when(player.createConnectionRequest(smp)).thenReturn(request);
        var result=mock(ConnectionRequestBuilder.Result.class);when(result.isSuccessful()).thenReturn(true);when(request.connect()).thenReturn(CompletableFuture.completedFuture(result));
        session=new Session(new HostnameMatcher().match("play.peaksmp.us",p.config.get().networks().values()));p.sessions.put(uuid,session);
    }
    @AfterEach void close(){p.administration.shutdownNow();}
    @Test void authenticationGateAndExactlyOneTransfer(){p.connected(new ServerPostConnectEvent(player,null));verify(player,never()).createConnectionRequest(any());assertFalse(session.routed.get());when(p.auth.authenticated(player)).thenReturn(true);p.connected(new ServerPostConnectEvent(player,lobby));p.connected(new ServerPostConnectEvent(player,lobby));verify(player,times(1)).createConnectionRequest(smp);assertTrue(session.routed.get());assertTrue(session.action.startsWith("Transferred"));}
    @Test void disconnectedPlayerCannotBeTransferred(){when(p.auth.authenticated(player)).thenReturn(true);when(player.isActive()).thenReturn(false);p.connected(new ServerPostConnectEvent(player,null));verify(player,never()).createConnectionRequest(any());}
    @Test void unavailableDestinationStaysInLobbyAndDoesNotRetry(){when(p.auth.authenticated(player)).thenReturn(true);when(p.monitor.usable("smp4")).thenReturn(false);p.connected(new ServerPostConnectEvent(player,null));p.connected(new ServerPostConnectEvent(player,lobby));verify(player,never()).createConnectionRequest(any());assertTrue(session.action.contains("no automatic destination retry"));}
    @Test void removedSessionCannotBeTransferred(){p.sessions.clear();when(p.auth.authenticated(player)).thenReturn(true);p.connected(new ServerPostConnectEvent(player,null));verify(player,never()).createConnectionRequest(any());}
    @Test void failedConnectionOnlyAttemptsDestinationOnce(){when(p.auth.authenticated(player)).thenReturn(true);when(request.connect()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));p.connected(new ServerPostConnectEvent(player,null));p.connected(new ServerPostConnectEvent(player,lobby));verify(player,times(1)).createConnectionRequest(smp);assertTrue(session.action.contains("Destination connection failed"));}
}
