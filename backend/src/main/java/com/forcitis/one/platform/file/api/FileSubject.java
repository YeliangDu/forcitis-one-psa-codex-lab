package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** Who stored or linked a file: a user id, a one-time link id or a platform module id, by kind. */
@ValueObject
public record FileSubject(FileSubjectKind kind, String id) {

    public FileSubject {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("A file subject id must not be blank");
        }
    }
}
