package com.peakmc.hostrouter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
class ConfigurationTest {
    @TempDir Path dir;
    @Test void defaultsLoadAndEditsSurviveRestart()throws Exception{Configuration c=new Configuration(dir);c.load();assertEquals(3,c.get().networks().size());c.edit("config",m->m.put("debug",true));Configuration restart=new Configuration(dir);restart.load();assertTrue(restart.get().debug());try(var s=Files.list(dir.resolve("backups"))){assertEquals(1,s.count());}}
    @Test void failedReloadPreservesWorkingSnapshot()throws Exception{Configuration c=new Configuration(dir);c.load();var before=c.get();Files.writeString(dir.resolve("networks.yml"),"networks: [broken]");assertThrows(java.io.IOException.class,c::load);assertSame(before,c.get());}
    @Test void invalidEditDoesNotTouchDiskOrSnapshot()throws Exception{Configuration c=new Configuration(dir);c.load();String before=Files.readString(dir.resolve("config.yml"));var snapshot=c.get();assertThrows(IllegalArgumentException.class,()->c.edit("config",m->m.put("auth-check-milliseconds",1)));assertSame(snapshot,c.get());assertEquals(before,Files.readString(dir.resolve("config.yml")));}
    @Test void duplicateYamlKeysRejected()throws Exception{Configuration c=new Configuration(dir);c.load();Files.writeString(dir.resolve("config.yml"),"debug: true\ndebug: false\n");assertThrows(java.io.IOException.class,c::load);}
    @Test void snapshotsCannotBeChangedOutsideTransaction()throws Exception{Configuration c=new Configuration(dir);c.load();assertThrows(UnsupportedOperationException.class,()->c.get().file("config").put("debug",true));assertThrows(UnsupportedOperationException.class,()->c.get().networks().clear());}
    @Test void correctedSkyboxyHostnameWithPortAndSmp4Defaults()throws Exception {
        Configuration c=new Configuration(dir);c.load();
        var match=new HostnameMatcher().match("play.skyboxy.net:9321",c.get().networks().values());
        assertTrue(match.matched());assertEquals("skyboxy",match.network().name());
        assertEquals("play.skyboxy.net",match.normalized());
        assertEquals("smp4",c.get().networks().get("peaksmp").destination());
        assertEquals("smp4",c.get().networks().get("peaksmp").motd());
    }
}
