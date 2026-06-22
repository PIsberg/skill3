package se.deversity.skill3.pipeline;

import se.deversity.skill3.model.Cutoff;
import se.deversity.skill3.model.Source;

import java.time.LocalDate;
import java.util.List;

/**
 * Phase 2: evaluative ingestion. Runs consensus annotation then cutoff-anchored
 * freshness scoring/filtering (bounded above by {@code today}) over already-retrieved
 * sources, returning them ranked best-first.
 */
public class IngestionPipeline {

    private final ConsensusValidator consensus;
    private final FreshnessFilter freshness;

    public IngestionPipeline(Cutoff cutoff, boolean strictCutoff, int minAgreement, LocalDate today) {
        this.consensus = new ConsensusValidator(minAgreement);
        this.freshness = new FreshnessFilter(cutoff, strictCutoff, today);
    }

    public List<Source> ingest(List<Source> sources) {
        consensus.annotate(sources);
        return freshness.apply(sources);
    }
}
