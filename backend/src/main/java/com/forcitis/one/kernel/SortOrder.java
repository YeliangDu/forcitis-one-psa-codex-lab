package com.forcitis.one.kernel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The parsed form of {@code sort=field,-field}; a field outside the allowed set is refused, never ignored. */
public record SortOrder(List<Key> keys) {
    public record Key(String field, boolean descending) {
    }

    public SortOrder {
        keys = List.copyOf(keys);
    }

    public static SortOrder parse(@Nullable String sort, Set<String> allowedFields) {
        if (sort == null || sort.isBlank()) {
            return new SortOrder(List.of());
        }
        List<Key> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : sort.split(",", -1)) {
            boolean descending = part.startsWith("-");
            String field = descending ? part.substring(1) : part;
            if (!allowedFields.contains(field)) {
                throw new InvalidRequestException("sort field is not allowed: " + field);
            }
            if (!seen.add(field)) {
                throw new InvalidRequestException("sort field is repeated: " + field);
            }
            keys.add(new Key(field, descending));
        }
        return new SortOrder(keys);
    }
}
