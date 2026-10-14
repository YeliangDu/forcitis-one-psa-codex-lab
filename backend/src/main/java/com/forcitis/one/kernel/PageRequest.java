package com.forcitis.one.kernel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** limit defaults to 50 and is at most 200; the cursor is opaque to the caller and means one list only. */
public record PageRequest(int limit, @Nullable String cursorValue) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public PageRequest {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidCursorException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public static PageRequest of(@Nullable Integer limit, @Nullable String cursor, String list) {
        return new PageRequest(limit == null ? DEFAULT_LIMIT : limit, decode(cursor, list).orElse(null));
    }

    /** Cuts the page out of a list that is already in its final order; key names a row uniquely and is what the cursor carries. */
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
        return Base64.getUrlEncoder().withoutPadding().encodeToString((list + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<String> decode(@Nullable String cursor, String list) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        String text;
        try {
            text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
        if (!text.startsWith(list + ":")) {
            throw new InvalidCursorException("the cursor belongs to another list");
        }
        return Optional.of(text.substring(list.length() + 1));
    }
}
