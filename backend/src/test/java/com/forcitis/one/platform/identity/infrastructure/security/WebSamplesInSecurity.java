package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.web.NotFromQuery;
import com.forcitis.one.platform.web.WebTypeSample;

/** Samples for ModuleDependencyTest: in the security package the marker is allowed, any other platform.web type is not (CS-5.9). */
final class WebSamplesInSecurity {
    @NotFromQuery(source = "the session")
    static final class Marked {
    }

    static final class UsesAnotherWebType {
        WebTypeSample sample;
    }

    private WebSamplesInSecurity() {
    }
}
