package nl.hauntedmc.ailex.assistant.application.routing;

import nl.hauntedmc.ailex.assistant.application.context.RequiredContextPlanner;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;
import nl.hauntedmc.ailex.assistant.domain.AssistantMode;
import nl.hauntedmc.ailex.assistant.domain.AssistantSettings;
import nl.hauntedmc.ailex.assistant.infrastructure.knowledge.SemanticEmbeddingProvider;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Learned semantic refinement layer over the deterministic router. Deterministic routing remains the permission/safety
 * prior; this planner may only broaden information needs inside the already-authorized capability ceiling.
 */
public final class SemanticNeedPlanner {

    private static final double DEFAULT_MINIMUM_SIMILARITY = 0.42D;
    private static final double DEFAULT_MINIMUM_MARGIN = 0.025D;

    private final SemanticEmbeddingProvider embeddings;
    private final List<Prototype> prototypes;
    private volatile List<double[]> prototypeVectors = List.of();

    public SemanticNeedPlanner(SemanticEmbeddingProvider embeddings) {
        this.embeddings = embeddings;
        this.prototypes = defaultPrototypes();
    }

    /** Pre-computes prototype vectors. Safe to call repeatedly and suitable for asynchronous warmup. */
    public void warm() {
        if (embeddings == null || !embeddings.available() || !prototypeVectors.isEmpty()) {
            return;
        }
        List<double[]> vectors = embeddings.embed(prototypes.stream().map(Prototype::text).toList());
        if (vectors.size() == prototypes.size()) {
            prototypeVectors = List.copyOf(vectors);
        }
    }

    public Decision refine(
            String message,
            AssistantIntentClassifier.Analysis prior,
            RequiredContextPlanner.Plan priorPlan,
            AssistantSettings settings,
            double minimumSimilarity,
            double minimumMargin
    ) {
        AssistantIntentClassifier.Analysis safePrior = prior == null
                ? new AssistantIntentClassifier.Analysis(AssistantIntent.CONVERSATION, AssistantMode.FAST, "nl") : prior;
        RequiredContextPlanner.Plan safePlan = priorPlan == null
                ? new RequiredContextPlanner.Plan(false, false, false, Set.of()) : priorPlan;

        // Explicit remember/forget operations and durable self-declarations are already high-confidence speech acts.
        // Embedding prototypes describe information needs, not speech acts, so letting them override these messages can
        // convert "my favorite block is netherite" into LIVE_STATE simply because it is close to the block prototype.
        if (AssistantIntentClassifier.isMemoryWriteStatement(message)
                || AssistantIntentClassifier.isMemoryForgetStatement(message)) {
            return Decision.fromPrior(safePrior, safePlan);
        }

        if (safePrior.mode() == AssistantMode.HANDOFF || safePrior.intent() == AssistantIntent.SAFETY
                || safePrior.intent() == AssistantIntent.SUPPORT || embeddings == null || !embeddings.available()) {
            return Decision.fromPrior(safePrior, safePlan);
        }
        warm();
        List<double[]> vectors = prototypeVectors;
        if (vectors.size() != prototypes.size()) {
            return Decision.fromPrior(safePrior, safePlan);
        }
        List<double[]> query = embeddings.embed(List.of(clean(message)));
        if (query.size() != 1 || query.getFirst().length == 0) {
            return Decision.fromPrior(safePrior, safePlan);
        }

        double best = -1.0D;
        double second = -1.0D;
        Prototype winner = null;
        for (int index = 0; index < prototypes.size(); index++) {
            double score = cosine(query.getFirst(), vectors.get(index));
            if (score > best) {
                second = best;
                best = score;
                winner = prototypes.get(index);
            } else if (score > second) {
                second = score;
            }
        }
        double requiredScore = minimumSimilarity <= 0.0D ? DEFAULT_MINIMUM_SIMILARITY : minimumSimilarity;
        double requiredMargin = minimumMargin < 0.0D ? DEFAULT_MINIMUM_MARGIN : minimumMargin;
        if (winner == null || best < requiredScore || best - Math.max(0.0D, second) < requiredMargin) {
            return new Decision(
                    safePrior.intent(), safePrior.mode(), needsFromPlan(safePlan), false,
                    Math.max(0.0D, best), 1.0D - Math.max(0.0D, best), false
            );
        }

        AssistantIntent refinedIntent = selectIntent(safePrior.intent(), winner.intent(), best);
        AssistantMode refinedMode = refinedIntent == AssistantIntent.CONVERSATION
                ? safePrior.mode() : safePrior.mode() == AssistantMode.DELIBERATE ? AssistantMode.DELIBERATE : AssistantMode.GROUNDED;
        EnumSet<Need> needs = EnumSet.noneOf(Need.class);
        needs.addAll(needsFromPlan(safePlan));
        needs.addAll(winner.needs());
        boolean temporal = winner.needs().contains(Need.MEMORY_TIMELINE);
        return new Decision(
                refinedIntent, refinedMode, Set.copyOf(needs), temporal, Math.clamp(best, 0.0D, 1.0D),
                Math.clamp(1.0D - (best - Math.max(0.0D, second)), 0.0D, 1.0D), true
        );
    }

