package com.peakmc.hostrouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.velocitypowered.api.command.*;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
class WebhookCommandTest {
    @TempDir Path dir;
    @Test void bindallPersistsWithoutDuplicatesAndPreservesOtherRoutesAndToggles()throws Exception {
        HostRouter p=new HostRouter(mock(ProxyServer.class),LoggerFactory.getLogger("test"),dir);
        try {
            p.config=new Configuration(dir);p.config.load();p.webhooks=mock(Webhooks.class);
            p.config.edit("webhooks",m->{m.put("webhooks",Map.of("all","https://discord.com/api/webhooks/123456789012/abcdefghijklmnopqrst","existing","https://discord.com/api/webhooks/123456789013/abcdefghijklmnopqrst"));var events=Configuration.map(m.get("events"));var joins=Configuration.map(events.get("joins"));joins.put("webhooks",List.of("existing"));events.put("joins",joins);m.put("events",events);});
            CommandSource source=mock(CommandSource.class);when(source.hasPermission("hostrouter.webhook.manage")).thenReturn(true);
            SimpleCommand.Invocation invocation=mock(SimpleCommand.Invocation.class);when(invocation.source()).thenReturn(source);when(invocation.arguments()).thenReturn(new String[]{"webhook","bindall","all"});
            RouterCommand command=new RouterCommand(p);command.execute(invocation);command.execute(invocation);
            p.administration.submit(()->{}).get(5,TimeUnit.SECONDS);
            Configuration reloaded=new Configuration(dir);reloaded.load();var events=Configuration.map(reloaded.get().file("webhooks").get("events"));
            for(Object value:events.values())assertEquals(1,Collections.frequency(Configuration.list(Configuration.map(value).get("webhooks")),"all"));
            assertTrue(Configuration.list(Configuration.map(events.get("joins")).get("webhooks")).contains("existing"));
            assertFalse(Configuration.bool(Configuration.map(events.get("analytics")),"enabled",true));
        } finally {p.administration.shutdownNow();}
    }
}
