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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantToolRegistryTest {

    @Test
    void registryExposesOnlyPermittedCapabilities() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.tools.allowed", List.of("knowledge", "requester"));
        AssistantSettings settings = AssistantSettings.from(config);
        AssistantToolRegistry registry = new AssistantToolRegistry(
                mock(LocalKnowledgeIndex.class),
                mock(AssistantMemoryService.class),
                mock(AssistantExperienceMemoryService.class)
        );

        Set<String> names = registry.availableNames(settings);

        assertTrue(names.contains("search_knowledge"));
        assertTrue(names.contains("inspect_live"));
        assertFalse(names.contains("search_memory"));
        assertFalse(names.contains("search_memory_timeline"));
        assertFalse(names.contains("search_experience"));
    }

    @Test
    void registryExecutesKnowledgeToolAndPreservesEvidenceIdentity() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.tools.allowed", List.of("knowledge"));
        AssistantSettings settings = AssistantSettings.from(config);
        LocalKnowledgeIndex knowledge = mock(LocalKnowledgeIndex.class);
        when(knowledge.search(any(), any())).thenReturn(List.of(new LocalKnowledgeIndex.KnowledgeChunk(
                "knowledge.claims", "Claims", List.of("claim"), "Claims protect builds.",
                false, "survival", "official"
        )));
        AssistantToolRegistry registry = new AssistantToolRegistry(
                knowledge, mock(AssistantMemoryService.class), mock(AssistantExperienceMemoryService.class)
        );

        AssistantTool.ToolResult result = registry.execute(
                request(settings),
                new OpenAiToolPlanningClient.FunctionCall(
                        "search_knowledge", "{\"query\":\"protect my base\"}", "call-1"
                )
        );

        assertEquals(Set.of("knowledge.claims"), result.evidenceIds());
        assertTrue(result.output().contains("Claims protect builds"));
    }

    private static AssistantService.PreparedRequest request(AssistantSettings settings) {
        return new AssistantService.PreparedRequest(
                UUID.randomUUID().toString(), "Player", "AIlex", "1", "question", "", "",
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
    }
}
