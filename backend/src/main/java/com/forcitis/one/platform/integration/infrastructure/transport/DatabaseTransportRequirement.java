package com.forcitis.one.platform.integration.infrastructure.transport;

import java.util.Set;

/** What a database connection must do to protect the wire: production verifies the server certificate and its host name, dev only encrypts. */
enum DatabaseTransportRequirement {
    NONE,
    ENCRYPTED,
    VERIFIED_SERVER;

    static DatabaseTransportRequirement forProfiles(Set<String> activeProfiles) {
        if (activeProfiles.contains("prod")) {
            return VERIFIED_SERVER;
        }
        if (activeProfiles.contains("dev")) {
            return ENCRYPTED;
        }
        return NONE;
    }
}
