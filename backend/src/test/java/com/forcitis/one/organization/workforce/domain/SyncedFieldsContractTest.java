package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.Fixtures.apply;
import static com.forcitis.one.organization.workforce.domain.Fixtures.first;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.sourcePerson;
import static com.forcitis.one.organization.workforce.domain.Fixtures.sourceUnit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unitId;
import static com.forcitis.one.organization.workforce.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceGender;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRegionKind;
import com.forcitis.one.platform.integration.api.SourceRelationship;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-1a-23: OrgSnapshotChanged and the diff list are driven by the one list of synced fields. Each field of the list, changed alone in the
 * source, must reach the event and the diff; a field that is not on the list (the short name) must not. A new synced field cannot be added
 * without a case here, because the cases must be exactly the list.
 */
class SyncedFieldsContractTest {
    private static SourcePerson employee(long id) {
        return person(id, 2, 100L, null, "139" + id, "wx-" + id);
    }

    private static SourcePerson with(SourcePerson p, UnaryOperator<Builder> change) {
        return change.apply(new Builder(p)).build();
    }

    private static final class Builder {
        SourcePerson p;
        String employeeCode;
        String displayName;
        OwnObjectId unitId;
        OwnObjectId positionId;
        OwnObjectId supervisorId;
        String loginName;
        String email;
        SourceGender gender;
        WecomUserId wecomUserId;
        boolean feeEarner;

        Builder(SourcePerson p) {
            this.p = p;
            employeeCode = p.employeeCode();
            displayName = p.displayName();
            unitId = p.unitId();
            positionId = p.positionId();
            supervisorId = p.supervisorId();
            loginName = p.loginName();
            email = p.email();
            gender = p.gender();
            wecomUserId = p.wecomUserId();
            feeEarner = p.feeEarner();
        }

        SourcePerson build() {
            return new SourcePerson(p.personId(), employeeCode, displayName, unitId, positionId, supervisorId, loginName, email, gender,
                    wecomUserId, feeEarner);
        }
    }

    private static final Map<String, UnaryOperator<Builder>> STAFF_CHANGES = new LinkedHashMap<>();

    static {
        // employeeCode is fixed once issued (ADR-0078): a source change is kept out of the diff and recorded as an issue, see EmployeeSyncRulesTest
        STAFF_CHANGES.put("displayName", b -> { b.displayName = "Changed name"; return b; });
        STAFF_CHANGES.put("email", b -> { b.email = "changed@example.test"; return b; });
        STAFF_CHANGES.put("gender", b -> { b.gender = SourceGender.FEMALE; return b; });
        STAFF_CHANGES.put("wecomUserId", b -> { b.wecomUserId = new WecomUserId("wx-changed"); return b; });
        STAFF_CHANGES.put("employmentStatus", b -> { b.loginName = null; return b; });
        STAFF_CHANGES.put("feeEarner", b -> { b.feeEarner = true; return b; });
        STAFF_CHANGES.put("orgUnitId", b -> { b.unitId = sourceUnit(3); return b; });
        STAFF_CHANGES.put("positionId", b -> { b.positionId = Fixtures.sourcePosition(101); return b; });
        STAFF_CHANGES.put("reportsToEmployeeId", b -> { b.supervisorId = sourcePerson(2002); return b; });
    }

