package com.forcitis.one.platform.approval.infrastructure.legacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Read-only access to the old system's to-do view (REIMAGINED_ARCHITECTURE §3.6). All values come from the environment; there
 * is no default account. url blank = the old system is not connected (the merged inbox then reports legacyAvailable=false).
 * viewName is the ONE object the account may read (docs/modernization/phase1a/LEGACY_INBOX_VIEW.sql); detailUrlTemplate is the
 * old system's detail page with {processInstanceId} and {taskId} placeholders.
 */
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
