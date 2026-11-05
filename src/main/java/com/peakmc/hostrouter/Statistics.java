package com.peakmc.hostrouter;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;

/** Only salted player digests and routing metadata are persisted, never usernames or IPs. */
public final class Statistics implements AutoCloseable {
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(10000),Thread.ofPlatform().name("HostRouter-statistics").daemon().factory(),new ThreadPoolExecutor.AbortPolicy());
    private final Configuration config;
    private final Logger logger;
    private Connection db;
    private String salt;
    private volatile boolean available;
    private long lastPrune;
    public Statistics(Path dir,Configuration config,Logger logger) throws Exception {
        this.config=config;this.logger=logger;
        Files.createDirectories(dir); Class.forName("org.sqlite.JDBC");
        db=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("statistics.db"));
        try(Statement s=db.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, player TEXT NOT NULL, network TEXT NOT NULL, hostname TEXT NOT NULL, started INTEGER NOT NULL, duration INTEGER, success INTEGER NOT NULL DEFAULT 0, failure INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE INDEX IF NOT EXISTS session_time ON sessions(started,network)");
            // Interrupted sessions have unknown duration and are excluded from the duration average.
        }
        try(PreparedStatement s=db.prepareStatement("SELECT value FROM metadata WHERE key='salt'");ResultSet r=s.executeQuery()) { if(r.next())salt=r.getString(1); }
        if(salt==null) {
            salt=UUID.randomUUID().toString()+UUID.randomUUID();
            try(PreparedStatement s=db.prepareStatement("INSERT INTO metadata VALUES ('salt',?)")){s.setString(1,salt);s.executeUpdate();}
        }
        available=true;
    }
    public boolean available(){return available;}
    public boolean enabled(Network n){return available && n.statistics() && Configuration.bool(config.get().file("statistics"),"enabled",true);}
    public void join(Session session,UUID uuid) {
        submit(()->{
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((salt+uuid).getBytes(StandardCharsets.UTF_8)));
            try(PreparedStatement s=db.prepareStatement("INSERT INTO sessions (id,player,network,hostname,started) VALUES (?,?,?,?,?)")) {
                s.setString(1,session.id.toString()); s.setString(2,digest); s.setString(3,session.network);s.setString(4,session.original);s.setLong(5,session.started);s.executeUpdate();
            }
            prune();
        });
    }
    public void transfer(Session session,boolean success) {
        submit(()->{try(PreparedStatement s=db.prepareStatement("UPDATE sessions SET "+(success?"success":"failure")+"="+(success?"success":"failure")+"+1 WHERE id=?")){s.setString(1,session.id.toString());s.executeUpdate();}});
    }
    public void end(Session session) {
        long duration=Math.max(0,System.currentTimeMillis()-session.started);
        submit(()->{try(PreparedStatement s=db.prepareStatement("UPDATE sessions SET duration=? WHERE id=?")){s.setLong(1,duration);s.setString(2,session.id.toString());s.executeUpdate();}});
    }
    public CompletableFuture<String> report(String network,String period) {
        CompletableFuture<String> future=new CompletableFuture<>();
        try { worker.execute(()->{
            try {
                long since=switch(period){case "daily"->LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();case "weekly"->Instant.now().minus(Duration.ofDays(7)).toEpochMilli();case "monthly"->Instant.now().minus(Duration.ofDays(30)).toEpochMilli();default->0;};
                String filter=" WHERE started>=?"+(network.isEmpty()?"":" AND network=?");
                StringBuilder out=new StringBuilder("Statistics "+(network.isEmpty()?"all networks":network)+" / "+period+" (UTC)\n");
                try(PreparedStatement s=db.prepareStatement("SELECT COUNT(*),COUNT(DISTINCT player),AVG(duration),SUM(success),SUM(failure) FROM sessions"+filter)) {
                    s.setLong(1,since);if(!network.isEmpty())s.setString(2,network);
                    try(ResultSet r=s.executeQuery()){if(r.next())out.append("Connections: ").append(r.getLong(1)).append(" | Unique: ").append(r.getLong(2)).append(" | Average completed session: ").append(r.getLong(3)/1000).append("s | Transfers OK/failed: ").append(r.getLong(4)).append('/').append(r.getLong(5));}
                }
                for(String column:List.of("network","hostname"))try(PreparedStatement s=db.prepareStatement("SELECT "+column+",COUNT(*) FROM sessions"+filter+" GROUP BY "+column+" ORDER BY COUNT(*) DESC LIMIT 20")) {
                    s.setLong(1,since);if(!network.isEmpty())s.setString(2,network);
                    try(ResultSet r=s.executeQuery()){out.append("\nTop ").append(column).append(":");while(r.next())out.append("\n  ").append(r.getString(1)).append(": ").append(r.getLong(2));}
                }
                future.complete(out.toString());
            }catch(Exception e){future.completeExceptionally(e);}
        }); }catch(RejectedExecutionException e){future.completeExceptionally(e);}
        return future;
    }
    private void prune() throws SQLException {
        long now=System.currentTimeMillis();if(now-lastPrune<3_600_000)return;
        try(PreparedStatement s=db.prepareStatement("DELETE FROM sessions WHERE started<?")){s.setLong(1,now-Duration.ofDays(Configuration.number(config.get().file("statistics"),"retention-days",365)).toMillis());s.executeUpdate();}lastPrune=now;
    }
    @FunctionalInterface private interface Work {void run()throws Exception;}
    private void submit(Work work) {
        try {worker.execute(()->{try{work.run();}catch(Exception e){available=false;logger.error("HostRouter statistics write failed; routing continues",e);}});}
        catch(RejectedExecutionException e){logger.warn("HostRouter statistics queue full; record dropped");}
    }
    public void close() {
        worker.shutdown();try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))worker.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();worker.shutdownNow();}
        try{db.close();}catch(SQLException e){logger.warn("Could not close HostRouter statistics database");}
    }
}
