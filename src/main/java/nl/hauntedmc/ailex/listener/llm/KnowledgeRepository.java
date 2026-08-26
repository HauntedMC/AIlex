package nl.hauntedmc.ailex.listener.llm;

import nl.hauntedmc.ailex.util.LoggerUtils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Loads operator-maintained, non-sensitive server knowledge from a dedicated directory.
 */
final class KnowledgeRepository {

    private static final String KNOWLEDGE_PATH = "openai.knowledge";
    private static final int DEFAULT_MAX_FILES = 20;
    private static final int DEFAULT_MAX_CHARACTERS = 30000;

    private final JavaPlugin plugin;

    KnowledgeRepository(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    String loadExternalKnowledge() {
        FileConfiguration config = plugin.getConfig();
        if (config == null || !config.getBoolean(KNOWLEDGE_PATH + ".external.enabled", true)) {
            return "";
        }

        String directoryName = config.getString(KNOWLEDGE_PATH + ".external.directory", "knowledge");
        if (directoryName == null || directoryName.isBlank()) {
            return "";
        }

        File dataFolder = plugin.getDataFolder();
        if (dataFolder == null) {
            return "";
        }
        Path dataDirectory = dataFolder.toPath().toAbsolutePath().normalize();
        Path knowledgeDirectory = dataDirectory.resolve(directoryName).normalize();
        if (!knowledgeDirectory.startsWith(dataDirectory) || !Files.isDirectory(knowledgeDirectory)) {
            return "";
        }

        int maxFiles = Math.clamp(config.getInt(KNOWLEDGE_PATH + ".external.max_files", DEFAULT_MAX_FILES), 1, 100);
        int maxCharacters = Math.clamp(
                config.getInt(KNOWLEDGE_PATH + ".external.max_characters", DEFAULT_MAX_CHARACTERS),
                1,
                120000
        );
        StringBuilder knowledge = new StringBuilder();
        try (Stream<Path> paths = Files.list(knowledgeDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isSupportedKnowledgeFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(maxFiles)
                    .forEach(path -> appendFile(knowledge, path, maxCharacters));
        } catch (IOException e) {
            LoggerUtils.logWarning("Could not load external AI knowledge: " + e.getMessage());
        }
        return knowledge.toString().trim();
    }

    private boolean isSupportedKnowledgeFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".txt");
    }

    private void appendFile(StringBuilder knowledge, Path path, int maxCharacters) {
        if (knowledge.length() >= maxCharacters) {
            return;
        }
        try {
            String contents = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (contents.isBlank()) {
                return;
            }
            if (!knowledge.isEmpty()) {
                knowledge.append('\n');
            }
            int remaining = maxCharacters - knowledge.length();
            knowledge.append(contents, 0, Math.min(contents.length(), remaining));
        } catch (IOException e) {
            LoggerUtils.logWarning("Could not read AI knowledge file " + path.getFileName() + ": " + e.getMessage());
        }
    }
}
