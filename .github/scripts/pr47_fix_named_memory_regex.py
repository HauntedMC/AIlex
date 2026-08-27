from pathlib import Path

path = Path('src/main/java/nl/hauntedmc/ailex/assistant/application/routing/AssistantIntentClassifier.java')
text = path.read_text()
# The Java source contained a single \\b escape, which Java interprets as a backspace character rather than a regex
# word-boundary. The phrase itself is already anchored structurally, so remove the unnecessary boundary escape entirely.
updated = text.replace('.*\\bwat weet je ', '.*wat weet je ')
updated = updated.replace('.*\\bwat herinner je ', '.*wat herinner je ')
updated = updated.replace('.*\\bwhat do you know about ', '.*what do you know about ')
updated = updated.replace('.*\\bwhat do you remember about ', '.*what do you remember about ')
if updated == text:
    raise SystemExit('named-memory regex escape was not found')
path.write_text(updated)
