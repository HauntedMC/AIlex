package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantIntentClassifierTest {

    @Test
    void shouldRouteServerCommandsToGroundedEvidence() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Hoe werkt /plot claim op HauntedMC?"
        );

        assertEquals(AssistantIntent.SERVER_FACT, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }

    @Test
    void shouldRouteDiscordAndAiReleaseQuestionsToGroundedServerEvidence() {
        AssistantIntentClassifier.Analysis discord = AssistantIntentClassifier.analyze(
                "In welk Discord kanaal staan de aankondigingen?"
        );
        AssistantIntentClassifier.Analysis release = AssistantIntentClassifier.analyze(
                "Is de nieuwe Haunty versie al live?"
        );

        assertEquals(AssistantIntent.SERVER_FACT, discord.intent());
        assertEquals(AssistantMode.GROUNDED, discord.mode());
        assertEquals(AssistantIntent.SERVER_FACT, release.intent());
        assertEquals(AssistantMode.GROUNDED, release.mode());
    }

    @Test
    void shouldRouteLiveQuestionsToGroundedEvidenceWithoutSpendingDeliberateReasoning() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Welk biome is hier dichtbij?"
        );

        assertEquals(AssistantIntent.LIVE_STATE, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }

    @Test
    void shouldRouteActualPlayerStateQuestionsAsLive() {
        AssistantIntentClassifier.Analysis heldItem = AssistantIntentClassifier.analyze(
                "Wat houd ik in mijn hand?"
        );
        AssistantIntentClassifier.Analysis ping = AssistantIntentClassifier.analyze("Wat is mijn ping?");

        assertEquals(AssistantIntent.LIVE_STATE, heldItem.intent());
        assertEquals(AssistantMode.GROUNDED, heldItem.mode());
        assertEquals(AssistantIntent.LIVE_STATE, ping.intent());
    }

    @Test
    void shouldRouteWorldAndDimensionQuestionsAsLive() {
        assertEquals(AssistantIntent.LIVE_STATE,
                AssistantIntentClassifier.analyze("In welke world ben ik?").intent());
        assertEquals(AssistantIntent.LIVE_STATE,
                AssistantIntentClassifier.analyze("What dimension am I in?").intent());
    }

    @Test
    void shouldRouteCurrentHauntedMcFeatureStateToLiveProviders() {
        assertEquals(AssistantIntent.LIVE_STATE,
                AssistantIntentClassifier.analyze("Wat is mijn rank?").intent());
        assertEquals(AssistantIntent.LIVE_STATE,
                AssistantIntentClassifier.analyze("Hoeveel geld heb ik?").intent());
        assertEquals(AssistantIntent.LIVE_STATE,
                AssistantIntentClassifier.analyze("Ben ik combat tagged?").intent());
        assertEquals(AssistantIntent.LIVE_STATE,
                AssistantIntentClassifier.analyze("Staat mijn AutoPickup aan?").intent());
    }

    @Test
    void shouldRouteFreshSemanticMemoryRecallToGroundedMemory() {
        AssistantIntentClassifier.Analysis dutch = AssistantIntentClassifier.analyze("Wat weet je van mij?");
        AssistantIntentClassifier.Analysis english = AssistantIntentClassifier.analyze(
                "What do you remember about me?"
        );

        assertEquals(AssistantIntent.MEMORY_RECALL, dutch.intent());
        assertEquals(AssistantMode.GROUNDED, dutch.mode());
        assertEquals(AssistantIntent.MEMORY_RECALL, english.intent());
    }

    @Test
    void shouldRouteFreshEventRecallToEpisodicMemory() {
        AssistantIntentClassifier.Analysis dutch = AssistantIntentClassifier.analyze(
                "Wat gebeurde er vorige keer?"
        );
        AssistantIntentClassifier.Analysis english = AssistantIntentClassifier.analyze(
                "What happened last time?"
        );

        assertEquals(AssistantIntent.EVENT_RECALL, dutch.intent());
        assertEquals(AssistantMode.GROUNDED, dutch.mode());
        assertEquals(AssistantIntent.EVENT_RECALL, english.intent());
    }

    @Test
    void shouldNotConfuseOrdinaryKnowledgeWithPersonalMemoryRecall() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze(
                "Weet je hoe ik een wolf tem in Minecraft?"
        );

        assertEquals(AssistantIntent.GAMEPLAY_HELP, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }

    @Test
    void shouldNotMistakeOrdinaryGameplayQuestionsForLiveState() {
        AssistantIntentClassifier.Analysis crafting = AssistantIntentClassifier.analyze(
                "Hoe craft ik dit item?"
        );
        AssistantIntentClassifier.Analysis diamonds = AssistantIntentClassifier.analyze(
                "Waar vind ik diamonds?"
        );

        assertEquals(AssistantIntent.GAMEPLAY_HELP, crafting.intent());
        assertEquals(AssistantMode.GROUNDED, crafting.mode());
        assertEquals(AssistantIntent.GAMEPLAY_HELP, diamonds.intent());
        assertEquals(AssistantMode.GROUNDED, diamonds.mode());
    }

    @Test
    void shouldKeepCasualConversationFast() {
        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze("hey bot, alles goed?");

        assertEquals(AssistantIntent.CONVERSATION, analysis.intent());
        assertEquals(AssistantMode.FAST, analysis.mode());
    }

    @Test
    void shouldRouteVanillaMobQuestionsToGameplayHelp() {
        AssistantIntentClassifier.Analysis camel = AssistantIntentClassifier.analyze(
                "ik wil weten hoe je een kameel temt"
        );
        AssistantIntentClassifier.Analysis wolf = AssistantIntentClassifier.analyze(
                "Hoe tem je een wolf in Minecraft?"
        );

        assertEquals(AssistantIntent.GAMEPLAY_HELP, camel.intent());
        assertEquals(AssistantMode.GROUNDED, camel.mode());
        assertEquals(AssistantIntent.GAMEPLAY_HELP, wolf.intent());
        assertEquals(AssistantMode.GROUNDED, wolf.mode());
    }

    @Test
    void shouldUseActiveDialogueToRouteAShortFollowUp() {
        AssistantDialogueContext dialogue = new AssistantDialogueContext(
                true,
                false,
                AssistantIntent.EVENT_RECALL,
                "wat gaat er mis haunty",
                "De chatgame lijkt vastgelopen.",
                ""
        );

        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze("haunty?", dialogue);

        assertEquals(AssistantIntent.CONTEXT_FOLLOWUP, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }

    @Test
    void shouldRouteEventDiagnosticQuestionsInsideActiveDialogue() {
        AssistantDialogueContext dialogue = new AssistantDialogueContext(
                true,
                true,
                AssistantIntent.CONVERSATION,
                "haunty?",
                "",
                ""
        );

        AssistantIntentClassifier.Analysis analysis = AssistantIntentClassifier.analyze("wat gaat er mis", dialogue);

        assertEquals(AssistantIntent.EVENT_RECALL, analysis.intent());
        assertEquals(AssistantMode.GROUNDED, analysis.mode());
    }

    @Test
    void shouldUseIndependentModelProfilesForEachAssistantLayer() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.model", "gpt-5.6-luna");
        config.set("openai.reasoning_effort", "low");
        config.set("openai.assistant.models.grounded.model", "gpt-5.6-terra");
        config.set("openai.assistant.models.grounded.reasoning_effort", "medium");
        config.set("openai.assistant.models.grounded.max_output_tokens", 320);
        config.set("openai.assistant.models.deliberate.model", "gpt-5.6-sol");
        config.set("openai.assistant.models.deliberate.reasoning_effort", "high");
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

        assertEquals(4_000, settings.maxInputTokens(AssistantMode.FAST));
        assertEquals(12_000, settings.maxInputTokens(AssistantMode.GROUNDED));
        assertEquals(24_000, settings.maxInputTokens(AssistantMode.DELIBERATE));
        assertEquals(12, settings.maxChunks());
        assertEquals(32_000, settings.maxEvidenceCharacters());
        assertEquals(240, settings.profileFor(AssistantMode.FAST).maxOutputTokens());
        assertEquals(480, settings.profileFor(AssistantMode.GROUNDED).maxOutputTokens());
        assertEquals(800, settings.profileFor(AssistantMode.DELIBERATE).maxOutputTokens());
    }

    @Test
    void shouldDefaultUnknownLanguageToDutchAndAllowOnlyConfiguredEnglishDetection() {
        assertEquals("nl", AssistantIntentClassifier.detectLanguage(
                "bonjour, comment ça va?", "nl", Set.of("nl", "en")
        ));
        assertEquals("en", AssistantIntentClassifier.detectLanguage(
                "How do I claim this plot?", "nl", Set.of("nl", "en")
        ));
        assertEquals("nl", AssistantIntentClassifier.detectLanguage(
                "How do I claim this plot?", "nl", Set.of("nl")
        ));
    }

    @Test
    void shouldDetectGermanOnlyWhenConfigured() {
        assertEquals("de", AssistantIntentClassifier.detectLanguage(
                "Wie baue ich eine Farm?", "nl", Set.of("nl", "en", "de")
        ));
        assertEquals("nl", AssistantIntentClassifier.detectLanguage(
                "Wie baue ich eine Farm?", "nl", Set.of("nl", "en")
        ));
    }

    @Test
    void shouldNormalizeConfiguredLanguageListAndKeepDutchFallback() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.routing.default_language", "English");
        config.set("openai.assistant.routing.allowed_languages", java.util.List.of("english", "french"));

        AssistantSettings settings = AssistantSettings.from(config);

        assertEquals("en", settings.defaultLanguage());
        assertEquals(Set.of("nl", "en"), settings.allowedLanguages());
        assertFalse(settings.languageAllowed("fr"));
    }
}
