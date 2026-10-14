package com.forcitis.one.platform.integration.infrastructure.legacy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** viewName is the ONE object the read-only account may read; enabled=false (default) means no access at all. */
@ConfigurationProperties("forcitis.platform.integration.legacy")
record LegacyProperties(@DefaultValue("false") boolean enabled, String jdbcUrl, String username, String password,
                        @DefaultValue("false") boolean validateOnStartup,
                        @DefaultValue("dbo.v_one_legacy_org_short_name") String viewName) {

    LegacyProperties {
        if (!viewName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?")) {
            throw new IllegalArgumentException("forcitis.platform.integration.legacy.view-name must be [schema.]name, was " + viewName);
        }
    }
}
