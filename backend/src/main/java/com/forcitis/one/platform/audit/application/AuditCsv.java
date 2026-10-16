package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.domain.AuditRecord;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A cell that a spreadsheet would read as a formula is prefixed with an apostrophe (CSV injection). */
final class AuditCsv {

    private static final String TRIGGERS = "=+-@\t\r\uFF1D\uFF0B\uFF0D\uFF20";
    private static final String HEADER = "entryId,createdAt,action,actorId,onBehalfOfId,subjectKind,subjectId,detail,sourceAddress,outcome,loginMethod";

    private AuditCsv() {
    }

    static String write(List<AuditRecord> rows) {
        return Stream.concat(Stream.of(HEADER), rows.stream().map(AuditCsv::line)).collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    private static String line(AuditRecord row) {
        return Stream.of(String.valueOf(row.entryId().value()), row.createdAt().toString(), cell(row.action()), id(row.actorId()), id(row.onBehalfOfId()),
                row.subject().kind().name(), id(row.subject().id()), cell(row.detail()), cell(row.sourceAddress()), row.outcome() == null ? "" : row.outcome().name(),
                cell(row.loginMethod())).collect(Collectors.joining(","));
    }

    private static String id(UserId userId) {
        return userId == null ? "" : String.valueOf(userId.value());
    }

    private static String id(java.util.UUID id) {
        return id == null ? "" : id.toString();
    }

    /** The first character that is not a space decides, and the full-width forms of the trigger characters count as well. */
    private static boolean readAsFormula(String value) {
        if (!value.isEmpty() && TRIGGERS.indexOf(value.charAt(0)) >= 0) {
            return true;
        }
        int first = 0;
        while (first < value.length() && (Character.isWhitespace(value.charAt(first)) || Character.isSpaceChar(value.charAt(first)))) {
            first++;
        }
        return first < value.length() && TRIGGERS.indexOf(value.charAt(first)) >= 0;
    }

    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = readAsFormula(value) ? "'" + value : value;
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r") || safe.startsWith("'")
                ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
