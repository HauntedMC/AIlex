from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


root = Path(__file__).resolve().parents[2]

# Production players commonly use Dutch informal "onthou" rather than "onthoud". Treat both as the same explicit
# remember speech act; this is a routing normalization, not a relaxation of memory safety.
path = root / "src/main/java/nl/hauntedmc/ailex/assistant/application/routing/AssistantIntentClassifier.java"
text = path.read_text()
text = replace_once(
    text,
    '            "onthoud", "onthouden", "herinner", "herinneren", "remember", "remembered", "weet", "wist", "vergeet",\n',
    '            "onthou", "onthoud", "onthouden", "herinner", "herinneren", "remember", "remembered", "weet", "wist", "vergeet",\n',
    "classifier informal onthou token",
)
text = replace_once(
    text,
    '        if (containsAnyPhrase(normalized, "onthoud ", "onthoud dat ", "onthouden dat ", "remember ", "remember that ")) {\n',
    '        if (containsAnyPhrase(normalized, "onthou ", "onthou dat ", "onthoud ", "onthoud dat ", "onthouden dat ", "remember ", "remember that ")) {\n',
    "classifier informal onthou phrase",
)
path.write_text(text)

path = root / "src/main/java/nl/hauntedmc/ailex/assistant/application/context/RequiredContextPlanner.java"
text = path.read_text()
text = replace_once(
    text,
    '                "what should i", "what do you suggest", "what would you recommend", "remember", "onthoud",\n',
    '                "what should i", "what do you suggest", "what would you recommend", "remember", "onthou", "onthoud",\n',
    "context informal onthou",
)
path.write_text(text)

path = root / "src/main/java/nl/hauntedmc/ailex/assistant/infrastructure/memory/ExplicitPlayerMemoryService.java"
text = path.read_text()
text = replace_once(
    text,
    '    private static final Pattern HAIR = Pattern.compile(\n',
    '    private static final Pattern PLAYS_SINCE_REVERSED = Pattern.compile(\n'
    '            "ik\\\\s+(?:al\\\\s+)?sinds\\\\s+(\\\\d{4}).{0,48}\\\\bspeel\\\\b",\n'
    '            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE\n'
    '    );\n'
    '    private static final Pattern HAIR = Pattern.compile(\n',
    "explicit memory reversed play-since pattern",
)
text = replace_once(
    text,
    '            ".*?\\\\b(?:onthoud(?:en)?|remember)\\\\b(?:\\\\s+dat|\\\\s+that)?\\\\s+(.+)",\n',
    '            ".*?\\\\b(?:onthou(?:d|den)?|remember)\\\\b(?:\\\\s+dat|\\\\s+that)?\\\\s+(.+)",\n',
    "explicit memory informal onthou regex",
)
text = replace_once(
    text,
    '        String clean = compact(message);\n        if (AssistantIntentClassifier.isMemoryForgetStatement(clean)) {\n',
    '        String clean = compact(message);\n'
    '        String extractionText = clean.replaceAll("(?i)\\\\bsidns\\\\b", "sinds");\n'
    '        if (AssistantIntentClassifier.isMemoryForgetStatement(clean)) {\n',
    "explicit memory production typo normalization",
)
text = replace_once(
    text,
    '        List<MemoryCandidate> candidates = extractWriteCandidates(clean);\n',
    '        List<MemoryCandidate> candidates = extractWriteCandidates(extractionText);\n',
    "explicit memory normalized extraction input",
)
text = replace_once(
    text,
    '        Matcher hair = HAIR.matcher(message);\n',
    '        Matcher reversedPlaySince = PLAYS_SINCE_REVERSED.matcher(message);\n'
    '        if (reversedPlaySince.find()) {\n'
    '            add(result, "fact", "plays_since", reversedPlaySince.group(1));\n'
    '            return List.copyOf(result);\n'
    '        }\n\n'
    '        Matcher hair = HAIR.matcher(message);\n',
    "explicit memory reversed play-since extraction",
)
path.write_text(text)
