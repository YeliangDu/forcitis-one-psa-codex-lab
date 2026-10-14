package com.forcitis.one.platform.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The administrator mode lasts at most one hour and is never extended (ADR-0026 section 5). */
@ConfigurationProperties("forcitis.platform.identity.administrator-mode")
public record AdministratorModeSettings(@DefaultValue("1h") Duration duration) {

    public AdministratorModeSettings {
        if (duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("the administrator mode lasts between 1 second and 1 hour");
        }
    }
}
