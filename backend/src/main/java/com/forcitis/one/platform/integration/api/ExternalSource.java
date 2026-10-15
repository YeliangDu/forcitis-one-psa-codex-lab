package com.forcitis.one.platform.integration.api;

/** The systems whose object ids are kept in source references; kernel.event.SourceSystem tells where an event came from, which is another matter. */
public enum ExternalSource {
    ORG_SOURCE,
    LEGACY_SYSTEM,
    ACCOUNTING_SYSTEM
}
