package com.forcitis.one.platform.identity.application;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** totpEncryptionKey is a Base64 AES-256 key from the environment / KMS; without it emergency login is impossible. */
@ConfigurationProperties("forcitis.platform.identity.emergency")
public record EmergencySettings(@DefaultValue({"127.0.0.1/32", "::1/128"}) List<String> internalNetworks,
                                @DefaultValue("5") int maxAttempts, @DefaultValue("15m") Duration lockDuration,
                                @DefaultValue("") String totpEncryptionKey) {

    /** The key must never reach a log line. */
    @Override
    public String toString() {
        return "EmergencySettings[internalNetworks=" + internalNetworks + ", maxAttempts=" + maxAttempts + ", lockDuration=" + lockDuration + "]";
    }
}
