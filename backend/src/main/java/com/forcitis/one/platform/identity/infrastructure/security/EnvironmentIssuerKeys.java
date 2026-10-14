package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.domain.IssuerKeys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Only "env:FORCITIS_TICKET_KEY_<NAME>" references are accepted so a database row cannot point at an unrelated secret; keys under 32 characters are refused. */
@Component
class EnvironmentIssuerKeys implements IssuerKeys {

    private static final Pattern REFERENCE = Pattern.compile("env:(FORCITIS_TICKET_KEY_[A-Z0-9_]{1,48})");
    private static final int MIN_KEY_LENGTH = 32;

    private final Environment environment;

    EnvironmentIssuerKeys(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<byte[]> resolve(String keyReference) {
        var matcher = REFERENCE.matcher(keyReference == null ? "" : keyReference);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String value = environment.getProperty(matcher.group(1));
        if (value == null || value.length() < MIN_KEY_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(value.getBytes(StandardCharsets.UTF_8));
    }
}
