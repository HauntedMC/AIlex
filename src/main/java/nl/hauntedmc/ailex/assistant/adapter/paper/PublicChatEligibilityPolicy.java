package nl.hauntedmc.ailex.assistant.adapter.paper;

import java.util.Set;
import java.util.UUID;

/**
 * Conservative automatic public-chat detection, independent of channel names and plugin identities.
 * Audience coverage cannot distinguish a private channel containing everyone online or renderer-only hiding.
 */
final class PublicChatEligibilityPolicy {

    boolean allows(UUID sender, Set<UUID> recipients, Set<UUID> onlinePlayers) {
        return onlinePlayers.contains(sender)
                && recipients.contains(sender)
                && recipients.containsAll(onlinePlayers);
    }
}
