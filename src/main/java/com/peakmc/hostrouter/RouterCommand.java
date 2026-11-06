package com.peakmc.hostrouter;

import com.velocitypowered.api.command.*;
import com.velocitypowered.api.proxy.*;
import net.kyori.adventure.text.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public final class RouterCommand implements SimpleCommand {
    private final HostRouter p;
    private static final List<String> ROOTS=List.of("help","version","status","reload","backup","servers","list","network","hostname","info","debug","test","maintenance","stats","webhook","toggle","alerts");
    private static final List<String> FIELDS=List.of("identifier","initial-server","destination","backup-server","motd-source","player-count-mode","max-players","priority","require-authentication","statistics","discord","maintenance-message");
    public RouterCommand(HostRouter p){this.p=p;}
    public static boolean permitted(CommandSource s,String node) {return s.hasPermission(node) || (!node.equals("hostrouter.info.ip") && !node.equals("hostrouter.info.hostname") && s.hasPermission("hostrouter.admin"));}
    private String permission(String[] a) {
        if(a.length==0)return "hostrouter.status";
        return switch(a[0].toLowerCase(Locale.ROOT)) {
            case "help","version","status","servers"->"hostrouter.status";
            case "list"->"hostrouter.network.list";
            case "network"->"hostrouter.network."+(a.length<2?"list":switch(a[1]){case "list","info","aliases"->"list";case "create"->"create";case "remove"->"remove";default->"edit";});
            case "hostname"->"hostrouter.hostname.manage";
            case "info"->"hostrouter.info";
            case "debug","test"->"hostrouter.debug";
            case "webhook"->"hostrouter.webhook.manage";
            case "toggle"->a.length>1 && a[1].equals("alerts")?"hostrouter.alerts":"hostrouter.toggle."+(a.length>1?a[1]:"hostname");
            default->"hostrouter."+a[0].toLowerCase(Locale.ROOT);
        };
    }
    @Override public void execute(Invocation invocation) {
        String[] args=invocation.arguments();CommandSource source=invocation.source();
        if(!permitted(source,permission(args))){say(source,"You do not have permission.");return;}
        try {p.administration.execute(()->{
            if(!permitted(source,permission(args))){say(source,"You do not have permission.");return;}
            try{run(source,args);}catch(IllegalArgumentException e){say(source,"Error: "+e.getMessage());}
            catch(Exception e){say(source,"Operation failed; the active configuration was retained. Check console.");p.logger.warn("HostRouter administrative operation failed (details withheld to protect secrets)");}
        });}catch(RejectedExecutionException e){say(source,"HostRouter is busy; try again shortly.");}
    }
    private void run(CommandSource s,String[] a)throws Exception {
        String root=a.length==0?"help":a[0].toLowerCase(Locale.ROOT);
        switch(root) {
            case "help"->help(s);
            case "version"->say(s,"HostRouter 1.0.6 • Velocity 4.2.0 • Java 25");
            case "status"->say(s,"HostRouter: "+p.config.get().networks().size()+" networks; "+p.sessions.size()+" assigned sessions\n"+p.auth.status()+"\nStatistics: "+(p.stats!=null && p.stats.available()?"available":"unavailable")+"; webhook drops: "+p.webhooks.dropped());
            case "servers"->{for(var server:p.proxy.getAllServers())say(s,server.getServerInfo().getName()+": "+p.monitor.status(server.getServerInfo().getName())+"; players="+server.getPlayersConnected().size());}
            case "list"->networks(s);
            case "reload"->{p.config.load();p.validateBackends();p.monitor.refresh();changed(s,"Configuration reloaded");}
            case "backup"->{p.config.backup();say(s,"Configuration backup created in plugins/hostrouter/backups (database excluded).");}
            case "network"->network(s,a);
            case "hostname"->hostname(s,a);
            case "debug"->{
                if(a.length==1){p.config.edit("config",m->m.put("debug",!p.config.get().debug()));changed(s,"Console debug logging: "+p.config.get().debug());}
                else if(a[1].equals("player")){need(a,3,"/hr debug player <player>");player(s,a[2]);}
                else debug(s,a[1]);
            }
            case "test"->{need(a,2,"/hr test <hostname>");debug(s,a[1]);}
            case "info"->{need(a,2,"/hr info <player>");player(s,a[1]);}
            case "maintenance"->{need(a,3,"/hr maintenance <network> on|off");get(a[1]);if(!Set.of("on","off").contains(a[2]))throw new IllegalArgumentException("Use on or off");editNetwork(a[1],m->m.put("maintenance",a[2].equals("on")));changed(s,"Maintenance "+a[2]+" for "+a[1]);}
            case "alerts"->alerts(s,a.length>1?a[1]:"toggle");
            case "toggle"->{if(a.length>1 && a[1].equals("alerts"))alerts(s,"toggle");else toggle(s,a);}
            case "webhook"->webhook(s,a);
            case "stats"->{
                String network=a.length>1?a[1]:"";if(!network.isEmpty())get(network);
                String period=a.length>2?a[2]:"all";if(!Set.of("all","daily","weekly","monthly").contains(period))throw new IllegalArgumentException("Use daily, weekly or monthly");
                if(p.stats==null || !p.stats.available()){say(s,"Statistics database unavailable.");return;}
                p.stats.report(network,period).whenComplete((report,error)->{if(error==null)say(s,report);else say(s,"Statistics query failed; see database availability in /hr status.");});
            }
            default->throw new IllegalArgumentException("Unknown command. Use /hr help");
        }
    }
    private void network(CommandSource s,String[] a)throws Exception {
        if(a.length<2 || a[1].equals("list")){networks(s);return;}
        need(a,3,"/hr network "+a[1]+" <name>");String name=a[2];
        if(a[1].equals("create")) {
            need(a,6,"/hr network create <name> <identifier> <initial-server> <destination>");
            if(p.config.get().networks().containsKey(name))throw new IllegalArgumentException("Network already exists");
            registered(a[4]);registered(a[5]);String id=Configuration.identifier(a[3]);
            p.config.edit("networks",root->{Map<String,Object> all=Configuration.map(root.get("networks"));Map<String,Object> n=new LinkedHashMap<>();
                n.put("identifiers",List.of(id));n.put("aliases",List.of());n.put("initial-server",a[4]);n.put("destination-server",a[5]);n.put("motd-source",a[5]);n.put("enabled",true);n.put("require-authentication",true);n.put("max-players",200);n.put("player-count-mode","network");all.put(name,n);root.put("networks",all);});
            changed(s,"Network created: "+name);p.validateBackends();return;
        }
        Network n=get(name);
        switch(a[1]) {
            case "remove"->{if(p.sessions.values().stream().anyMatch(session->session.network.equals(name)))throw new IllegalArgumentException("Disable this network and wait for its players to disconnect before removal");p.config.edit("networks",root->{var all=Configuration.map(root.get("networks"));all.remove(name);root.put("networks",all);});changed(s,"Network removed: "+name);}
            case "info"->say(s,describe(n));
            case "aliases"->say(s,name+" exact aliases: "+n.aliases()+"\nUse /hr hostname alias-add|alias-remove <network> <hostname>");
            case "enable","disable"->{editNetwork(name,m->m.put("enabled",a[1].equals("enable")));changed(s,name+" "+a[1]+"d");}
            case "set"->{
                need(a,5,"/hr network set <name> <field> <value>");String field=a[3];if(!FIELDS.contains(field))throw new IllegalArgumentException("Fields: "+String.join(", ",FIELDS));
                Object value;
                switch(field) {
                    case "identifier"->value=List.of(Configuration.identifier(a[4]));
                    case "max-players","priority"->{try{value=Integer.valueOf(a[4]);}catch(NumberFormatException e){throw new IllegalArgumentException("Use an integer");}}
                    case "require-authentication","statistics","discord"->{if(!Set.of("true","false").contains(a[4]))throw new IllegalArgumentException("Use true or false");value=Boolean.valueOf(a[4]);}
                    case "initial-server","destination","motd-source","backup-server"->{if(!(field.equals("backup-server") && a[4].equals("none")))registered(a[4]);value=a[4].equals("none")?"":a[4];}
                    case "maintenance-message"->value=String.join(" ",Arrays.copyOfRange(a,4,a.length));
                    default->value=a[4];
                }
                String key=field.equals("identifier")?"identifiers":field.equals("destination")?"destination-server":field;
                editNetwork(name,m->m.put(key,value));changed(s,"Updated "+field+" for "+name);p.validateBackends();
            }
            default->throw new IllegalArgumentException("Use network list, create, remove, info, enable, disable, set or aliases");
        }
    }
    private void hostname(CommandSource s,String[] a)throws Exception {
        if(a.length<2 || a[1].equals("list")){for(Network n:p.config.get().networks().values())say(s,n.name()+": identifiers="+n.identifiers()+"; aliases="+n.aliases()+"; priority="+n.priority());return;}
        if(a[1].equals("test")){need(a,3,"/hr hostname test <hostname>");debug(s,a[2]);return;}
        need(a,4,"/hr hostname add|remove|alias-add|alias-remove <network> <identifier/hostname>");
        Network n=get(a[2]);boolean alias=a[1].startsWith("alias-");boolean add=a[1].equals("add") || a[1].equals("alias-add");
        if(!Set.of("add","remove","alias-add","alias-remove").contains(a[1]))throw new IllegalArgumentException("Unknown hostname action");
        String value=alias?HostnameMatcher.normalize(a[3]):Configuration.identifier(a[3]);
        List<String> values=new ArrayList<>(alias?n.aliases():n.identifiers());
        if(add){if(values.contains(value))throw new IllegalArgumentException("Rule already exists");values.add(value);}else if(!values.remove(value))throw new IllegalArgumentException("Rule does not exist");
        editNetwork(n.name(),m->m.put(alias?"aliases":"identifiers",values));changed(s,"Hostname rule "+(add?"added":"removed")+" for "+n.name());
    }
    private void webhook(CommandSource s,String[] a)throws Exception {
        var hooks=Configuration.map(p.config.get().file("webhooks").get("webhooks"));
        if(a.length<2 || a[1].equals("list")){say(s,"Webhooks: "+hooks.keySet()+" (URLs hidden)\nEvent routes: "+Configuration.map(p.config.get().file("webhooks").get("events")).entrySet().stream().map(e->e.getKey()+"="+Configuration.list(Configuration.map(e.getValue()).get("webhooks"))).toList());return;}
        need(a,3,"/hr webhook add|remove|test|bind|bindall <name> [url/event]");String name=a[2];
        switch(a[1]) {
            case "add"->{need(a,4,"/hr webhook add <name> <url>");Webhooks.validateUrl(a[3]);if(hooks.containsKey(name))throw new IllegalArgumentException("Webhook name already exists");p.config.edit("webhooks",m->{var h=Configuration.map(m.get("webhooks"));h.put(name,a[3]);m.put("webhooks",h);});changed(s,"Webhook added: "+name+". Bind it with /hr webhook bind "+name+" <event>");}
            case "remove"->{if(!hooks.containsKey(name))throw new IllegalArgumentException("Unknown webhook");p.config.edit("webhooks",m->{var h=Configuration.map(m.get("webhooks"));h.remove(name);m.put("webhooks",h);var events=Configuration.map(m.get("events"));events.replaceAll((key,value)->{var rule=Configuration.map(value);List<String> bound=new ArrayList<>(Configuration.list(rule.get("webhooks")));bound.remove(name);rule.put("webhooks",bound);return rule;});m.put("events",events);});changed(s,"Webhook removed: "+name);}
            case "test"->{p.webhooks.test(name);say(s,"Webhook test queued. Check its channel for delivery.");}
            case "bindall"->{
                if(!hooks.containsKey(name))throw new IllegalArgumentException("Unknown webhook");
                p.config.edit("webhooks",m->{
                    var events=Configuration.map(m.get("events"));
                    events.replaceAll((event,value)->{
                        var rule=Configuration.map(value);
                        List<String> bound=new ArrayList<>(Configuration.list(rule.get("webhooks")));
                        if(!bound.contains(name))bound.add(name);
                        rule.put("webhooks",bound);return rule;
                    });
                    m.put("events",events);
                });
                changed(s,"Webhook "+name+" bound to all events. Event enabled/disabled settings are preserved");
            }
            case "bind","unbind"->{need(a,4,"/hr webhook bind|unbind <name> <event>");if(!hooks.containsKey(name))throw new IllegalArgumentException("Unknown webhook");var events=Configuration.map(p.config.get().file("webhooks").get("events"));if(!events.containsKey(a[3]))throw new IllegalArgumentException("Events: "+events.keySet());p.config.edit("webhooks",m->{var ev=Configuration.map(m.get("events"));var rule=Configuration.map(ev.get(a[3]));List<String> bound=new ArrayList<>(Configuration.list(rule.get("webhooks")));bound.remove(name);if(a[1].equals("bind"))bound.add(name);rule.put("webhooks",bound);ev.put(a[3],rule);m.put("events",ev);});changed(s,"Webhook event routing updated");}
            default->throw new IllegalArgumentException("Use webhook list, add, remove, test, bind, bindall or unbind");
        }
    }
    private void debug(CommandSource s,String original) {
        var m=p.match(original);String base="HostRouter Debug Report\nOriginal: "+original+"\nNormalised: "+m.normalized()+"\nRule: "+m.rule()+"; identifier="+m.identifier()+"; affiliate/subdomain="+m.affiliate();
        if(!m.matched()){say(s,base+"\nNo network: "+m.error());return;}
        Network n=m.network();say(s,base+"\n"+describe(n)+"\nAuthentication: "+p.auth.status()+"\nRouting: "+(!n.enabled()?"DISABLED":n.maintenance()?"MAINTENANCE":!p.monitor.usable(n.destination())?"DESTINATION UNAVAILABLE":n.requireAuth() && (!p.auth.status().equals("PeakLogin public API available") || !p.auth.loginServer().isBlank() && !p.auth.loginServer().equals(n.initial()))?"AUTH UNAVAILABLE OR LOGIN SERVER MISMATCH":"READY"));
    }
    private void player(CommandSource viewer,String name) {
        Player target=p.proxy.getPlayer(name).orElseThrow(()->new IllegalArgumentException("Player is not online"));
        Session session=p.sessions.get(target.getUniqueId());if(session==null)throw new IllegalArgumentException("Player has no HostRouter session");
        Network n=p.config.get().networks().get(session.network);
        String report="Player: "+target.getUsername()+"\nNetwork: "+session.network+"; backend="+session.backend+"; destination="+(n==null?"removed":n.destination())+"\nAuthenticated: "+p.auth.authenticated(target)+" ("+p.auth.status()+")\nLast routing action: "+session.action+"\nConnected: "+Instant.ofEpochMilli(session.started)+"; duration="+(System.currentTimeMillis()-session.started)/1000+"s";
        if(viewer.hasPermission("hostrouter.info.hostname") && preference(viewer,"hostname",true))report+="\nOriginal hostname: "+session.original+"\nNormalised: "+session.normalized;
        else report+="\nHostname: hidden";
        if(viewer.hasPermission("hostrouter.info.ip") && preference(viewer,"ip",false))report+="\nIP: "+target.getRemoteAddress().getAddress().getHostAddress();
        else report+="\nIP: hidden";
        say(viewer,report);
    }
    private String viewerId(CommandSource source){return source instanceof Player player?player.getUniqueId().toString():"console";}
    private boolean preference(CommandSource source,String key,boolean fallback){var prefs=Configuration.map(p.config.get().file("privacy").get("preferences"));return Configuration.bool(Configuration.map(prefs.get(viewerId(source))),key,fallback);}
    private void toggle(CommandSource source,String[] a)throws Exception {
        need(a,2,"/hr toggle hostname|ip");if(!Set.of("hostname","ip").contains(a[1]))throw new IllegalArgumentException("Use hostname or ip");
        boolean next=!preference(source,a[1],a[1].equals("hostname"));
        p.config.edit("privacy",m->{var prefs=Configuration.map(m.get("preferences"));var mine=Configuration.map(prefs.get(viewerId(source)));mine.put(a[1],next);prefs.put(viewerId(source),mine);m.put("preferences",prefs);});say(source,a[1]+" visibility "+(next?"enabled":"disabled")+". The corresponding info permission is still required.");
    }
    private void alerts(CommandSource source,String mode)throws Exception {
        if(!(source instanceof Player viewer))throw new IllegalArgumentException("This command is for players");
        if(!Set.of("toggle","on","off").contains(mode))throw new IllegalArgumentException("Usage: /hr alerts [on|off]");
        boolean next=mode.equals("toggle")?!JoinAlerts.enabled(p.config,viewer):mode.equals("on");
        p.config.edit("privacy",m->{
            var prefs=Configuration.map(m.get("preferences"));var mine=Configuration.map(prefs.get(viewerId(source)));
            mine.put("join-alerts",next);prefs.put(viewerId(source),mine);m.put("preferences",prefs);
        });
        say(source,"Join alerts "+(next?"enabled":"disabled")+". Preference saved.");
    }
    private void help(CommandSource s) {
        String[][] lines={{"alerts","/hr alerts [on|off] (personal join alerts)"},{"status","/hr help | version | status | servers"},{"reload","/hr reload"},{"backup","/hr backup"},{"network","/hr list | network list|info|aliases <name>"},{"network","/hr network create <name> <identifier> <initial-server> <destination>"},{"network","/hr network remove|enable|disable <name>"},{"network","/hr network set <name> <field> <value>"},{"hostname","/hr hostname list|test <hostname> | add|remove|alias-add|alias-remove <network> <rule>"},{"info","/hr info <player>"},{"debug","/hr debug [hostname] | debug player <player> | test <hostname>"},{"maintenance","/hr maintenance <network> on|off"},{"stats","/hr stats [network] [daily|weekly|monthly]"},{"webhook","/hr webhook list|add|remove|test|bind|bindall|unbind <name> [url/event]"},{"toggle","/hr toggle hostname|ip"}};
        for(var line:lines)if(permitted(s,permission(new String[]{line[0]})))say(s,line[1]);
    }
    private String describe(Network n){return "Network: "+n.name()+"; enabled="+n.enabled()+"; maintenance="+n.maintenance()+"\nIdentifiers: "+n.identifiers()+"; aliases="+n.aliases()+"; priority="+n.priority()+"\nInitial: "+n.initial()+"; destination="+n.destination()+"; backup="+n.backup()+"\nMOTD source: "+n.motd()+" ("+p.monitor.status(n.motd())+")\nCount: "+n.countMode()+"; online="+p.monitor.count(n)+"; maximum="+(n.countMode().equals("global")?p.config.get().integer("global-max-players",500):n.maxPlayers())+"\nRequires authentication: "+n.requireAuth();}
    private void networks(CommandSource s){for(Network n:p.config.get().networks().values())say(s,n.name()+": "+(n.enabled()?"enabled":"disabled")+"; "+n.initial()+" → "+n.destination()+"; count="+p.monitor.count(n));}
    private Network get(String name){Network n=p.config.get().networks().get(name);if(n==null)throw new IllegalArgumentException("Unknown network: "+name);return n;}
    private void registered(String name){if(p.proxy.getServer(name).isEmpty())throw new IllegalArgumentException("Backend is not registered in velocity.toml: "+name);}
    private void editNetwork(String name,Consumer<Map<String,Object>> update)throws Exception{p.config.edit("networks",m->{var all=Configuration.map(m.get("networks"));var n=Configuration.map(all.get(name));update.accept(n);all.put(name,n);m.put("networks",all);});}
    private void changed(CommandSource s,String text){say(s,text+". Saved immediately.");p.webhooks.emit("administration",text+"; actor="+(s instanceof Player player?player.getUsername():"console"));}
    private static void need(String[] a,int count,String usage){if(a.length<count)throw new IllegalArgumentException("Usage: "+usage);}
    private static void say(CommandSource s,String text){s.sendMessage(Component.text(text));}
    @Override public List<String> suggest(Invocation invocation) {
        String[] a=invocation.arguments();CommandSource source=invocation.source();
        if(a.length<=1){String prefix=a.length==0?"":a[0];return ROOTS.stream().filter(v->v.startsWith(prefix) && permitted(source,permission(new String[]{v}))).toList();}
        if(!permitted(source,permission(a)))return List.of();
        List<String> options=new ArrayList<>();String root=a[0];int pos=a.length-1;
        if(pos==1)switch(root) {
            case "network"->options.addAll(List.of("list","create","remove","info","enable","disable","set","aliases"));
            case "hostname"->options.addAll(List.of("list","add","remove","test","alias-add","alias-remove"));
            case "webhook"->options.addAll(List.of("list","add","remove","test","bind","bindall","unbind"));
            case "toggle"->options.addAll(List.of("hostname","ip","alerts"));
            case "alerts"->options.addAll(List.of("on","off"));
            case "maintenance","stats"->options.addAll(p.config.get().networks().keySet());
            case "info"->p.proxy.getAllPlayers().forEach(player->options.add(player.getUsername()));
            case "debug"->options.add("player");
        }
        else if(pos==2)switch(root) {
            case "network","hostname"->{if(!Set.of("create","list","test").contains(a[1]))options.addAll(p.config.get().networks().keySet());}
            case "maintenance"->options.addAll(List.of("on","off"));
            case "stats"->options.addAll(List.of("daily","weekly","monthly"));
            case "debug"->{if(a[1].equals("player"))p.proxy.getAllPlayers().forEach(player->options.add(player.getUsername()));}
            case "webhook"->{if(!a[1].equals("add"))options.addAll(Configuration.map(p.config.get().file("webhooks").get("webhooks")).keySet());}
        }
        else if(root.equals("network") && a[1].equals("set") && pos==3)options.addAll(FIELDS);
        else if(root.equals("network") && a[1].equals("set") && pos==4) {
            if(List.of("initial-server","destination","backup-server","motd-source").contains(a[3]))p.proxy.getAllServers().forEach(server->options.add(server.getServerInfo().getName()));
            else if(a[3].equals("player-count-mode"))options.addAll(List.of("global","network","backend"));
            else if(List.of("require-authentication","statistics","discord").contains(a[3]))options.addAll(List.of("true","false"));
        }
        else if(root.equals("network") && a[1].equals("create") && (pos==4 || pos==5))p.proxy.getAllServers().forEach(server->options.add(server.getServerInfo().getName()));
        else if(root.equals("hostname") && pos==3 && (a[1].equals("remove") || a[1].equals("alias-remove"))) {Network n=p.config.get().networks().get(a[2]);if(n!=null)options.addAll(a[1].equals("remove")?n.identifiers():n.aliases());}
        else if(root.equals("webhook") && pos==3 && Set.of("bind","unbind").contains(a[1]))options.addAll(Configuration.map(p.config.get().file("webhooks").get("events")).keySet());
        String prefix=a[pos].toLowerCase(Locale.ROOT);
        return options.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).filter(v->{String[] candidate=a.clone();candidate[pos]=v;return permitted(source,permission(candidate));}).sorted().toList();
    }
}
