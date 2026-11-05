package com.peakmc.hostrouter;

import java.net.IDN;
import java.util.*;

/** Hostnames are untrusted routing hints, never authentication credentials. */
public final class HostnameMatcher {
    public record Match(String original, String normalized, Network network, String identifier, String rule,
                        boolean affiliate, String error) { public boolean matched() { return network != null; } }
    public static String normalize(String original) {
        if (original == null || original.isEmpty() || original.length() > 1024 || !original.equals(original.trim()))
            throw new IllegalArgumentException("Missing or invalid hostname");
        String host = original;
        int colon = host.indexOf(':');
        if (colon >= 0) {
            if (colon != host.lastIndexOf(':') || !host.substring(colon + 1).matches("[0-9]{1,5}"))
                throw new IllegalArgumentException("Invalid hostname port");
            int port = Integer.parseInt(host.substring(colon + 1));
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty()) throw new IllegalArgumentException("Empty hostname");
        host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (host.length() > 253 || !host.contains(".")) throw new IllegalArgumentException("Use a DNS hostname");
        for (String label : host.split("\\.", -1))
            if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw new IllegalArgumentException("Invalid DNS label");
        return host;
    }
    public Match match(String original, Collection<Network> networks) {
        String host;
        try { host = normalize(original); }
        catch (IllegalArgumentException e) { return new Match(original, "", null, "", "invalid", false, e.getMessage()); }
        record Candidate(Network n, String id, String rule, boolean affiliate, int specificity) {}
        List<Candidate> hits = new ArrayList<>();
        List<String> labels = Arrays.asList(host.split("\\."));
        for (Network n : networks) {
            if (n.aliases().contains(host)) { hits.add(new Candidate(n, host, "exact-alias", false, 2)); continue; }
            for (String id : n.identifiers()) {
                if (id.contains(".")) {
                    if (host.equals(id) || host.endsWith("." + id)) {
                        hits.add(new Candidate(n, id, "domain-suffix", !host.equals(id), 1));
                    }
                } else if (labels.contains(id)) {
                    int index = labels.indexOf(id);
                    hits.add(new Candidate(n, id, "whole-label", index > 0 || labels.size() > 2, 0));
                }
            }
        }
        hits.sort(Comparator.<Candidate>comparingInt(Candidate::specificity).reversed()
                .thenComparing(Comparator.comparingInt((Candidate c) -> c.n().priority()).reversed()));
        if (hits.isEmpty()) return new Match(original, host, null, "", "none", false, "Unknown hostname");
        Set<String> seen = new HashSet<>();
        hits.removeIf(c -> !seen.add(c.n().name()));
        Candidate best = hits.getFirst();
        if (hits.size() > 1 && best.specificity() == hits.get(1).specificity() && best.n().priority() == hits.get(1).n().priority())
            return new Match(original, host, null, "", "ambiguous", false, "Ambiguous hostname; configure an alias or priority");
        return new Match(original, host, best.n(), best.id(), best.rule(), best.affiliate(), "");
    }
}
