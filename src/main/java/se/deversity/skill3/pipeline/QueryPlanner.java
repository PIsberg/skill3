package se.deversity.skill3.pipeline;

import se.deversity.skill3.llm.ChatModel;
import se.deversity.skill3.model.Cutoff;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Topic-agnostic discovery planner: asks the model what to search for. The model
 * already knows the topic up to its cutoff, so it is well placed to enumerate the
 * facets most likely to have changed after it — and this generalises to any topic
 * (a person, a protocol, a release) without hardcoded per-topic logic.
 */
public class QueryPlanner {

    private static final int DEFAULT_MAX_QUERIES = 6;
    /** Strips a leading list marker only: "- ", "* ", "1. ", "2) " — not a leading year. */
    private static final Pattern LEADING_MARKER = Pattern.compile("^\\s*(?:[-*]|\\d+[.)])\\s+");

    private static final String SYSTEM = """
            You plan web searches that discover what changed about a topic AFTER a
            knowledge cutoff. You already know the topic up to the cutoff, so target
            the facets most likely to have NEW developments after it. Output ONLY the
            search query strings, one per line — no numbering, quotes, or commentary.
            """;

    private final ChatModel model;
    private final int maxQueries;

    public QueryPlanner(ChatModel model) {
        this(model, DEFAULT_MAX_QUERIES);
    }

    public QueryPlanner(ChatModel model, int maxQueries) {
        this.model = model;
        this.maxQueries = maxQueries;
    }

    /** {@return distinct search queries covering post-cutoff facets of {@code topic}} */
    public List<String> plan(String topic, Cutoff cutoff, LocalDate today) {
        String user = "Topic: " + topic + "\n"
                + "Knowledge cutoff: " + cutoff.iso() + "\n"
                + "Today: " + today + "\n\n"
                + "Write up to " + maxQueries + " web-search queries that, run together, would surface "
                + "the most important developments about \"" + topic + "\" since " + cutoff.iso()
                + ". Cover distinct facets, not minor variations. Include one broad \"what is new\" "
                + "query; if the topic is a person, organisation, or product, add queries for its "
                + "major activity areas. One query per line.";
        try {
            List<String> queries = parse(model.complete(SYSTEM, user));
            if (!queries.isEmpty()) {
                return queries;
            }
        } catch (IOException e) {
            System.err.println("  ! query planning failed (" + e.getMessage() + "); using the topic alone.");
        }
        return List.of(topic);
    }

    private List<String> parse(String raw) {
        Set<String> queries = new LinkedHashSet<>();
        for (String line : raw.split("\n", -1)) {
            String query = LEADING_MARKER.matcher(line.strip()).replaceFirst("")
                    .replaceAll("^[\"']|[\"']$", "")
                    .strip();
            if (!query.isBlank() && query.length() <= 200) {
                queries.add(query);
            }
            if (queries.size() >= maxQueries) {
                break;
            }
        }
        return new ArrayList<>(queries);
    }
}
