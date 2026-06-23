package se.deversity.skill3.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A best-effort, content-addressed file cache for expensive network results (search results,
 * fetched pages). Re-running a topic otherwise re-fetches everything; with this, iterating on
 * synthesis is cheap. Entries older than the TTL are treated as misses. Every cache operation
 * is best-effort: a read or write failure degrades to a cache miss and never breaks a run.
 */
public final class DiskCache {

    private final Path dir;
    private final Duration ttl;

    public DiskCache(Path dir, Duration ttl) {
        this.dir = dir;
        this.ttl = ttl;
    }

    /** {@return the cached value for {@code key}, or empty if absent, expired, or unreadable} */
    public Optional<String> get(String key) {
        Path file = dir.resolve(key);
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            Instant modified = Files.getLastModifiedTime(file).toInstant();
            if (ttl != null && modified.plus(ttl).isBefore(Instant.now())) {
                return Optional.empty(); // stale
            }
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException miss) {
            return Optional.empty();
        }
    }

    /** Stores {@code value} under {@code key}. Failures are swallowed — the cache is an optimization. */
    public void put(String key, String value) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(key), value, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best-effort; a cache write must never fail the run
        }
    }

    /** {@return a filesystem-safe SHA-256 hex key over {@code parts}} */
    public static String key(String... parts) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
        for (String part : parts) {
            md.update(part.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0); // separator so key("a","b") != key("ab")
        }
        byte[] hash = md.digest();
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }
}
