package se.deversity.skill3.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Provenance record for one {@code learn} run, written alongside the skill as {@code run.json}.
 * It answers "what produced this SKILL.md?" — the queries, the exact sources and scores that
 * backed it, whether it was verified and vetted, and how long each phase took. Plain value types
 * only (dates as ISO strings), so it serializes with a default {@code ObjectMapper} and no extra
 * Jackson modules. Lists are defensively copied so the record stays immutable.
 */
public record RunManifest(
        String generatedBy,
        String skill,
        String targetModel,
        String cutoff,
        String today,
        List<String> queries,
        int sourceCount,
        boolean verified,
        boolean vetted,
        boolean clean,
        int findings,
        List<SourceRef> sources,
        Map<String, Long> timingsMs) {

    public RunManifest {
        queries = List.copyOf(queries);
        sources = List.copyOf(sources);
        timingsMs = Map.copyOf(timingsMs);
    }

    /** One ranked source and the scores that decided its inclusion and ordering. */
    public record SourceRef(String url, @Nullable String published, double authority,
                            double recency, double combinedScore, boolean postCutoff, int consensus) {
    }
}
