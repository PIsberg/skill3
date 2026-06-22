package se.deversity.skill3.llm;

import java.io.IOException;

/** Minimal chat abstraction so synthesis can be unit-tested without a live LLM. */
@FunctionalInterface
public interface ChatModel {

    /** {@return the assistant message content for a system+user prompt} */
    String complete(String system, String user) throws IOException;
}
