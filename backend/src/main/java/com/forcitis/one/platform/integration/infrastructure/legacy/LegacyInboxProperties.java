package com.forcitis.one.platform.integration.infrastructure.legacy;

/** No default account; blank url means the old system's to-dos are not connected; viewName is the frozen to-do view (ADR-0037 item 6). */
record LegacyInboxProperties(String url, String username, String password, String viewName, String detailUrlTemplate) {

    static final String PREFIX = "forcitis.platform.integration.legacy.inbox";
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

    @Override
    public String toString() {
        return "LegacyInboxProperties[url=" + url + ", username=" + username + ", password=" + (password == null || password.isBlank() ? "none" : "***")
                + ", viewName=" + viewName + ", detailUrlTemplate=" + detailUrlTemplate + "]";
    }
}
