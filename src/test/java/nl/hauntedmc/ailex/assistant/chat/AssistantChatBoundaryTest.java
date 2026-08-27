package nl.hauntedmc.ailex.assistant.chat;

import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void implicitFollowUpsAreDisabledByDefaultSoNormalChatCannotTriggerTheBot() {
        AssistantChatConfiguration configuration = new AssistantChatConfiguration(YamlConfiguration::new);
        assertFalse(configuration.allowImplicitFollowUps());
    }

    @Test
    void implicitFollowUpsRequireExplicitOperatorOptIn() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("openai.chat.allow_implicit_followups", true);
        AssistantChatConfiguration configuration = new AssistantChatConfiguration(() -> yaml);
        assertTrue(configuration.allowImplicitFollowUps());
    }

    @Test
    void playerFacingChatFlattensLogicalReplyLinesIntoNaturalSentenceFlow() {
        String response = "This is sentence 1.\nThis is sentence 2.\r\nThis is 3.";
        assertEquals(
                "This is sentence 1. This is sentence 2. This is 3.",
                AssistantChatController.flattenForChat(response)
        );
    }

    @Test
    void playerFacingChatAlsoCollapsesAccidentalExtraWhitespace() {
        assertEquals("one two three", AssistantChatController.flattenForChat(" one\t two  \n three "));
    }
}
