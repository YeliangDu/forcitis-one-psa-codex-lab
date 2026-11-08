package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.organization.attendance.events.StandardHoursVersionPublished;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Own database: the failure witness cannot see a DEFAULT committed by another case. */
@RecordApplicationEvents
class StandardHoursExpiredDefaultIT extends AcceptanceSupport {
    static final TestDatabase postgres = TestPostgres.database(StandardHoursExpiredDefaultIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @TestBean(name = "acceptanceClock", methodName = "staleStartupClock")
    MutableClock startupClock;

    static MutableClock staleStartupClock() throws SQLException {
        try (var connection = DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password());
                var statement = connection.createStatement();
                var rows = statement.executeQuery("select (transaction_timestamp() at time zone 'Asia/Shanghai')::date::text")) {
            rows.next();
            LocalDate captured = LocalDate.parse(rows.getString(1)).minusDays(1);
            return new MutableClock(captured.atTime(10, 0).atZone(BusinessDay.ZONE).toInstant(), ZoneOffset.UTC);
        }
    }

    @Autowired
    DataSource source;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired
    ApplicationEvents events;

    @Test
    @DisplayName("#47 旧应用日期允许的默认发布被真实库事务日拒绝；精确23514日期原因且无发布事件")
    void expiredDefaultReachesActualDatabaseDateGuard() {
        StandardHoursWriteDates.inDatabaseTransaction(source, transactions, (day, status) -> {
            status.setRollbackOnly();
            LocalDate expired = day.minusDays(1);
            setToday(expired);
            String body = "{\"effectiveFromDate\":\"" + expired + "\",\"workdayMinutes\":465,\"adjustedWorkdayMinutes\":405,"
                    + "\"basisRef\":\"Synthetic expired default witness\"}";
            var result = mvc.perform(post("/api/v1/organization/standard-hours-versions")
                    .with(person(31, "organization.attendance:standard-hours:publish")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
            assertThat(result.getResponse().getStatus()).as("actual stale app/DB date split returns HTTP 500").isEqualTo(500);
            StandardHoursWriteDates.assertDatabaseDateRefusal(result);
            assertThat(events.stream(StandardHoursVersionPublished.class)).as("rejected DEFAULT adds no event").isEmpty();
            return expired;
        });
    }
}
