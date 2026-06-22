package se.deversity.skill3.skillspector;

import java.io.IOException;

/**
 * Revises a SKILL.md in response to SkillSpector findings. Supplied by the caller
 * (typically a local-LLM call wrapped in the SKILL.md post-processor) so the loop
 * stays independent of the synthesis stack.
 */
@FunctionalInterface
public interface Reviser {

    String revise(String currentSkillMd, SkillSpectorReport report) throws IOException;
}
