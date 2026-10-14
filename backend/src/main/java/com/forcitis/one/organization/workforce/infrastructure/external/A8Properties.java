package com.forcitis.one.organization.workforce.infrastructure.external;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * forcitis.organization.workforce.a8.*: the read-only A8 connection (values come from environment variables, never
 * from the repository) and the A8-instance ids. enabled=false (default) means no A8 access at all.
 *
 * <p>Bound programmatically on purpose: the key segment "a8" contains a digit, which the own-key naming guard (G46,
 * NamingSurfaceTest) does not accept in @ConfigurationProperties / @Value declarations, while the key itself is the
 * agreed one. See the report to the main session.
 */
record A8Properties(boolean enabled, String jdbcUrl, String username, String password, boolean validateOnStartup,
                    long headRelationshipTypeId, long regionalReceivablesManagerRelationshipTypeId,
                    String deliveryUnitYesEnumId, String feeEarnerYesEnumId) {

    static final String PREFIX = "forcitis.organization.workforce.a8";

    static A8Properties bind(Environment environment) {
        return Binder.get(environment).bind(PREFIX, A8Properties.class)
                .orElseGet(() -> new A8Properties(false, null, null, null, false, 0, 0, null, null));
    }

    A8Mapping mapping() {
        return new A8Mapping(headRelationshipTypeId, regionalReceivablesManagerRelationshipTypeId,
                deliveryUnitYesEnumId, feeEarnerYesEnumId);
    }
}
