package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** One limit per call kind, applied to the query, the network read and the lock wait alike. */
@ConfigurationProperties("forcitis.platform.integration.external-connection")
record ExternalConnectionProperties(@DefaultValue("3s") Duration interactiveTimeout, @DefaultValue("20s") Duration backgroundTimeout) {

    static final Duration INTERACTIVE_CEILING = Duration.ofSeconds(3);
    static final Duration BACKGROUND_CEILING = Duration.ofSeconds(30);

    ExternalConnectionProperties {
        require("interactive-timeout", interactiveTimeout, INTERACTIVE_CEILING);
        require("background-timeout", backgroundTimeout, BACKGROUND_CEILING);
    }

    Duration timeoutOf(ExternalCallKind kind) {
        return kind == ExternalCallKind.INTERACTIVE ? interactiveTimeout : backgroundTimeout;
    }

    private static void require(String key, Duration value, Duration ceiling) {
        if (value.isZero() || value.isNegative() || value.compareTo(ceiling) > 0) {
            throw new IllegalArgumentException("forcitis.platform.integration.external-connection." + key + " must be above zero and at most " + ceiling + ", was " + value);
        }
    }
}
