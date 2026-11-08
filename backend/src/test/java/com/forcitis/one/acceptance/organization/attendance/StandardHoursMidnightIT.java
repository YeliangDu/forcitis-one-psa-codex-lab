package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.attendance.api.StandardHoursVersionId;
import com.forcitis.one.organization.attendance.events.StandardHoursVersionPublished;
import com.jayway.jsonpath.JsonPath;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Real HTTP + unchanged PG guard; deterministic stale app date, not a fake DB date. */
@RecordApplicationEvents
class StandardHoursMidnightIT extends AcceptanceSupport {
    static final TestDatabase postgres = TestPostgres.database(StandardHoursMidnightIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String VERSIONS = "/api/v1/organization/standard-hours-versions";
    private static final int CONFIRMER = 31;
    private static final RequestPostProcessor PUBLISHER = person(CONFIRMER, "organization.attendance:standard-hours:publish");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Model a JVM that booted on yesterday, including its normal audit partition startup. */
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
    PlatformTransactionManager transactions;
    @Autowired
    ApplicationEvents events;

    @Test
    @DisplayName("#47 请求前重新校准旧 JVM 日期后，默认范围真实提交且保留完整单事件断言")
    void capturedJvmDayIsRecalibratedBeforeDefaultPublicationAndCommittedEvent() {
        LocalDate capturedBeforeMidnight = StandardHoursWriteDates.databaseDay(source).minusDays(1);
        Published observed = StandardHoursWriteDates.atRequest(source, transactions, clock, capturedBeforeMidnight, requestDay -> {
            LocalDate actualTransactionDay = StandardHoursWriteDates.databaseDay(source);
            MvcResult result = publication(null, requestDay, 465, 405);
            if (result.getResponse().getStatus() == 500) {
                StandardHoursWriteDates.assertDatabaseDateRefusal(result); // Another 500/23514 is NOT the intended red.
            }
            assertThat(result.getResponse().getStatus())
                    .as("MIDNIGHT_WRITE_FIXTURE_STALE_DAY: calibrated DEFAULT publication returns 200")
                    .isEqualTo(200);
            assertThat(requestDay).as("calibrated DEFAULT uses exact transaction day, not a future-date bypass").isEqualTo(actualTransactionDay);
            assertThat((Integer) JsonPath.read(result.getResponse().getContentAsString(), "$.versionNumber")).isEqualTo(1);
            return new Published(JsonPath.read(result.getResponse().getContentAsString(), "$.standardHoursVersionId"), requestDay);
        }); // A successful branch commits the real transaction before observations below.
        assertPublishedEvent(observed.id(), observed.day(), null, 465, 405, 1);
        assertThat(events.stream(StandardHoursVersionPublished.class).count()).as("exactly one DEFAULT event after actual commit").isEqualTo(1L);
        try (var connection = schemaConnection("organization_attendance");
                var query = connection.prepareStatement("select count(*) from standard_hours_versions where id = ? and scope_kind = 'DEFAULT'")) {
            query.setObject(1, UUID.fromString(observed.id()));
            try (var rows = query.executeQuery()) {
                rows.next();
                assertThat(rows.getLong(1)).as("DEFAULT row is visible from an independent connection after COMMIT").isEqualTo(1L);
            }
        } catch (SQLException failure) {
            throw new AssertionError("committed DEFAULT readback failed", failure);
        }
    }

    @Test
    @DisplayName("#47 同日仍可真实提交；重复日409、历史首版422、零分钟400均不新增发布事件")
    void sameDayAndHistoricalBoundariesKeepTheirDistinctHttpRefusals() throws Exception {
        setToday(StandardHoursWriteDates.databaseDay(source));
        UUID scope = newScope();
        Published observed = StandardHoursWriteDates.inDatabaseTransaction(source, transactions, (day, status) -> {
            setToday(day);
            MvcResult result = publication(scope, day, 450, 390);
            assertThat(result.getResponse().getStatus()).as("same transaction Shanghai date is inclusive").isEqualTo(200);
            return new Published(JsonPath.read(result.getResponse().getContentAsString(), "$.standardHoursVersionId"), day);
        });
        assertPublishedEvent(observed.id(), observed.day(), scope, 450, 390, 1);
        StandardHoursWriteDates.inDatabaseTransaction(source, transactions, (day, status) -> {
            status.setRollbackOnly();setToday(day);
            MvcResult duplicate = publication(scope, observed.day(), 450, 390);
            assertThat(duplicate.getResponse().getStatus()).as("same scope duplicate date refuses as overlap").isEqualTo(409);
            assertThat((String) JsonPath.read(duplicate.getResponse().getContentAsString(), "$.errorCode"))
                    .isEqualTo("ORGANIZATION_ATTENDANCE_STANDARD_HOURS_OVERLAP");
            return day;
        });
        UUID empty = newScope();
        StandardHoursWriteDates.inDatabaseTransaction(source, transactions, (day, status) -> {
            status.setRollbackOnly();setToday(day);
            MvcResult historical = publication(empty, day.minusDays(1), 480, 420);
            assertThat(historical.getResponse().getStatus()).as("aligned application refuses past first version").isEqualTo(422);
            assertThat((String) JsonPath.read(historical.getResponse().getContentAsString(), "$.errorCode"))
                    .isEqualTo("ORGANIZATION_ATTENDANCE_STANDARD_HOURS_BACKDATED");
            return day;
        });
        StandardHoursWriteDates.inDatabaseTransaction(source, transactions, (day, status) -> {
            status.setRollbackOnly();setToday(day);
            assertThat(publication(empty, day, 0, 420).getResponse().getStatus()).as("invalid minutes remain a separate 400").isEqualTo(400);
            return day;
        });
        assertThat(events.stream(StandardHoursVersionPublished.class).count()).as("all refused requests add no standard-hours event").isEqualTo(1L);
    }

    @Test
    @DisplayName("#47 同为23514的版本号拒绝不算日期拒绝")
    void another23514IsNotMistakenForDatabaseDateRefusal() throws Exception {
        try (var connection = schemaConnection("organization_attendance")) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("""
                    insert into standard_hours_versions
                    (id, scope_kind, legal_entity_id, version_number, status, effective_from_date,
                     workday_minutes_count, adjusted_workday_minutes_count, basis_ref_text,
                     confirmed_by, confirmed_at, decision_audit_entry_id, created_at, updated_at)
                    values (?, 'LEGAL_ENTITY', ?, 2, 'IN_EFFECT',
                     (transaction_timestamp() at time zone 'Asia/Shanghai')::date,
                     480, 420, 'Synthetic wrong sequence control', ?, transaction_timestamp(), ?,
                     transaction_timestamp(), transaction_timestamp())
                    """)) {
                statement.setObject(1, UUID.randomUUID());statement.setObject(2, UUID.randomUUID());
                statement.setObject(3, UUID.randomUUID());statement.setObject(4, UUID.randomUUID());
                SQLException refused = catchThrowableOfType(SQLException.class, statement::executeUpdate);
                assertThat(refused).as("real PG numbering trigger refuses an otherwise current-date first row").isNotNull();
                assertThat(refused.getSQLState()).isEqualTo("23514");
                assertThat(refused.getMessage()).contains("version_number must be 1 for this scope, got 2");
                assertThat(StandardHoursWriteDates.isDateRefusal(refused)).as("23514 alone is not date evidence").isFalse();
            } finally {
                connection.rollback();
            }
        }
    }

    private record Published(String id, LocalDate day) { }

    private MvcResult publication(UUID scope, LocalDate day, int workday, int adjusted) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (scope != null) body.put("legalEntityId", scope.toString());
        body.put("effectiveFromDate", day.toString());body.put("workdayMinutes", workday);
        body.put("adjustedWorkdayMinutes", adjusted);body.put("basisRef", "Synthetic midnight acceptance");
        return mvc.perform(post(VERSIONS).with(PUBLISHER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn();
    }

    private UUID newScope() throws Exception {
        StringBuilder code = new StringBuilder("MH");
        for (int i = 0; i < 8; i++) code.append((char) ('A' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
        return createLegalEntity(code.toString(), "Synthetic midnight entity");
    }

    private void assertPublishedEvent(String id, LocalDate effectiveDate, UUID scope, int workdayMinutes, int adjustedWorkdayMinutes,
            int versionNumber) {
        StandardHoursVersionId versionId = new StandardHoursVersionId(UUID.fromString(id));
        assertThat(events.stream(StandardHoursVersionPublished.class)
                .filter(event -> versionId.equals(event.standardHoursVersionId())))
                .as("version " + id + " emits exactly one StandardHoursVersionPublished").singleElement().satisfies(event -> {
                    assertThat(event.standardHoursVersionId()).as("event identifies the published version").isEqualTo(versionId);
                    assertThat(event.standardHoursVersionId().value()).as("event version UUID equals the HTTP response identifier")
                            .isEqualTo(UUID.fromString(id));
                    assertThat(event.effectiveFromDate()).as("event carries the published effective start date").isEqualTo(effectiveDate);
                    EventEnvelope envelope = event.envelope();
                    assertThat(envelope).as("publication event has an envelope").isNotNull();
                    assertThat(envelope.aggregateId()).as("envelope aggregate ID is the published version UUID").isEqualTo(versionId.value());
                    assertThat(envelope.aggregateType()).as("envelope aggregate type is non-blank text").isNotBlank();
                    assertThat(envelope.actorType()).as("publication actor is a USER").isEqualTo(ActorType.USER);
                    assertThat(envelope.actorId()).as("publication actor is the signed-in publisher UUID")
                            .isEqualTo(UUID.fromString(principalName(CONFIRMER)));
                    assertThat(envelope.correlationId()).as("publication has a correlation ID").isNotNull();
                    assertThat(envelope.businessDate()).as("business date is the published effective start date").isEqualTo(effectiveDate);
                    assertThat(component(event, "scopeKind")).as("payload scope kind").hasToString(scope == null ? "DEFAULT" : "LEGAL_ENTITY");
                    assertThat(component(event, "legalEntityId")).as("payload legal entity id (null for the DEFAULT scope)")
                            .isEqualTo(scope == null ? null : new LegalEntityId(scope));
                    assertThat(number(component(event, "workdayMinutes"))).as("payload workday minutes").isEqualTo(workdayMinutes);
                    assertThat(number(component(event, "adjustedWorkdayMinutes"))).as("payload adjusted workday minutes").isEqualTo(adjustedWorkdayMinutes);
                    assertThat(number(component(event, "versionNumber"))).as("payload per-scope version number").isEqualTo(versionNumber);
                    if (scope == null) {
                        assertThat(envelope.legalEntityId()).as("DEFAULT event has no legal entity").isNull();
                    } else {
                        assertThat(envelope.legalEntityId()).as("entity event carries the body's legal entity")
                                .isEqualTo(new LegalEntityId(scope));
                    }
                });
    }

    /** Reads a record component by name so a missing payload field is a red assertion, not a compile error for the whole module. */
    private static Object component(StandardHoursVersionPublished event, String name) {
        for (java.lang.reflect.RecordComponent candidate : StandardHoursVersionPublished.class.getRecordComponents()) {
            if (candidate.getName().equals(name)) {
                try {
                    return candidate.getAccessor().invoke(event);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError("payload component " + name + " is not readable", failure);
                }
            }
        }
        throw new AssertionError("StandardHoursVersionPublished has no payload component " + name);
    }

    /** A payload number is an int or a single-int value object with a value() accessor (the event catalogue forbids bare ints). */
    private static Object number(Object payload) {
        if (payload instanceof Integer) {
            return payload;
        }
        try {
            return payload.getClass().getMethod("value").invoke(payload);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("payload number " + payload + " has no value() accessor", failure);
        }
    }

}
