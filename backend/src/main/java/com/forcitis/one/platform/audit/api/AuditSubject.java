package com.forcitis.one.platform.audit.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The business object an audit entry is about: its kind and its own id; the kinds without an object (REQUEST, OPERATION, LOGIN_ATTEMPT) have no id. */
@ValueObject
public record AuditSubject(AuditSubjectKind kind, @Nullable UUID id) {

    public AuditSubject {
        Objects.requireNonNull(kind, "kind");
        if (kind.hasObject() != (id != null)) {
            throw new IllegalArgumentException(kind + (kind.hasObject() ? " needs the id of its object" : " has no object, so no id"));
        }
    }

    public static AuditSubject of(AuditSubjectKind kind, UUID id) {
        return new AuditSubject(kind, Objects.requireNonNull(id, "id"));
    }

    public static AuditSubject without(AuditSubjectKind kind) {
        return new AuditSubject(kind, null);
    }
}
