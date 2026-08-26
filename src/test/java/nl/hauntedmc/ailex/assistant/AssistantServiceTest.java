package nl.hauntedmc.ailex.assistant;

import com.google.gson.JsonObject;
import nl.hauntedmc.ailex.AIlexPlugin;
import nl.hauntedmc.ailex.assistant.application.AssistantService;
import nl.hauntedmc.ailex.assistant.domain.AssistantReply;
import nl.hauntedmc.ailex.infrastructure.openai.OpenAiResponsesClient;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    @Test
    void shouldAcceptVanillaGameplayAnswerWithoutLocalKnowledgeEvidence() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        OpenAiResponsesClient client = mock(OpenAiResponsesClient.class);
        Player player = mock(Player.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.enabled", true);
        config.set("openai.assistant.models.grounded.model", "gpt-5.6-terra");
        config.set("openai.assistant.verification.enabled", true);
        config.set("openai.assistant.verification.minimum_confidence", "medium");
        config.set("openai.assistant.observability.enabled", false);
        config.set("openai.assistant.tools.read_only", true);
        config.set("openai.assistant.tools.allowed", List.of("knowledge"));
        config.set("openai.knowledge.enabled", true);
        config.set("openai.knowledge.prompt", "Official HauntedMC server information.");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getOpenAiResponsesClient()).thenReturn(client);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("remymine");
        when(client.getStructuredChatResponse(anyString(), anyString(), any(JsonObject.class), any()))
                .thenReturn("""
                        {"lines":["Geef een wolf botten tot hij hartjes toont; dan is hij getemd."],
                        "confidence":"high","evidence_ids":[],"handoff":"","memory_candidates":[]}
                        """);

        AssistantService service = new AssistantService(plugin);
        AssistantService.PreparedRequest request = service.prepare(
                player, null, "Hoe tem je een wolf in Minecraft?", "Je bent Haunty.", "Beantwoord de vraag."
        );
        AssistantReply reply = service.respond(request);

        assertEquals(List.of("Geef een wolf botten tot hij hartjes toont; dan is hij getemd."), reply.lines());
        assertTrue(reply.handoff().isBlank());
        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(client).getStructuredChatResponse(systemPrompt.capture(), anyString(), any(JsonObject.class), any());
        assertTrue(systemPrompt.getValue().contains("never a limit on your capabilities"));
    }

    @Test
    void shouldTreatAiLexCollectedMetadataAsLiveEvidence() {
        AIlexPlugin plugin = mock(AIlexPlugin.class);
        OpenAiResponsesClient client = mock(OpenAiResponsesClient.class);
        Player player = mock(Player.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("openai.assistant.enabled", true);
        config.set("openai.assistant.observability.enabled", false);
        config.set("openai.assistant.tools.read_only", true);
        config.set("openai.assistant.tools.allowed", List.of());
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getOpenAiResponsesClient()).thenReturn(client);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("remymine");
        when(client.getStructuredChatResponse(anyString(), anyString(), any(JsonObject.class), any()))
                .thenReturn("""
                        {"lines":["Je houdt een diamond_sword vast."],"confidence":"high",
                        "evidence_ids":["live.context"],"handoff":"","memory_candidates":[]}
                        """);

        AssistantService service = new AssistantService(plugin);
        AssistantService.PreparedRequest request = service.prepare(
                player, null, "Wat houd ik vast?", "Je bent Haunty.", "Beantwoord de vraag.",
                "player_main_hand=minecraft:diamond_swordx1"
        );
        AssistantReply reply = service.respond(request);

        assertEquals(List.of("Je houdt een diamond_sword vast."), reply.lines());
        assertTrue(reply.handoff().isBlank());
    }
}
