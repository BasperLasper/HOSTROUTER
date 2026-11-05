package com.peakmc.hostrouter;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import java.util.Collection;

/** Personal join notices; permission is checked again at delivery time. */
public final class JoinAlerts {
    private JoinAlerts() {}
    public static boolean enabled(Configuration config,Player player) {
        return RouterCommand.permitted(player,"hostrouter.alerts") && preference(config,player,"join-alerts",true);
    }
    private static boolean preference(Configuration config,Player player,String key,boolean fallback) {
        var preferences=Configuration.map(config.get().file("privacy").get("preferences"));
        return Configuration.bool(Configuration.map(preferences.get(player.getUniqueId().toString())),key,fallback);
    }
    public static void announce(Configuration config,Collection<Player> viewers,Player joined,Session session) {
        for(Player viewer:viewers) {
            if(!viewer.isActive() || !enabled(config,viewer))continue;
            String text="[HostRouter] "+joined.getUsername()+" joined "+session.network+" ("+session.backend+")";
            if(viewer.hasPermission("hostrouter.info.hostname") && preference(config,viewer,"hostname",true))text+=" via "+session.original;
            viewer.sendMessage(Component.text(text));
        }
    }
}
