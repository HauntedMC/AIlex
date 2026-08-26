package nl.hauntedmc.ailex.assistant.chat;

import nl.hauntedmc.ailex.npc.NPC;
import nl.hauntedmc.ailex.npc.NPCProperties;

/** Immutable addressed assistant endpoint shared by physical NPC and standalone-chat modes. */
public record AssistantChatTarget(
        int id,
        String name,
        String displayName,
        String systemPrompt,
        String userPromptTemplate,
        NPC npc
) {

    public static final int STANDALONE_ID = -1;

    public AssistantChatTarget {
        name = clean(name, "AIlex");
        displayName = clean(displayName, name);
        systemPrompt = clean(systemPrompt, NPCProperties.DEFAULT_SYSTEM_PROMPT);
        userPromptTemplate = clean(userPromptTemplate, NPCProperties.DEFAULT_USER_PROMPT_TEMPLATE);
    }

    public static AssistantChatTarget fromNpc(NPC npc) {
        if (npc == null) {
            throw new IllegalArgumentException("npc cannot be null");
        }
        return new AssistantChatTarget(
                npc.getId(), npc.getName(), npc.getDisplayName(), npc.getSystemPrompt(), npc.getUserPromptTemplate(), npc
        );
    }

    public static AssistantChatTarget standalone(String mention, String displayName, String systemPrompt) {
        return new AssistantChatTarget(
                STANDALONE_ID, mention, displayName, systemPrompt, NPCProperties.DEFAULT_USER_PROMPT_TEMPLATE, null
        );
    }

    public String userPrompt(String playerName, String message) {
        String source = clean(playerName, "player");
        String chat = message == null ? "" : message;
        return userPromptTemplate
                .replace("{player_name}", source)
                .replace("{player_display_name}", source)
                .replace("{npc_name}", name)
                .replace("{npc_display_name}", displayName)
                .replace("{chat_message}", chat);
    }

    public boolean physicalNpc() {
        return npc != null;
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
