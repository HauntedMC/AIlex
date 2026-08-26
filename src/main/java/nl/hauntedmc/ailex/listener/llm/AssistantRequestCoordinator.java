package nl.hauntedmc.ailex.listener.llm;

import java.util.function.Consumer;

/** @deprecated use {@link nl.hauntedmc.ailex.assistant.runtime.AssistantRequestCoordinator}. */
@Deprecated(forRemoval = true)
final class AssistantRequestCoordinator extends nl.hauntedmc.ailex.assistant.runtime.AssistantRequestCoordinator {
    AssistantRequestCoordinator(Consumer<Runnable> dispatcher) {
        super(dispatcher);
    }
}
