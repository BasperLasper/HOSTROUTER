package com.peakmc.hostrouter;
import java.util.*;
public record Network(String name, List<String> identifiers, List<String> aliases, int priority,
                      String initial, String destination, String backup, String motd, String countMode,
                      int maxPlayers, boolean enabled, boolean maintenance, boolean requireAuth,
                      boolean statistics, boolean discord, String maintenanceMessage) {
    public Network { identifiers = List.copyOf(identifiers); aliases = List.copyOf(aliases); }
}
