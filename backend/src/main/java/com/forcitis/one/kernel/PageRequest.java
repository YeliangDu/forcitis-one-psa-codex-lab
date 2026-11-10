package com.forcitis.one.kernel;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * limit defaults to 50 and is at most 200 (ListLimits). The cursor is opaque to the caller, versioned, unsigned, and means one list only:
 * it only says where to go on, the data scope is enforced in the query, so a forged cursor reaches nothing a caller may not read.
 */
public record PageRequest(int limit, @Nullable String cursorValue) {
    private static final Logger LOG = LoggerFactory.getLogger(PageRequest.class);
    private static final String CURSOR_VERSION = "v1";
    /** Room for the longest key of any list (128 characters, up to 3 bytes each) plus version and list name, as base64. */
    private static final int MAX_CURSOR_LENGTH = 1024;

    public PageRequest {
        if (limit < 1 || limit > ListLimits.MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + ListLimits.MAX_LIMIT);
        }
    }

    public static PageRequest of(@Nullable Integer limit, @Nullable String cursor, String list) {
        return new PageRequest(limit == null ? ListLimits.DEFAULT_LIMIT : limit, decode(cursor, list).orElse(null));
    }

    public @Nullable UUID cursorUuid() {
        if (cursorValue == null) {
            return null;
        }
        try {
            return Uuids.parseCanonical(cursorValue);
        } catch (IllegalArgumentException notUuid) {
            throw new InvalidCursorException("the cursor key is not a UUID");
        }
    }

    public @Nullable Integer cursorNumber() {
        if (cursorValue == null) {
            return null;
        }
        try {
            return Integer.valueOf(cursorValue);
        } catch (NumberFormatException notNumber) {
            throw new InvalidCursorException("the cursor key is not a whole number");
        }
    }

    /**
     * Cuts the page out of a list that is already in its final order; key names a row uniquely and is what the cursor carries.
     *
     * @deprecated lists page by key in the database; callers move over in K-3b and this is deleted with the last one.
     */
    @Deprecated
    public <T> CursorPage<T> slice(java.util.List<T> ordered, java.util.function.Function<T, String> key, String list) {
        int start = 0;
        if (cursorValue != null) {
            start = -1;
            for (int i = 0; i < ordered.size(); i++) {
                if (key.apply(ordered.get(i)).equals(cursorValue)) {
                    start = i + 1;
                    break;
                }
            }
            if (start < 0) {
                throw new InvalidCursorException("the row the cursor points at is gone");
            }
        }
        int end = Math.min(ordered.size(), start + limit);
        java.util.List<T> items = ordered.subList(start, end);
        return CursorPage.of(items, end < ordered.size() ? encode(list, key.apply(items.getLast())) : null);
    }

    public static String encode(String list, String key) {
        String text = CURSOR_VERSION + ":" + list + ":" + key;
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        try {
            // UTF-8 encoding replaces unpaired surrogates; readable replacement text is not a lossless cursor.
            if (!readText(cursor).equals(text)) {
                throw new InvalidCursorException("the cursor text cannot be encoded without loss");
            }
            decode(cursor, list);
        } catch (InvalidCursorException unreadable) {
            LOG.error("ALERT kernel the cursor of list {} cannot be issued: it would not read back ({})", list, unreadable.getMessage());
            throw new IllegalStateException("a cursor of list " + list + " would not read back: " + unreadable.getMessage());
        }
        return cursor;
    }

    private static Optional<String> decode(@Nullable String cursor, String list) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        String[] parts = readText(cursor).split(":", 3);
        if (parts.length != 3 || !CURSOR_VERSION.equals(parts[0])) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
        if (!parts[1].equals(list)) {
            throw new InvalidCursorException("the cursor belongs to another list");
        }
        if (parts[2].isEmpty() || parts[2].chars().anyMatch(Character::isISOControl)) {
            throw new InvalidCursorException("the cursor carries no usable key");
        }
        return Optional.of(parts[2]);
    }

    private static String readText(String cursor) {
        if (cursor.length() > MAX_CURSOR_LENGTH) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(cursor);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(cursor)) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
    }
}