    public Decision refine(
            String message,
            AssistantIntentClassifier.Analysis prior,
            RequiredContextPlanner.Plan priorPlan,
            AssistantSettings settings
    ) {
        return refine(message, prior, priorPlan, settings, DEFAULT_MINIMUM_SIMILARITY, DEFAULT_MINIMUM_MARGIN);
    }

    public RequiredContextPlanner.Plan mergePlan(
            RequiredContextPlanner.Plan prior,
            Decision decision,
            AssistantSettings settings
    ) {
        RequiredContextPlanner.Plan safe = prior == null
                ? new RequiredContextPlanner.Plan(false, false, false, Set.of()) : prior;
        if (decision == null || settings == null) {
            return safe;
        }
        EnumSet<RequiredContextPlanner.LiveSource> live = EnumSet.noneOf(RequiredContextPlanner.LiveSource.class);
        live.addAll(safe.liveSources());
        if (settings.toolAllowed("requester") && decision.needs().contains(Need.REQUESTER)) {
            live.add(RequiredContextPlanner.LiveSource.REQUESTER);
        }
        if (settings.toolAllowed("requester") && decision.needs().contains(Need.INVENTORY)) {
            live.add(RequiredContextPlanner.LiveSource.INVENTORY);
        }
        if (settings.toolAllowed("world") && decision.needs().contains(Need.WORLD)) {
            live.add(RequiredContextPlanner.LiveSource.WORLD);
        }
        if (settings.toolAllowed("world") && decision.needs().contains(Need.TARGET)) {
            live.add(RequiredContextPlanner.LiveSource.TARGET);
        }
        if (settings.toolAllowed("server") && decision.needs().contains(Need.SERVER)) {
            live.add(RequiredContextPlanner.LiveSource.SERVER);
        }
        if (settings.toolAllowed("nearby") && decision.needs().contains(Need.NEARBY)) {
            live.add(RequiredContextPlanner.LiveSource.NEARBY);
        }
        if (settings.toolAllowed("npc") && decision.needs().contains(Need.NPC)) {
            live.add(RequiredContextPlanner.LiveSource.NPC);
        }
        boolean knowledge = safe.knowledge()
                || settings.toolAllowed("knowledge") && decision.needs().contains(Need.KNOWLEDGE);
        boolean memory = safe.durableMemory()
                || settings.toolAllowed("session") && (decision.needs().contains(Need.MEMORY)
                || decision.needs().contains(Need.MEMORY_TIMELINE));
        boolean events = safe.eventMemory() || decision.intent() == AssistantIntent.EVENT_RECALL;
        return new RequiredContextPlanner.Plan(knowledge, memory, events, Set.copyOf(live));
    }

    private AssistantIntent selectIntent(AssistantIntent prior, AssistantIntent semantic, double score) {
        if (prior == AssistantIntent.SAFETY || prior == AssistantIntent.SUPPORT || prior == AssistantIntent.LIVE_STATE
                || prior == AssistantIntent.MEMORY_RECALL || prior == AssistantIntent.EVENT_RECALL) {
            return prior;
        }
        if (prior == AssistantIntent.SERVER_FACT && semantic == AssistantIntent.GAMEPLAY_HELP && score < 0.62D) {
            return prior;
        }
        if (prior == AssistantIntent.CONVERSATION || prior == AssistantIntent.GAMEPLAY_HELP
                || prior == AssistantIntent.CONTEXT_FOLLOWUP || prior == AssistantIntent.SERVER_FACT) {
            return semantic;
        }
        return prior;
    }

