package nl.hauntedmc.ailex.assistant.proactive;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Immutable, validated proactive-assistant configuration. */
public record ProactiveChatSettings(
        boolean enabled,
        String responseVisibility,
        long cooldownMillis,
        JoinSettings join,
        QuestionSettings questions,
        CollectiveSettings collective,
        IdleSettings idle,
        GoalSettings goals
) {

    private static final String PATH = "openai.proactive_chat";
    private static final List<String> DEFAULT_COLLECTIVE_TERMS = List.of(
            "gg", "wp", "well played", "welkom", "welcome", "congrats", "gefeliciteerd", "nice"
    );
    private static final String DEFAULT_JOIN_PROMPT = "Een speler met de naam {player_name} is zojuist gejoined. "
            + "Geef precies één korte, persoonlijke begroeting direct aan {player_name}. Je antwoord MOET de "
            + "exacte naam {player_name} bevatten. Gebruik geen generieke serverwelkomsttekst, slogan, uitleg, "
            + "'gg' of avontuur-wens. Houd het natuurlijk en eenvoudig.";

    public static ProactiveChatSettings from(FileConfiguration config) {
        if (config == null) {
            return disabled();
        }
        return new ProactiveChatSettings(
                config.getBoolean(PATH + ".enabled", true),
                config.getString(PATH + ".response_visibility", "server"),
                secondsToMillis(config.getLong(PATH + ".cooldown_seconds", 900L)),
                new JoinSettings(
                        config.getBoolean(PATH + ".join.enabled", true),
                        probability(config.getDouble(PATH + ".join.probability", 0.03D)),
                        secondsToMillis(config.getLong(PATH + ".join.cooldown_seconds", 1_800L)),
                        joinPrompt(config.getString(PATH + ".join.prompt", DEFAULT_JOIN_PROMPT))
                ),
                new QuestionSettings(
                        config.getBoolean(PATH + ".questions.enabled", true),
                        probability(config.getDouble(PATH + ".questions.probability", 0.10D)),
                        secondsToMillis(config.getLong(PATH + ".questions.conversation_window_seconds", 45L)),
                        Math.clamp(config.getInt(PATH + ".questions.minimum_speaker_alternations", 2), 2, 6),
                        secondsToMillis(config.getLong(PATH + ".questions.social_graph_window_seconds", 180L)),
                        Math.clamp(config.getDouble(PATH + ".questions.strong_pair_score", 2.5D), 0.5D, 12.0D),
                        Math.clamp(config.getDouble(PATH + ".questions.utility.threshold", 0.45D), -1.0D, 2.0D),
                        Math.clamp(config.getDouble(PATH + ".questions.utility.helpful_weight", 1.25D), 0.0D, 3.0D),
                        Math.clamp(config.getDouble(PATH + ".questions.utility.privacy_cost", 1.50D), 0.0D, 3.0D),
                        Math.clamp(config.getDouble(PATH + ".questions.utility.error_cost", 1.00D), 0.0D, 3.0D),
                        Math.clamp(config.getDouble(PATH + ".questions.utility.repetition_cost", 1.25D), 0.0D, 3.0D)
                ),
                new CollectiveSettings(
                        config.getBoolean(PATH + ".collective.enabled", true),
                        collectiveTerms(config.getStringList(PATH + ".collective.terms")),
                        Math.clamp(config.getInt(PATH + ".collective.minimum_distinct_players", 2), 2, 10),
                        secondsToMillis(config.getLong(PATH + ".collective.window_seconds", 45L)),
                        probability(config.getDouble(PATH + ".collective.probability", 0.12D))
                ),
                new IdleSettings(
                        config.getBoolean(PATH + ".idle.enabled", false),
                        secondsToMillis(config.getLong(PATH + ".idle.silence_seconds", 30L * 60L)),
                        secondsToMillis(config.getLong(PATH + ".idle.check_interval_seconds", 60L)),
                        probability(config.getDouble(PATH + ".idle.probability", 0.02D)),
                        Math.clamp(config.getInt(PATH + ".idle.minimum_online_players", 1), 1, 100),
                        secondsToMillis(config.getLong(PATH + ".idle.recent_chat_window_seconds", 600L))
                ),
                GoalSettings.from(config)
        );
    }

    /** Source-compatible constructor retained for existing tests/integrations. */
    public ProactiveChatSettings(
            boolean enabled,
            String responseVisibility,
            long cooldownMillis,
            JoinSettings join,
            QuestionSettings questions,
            CollectiveSettings collective,
            IdleSettings idle
    ) {
        this(enabled, responseVisibility, cooldownMillis, join, questions, collective, idle, GoalSettings.defaults());
    }

    private static ProactiveChatSettings disabled() {
        return new ProactiveChatSettings(
                false, "server", 900_000L,
                new JoinSettings(false, 0.0D, 1_800_000L, DEFAULT_JOIN_PROMPT),
                new QuestionSettings(false, 0.0D, 45_000L, 2, 180_000L, 2.5D),
                new CollectiveSettings(false, DEFAULT_COLLECTIVE_TERMS, 2, 45_000L, 0.0D),
                new IdleSettings(false, 1_800_000L, 60_000L, 0.0D, 1, 600_000L),
                GoalSettings.disabled()
        );
    }

    private static List<String> collectiveTerms(List<String> terms) {
        return terms == null || terms.isEmpty() ? DEFAULT_COLLECTIVE_TERMS : List.copyOf(terms);
    }

    private static String joinPrompt(String prompt) {
        return prompt == null || prompt.isBlank() || !prompt.contains("{player_name}")
                ? DEFAULT_JOIN_PROMPT : prompt.trim();
    }

    private static double probability(double value) {
        return Math.clamp(value, 0.0D, 1.0D);
    }

    private static long secondsToMillis(long value) {
        return Math.clamp(value, 1L, 24L * 60L * 60L) * 1_000L;
    }

    public record JoinSettings(boolean enabled, double probability, long cooldownMillis, String prompt) {
    }

    public record QuestionSettings(
            boolean enabled,
            double probability,
            long conversationWindowMillis,
            int minimumSpeakerAlternations,
            long socialGraphWindowMillis,
            double strongPairScore,
            double utilityThreshold,
            double helpfulWeight,
            double privacyCost,
            double errorCost,
            double repetitionCost
    ) {
        /** Source-compatible defaults for existing integrations/tests. */
        public QuestionSettings(
                boolean enabled,
                double probability,
                long conversationWindowMillis,
                int minimumSpeakerAlternations,
                long socialGraphWindowMillis,
                double strongPairScore
        ) {
            this(
                    enabled, probability, conversationWindowMillis, minimumSpeakerAlternations,
                    socialGraphWindowMillis, strongPairScore, 0.45D, 1.25D, 1.50D, 1.00D, 1.25D
            );
        }
    }

    public record CollectiveSettings(
            boolean enabled,
            List<String> terms,
            int minimumDistinctPlayers,
            long windowMillis,
            double probability
    ) {
    }

    public record GoalSettings(
            boolean enabled,
            Set<CommunityGoal> enabledGoals,
            double probability,
            double followUpProbability
    ) {
        static GoalSettings from(FileConfiguration config) {
            Set<CommunityGoal> goals = EnumSet.noneOf(CommunityGoal.class);
            List<String> configured = config.getStringList(PATH + ".goals.enabled_goals");
            if (configured.isEmpty()) {
                goals.addAll(defaultGoals());
            } else {
                for (String value : configured) {
                    try {
                        goals.add(CommunityGoal.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_')));
                    } catch (IllegalArgumentException ignored) {
                        // Invalid config values are ignored fail-closed.
                    }
                }
            }
            goals.remove(CommunityGoal.SILENCE);
            return new GoalSettings(
                    config.getBoolean(PATH + ".goals.enabled", true),
                    Set.copyOf(goals),
                    ProactiveChatSettings.probability(config.getDouble(PATH + ".goals.probability", 0.08D)),
                    ProactiveChatSettings.probability(
                            config.getDouble(PATH + ".goals.follow_up_probability", 0.0D)
                    )
            );
        }

        static GoalSettings defaults() {
            return new GoalSettings(true, defaultGoals(), 0.08D, 0.0D);
        }

        static GoalSettings disabled() {
            return new GoalSettings(false, Set.of(), 0.0D, 0.0D);
        }

        public boolean enabled(CommunityGoal goal) {
            return enabled && goal != null && enabledGoals.contains(goal);
        }

        private static Set<CommunityGoal> defaultGoals() {
            return Set.of(
                    CommunityGoal.HELP_NEW_PLAYER, CommunityGoal.CELEBRATE, CommunityGoal.CONNECT,
                    CommunityGoal.DEFUSE, CommunityGoal.INFORM
            );
        }
    }

    public record IdleSettings(
            boolean enabled,
            long silenceMillis,
            long checkIntervalMillis,
            double probability,
            int minimumOnlinePlayers,
            long recentChatWindowMillis
    ) {
    }
}
