package com.forcitis.one;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The *_version_id columns that point at a version table of another schema by ID only, from architecture/cross-schema-version-references.txt (D2, B4). */
final class CrossSchemaVersionReferences {
    static final Path FILE = Path.of("architecture/cross-schema-version-references.txt");

    private CrossSchemaVersionReferences() {
    }

    /** Source column (schema.table.column) to target version table (schema.table); a line without a note or a repeated column is refused. */
    static Map<String, String> read() {
        try {
            return parse(Files.readAllLines(FILE));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> references = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int hash = trimmed.indexOf('#');
            String[] sides = hash <= 0 ? new String[0] : trimmed.substring(0, hash).split("->");
            if (sides.length != 2 || trimmed.substring(hash + 1).isBlank()) {
                throw new IllegalStateException(FILE + ": '" + trimmed + "' needs <schema.table.column> -> <schema.table>  # note");
            }
            if (references.putIfAbsent(sides[0].strip(), sides[1].strip()) != null) {
                throw new IllegalStateException(FILE + ": '" + sides[0].strip() + "' is listed twice");
            }
        }
        return references;
    }
}
