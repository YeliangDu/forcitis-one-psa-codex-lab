package com.forcitis.one.kernel;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One page of a list: nextCursor is null on the last page. Every list endpoint answers this shape. */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {
    public CursorPage {
        items = List.copyOf(items);
    }

    public static <T> CursorPage<T> of(List<T> items, @Nullable String nextCursor) {
        return new CursorPage<>(items, nextCursor);
    }
}
