package com.forcitis.one.kernel;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * The ambient identity of the unit of work on this thread: the correlation id (kept in the logging MDC so every log line carries
 * it), the event being handled (the cause of what it publishes) and, outside a user session, the job or listener acting.
 */
public final class WorkContext {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final ThreadLocal<Frame> FRAME = ThreadLocal.withInitial(() -> new Frame(null, null));

    private record Frame(@Nullable UUID causationId, @Nullable String systemActor) {
    }

    private WorkContext() {
    }

    public static Optional<UUID> correlationId() {
        String value = MDC.get(MDC_KEY);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    public static Optional<UUID> causationId() {
        return Optional.ofNullable(FRAME.get().causationId());
    }

    public static Optional<String> systemActor() {
        return Optional.ofNullable(FRAME.get().systemActor());
    }

    /** Starts a unit of work (a request, a job run, a handled event); close restores the previous one. */
    public static Scope enter(UUID correlationId, @Nullable UUID causationId, @Nullable String systemActor) {
        String previousCorrelation = MDC.get(MDC_KEY);
        Frame previous = FRAME.get();
        MDC.put(MDC_KEY, correlationId.toString());
        FRAME.set(new Frame(causationId, systemActor));
        return () -> {
            if (previousCorrelation == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousCorrelation);
            }
            FRAME.set(previous);
        };
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
