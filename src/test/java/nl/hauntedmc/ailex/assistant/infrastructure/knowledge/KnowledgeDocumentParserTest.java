package nl.hauntedmc.ailex.assistant.infrastructure.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentParserTest {

    @Test
    void yamlFrontMatterBecomesOperationalProvenance() {
        String markdown = """
                ---
                id: hauntedmc.claims
                title: Survival claims
                aliases: [/claim, claims, protect build]
                category: server-fact
                authority: official
                updated: 2026-08-26
                expires: null
                source: https://hauntedmc.nl/help
                ---
                Use /claim to protect a build.
                """;

        List<KnowledgeDocumentParser.ParsedSection> sections = new KnowledgeDocumentParser().parse("claims.md", markdown);

        assertEquals(1, sections.size());
        var section = sections.getFirst();
        assertEquals("hauntedmc.claims", section.id());
        assertEquals("Survival claims", section.title());
        assertEquals(List.of("/claim", "claims", "protect build"), section.aliases());
        assertEquals("server-fact", section.category());
        assertEquals("official", section.authority());
        assertEquals("2026-08-26", section.updated());
        assertEquals("https://hauntedmc.nl/help", section.source());
        assertFalse(section.expired());
        assertTrue(section.text().contains("/claim"));
    }

    @Test
    void unknownFrontMatterKeysAreIgnoredRatherThanDeserialized() {
        String markdown = """
                ---
                id: safe.test
                title: Safe
                javaClass: evil.Payload
                authority: reviewed
                ---
                Safe text.
                """;

        var section = new KnowledgeDocumentParser().parse("safe.md", markdown).getFirst();

        assertEquals("safe.test", section.id());
        assertEquals("reviewed", section.authority());
        assertFalse(section.text().contains("evil.Payload"));
    }
}
