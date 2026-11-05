package com.peakmc.hostrouter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
class JoinAlertsTest {
    @TempDir Path dir;
    Configuration config;Player viewer,joined;Session session;
    @BeforeEach void setup()throws Exception {
        config=new Configuration(dir);config.load();viewer=mock(Player.class);joined=mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());when(viewer.isActive()).thenReturn(true);when(joined.getUsername()).thenReturn("NewPlayer");
        session=new Session(new HostnameMatcher().match("affiliate.skyboxy.net:9321",config.get().networks().values()));session.backend="lobby";
    }
    @Test void permissionEnablesAlertsByDefaultWithoutExposingHostnames(){
        assertFalse(JoinAlerts.enabled(config,viewer));JoinAlerts.announce(config,List.of(viewer),joined,session);verify(viewer,never()).sendMessage(any(Component.class));
        when(viewer.hasPermission("hostrouter.alerts")).thenReturn(true);assertTrue(JoinAlerts.enabled(config,viewer));JoinAlerts.announce(config,List.of(viewer),joined,session);
        verify(viewer).sendMessage(Component.text("[HostRouter] NewPlayer joined skyboxy (lobby)"));
    }
    @Test void hostnameRequiresExplicitPermissionAndVisibilityPreference()throws Exception {
        when(viewer.hasPermission("hostrouter.admin")).thenReturn(true);JoinAlerts.announce(config,List.of(viewer),joined,session);
        verify(viewer).sendMessage(Component.text("[HostRouter] NewPlayer joined skyboxy (lobby)"));
        when(viewer.hasPermission("hostrouter.info.hostname")).thenReturn(true);JoinAlerts.announce(config,List.of(viewer),joined,session);
        verify(viewer).sendMessage(Component.text("[HostRouter] NewPlayer joined skyboxy (lobby) via affiliate.skyboxy.net:9321"));
        config.edit("privacy",m->m.put("preferences",Map.of(viewer.getUniqueId().toString(),Map.of("hostname",false))));
        JoinAlerts.announce(config,List.of(viewer),joined,session);verify(viewer,times(2)).sendMessage(Component.text("[HostRouter] NewPlayer joined skyboxy (lobby)"));
    }
    @Test void playerCanPersistentlyToggleOffAndBackOnAndRevocationStopsDelivery()throws Exception {
        HostRouter plugin=new HostRouter(mock(ProxyServer.class),LoggerFactory.getLogger("test"),dir);plugin.config=config;
        try {
            when(viewer.hasPermission("hostrouter.alerts")).thenReturn(true);
            RouterCommand command=new RouterCommand(plugin);SimpleCommand.Invocation invocation=mock(SimpleCommand.Invocation.class);when(invocation.source()).thenReturn(viewer);when(invocation.arguments()).thenReturn(new String[]{"alerts"});
            command.execute(invocation);plugin.administration.submit(()->{}).get(5,TimeUnit.SECONDS);
            Configuration restart=new Configuration(dir);restart.load();assertFalse(JoinAlerts.enabled(restart,viewer));
            when(invocation.arguments()).thenReturn(new String[]{"alerts","on"});command.execute(invocation);plugin.administration.submit(()->{}).get(5,TimeUnit.SECONDS);assertTrue(JoinAlerts.enabled(config,viewer));
            when(viewer.hasPermission("hostrouter.alerts")).thenReturn(false);assertFalse(JoinAlerts.enabled(config,viewer));
        }finally{plugin.administration.shutdownNow();}
    }
}
