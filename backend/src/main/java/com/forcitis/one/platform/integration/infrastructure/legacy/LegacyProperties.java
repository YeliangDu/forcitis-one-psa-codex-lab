package com.forcitis.one.platform.integration.infrastructure.legacy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * forcitis.platform.integration.legacy.*: the read-only connection to the old system's database, used only for the daily
 * org unit short-name read. viewName is the ONE object the account may read (docs/modernization/phase1a/LEGACY_ORG_NAMES_VIEW.sql).
 * Values come from environment variables; enabled=false (default) means no access at all.
 */
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
