package nl.hauntedmc.ailex.assistant.chat;

import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantChatBoundaryTest {

    @Test
    void mentionMatchingUsesMinecraftNameBoundaries() {
        assertTrue(AssistantMentionMatcher.isMentioned("haunty?", "Haunty"));
        assertTrue(AssistantMentionMatcher.isMentioned("@Haunty wat ging mis", "Haunty"));
        assertFalse(AssistantMentionMatcher.isMentioned("Alexander", "Alex"));
        assertFalse(AssistantMentionMatcher.isMentioned("haunty_bot", "Haunty"));
    }

    @Test
    void rawHistoryIsSelectiveInsteadOfIncludedForEveryChatTurn() {
        assertFalse(WorkingContextPolicy.includeRawHistory("hey alles goed?", AssistantDialogueContext.empty()));
        assertTrue(WorkingContextPolicy.includeRawHistory("wat gebeurde er net in de chat?", AssistantDialogueContext.empty()));
    }

    @Test
    void rawTranscriptPersistenceIsPrivacyFirstByDefault() {
        AssistantChatConfiguration configuration = new AssistantChatConfiguration(YamlConfiguration::new);
        assertFalse(configuration.contextSettings().persistToDisk());
    }

    @Test
    void implicitFollowUpsAreEnabledByDefaultForNaturalDialogue() {
        AssistantChatConfiguration configuration = new AssistantChatConfiguration(YamlConfiguration::new);
        assertTrue(configuration.allowImplicitFollowUps());
    }

    @Test
    void implicitFollowUpsCanBeExplicitlyDisabledByOperator() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("openai.chat.allow_implicit_followups", false);
        AssistantChatConfiguration configuration = new AssistantChatConfiguration(() -> yaml);
        assertFalse(configuration.allowImplicitFollowUps());
    }
}
