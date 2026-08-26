package nl.hauntedmc.ailex.listener.llm;

import java.io.File;
import java.util.function.LongSupplier;

/** @deprecated use {@link nl.hauntedmc.ailex.assistant.runtime.context.ChatContextStore}. */
@Deprecated(forRemoval = true)
final class ChatContextStore extends nl.hauntedmc.ailex.assistant.runtime.context.ChatContextStore {
    ChatContextStore(LongSupplier currentTimeMillis) {
        super(currentTimeMillis);
    }

    ChatContextStore(File dataFolder, LongSupplier currentTimeMillis) {
        // Preserve pre-1.5 constructor semantics only for compatibility callers/tests.
        super(dataFolder, currentTimeMillis, true);
    }

    ChatContextStore(File dataFolder, LongSupplier currentTimeMillis, boolean restorePersistedContext) {
        super(dataFolder, currentTimeMillis, restorePersistedContext);
    }
}
