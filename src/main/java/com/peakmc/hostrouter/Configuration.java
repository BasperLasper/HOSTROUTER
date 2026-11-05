package com.peakmc.hostrouter;

import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** All disk writes and reloads run on HostRouter's serial administration executor. */
public final class Configuration {
    public static final List<String> FILES = List.of("config.yml", "networks.yml", "webhooks.yml", "privacy.yml", "statistics.yml", "messages.yml");
    public record Snapshot(Map<String, Map<String,Object>> files, Map<String,Network> networks) {
        public Map<String,Object> file(String name) { return files.get(name + ".yml"); }
        public int integer(String key, int fallback) { return number(file("config"), key, fallback); }
        public boolean debug() { return bool(file("config"), "debug", false); }
        public String message(String key) { return string(file("messages"), key, key); }
    }
    private final Path directory;
    private volatile Snapshot current;
    public Configuration(Path directory) { this.directory = directory; }
    public Snapshot get() { return current; }
    private Yaml yaml() {
        LoaderOptions o = new LoaderOptions(); o.setAllowDuplicateKeys(false); o.setMaxAliasesForCollections(20); o.setCodePointLimit(1_000_000);
        return new Yaml(new SafeConstructor(o));
    }
    public synchronized void load() throws IOException {
        Files.createDirectories(directory);
        Map<String,Map<String,Object>> files = new LinkedHashMap<>();
        for (String name : FILES) {
            Path p = directory.resolve(name);
            if (!Files.exists(p)) {
                try (InputStream in = getClass().getResourceAsStream("/" + name)) {
                    if (in == null) throw new IOException("Missing bundled " + name);
                    Files.copy(in, p);
                }
            }
            try (Reader r = Files.newBufferedReader(p)) { files.put(name, map(yaml().load(r))); }
            catch (RuntimeException e) { throw new IOException("Invalid " + name + "; check YAML syntax and mapping structure (source values withheld)"); }
        }
        try { current = validate(files); }
        catch (RuntimeException e) { throw new IOException("Configuration rejected: " + e.getMessage()); }
    }
    public synchronized void edit(String file, Consumer<Map<String,Object>> change) throws IOException {
        Map<String,Map<String,Object>> files = new LinkedHashMap<>(current.files());
        Map<String,Object> changed = map(yaml().load(yaml().dump(files.get(file + ".yml"))));
        change.accept(changed); files.put(file + ".yml", changed);
        Snapshot next = validate(files);
        backup();
        atomic(directory.resolve(file + ".yml"), yaml().dump(changed));
        current = next;
    }
    public synchronized Path backup() throws IOException {
        Path out = directory.resolve("backups").resolve(System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0,8));
        Files.createDirectories(out);
        for (String file : FILES) if (Files.exists(directory.resolve(file))) Files.copy(directory.resolve(file), out.resolve(file));
        // Retain the newest 30 snapshots to bound disk use.
        try (var dirs = Files.list(directory.resolve("backups"))) {
            List<Path> old = dirs.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).skip(30).toList();
            for (Path p : old) { for (String file : FILES) Files.deleteIfExists(p.resolve(file)); Files.deleteIfExists(p); }
        }
        return out;
    }
    private void atomic(Path target, String content) throws IOException {
        Path tmp = Files.createTempFile(directory, ".hostrouter-", ".tmp");
        try {
            Files.writeString(tmp, content);
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { throw new IOException("Filesystem does not support atomic configuration replacement", e); }
        } finally { Files.deleteIfExists(tmp); }
    }
    private Snapshot validate(Map<String,Map<String,Object>> files) {
        Map<String,Object> settings = files.get("config.yml");
        bool(settings,"debug",false);
        bool(files.get("statistics.yml"),"enabled",true);
        for(Object pref:map(files.get("privacy.yml").get("preferences")).values()){bool(map(pref),"ip",false);bool(map(pref),"hostname",true);bool(map(pref),"join-alerts",true);}
        for (String k : List.of("ping-refresh-seconds", "ping-stale-seconds", "ping-timeout-seconds", "health-failure-threshold", "count-refresh-seconds", "auth-check-milliseconds", "global-max-players")) {
            int v = number(settings,k,1); if (v < 1 || v > 1_000_000) throw new IllegalArgumentException(k + " must be between 1 and 1000000");
        }
        if (number(settings,"auth-check-milliseconds",500) < 100) throw new IllegalArgumentException("auth-check-milliseconds must be >= 100");
        Map<String,Network> networks = new LinkedHashMap<>();
        Map<String,Object> raw = map(files.get("networks.yml").get("networks"));
        if (raw.isEmpty() || raw.size() > 1000) throw new IllegalArgumentException("Register 1–1000 networks");
        for (var entry : raw.entrySet()) {
            String name = entry.getKey(); if (!name.matches("[a-z0-9_-]{1,48}")) throw new IllegalArgumentException("Invalid network name");
            Map<String,Object> m = map(entry.getValue());
            List<String> ids = list(m.get("identifiers")).stream().map(Configuration::identifier).toList();
            List<String> aliases = list(m.get("aliases")).stream().map(HostnameMatcher::normalize).toList();
            if (ids.size() + aliases.size() == 0 || ids.size()+aliases.size()>256) throw new IllegalArgumentException("Network needs 1–256 matching rules");
            String initial = server(m,"initial-server",""); String dest = server(m,"destination-server",initial);
            String motd = server(m,"motd-source",dest); String backup = server(m,"backup-server","");
            if(initial.isBlank() || dest.isBlank() || motd.isBlank()) throw new IllegalArgumentException("Missing backend for " + name);
            String mode = string(m,"player-count-mode","network").toLowerCase(Locale.ROOT);
            if (!Set.of("global","network","backend").contains(mode)) throw new IllegalArgumentException("Unknown count mode");
            int max = number(m,"max-players",200); if(max<1 || max>1_000_000) throw new IllegalArgumentException("Invalid max-players");
            networks.put(name,new Network(name,ids,aliases,number(m,"priority",0),initial,dest,backup,motd,mode,max,
                    bool(m,"enabled",true),bool(m,"maintenance",false),bool(m,"require-authentication",true),
                    bool(m,"statistics",true),bool(m,"discord",true),string(m,"maintenance-message",string(files.get("messages.yml"),"maintenance","Maintenance"))));
        }
        Map<String,Object> hooks = map(files.get("webhooks.yml").get("webhooks"));
        if (hooks.size() > 100) throw new IllegalArgumentException("Maximum 100 webhooks");
        for (var entry : hooks.entrySet()) {
            if(!entry.getKey().matches("[a-zA-Z0-9_-]{1,48}")) throw new IllegalArgumentException("Invalid webhook name");
            Webhooks.validateUrl(String.valueOf(entry.getValue()));
        }
        int capacity = number(files.get("webhooks.yml"),"queue-capacity",500);
        if(capacity<1 || capacity>10000) throw new IllegalArgumentException("Webhook capacity must be 1–10000");
        for (Object event : map(files.get("webhooks.yml").get("events")).values()) {
            bool(map(event),"enabled",false);
            number(map(event),"colour",3447003);
            for(String hook : list(map(event).get("webhooks"))) if(!hooks.containsKey(hook)) throw new IllegalArgumentException("Event references a missing webhook name");
        }
        int retention = number(files.get("statistics.yml"),"retention-days",365);
        if (retention<1 || retention>3650) throw new IllegalArgumentException("retention-days must be 1–3650");
        Map<String,Map<String,Object>> frozen = new LinkedHashMap<>();
        files.forEach((k,v)-> frozen.put(k,freeze(v)));
        return new Snapshot(Collections.unmodifiableMap(frozen),Collections.unmodifiableMap(networks));
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> freeze(Map<String,Object> m) {
        Map<String,Object> out=new LinkedHashMap<>(); m.forEach((k,v)->out.put(k,v instanceof Map<?,?> ? freeze(map(v)) : v instanceof List<?> l ? List.copyOf(l) : v));
        return Collections.unmodifiableMap(out);
    }
    public static String identifier(String value) {
        String id = value.toLowerCase(Locale.ROOT);
        if(id.contains(".")) return HostnameMatcher.normalize(id);
        if(!id.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw new IllegalArgumentException("Identifier must be a whole DNS label or domain");
        return id;
    }
    private static String server(Map<String,Object> m,String key,String fallback) {
        String s=string(m,key,fallback); if(!s.isEmpty() && !s.matches("[a-zA-Z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid backend name"); return s;
    }
    public static Map<String,Object> map(Object value) {
        if(value == null) return new LinkedHashMap<>();
        if(!(value instanceof Map<?,?> input)) throw new IllegalArgumentException("Expected YAML mapping");
        Map<String,Object> result=new LinkedHashMap<>(); input.forEach((k,v)->{ if(!(k instanceof String)) throw new IllegalArgumentException("Keys must be strings"); result.put((String)k,v); }); return result;
    }
    public static List<String> list(Object o) { if(o==null) return List.of(); if(!(o instanceof List<?> l)) throw new IllegalArgumentException("Expected a list"); return l.stream().map(String::valueOf).toList(); }
    public static String string(Map<String,Object> m,String k,String def) { return String.valueOf(m.getOrDefault(k,def)); }
    public static int number(Map<String,Object> m,String k,int def) { Object v=m.get(k); if(v==null)return def;try{return Integer.parseInt(String.valueOf(v));}catch(NumberFormatException e){throw new IllegalArgumentException(k+" must be an integer");} }
    public static boolean bool(Map<String,Object> m,String k,boolean def) { Object v=m.get(k); if(v==null)return def; if(v instanceof Boolean b)return b; throw new IllegalArgumentException(k+" must be true or false"); }
}
