package se.deversity.skill3.pipeline;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Assigns a trust multiplier in [0, 1] to a URL by host. Authoritative hosts are
 * provided per-run (config-driven, not hardcoded per skill); blogging platforms
 * are demoted; standard repositories sit in the middle.
 */
public class AuthorityScorer {

    /** Host suffixes considered low-authority (blogs, Q&A, gists). */
    private static final Set<String> LOW = Set.of(
            "medium.com", "dev.to", "substack.com", "gist.github.com",
            "reddit.com", "quora.com", "blogspot.com", "wordpress.com",
            "hashnode.dev", "stackoverflow.com");

    /** Host suffixes treated as authoritative (score 1.0) for this run. */
    private final Set<String> authoritative;

    public AuthorityScorer(Set<String> authoritative) {
        this.authoritative = authoritative == null ? Set.of() : authoritative;
    }

    public double score(String url) {
        String host = host(url);
        if (host == null) {
            return 0.5;
        }
        for (String a : authoritative) {
            if (matches(host, a)) {
                return 1.0;
            }
        }
        for (String low : LOW) {
            if (matches(host, low)) {
                return 0.2;
            }
        }
        if (host.equals("github.com")) {
            return 0.7; // standard repository
        }
        return 0.5;
    }

    private static boolean matches(String host, String suffix) {
        return host.equals(suffix) || host.endsWith("." + suffix);
    }

    /** Extracts the lowercased host, or null if the URL is unparseable. */
    public static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? null : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
