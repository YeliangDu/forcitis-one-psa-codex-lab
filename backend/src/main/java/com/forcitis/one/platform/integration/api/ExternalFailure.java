package com.forcitis.one.platform.integration.api;

/**
 * What a failed external database call may tell (ADR-0037 item 3.5): SQL Server messages can carry column values, so only class names, the
 * error number and the SQLState, never a message.
 */
public record ExternalFailure(String description) {

    private static final int MAXIMUM_CAUSE_DEPTH = 64;

    public static ExternalFailure of(Throwable failure) {
        Throwable innermost = failure;
        String database = "no SQL error";
        boolean databaseFound = false;
        int depth = 0;
        for (Throwable t = failure; t != null && depth < MAXIMUM_CAUSE_DEPTH; t = t.getCause(), depth++) {
            innermost = t;
            if (!databaseFound && answered(t, "getSQLState") instanceof String state && answered(t, "getErrorCode") instanceof Integer number) {
                database = "error " + number + ", SQLState " + state;
                databaseFound = true;
            }
        }
        return new ExternalFailure(failure.getClass().getSimpleName() + ", " + database + ", innermost " + innermost.getClass().getSimpleName());
    }

    /** By name, like platform.web: outside infrastructure nothing may depend on java.sql (ArchitectureRulesTest.jdbcOnlyInInfrastructure). */
    private static Object answered(Throwable failure, String accessor) {
        try {
            return failure.getClass().getMethod(accessor).invoke(failure);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    @Override
    public String toString() {
        return description;
    }
}
