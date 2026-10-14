package com.forcitis.one.kernel;

/** One enum per module implements this; the constant is the code: {SUBDOMAIN}_{BC}_{REASON}, glossary words only (G48). */
public interface ErrorCode {
    String name();

    default String code() {
        return name();
    }
}