    @Test
    @DisplayName("D-1a-23 every synced employee field, changed alone, reaches the diff and the event of that employee and of nobody else")
    void everySyncedStaffFieldReachesTheEvent() {
        assertThat(STAFF_CHANGES.keySet()).containsExactlyElementsOf(SyncedFields.STAFF.stream().map(SyncedFields.Field::name)
                .filter(name -> !name.equals("employeeCode")).toList());
        SourceOrgSnapshot base = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L)), List.of(), List.of(post(100), post(101)),
                List.of(employee(2001), employee(2002)));
        OrgSnapshot stored = apply(OrgSnapshot.empty(), first(base));

        STAFF_CHANGES.forEach((field, change) -> {
            SourceOrgSnapshot changed = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L)), List.of(), List.of(post(100), post(101)),
                    List.of(with(employee(2001), change), employee(2002)));

            Reconciliation result = SnapshotReconciler.reconcile(stored, changed);

            assertThat(result.diffs()).as(field).anySatisfy(d -> {
                assertThat(d.subjectKey()).isEqualTo("employee:" + user(2001).value());
                assertThat(d.changedFields()).contains(field);
            });
            List<com.forcitis.one.kernel.EmployeeId> announced = new ArrayList<>(result.changedEmployees());
            announced.addAll(result.separatedEmployees());
            assertThat(announced).as(field).containsExactly(user(2001));
        });
    }

    @Test
    @DisplayName("D-1a-23 an unchanged source announces nothing, and a short name (not a synced field) is not a change")
    void nothingChangedAnnouncesNothing() {
        SourceOrgSnapshot base = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L)), List.of(), List.of(post(100), post(101)),
                List.of(employee(2001)));
        OrgSnapshot stored = apply(OrgSnapshot.empty(), first(base));
        OrgSnapshot withShortName = new OrgSnapshot(stored.orgUnits().stream().map(u -> u.withShortName("S")).toList(), stored.positions(), stored.staff());

        Reconciliation result = SnapshotReconciler.reconcile(withShortName, base);

        assertThat(result.hasChanges()).isFalse();
        assertThat(result.changedEmployees()).isEmpty();
        assertThat(result.separatedEmployees()).isEmpty();
        assertThat(result.changedOrgUnits()).isEmpty();
    }

    private static SourceOrgUnit changedUnit(SourceOrgUnit u, String code, String name, Long parent, Integer sortOrder, SourceRecordState state,
                                             SourceRegionKind region, boolean delivery) {
        return new SourceOrgUnit(u.unitId(), code, name, parent == null ? null : sourceUnit(parent), sortOrder, state, u.emptyFlags(), region,
                u.rawRegion(), delivery, u.duplicateOfUnitId());
    }

    @Test
    @DisplayName("D-1a-23 every synced org unit field, changed alone, reaches the diff and the event of that unit and of no other")
    void everySyncedOrgUnitFieldReachesTheEvent() {
        SourceOrgUnit three = unit(3, 2L);
        Map<String, SourceOrgSnapshot> changes = new LinkedHashMap<>();
        List<SourcePerson> people = List.of(employee(2001));
        List<SourceRelationship> none = List.of();
        changes.put("code", snapshot(changedUnit(three, "C-changed", three.name(), 2L, three.sortOrder(), three.state(), three.regionKind(), three.deliveryUnit()), none, people));
        changes.put("name", snapshot(changedUnit(three, three.code(), "Changed", 2L, three.sortOrder(), three.state(), three.regionKind(), three.deliveryUnit()), none, people));
        changes.put("parentOrgUnitId", snapshot(changedUnit(three, three.code(), three.name(), 1L, three.sortOrder(), three.state(), three.regionKind(), three.deliveryUnit()), none, people));
        changes.put("sortOrder", snapshot(changedUnit(three, three.code(), three.name(), 2L, 99, three.state(), three.regionKind(), three.deliveryUnit()), none, people));
        changes.put("status", snapshot(changedUnit(three, three.code(), three.name(), 2L, three.sortOrder(), SourceRecordState.DISABLED, three.regionKind(), three.deliveryUnit()), none, people));
        changes.put("headEmployeeId", snapshot(three, List.of(head(3, 2001)), people));
        changes.put("regionalReceivablesManagerId", snapshot(three, List.of(manager(3, 2001)), people));
        changes.put("regionCategory", snapshot(changedUnit(three, three.code(), three.name(), 2L, three.sortOrder(), three.state(), SourceRegionKind.REGIONAL, three.deliveryUnit()), none, people));
        changes.put("deliveryUnit", snapshot(changedUnit(three, three.code(), three.name(), 2L, three.sortOrder(), three.state(), three.regionKind(), true), none, people));
        assertThat(changes.keySet()).containsExactlyElementsOf(SyncedFields.ORG_UNIT.stream().map(SyncedFields.Field::name).toList());
        OrgSnapshot stored = apply(OrgSnapshot.empty(), first(snapshot(three, none, people)));

        changes.forEach((field, changed) -> {
            Reconciliation result = SnapshotReconciler.reconcile(stored, changed);

            assertThat(result.diffs()).as(field).anySatisfy(d -> {
                assertThat(d.subjectKey()).isEqualTo("org-unit:" + unitId(3).value());
                assertThat(d.changedFields()).contains(field);
            });
            assertThat(result.changedOrgUnits()).as(field).containsExactly(unitId(3));
        });
    }

    private static SourceOrgSnapshot snapshot(SourceOrgUnit three, List<SourceRelationship> relationships, List<SourcePerson> people) {
        return source(List.of(unit(1, null), unit(2, 1L), three), relationships, List.of(post(100), post(101)), people);
    }
}
