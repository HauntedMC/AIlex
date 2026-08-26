package nl.hauntedmc.ailex.listener.llm;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** @deprecated use {@link nl.hauntedmc.ailex.assistant.runtime.PlayerResponseRateLimiter}. */
@Deprecated(forRemoval = true)
final class PlayerResponseRateLimiter extends nl.hauntedmc.ailex.assistant.runtime.PlayerResponseRateLimiter {
    PlayerResponseRateLimiter(Supplier<ResponseRateLimit> limitSupplier, LongSupplier currentTimeMillis) {
        super(limitSupplier, currentTimeMillis);
    }
}
