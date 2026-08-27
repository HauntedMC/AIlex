package nl.hauntedmc.ailex.infrastructure.openai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.hauntedmc.ailex.util.LoggerUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OpenAiConversationRoleTest {

    @Test
    void shouldReplayActiveDialogueAsNativeResponsesApiRoles() {
        String prompt = "Current player request\n\n"
                + "[Active player-assistant dialogue]\n"
                + "previous_intent=context_followup\n"
                + "pending_answer=false\n"
                + "session_topics=lottery,rewards\n"
                + "user(remymine): Hoe werkt de lottery?\n"
                + "assistant(Haunty): Je koopt tickets voor de trekking.\n"
                + "user(remymine): En wanneer is die?\n\n"
                + "[Trusted knowledge source server.lottery — Lottery]\n"
                + "De trekking is op zondag.";

        try (MockedStatic<LoggerUtils> ignored = org.mockito.Mockito.mockStatic(LoggerUtils.class)) {
            OpenAiResponsesClient client = new OpenAiResponsesClient(
                    "key", "gpt-5.6-terra", mock(HttpClient.class)
            );
            JsonObject payload = JsonParser.parseString(client.createRequestBody("persona", prompt)).getAsJsonObject();
            JsonArray input = payload.getAsJsonArray("input");

            assertEquals(4, input.size());
            assertMessage(input, 0, "user", "Hoe werkt de lottery?");
            assertMessage(input, 1, "assistant", "Je koopt tickets voor de trekking.");
            assertMessage(input, 2, "user", "En wanneer is die?");

            JsonObject current = input.get(3).getAsJsonObject();
            assertEquals("user", current.get("role").getAsString());
            String currentText = current.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
            assertTrue(currentText.contains("Current player request"));
            assertTrue(currentText.contains("[Dialogue state]"));
            assertTrue(currentText.contains("session_topics=lottery,rewards"));
            assertTrue(currentText.contains("Trusted knowledge source server.lottery"));
            assertFalse(currentText.contains("user(remymine):"));
            assertFalse(currentText.contains("assistant(Haunty):"));
        }
    }

    @Test
    void shouldLeavePromptUntouchedWhenDialogueBlockHasNoRoleTurns() {
        String prompt = "Question\n\n[Active player-assistant dialogue]\npending_answer=false";
        ResponsesConversationInput.Parsed parsed = ResponsesConversationInput.parse(prompt);

        assertTrue(parsed.history().isEmpty());
        assertEquals(prompt, parsed.currentPrompt());
    }

    private void assertMessage(JsonArray input, int index, String role, String text) {
        JsonObject message = input.get(index).getAsJsonObject();
        assertEquals(role, message.get("role").getAsString());
        assertEquals(text, message.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
    }
}
