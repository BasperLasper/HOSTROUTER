package com.peakmc.hostrouter;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;

/** Bounded, serial async delivery; URLs and HTTP errors containing them are never logged. */
public final class Webhooks implements AutoCloseable {
    private record Delivery(String url,String body,int attempts) {}
    private final Configuration config;
    private final Logger logger;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("HostRouter-webhooks").daemon().factory());
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ArrayDeque<Delivery> queue=new ArrayDeque<>();
    private boolean busy;
    private volatile boolean closed;
    private long dropped;
    public Webhooks(Configuration config,Logger logger) { this.config=config; this.logger=logger; }
    public static URI validateUrl(String url) {
        try {
            URI u=URI.create(url);
            if(!"https".equals(u.getScheme()) || !Set.of("discord.com","canary.discord.com","ptb.discord.com").contains(u.getHost()) ||
                    u.getPort()!=-1 || u.getRawQuery()!=null || u.getFragment()!=null || u.getUserInfo()!=null ||
                    !u.getPath().matches("/api(?:/v[0-9]+)?/webhooks/[0-9]{10,24}/[A-Za-z0-9_-]{20,200}")) throw new IllegalArgumentException();
            return u;
        } catch (RuntimeException e) { throw new IllegalArgumentException("Use a valid HTTPS Discord webhook URL"); }
    }
    public void emit(String event,String text) {
        var cfg=config.get().file("webhooks");
        var rules=Configuration.map(Configuration.map(cfg.get("events")).get(event));
        if(!Configuration.bool(rules,"enabled",false)) return;
        var hooks=Configuration.map(cfg.get("webhooks"));
        String body=body(Configuration.string(rules,"title",event), text, Configuration.number(rules,"colour",3447003));
        for(String name:Configuration.list(rules.get("webhooks"))) {
            Object url=hooks.get(name); if(url!=null) enqueue(String.valueOf(url),body);
        }
    }
    public void test(String name) {
        Object url=Configuration.map(config.get().file("webhooks").get("webhooks")).get(name);
        if(url==null) throw new IllegalArgumentException("Unknown webhook");
        enqueue(String.valueOf(url),body("HostRouter test","Webhook delivery test",3447003));
    }
    private synchronized void enqueue(String url,String body) {
        if(closed)return;
        if(queue.size()>=Configuration.number(config.get().file("webhooks"),"queue-capacity",500)) {
            dropped++; if(dropped==1 || dropped%100==0)logger.warn("HostRouter webhook queue full; {} deliveries dropped",dropped); return;
        }
        queue.add(new Delivery(url,body,0)); if(!busy){busy=true;worker.execute(this::sendNext);}
    }
    private void sendNext() {
        Delivery d;
        synchronized(this) { if(closed)return; d=queue.poll(); if(d==null){busy=false;return;} }
        send(d);
    }
    private void send(Delivery d) {
        if(closed)return;
        HttpRequest request=HttpRequest.newBuilder(validateUrl(d.url())).timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(d.body())).build();
        http.sendAsync(request,HttpResponse.BodyHandlers.discarding()).whenComplete((response,error)-> {
            if(closed)return;
            int code=response==null?0:response.statusCode();
            if((error!=null || code==429 || code>=500) && d.attempts()<3) {
                long delay=Math.min(60,1L<<d.attempts());
                if(response!=null) {
                    String retry=response.headers().firstValue("Retry-After").orElse(response.headers().firstValue("X-RateLimit-Reset-After").orElse(""));
                    try { delay=Math.max(delay,Math.min(300,(long)Math.ceil(Double.parseDouble(retry)))); }catch(NumberFormatException ignored){}
                }
                schedule(()->send(new Delivery(d.url(),d.body(),d.attempts()+1)),delay*1000);
            } else {
                if(error!=null || code<200 || code>=300) logger.warn("HostRouter webhook delivery failed (HTTP {}, details withheld)",code);
                schedule(this::sendNext,600);
            }
        });
    }
    private void schedule(Runnable action,long ms) { try { worker.schedule(action,ms,TimeUnit.MILLISECONDS); }catch(RejectedExecutionException ignored){} }
    private static String body(String title,String text,int colour) {
        return "{\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{\"title\":"+json(title.substring(0,Math.min(256,title.length())))+",\"description\":"+json(text.substring(0,Math.min(4000,text.length())))+",\"color\":"+Math.max(0,Math.min(16777215,colour))+",\"timestamp\":"+json(Instant.now().toString())+"}]}";
    }
    static String json(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(char c:s.toCharArray()) switch(c) { case '"'->b.append("\\\""); case '\\'->b.append("\\\\"); case '\n'->b.append("\\n"); case '\r'->b.append("\\r"); case '\t'->b.append("\\t"); default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);} }
        return b.append('"').toString();
    }
    public synchronized long dropped() { return dropped; }
    public synchronized void close() { closed=true;queue.clear();worker.shutdownNow();http.close(); }
}
