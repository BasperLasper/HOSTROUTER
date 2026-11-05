package com.peakmc.hostrouter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.velocitypowered.api.command.CommandSource;
import java.util.*;
class SecurityTest {
    @Test void webhookUrlCannotReachInternalServicesOrUseRedirectEndpoints(){for(String url:List.of("http://discord.com/api/webhooks/123456789012/abcdefghijklmnopqrst", "https://localhost/api/webhooks/123456789012/abcdefghijklmnopqrst","https://discord.com.evil.com/api/webhooks/123456789012/abcdefghijklmnopqrst","https://discord.com@127.0.0.1/api/webhooks/123456789012/abcdefghijklmnopqrst","https://discord.com:443/api/webhooks/123456789012/abcdefghijklmnopqrst","https://discord.com/api/webhooks/123456789012/abcdefghijklmnopqrst?x=y"))assertThrows(IllegalArgumentException.class,()->Webhooks.validateUrl(url));assertNotNull(Webhooks.validateUrl("https://discord.com/api/webhooks/123456789012/abcdefghijklmnopqrst"));}
    @Test void adminDoesNotImplySensitiveVisibility(){CommandSource s=mock(CommandSource.class);when(s.hasPermission("hostrouter.admin")).thenReturn(true);assertTrue(RouterCommand.permitted(s,"hostrouter.reload"));assertFalse(RouterCommand.permitted(s,"hostrouter.info.ip"));assertFalse(RouterCommand.permitted(s,"hostrouter.info.hostname"));}
    @Test void webhookJsonEscapesControlCharacters(){assertEquals("\"a\\\"\\\\\\n\\u0000\"",Webhooks.json("a\"\\\n\u0000"));}
}