    private Set<Need> needsFromPlan(RequiredContextPlanner.Plan plan) {
        EnumSet<Need> result = EnumSet.noneOf(Need.class);
        if (plan.knowledge()) {
            result.add(Need.KNOWLEDGE);
        }
        if (plan.durableMemory()) {
            result.add(Need.MEMORY);
        }
        if (plan.eventMemory()) {
            result.add(Need.MEMORY_TIMELINE);
        }
        for (RequiredContextPlanner.LiveSource source : plan.liveSources()) {
            result.add(switch (source) {
                case REQUESTER -> Need.REQUESTER;
                case INVENTORY -> Need.INVENTORY;
                case WORLD -> Need.WORLD;
                case TARGET -> Need.TARGET;
                case SERVER -> Need.SERVER;
                case NEARBY -> Need.NEARBY;
                case NPC -> Need.NPC;
            });
        }
        return Set.copyOf(result);
    }

    private List<Prototype> defaultPrototypes() {
        Map<String, Prototype> unique = new LinkedHashMap<>();
        add(unique, AssistantIntent.LIVE_STATE, Set.of(Need.REQUESTER, Need.INVENTORY),
                "What am I carrying, wearing, holding, what is my rank balance health status or inventory right now?");
        add(unique, AssistantIntent.LIVE_STATE, Set.of(Need.WORLD, Need.TARGET),
                "Where am I, which biome or world is this, what block am I looking at, what is around my current location?");
        add(unique, AssistantIntent.LIVE_STATE, Set.of(Need.SERVER),
                "Why is the server slow right now, what are TPS MSPT online count uptime performance and current server state?");
        add(unique, AssistantIntent.LIVE_STATE, Set.of(Need.NEARBY),
                "What players mobs or entities are near me or around me right now?");
        add(unique, AssistantIntent.MEMORY_RECALL, Set.of(Need.MEMORY),
                "What do you remember about me, my preferences, projects, interests or goals?");
        add(unique, AssistantIntent.EVENT_RECALL, Set.of(Need.MEMORY, Need.MEMORY_TIMELINE),
                "What happened before, last time, earlier, yesterday, after the reset, or what changed over time?");
        add(unique, AssistantIntent.SERVER_FACT, Set.of(Need.KNOWLEDGE),
                "How does this HauntedMC server feature, rank, command, claim, currency, event or rule work?");
        add(unique, AssistantIntent.GAMEPLAY_HELP, Set.of(),
                "How do I craft, tame, enchant, build, farm, use redstone or play ordinary Minecraft?");
        add(unique, AssistantIntent.CONTEXT_FOLLOWUP, Set.of(Need.MEMORY, Need.KNOWLEDGE),
                "That thing we discussed earlier, is it still true and how does it relate to what we were talking about?");
        add(unique, AssistantIntent.CONVERSATION, Set.of(),
                "Hello, how are you, tell me a joke, casual social conversation without a factual information request.");
        return List.copyOf(unique.values());
    }

    private void add(Map<String, Prototype> target, AssistantIntent intent, Set<Need> needs, String text) {
        target.put(text, new Prototype(intent, needs, text));
    }

    private double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0.0D;
        }
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm <= 0.0D || rightNorm <= 0.0D) {
            return 0.0D;
        }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public enum Need {
        KNOWLEDGE,
        MEMORY,
        MEMORY_TIMELINE,
        REQUESTER,
        INVENTORY,
        WORLD,
        TARGET,
        SERVER,
        NEARBY,
        NPC
    }

    public record Decision(
            AssistantIntent intent,
            AssistantMode mode,
            Set<Need> needs,
            boolean temporal,
            double confidence,
            double uncertainty,
            boolean semanticallyRefined
    ) {
        public Decision {
            intent = intent == null ? AssistantIntent.CONVERSATION : intent;
            mode = mode == null ? AssistantMode.FAST : mode;
            needs = needs == null ? Set.of() : Set.copyOf(needs);
            confidence = Math.clamp(confidence, 0.0D, 1.0D);
            uncertainty = Math.clamp(uncertainty, 0.0D, 1.0D);
        }

        static Decision fromPrior(AssistantIntentClassifier.Analysis prior, RequiredContextPlanner.Plan plan) {
            EnumSet<Need> needs = EnumSet.noneOf(Need.class);
            if (plan.knowledge()) {
                needs.add(Need.KNOWLEDGE);
            }
            if (plan.durableMemory()) {
                needs.add(Need.MEMORY);
            }
            if (plan.eventMemory()) {
                needs.add(Need.MEMORY_TIMELINE);
            }
            return new Decision(prior.intent(), prior.mode(), Set.copyOf(needs), false, 0.0D, 1.0D, false);
        }
    }

    private record Prototype(AssistantIntent intent, Set<Need> needs, String text) {
        private Prototype {
            needs = needs == null ? Set.of() : Set.copyOf(needs);
            text = text == null ? "" : text.trim();
        }
    }
}
