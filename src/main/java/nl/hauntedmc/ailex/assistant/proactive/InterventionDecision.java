package nl.hauntedmc.ailex.assistant.proactive;

/** Utility decomposition for one proactive participation decision. */
public record InterventionDecision(
        CommunityGoal goal,
        double helpfulProbability,
        double privateConversationProbability,
        double errorProbability,
        double repetitionPenalty,
        double utility,
        boolean speak
) {
    public InterventionDecision {
        goal = goal == null ? CommunityGoal.SILENCE : goal;
        helpfulProbability = clamp(helpfulProbability);
        privateConversationProbability = clamp(privateConversationProbability);
        errorProbability = clamp(errorProbability);
        repetitionPenalty = clamp(repetitionPenalty);
        utility = Math.clamp(utility, -4.0D, 4.0D);
        if (!speak) {
            goal = CommunityGoal.SILENCE;
        }
    }

    private static double clamp(double value) {
        return Math.clamp(value, 0.0D, 1.0D);
    }
}
