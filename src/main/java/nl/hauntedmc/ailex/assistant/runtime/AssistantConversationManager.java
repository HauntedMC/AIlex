package nl.hauntedmc.ailex.assistant.runtime;

import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Tracks active player-to-assistant dialogue independently from ambient server chat.
 * Recent turns are retained as a bounded working-memory window so follow-ups can resolve references and corrections.
 */
public class AssistantConversationManager {

    private static final int MAX_TURNS = 24;
    private static final int MAX_PROMPT_CHARACTERS = 6_000;
    private final Map<SessionKey, Session> sessions = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public AssistantConversationManager(LongSupplier clock) {
        this.clock = clock;
    }

    public Snapshot snapshot(UUID playerId, int npcId, long timeoutMillis) {
        SessionKey key = new SessionKey(playerId, npcId);
        Session session = sessions.get(key);
        if (session == null) {
            return Snapshot.empty();
        }
        synchronized (session) {
            if (expired(session, timeoutMillis)) {
                sessions.remove(key, session);
                return Snapshot.empty();
            }
            return snapshot(session);
        }
    }

    public void recordUser(UUID playerId, int npcId, String speaker, String message) {
        Session session = sessions.computeIfAbsent(new SessionKey(playerId, npcId), ignored -> new Session());
        synchronized (session) {
            session.lastActivityMillis = clock.getAsLong();
            session.pendingAnswer = true;
            session.previousUserMessage = compact(message, 720);
            session.turns.addLast(new Turn("user", compact(speaker, 48), compact(message, 900)));
            trim(session.turns);
        }
    }

    public void recordAssistant(UUID playerId, int npcId, String speaker, String message, AssistantIntent intent) {
        Session session = sessions.computeIfAbsent(new SessionKey(playerId, npcId), ignored -> new Session());
        synchronized (session) {
            session.lastActivityMillis = clock.getAsLong();
            session.pendingAnswer = false;
            session.previousAssistantMessage = compact(message, 720);
            session.previousIntent = intent;
            session.turns.addLast(new Turn("assistant", compact(speaker, 48), compact(message, 900)));
            trim(session.turns);
        }
    }

    public ActiveTarget activeTarget(UUID playerId, long timeoutMillis) {
        long now = clock.getAsLong();
        return sessions.entrySet().stream()
                .filter(entry -> entry.getKey().playerId().equals(playerId))
                .map(entry -> {
                    Session session = entry.getValue();
                    synchronized (session) {
                        if (now - session.lastActivityMillis > timeoutMillis) {
                            return null;
                        }
                        return new ActiveTarget(entry.getKey().npcId(), snapshot(session), session.lastActivityMillis);
                    }
                })
                .filter(java.util.Objects::nonNull)
                .max(Comparator.comparingLong(ActiveTarget::lastActivityMillis))
                .orElse(null);
    }

    /**
     * Decides whether an unmentioned message belongs to the active AIlex dialogue. A bare question mark is not enough:
     * the turn must carry a conversational continuation, correction or explicit reference to prior assistant content.
     */
    public boolean isLikelyFollowUp(String message, Snapshot snapshot) {
        if (snapshot == null || !snapshot.active() || message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320) {
            return false;
        }
        if (startsLikeFollowUp(normalized) || correctionLikeFollowUp(normalized) || referencesPriorTurn(normalized)) {
            return true;
        }
        // While the model is still answering, allow terse acknowledgements/continuations but not an arbitrary new
        // public question. This preserves fast conversational turns without capturing normal server chat.
        return snapshot.pendingAnswer() && normalized.length() <= 32 && isTerseContinuation(normalized);
    }

    private boolean startsLikeFollowUp(String text) {
        return List.of(
                "ja", "nee", "maar", "en ", "dus", "waarom", "hoezo", "wat dan", "welke", "waar dan",
                "wacht", "bedoel", "huh", "uh", "ok", "oke", "oké", "yes", "no", "but", "and ",
                "so ", "why", "how come", "what then", "which one", "where then", "wait", "i mean", "hmm",
                "eigenlijk", "actually", "correctie", "correction"
        ).stream().anyMatch(text::startsWith);
    }

