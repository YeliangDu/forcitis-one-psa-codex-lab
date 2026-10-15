package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** views.enabled=false means no database access; an empty sso.base-url means the ticket check answers "unavailable". */
@ConfigurationProperties("forcitis.platform.integration.seeyon")
record SeeyonProperties(@DefaultValue Views views, @DefaultValue Sso sso) {

    record Views(@DefaultValue("false") boolean enabled, String jdbcUrl, String username, String password,
                 long headRelationshipTypeId,
                 long regionalReceivablesManagerRelationshipTypeId, String deliveryUnitYesEnumId, String feeEarnerYesEnumId, String rootPath) {

        SeeyonMapping mapping() {
            return new SeeyonMapping(headRelationshipTypeId, regionalReceivablesManagerRelationshipTypeId,
                    deliveryUnitYesEnumId, feeEarnerYesEnumId, rootPath);
        }
    }

    record Sso(@DefaultValue("") String baseUrl, @DefaultValue("5s") Duration timeout) {
    }
}
