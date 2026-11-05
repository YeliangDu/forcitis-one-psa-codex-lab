package com.forcitis.one.acceptance.organization.workforce.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommands;
import com.forcitis.one.organization.workforce.application.PersonnelCommandDueJob;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ground of the workforce golden-sample scenario (#1410): a small world (two org units, one legal entity, the personnel specialist as the
 * caller), the business clock moved to the pack's days, the real application entry (PersonnelCommands, the due job) and reads of the
 * workforce schema. The scenario IT holds every assertion; this class only enters and reads.
 */
abstract class GoldenSampleSupport extends AccountsAcceptanceSupport {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private PersonnelCommands personnelCommands;
    @Autowired
    private PersonnelCommandDueJob dueJob;
    @Autowired
    protected OrgDirectory directory;

    protected UUID nanjingUnit;
    protected UUID shanghaiUnit;
    private SecurityContext acting;

    @BeforeEach
    void buildTheWorld() throws Exception {
        nanjingUnit = newOrgUnitId();
        orgUnit(nanjingUnit, null, null);
        shanghaiUnit = newOrgUnitId();
        orgUnit(shanghaiUnit, null, null);
        UUID admin = newEmployeeId();
        employee(admin, nanjingUnit, null);
        grantByOperations(admin, "org_data_admin");
        SignedIn administrator = signIn(admin);
        createLegalEntity(administrator, newLegalEntityCode("GA"), "Synthetic employer A");
        UUID businessAdminEmployee = newEmployeeId();
        employee(businessAdminEmployee, nanjingUnit, null);
        grantByOperations(businessAdminEmployee, "business_admin");
        confirmSeededNumberingRule("EMPLOYEE", signIn(businessAdminEmployee));
        UUID specialist = newEmployeeId();
        employee(specialist, nanjingUnit, null);
        grantByOperations(specialist, "personnel_specialist");
        actIn(signIn(specialist));
        auditPartitionsForThePackDays();
    }

