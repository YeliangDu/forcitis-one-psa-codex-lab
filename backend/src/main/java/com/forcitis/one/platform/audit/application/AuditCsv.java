package com.forcitis.one.platform.audit.application;

import com.forcitis.one.platform.audit.domain.AuditRecord;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A cell that a spreadsheet would read as a formula is prefixed with an apostrophe (CSV injection). */
final class AuditCsv {

    private static final String HEADER = "entryId,createdAt,action,actorId,onBehalfOfId,subjectKey,detail,sourceAddress,outcome,loginMethod";

    private AuditCsv() {
    }

    static String write(List<AuditRecord> rows) {
        return Stream.concat(Stream.of(HEADER), rows.stream().map(AuditCsv::line)).collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    private static String line(AuditRecord row) {
        return Stream.of(String.valueOf(row.entryId().value()), row.createdAt().toString(), cell(row.action()), id(row.actorId()), id(row.onBehalfOfId()),
                cell(row.subjectKey()), cell(row.detail()), cell(row.sourceAddress()), row.outcome() == null ? "" : row.outcome().name(),
                cell(row.loginMethod())).collect(Collectors.joining(","));
    }

    private static String id(com.forcitis.one.kernel.UserId userId) {
        return userId == null ? "" : String.valueOf(userId.value());
    }

    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r") || safe.startsWith("'")
                ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
