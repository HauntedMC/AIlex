package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.application.context.ContextCompiler;
import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextCompilerTest {

    @Test
    void shouldKeepContextWithinTheRouteBudget() {
        ContextCompiler compiler = new ContextCompiler();
        String hugeHistory = "old-chat ".repeat(5_000);
        String hugeEvidence = "knowledge ".repeat(2_000);

        ContextCompiler.CompiledContext compiled = compiler.compile(
                AssistantMode.GROUNDED,
                1_200,
                "Current request: why is the chatgame broken?",
                AssistantDialogueContext.empty(),
                "server_tps=20 player_world=survival",
                "Player prefers Dutch answers.",
                List.of(new ContextCompiler.ContextSource("server.tryme", "Tryme", hugeEvidence)),
                hugeHistory
        );

        assertTrue(compiled.estimatedTokens() <= 1_220);
        assertTrue(compiled.prompt().contains("Current request"));
        assertTrue(compiled.prompt().contains("Trusted live Minecraft context"));
        assertTrue(compiled.prompt().contains("Saved assistant memory"));
    }

    @Test
    void shouldPrioritiseActiveDialogueBeforeLargeHistoricalChat() {
        ContextCompiler compiler = new ContextCompiler();
        AssistantDialogueContext dialogue = new AssistantDialogueContext(
                true,
                false,
                AssistantIntent.EVENT_RECALL,
                "wat gaat er mis haunty",
                "De vorige ronde lijkt vastgelopen."
        );

        ContextCompiler.CompiledContext compiled = compiler.compile(
                AssistantMode.GROUNDED,
                700,
                "Bericht van speler: waarom?",
                dialogue,
                "",
                "",
                List.of(),
                "irrelevant ".repeat(3_000)
        );

        assertTrue(compiled.prompt().contains("previous_intent=event_recall"));
        assertTrue(compiled.prompt().contains("wat gaat er mis haunty"));
        assertTrue(compiled.tokensBySource().containsKey("dialogue"));
    }

    @Test
    void shouldRetainTheNewestTailWhenHistoryMustBeClipped() {
        ContextCompiler compiler = new ContextCompiler();
        String history = "very-old ".repeat(1_000) + "LATEST_IMPORTANT_MESSAGE";

        ContextCompiler.CompiledContext compiled = compiler.compile(
                AssistantMode.FAST,
                320,
                "Current request",
                AssistantDialogueContext.empty(),
                "",
                "",
                List.of(),
                history
        );

        assertTrue(compiled.prompt().contains("LATEST_IMPORTANT_MESSAGE"));
    }
}
