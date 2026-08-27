from pathlib import Path

# Deterministically recognize named-player memory recall while protecting known gameplay/server topics.
path = Path('src/main/java/nl/hauntedmc/ailex/assistant/application/routing/AssistantIntentClassifier.java')
text = path.read_text()
old = '''    private static boolean isDirectMemoryRecall(String message) {
        return containsAnyPhrase(message,
                "wat weet je van mij", "wat weet je over mij", "wat herinner je van mij", "wat herinner je over mij",
                "wat heb je onthouden", "wat onthoud je van mij", "herinner je mij", "herinner je je mij",
                "what do you remember about me", "what do you know about me", "what have you remembered about me",
                "what have you saved about me", "do you remember me"
        );
    }
'''
new = '''    private static boolean isDirectMemoryRecall(String message) {
        return containsAnyPhrase(message,
                "wat weet je van mij", "wat weet je over mij", "wat herinner je van mij", "wat herinner je over mij",
                "wat heb je onthouden", "wat onthoud je van mij", "herinner je mij", "herinner je je mij",
                "what do you remember about me", "what do you know about me", "what have you remembered about me",
                "what have you saved about me", "do you remember me"
        ) || isNamedPlayerMemoryRecall(message);
    }

    private static boolean isNamedPlayerMemoryRecall(String message) {
        String normalized = cleanForRouting(message).replaceAll("[?!.,]+$", "").trim();
        if (containsAny(normalized, GAMEPLAY_WORDS) || containsAny(normalized, SERVER_WORDS)) {
            return false;
        }
        return normalized.matches(".*\\bwat weet je (?:over|van) [a-z0-9_]{3,16}(?: haunty| ailex)?$")
                || normalized.matches(".*\\bwat herinner je (?:over|van) [a-z0-9_]{3,16}(?: haunty| ailex)?$")
                || normalized.matches(".*\\bwhat do you know about [a-z0-9_]{3,16}(?: haunty| ailex)?$")
                || normalized.matches(".*\\bwhat do you remember about [a-z0-9_]{3,16}(?: haunty| ailex)?$");
    }
'''
if text.count(old) != 1:
    raise SystemExit(f'classifier memory recall match count={text.count(old)}')
path.write_text(text.replace(old, new, 1))

# Only attach cross-player public episodic observations when the recall wording actually targets a third party.
path = Path('src/main/java/nl/hauntedmc/ailex/assistant/application/context/RequiredContextPlanner.java')
text = path.read_text()
old = '''        // A memory-recall question asks what Haunty remembers, not only what was promoted into a semantic profile.
        // Include bounded episodic observations too so public conversations and meaningful events are recallable without
        // weakening player-memory visibility rules. Ordinary personalized conversation still avoids this extra context.
        boolean eventMemory = settings.toolAllowed("session")
                && (effectiveIntent == AssistantIntent.MEMORY_RECALL || effectiveIntent == AssistantIntent.EVENT_RECALL);
'''
new = '''        // Event recall always needs the timeline. Memory recall adds public NPC-observed events only when the wording
        // explicitly targets a third party; broad self/profile recall stays player-scoped and cannot be polluted by unrelated
        // public conversations that the same NPC happened to witness.
        boolean eventMemory = settings.toolAllowed("session")
                && (effectiveIntent == AssistantIntent.EVENT_RECALL
                || effectiveIntent == AssistantIntent.MEMORY_RECALL && publicObservationRecallSignal(text));
'''
if text.count(old) != 1:
    raise SystemExit(f'planner event memory match count={text.count(old)}')
text = text.replace(old, new, 1)
anchor = '''    private boolean requesterSignal(String text) {
'''
method = '''    private boolean publicObservationRecallSignal(String text) {
        if (containsAny(text,
                "over mij", "van mij", "about me", "about myself", "remember me", "onthoud je van mij"
        )) {
            return false;
        }
        return containsAny(text,
                "wat weet je over ", "wat weet je van ", "wat herinner je over ", "wat herinner je van ",
                "what do you know about ", "what do you remember about "
        );
    }

'''
if text.count(anchor) != 1:
    raise SystemExit(f'planner method anchor count={text.count(anchor)}')
path.write_text(text.replace(anchor, method + anchor, 1))

# Semantic refinement must preserve the deterministic event-memory decision rather than broadening every memory recall.
path = Path('src/main/java/nl/hauntedmc/ailex/assistant/application/routing/SemanticNeedPlanner.java')
text = path.read_text()
old = '''        boolean events = safe.eventMemory() || decision.intent() == AssistantIntent.EVENT_RECALL
                || decision.intent() == AssistantIntent.MEMORY_RECALL;
'''
new = '''        boolean events = safe.eventMemory() || decision.intent() == AssistantIntent.EVENT_RECALL;
'''
if text.count(old) != 1:
    raise SystemExit(f'semantic event memory match count={text.count(old)}')
path.write_text(text.replace(old, new, 1))
