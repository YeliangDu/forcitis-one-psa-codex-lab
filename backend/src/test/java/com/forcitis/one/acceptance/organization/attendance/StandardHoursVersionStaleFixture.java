package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;

/** Test-only stale startup input/causal evidence; not a production clock or SQL seam. */
final class StandardHoursVersionStaleFixture {
    private static final Logger LOG = LoggerFactory.getLogger(StandardHoursVersionStaleFixture.class);
    private static final String VOID_DATE_MESSAGE = "a version in force or in the past cannot be voided: correct it with a new version from today";

    private StandardHoursVersionStaleFixture() {
    }

    static LocalDate capturedDay(TestDatabase database) {
        try (var connection = DriverManager.getConnection(database.url(), database.username(), database.password());
                var query = connection.createStatement();
                var rows = query.executeQuery("select (transaction_timestamp() at time zone 'Asia/Shanghai')::date::text")) {
            rows.next();
            return LocalDate.parse(rows.getString(1)).minusDays(1);
        } catch (SQLException failure) {
            throw new IllegalStateException("version31 date fixture cannot read its actual test database", failure);
        }
    }

    static void confirmedInsertCause() {
        LOG.info("VERSION31_INSERT_DATE_CAUSE_CONFIRMED sqlState=23514");
    }

    static void assertVoidDateRefusal(MvcResult result, TestDatabase database) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> queue = new ArrayDeque<>();
        if (result.getResolvedException() != null) queue.add(result.getResolvedException());
        boolean matched = false;
        while (!queue.isEmpty()) {
            Throwable cause = queue.remove();
            if (!seen.add(cause)) continue;
            if (cause instanceof SQLException sql) {
                String primary = sql.getMessage();
                String where = null;
                if (sql instanceof PSQLException pg && pg.getServerErrorMessage() != null) {
                    primary = pg.getServerErrorMessage().getMessage();
                    where = pg.getServerErrorMessage().getWhere();
                } else if (primary != null) {
                    primary = primary.split("\\R", 2)[0].replaceFirst("^ERROR: ", "");
                }
                if ("23514".equals(sql.getSQLState()) && VOID_DATE_MESSAGE.equals(primary)) {
                    if (where != null && !where.isBlank()) {
                        assertThat(where).as("actual PG Where for date-based void refusal")
                                .contains("guard_standard_hours_version_change");
                    }
                    matched = true;
                }
                if (sql.getNextException() != null) queue.add(sql.getNextException());
            }
            if (cause.getCause() != null) queue.add(cause.getCause());
        }
        assertThat(matched).as("actual HTTP500 is specifically PG23514 nonfuture-void rejection").isTrue();
        try (var connection = DriverManager.getConnection(database.url(), database.username(), database.password())) {
            connection.setReadOnly(true);
            try (var query = connection.prepareStatement("""
                    select pn.nspname, p.proname, t.tgenabled::text, t.tgtype,
                           pg_catalog.pg_get_functiondef(p.oid)
                    from pg_catalog.pg_trigger t
                    join pg_catalog.pg_class c on c.oid = t.tgrelid
                    join pg_catalog.pg_namespace cn on cn.oid = c.relnamespace
                    join pg_catalog.pg_proc p on p.oid = t.tgfoid
                    join pg_catalog.pg_namespace pn on pn.oid = p.pronamespace
                    where cn.nspname = 'organization_attendance' and c.relname = 'standard_hours_versions'
                      and not t.tgisinternal and t.tgenabled in ('O','A')
                      and (t.tgtype::integer & 2) = 2 and (t.tgtype::integer & 16) = 16
                    """); var rows = query.executeQuery()) {
                int matches = 0;
                while (rows.next()) {
                    String definition = rows.getString(5);
                    if (!definition.contains(VOID_DATE_MESSAGE)) continue;
                    matches++;
                    assertThat(rows.getString(1)).isEqualTo("organization_attendance");
                    assertThat(rows.getString(2)).isEqualTo("guard_standard_hours_version_change");
                    assertThat(rows.getInt(4) & 1).isEqualTo(1);
                    String normalized = definition.replaceAll("\\s+", " ");
                    assertThat(normalized).contains("IF OLD.status = 'IN_EFFECT' AND NEW.status = 'VOIDED'")
                            .contains("OLD.effective_from_date <= today")
                            .contains("today date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date")
                            .contains("RAISE EXCEPTION '" + VOID_DATE_MESSAGE);
                }
                assertThat(matches).as("unique active actual nonfuture void message emitter").isEqualTo(1);
            }
        } catch (SQLException failure) {
            throw new AssertionError("version31 actual void-date guard metadata is unavailable", failure);
        }
        LOG.info("VERSION31_VOID_DATE_CAUSE_CONFIRMED sqlState=23514");
    }
}
