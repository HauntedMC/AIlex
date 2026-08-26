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
                "De chatgame lijkt vastgelopen."
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
