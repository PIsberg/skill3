package se.deversity.skill3.pipeline;

import se.deversity.skill3.model.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schema-free consensus: a code block is trusted when it recurs across N
 * independent sources (by host), or when it comes from a high-authority source.
 * Blocks failing both tests are dropped; each source's {@code consensusCount} is
 * set to the strongest agreement among its kept blocks.
 */
public class ConsensusValidator {

    /**
     * Authority at which a lone block bypasses consensus. Deliberately above the 0.7 a
     * plain github.com source scores ("standard repository, sits in the middle") — only
     * genuinely authoritative hosts (per-run 1.0) skip the cross-source agreement test.
     */
    private static final double HIGH_AUTHORITY = 0.8;

    private final int minAgreement;

    public ConsensusValidator(int minAgreement) {
        this.minAgreement = Math.max(1, minAgreement);
    }

    public void annotate(List<Source> sources) {
        // normalized code block -> distinct hosts that contain it
        Map<String, Set<String>> hostsByBlock = new HashMap<>();
        for (Source s : sources) {
            String host = hostKey(s);
            for (String block : s.codeBlocks) {
                hostsByBlock.computeIfAbsent(normalize(block), k -> new HashSet<>()).add(host);
            }
        }

        for (Source s : sources) {
            List<String> kept = new ArrayList<>();
            int max = 0;
            for (String block : s.codeBlocks) {
                int agreement = hostsByBlock.getOrDefault(normalize(block), Set.of()).size();
                if (agreement >= minAgreement || s.authority >= HIGH_AUTHORITY) {
                    kept.add(block);
                    max = Math.max(max, agreement);
                }
            }
            s.codeBlocks = kept;
            s.consensusCount = max;
        }
    }

    private static String hostKey(Source s) {
        String h = AuthorityScorer.host(s.url);
        return h != null ? h : s.url;
    }

    static String normalize(String block) {
        // Collapse whitespace runs to one space instead of deleting them: removal made
        // token boundaries vanish, so distinct snippets (e.g. differently-indented,
        // whitespace-significant code) collapsed to the same key and falsely "agreed".
        return block == null ? "" : block.strip().replaceAll("\\s+", " ");
    }
}
