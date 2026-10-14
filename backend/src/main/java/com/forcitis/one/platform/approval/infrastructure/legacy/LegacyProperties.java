package com.forcitis.one.platform.approval.infrastructure.legacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** No default account; blank url means the old system is not connected; viewName is the ONE object the account may read. */
@ConfigurationProperties("forcitis.platform.approval.legacy")
record LegacyProperties(String url, String username, String password, String viewName, String detailUrlTemplate) {

    static final String DEFAULT_VIEW = "dbo.v_psa_one_legacy_todo";

    LegacyProperties {
        viewName = viewName == null || viewName.isBlank() ? DEFAULT_VIEW : viewName;
        if (!viewName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?")) {
            throw new IllegalArgumentException("forcitis.platform.approval.legacy.view-name must be [schema.]name, was " + viewName);
        }
    }

    boolean isConfigured() {
        return url != null && !url.isBlank();
    }
}
