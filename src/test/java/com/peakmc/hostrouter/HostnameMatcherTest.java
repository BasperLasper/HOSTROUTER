package com.peakmc.hostrouter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class HostnameMatcherTest {
    private final HostnameMatcher matcher=new HostnameMatcher();
    static Network network(String name,List<String> ids,List<String> aliases,int priority){return new Network(name,ids,aliases,priority,"lobby","smp","","smp","network",200,true,false,true,true,true,"Maintenance");}
    @Test void normalizesCasePortsIdnAndTrailingDots(){assertEquals("play.peaksmp.us",HostnameMatcher.normalize("PLAY.PeakSMP.US.:25565"));assertEquals("xn--bcher-kva.de",HostnameMatcher.normalize("bücher.de"));}
    @Test void matchesAllRequiredForms(){Network n=network("peaksmp",List.of("peaksmp"),List.of(),0);for(String host:List.of("peaksmp.us","play.peaksmp.us","mc.peaksmp.us","affiliate.peaksmp.us","peaksmp.affiliate-domain.com"))assertEquals(n,matcher.match(host,List.of(n)).network());}
    @Test void rejectsPartialLabels(){Network n=network("peakmc",List.of("peakmc"),List.of(),0);for(String host:List.of("notpeakmc.us","peakmc-fake.com","peaksmp.us"))assertFalse(matcher.match(host,List.of(n)).matched());}
    @Test void rejectsMalformedHostnames(){for(String s:List.of("", "a..com","a.com:0","a.com:65536","a.com:abc","a.com:1:2","a.com\u0000evil","_srv.a.com","-a.com","a.com/path"," a.com","[::1]","a"))assertThrows(IllegalArgumentException.class,()->HostnameMatcher.normalize(s),s);}
    @Test void ambiguityFailsClosed(){var a=network("a",List.of("one"),List.of(),0);var b=network("b",List.of("two"),List.of(),0);assertEquals("ambiguous",matcher.match("one.two.com",List.of(a,b)).rule());}
    @Test void exactAliasWinsOverHigherPriorityIdentifier(){var a=network("a",List.of("one"),List.of(),100);var b=network("b",List.of("two"),List.of("one.com"),0);assertEquals("b",matcher.match("one.com",List.of(a,b)).network().name());}
    @Test void suffixWinsOverLabelAndRuleOrderDoesNotMatter(){var a=network("a",List.of("one","one.com"),List.of(),0);var b=network("b",List.of("one"),List.of(),1);assertEquals("a",matcher.match("play.one.com",List.of(a,b)).network().name());assertFalse(matcher.match("one.com.evil.net",List.of(network("suffix",List.of("one.com"),List.of(),0))).matched());}
    @Test void priorityResolvesEqualRulesAndDuplicateIdentifiersDoNotCreateAmbiguity(){var a=network("a",List.of("one","two"),List.of(),2);var b=network("b",List.of("two"),List.of(),1);assertEquals("a",matcher.match("one.two.com",List.of(a,b)).network().name());}
}
