package nl.hauntedmc.ailex.assistant.domain;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Central bounded configuration view for the assistant subsystem. */
public record AssistantSettings(
        boolean enabled, String mode, int totalDeadlineSeconds, int maxModelCalls, int maxToolRounds,
        boolean languageDetection, String defaultLanguage, Set<String> allowedLanguages,
        boolean clarifyOnlyWhenRequired, boolean hybridRetrieval,
        int queryCacheSeconds, boolean excludeExpired, int maxEvidenceCharacters, int maxChunks,
        ModelProfile fastProfile, ModelProfile groundedProfile, ModelProfile deliberateProfile,
        boolean readOnlyTools, Set<String> allowedTools, boolean redactOtherPlayers,
        int maxLinesFast, int maxLinesGrounded, int maxLinesDeliberate, int maxLineCharacters,
        int maxInputTokensFast, int maxInputTokensGrounded, int maxInputTokensDeliberate,
        boolean structuredOutput, boolean verificationEnabled, String minimumConfidence,
        boolean externalKnowledgeEnabled, int externalMaxFiles, int externalMaxCharacters,
        boolean circuitBreakerEnabled, boolean cacheStaticAnswers, boolean shadowMode,
        boolean diagnosticLogging, boolean logRequesterName, boolean logResponsePreview, int maxResponsePreviewCharacters
) {
    private static final String PATH = "openai.assistant";

    public static AssistantSettings from(FileConfiguration config) {
        if (config == null) {
            return defaults();
        }
        String defaultModel = config.getString("openai.model", "gpt-5.6-luna");
        String defaultEffort = config.getString("openai.reasoning_effort", "low");
        int defaultOutputTokens = config.getInt("openai.max_output_tokens", 200);
        Set<String> allowedLanguages = allowedLanguages(config.getStringList(PATH + ".routing.allowed_languages"));
        return new AssistantSettings(
                config.getBoolean(PATH + ".enabled", true), config.getString(PATH + ".mode", "adaptive"),
                Math.clamp(config.getInt(PATH + ".total_deadline_seconds", 15), 3, 60),
                Math.clamp(config.getInt(PATH + ".max_model_calls", 3), 1, 3),
                Math.clamp(config.getInt(PATH + ".max_tool_rounds", 2), 0, 4),
                config.getBoolean(PATH + ".routing.language_detection", true),
                defaultLanguage(config.getString(PATH + ".routing.default_language", "nl"), allowedLanguages),
                allowedLanguages,
                config.getBoolean(PATH + ".routing.clarify_only_when_required", true),
                config.getBoolean(PATH + ".retrieval.hybrid_enabled", true),
                Math.clamp(config.getInt(PATH + ".retrieval.query_cache_seconds", 300), 0, 3600),
                config.getBoolean(PATH + ".retrieval.exclude_expired", true),
                Math.clamp(config.getInt(PATH + ".retrieval.max_evidence_characters", 24_000), 500, 60_000),
                Math.clamp(config.getInt(PATH + ".retrieval.max_chunks", 10), 1, 20),
                profile(config, "fast", defaultModel, defaultEffort, Math.min(defaultOutputTokens, 220)),
                profile(config, "grounded", defaultModel, defaultEffort, Math.max(defaultOutputTokens, 360)),
                profile(config, "deliberate", defaultModel, defaultEffort, Math.max(defaultOutputTokens, 640)),
                config.getBoolean(PATH + ".tools.read_only", true),
                Set.copyOf(config.getStringList(PATH + ".tools.allowed").stream()
                        .map(value -> value.toLowerCase(Locale.ROOT)).toList()),
                config.getBoolean(PATH + ".tools.redact_other_players", true),
                Math.clamp(config.getInt(PATH + ".delivery.max_lines_fast", 1), 1, 4),
                Math.clamp(config.getInt(PATH + ".delivery.max_lines_grounded", 3), 1, 5),
                Math.clamp(config.getInt(PATH + ".delivery.max_lines_deliberate", 4), 1, 6),
                Math.clamp(config.getInt(PATH + ".delivery.max_line_characters", 240), 80, 320),
                Math.clamp(config.getInt(PATH + ".context.max_input_tokens_fast", 3_000), 512, 16_000),
                Math.clamp(config.getInt(PATH + ".context.max_input_tokens_grounded", 9_000), 1_024, 32_000),
                Math.clamp(config.getInt(PATH + ".context.max_input_tokens_deliberate", 18_000), 2_048, 64_000),
                config.getBoolean(PATH + ".structured_output", true),
                config.getBoolean(PATH + ".verification.enabled", true),
                config.getString(PATH + ".verification.minimum_confidence", "medium"),
                config.getBoolean("openai.knowledge.external.enabled", true),
                Math.clamp(config.getInt("openai.knowledge.external.max_files", 64), 1, 256),
                Math.clamp(config.getInt("openai.knowledge.external.max_characters", 120_000), 1, 500_000),
                config.getBoolean(PATH + ".reliability.circuit_breaker_enabled", true),
                config.getBoolean(PATH + ".reliability.cache_static_answers", true),
                config.getBoolean(PATH + ".reliability.shadow_mode", false),
                config.getBoolean(PATH + ".observability.enabled", true),
                config.getBoolean(PATH + ".observability.include_requester_name", true),
                config.getBoolean(PATH + ".observability.include_response_preview", false),
                Math.clamp(config.getInt(PATH + ".observability.max_response_preview_characters", 240), 40, 600)
        );
    }

    public static AssistantSettings defaults() {
        return new AssistantSettings(true, "adaptive", 15, 3, 2, true, "nl", Set.of("nl", "en"), true, true,
                300, true, 24_000, 10,
                new ModelProfile("gpt-5.6-luna", "low", 180),
                new ModelProfile("gpt-5.6-terra", "medium", 360),
                new ModelProfile("gpt-5.6-sol", "high", 640),
                true, Set.of("knowledge", "requester", "world", "nearby", "server", "npc", "session"), true,
                1, 3, 4, 240, 3_000, 9_000, 18_000,
                true, true, "medium", true, 64, 120_000, true, true, false,
                true, true, false, 240);
    }

    public boolean toolAllowed(String name) {
        return allowedTools.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Returns whether a normalized language code is allowed for player-facing output. */
    public boolean languageAllowed(String language) {
        return allowedLanguages.contains(normalizeLanguage(language));
    }

    private static Set<String> allowedLanguages(java.util.List<String> configuredLanguages) {
        Set<String> allowed = new LinkedHashSet<>();
        for (String language : configuredLanguages) {
            String normalized = normalizeLanguage(language);
            if ("nl".equals(normalized) || "en".equals(normalized) || "de".equals(normalized)) {
                allowed.add(normalized);
            }
        }
        if (configuredLanguages.isEmpty()) {
            allowed.add("en");
        }
        allowed.add("nl");
        return Set.copyOf(allowed);
    }

    private static String defaultLanguage(String configuredLanguage, Set<String> allowedLanguages) {
        String normalized = normalizeLanguage(configuredLanguage);
        return allowedLanguages.contains(normalized) ? normalized : "nl";
    }

    private static String normalizeLanguage(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "nl", "nederlands", "dutch" -> "nl";
            case "en", "english", "engels" -> "en";
            case "de", "deutsch", "duits", "german" -> "de";
            default -> "";
        };
    }

    public int maxLines(AssistantMode mode) {
        return switch (mode) {
            case FAST -> maxLinesFast;
            case GROUNDED, HANDOFF -> maxLinesGrounded;
            case DELIBERATE -> maxLinesDeliberate;
        };
    }

    public int maxInputTokens(AssistantMode mode) {
        return switch (mode) {
            case FAST, HANDOFF -> maxInputTokensFast;
            case GROUNDED -> maxInputTokensGrounded;
            case DELIBERATE -> maxInputTokensDeliberate;
        };
    }

    public AssistantMode resolveMode(AssistantMode inferred) {
        return switch (mode == null ? "adaptive" : mode.toLowerCase(Locale.ROOT)) {
            case "fast" -> AssistantMode.FAST;
            case "grounded" -> AssistantMode.GROUNDED;
            case "deliberate" -> AssistantMode.DELIBERATE;
            default -> inferred;
        };
    }

    /** Maps the semantic layer to an independently configurable model execution profile. */
    public ModelProfile profileFor(AssistantMode requestMode) {
        return switch (requestMode) {
            case FAST, HANDOFF -> fastProfile;
            case GROUNDED -> groundedProfile;
            case DELIBERATE -> deliberateProfile;
        };
    }

    private static ModelProfile profile(
            FileConfiguration config, String name, String fallbackModel, String fallbackEffort, int fallbackTokens
    ) {
        String path = PATH + ".models." + name;
        return new ModelProfile(
                config.getString(path + ".model", fallbackModel),
                config.getString(path + ".reasoning_effort", fallbackEffort),
                Math.clamp(config.getInt(path + ".max_output_tokens", fallbackTokens), 16, 4096)
        );
    }

    /** The model, reasoning budget, and generation cap for one assistant layer. */
    public record ModelProfile(String model, String reasoningEffort, int maxOutputTokens) {
        public ModelProfile {
            model = model == null ? "" : model.trim();
            reasoningEffort = normalizeReasoningEffort(reasoningEffort);
            maxOutputTokens = Math.clamp(maxOutputTokens, 16, 4096);
        }

        private static String normalizeReasoningEffort(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "none", "low", "medium", "high", "xhigh", "max" -> normalized;
                default -> "low";
            };
        }
    }
}
