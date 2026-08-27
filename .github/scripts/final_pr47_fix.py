from pathlib import Path

path = Path('src/main/java/nl/hauntedmc/ailex/assistant/application/routing/AssistantIntentClassifier.java')
text = path.read_text()
old = '                "welk blok", "welke block", "what block", "which block", "waar kijk ik", "what am i looking at",\n'
new = '                "welk blok", "welk block", "welke block", "what block", "which block", "waar kijk ik", "what am i looking at",\n'
if text.count(old) != 1:
    raise SystemExit(f'expected one live block phrase table match, found {text.count(old)}')
path.write_text(text.replace(old, new, 1))
