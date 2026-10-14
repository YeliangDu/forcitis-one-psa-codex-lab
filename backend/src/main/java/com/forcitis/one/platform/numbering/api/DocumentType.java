package com.forcitis.one.platform.numbering.api;

import java.util.Optional;

/** A type's code feeds {TYPE} (ADR-0010 decision 5) and must be a glossary word (DocumentTypeCodeTest). */
public enum DocumentType {
    QUOTE("QT"),
    TIMESHEET(null),
    JOURNAL_VOUCHER(null);

    private final String code;

    DocumentType(String code) {
        this.code = code;
    }

    /** Empty while the type is not registered for the unified format. */
    public Optional<String> code() {
        return Optional.ofNullable(code);
    }
}
