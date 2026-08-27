from pathlib import Path

path = Path('src/main/java/nl/hauntedmc/ailex/assistant/application/routing/SemanticNeedPlanner.java')
text = path.read_text()
old = '        boolean events = safe.eventMemory() || decision.intent() == AssistantIntent.EVENT_RECALL;\n'
new = ('        boolean events = safe.eventMemory() || decision.intent() == AssistantIntent.EVENT_RECALL\n'
       '                || decision.intent() == AssistantIntent.MEMORY_RECALL;\n')
if text.count(old) != 1:
    raise SystemExit(f'expected one event-memory merge match, found {text.count(old)}')
path.write_text(text.replace(old, new, 1))
