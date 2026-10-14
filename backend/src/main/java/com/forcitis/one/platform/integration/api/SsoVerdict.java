package com.forcitis.one.platform.integration.api;

public sealed interface SsoVerdict {

    record LoginName(String value) implements SsoVerdict {
    }

    record Invalid() implements SsoVerdict {
    }

    record Unavailable() implements SsoVerdict {
    }
}
