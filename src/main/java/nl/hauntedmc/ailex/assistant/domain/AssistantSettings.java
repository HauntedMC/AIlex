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
        String defaultModel = config.getString("openai.model", "gpt-5.6-terra");
        String defaultEffort = config.getString("openai.reasoning_effort", "low");
        int defaultOutputTokens = config.getInt("openai.max_output_tokens", 360);
        Set<String> allowedLanguages = allowedLanguages(config.getStringList(PATH + ".routing.allowed_languages"));
        return new AssistantSettings(
                config.getBoolean(PATH + ".enabled", true), config.getString(PATH + ".mode", "adaptive"),
                Math.clamp(config.getInt(PATH + ".total_deadline_seconds", 30), 3, 60),
                // Four calls permits two planner/tool rounds + answer + one grounded escalation. Six remains a hard ceiling.
                Math.clamp(config.getInt(PATH + ".max_model_calls", 4), 1, 6),
                Math.clamp(config.getInt(PATH + ".max_tool_rounds", 2), 0, 4),
                config.getBoolean(PATH + ".routing.language_detection", true),
                defaultLanguage(config.getString(PATH + ".routing.default_language", "nl"), allowedLanguages),
                allowedLanguages,
                config.getBoolean(PATH + ".routing.clarify_only_when_required", true),
                config.getBoolean(PATH + ".retrieval.hybrid_enabled", true),
                Math.clamp(config.getInt(PATH + ".retrieval.query_cache_seconds", 300), 0, 3600),
                config.getBoolean(PATH + ".retrieval.exclude_expired", true),
                Math.clamp(config.getInt(PATH + ".retrieval.max_evidence_characters", 140_000), 500, 200_000),
                Math.clamp(config.getInt(PATH + ".retrieval.max_chunks", 64), 1, 96),
                profile(config, "fast", defaultModel, defaultEffort, Math.max(defaultOutputTokens, 400)),
                profile(config, "grounded", "gpt-5.6-terra", "medium", 640),
                profile(config, "deliberate", "gpt-5.6-sol", "high", 1_000),
                config.getBoolean(PATH + ".tools.read_only", true),
                Set.copyOf(config.getStringList(PATH + ".tools.allowed").stream()
                        .map(value -> value.toLowerCase(Locale.ROOT)).toList()),
                config.getBoolean(PATH + ".tools.redact_other_players", true),
                Math.clamp(config.getInt(PATH + ".delivery.max_lines_fast", 3), 1, 6),
                Math.clamp(config.getInt(PATH + ".delivery.max_lines_grounded", 5), 1, 8),
                Math.clamp(config.getInt(PATH + ".delivery.max_lines_deliberate", 8), 1, 10),
                Math.clamp(config.getInt(PATH + ".delivery.max_line_characters", 300), 80, 360),
                Math.clamp(config.getInt(PATH + ".context.max_input_tokens_fast", 8_000), 512, 32_000),
                Math.clamp(config.getInt(PATH + ".context.max_input_tokens_grounded", 32_000), 1_024, 64_000),
                Math.clamp(config.getInt(PATH + ".context.max_input_tokens_deliberate", 64_000), 2_048, 128_000),
                config.getBoolean(PATH + ".structured_output", true),
                config.getBoolean(PATH + ".verification.enabled", true),
                config.getString(PATH + ".verification.minimum_confidence", "medium"),
                config.getBoolean("openai.knowledge.external.enabled", true),
                Math.clamp(config.getInt("openai.knowledge.external.max_files", 192), 1, 256),
                Math.clamp(config.getInt("openai.knowledge.external.max_characters", 500_000), 1, 1_000_000),
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
        return new AssistantSettings(true, "adaptive", 30, 4, 2, true, "nl", Set.of("nl", "en", "de"), true, true,
                300, true, 140_000, 64,
                new ModelProfile("gpt-5.6-terra", "low", 400),
                new ModelProfile("gpt-5.6-terra", "medium", 640),
                new ModelProfile("gpt-5.6-sol", "high", 1_000),
                true, Set.of("knowledge", "requester", "world", "nearby", "server", "npc", "session"), true,
                3, 5, 8, 300, 8_000, 32_000, 64_000,
                true, true, "medium", true, 192, 500_000, true, true, false,
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
            allowed.addAll(Set.of("nl", "en", "de"));
        } else {
            allowed.add("nl");
        }
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
