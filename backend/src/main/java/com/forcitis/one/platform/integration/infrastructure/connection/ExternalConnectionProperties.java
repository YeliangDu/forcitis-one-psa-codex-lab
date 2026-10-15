package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Four limits per call kind. A statement is cancelled on the server (lock-timeout, then query-timeout) before the socket gives up, so the connection survives and nothing keeps running over there. */
@ConfigurationProperties("forcitis.platform.integration.external-connection")
record ExternalConnectionProperties(Limits interactive, Limits background) {

    static final Limits INTERACTIVE_DEFAULT = new Limits(Duration.ofSeconds(1), Duration.ofMillis(1_500), Duration.ofSeconds(2), Duration.ofSeconds(3));
    static final Limits BACKGROUND_DEFAULT = new Limits(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(15), Duration.ofSeconds(20));
    static final Duration INTERACTIVE_BUDGET = Duration.ofSeconds(3);
    static final Duration INTERACTIVE_SOCKET_CEILING = Duration.ofSeconds(3);
    static final Duration BACKGROUND_ACQUIRE_CEILING = Duration.ofSeconds(5);
    static final Duration BACKGROUND_SOCKET_CEILING = Duration.ofSeconds(30);

    ExternalConnectionProperties {
        interactive = interactive == null ? INTERACTIVE_DEFAULT : interactive;
        background = background == null ? BACKGROUND_DEFAULT : background;
        Duration interactiveTotal = interactive.acquireTimeout().plus(interactive.queryTimeout());
        if (interactiveTotal.compareTo(INTERACTIVE_BUDGET) > 0) {
            throw new IllegalArgumentException(key("interactive") + ": acquire-timeout + query-timeout must be at most " + INTERACTIVE_BUDGET + ", was " + interactiveTotal);
        }
        requireAtMost("interactive.socket-timeout", interactive.socketTimeout(), INTERACTIVE_SOCKET_CEILING);
        requireAtMost("background.acquire-timeout", background.acquireTimeout(), BACKGROUND_ACQUIRE_CEILING);
        requireAtMost("background.socket-timeout", background.socketTimeout(), BACKGROUND_SOCKET_CEILING);
    }

    Limits limitsOf(ExternalCallKind kind) {
        return kind == ExternalCallKind.INTERACTIVE ? interactive : background;
    }

    private static void requireAtMost(String name, Duration value, Duration ceiling) {
        if (value.compareTo(ceiling) > 0) {
            throw new IllegalArgumentException(key(name) + " must be at most " + ceiling + ", was " + value);
        }
    }

    private static String key(String name) {
        return "forcitis.platform.integration.external-connection." + name;
    }

    record Limits(Duration acquireTimeout, Duration lockTimeout, Duration queryTimeout, Duration socketTimeout) {

        Limits {
            if (acquireTimeout == null || lockTimeout == null || queryTimeout == null || socketTimeout == null) {
                throw new IllegalArgumentException("acquire-timeout, lock-timeout, query-timeout and socket-timeout must all be set, or none of them");
            }
            for (Duration value : new Duration[] {acquireTimeout, lockTimeout, queryTimeout, socketTimeout}) {
                if (value.isZero() || value.isNegative()) {
                    throw new IllegalArgumentException("every external connection timeout must be above zero, was " + value);
                }
            }
            if (queryTimeout.toMillis() % 1_000 != 0) {
                throw new IllegalArgumentException("query-timeout is whole seconds, was " + queryTimeout);
            }
            if (lockTimeout.compareTo(queryTimeout) >= 0 || queryTimeout.compareTo(socketTimeout) >= 0) {
                throw new IllegalArgumentException("lock-timeout < query-timeout < socket-timeout must hold so the server cancels before the socket gives up, was "
                        + lockTimeout + " / " + queryTimeout + " / " + socketTimeout);
            }
        }
    }
}