    /** The audit log is partitioned by month; the scenario writes audit entries on the pack's days (2026-01 to 2028-03), so those months need partitions. */
    private void auditPartitionsForThePackDays() throws SQLException {
        try (Connection audit = schemaConnection("platform_audit")) {
            query(audit, "select create_audit_entry_partitions(?, ?)", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 1));
            query(audit, "select create_audit_entry_partitions(?, ?)", LocalDate.of(2028, 1, 1), LocalDate.of(2028, 3, 1));
        }
    }

    // ---- the business day ----

    /** The business clock is 10:00 Shanghai on that day, the way a person at work would enter things. */
    protected void onDay(LocalDate day) {
        clock.set(day.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant());
    }

    protected void runDueJob() {
        SecurityContextHolder.clearContext();
        dueJob.run();
    }

    // ---- entering ----

    protected UUID newPosition() throws Exception {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "insert into positions (id, code, name, status) values (?, ?, 'Synthetic position', 'ACTIVE')", id, "GS" + id.toString().substring(24));
        }
        return id;
    }

    protected PersonnelCommandId hire(LocalDate effective, UUID unit, UUID position, EmploymentPhaseKind phase) {
        return enter(new EnterPersonnelCommand(null, PersonnelCommandKind.HIRE, effective, null, new com.forcitis.one.organization.workforce.api.OrgUnitId(unit),
                new com.forcitis.one.organization.workforce.api.PositionId(position), null, true, phase, "Synthetic Golden Person F-P01",
                null, "f-p01@golden.example", null));
    }

    protected PersonnelCommandId enter(UUID employee, PersonnelCommandKind kind, LocalDate effective) {
        return enter(new EnterPersonnelCommand(employeeIdOf(employee), kind, effective, null, null, null, null, null, null, null, null, null, null));
    }

    protected PersonnelCommandId transfer(UUID employee, LocalDate effective, UUID unit, UUID position) {
        return enter(new EnterPersonnelCommand(employeeIdOf(employee), PersonnelCommandKind.TRANSFER, effective, null,
                new com.forcitis.one.organization.workforce.api.OrgUnitId(unit), new com.forcitis.one.organization.workforce.api.PositionId(position),
                null, null, null, null, null, null, null));
    }

    protected PersonnelCommandId separate(UUID employee, LocalDate lastWorkingDay) {
        return enter(new EnterPersonnelCommand(employeeIdOf(employee), PersonnelCommandKind.SEPARATE, null, lastWorkingDay, null, null, null, null, null,
                null, null, null, null));
    }

    protected PersonnelCommandId reinstate(UUID employee, LocalDate effective, UUID unit, UUID position, EmploymentPhaseKind phase) {
        return enter(new EnterPersonnelCommand(employeeIdOf(employee), PersonnelCommandKind.REINSTATE, effective, null,
                new com.forcitis.one.organization.workforce.api.OrgUnitId(unit), new com.forcitis.one.organization.workforce.api.PositionId(position),
                null, false, phase, null, null, null, null));
    }

    private PersonnelCommandId enter(EnterPersonnelCommand command) {
        restoreActing();
        return personnelCommands.enter(command);
    }

    // ---- reading ----

    protected UUID employeeOf(PersonnelCommandId command) throws SQLException {
        return fromStored(rows("select employee_id from personnel_commands where personnel_command_id = ?", command.value()).get(0).get("employee_id"));
    }

    protected Optional<EmploymentStatus> employmentOn(UUID employee, LocalDate day) {
        return directory.employmentOn(employeeIdOf(employee), day);
    }

    protected long employeeCount() throws SQLException {
        return (long) rows("select count(*) as n from employees where display_name = 'Synthetic Golden Person F-P01'").get(0).get("n");
    }

    protected String commandStatus(PersonnelCommandId command) throws SQLException {
        return (String) rows("select status from personnel_commands where personnel_command_id = ?", command.value()).get(0).get("status");
    }

    /** The version of the table in force for the employee on the day (current rows only), empty when none. */
    protected Optional<Map<String, Object>> inForceOn(String table, UUID employee, LocalDate day) throws SQLException {
        return rows("select * from " + table + " where employee_id = ? and superseded_at is null and effective_from_date <= ? "
                + "and (effective_to_date is null or effective_to_date > ?) order by effective_from_date desc", stored(employee), day, day).stream().findFirst();
    }

    protected List<Map<String, Object>> currentVersions(String table, UUID employee) throws SQLException {
        return rows("select * from " + table + " where employee_id = ? and superseded_at is null order by effective_from_date", stored(employee));
    }

    /** Distinct published events of the type that mention the command. */
    protected int eventsOf(String eventType, PersonnelCommandId command) throws SQLException {
        Set<String> events = new HashSet<>();
        Pattern eventId = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"");
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                for (Map<String, Object> row : query(registry, "select serialized_event from " + table + " where event_type like ? and serialized_event like ?",
                        "%." + eventType, "%" + command.value() + "%")) {
                    String json = (String) row.get("serialized_event");
                    Matcher found = eventId.matcher(json);
                    events.add(found.find() ? found.group(1) : json);
                }
            }
        }
        return events.size();
    }

    protected List<Map<String, Object>> rows(String sql, Object... args) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            return query(workforce, sql, args);
        }
    }

    private static List<Map<String, Object>> query(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData meta = result.getMetaData();
                List<Map<String, Object>> found = new ArrayList<>();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= meta.getColumnCount(); column++) {
                        row.put(meta.getColumnLabel(column), result.getObject(column));
                    }
                    found.add(row);
                }
                return found;
            }
        }
    }

    private static void update(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    // ---- acting (the login of the personnel specialist, the production path of the permissions) ----

    private void restoreActing() {
        if (acting == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.setContext(acting);
        }
    }

    private void actIn(SignedIn who) throws Exception {
        MockHttpServletResponse current = mvc.perform(get("/api/v1/platform/sessions:current").with(session(who))).andReturn().getResponse();
        assertThat(current.getStatus()).as("the session's own profile: " + current.getContentAsString()).isEqualTo(200);
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String code : JsonPath.<List<String>>read(current.getContentAsString(), "$.permissionCodes")) {
            authorities.add(new SimpleGrantedAuthority(code));
        }
        if ("ORDINARY".equals(JsonPath.<String>read(current.getContentAsString(), "$.mode"))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        }
        actInSession(who);
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        Method refresh = login.getClass().getDeclaredMethod("withAuthorities", java.util.Collection.class, boolean.class);
        refresh.setAccessible(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication((Authentication) refresh.invoke(login, authorities, true));
        acting = context;
        SecurityContextHolder.setContext(context);
    }
}
