package com.forcitis.one.platform.integration.infrastructure.legacy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** viewName is the frozen short-name view (ADR-0037 item 6); enabled=false (default) means no access at all. */
@ConfigurationProperties("forcitis.platform.integration.legacy")
record LegacyProperties(@DefaultValue("false") boolean enabled, String jdbcUrl, String username, String password,
                        @DefaultValue("dbo.v_one_legacy_org_short_name") String viewName) {

    LegacyProperties {
        if (!viewName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?")) {
            throw new IllegalArgumentException("forcitis.platform.integration.legacy.view-name must be [schema.]name, was " + viewName);
        }
    }

    @Override
    public String toString() {
        return "LegacyProperties[enabled=" + enabled + ", jdbcUrl=" + jdbcUrl + ", username=" + username + ", password="
                + (password == null || password.isBlank() ? "none" : "***") + ", viewName=" + viewName + "]";
    }
}
