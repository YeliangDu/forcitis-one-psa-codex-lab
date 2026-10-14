package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * forcitis.platform.integration.seeyon.*: the read-only organisation views connection (values come from environment
 * variables, never from the repository) with the instance-specific ids, and the single-sign-on ticket check. Off by
 * default: views.enabled=false means no database access at all; an empty sso.base-url means the ticket check answers
 * "unavailable".
 */
@ConfigurationProperties("forcitis.platform.integration.seeyon")
record SeeyonProperties(@DefaultValue Views views, @DefaultValue Sso sso) {

    record Views(@DefaultValue("false") boolean enabled, String jdbcUrl, String username, String password,
                 @DefaultValue("false") boolean validateOnStartup, long headRelationshipTypeId,
                 long regionalReceivablesManagerRelationshipTypeId, String deliveryUnitYesEnumId, String feeEarnerYesEnumId) {

        SeeyonMapping mapping() {
            return new SeeyonMapping(headRelationshipTypeId, regionalReceivablesManagerRelationshipTypeId,
                    deliveryUnitYesEnumId, feeEarnerYesEnumId);
        }
    }

    record Sso(@DefaultValue("") String baseUrl, @DefaultValue("5s") Duration timeout) {
    }
}
