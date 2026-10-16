package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.audit.domain.AuditEntryId;
import com.forcitis.one.platform.audit.domain.AuditPosition;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** The cursor key of the audit lists is "<microseconds>|<entry id>"; the list name keeps the cursor of one list out of another. */
final class AuditCursor {

    private AuditCursor() {
    }

    static String encode(String list, AuditPosition position) {
        return PageRequest.encode(list, ChronoUnit.MICROS.between(Instant.EPOCH, position.createdAt()) + "|" + position.entryId().value());
    }

    static AuditPosition decode(PageRequest page) {
        if (page.cursorValue() == null) {
            return null;
        }
        String[] parts = page.cursorValue().split("\\|");
        try {
            return new AuditPosition(Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS), new AuditEntryId(Uuids.parseCanonical(parts[1])));
        } catch (RuntimeException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
    }
}
