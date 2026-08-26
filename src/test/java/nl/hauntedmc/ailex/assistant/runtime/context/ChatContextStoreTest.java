package nl.hauntedmc.ailex.assistant.runtime.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
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

    @Test
    void shouldPreferRelevantHistoryOverUnrelatedRecentMessages() {
        AtomicLong now = new AtomicLong(0L);
        ChatContextStore store = new ChatContextStore(now::get);
        ChatContextStore.ContextSettings settings = settings(10, 10, 1_000L);
        store.recordGeneralChat("Alex", "Use /plot claim to get a Creative plot.", settings);
        now.incrementAndGet();
        store.recordGeneralChat("Bea", "I am mining stone now.", settings);
        now.incrementAndGet();
        store.recordGeneralChat("Chris", "Nice weather today.", settings);
        String context = store.buildContext(UUID.randomUUID(), 1, "Bot", "How does /plot claim work?", settings);
        assertTrue(context.contains("Use /plot claim"));
    }

    @Test
    void shouldRestorePersistedShortTermContextOnlyWhenExplicitlyRequested(@TempDir Path dataDirectory) {
        AtomicLong now = new AtomicLong(1L);
        ChatContextStore.ContextSettings settings = persistentSettings();
        UUID playerId = UUID.randomUUID();
        ChatContextStore store = new ChatContextStore(dataDirectory.toFile(), now::get, false);
        store.recordGeneralChat("Alex", "I am building a redstone farm.", settings);
        store.recordConversation(playerId, 1, "Alex", "Can you help with observers?", settings);
        store.recordBotMemory(1, "Bot", "Observers detect block updates.", settings);
        store.recordMetadata(playerId, 1, "player.world=world; player.location=10,64,10", settings);

        ChatContextStore notRestored = new ChatContextStore(dataDirectory.toFile(), now::get, false);
        assertTrue(notRestored.buildContext(playerId, 1, "Bot", settings).isBlank());

        ChatContextStore restored = new ChatContextStore(dataDirectory.toFile(), now::get, true);
        String context = restored.buildContext(playerId, 1, "Bot", settings);
        File savedFile = dataDirectory.resolve("assistant-short-term-memory.yml").toFile();
        assertTrue(savedFile.isFile());
        assertTrue(context.contains("I am building a redstone farm."));
        assertTrue(context.contains("Can you help with observers?"));
        assertTrue(context.contains("Observers detect block updates."));
        assertTrue(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(savedFile)
                .getString("metadata." + playerId + ".1.message", "").contains("player.location"));
    }

    private static ChatContextStore.ContextSettings settings(int generalMaxMessages, int conversationMaxMessages, long maxAgeMillis) {
        return settings(generalMaxMessages, conversationMaxMessages, maxAgeMillis, 240);
    }

    private static ChatContextStore.ContextSettings settings(
            int generalMaxMessages, int conversationMaxMessages, long maxAgeMillis, int maxMessageCharacters
    ) {
        return new ChatContextStore.ContextSettings(
                true, false, maxMessageCharacters, true, "HH:mm:ss",
                new ChatContextStore.HistorySettings(true, generalMaxMessages, maxAgeMillis, 1_000),
                new ChatContextStore.HistorySettings(true, conversationMaxMessages, maxAgeMillis, 1_000),
                new ChatContextStore.HistorySettings(true, 10, maxAgeMillis, 1_000), 3_000
        );
    }

    private static ChatContextStore.ContextSettings persistentSettings() {
        return new ChatContextStore.ContextSettings(
                true, true, 240, true, "HH:mm:ss",
                new ChatContextStore.HistorySettings(true, 10, 10_000L, 1_000),
                new ChatContextStore.HistorySettings(true, 10, 10_000L, 1_000),
                new ChatContextStore.HistorySettings(true, 10, 10_000L, 1_000), 3_000
        );
    }
}
