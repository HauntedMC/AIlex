package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

import java.util.List;

/** Pluggable learned semantic embedding source used by hybrid retrieval and deterministic tests. */
public interface SemanticEmbeddingProvider {

    boolean available();

    /** Returns one vector per input in the same order. Failure returns an empty list rather than blocking retrieval. */
    List<double[]> embed(List<String> inputs);
}
