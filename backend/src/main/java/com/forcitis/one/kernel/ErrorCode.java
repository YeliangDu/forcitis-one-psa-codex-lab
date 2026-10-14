package com.forcitis.one.kernel;

/**
 * Stable, machine-readable business error code returned to clients. Each module declares its codes as ONE enum
 * implementing this interface (a closed set the compiler checks in switches); the constant is the code:
 * {SUBDOMAIN}_{BC}_{REASON} of the declaring module, upper snake_case, glossary words only (G48).
 */
public interface ErrorCode {

    String name();

    default String code() {
        return name();
    }
}
