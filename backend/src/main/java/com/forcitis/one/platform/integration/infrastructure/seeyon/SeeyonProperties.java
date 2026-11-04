package com.forcitis.one.platform.integration.infrastructure.seeyon;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** views.enabled=false means no database access. */
@ConfigurationProperties("forcitis.platform.integration.seeyon")
record SeeyonProperties(@DefaultValue Views views) {

    record Views(@DefaultValue("false") boolean enabled, String jdbcUrl, String username, String password,
                 long headRelationshipTypeId,
                 long regionalReceivablesManagerRelationshipTypeId, String deliveryUnitYesEnumId, String feeEarnerYesEnumId, String rootPath) {

        SeeyonMapping mapping() {
            return new SeeyonMapping(headRelationshipTypeId, regionalReceivablesManagerRelationshipTypeId,
                    deliveryUnitYesEnumId, feeEarnerYesEnumId, rootPath);
        }
    }
}
