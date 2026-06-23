package se.deversity.skill3.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Picks the most topic-relevant items to fit a prompt budget instead of taking the first N in
 * document order — so when a source has more excerpts than the budget, the ones that actually
 * mention the topic survive rather than whatever happened to appear first (nav crumbs, intros).
 * Selected items are returned in their original order to preserve reading flow.
 */
final class EvidenceSelector {

    private EvidenceSelector() {
    }

    /** {@return up to {@code max} items, the most topic-relevant first-seen-order subset} */
    static List<String> topByRelevance(List<String> items, String topic, int max) {
        if (max <= 0) {
            return List.of();
        }
        if (items.size() <= max) {
            return List.copyOf(items);
        }
        Set<String> tokens = topicTokens(topic);
        if (tokens.isEmpty()) {
            return List.copyOf(items.subList(0, max)); // no signal -> keep document order
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            indices.add(i);
        }
        // Most topic tokens first; ties keep earlier items. Then restore document order.
        indices.sort(Comparator
                .comparingInt((Integer i) -> score(items.get(i), tokens)).reversed()
                .thenComparingInt(i -> i));
        List<Integer> chosen = new ArrayList<>(indices.subList(0, max));
        chosen.sort(Comparator.naturalOrder());
        List<String> out = new ArrayList<>(max);
        for (int i : chosen) {
            out.add(items.get(i));
        }
        return out;
    }

    private static int score(String item, Set<String> tokens) {
        String lower = item.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            if (lower.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private static Set<String> topicTokens(String topic) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : topic.toLowerCase(Locale.ROOT).split("[^a-z0-9]+", -1)) {
            if (token.length() >= 3) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
