package nl.hauntedmc.ailex.listener.llm;

/** @deprecated use {@link nl.hauntedmc.ailex.assistant.infrastructure.knowledge.KnowledgeSelector}. */
@Deprecated(forRemoval = true)
final class KnowledgeSelector {
    private KnowledgeSelector() {
    }

    static String select(String knowledge, String query, int maxCharacters) {
        return nl.hauntedmc.ailex.assistant.infrastructure.knowledge.KnowledgeSelector.select(
                knowledge, query, maxCharacters
        );
    }
}
