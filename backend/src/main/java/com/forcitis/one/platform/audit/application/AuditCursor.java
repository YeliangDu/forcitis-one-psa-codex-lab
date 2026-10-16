package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.audit.domain.AuditEntryId;
import com.forcitis.one.platform.audit.domain.AuditPageMark;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** The cursor key of the audit lists is "<microseconds>|<entry id>"; the list name keeps the cursor of one list out of another. */
final class AuditCursor {

    private AuditCursor() {
    }

    static String encode(String list, AuditPageMark position) {
        return PageRequest.encode(list, ChronoUnit.MICROS.between(Instant.EPOCH, position.createdAt()) + "|" + position.entryId().value());
    }

    static AuditPageMark decode(PageRequest page, Instant now) {
        if (page.cursorValue() == null) {
            return null;
        }
        String[] parts = page.cursorValue().split("\\|", -1);
        try {
            if (parts.length != 2 || !parts[0].matches("[0-9]{1,16}")) {
                throw new IllegalArgumentException("malformed cursor key");
            }
            Instant at = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);
            if (at.isAfter(now.plus(365, ChronoUnit.DAYS))) {
                throw new IllegalArgumentException("cursor time out of range");
            }
            return new AuditPageMark(at, new AuditEntryId(Uuids.parseCanonical(parts[1])));
        } catch (RuntimeException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
    }
}
