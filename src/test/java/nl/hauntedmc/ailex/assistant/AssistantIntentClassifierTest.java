package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.application.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.application.RequiredContextPlanner;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantRoute;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.domain.AssistantSource;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantIntentClassifierTest {

    private final AssistantIntentClassifier classifier = new AssistantIntentClassifier();
    private final RequiredContextPlanner planner = new RequiredContextPlanner();

    @Test
    void shouldKeepCasualConversationFast() {
        AssistantRoute route = classifier.classify("hoe gaat het?", false, false, false, false);

        assertEquals(AssistantIntent.CONVERSATION, route.intent());
        assertEquals(AssistantMode.FAST, route.mode());
        assertFalse(route.retrievalRequired());
    }

    @Test
    void shouldRouteServerCommandsToGroundedEvidence() {
        AssistantRoute route = classifier.classify("wat doet /back op HauntedMC?", false, false, false, false);

        assertEquals(AssistantIntent.SERVER_FACT, route.intent());
        assertEquals(AssistantMode.GROUNDED, route.mode());
        assertTrue(route.retrievalRequired());
    }

    @Test
    void shouldRouteLiveQuestionsToGroundedEvidenceWithoutSpendingDeliberateReasoning() {
        AssistantRoute route = classifier.classify("hoeveel spelers zijn nu online?", false, false, false, false);

        assertEquals(AssistantIntent.LIVE_STATE, route.intent());
        assertEquals(AssistantMode.GROUNDED, route.mode());
        assertTrue(route.liveDataRequired());
    }

    @Test
    void shouldRouteActualPlayerStateQuestionsAsLive() {
        AssistantRoute route = classifier.classify("wat heb ik vast?", false, false, false, false);

        assertEquals(AssistantIntent.LIVE_STATE, route.intent());
        assertTrue(route.liveDataRequired());
    }

    @Test
    void shouldNotMistakeOrdinaryGameplayQuestionsForLiveState() {
        AssistantRoute route = classifier.classify("hoe maak ik een diamond sword?", false, false, false, false);

        assertEquals(AssistantIntent.GAMEPLAY_HELP, route.intent());
        assertFalse(route.liveDataRequired());
    }

    @Test
    void shouldRouteWorldAndDimensionQuestionsAsLive() {
        AssistantRoute route = classifier.classify("in welke wereld ben ik?", false, false, false, false);

        assertEquals(AssistantIntent.LIVE_STATE, route.intent());
        assertTrue(route.liveDataRequired());
    }

    @Test
    void shouldRouteCurrentHauntedMcFeatureStateToLiveProviders() {
        AssistantRoute route = classifier.classify("welke HauntedMC servers zijn nu online?", false, false, false, false);

        assertEquals(AssistantIntent.LIVE_STATE, route.intent());
        assertEquals(AssistantMode.GROUNDED, route.mode());
        assertTrue(route.liveDataRequired());
    }

    @Test
    void shouldRouteVanillaMobQuestionsToGameplayHelp() {
        AssistantRoute route = classifier.classify("wat eet een axolotl?", false, false, false, false);

        assertEquals(AssistantIntent.GAMEPLAY_HELP, route.intent());
        assertFalse(route.retrievalRequired());
    }

    @Test
    void shouldRouteDiscordAndAiReleaseQuestionsToGroundedServerEvidence() {
        AssistantRoute discord = classifier.classify("welke discord kanalen heeft HauntedMC?", false, false, false, false);
        AssistantRoute release = classifier.classify("welke versie draait AIlex?", false, false, false, false);

        assertEquals(AssistantIntent.SERVER_FACT, discord.intent());
        assertTrue(discord.retrievalRequired());
        assertEquals(AssistantIntent.SERVER_FACT, release.intent());
        assertTrue(release.retrievalRequired());
    }

    @Test
    void shouldNotConfuseOrdinaryKnowledgeWithPersonalMemoryRecall() {
        AssistantRoute route = classifier.classify("wat is combat tag?", false, false, false, false);

        assertEquals(AssistantIntent.SERVER_FACT, route.intent());
        assertTrue(route.retrievalRequired());
    }

    @Test
    void shouldRouteFreshSemanticMemoryRecallToGroundedMemory() {
        AssistantRoute route = classifier.classify("wat weet je nog over mijn favoriete block?", false, false, false, false);

        assertEquals(AssistantIntent.MEMORY_RECALL, route.intent());
        assertTrue(route.memoryRequired());
        assertEquals(AssistantMode.GROUNDED, route.mode());
    }

    @Test
    void shouldRouteFreshEventRecallToEpisodicMemory() {
        AssistantRoute route = classifier.classify("wie vroeg dat gisteren?", false, false, false, false);

        assertEquals(AssistantIntent.EVENT_RECALL, route.intent());
        assertTrue(route.memoryRequired());
    }

    @Test
    void shouldRouteEventDiagnosticQuestionsInsideActiveDialogue() {
        AssistantRoute route = classifier.classify("wie zei dat net?", false, false, false, true);

        assertEquals(AssistantIntent.EVENT_RECALL, route.intent());
        assertTrue(route.memoryRequired());
    }

    @Test
    void shouldUseActiveDialogueToRouteAShortFollowUp() {
        AssistantRoute route = classifier.classify("en waarom?", false, false, false, true);

        assertEquals(AssistantIntent.CONVERSATION, route.intent());
        assertEquals(AssistantMode.FAST, route.mode());
    }

    @Test
    void shouldNormalizeConfiguredLanguageListAndKeepDutchFallback() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.routing.allowed_languages", java.util.List.of("EN", "de", "xx"));
        config.set("openai.assistant.routing.default_language", "xx");

        AssistantSettings settings = AssistantSettings.from(config);

        assertEquals(Set.of("nl", "en", "de"), settings.allowedLanguages());
        assertEquals("nl", settings.defaultLanguage());
    }

    @Test
    void shouldDefaultUnknownLanguageToDutchAndAllowOnlyConfiguredEnglishDetection() {
        AssistantSettings settings = AssistantSettings.defaults();

        assertTrue(settings.languageAllowed("nl"));
        assertTrue(settings.languageAllowed("en"));
        assertFalse(settings.languageAllowed("de"));
        assertEquals("nl", settings.normalizeOutputLanguage("xx"));
    }

    @Test
    void shouldDetectGermanOnlyWhenConfigured() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.routing.allowed_languages", java.util.List.of("nl", "en", "de"));
        AssistantSettings settings = AssistantSettings.from(config);

        assertTrue(settings.languageAllowed("de"));
    }

    @Test
    void shouldUseIndependentModelProfilesForEachAssistantLayer() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.model", "gpt-5.6-luna");
        config.set("openai.assistant.profiles.fast.model", "gpt-5.6-luna");
        config.set("openai.assistant.profiles.grounded.model", "gpt-5.6-terra");
        config.set("openai.assistant.profiles.grounded.reasoning_effort", "medium");
        config.set("openai.assistant.profiles.grounded.max_output_tokens", 320);
        config.set("openai.assistant.profiles.deliberate.model", "gpt-5.6-sol");
        config.set("openai.assistant.profiles.deliberate.reasoning_effort", "high");
        config.set("openai.assistant.observability.enabled", true);
        config.set("openai.assistant.observability.include_response_preview", true);
        config.set("openai.assistant.observability.max_response_preview_characters", 120);

        AssistantSettings settings = AssistantSettings.from(config);

        assertEquals("gpt-5.6-luna", settings.profileFor(AssistantMode.FAST).model());
        assertEquals("gpt-5.6-terra", settings.profileFor(AssistantMode.GROUNDED).model());
        assertEquals("medium", settings.profileFor(AssistantMode.GROUNDED).reasoningEffort());
        assertEquals(320, settings.profileFor(AssistantMode.GROUNDED).maxOutputTokens());
        assertEquals("gpt-5.6-sol", settings.profileFor(AssistantMode.DELIBERATE).model());
        assertEquals("high", settings.profileFor(AssistantMode.DELIBERATE).reasoningEffort());
        assertTrue(settings.diagnosticLogging());
        assertTrue(settings.logResponsePreview());
        assertEquals(120, settings.maxResponsePreviewCharacters());
    }

    @Test
    void shouldUseRicherBoundedDefaultsWithoutMakingFastChatUnbounded() {
        AssistantSettings settings = AssistantSettings.defaults();

        assertEquals(8_000, settings.maxInputTokens(AssistantMode.FAST));
        assertEquals(32_000, settings.maxInputTokens(AssistantMode.GROUNDED));
        assertEquals(64_000, settings.maxInputTokens(AssistantMode.DELIBERATE));
        assertEquals(64, settings.maxChunks());
        assertEquals(140_000, settings.maxEvidenceCharacters());
        assertEquals("gpt-5.6-terra", settings.profileFor(AssistantMode.FAST).model());
        assertEquals(400, settings.profileFor(AssistantMode.FAST).maxOutputTokens());
        assertEquals(640, settings.profileFor(AssistantMode.GROUNDED).maxOutputTokens());
        assertEquals(1_000, settings.profileFor(AssistantMode.DELIBERATE).maxOutputTokens());
        assertEquals(3, settings.maxLines(AssistantMode.FAST));
        assertEquals(5, settings.maxLines(AssistantMode.GROUNDED));
        assertEquals(8, settings.maxLines(AssistantMode.DELIBERATE));
    }

    @Test
    void shouldRouteFreshSemanticMemoryRecallToGroundedMemory() {
        AssistantRoute route = classifier.classify("wat weet je nog over mijn favoriete block?", false, false, false, false);

        assertEquals(AssistantIntent.MEMORY_RECALL, route.intent());
        assertTrue(route.memoryRequired());
        assertEquals(AssistantMode.GROUNDED, route.mode());
    }
}
