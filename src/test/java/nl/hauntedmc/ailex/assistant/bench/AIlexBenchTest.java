package nl.hauntedmc.ailex.assistant.bench;

import nl.hauntedmc.ailex.assistant.application.inference.AssistantGenerationPolicy;
import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantReply;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryRecord;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryScope;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryTruthResolver;
import nl.hauntedmc.ailex.assistant.proactive.ProactiveChatSettings;
import nl.hauntedmc.ailex.assistant.proactive.ProactiveInterventionPolicy;
import nl.hauntedmc.ailex.assistant.proactive.SocialConversationGraph;
import nl.hauntedmc.ailex.assistant.security.AssistantDataSafety;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deterministic intelligence regression benchmark. It intentionally avoids network/model calls so CI can measure the
 * control-plane properties that should never regress: routing, selective memory extraction, temporal truth, grounding,
 * privacy boundaries and proactive intervention precision.
 */
class AIlexBenchTest {

    @Test
    void cognitiveControlPlaneShouldPassAllReferenceCases() {
        BenchScore score = new BenchScore();
        score.add("routing",
                intent("Hoe werkt /plot claim op HauntedMC?", AssistantIntent.SERVER_FACT),
                intent("Wat is mijn rank?", AssistantIntent.LIVE_STATE),
                intent("Wat weet je van mij?", AssistantIntent.MEMORY_RECALL),
                intent("Wat gebeurde er vorige keer?", AssistantIntent.EVENT_RECALL),
                intent("hey bot, alles goed?", AssistantIntent.CONVERSATION));

        score.add("memory-extraction",
                AssistantGenerationPolicy.hasDurableMemorySignal("Ik heb twee katten."),
                AssistantGenerationPolicy.hasDurableMemorySignal("Mijn favoriete gamemode is Survival."),
                !AssistantGenerationPolicy.hasDurableMemorySignal("mooie spawn vandaag"));

        score.add("grounding",
                fullyGroundedReply().valid(),
                !partiallyGroundedReply().valid());

        MemoryTruthResolver.ResolvedClaim temporal = new MemoryTruthResolver().resolve(
                List.of(
                        memory("old", "survival", 1_000L, 10_000L, ""),
                        memory("new", "creative", 10_000L, 0L, "old")
                ),
                20_000L
        ).getFirst();
        score.add("temporal-memory", "creative".equals(temporal.primary().object()));

        score.add("privacy",
                AssistantDataSafety.forbiddenDurableMemory("server", "10.0.0.12"),
                AssistantDataSafety.forbiddenDurableMemory("contact", "player@example.org"),
                AssistantDataSafety.forbiddenDurableMemory("location", "120 64 -800"),
                !AssistantDataSafety.forbiddenDurableMemory("favorite_gamemode", "survival"));

        Player alex = player("Alex");
        Player sam = player("Sam");
        ProactiveChatSettings.QuestionSettings questions = new ProactiveChatSettings.QuestionSettings(
                true, 1.0D, 45_000L, 2, 180_000L, 2.5D
        );
        SocialConversationGraph graph = new SocialConversationGraph();
        graph.observe(alex, "Sam, kom je naar spawn?", List.of(alex, sam), 1_000L, 180_000L);
        score.add("intervention",
                !ProactiveInterventionPolicy.shouldAnswerQuestion(
                        alex, "waar ben je dan?", List.of(alex, sam), false, graph, 2_000L, questions
                ),
                ProactiveInterventionPolicy.shouldAnswerQuestion(
                        alex, "Weet iemand hoe /vote werkt?", List.of(alex, sam), true, graph, 2_000L, questions
                ));

        assertEquals(score.total(), score.passed(), score.summary());
    }

    private static boolean intent(String message, AssistantIntent expected) {
        return AssistantIntentClassifier.analyze(message).intent() == expected;
    }

    private static AssistantReply fullyGroundedReply() {
        return new AssistantReply(
                List.of("Claims beschermen je build."), Set.of("knowledge.claims"), "high", "", List.of(),
                Map.of(0, Set.of("knowledge.claims")), true
        );
    }

    private static AssistantReply partiallyGroundedReply() {
        return new AssistantReply(
                List.of("Claims beschermen je build.", "Dit is altijd gratis."), Set.of("knowledge.claims"),
                "high", "", List.of(), Map.of(0, Set.of("knowledge.claims")), true
        );
    }

    private static MemoryRecord memory(String id, String value, long assertedAt, long expiresAt, String supersedes) {
        return new MemoryRecord(
                id, MemoryScope.PLAYER, "player", "", MemoryKind.PREFERENCE, "favorite_gamemode", value,
                0.99D, 0.90D, "player-explicit", "player", assertedAt, assertedAt, 0L, expiresAt,
                supersedes, Set.of("semantic", "preference")
        );
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        return player;
    }

    private static final class BenchScore {
        private int passed;
        private int total;
        private final StringBuilder categories = new StringBuilder();

        void add(String category, boolean... cases) {
            int categoryPassed = 0;
            for (boolean result : cases) {
                total++;
                if (result) {
                    passed++;
                    categoryPassed++;
                }
            }
            if (!categories.isEmpty()) {
                categories.append(", ");
            }
            categories.append(category).append('=').append(categoryPassed).append('/').append(cases.length);
        }

        int passed() {
            return passed;
        }

        int total() {
            return total;
        }

        String summary() {
            return "AIlexBench " + passed + '/' + total + " [" + categories + ']';
        }
    }
}
