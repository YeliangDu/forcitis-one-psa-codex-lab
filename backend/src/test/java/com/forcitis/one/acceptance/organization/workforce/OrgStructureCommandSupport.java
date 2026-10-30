package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.ASSIGNMENT_VERSIONS;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PERSONNEL_SPECIALIST_ROLE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Ground of the #906 org-structure command tests, on the world of {@link PersonnelCommandSupport}: the caller is a real 组织数据管理员
 * (the seeded role org_data_admin, granted by the operations command and signed in), org units are seeded with a migrated initial
 * version so that commands have a timeline to extend, and the org tables are read by their agreed names (#904, #905 DD sign-offs).
 */
abstract class OrgStructureCommandSupport extends PersonnelCommandSupport {

    @Autowired
    private ApplicationContext appContext;

    protected Staff orgAdmin;

    @Override
    protected Staff buildPersonnelSpecialist() {
        try {
            return super.buildPersonnelSpecialist();
        } catch (Exception | AssertionError notSeededYet) {
            return null;
        }
    }

    /** The caller is the 人事专员; the role is #871's to seed, so its absence is a failure of the test that needs it, with this reason. */
    protected void actInSpecialist() throws Exception {
        assertThat(hr).as("the seeded role " + PERSONNEL_SPECIALIST_ROLE + " (personnel command card #871, user decision 2026-10-30)").isNotNull();
        actIn(hr);
    }

    @BeforeEach
    void signInTheOrgDataAdmin() throws Exception {
        setToday(TODAY);
        orgAdmin = staff(ORG_DATA_ADMIN_ROLE);
        actIn(orgAdmin);
    }

    @AfterEach
    void putTheClockBack() {
        setToday(TODAY);
    }

    protected OrgStructureCommandsProbe orgCommands() {
        restoreActing();
        return OrgStructureCommandsProbe.of(appContext, AccountsAcceptanceSupport::employeeIdOf);
    }

    // ---- entering ----

    protected static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> fields = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            fields.put((String) keyValues[i], keyValues[i + 1]);
        }
        return fields;
    }

    /** A command on an org unit; {@code extra} pairs (component name, value) override or add. */
    protected UUID unitCommand(String kind, UUID target, LocalDate day, Object... extra) {
        Map<String, Object> fields = fields(TARGET_KIND, ORG_UNIT, COMMAND_KIND, kind, TARGET_ORG_UNIT_ID, target, EFFECTIVE_DATE, day);
        fields.putAll(fields(extra));
        return orgCommands().enter(fields);
    }

    /** A CREATE of an org unit (the server makes its id); a plain headquarters-less, non-delivery unit unless {@code extra} says otherwise. */
    protected UUID createUnit(LocalDate day, Object... extra) {
        Map<String, Object> fields = fields(TARGET_KIND, ORG_UNIT, COMMAND_KIND, CREATE, EFFECTIVE_DATE, day, TARGET_NAME, "Synthetic new unit",
                TARGET_IS_DELIVERY_UNIT, false);
        fields.putAll(fields(extra));
        return orgCommands().enter(fields);
    }

    protected UUID positionCommand(String kind, UUID target, LocalDate day, Object... extra) {
        Map<String, Object> fields = fields(TARGET_KIND, POSITION, COMMAND_KIND, kind, TARGET_POSITION_ID, target, EFFECTIVE_DATE, day);
        fields.putAll(fields(extra));
        return orgCommands().enter(fields);
    }

    protected UUID createPosition(LocalDate day, String code, String name) {
        return orgCommands().enter(fields(TARGET_KIND, POSITION, COMMAND_KIND, CREATE, EFFECTIVE_DATE, day, TARGET_CODE, code, TARGET_NAME, name));
    }

    /** The entry is refused for its content (a business refusal, not a failure of the system). */
    protected static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable entry) {
        assertThatThrownBy(entry).isInstanceOf(DomainException.class);
    }

    // ---- seeds ----

    /** An org unit with its snapshot row and a migrated first version from {@link #SINCE}: ACTIVE, headquarters, not a delivery unit. */
    protected UUID seedUnit(UUID parent) throws Exception {
        UUID id = newOrgUnitId();
        orgUnit(id, parent, null);
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, code, name, parent_org_unit_id, status,
                      region_kind, is_delivery_unit, created_at, updated_at) values (?, ?, ?, null, ?, ?, 'ACTIVE', 'HEADQUARTERS', false, ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(id), SINCE, seededName(id), stored(parent), at, at);
        }
        return id;
    }

    protected static String seededName(UUID unitId) {
        return "Seeded unit " + unitId.toString().substring(24);
    }

    // ---- reading ----

    protected Map<String, Object> orgCommand(UUID id) throws SQLException {
        List<Map<String, Object>> found = rows("select * from " + ORG_COMMANDS + " where org_structure_command_id = ?", id);
        assertThat(found).as("a row of " + ORG_COMMANDS + " for " + id).hasSize(1);
        return found.get(0);
    }

    protected UUID targetUnitOf(UUID command) throws SQLException {
        return fromStored(orgCommand(command).get("target_org_unit_id"));
    }

    protected UUID targetPositionOf(UUID command) throws SQLException {
        return fromStored(orgCommand(command).get("target_position_id"));
    }

    protected long orgCommandCount() throws SQLException {
        return (long) rows("select count(*) as n from " + ORG_COMMANDS).get(0).get("n");
    }

    protected long unitCount() throws SQLException {
        return (long) rows("select count(*) as n from " + ORG_UNITS).get(0).get("n");
    }

    protected long positionCount() throws SQLException {
        return (long) rows("select count(*) as n from " + POSITIONS).get(0).get("n");
    }

    protected List<Map<String, Object>> unitVersions(UUID unitId) throws SQLException {
        return rows("select * from " + ORG_UNIT_VERSIONS + " where org_unit_id = ? order by effective_from_date, revision_number", stored(unitId));
    }

    protected List<Map<String, Object>> currentUnitVersions(UUID unitId) throws SQLException {
        return rows("select * from " + ORG_UNIT_VERSIONS + " where org_unit_id = ? and superseded_at is null order by effective_from_date", stored(unitId));
    }

    /** The current fact about the unit on the day, or null. */
    protected Map<String, Object> unitOn(UUID unitId, LocalDate day) throws SQLException {
        return inForceOn(currentUnitVersions(unitId), day);
    }

    protected List<Map<String, Object>> currentPositionVersions(UUID positionId) throws SQLException {
        return rows("select * from " + POSITION_VERSIONS + " where position_id = ? and superseded_at is null order by effective_from_date", stored(positionId));
    }

    protected Map<String, Object> positionOn(UUID positionId, LocalDate day) throws SQLException {
        return inForceOn(currentPositionVersions(positionId), day);
    }

    /** The position the employee holds on the day (the current assignment fact). */
    protected UUID positionHeldBy(UUID employee, LocalDate day) throws SQLException {
        return fromStored(inForceOn(currentVersions(ASSIGNMENT_VERSIONS, employee), day).get("position_id"));
    }

    protected UUID unitOfEmployee(UUID employee, LocalDate day) throws SQLException {
        return fromStored(inForceOn(currentVersions(ASSIGNMENT_VERSIONS, employee), day).get("org_unit_id"));
    }

    protected int orgEventsOf(UUID command) throws SQLException {
        int count = 0;
        for (String type : ALL_ORG_EVENTS) {
            count += eventsOf(type, command);
        }
        return count;
    }

    /** ADR-0068 item 4 and the #905 DD: the rows chain and every step is one the lifecycle allows (PENDING to IN_PROGRESS only for merges). */
    protected void assertLawfulOrgLifecycle(List<Map<String, Object>> steps, String lastStatus) {
        Map<String, List<String>> allowed = Map.of(PENDING, List.of(IN_PROGRESS, EFFECTIVE, WITHDRAWN), IN_PROGRESS, List.of(EFFECTIVE));
        assertThat(steps).isNotEmpty();
        String previous = null;
        long sequence = -1;
        for (Map<String, Object> step : steps) {
            String from = (String) step.get("from_status");
            String to = (String) step.get("to_status");
            assertThat(from).as("a step starts where the last one ended").isEqualTo(previous);
            if (from != null) {
                assertThat(allowed.getOrDefault(from, List.of())).as(from + " -> " + to).contains(to);
            } else {
                assertThat(to).as("a command is first PENDING or goes straight to EFFECTIVE").isIn(PENDING, EFFECTIVE);
            }
            long next = (long) step.get("transition_sequence_number");
            assertThat(next).isGreaterThan(sequence);
            sequence = next;
            previous = to;
        }
        assertThat(previous).isEqualTo(lastStatus);
    }

    // ---- audit ----

    protected Set<Object> auditIds(String action) throws Exception {
        Set<Object> ids = new HashSet<>();
        for (Map<String, Object> entry : auditQuery(null, null, action)) {
            ids.add(entry.get("entryId"));
        }
        return ids;
    }

    /** The text of the audit entries of the action that were not there before (detail, reason, before and after), as one string. */
    protected String auditTextAdded(String action, Set<Object> before) throws Exception {
        List<String> added = new ArrayList<>();
        for (Map<String, Object> entry : auditQuery(null, null, action)) {
            if (!before.contains(entry.get("entryId"))) {
                added.add(entry.get("detail") + " " + entry.get("changeReason") + " " + entry.get("beforeText") + " " + entry.get("afterText"));
            }
        }
        assertThat(added).as("new audit entries of " + action).isNotEmpty();
        return String.join(" | ", added);
    }
}
