package se.deversity.skill3.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskCacheTest {

    @Test
    void putThenGetRoundTrips(@TempDir Path dir) {
        DiskCache cache = new DiskCache(dir, Duration.ofDays(7));
        cache.put("k", "value\nwith lines");
        assertEquals(Optional.of("value\nwith lines"), cache.get("k"));
    }

    @Test
    void missReturnsEmpty(@TempDir Path dir) {
        assertEquals(Optional.empty(), new DiskCache(dir, Duration.ofDays(7)).get("absent"));
    }

    @Test
    void expiredEntryIsAMiss(@TempDir Path dir) throws Exception {
        DiskCache cache = new DiskCache(dir, Duration.ofMinutes(10));
        cache.put("k", "old");
        // Backdate the file well beyond the TTL.
        Files.setLastModifiedTime(dir.resolve("k"), FileTime.from(Instant.now().minus(Duration.ofHours(1))));
        assertEquals(Optional.empty(), cache.get("k"));
    }

    @Test
    void keyIsDeterministicAndSeparatorSafe() {
        assertEquals(DiskCache.key("page", "https://a"), DiskCache.key("page", "https://a"));
        assertNotEquals(DiskCache.key("a", "b"), DiskCache.key("ab"));
        assertTrue(DiskCache.key("x").matches("[0-9a-f]{64}")); // SHA-256 hex
    }

    @Test
    void writeFailureDegradesToMissNotError(@TempDir Path dir) throws Exception {
        // A regular file standing in for the cache dir's parent makes createDirectories fail.
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "i am a file, not a directory");
        DiskCache cache = new DiskCache(blocker.resolve("cache"), Duration.ofDays(1));
        cache.put("k", "v"); // must not throw
        assertFalse(cache.get("k").isPresent());
    }
}
