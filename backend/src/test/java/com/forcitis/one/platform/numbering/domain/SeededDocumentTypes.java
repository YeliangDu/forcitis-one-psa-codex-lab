package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.DocumentTypeCode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** The four rows the numbering baseline registers, for tests that build a rule without a database. */
public final class SeededDocumentTypes {

    private SeededDocumentTypes() {
    }

    public static DocumentTypeEntry of(String code) {
        return switch (code) {
            case "QUOTE" -> entry(1, code, "QT", "presales.opportunity", false);
            case "TIMESHEET" -> entry(2, code, "TS", "cost.timesheet", false);
            case "JOURNAL_VOUCHER" -> entry(3, code, "JV", "accounting.ledger", true);
            case "EMPLOYEE" -> entry(4, code, "EMP", "organization.workforce", false);
            default -> throw new IllegalArgumentException("not a seeded document type: " + code);
        };
    }

    private static DocumentTypeEntry entry(int n, String code, String token, String ownerBcCode, boolean finance) {
        return new DocumentTypeEntry(new UUID(0x019a000000007000L, 0x8000000000000400L + n), new DocumentTypeCode(code), Optional.of(token), ownerBcCode, finance,
                LocalDate.of(2000, 1, 1), Optional.empty());
    }
}
