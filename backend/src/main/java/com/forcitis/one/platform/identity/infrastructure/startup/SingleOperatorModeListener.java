package com.forcitis.one.platform.identity.infrastructure.startup;

import java.util.Set;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * ADR-0030 section 1: decides the single operator mode before any bean exists, from the raw string of the key in every property source (not through
 * Boolean binding, which reads an empty string as unset). Whitelist: only the literals true and false are values, prod refuses the key under any
 * value, true needs dev among the active profiles (a dev in the default profiles does not count). The refusals use the effective profiles: the active ones, or the default ones when none is active, as @Profile does. Runs before the other listeners that end the process (migrate, read-only verification).
 */
final class SingleOperatorModeListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
    static final String KEY = "forcitis.platform.identity.single-operator-mode";
    private static final ConfigurationPropertyName NAME = ConfigurationPropertyName.of(KEY);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        resolve(event.getEnvironment());
    }

    static boolean resolve(ConfigurableEnvironment environment) {
        Set<String> active = Set.of(environment.getActiveProfiles());
        Set<String> effective = active.isEmpty() ? Set.of(environment.getDefaultProfiles()) : active;
        boolean prod = effective.contains("prod");
        if (effective.contains("identity-operations") && !effective.contains("dev") && !prod) {
            throw refused("the profile identity-operations needs the environment profile too (dev or prod)");
        }
        boolean present = false;
        Object value = null;
        for (ConfigurationPropertySource source : ConfigurationPropertySources.get(environment)) {
            ConfigurationProperty property = source.getConfigurationProperty(NAME);
            if (property != null) {
                if (!present) {
                    value = property.getValue();
                }
                present = true;
            }
        }
        if (!present) {
            return false;
        }
        if (prod) {
            throw refused("the key must not be set while the profile prod is active");
        }
        String text = value == null ? null : value.toString();
        if ("false".equals(text)) {
            return false;
        }
        if (!"true".equals(text)) {
            throw refused("the value must be exactly true or false");
        }
        if (!active.contains("dev")) {
            throw refused("the value true needs the profile dev to be active");
        }
        return true;
    }

    private static IllegalStateException refused(String reason) {
        return new IllegalStateException(KEY + ": " + reason + " (ADR-0030)");
    }
}
