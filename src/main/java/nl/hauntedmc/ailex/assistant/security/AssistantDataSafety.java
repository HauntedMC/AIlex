package nl.hauntedmc.ailex.assistant.security;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic defense-in-depth checks for data that may enter model context or durable memory. */
public final class AssistantDataSafety {

    private static final Set<String> SENSITIVE_KEY_TERMS = Set.of(
            "password", "passwd", "wachtwoord", "secret", "token", "api_key", "apikey", "credential",
            "email", "e_mail", "phone", "telephone", "telefoon", "ip", "ip_address", "ip_adres",
            "home_address", "woonadres", "address", "report", "rapport", "sanction", "ban_reason",
            "staff_note", "database_url", "jdbc", "redis_url"
    );
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9])(?:25[0-5]|2[0-4][0-9]|1?[0-9]{1,2})(?:\\.(?:25[0-5]|2[0-4][0-9]|1?[0-9]{1,2})){3}(?![0-9])"
    );
    private static final Pattern IPV6 = Pattern.compile(
            "(?i)(?<![0-9a-f])(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{0,4}(?![0-9a-f])"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b"
    );
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b"
    );
    private static final Pattern LONG_SECRET = Pattern.compile("(?i)\\b[a-z0-9_-]{40,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])\\+?[0-9][0-9 ()-]{8,}[0-9](?![0-9])");
    private static final Pattern COORDINATE_TRIPLE = Pattern.compile(
            "(?<![0-9])-?[0-9]{1,7}(?:\\s*,\\s*|\\s+)-?[0-9]{1,5}(?:\\s*,\\s*|\\s+)-?[0-9]{1,7}(?![0-9])"
    );

    private AssistantDataSafety() {
    }

    /** Blocks credentials and personal/network identifiers from trusted integration output as a final safety net. */
    public static boolean forbiddenLiveIntegration(String key, String value) {
        return sensitiveKey(key) || looksLikeSensitiveIdentifier(value);
    }

    /** Durable memory additionally rejects precise coordinate triples even when the candidate omits a location label. */
    public static boolean forbiddenDurableMemory(String key, String value) {
        return forbiddenLiveIntegration(key, value)
                || COORDINATE_TRIPLE.matcher(clean(value)).find();
    }

    private static boolean sensitiveKey(String key) {
        String normalized = clean(key).toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
        for (String term : SENSITIVE_KEY_TERMS) {
            if (containsToken(normalized, term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeSensitiveIdentifier(String value) {
        String clean = clean(value);
        return IPV4.matcher(clean).find()
                || IPV6.matcher(clean).find()
                || EMAIL.matcher(clean).find()
                || UUID.matcher(clean).find()
                || LONG_SECRET.matcher(clean).find()
                || PHONE.matcher(clean).find();
    }

    private static boolean containsToken(String value, String term) {
        int index = value.indexOf(term);
        while (index >= 0) {
            int end = index + term.length();
            boolean before = index == 0 || Character.isLetterOrDigit(value.charAt(index - 1)) == false;
            boolean after = end == value.length() || Character.isLetterOrDigit(value.charAt(end)) == false;
            if (before && after) {
                return true;
            }
            index = value.indexOf(term, index + 1);
        }
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
    }
}
