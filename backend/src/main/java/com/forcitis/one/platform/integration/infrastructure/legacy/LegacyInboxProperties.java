package com.forcitis.one.platform.integration.infrastructure.legacy;

/** No default account; blank url means the old system's to-dos are not connected; viewName is the frozen to-do view (ADR-0037 item 6). */
record LegacyInboxProperties(String url, String username, String password, String viewName, String detailUrlTemplate) {

    static final String PREFIX = "forcitis.platform.integration.legacy.inbox";
    /** The keys before the reader moved into platform.integration (R-1b P3b); read only while the new url is not set. */
    static final String FORMER_PREFIX = "forcitis.platform.approval.legacy";
    static final String DEFAULT_VIEW = "dbo.v_psa_one_legacy_todo";

    LegacyInboxProperties {
        viewName = viewName == null || viewName.isBlank() ? DEFAULT_VIEW : viewName;
        if (!viewName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?")) {
            throw new IllegalArgumentException("the legacy to-do view-name must be [schema.]name, was " + viewName);
        }
    }

    static LegacyInboxProperties none() {
        return new LegacyInboxProperties(null, null, null, null, null);
    }

    boolean isConfigured() {
        return url != null && !url.isBlank();
    }
}
