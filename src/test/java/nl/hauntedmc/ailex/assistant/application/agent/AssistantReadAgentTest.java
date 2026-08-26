package nl.hauntedmc.ailex.assistant.application.agent;

import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.application.context.RequiredContextPlanner;
import nl.hauntedmc.ailex.assistant.application.routing.AssistantIntentClassifier;
import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.LocalKnowledgeIndex;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantExperienceMemoryService;
import nl.hauntedmc.ailex.assistant.infrastructure.memory.AssistantMemoryService;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantReadAgentTest {

    @Test
    void plannerCanRequestMissingKnowledgeAndReturnsGroundableEvidence() {
        Fixture fixture = fixture();
        when(fixture.planner().plan(anyList(), anyList(), any(Duration.class))).thenReturn(
                new OpenAiToolPlanningClient.PlanningResponse(
                        List.of(new OpenAiToolPlanningClient.FunctionCall(
                                "search_knowledge", "{\"query\":\"claims protection\"}", "call-1"
                        )), "", true, 37, 8
                )
        );
        LocalKnowledgeIndex.KnowledgeChunk chunk = new LocalKnowledgeIndex.KnowledgeChunk(
                "claims.0", "Claims", List.of("claim"), "Claims protect builds.", false, "survival", "official"
        );
        when(fixture.knowledge().search(any(), any())).thenReturn(List.of(chunk));

        AssistantReadAgent.AgentEnrichment result = fixture.agent().enrich(
                fixture.request(), List.of(), 1, Duration.ofSeconds(10)
        );

        assertEquals(1, result.modelCalls());
        assertEquals(1, result.toolCalls());
        assertEquals(37, result.plannerInputTokens());
        assertEquals(8, result.plannerOutputTokens());
        assertTrue(result.evidenceIds().contains("claims.0"));
        assertTrue(result.context().contains("Claims protect builds"));
    }

    @Test
    void emptyKnowledgeResultProducesDeterministicNegativeEvidence() {
        Fixture fixture = fixture();
        when(fixture.planner().plan(anyList(), anyList(), any(Duration.class))).thenReturn(
                new OpenAiToolPlanningClient.PlanningResponse(
                        List.of(new OpenAiToolPlanningClient.FunctionCall(
                                "search_knowledge", "{\"query\":\"unknown custom fact\"}", "call-1"
                        )), "", true, 10, 3
                )
        );
        when(fixture.knowledge().search(any(), any())).thenReturn(List.of());

        AssistantReadAgent.AgentEnrichment result = fixture.agent().enrich(
                fixture.request(), List.of(), 1, Duration.ofSeconds(10)
        );

        assertEquals(Set.of("knowledge.none"), result.evidenceIds());
        assertTrue(result.context().contains("No reviewed knowledge matched"));
    }

    @Test
    void deadlineReservePreventsPlannerFromConsumingFinalAnswerBudget() {
        Fixture fixture = fixture();

        AssistantReadAgent.AgentEnrichment result = fixture.agent().enrich(
                fixture.request(), List.of(), 2, Duration.ofMillis(900)
        );

        assertEquals(0, result.modelCalls());
        assertEquals(0, result.toolCalls());
        verify(fixture.planner(), never()).plan(anyList(), anyList(), any(Duration.class));
    }

    private static Fixture fixture() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.agent.enabled", true);
        config.set("openai.assistant.agent.max_tool_calls_per_round", 2);
        config.set("openai.assistant.tools.allowed", List.of("knowledge", "session", "requester", "world"));
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        LocalKnowledgeIndex knowledge = mock(LocalKnowledgeIndex.class);
        AssistantMemoryService memory = mock(AssistantMemoryService.class);
        AssistantExperienceMemoryService experience = mock(AssistantExperienceMemoryService.class);
        OpenAiToolPlanningClient planner = mock(OpenAiToolPlanningClient.class);
        AssistantReadAgent agent = new AssistantReadAgent(plugin, knowledge, memory, experience, planner);
        AssistantSettings settings = AssistantSettings.from(config);
        AssistantService.PreparedRequest request = new AssistantService.PreparedRequest(
                UUID.randomUUID().toString(), "Player", "AIlex", "1",
                "Vertel me hoe claims werken op HauntedMC", "", "player request",
                new AssistantIntentClassifier.Analysis(AssistantIntent.SERVER_FACT, AssistantMode.GROUNDED, "nl"),
                settings,
                new RequiredContextPlanner.Plan(true, false, false, Set.of()),
                true,
                new AssistantService.LiveSnapshot(List.of(), Set.of()),
                "",
                AssistantDialogueContext.empty(),
                false,
                System.nanoTime()
        );
        return new Fixture(agent, planner, knowledge, request);
    }

    private record Fixture(
            AssistantReadAgent agent,
            OpenAiToolPlanningClient planner,
            LocalKnowledgeIndex knowledge,
            AssistantService.PreparedRequest request
    ) {
    }
}
