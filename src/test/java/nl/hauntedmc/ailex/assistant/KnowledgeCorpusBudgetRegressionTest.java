package nl.hauntedmc.ailex.assistant;

import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeCorpusBudgetRegressionTest {

    @Test
    void bundledManagedKnowledgeMustFitDefaultExternalCorpusBudget() throws IOException {
        List<String> files;
        try (InputStream manifest = getClass().getResourceAsStream("/knowledge/index.txt")) {
            assertNotNull(manifest);
            files = new String(manifest.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(name -> name.endsWith(".md"))
                    .filter(name -> !name.equalsIgnoreCase("README.md"))
                    .toList();
        }

        int characters = 0;
        for (String file : files) {
            try (InputStream article = getClass().getResourceAsStream("/knowledge/" + file)) {
                assertNotNull(article, "Missing managed knowledge resource: " + file);
                characters += article.readAllBytes().length;
            }
        }

        AssistantSettings defaults = AssistantSettings.defaults();
        assertTrue(files.size() <= defaults.externalMaxFiles(),
                "Managed knowledge file count exceeds default corpus budget: " + files.size());
        assertTrue(characters <= defaults.externalMaxCharacters(),
                "Managed knowledge exceeds default corpus budget: " + characters);
        assertTrue(characters <= defaults.maxEvidenceCharacters(),
                "Managed knowledge exceeds deliberate evidence window: " + characters);
    }

    @Test
    void deliberateContextBudgetMustBeLargeEnoughForFullReviewedEvidenceWindow() {
        AssistantSettings defaults = AssistantSettings.defaults();
        int approximateEvidenceCharacters = (int) (defaults.maxInputTokensDeliberate() * 4L * 3L / 5L);
        assertTrue(defaults.maxEvidenceCharacters() <= approximateEvidenceCharacters,
                "Retrieval evidence budget must fit the deliberate context evidence partition");
    }
}
