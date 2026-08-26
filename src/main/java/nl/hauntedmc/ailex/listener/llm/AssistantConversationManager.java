package nl.hauntedmc.ailex.listener.llm;

import java.util.function.LongSupplier;

/** @deprecated use {@link nl.hauntedmc.ailex.assistant.runtime.AssistantConversationManager}. */
@Deprecated(forRemoval = true)
final class AssistantConversationManager extends nl.hauntedmc.ailex.assistant.runtime.AssistantConversationManager {
    AssistantConversationManager(LongSupplier clock) {
        super(clock);
    }
}
