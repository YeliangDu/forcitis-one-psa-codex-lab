package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.BusinessDay;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.postgresql.util.PSQLException;
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

    static void assertDatabaseDateRefusal(MvcResult result, TestDatabase database) {
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
        byte[] request = Objects.requireNonNull(result.getRequest().getContentAsByteArray());
        String submitted = JsonPath.read(new String(request, StandardCharsets.UTF_8), "$.effectiveFromDate");
        LocalDate.parse(submitted); // This evidence must identify the actual submitted ISO date.
        String expectedPrimary = "effective_from_date " + submitted
                + " is earlier than today: a standard-hours version does not act on the past";
        assertInstalledDateGuard(database);
        assertThat(failures).as("HTTP resolved exception retains actual JDBC date refusal")
                .anySatisfy(failure -> {
                    assertThat(failure.getSQLState()).isEqualTo("23514");
                    String primary = failure.getMessage();
                    String where = null;
                    if (failure instanceof PSQLException pg && pg.getServerErrorMessage() != null) {
                        primary = pg.getServerErrorMessage().getMessage();
                        where = pg.getServerErrorMessage().getWhere();
                    } else if (primary != null) {
                        primary = primary.split("\\R", 2)[0].replaceFirst("^ERROR: ", "");
                    }
                    assertThat(primary).as("native primary message identifies the exact submitted date refusal")
                            .isEqualTo(expectedPrimary);
                    if (where != null && !where.isBlank()) {
                        assertThat(where).as("native PostgreSQL Where, when actually supplied")
                                .contains("guard_standard_hours_version_insert");
                    }
                    // Missing native Where is not fabricated: the independent
                    // catalog proof above is still mandatory for every request.
                });
    }


    private record InstalledDateGuard(String schema, String name, String enabled, int type, String definition) { }

    private static void assertInstalledDateGuard(TestDatabase database) {
        List<InstalledDateGuard> matching = new ArrayList<>();
        String message = "is earlier than today: a standard-hours version does not act on the past";
        try (Connection connection = DriverManager.getConnection(database.url(), database.username(), database.password())) {
            connection.setReadOnly(true); // Independent metadata read; the HTTP transaction may already be aborted.
            try (var query = connection.prepareStatement("""
                    select pn.nspname, p.proname, t.tgenabled::text, t.tgtype,
                           pg_catalog.pg_get_functiondef(p.oid)
                    from pg_catalog.pg_trigger t
                    join pg_catalog.pg_class c on c.oid = t.tgrelid
                    join pg_catalog.pg_namespace cn on cn.oid = c.relnamespace
                    join pg_catalog.pg_proc p on p.oid = t.tgfoid
                    join pg_catalog.pg_namespace pn on pn.oid = p.pronamespace
                    where cn.nspname = 'organization_attendance'
                      and c.relname = 'standard_hours_versions'
                      and not t.tgisinternal and t.tgenabled in ('O', 'A')
                      and (t.tgtype::integer & 2) = 2 and (t.tgtype::integer & 4) = 4
                    """); var rows = query.executeQuery()) {
                while (rows.next()) {
                    String definition = rows.getString(5);
                    if (definition.contains(message)) {
                        matching.add(new InstalledDateGuard(rows.getString(1), rows.getString(2),
                                rows.getString(3), rows.getInt(4), definition));
                    }
                }
            }
        } catch (SQLException failure) {
            throw new AssertionError("independent installed date-guard metadata read failed", failure);
        }
        assertThat(matching).as("unique enabled BEFORE INSERT date-message emitter on the actual module table")
                .singleElement().satisfies(guard -> {
                    assertThat(guard.schema()).isEqualTo("organization_attendance");
                    assertThat(guard.name()).isEqualTo("guard_standard_hours_version_insert");
                    assertThat(guard.enabled()).isIn("O", "A");
                    assertThat(guard.type() & 1).as("the enabled trigger is FOR EACH ROW").isEqualTo(1);
                    String definition = guard.definition().replaceAll("\\s+", " ");
                    assertThat(definition).contains("IF NEW.effective_from_date < today THEN")
                            .contains("today date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date")
                            .contains("RAISE EXCEPTION 'effective_from_date % " + message);
                });
    }
}
