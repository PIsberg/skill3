package se.deversity.skill3.llm;

import se.deversity.vibetags.annotations.AIContract;

import java.io.IOException;

/** Minimal chat abstraction so synthesis can be unit-tested without a live LLM. */
@AIContract(reason = "The single seam every model-driven stage binds to — QueryPlanner, "
        + "Synthesizer, Verifier and the self-correction Reviser all take this one interface, "
        + "which is what lets one --llm-provider choice apply uniformly. Test fakes implement "
        + "it directly, so changing the signature breaks every unit test that avoids a live model.")
@FunctionalInterface
public interface ChatModel {

    /** {@return the assistant message content for a system+user prompt} */
    String complete(String system, String user) throws IOException;
}
