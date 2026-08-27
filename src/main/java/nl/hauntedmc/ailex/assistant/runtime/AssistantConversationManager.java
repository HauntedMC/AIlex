package nl.hauntedmc.ailex.assistant.runtime;

import nl.hauntedmc.ailex.assistant.domain.AssistantDialogueContext;
import nl.hauntedmc.ailex.assistant.domain.AssistantIntent;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Tracks active player-to-assistant dialogue independently from ambient server chat.
 *
 * <p>Recent turns stay verbatim while older turns are folded into a bounded mid-term digest and a tiny topic state.
 * This gives AIlex short/mid-term conversational continuity without turning raw dialogue into durable long-term memory.</p>
 */
public class AssistantConversationManager {

    private static final int MAX_TURNS = 28;
    private static final int MAX_PROMPT_CHARACTERS = 8_000;
    private static final int MAX_MIDTERM_CHARACTERS = 1_800;
    private static final int MAX_TOPIC_TERMS = 10;
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}_/-]+");
    private static final Set<String> TOPIC_STOP_WORDS = Set.of(
            "ailex", "als", "and", "ben", "bij", "dan", "dat", "de", "deze", "die", "dit", "een", "en",
            "for", "haunted", "hauntedmc", "haunty", "heb", "het", "hoe", "i", "ik", "in", "is", "it", "je",
            "jij", "kan", "maar", "me", "met", "mijn", "naar", "niet", "of", "om", "op", "the", "to", "van",
            "wat", "we", "wel", "why", "with", "you", "your"
    );

    private final Map<SessionKey, Session> sessions = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final AtomicLong lastCleanupMillis = new AtomicLong(Long.MIN_VALUE);

    public AssistantConversationManager(LongSupplier clock) {
        this.clock = clock;
    }

    public Snapshot snapshot(UUID playerId, int npcId, long timeoutMillis) {
        cleanupExpiredSessions(timeoutMillis);
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
            updateTopics(session, message);
            session.turns.addLast(new Turn("user", compact(speaker, 48), compact(message, 900)));
            trim(session);
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
            trim(session);
        }
    }

    public ActiveTarget activeTarget(UUID playerId, long timeoutMillis) {
        cleanupExpiredSessions(timeoutMillis);
        long now = clock.getAsLong();
        return sessions.entrySet().stream()
                .filter(entry -> entry.getKey().playerId().equals(playerId))
                .map(entry -> {
                    Session session = entry.getValue();
                    synchronized (session) {
                        if (now - session.lastActivityMillis > timeoutMillis) {
                            sessions.remove(entry.getKey(), session);
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
        if (text.matches("^(dit|dat|die|deze|daar|daarmee|daarover|ervoor|vorige|eerder)\\b.*")
                || text.matches("^(this|that|those|there|it|previous|earlier)\\b.*")) {
            return true;
        }
        return containsAny(text,
                "wat je zei", "wat jij zei", "je antwoord", "jouw antwoord", "je uitleg", "jouw uitleg",
                "leg dat uit", "leg dit uit", "dat verder uitleggen", "dit verder uitleggen", "die vorige",
                "what you said", "your answer", "your explanation", "explain that", "explain this",
                "that explanation", "this explanation", "the previous answer"
        );
    }

    private boolean isTerseContinuation(String text) {
        String stripped = text.replaceAll("[?!.,]+$", "").trim();
        return SetLike.TERSE.contains(stripped)
                || stripped.startsWith("maar ")
                || stripped.startsWith("en ")
                || stripped.startsWith("but ")
                || stripped.startsWith("and ");
    }

    private void cleanupExpiredSessions(long timeoutMillis) {
        long now = clock.getAsLong();
        long previous = lastCleanupMillis.get();
        if (previous != Long.MIN_VALUE && now - previous < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanupMillis.compareAndSet(previous, now)) {
            return;
        }
        long maximumAge = Math.max(1L, timeoutMillis);
        sessions.forEach((key, session) -> {
            synchronized (session) {
                if (now - session.lastActivityMillis > maximumAge) {
                    sessions.remove(key, session);
                }
            }
        });
    }

    private boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private Snapshot snapshot(Session session) {
        StringBuilder context = new StringBuilder();
        if (session.previousIntent != null) {
            context.append("previous_intent=").append(session.previousIntent.name().toLowerCase(Locale.ROOT)).append('\n');
        }
        context.append("pending_answer=").append(session.pendingAnswer).append('\n');
        if (!session.topics.isEmpty()) {
            context.append("session_topics=").append(String.join(",", session.topics)).append('\n');
        }
        if (!session.midtermDigest.isBlank()) {
            context.append("midterm_dialogue=").append(session.midtermDigest).append('\n');
        }
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

    private void trim(Session session) {
        while (session.turns.size() > MAX_TURNS) {
            rememberMidterm(session, session.turns.removeFirst());
        }
    }

    private void rememberMidterm(Session session, Turn turn) {
        String fragment = turn.role() + ": " + compact(turn.message(), 180);
        String combined = session.midtermDigest.isBlank() ? fragment : session.midtermDigest + " | " + fragment;
        session.midtermDigest = combined.length() <= MAX_MIDTERM_CHARACTERS
                ? combined
                : "…" + combined.substring(combined.length() - MAX_MIDTERM_CHARACTERS + 1);
    }

    private void updateTopics(Session session, String message) {
        for (String token : TOKEN_SEPARATOR.split(message == null ? "" : message.toLowerCase(Locale.ROOT))) {
            String topic = token.trim();
            if (topic.length() < 3 || TOPIC_STOP_WORDS.contains(topic) || topic.matches("\\d+")) {
                continue;
            }
            session.topics.remove(topic);
            session.topics.addLast(topic);
            while (session.topics.size() > MAX_TOPIC_TERMS) {
                session.topics.removeFirst();
            }
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
        private final Deque<String> topics = new ArrayDeque<>();
        private long lastActivityMillis;
        private boolean pendingAnswer;
        private AssistantIntent previousIntent;
        private String previousUserMessage = "";
        private String previousAssistantMessage = "";
        private String midtermDigest = "";
    }

    private static final class SetLike {
        private static final Set<String> TERSE = Set.of(
                "ja", "nee", "ok", "oke", "oké", "waarom", "hoezo", "wacht", "huh", "yes", "no", "okay",
                "why", "wait", "hmm"
        );

        private SetLike() {
        }
    }
}
