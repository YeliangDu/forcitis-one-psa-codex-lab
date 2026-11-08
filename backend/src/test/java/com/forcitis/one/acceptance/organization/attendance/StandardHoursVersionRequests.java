package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.MutableClock;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** BC-local fixture: each HTTP write gets its own real PostgreSQL transaction and immutable input. */
final class StandardHoursVersionRequests {
    private StandardHoursVersionRequests() { }

    @FunctionalInterface
    interface Work<T> {
        T run(LocalDate requestDay) throws Exception;
    }

    record Published(String id, LocalDate requestDay, Map<String, Object> postedBody) {
        Published {
            postedBody = snapshot(postedBody);
        }

        LocalDate effectiveDate() {
            return LocalDate.parse((String) postedBody.get("effectiveFromDate"));
        }
    }

    static <T> T request(DataSource source, PlatformTransactionManager manager, MutableClock clock,
            boolean expectedRejection, Work<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return Objects.requireNonNull(transaction.execute(status -> {
            if (expectedRejection) {
                // A caught domain refusal can mark the participating HTTP transaction rollback-only.
                // Explicit local rollback prevents an UnexpectedRollbackException replacing its HTTP response.
                status.setRollbackOnly();
            }
            LocalDate day = LocalDate.parse(Objects.requireNonNull(new JdbcTemplate(source).queryForObject(
                    "select (transaction_timestamp() at time zone 'Asia/Shanghai')::date::text", String.class)));
            clock.set(day.atTime(10, 0).atZone(BusinessDay.ZONE).toInstant());
            try {
                return work.run(day);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("version31 request fixture failed", failure);
            }
        }));
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put((String) key, freeze(item)));
            return Map.copyOf(copy);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(StandardHoursVersionRequests::freeze).toList();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshot(Map<String, Object> body) {
        return (Map<String, Object>) freeze(body);
    }

    static void committed(TestDatabase database, Published published, int version) throws Exception {
        // Independent physical connection: the enclosing request must already have committed.
        try (var db = DriverManager.getConnection(database.url(), database.username(), database.password())) {
            db.setReadOnly(true);
            try (var query = db.prepareStatement("""
                    select effective_from_date, version_number, workday_minutes, adjusted_workday_minutes
                    from organization_attendance.standard_hours_versions where id = ?
                    """)) {
                query.setObject(1, java.util.UUID.fromString(published.id()));
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).as("version31 publication is independently visible after COMMIT").isTrue();
                    assertThat(rows.getObject(1, LocalDate.class)).isEqualTo(published.effectiveDate());
                    assertThat(rows.getInt(2)).isEqualTo(version);
                    assertThat(rows.getInt(3)).isEqualTo((Integer) published.postedBody().get("workdayMinutes"));
                    assertThat(rows.getInt(4)).isEqualTo((Integer) published.postedBody().get("adjustedWorkdayMinutes"));
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }
}
