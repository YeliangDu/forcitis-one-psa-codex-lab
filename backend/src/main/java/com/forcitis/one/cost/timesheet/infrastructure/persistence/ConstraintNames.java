package com.forcitis.one.cost.timesheet.infrastructure.persistence;

final class ConstraintNames {

    private static final String UNIQUE_VIOLATION = "23505";

    private ConstraintNames() {
    }

    /** Read by name because only the connection factory may depend on the database driver. */
    static String of(Throwable failure) {
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

    private static Object invoke(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }
}