    private boolean correctionLikeFollowUp(String text) {
        return text.contains("klopt niet") || text.contains("niet waar") || text.contains("je hebt het fout")
                || text.contains("je zit fout") || text.contains("that's wrong") || text.contains("you are wrong")
                || text.contains("you're wrong") || text.contains("not correct");
    }

    private boolean referencesPriorTurn(String text) {
        return text.matches(".*\\b(dit|dat|die|deze|daar|daarmee|daarover|ervoor|vorige|eerder|antwoord|uitleg)\\b.*")
                || text.matches(".*\\b(this|that|those|there|it|previous|earlier|answer|explanation|what you said)\\b.*");
    }

    private boolean isTerseContinuation(String text) {
        String stripped = text.replaceAll("[?!.,]+$", "").trim();
        return SetLike.TERSE.contains(stripped)
                || stripped.startsWith("maar ")
                || stripped.startsWith("en ")
                || stripped.startsWith("but ")
                || stripped.startsWith("and ");
    }

    private Snapshot snapshot(Session session) {
        StringBuilder context = new StringBuilder();
        if (session.previousIntent != null) {
            context.append("previous_intent=").append(session.previousIntent.name().toLowerCase(Locale.ROOT)).append('\n');
        }
        context.append("pending_answer=").append(session.pendingAnswer).append('\n');
        for (Turn turn : session.turns) {
            context.append(turn.role()).append('(').append(turn.speaker()).append("): ")
                    .append(turn.message()).append('\n');
        }
        String promptContext = context.toString().trim();
        if (promptContext.length() > MAX_PROMPT_CHARACTERS) {
            promptContext = "…" + promptContext.substring(promptContext.length() - MAX_PROMPT_CHARACTERS + 1);
        }
        return new Snapshot(
                true,
                session.pendingAnswer,
                session.previousIntent,
                session.previousUserMessage,
                session.previousAssistantMessage,
                promptContext
        );
    }

    private boolean expired(Session session, long timeoutMillis) {
        return clock.getAsLong() - session.lastActivityMillis > Math.max(1L, timeoutMillis);
    }

    private void trim(Deque<Turn> turns) {
        while (turns.size() > MAX_TURNS) {
            turns.removeFirst();
        }
    }

    private String compact(String value, int maxCharacters) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxCharacters
                ? normalized
                : normalized.substring(0, Math.max(0, maxCharacters - 1)) + "…";
    }

    public record Snapshot(
            boolean active,
            boolean pendingAnswer,
            AssistantIntent previousIntent,
            String previousUserMessage,
            String previousAssistantMessage,
            String promptContext
    ) {
        public static Snapshot empty() {
            return new Snapshot(false, false, null, "", "", "");
        }

        public AssistantDialogueContext asDialogueContext() {
            return new AssistantDialogueContext(
                    active, pendingAnswer, previousIntent, previousUserMessage, previousAssistantMessage, promptContext
            );
        }
    }

    public record ActiveTarget(int npcId, Snapshot snapshot, long lastActivityMillis) {
    }

    private record SessionKey(UUID playerId, int npcId) {
    }

    private record Turn(String role, String speaker, String message) {
    }

    private static final class Session {
        private final Deque<Turn> turns = new ArrayDeque<>();
        private long lastActivityMillis;
        private boolean pendingAnswer;
        private AssistantIntent previousIntent;
        private String previousUserMessage = "";
        private String previousAssistantMessage = "";
    }

    private static final class SetLike {
        private static final java.util.Set<String> TERSE = java.util.Set.of(
                "ja", "nee", "ok", "oke", "oké", "waarom", "hoezo", "wacht", "huh", "yes", "no", "okay",
                "why", "wait", "hmm"
        );

        private SetLike() {
        }
    }
}
