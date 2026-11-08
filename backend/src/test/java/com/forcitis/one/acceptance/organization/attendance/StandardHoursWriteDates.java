package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.MutableClock;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.test.web.servlet.MvcResult;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Test-only HTTP transaction/date fixture; never changes the database/host clock. */
final class StandardHoursWriteDates {
    private static final String SHANGHAI_DATE = "select (transaction_timestamp() at time zone 'Asia/Shanghai')::date::text";

    private StandardHoursWriteDates() {
    }

    @FunctionalInterface
    interface DatabaseWork<T> {
        T run(LocalDate transactionDay, TransactionStatus status) throws Exception;
    }

    @FunctionalInterface
    interface HttpWork<T> {
        T run(LocalDate requestDay) throws Exception;
    }

    static LocalDate databaseDay(DataSource source) {
        return LocalDate.parse(Objects.requireNonNull(new JdbcTemplate(source).queryForObject(SHANGHAI_DATE, String.class)));
    }

    static <T> T inDatabaseTransaction(DataSource source, PlatformTransactionManager manager, DatabaseWork<T> work) {
        return Objects.requireNonNull(new TransactionTemplate(manager).execute(status -> {
            LocalDate transactionDay = databaseDay(source);
            try {
                return work.run(transactionDay, status);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("Standard-hours HTTP fixture callback failed", failure);
            }
        }));
    }

    static <T> T atRequest(DataSource source, PlatformTransactionManager manager, MutableClock clock,
            LocalDate capturedJvmDay, HttpWork<T> work) {
        return inDatabaseTransaction(source, manager, (transactionDay, status) -> {
            // RED: the old fixture trusts the day captured before midnight.
            LocalDate requestDay = capturedJvmDay;
            clock.set(requestDay.atTime(10, 0).atZone(BusinessDay.ZONE).toInstant());
            return work.run(requestDay);
        });
    }

    /** A positive DEFAULT event fixture may be future-effective; keep a one-day buffer across the sample/HTTP gap. */
    static LocalDate safeDefaultDate(TestDatabase database) throws SQLException {
        try (Connection connection = DriverManager.getConnection(database.url(), database.username(), database.password());
                var query = connection.createStatement();
                var result = query.executeQuery(SHANGHAI_DATE)) {
            result.next();
            return LocalDate.parse(result.getString(1)).plusDays(1);
        }
    }
    static boolean isDateRefusal(SQLException failure) {
        return "23514".equals(failure.getSQLState()) && failure.getMessage() != null
                && failure.getMessage().contains("is earlier than today: a standard-hours version does not act on the past");
    }

    static void assertDatabaseDateRefusal(MvcResult result) {
        List<SQLException> failures = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> remaining = new ArrayDeque<>();
        if (result.getResolvedException() != null) remaining.add(result.getResolvedException());
        while (!remaining.isEmpty()) {
            Throwable failure = remaining.remove();
            if (!seen.add(failure)) continue;
            if (failure instanceof SQLException sql) {
                failures.add(sql);
                if (sql.getNextException() != null) remaining.add(sql.getNextException());
            }
            if (failure.getCause() != null) remaining.add(failure.getCause());
        }
        assertThat(failures).as("HTTP resolved exception retains actual JDBC date refusal")
                .anySatisfy(failure -> {
                    assertThat(failure.getSQLState()).isEqualTo("23514");
                    assertThat(failure.getMessage()).contains("effective_from_date")
                            .contains("is earlier than today: a standard-hours version does not act on the past")
                            .contains("guard_standard_hours_version_insert");
                });
    }

}
