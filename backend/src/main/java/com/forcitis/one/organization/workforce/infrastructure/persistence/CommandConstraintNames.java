package com.forcitis.one.organization.workforce.infrastructure.persistence;

import org.jspecify.annotations.Nullable;

final class CommandConstraintNames {

    private static final String UNIQUE_VIOLATION = "23505";

    private CommandConstraintNames() {
    }

    /** Read by name because only the connection factory may depend on the database driver. */
    static @Nullable String of(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (UNIQUE_VIOLATION.equals(invoke(cause, "getSQLState"))) {
                Object detail = invoke(cause, "getServerErrorMessage");
                if (detail != null && invoke(detail, "getConstraint") instanceof String name) {
                    return name;
                }
            }
        }
        return null;
    }

    private static @Nullable Object invoke(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }
}
