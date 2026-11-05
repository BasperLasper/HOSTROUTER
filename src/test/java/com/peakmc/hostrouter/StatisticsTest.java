package com.peakmc.hostrouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
class StatisticsTest {
    @TempDir Path dir;
    @Test void persistsCountsDurationsAndTransfersWithoutRawPlayerId()throws Exception {
        Configuration c=new Configuration(dir);c.load();Session session=new Session(new HostnameMatcher().match("affiliate.peaksmp.us",c.get().networks().values()));UUID player=UUID.randomUUID();
        try(Statistics stats=new Statistics(dir.resolve("database"),c,LoggerFactory.getLogger("test"))){stats.join(session,player);stats.transfer(session,true);stats.transfer(session,false);stats.end(session);String report=stats.report("peaksmp","daily").get(5,TimeUnit.SECONDS);assertTrue(report.contains("Connections: 1 | Unique: 1"));assertTrue(report.contains("Transfers OK/failed: 1/1"));assertTrue(report.contains("affiliate.peaksmp.us: 1"));}
        try(Statistics reopened=new Statistics(dir.resolve("database"),c,LoggerFactory.getLogger("test"))){assertTrue(reopened.report("peaksmp","all").get(5,TimeUnit.SECONDS).contains("Connections: 1"));}
        byte[] bytes=Files.readAllBytes(dir.resolve("database/statistics.db"));assertFalse(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains(player.toString()));
    }
}
