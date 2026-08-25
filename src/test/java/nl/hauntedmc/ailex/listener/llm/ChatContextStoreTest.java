package nl.hauntedmc.ailex.listener.llm;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatContextStoreTest {

    @Test
    void shouldKeepOnlyTheConfiguredRecentGeneralMessages() {
        AtomicLong now = new AtomicLong(0L);
        ChatContextStore store = new ChatContextStore(now::get);
        ChatContextStore.ContextSettings settings = settings(2, 10, 1_000L);

        store.recordGeneralChat("Alex", "first message", settings);
        now.incrementAndGet();
        store.recordGeneralChat("Bea", "second message", settings);
        now.incrementAndGet();
        store.recordGeneralChat("Chris", "third message", settings);

        String context = store.buildContext(UUID.randomUUID(), 1, "Bot", settings);

        assertFalse(context.contains("first message"));
        assertTrue(context.contains("] Bea: second message"));
        assertTrue(context.contains("] Chris: third message"));
    }

    @Test
    void shouldKeepConversationHistorySeparateForEachBot() {
        ChatContextStore store = new ChatContextStore(() -> 0L);
        ChatContextStore.ContextSettings settings = settings(5, 5, 1_000L);
        UUID playerId = UUID.randomUUID();

        store.recordConversation(playerId, 1, "Alex", "hello Bot One", settings);
        store.recordConversation(playerId, 2, "Alex", "hello Bot Two", settings);

        String firstBotContext = store.buildContext(playerId, 1, "Bot One", settings);
        String secondBotContext = store.buildContext(playerId, 2, "Bot Two", settings);

        assertTrue(firstBotContext.contains("hello Bot One"));
        assertFalse(firstBotContext.contains("hello Bot Two"));
        assertTrue(secondBotContext.contains("hello Bot Two"));
    }

    @Test
    void shouldKeepSharedBotMemoryAvailableToEveryPlayer() {
        ChatContextStore store = new ChatContextStore(() -> 0L);
        ChatContextStore.ContextSettings settings = settings(5, 5, 1_000L);

        store.recordBotMemory(1, "PlayerX", "hi Alfred you okay", settings);
        store.recordBotMemory(1, "Alfred", "Ja hoor!", settings);

        String context = store.buildContext(UUID.randomUUID(), 1, "Alfred", settings);

        assertTrue(context.contains("Recente berichten aan Alfred"));
        assertTrue(context.contains("PlayerX: hi Alfred you okay"));
        assertTrue(context.contains("Alfred: Ja hoor!"));
    }

    @Test
    void shouldRemoveExpiredChatAndCompactLongMessages() {
        AtomicLong now = new AtomicLong(0L);
        ChatContextStore store = new ChatContextStore(now::get);
        ChatContextStore.ContextSettings settings = settings(5, 5, 1_000L, 10);

        store.recordGeneralChat("Alex", "one\n two\n three four", settings);
        now.set(1_000L);

        String context = store.buildContext(UUID.randomUUID(), 1, "Bot", settings);

        assertFalse(context.contains("one two"));

        store.recordGeneralChat("Alex", "one\n two\n three four", settings);

        context = store.buildContext(UUID.randomUUID(), 1, "Bot", settings);

        assertTrue(context.contains("Alex: one two t…"));
    }

    private static ChatContextStore.ContextSettings settings(int generalMaxMessages, int conversationMaxMessages, long maxAgeMillis) {
        return settings(generalMaxMessages, conversationMaxMessages, maxAgeMillis, 240);
    }

    private static ChatContextStore.ContextSettings settings(
            int generalMaxMessages,
            int conversationMaxMessages,
            long maxAgeMillis,
            int maxMessageCharacters
    ) {
        return new ChatContextStore.ContextSettings(
                true,
                maxMessageCharacters,
                true,
                "HH:mm:ss",
                new ChatContextStore.HistorySettings(true, generalMaxMessages, maxAgeMillis, 1_000),
                new ChatContextStore.HistorySettings(true, conversationMaxMessages, maxAgeMillis, 1_000),
                new ChatContextStore.HistorySettings(true, 10, maxAgeMillis, 1_000),
                3_000
        );
    }
}
