/**
 * Shared networking utilities used by the discovery and LLM clients: {@code HttpRetry}
 * adds transient-failure retry with backoff around an HTTP send. Depends on nothing
 * internal, so it stays a leaf both {@code pipeline} and {@code llm} can build on.
 */
@NullMarked
package se.deversity.skill3.net;

import org.jspecify.annotations.NullMarked;
