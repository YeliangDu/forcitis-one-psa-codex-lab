package com.forcitis.one.organization.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.lang.reflect.InvocationTargetException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Lab #13: real import port and PostgreSQL outcomes; no HTTP or external source adapter. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class ImportAttendanceRecordsIT {
    static final TestDatabase postgres = TestPostgres.database(ImportAttendanceRecordsIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");
    static final String API = "com.forcitis.one.organization.leave.api.";
    static final Instant SUNDAY = Instant.parse("2026-11-08T02:03:04.123456Z");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @MockitoBean WecomGateway wecomGateway;
    @Autowired ApplicationContext beans;
    @Autowired JdbcClient jdbc;
    @Autowired IdGenerator ids;
    @Autowired OrgDirectory directory;

    @Test
    void importsAllThreeKindsAndOffCalendarInstantsForResolvableEmployees() throws Exception {
        Object port = port();
        String source = source();
        EmployeeId employed = employee("EMPLOYED");
        EmployeeId separated = employee("SEPARATED");
        assertThat(SUNDAY.atZone(ZoneId.of("Asia/Shanghai")).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        // Directory resolution needs a synthetic assignment; no calendar is configured for these people.
        execute(port, List.of(input(source, "punch", employed, "PUNCH", SUNDAY),
                input(source, "outfield", separated, "OUTFIELD", SUNDAY.plusSeconds(1)),
                input(source, "make-up", employed, "MAKE_UP", SUNDAY.plusSeconds(2))));
        assertThat(rows(source)).hasSize(3);
        assertRow(source, "punch", employed, "PUNCH", SUNDAY);
        assertRow(source, "outfield", separated, "OUTFIELD", SUNDAY.plusSeconds(1));
        assertRow(source, "make-up", employed, "MAKE_UP", SUNDAY.plusSeconds(2));
    }

    @Test
    void replayDoesNotReplaceExistingRowsAndBatchDuplicatesKeepFirstValidRecord() throws Exception {
        Object port = port();
        String source = source();
        EmployeeId first = employee("EMPLOYED");
        EmployeeId other = employee("SEPARATED");
        execute(port, List.of(input(source, "existing", first, "PUNCH", SUNDAY)));
        List<Stored> before = rows(source);
        execute(port, List.of(input(source, "existing", other, "MAKE_UP", SUNDAY.plusSeconds(600)),
                input(source, "new", first, "OUTFIELD", SUNDAY.plusSeconds(1)),
                input(source, "new", other, "MAKE_UP", SUNDAY.plusSeconds(2))));
        assertThat(rows(source)).hasSize(2).containsAll(before);
        assertRow(source, "new", first, "OUTFIELD", SUNDAY.plusSeconds(1));
        List<Stored> after = rows(source);
        execute(port, List.of(input(source, "existing", other, "OUTFIELD", SUNDAY.plusSeconds(900)),
                input(source, "new", other, "PUNCH", SUNDAY.plusSeconds(901))));
        assertThat(rows(source)).isEqualTo(after); // Includes primary key and both audit timestamps.
    }

    @Test
    void unknownEmployeeRollsBackWholeBatchAndPreservesEarlierRows() throws Exception {
        Object port = port();
        String source = source();
        EmployeeId known = employee("EMPLOYED");
        EmployeeId unknown = new EmployeeId(ids.next());
        assertThat(directory.findEmployee(unknown)).isEmpty();
        execute(port, List.of(input(source, "preserved", known, "PUNCH", SUNDAY)));
        List<Stored> before = rows(source);
        List<Object> batch = List.of(input(source, "before-unknown", known, "OUTFIELD", SUNDAY),
                input(source, "unknown", unknown, "PUNCH", SUNDAY),
                input(source, "after-unknown", known, "MAKE_UP", SUNDAY));
        assertThatThrownBy(() -> execute(port, batch)).isInstanceOf(DomainException.class);
        assertThat(rows(source)).isEqualTo(before);
    }

    @Test
    void employeeResolutionIsRequiredEvenForExistingAndInBatchDuplicateKeys() throws Exception {
        Object port = port();
        String source = source();
        EmployeeId known = employee("EMPLOYED");
        EmployeeId unknown = new EmployeeId(ids.next());
        assertThat(directory.findEmployee(unknown)).isEmpty();
        execute(port, List.of(input(source, "existing", known, "PUNCH", SUNDAY)));
        List<Stored> before = rows(source);
        for (String duplicate : List.of("existing", "within-batch")) {
            List<Object> batch = List.of(input(source, "within-batch", known, "OUTFIELD", SUNDAY),
                    input(source, duplicate, unknown, "MAKE_UP", SUNDAY.plusSeconds(1)));
            assertThatThrownBy(() -> execute(port, batch)).isInstanceOf(DomainException.class);
            assertThat(rows(source)).isEqualTo(before);
        }
    }

    private Object port() throws Exception {
        Class<?> type = api("ImportAttendanceRecords");
        assertThat(type.isInterface()).as("public import port").isTrue();
        assertThat(type.getMethod("execute", List.class).getReturnType()).isEqualTo(void.class);
        var candidates = beans.getBeansOfType(type);
        assertThat(candidates).as("Spring must expose one real ImportAttendanceRecords bean").hasSize(1);
        return candidates.values().iterator().next();
    }

    private Class<?> api(String name) {
        try {
            return Class.forName(API + name);
        } catch (ClassNotFoundException missing) {
            return fail("Missing S3-G0 public API: " + API + name, missing);
        }
    }

    private Object input(String source, String key, EmployeeId person, String kind, Instant at) throws Exception {
        Class<?> type = api("AttendanceRecordInput");
        Class<?> kinds = api("AttendanceRecordKind");
        assertThat(type.isRecord()).isTrue();
        assertThat(Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList())
                .containsExactly("sourceSystem", "externalRecordKey", "employeeId", "recordKind", "occurredAt");
        Object recordKind = kinds.getMethod("valueOf", String.class).invoke(null, kind);
        return type.getConstructor(String.class, String.class, EmployeeId.class, kinds, Instant.class)
                .newInstance(source, key, person, recordKind, at);
    }

    private void execute(Object port, List<Object> inputs) throws Exception {
        try {
            api("ImportAttendanceRecords").getMethod("execute", List.class).invoke(port, inputs);
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException failure) throw failure;
            if (wrapped.getCause() instanceof Error failure) throw failure;
            throw wrapped;
        }
    }

    private String source() {
        return "G0_" + ids.next().toString().replace("-", "").substring(0, 20).toUpperCase(java.util.Locale.ROOT);
    }

    private EmployeeId employee(String status) {
        EmployeeId person = new EmployeeId(ids.next());
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, employment_status)
                values (:id, :code, 'Synthetic G0 person', :status)
                """).param("id", person.value()).param("code", "G0-" + person.value()).param("status", status).update();
        UUID unit = ids.next();
        jdbc.sql("""
                insert into organization_workforce.org_units (id, code, name, status, sort_order)
                values (:id, :code, 'Synthetic G0 unit', 'ACTIVE', 1)
                """).param("id", unit).param("code", "G0-" + unit).update();
        jdbc.sql("""
                insert into organization_workforce.assignments (employee_id, org_unit_id)
                values (:employee, :unit)
                """).param("employee", person.value()).param("unit", unit).update();
        assertThat(directory.findEmployee(person)).as("real local directory resolves fixture, regardless of employment state").isPresent();
        return person;
    }

    private record Stored(UUID id, UUID employee, String kind, Instant at, String key, Instant created, Instant updated) {}

    private List<Stored> rows(String source) {
        return jdbc.sql("""
                select id, employee_id, record_kind, occurred_at, external_record_key, created_at, updated_at
                from organization_leave.attendance_records where source_system = :source order by external_record_key
                """).param("source", source).query((rs, number) -> new Stored(
                        rs.getObject("id", UUID.class), rs.getObject("employee_id", UUID.class),
                        rs.getString("record_kind"), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("external_record_key"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant())).list();
    }

    private void assertRow(String source, String key, EmployeeId employee, String kind, Instant at) {
        assertThat(rows(source)).filteredOn(row -> row.key().equals(key)).singleElement().satisfies(row -> {
            assertThat(row.employee()).isEqualTo(employee.value());
            assertThat(row.kind()).isEqualTo(kind);
            assertThat(row.at()).isEqualTo(at);
            assertThat(row.id()).isNotNull();
            assertThat(row.created()).isNotNull();
            assertThat(row.updated()).isNotNull();
        });
    }
}
