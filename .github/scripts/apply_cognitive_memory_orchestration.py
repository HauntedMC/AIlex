from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path(__file__).resolve().parents[2]

# AssistantService: typed memory evidence, authoritative empty recall, intent-specific fallbacks.
path = root / "src/main/java/nl/hauntedmc/ailex/assistant/application/AssistantService.java"
text = path.read_text()
text = replace_once(
    text,
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;\n",
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryEvidenceId;\n"
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;\n",
    "AssistantService MemoryEvidenceId import",
)
text = replace_once(
    text,
    "memory = memoryContext(playerId, npcMemoryId, message, plan.eventMemory());",
    "memory = memoryContext(playerId, npcMemoryId, message, plan.eventMemory(), analysis.intent());",
    "AssistantService prepare memoryContext",
)
text = replace_once(
    text,
    "memory = memoryContext(playerId, request.npcMemoryId(), request.message(), refinedPlan.eventMemory());",
    "memory = memoryContext(playerId, request.npcMemoryId(), request.message(), refinedPlan.eventMemory(), refinedAnalysis.intent());",
    "AssistantService refined memoryContext",
)
text = replace_once(
    text,
    "private String memoryContext(UUID playerId, String npcId, String query, boolean includeEvents) {",
    "private String memoryContext(\n"
    "            UUID playerId, String npcId, String query, boolean includeEvents, AssistantIntent intent\n"
    "    ) {",
    "AssistantService memoryContext signature",
)
text = replace_once(
    text,
    "output.append(\"- evidence_id=memory.\").append(record.id()).append(' ').append(record.value());",
    "output.append(\"- evidence_id=\").append(MemoryEvidenceId.forRecord(record)).append(' ').append(record.value());",
    "AssistantService typed event evidence",
)
text = replace_once(
    text,
    """        return output.toString().trim();
    }

    private AssistantReply complete(""",
    """        if (output.isEmpty() && intent == AssistantIntent.MEMORY_RECALL) {
            return "evidence_id=memory.none\\nNo relevant scoped player memory matched this recall request.";
        }
        if (output.isEmpty() && intent == AssistantIntent.EVENT_RECALL) {
            return "evidence_id=memory.timeline.none\\nNo relevant scoped event memory matched this recall request.";
        }
        return output.toString().trim();
    }

    private AssistantReply complete(""",
    "AssistantService authoritative empty memory",
)
text = replace_once(
    text,
    """        } else if (request.analysis().intent() == AssistantIntent.KNOWLEDGE_DISCOVERY) {
            text = "Ik kon nu geen betrouwbaar serverfeit ophalen; vraag me gerust naar Survival, Creative, events, ranks of commands.";
        } else if (request.analysis().intent() == AssistantIntent.CONVERSATION
                || request.analysis().intent() == AssistantIntent.CONTEXT_FOLLOWUP) {
            text = "Sorry, ik kreeg daar geen bruikbaar antwoord op. Kun je het nog eens kort zeggen?";
        } else if ("nl".equals(request.analysis().language())) {
            text = "Dat kan ik nu niet betrouwbaar verifiëren. Kijk in /help of vraag een stafflid om de actuele info.";
        } else {
            text = "I can't verify that reliably right now. Please check /help or ask staff for current information.";
        }
""",
    """        } else if (request.analysis().intent() == AssistantIntent.KNOWLEDGE_DISCOVERY) {
            text = "Ik kon nu geen betrouwbaar serverfeit ophalen; vraag me gerust naar Survival, Creative, events, ranks of commands.";
        } else if (request.analysis().intent() == AssistantIntent.MEMORY_RECALL) {
            text = "Dat heb ik nu niet in mijn geheugen teruggevonden.";
        } else if (request.analysis().intent() == AssistantIntent.EVENT_RECALL) {
            text = "Dat recente gesprek of moment kan ik nu niet terugvinden.";
        } else if (request.analysis().intent() == AssistantIntent.LIVE_STATE) {
            text = "Ik kan die actuele status nu niet betrouwbaar uitlezen.";
        } else if (request.analysis().intent() == AssistantIntent.CONVERSATION
                || request.analysis().intent() == AssistantIntent.CONTEXT_FOLLOWUP) {
            text = "Sorry, ik kreeg daar geen bruikbaar antwoord op. Kun je het nog eens kort zeggen?";
        } else if ("nl".equals(request.analysis().language())) {
            text = "Dat serverfeit kan ik nu niet betrouwbaar verifiëren. Kijk eventueel in /help voor actuele serverinformatie.";
        } else {
            text = "I can't verify that server fact reliably right now. Check /help for current server information if needed.";
        }
""",
    "AssistantService route-specific fallbacks",
)
path.write_text(text)

# AssistantToolRegistry: preserve memory provenance when the read agent retrieves extra records.
path = root / "src/main/java/nl/hauntedmc/ailex/assistant/application/agent/AssistantToolRegistry.java"
text = path.read_text()
text = replace_once(
    text,
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;\n",
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryEvidenceId;\n"
    "import nl.hauntedmc.ailex.assistant.infrastructure.memory.MemoryKind;\n",
    "AssistantToolRegistry MemoryEvidenceId import",
)
text = replace_once(
    text,
    '            String evidenceId = "memory." + record.id();',
    "            String evidenceId = MemoryEvidenceId.forRecord(record);",
    "AssistantToolRegistry typed memory evidence",
)
path.write_text(text)

# Direct recent-conversation recall must not depend on already having an active dialogue session.
path = root / "src/main/java/nl/hauntedmc/ailex/assistant/application/routing/AssistantIntentClassifier.java"
text = path.read_text()
text = replace_once(
    text,
    '                "last time what happened", "do you remember what happened"\n',
    '                "last time what happened", "do you remember what happened",\n'
    '                "wie vroeg net", "wie vroeg dat", "wie zei net", "wie zei dat", "wat vroeg je net", "wat zei je net",\n'
    '                "who just asked", "who asked that", "who just said", "who said that",\n'
    '                "what did you just ask", "what did you just say"\n',
    "AssistantIntentClassifier direct recent-event recall",
)
path.write_text(text)
