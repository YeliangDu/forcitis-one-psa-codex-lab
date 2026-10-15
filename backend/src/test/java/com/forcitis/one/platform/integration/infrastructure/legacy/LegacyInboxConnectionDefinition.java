package com.forcitis.one.platform.integration.infrastructure.legacy;

/** The runtime definition of the legacy to-do connection, for tests outside this package. */
public final class LegacyInboxConnectionDefinition {

    private LegacyInboxConnectionDefinition() {
    }

    public static String connectionName() {
        return LegacyInboxConfiguration.CONNECTION;
    }

    public static String propertyPrefix() {
        return LegacyInboxProperties.PREFIX;
    }
}
