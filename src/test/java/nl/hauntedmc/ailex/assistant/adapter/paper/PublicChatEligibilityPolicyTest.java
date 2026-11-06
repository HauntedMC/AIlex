package nl.hauntedmc.ailex.assistant.adapter.paper;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicChatEligibilityPolicyTest {

    private final PublicChatEligibilityPolicy policy = new PublicChatEligibilityPolicy();
    private final UUID sender = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void acceptsOnlyCoverageOfAllOnlinePlayersIncludingSender() {
        assertTrue(policy.allows(sender, Set.of(sender, other), Set.of(sender, other)));
        assertFalse(policy.allows(sender, Set.of(sender), Set.of(sender, other)));
        assertFalse(policy.allows(sender, Set.of(other), Set.of(sender, other)));
        assertFalse(policy.allows(sender, Set.of(), Set.of(sender, other)));
        assertFalse(policy.allows(sender, Set.of(sender, other), Set.of(other)));
        assertFalse(policy.allows(sender, Set.of(sender), Set.of()));
    }

    @Test
    void coverageAllowsSoloPublicChatAndRecipientsWhoHaveSinceDisconnected() {
        assertTrue(policy.allows(sender, Set.of(sender), Set.of(sender)));
        assertTrue(policy.allows(sender, Set.of(sender, other), Set.of(sender)));
    }
}
