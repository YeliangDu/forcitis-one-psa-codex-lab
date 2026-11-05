package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.organization.workforce.events.OrgUnitDissolved;
import com.forcitis.one.organization.workforce.events.OrgUnitMerged;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.UpholdReason;
import com.forcitis.one.platform.identity.events.RoleRevised;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #1244: the three listeners that finish F-1 block 3 on the real database. A separation closes the person's OPEN items and only those (invariant 29); a dissolved or
 * merged-away org unit opens one scope review item per role whose current version lists it (RULE-387); a role version that no longer lists the unit turns the OPEN item
 * ADJUSTED with that version's audit entry and no handler.
 */
@DisplayName("#1244 permission adjustment closeout listeners (EmployeeSeparated, OrgUnitDissolved, OrgUnitMerged, RoleRevised)")
class PermissionAdjustmentCloseoutIntegrationTest extends IdentityIntegrationTest {

    private static final Duration DELIVERY = Duration.ofSeconds(15);
    private static final String ORG_READ = "organization.workforce:org-unit:read";

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PermissionAdjustmentRepository adjustments;

    /** The default work calendar of the fixed year (weekends off, no holidays) the escalation deadline counts on; no leap in the test world means no days rows. */
    @BeforeEach
    void defaultCalendar() {
        jdbc.sql("insert into organization_attendance.work_calendar_years (id, legal_entity_id, year_number) values (:id, null, :year) on conflict do nothing")
                .param("id", java.util.UUID.randomUUID()).param("year", java.time.ZonedDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC).getYear()).update();
    }

    /** Fixture clean-up: the items and the custom org unit rows of the versions the test wrote, whose guard trigger is off for the delete. */
    @AfterEach
    void removeItems() {
        jdbc.sql("delete from platform_identity.permission_adjustments").update();
        jdbc.sql("delete from organization_attendance.work_calendar_years where legal_entity_id is null").update();
        jdbc.sql("alter table platform_identity.role_version_custom_org_units disable trigger user").update();
        try {
            jdbc.sql("delete from platform_identity.role_version_custom_org_units").update();
        } finally {
            jdbc.sql("alter table platform_identity.role_version_custom_org_units enable trigger user").update();
        }
    }

    private void deliver(DomainEvent event) {
        tx.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(jdbc.sql("select count(*) from platform_integration.event_publication p where p.completion_date is null and p.serialized_event like :event")
                .param("event", "%" + event.envelope().eventId() + "%").query(Long.class).single()).isZero());
    }

    private static OrgUnitId unit() {
        return new OrgUnitId(UUID.randomUUID());
    }

    private PermissionAdjustmentId open(PermissionAdjustment item) {
        tx.executeWithoutResult(status -> adjustments.insert(item));
        return item.id();
    }

    private PermissionAdjustmentId personItem(long person) {
        return open(PermissionAdjustment.assignmentChangeReview(new PermissionAdjustmentId(UUID.randomUUID()), TestAccounts.account(person),
                new PersonnelCommandId(UUID.randomUUID()), TriggerFieldKind.ORG_UNIT, unit(), unit(), null, null, clock.instant().plusSeconds(86_400), clock.instant()));
    }

    private void uphold(PermissionAdjustmentId id, long handler) {
        PermissionAdjustment item = adjustments.findById(id).orElseThrow();
        item.uphold(TestAccounts.account(handler), new UpholdReason("still needed"), new EmployeeId(UUID.randomUUID()), clock.instant().plus(Duration.ofDays(30)));
        tx.executeWithoutResult(status -> adjustments.update(item));
    }

    private Map<String, Object> row(PermissionAdjustmentId id) {
        return jdbc.sql("select * from platform_identity.permission_adjustments where permission_adjustment_id = :id").param("id", id.value()).query().singleRow();
    }

    private static EmployeeSeparated separated(long person, PersonnelCommandId command) {
        return new EmployeeSeparated(TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", TestAccounts.employee(person).value()), command,
                TestAccounts.employee(person), LocalDate.of(2026, 11, 3), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
    }

    @Test
    @DisplayName("#1244 invariant 29: a separation closes the person's OPEN items with the command, leaves UPHELD and other people's items, and audits the closure")
    void aSeparationClosesTheOpenItemsOfThePerson() {
        createAccount(1001);
        createAccount(1002);
        PermissionAdjustmentId open = personItem(1001);
        PermissionAdjustmentId upheld = personItem(1001);
        uphold(upheld, 1002);
        PermissionAdjustmentId other = personItem(1002);
        PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());

        deliver(separated(1001, command));

        Map<String, Object> closed = row(open);
        assertThat(closed.get("status")).isEqualTo("CLOSED_BY_SEPARATION");
        assertThat(closed.get("closure_personnel_command_id")).isEqualTo(command.value());
        assertThat(closed.get("closed_at")).isNotNull();
        assertThat(row(upheld).get("status")).isEqualTo("UPHELD");
        assertThat(row(other).get("status")).isEqualTo("OPEN");
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(jdbc.sql("select detail from platform_audit.audit_entries where action = 'platform.identity.PermissionAdjustmentClosedBySeparation' and subject_id = :id")
                .param("id", open.value()).query(String.class).list())
                .containsExactly("item " + open.value() + " SENSITIVE_ROLE_REVIEW closed by separation; user " + TestAccounts.account(1001).value() + "; command " + command.value()));
    }

    @Test
    @DisplayName("#1244: a person without an account, or a repeated separation, changes nothing")
    void aSeparationOfAPersonWithoutItemsChangesNothing() {
        createAccount(1001);
        PermissionAdjustmentId open = personItem(1001);
        PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());

        deliver(separated(1099, command));
        assertThat(row(open).get("status")).isEqualTo("OPEN");

        deliver(separated(1001, command));
        deliver(separated(1001, command));
        assertThat(row(open).get("status")).isEqualTo("CLOSED_BY_SEPARATION");
    }

    private RoleId roleListing(String code, String status, OrgUnitId... units) {
        UUID role = TestRoles.insertCustom(jdbc, code, code, status, units.length == 0 ? "SELF" : "CUSTOM_ORG_UNITS", ORG_READ);
        UUID version = jdbc.sql("select role_version_id from platform_identity.role_versions where role_id = :role and effective_to_at is null").param("role", role).query(UUID.class).single();
        list(version, units);
        return new RoleId(role);
    }

    private void list(UUID version, OrgUnitId... units) {
        for (OrgUnitId unit : units) {
            jdbc.sql("""
                    insert into platform_identity.role_version_custom_org_units (role_version_custom_org_unit_id, role_version_id, org_unit_id, is_descendants_included, created_at, updated_at)
                    values (uuidv7(), :version, :unit, false, now(), now())
                    """).param("version", version).param("unit", unit.value()).update();
        }
    }

    private List<Map<String, Object>> itemsAbout(OrgUnitId unit) {
        return jdbc.sql("select * from platform_identity.permission_adjustments where org_unit_id = :unit order by role_id").param("unit", unit.value()).query().listOfRows();
    }

    private static OrgUnitDissolved dissolved(OrgUnitId unit, OrgStructureCommandId command) {
        return new OrgUnitDissolved(TestEnvelopes.of("organization.workforce.OrgUnitDissolved", "OrgStructureCommand", command.value()), command, unit, LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("RULE-387: a dissolved org unit opens one scope review item for every role whose current version lists it, enabled or not, and none for the others")
    void aDissolvedOrgUnitOpensOneItemPerListingRole() {
        OrgUnitId gone = unit();
        OrgUnitId other = unit();
        RoleId enabled = roleListing("f1-enabled", "ENABLED", gone, other);
        RoleId disabled = roleListing("f1-disabled", "DISABLED", gone);
        roleListing("f1-elsewhere", "ENABLED", other);
        roleListing("f1-self", "ENABLED");
        OrgStructureCommandId command = new OrgStructureCommandId(UUID.randomUUID());

        deliver(dissolved(gone, command));

        List<Map<String, Object>> items = itemsAbout(gone);
        assertThat(items).extracting(item -> item.get("role_id")).containsExactlyInAnyOrder(enabled.value(), disabled.value());
        assertThat(items).allSatisfy(item -> {
            assertThat(item.get("kind")).isEqualTo("SCOPE_REVIEW");
            assertThat(item.get("origin_kind")).isEqualTo("ORG_UNIT_INACTIVE");
            assertThat(item.get("status")).isEqualTo("OPEN");
            assertThat(item.get("user_id")).isNull();
            assertThat(item.get("recipient_employee_id")).isNull();
            assertThat(item.get("trigger_org_structure_command_id")).isEqualTo(command.value());
        });
        assertThat(itemsAbout(other)).isEmpty();
    }

    @Test
    @DisplayName("RULE-387: a repeated delivery, or a merge of the same unit while an item is OPEN or UPHELD, adds nothing; the successor unit is never written")
    void anItemOpenOrUpheldForThePairIsNotOpenedAgain() {
        OrgUnitId gone = unit();
        OrgUnitId successor = unit();
        RoleId role = roleListing("f1-merged", "ENABLED", gone);
        OrgStructureCommandId command = new OrgStructureCommandId(UUID.randomUUID());
        OrgUnitDissolved first = dissolved(gone, command);

        deliver(first);
        deliver(first);
        deliver(new OrgUnitMerged(TestEnvelopes.of("organization.workforce.OrgUnitMerged", "OrgStructureCommand", command.value()), command, gone, successor,
                LocalDate.of(2026, 11, 3)));

        assertThat(itemsAbout(gone)).hasSize(1);
        assertThat(itemsAbout(successor)).isEmpty();
        assertThat(jdbc.sql("select count(*) from platform_identity.permission_adjustments where role_id = :role").param("role", role.value()).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-387: a merge opens the item for the merged-away unit")
    void aMergedAwayOrgUnitOpensTheItem() {
        OrgUnitId gone = unit();
        roleListing("f1-merge-away", "ENABLED", gone);
        OrgStructureCommandId command = new OrgStructureCommandId(UUID.randomUUID());

        deliver(new OrgUnitMerged(TestEnvelopes.of("organization.workforce.OrgUnitMerged", "OrgStructureCommand", command.value()), command, gone, unit(), LocalDate.of(2026, 11, 3)));

        assertThat(itemsAbout(gone)).singleElement().satisfies(item -> assertThat(item.get("origin_kind")).isEqualTo("ORG_UNIT_INACTIVE"));
    }

    /** The role's next version, as a revision writes it: the current one closes, the next lists the given units. Returns the new version. */
    private RoleVersionId revise(RoleId role, OrgUnitId... units) {
        jdbc.sql("update platform_identity.role_versions set effective_to_at = now(), updated_at = now() where role_id = :role and effective_to_at is null").param("role", role.value()).update();
        UUID version = jdbc.sql("""
                insert into platform_identity.role_versions (role_version_id, role_id, version_number, effective_from_at, role_name, role_status, access_scope_kind, permission_codes,
                       change_channel_kind, change_reason, version_audit_entry_id, created_at, updated_at)
                select uuidv7(), role_id, (select max(version_number) + 1 from platform_identity.role_versions where role_id = :role), now(), role_name, role_status, access_scope_kind, permission_codes, 'MIGRATION', 'test fixture', uuidv7(), now(), now()
                  from platform_identity.role_versions where role_id = :role and version_number = 1 returning role_version_id
                """).param("role", role.value()).query(UUID.class).single();
        list(version, units);
        return new RoleVersionId(version);
    }

    private static RoleRevised revised(RoleId role, RoleVersionId version) {
        return new RoleRevised(TestEnvelopes.of("platform.identity.RoleRevised", "Role", role.value()), role, version);
    }

    @Test
    @DisplayName("RULE-387: a role version that no longer lists the unit turns the OPEN item ADJUSTED with that version's audit entry and no handler; one that still lists it, and an UPHELD item, stay")
    void aRoleVersionWithoutTheUnitAdjustsTheOpenItem() {
        OrgUnitId gone = unit();
        OrgUnitId kept = unit();
        RoleId role = roleListing("f1-revised", "ENABLED", gone, kept);
        deliver(dissolved(gone, new OrgStructureCommandId(UUID.randomUUID())));
        PermissionAdjustmentId item = new PermissionAdjustmentId((UUID) itemsAbout(gone).getFirst().get("permission_adjustment_id"));
        createAccount(1002);

        RoleVersionId stillListing = revise(role, gone, kept);
        deliver(revised(role, stillListing));
        assertThat(row(item).get("status")).isEqualTo("OPEN");

        RoleVersionId without = revise(role, kept);
        deliver(revised(role, without));

        Map<String, Object> adjusted = row(item);
        assertThat(adjusted.get("status")).isEqualTo("ADJUSTED");
        assertThat(adjusted.get("adjusted_by")).isNull();
        assertThat(adjusted.get("adjusted_at")).isNotNull();
        assertThat(adjusted.get("adjusted_authorization_request_id")).isNull();
        assertThat(adjusted.get("adjusted_audit_entry_id")).isEqualTo(jdbc.sql("select version_audit_entry_id from platform_identity.role_versions where role_version_id = :v")
                .param("v", without.value()).query(UUID.class).single());
    }

    @Test
    @DisplayName("RULE-387: an UPHELD source-2 item stays UPHELD when the role drops the unit")
    void anUpheldItemStaysWhenTheRoleDropsTheUnit() {
        OrgUnitId gone = unit();
        RoleId role = roleListing("f1-upheld", "ENABLED", gone);
        deliver(dissolved(gone, new OrgStructureCommandId(UUID.randomUUID())));
        PermissionAdjustmentId item = new PermissionAdjustmentId((UUID) itemsAbout(gone).getFirst().get("permission_adjustment_id"));
        createAccount(1002);
        PermissionAdjustment loaded = adjustments.findById(item).orElseThrow();
        loaded.uphold(TestAccounts.account(1002), new UpholdReason("keep for now"), null, clock.instant());
        tx.executeWithoutResult(status -> adjustments.update(loaded));

        deliver(revised(role, revise(role)));

        assertThat(row(item).get("status")).isEqualTo("UPHELD");
    }

    @Test
    @DisplayName("RULE-387: a redelivered RoleRevised that names a version no longer current leaves the OPEN item, because the current version still lists the unit")
    void aStaleRoleRevisedLeavesTheItem() {
        OrgUnitId gone = unit();
        OrgUnitId kept = unit();
        RoleId role = roleListing("f1-stale", "ENABLED", kept);
        RoleVersionId old = new RoleVersionId(jdbc.sql("select role_version_id from platform_identity.role_versions where role_id = :role and effective_to_at is null")
                .param("role", role.value()).query(UUID.class).single());
        revise(role, kept, gone);
        deliver(dissolved(gone, new OrgStructureCommandId(UUID.randomUUID())));
        PermissionAdjustmentId item = new PermissionAdjustmentId((UUID) itemsAbout(gone).getFirst().get("permission_adjustment_id"));

        deliver(revised(role, old));

        assertThat(row(item).get("status")).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("RULE-387: two deliveries for the same role and unit at once add one item, and neither fails")
    void twoDeliveriesAtOnceAddOneItem() throws Exception {
        OrgUnitId gone = unit();
        roleListing("f1-race", "ENABLED", gone);
        OrgStructureCommandId command = new OrgStructureCommandId(UUID.randomUUID());
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<?>> runs = List.of(
                    pool.submit(() -> race(barrier, dissolved(gone, command))),
                    pool.submit(() -> race(barrier, new OrgUnitMerged(TestEnvelopes.of("organization.workforce.OrgUnitMerged", "OrgStructureCommand", command.value()), command, gone,
                            unit(), LocalDate.of(2026, 11, 3)))));
            for (java.util.concurrent.Future<?> run : runs) {
                run.get(DELIVERY.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(itemsAbout(gone)).hasSize(1);
    }

    private void race(java.util.concurrent.CyclicBarrier barrier, DomainEvent event) {
        try {
            barrier.await();
        } catch (InterruptedException | java.util.concurrent.BrokenBarrierException e) {
            throw new IllegalStateException(e);
        }
        tx.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(jdbc.sql("select count(*) from platform_integration.event_publication p where p.completion_date is null and p.serialized_event like :event")
                .param("event", "%" + event.envelope().eventId() + "%").query(Long.class).single()).isZero());
    }
}
