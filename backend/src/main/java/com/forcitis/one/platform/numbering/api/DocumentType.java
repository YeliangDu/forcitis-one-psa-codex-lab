package com.forcitis.one.platform.numbering.api;

import java.util.Optional;

/**
 * Typed catalogue of numbered documents. Each has a rule row in platform_numbering.numbering_rules. A type may be registered
 * with a short code (used by the {TYPE} placeholder of the unified number format, ADR-0010 decision 5); codes are
 * registered stage by stage, and the code must be a glossary word (see DocumentTypeCodeTest).
 */
public enum DocumentType {
    /** Quotation; first user of the unified format (Phase 1b). */
    QUOTE("QT"),
    TIMESHEET(null),
    JOURNAL_VOUCHER(null);

    private final String code;

    DocumentType(String code) {
        this.code = code;
    }

    /** The registered code, e.g. QT; empty while the type has not been registered for the unified format. */
    public Optional<String> code() {
        return Optional.ofNullable(code);
    }
}
