package se.deversity.skill3.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the run.json shape a default ObjectMapper produces, including a null published date. */
class RunManifestTest {

    @Test
    void roundTripsWithDefaultObjectMapperIncludingNullPublished() throws Exception {
        RunManifest manifest = new RunManifest(
                "skill3", "mcp", "claude-opus-4-8", "2026-01", "2026-07-02",
                List.of("q1", "q2"), 2, true, true, true, 0,
                true, true, 0, 1, 0,
                List.of(
                        new RunManifest.SourceRef("https://a", "2026-05-01", 1.0, 1.0, 1.0, true, 2),
                        new RunManifest.SourceRef("https://b", null, 0.5, 0.3, 0.15, false, 0)),
                Map.of("totalMs", 1234L));

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(manifest);
        JsonNode root = mapper.readTree(json);

        assertEquals("skill3", root.get("generatedBy").asText());
        assertEquals("mcp", root.get("skill").asText());
        assertEquals(2, root.get("queries").size());
        assertEquals(2, root.get("sources").size());
        assertTrue(root.get("sources").get(1).get("published").isNull()); // undated source
        assertEquals(1234, root.get("timingsMs").get("totalMs").asLong());

        // Round-trip: the record deserializes back to an equal value.
        RunManifest back = mapper.readValue(json, RunManifest.class);
        assertEquals(manifest, back);
    }
}
