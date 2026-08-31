package nl.hauntedmc.ailex.config;

import nl.hauntedmc.ailex.assistant.chat.AssistantChatConfiguration;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.proactive.CommunityGoal;
import nl.hauntedmc.ailex.assistant.proactive.ProactiveChatSettings;
import nl.hauntedmc.ailex.npc.NPCProperties;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippedConfigurationDefaultsTest {

    @Test
    void shippedAssistantDefaultsPreserveHelpfulBoundedProductionProfiles() {
        AssistantSettings settings = AssistantSettings.from(shippedConfig());

        assertEquals(30, settings.totalDeadlineSeconds());
        assertEquals(Set.of("nl", "en", "de"), settings.allowedLanguages());
        assertEquals(8_000, settings.maxInputTokens(AssistantMode.FAST));
        assertEquals(32_000, settings.maxInputTokens(AssistantMode.GROUNDED));
        assertEquals(64_000, settings.maxInputTokens(AssistantMode.DELIBERATE));
        assertEquals("gpt-5.6-terra", settings.profileFor(AssistantMode.FAST).model());
        assertEquals("medium", settings.profileFor(AssistantMode.GROUNDED).reasoningEffort());
        assertEquals("gpt-5.6-sol", settings.profileFor(AssistantMode.DELIBERATE).model());
        assertTrue(settings.readOnlyTools());
        assertTrue(settings.redactOtherPlayers());
        assertTrue(settings.verificationEnabled());
    }

    @Test
    void shippedChatDefaultsUseHauntyAndKeepDirectConversationsAvailable() {
        AssistantChatConfiguration chat = new AssistantChatConfiguration(this::shippedConfig);

        assertEquals("server", chat.responseVisibility());
        assertEquals(900_000L, chat.sessionTimeoutMillis());
        assertFalse(chat.allowImplicitFollowUps());
        assertEquals(30, chat.responseRateLimit().maxResponses());
        assertEquals("Haunty", chat.standaloneTarget().name());
    }

    @Test
    void shippedProactiveDefaultsAreRareAndNeverUseIdleOrMemoryFollowUps() {
        ProactiveChatSettings settings = ProactiveChatSettings.from(shippedConfig());

        assertTrue(settings.enabled());
        assertEquals(900_000L, settings.cooldownMillis());
        assertEquals(0.03D, settings.join().probability());
        assertEquals(1_800_000L, settings.join().cooldownMillis());
        assertEquals(0.10D, settings.questions().probability());
        assertEquals(0.45D, settings.questions().utilityThreshold());
        assertEquals(0.08D, settings.goals().probability());
        assertEquals(0.0D, settings.goals().followUpProbability());
        assertEquals(0.12D, settings.collective().probability());
        assertFalse(settings.idle().enabled());
        assertFalse(settings.goals().enabled(CommunityGoal.FOLLOW_UP));
        assertFalse(settings.goals().enabled(CommunityGoal.SUPPORT_CONVERSATION));
        assertTrue(settings.goals().enabled(CommunityGoal.HELP_NEW_PLAYER));
        assertTrue(settings.goals().enabled(CommunityGoal.CELEBRATE));
    }

    @Test
    void incompleteConfigurationFallsBackToTheSameProductionProfilesAndChatBoundary() {
        YamlConfiguration config = new YamlConfiguration();

        AssistantSettings assistant = AssistantSettings.from(config);
        AssistantChatConfiguration chat = new AssistantChatConfiguration(() -> config);
        ProactiveChatSettings proactive = ProactiveChatSettings.from(config);

        assertEquals("gpt-5.6-terra", assistant.profileFor(AssistantMode.GROUNDED).model());
        assertEquals("medium", assistant.profileFor(AssistantMode.GROUNDED).reasoningEffort());
        assertEquals("gpt-5.6-sol", assistant.profileFor(AssistantMode.DELIBERATE).model());
        assertEquals(Set.of("nl", "en", "de"), assistant.allowedLanguages());
        assertEquals("Haunty", chat.standaloneTarget().name());
        assertEquals(900_000L, chat.sessionTimeoutMillis());
        assertEquals(900_000L, proactive.cooldownMillis());
        assertFalse(proactive.idle().enabled());
        assertFalse(proactive.goals().enabled(CommunityGoal.FOLLOW_UP));
    }

    @Test
    void npcPropertyFallbacksMatchTheBundledProductionDefaults() {
        YamlConfiguration config = shippedConfig();
        NPCProperties defaults = NPCProperties.defaultValues();

        assertEquals(config.getString("npc.defaults.entity.prefix"), defaults.getPrefix());
        assertEquals(config.getString("npc.defaults.entity.tabPrefix"), defaults.getTabPrefix());
        assertEquals(config.getBoolean("npc.defaults.entity.listedInTab"), defaults.isListedInTab());
        assertEquals(config.getString("npc.defaults.entity.prompts.systemPrompt"), defaults.getSystemPrompt());
        assertEquals(
                config.getString("npc.defaults.entity.prompts.userPromptTemplate"), defaults.getUserPromptTemplate()
        );
    }

    private YamlConfiguration shippedConfig() {
        InputStream resource = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(resource, "Bundled config.yml must be available to tests.");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }
}
