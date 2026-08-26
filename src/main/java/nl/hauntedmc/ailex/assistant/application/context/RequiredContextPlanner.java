package nl.hauntedmc.ailex.assistant.application.context;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic context policy. It chooses the minimum trusted data sources required for a turn before any
 * expensive retrieval or Bukkit snapshot work is performed.
 */
public final class RequiredContextPlanner {

    public Plan plan(AssistantIntent intent, AssistantMode mode, String message, AssistantSettings settings) {
        AssistantIntent effectiveIntent = intent == null ? AssistantIntent.CONVERSATION : intent;
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean knowledge = settings.toolAllowed("knowledge") && switch (effectiveIntent) {
            case SERVER_FACT, GAMEPLAY_HELP, SUPPORT -> true;
            case CONTEXT_FOLLOWUP -> mode != AssistantMode.FAST && containsServerTopic(text);
            default -> false;
        };
        boolean durableMemory = settings.toolAllowed("session") && switch (effectiveIntent) {
            case MEMORY_RECALL, EVENT_RECALL, CONTEXT_FOLLOWUP -> true;
            case CONVERSATION -> hasPreferenceOrPersonalSignal(text);
            default -> false;
        };
        boolean eventMemory = settings.toolAllowed("session") && effectiveIntent == AssistantIntent.EVENT_RECALL;

        EnumSet<LiveSource> live = EnumSet.noneOf(LiveSource.class);
        if (effectiveIntent == AssistantIntent.LIVE_STATE) {
            if (settings.toolAllowed("requester") && requesterSignal(text)) {
                live.add(LiveSource.REQUESTER);
            }
            if (settings.toolAllowed("world") && worldSignal(text)) {
                live.add(LiveSource.WORLD);
            }
            if (settings.toolAllowed("server") && serverSignal(text)) {
                live.add(LiveSource.SERVER);
            }
            if (settings.toolAllowed("nearby") && nearbySignal(text)) {
                live.add(LiveSource.NEARBY);
            }
            if (settings.toolAllowed("npc") && npcSignal(text)) {
                live.add(LiveSource.NPC);
            }
            if (live.isEmpty()) {
                // Unknown live-state wording gets the two safest compact sources, never a full snapshot dump.
                if (settings.toolAllowed("requester")) {
                    live.add(LiveSource.REQUESTER);
                }
                if (settings.toolAllowed("world")) {
                    live.add(LiveSource.WORLD);
                }
            }
        }
        return new Plan(knowledge, durableMemory, eventMemory, Set.copyOf(live));
    }

    private boolean requesterSignal(String text) {
        return containsAny(text, "health", "leven", "hp", "honger", "food", "gamemode", "game mode",
                "level", "xp", "item", "hand", "holding", "effect", "armor", "pantser", "ping", "playtime");
    }

    private boolean worldSignal(String text) {
        return containsAny(text, "waar", "where", "hier", "here", "positie", "position", "coord", "locatie",
                "location", "world", "wereld", "biome", "weer", "weather", "tijd", "time", "light", "licht");
    }

    private boolean serverSignal(String text) {
        return containsAny(text, "online", "spelers", "players", "tps", "mspt", "server", "uptime", "versie", "version");
    }

    private boolean nearbySignal(String text) {
        return containsAny(text, "dichtbij", "nearby", "around", "om me heen", "naast me", "entities", "mobs");
    }

    private boolean npcSignal(String text) {
        return containsAny(text, "jij", "jou", "jouw", "you", "your", "npc", "bot", "waar sta je", "where are you");
    }

    private boolean containsServerTopic(String text) {
        return containsAny(text, "server", "command", "/", "rank", "plot", "claim", "vote", "economy", "survival",
                "creative", "minigame", "haunted");
    }

    private boolean hasPreferenceOrPersonalSignal(String text) {
        return containsAny(text, "onthoud", "remember", "ik hou", "ik vind", "mijn favoriete", "i like", "i love",
                "i prefer", "my favorite");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public enum LiveSource {
        REQUESTER,
        WORLD,
        SERVER,
        NEARBY,
        NPC
    }

    public record Plan(boolean knowledge, boolean durableMemory, boolean eventMemory, Set<LiveSource> liveSources) {
        public Plan {
            liveSources = liveSources == null ? Set.of() : Set.copyOf(liveSources);
        }

        public boolean live() {
            return !liveSources.isEmpty();
        }
    }
}
