package nl.hauntedmc.ailex.assistant.application.context;

import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic context policy. It exposes capabilities broadly while selecting only the safe source families
 * that can materially help the current turn.
 */
public final class RequiredContextPlanner {

    public Plan plan(AssistantIntent intent, AssistantMode mode, String message, AssistantSettings settings) {
        AssistantIntent effectiveIntent = intent == null ? AssistantIntent.CONVERSATION : intent;
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean knowledge = settings.toolAllowed("knowledge") && switch (effectiveIntent) {
            case SERVER_FACT, KNOWLEDGE_DISCOVERY, GAMEPLAY_HELP, SUPPORT -> true;
            case CONTEXT_FOLLOWUP -> mode != AssistantMode.FAST && containsServerTopic(text);
            default -> false;
        };

        // Long-term memory is not a default prompt attachment. Explicit recall/contextual work gets it automatically;
        // ordinary conversation/gameplay only gets it when personalization can materially improve the response.
        boolean durableMemory = settings.toolAllowed("session") && switch (effectiveIntent) {
            case MEMORY_RECALL, EVENT_RECALL, CONTEXT_FOLLOWUP, SERVER_FACT -> true;
            case CONVERSATION, GAMEPLAY_HELP -> personalizationSignal(text);
            case LIVE_STATE, KNOWLEDGE_DISCOVERY, SAFETY, SUPPORT -> false;
        };
        boolean eventMemory = settings.toolAllowed("session") && effectiveIntent == AssistantIntent.EVENT_RECALL;

        EnumSet<LiveSource> live = EnumSet.noneOf(LiveSource.class);
        if (effectiveIntent == AssistantIntent.LIVE_STATE) {
            if (settings.toolAllowed("requester") && requesterSignal(text)) {
                live.add(LiveSource.REQUESTER);
            }
            if (settings.toolAllowed("requester") && inventorySignal(text)) {
                live.add(LiveSource.INVENTORY);
            }
            if (settings.toolAllowed("world") && worldSignal(text)) {
                live.add(LiveSource.WORLD);
            }
            if (settings.toolAllowed("world") && targetSignal(text)) {
                live.add(LiveSource.TARGET);
            }
            if (settings.toolAllowed("nearby") && nearbySignal(text)) {
                live.add(LiveSource.NEARBY);
            }
            if (settings.toolAllowed("server") && serverSignal(text)) {
                live.add(LiveSource.SERVER);
            }
            if (settings.toolAllowed("npc") && npcSignal(text)) {
                live.add(LiveSource.NPC);
            }
            if (live.isEmpty()) {
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

    private boolean personalizationSignal(String text) {
        return containsAny(text,
                "mijn ", "mij ", "mezelf", "voor mij", "over mij", "wat zal ik", "wat moet ik", "wat raad je",
                "advies", "aanraden", "aanbevel", "suggest", "recommend", "for me", "about me", "my ", "me ",
                "what should i", "what do you suggest", "what would you recommend", "remember", "onthoud",
                "vergeet", "forget", "vorige keer", "last time", "eerder", "previously", "mijn project",
                "my project", "mijn doel", "my goal", "favoriet", "favorite", "voorkeur", "prefer"
        );
    }

    private boolean requesterSignal(String text) {
        return containsAny(text,
                "health", "gezondheid", "leven", "hp", "honger", "food", "gamemode", "game mode",
                "level", "xp", "ervaring", "experience", "item", "hand", "holding", "vasthoud", "vast", "effect",
                "armor", "armour", "pantser", "ping", "latency", "playtime", "speeltijd", "gespeeld", "saturation",
                "air", "lucht", "fire", "brand", "flying", "vliegen", "swimming", "zwemmen", "sprinting", "rennen",
                "rank", "balance", "saldo", "money", "geld", "currency", "valuta", "credits", "crowns", "essence",
                "claim", "combattag", "combat-tag", "tagged", "autopickup", "fly", "god", "vanish", "queue",
                "lottery", "loterij", "friends", "vrienden", "perk", "perks"
        );
    }

    private boolean inventorySignal(String text) {
        return containsAny(text,
                "inventory", "inventaris", "equipment", "uitrusting", "armor", "armour", "pantser", "hotbar", "slot",
                "storage", "opslag", "backpack", "rugzak"
        );
    }

    private boolean worldSignal(String text) {
        return containsAny(text, "waar", "where", "hier", "here", "positie", "position", "coord", "locatie",
                "location", "world", "wereld", "biome", "bioom", "weer", "weather", "tijd", "time", "light",
                "licht", "difficulty", "moeilijkheid", "environment", "omgeving", "dimension", "dimensie", "facing",
                "richting", "hoogte", "height", "sea level", "zeeniveau");
    }

    private boolean targetSignal(String text) {
        return containsAny(text, "kijk", "looking", "target", "blok", "block", "voor me", "in front", "ray");
    }

    private boolean serverSignal(String text) {
        return containsAny(text, "online", "tps", "mspt", "performance", "lag", "server", "uptime", "versie",
                "version");
    }

    private boolean nearbySignal(String text) {
        return containsAny(text, "dichtbij", "nearby", "around", "near me", "om me heen", "naast me", "entities",
                "entity", "mobs", "mob");
    }

    private boolean npcSignal(String text) {
        return containsAny(text, "jij", "jou", "jouw", "you", "your", "npc", "bot", "waar sta je", "where are you",
                "wat doe je", "what are you doing");
    }

    private boolean containsServerTopic(String text) {
        return containsAny(text, "server", "command", "/", "rank", "plot", "claim", "vote", "economy", "survival",
                "creative", "minigame", "haunted", "feit", "fact");
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
        INVENTORY,
        WORLD,
        TARGET,
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
