package se.deversity.skill3.model;

import se.deversity.vibetags.annotations.AIImmutable;

import java.util.List;

/**
 * The structured input handed to the synthesizer — never raw HTML. Sources are
 * pre-ranked (highest combined score first).
 */
@AIImmutable(note = "Immutable record; the sources list is defensively copied in the compact constructor.")
public record ContextBundle(
        String skillName,
        String targetModel,
        Cutoff cutoff,
        List<Source> sources) {

    /** Defensively copies {@code sources} so the bundle cannot be mutated after construction. */
    public ContextBundle {
        sources = List.copyOf(sources);
    }
}
