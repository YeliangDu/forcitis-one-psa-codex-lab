package com.forcitis.one.platform.audit;

import com.forcitis.one.BusinessDay;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.ZoneOffset;

/** Creates the audit partition of a UTC month on a given connection, so tests of other modules that write audit entries do not touch the audit schema themselves. */
public final class AuditPartitionsTestSupport {
    private AuditPartitionsTestSupport() {
    }

    public static void ensureCurrentMonth(Connection connection) {
        ensureMonth(connection, BusinessDay.utcMonth());
    }

    /** For a database an earlier start may not have migrated yet: does nothing while the audit schema does not exist. */
    public static void ensureCurrentMonthIfMigrated(Connection connection) {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select to_regnamespace('platform_audit') is not null")) {
            rows.next();
            if (rows.getBoolean(1)) {
                ensureCurrentMonth(connection);
            }
        } catch (SQLException failed) {
            throw new IllegalStateException(failed);
        }
    }

    public static void ensureMonth(Connection connection, YearMonth month) {
        try (PreparedStatement statement = connection.prepareStatement("select platform_audit.create_audit_entry_partitions(?, ?)")) {
            Date first = Date.valueOf(month.atDay(1));
            statement.setDate(1, first);
            statement.setDate(2, first);
            statement.execute();
        } catch (SQLException failed) {
            throw new IllegalStateException("could not create the audit partition of " + month, failed);
        }
    }
}
