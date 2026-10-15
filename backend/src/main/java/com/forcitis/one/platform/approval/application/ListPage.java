package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.ListLimits;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.approval.api.ApprovalError;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Which part of a list the caller wants: the page after cursor, at most limit long. A cursor is only ever one this module handed
 * out; anything else is a malformed request, not an empty page.
 */
public record ListPage(Optional<Position> cursor, int limit) {

    /** The place in a list after which a page starts: the sort time and the id of the last item of the page before. */
    public record Position(Instant at, String id) {

        String encode() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static ListPage first() {
        return new ListPage(Optional.empty(), ListLimits.DEFAULT_LIMIT);
    }

    public static ListPage of(String cursor, Integer limit) {
        int size = limit == null ? ListLimits.DEFAULT_LIMIT : limit;
        if (size < 1 || size > ListLimits.MAX_LIMIT) {
            throw ApprovalError.PLATFORM_APPROVAL_PAGE_INVALID.refusal("limit must be between 1 and " + ListLimits.MAX_LIMIT + ", not " + size);
        }
        return new ListPage(cursor == null || cursor.isBlank() ? Optional.empty() : Optional.of(decode(cursor)), size);
    }

    private static Position decode(String cursor) {
        try {
            String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int bar = text.indexOf('|');
            return new Position(Instant.parse(text.substring(0, bar)), text.substring(bar + 1));
        } catch (IllegalArgumentException | StringIndexOutOfBoundsException | DateTimeParseException malformed) {
            throw ApprovalError.PLATFORM_APPROVAL_PAGE_INVALID.refusal("The cursor is not one this list handed out");
        }
    }

    /** The id part of a cursor, which only this module's own lists make up: anything else is as malformed as an unreadable cursor. */
    public static UUID idOf(Position position) {
        try {
            return Uuids.parseCanonical(position.id());
        } catch (IllegalArgumentException malformed) {
            throw ApprovalError.PLATFORM_APPROVAL_PAGE_INVALID.refusal("The cursor is not one this list handed out");
        }
    }

    public static String cursorAfter(Instant at, String id) {
        return new Position(at, id).encode();
    }
}
