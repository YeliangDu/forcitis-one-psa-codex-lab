package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.ExternalSourceBrokenException;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeeyonOrgTreeTest {

    static OrgUnitRow row(long id, String path, SourceRecordState state, Integer sortOrder) {
        return typed(id, path, state, sortOrder, "Department");
    }

    static OrgUnitRow typed(long id, String path, SourceRecordState state, Integer sortOrder, String type) {
        return new OrgUnitRow(new SourceUnitId(id), "C" + id, "Unit " + id, path, sortOrder, state, List.of(), null, null, false, type);
    }

    static OrgUnitRow typed(long id, String path, String type) {
        return typed(id, path, SourceRecordState.ACTIVE, (int) id, type);
    }

    @Test
    @DisplayName("R-1b-0 C-4 the root unit may be Account or Department; every unit below it is Department")
    void rootTypeIsFreeBelowIsDepartment() {
        assertThat(resolve(List.of(typed(2, "00000001", "Account"), typed(3, "000000010001", "Department")))).hasSize(2);
        assertThat(resolve(List.of(typed(2, "00000001", "Department"), typed(3, "000000010001", "Department")))).hasSize(2);
    }

    @Test
    @DisplayName("R-1b-0 RULING-R1B0-2 an Account below the root rejects the whole read; the message names TYPE and a count, no unit")
    void accountBelowTheRootIsRejected() {
        assertThatThrownBy(() -> resolve(List.of(typed(2, "00000001", "Account"), typed(3, "000000010001", "Account"),
                typed(4, "000000010002", "Account"))))
                .isInstanceOf(ExternalSourceBrokenException.class).hasMessageContaining("TYPE").hasMessageContaining("2 unit(s) below the sync root are Account")
                .hasMessageNotContaining("Unit 3");
    }

    @Test
    @DisplayName("R-1b-0 C-3 TYPE NULL, another value, or the right word in another case or with spaces rejects the whole read, root or not")
    void illegalTypeIsRejected() {
        for (String type : Arrays.asList(null, "Group", "department", "ACCOUNT", "Department ", "")) {
            assertThatThrownBy(() -> resolve(List.of(typed(2, "00000001", "Account"), typed(3, "000000010001", type))))
                    .as("below the root: %s", type).isInstanceOf(ExternalSourceBrokenException.class)
                    .hasMessageContaining("TYPE").hasMessageContaining("1 unit(s) hold NULL or a value other than Department or Account");
            assertThatThrownBy(() -> resolve(List.of(typed(2, "00000001", type), typed(3, "000000010001", "Department"))))
                    .as("the root: %s", type).isInstanceOf(ExternalSourceBrokenException.class).hasMessageContaining("TYPE");
        }
    }

    static List<SourceOrgUnit> resolve(List<OrgUnitRow> rows) {
        return SeeyonOrgTree.resolve(rows, "00000001");
    }

    @Test
    @DisplayName("M7 the sync root is a setting: another root path scopes the tree below it")
    void rootPathIsConfigurable() {
        List<SourceOrgUnit> units = SeeyonOrgTree.resolve(List.of(row(7, "00000002"), row(8, "000000020001")), "00000002");

        assertThat(units).extracting(u -> u.parentUnitId() == null ? null : u.parentUnitId().value()).containsExactly(null, 7L);
        assertThatThrownBy(() -> SeeyonOrgTree.resolve(List.of(row(2, "00000001")), "00000002"))
                .isInstanceOf(ExternalSourceBrokenException.class).hasMessageContaining("not below the sync root");
    }

    static OrgUnitRow row(long id, String path) {
        return row(id, path, SourceRecordState.ACTIVE, (int) id);
    }

    @Test
    @DisplayName("RULE-354 parent = the unit one level up; the root has none; parents come before children")
    void parentsAreResolved() {
        List<SourceOrgUnit> units = resolve(List.of(row(4, "0000000100010001"), row(2, "00000001"), row(3, "000000010001"),
                row(5, "000000010002")));

        assertThat(units).extracting(u -> u.unitId().value()).containsExactly(2L, 3L, 5L, 4L);
        assertThat(units).extracting(u -> u.parentUnitId() == null ? null : u.parentUnitId().value()).containsExactly(null, 2L, 2L, 3L);
    }

    @Test
    @DisplayName("RULE-354 a missing parent breaks the tree: the whole read is rejected")
    void missingParent() {
        assertThatThrownBy(() -> resolve(List.of(row(2, "00000001"), row(4, "0000000100010001"))))
                .isInstanceOf(ExternalSourceBrokenException.class).hasMessageContaining("org unit 4").hasMessageContaining("no parent");
    }

    @Test
    @DisplayName("RULE-354 a unit outside the sync root, or with a malformed path, breaks the tree")
    void outsideTheRoot() {
        assertThatThrownBy(() -> resolve(List.of(row(9, "00000002")))).isInstanceOf(ExternalSourceBrokenException.class)
                .hasMessageContaining("not below the sync root");
        assertThatThrownBy(() -> resolve(List.of(row(2, "00000001"), row(9, "00000001000"))))
                .isInstanceOf(ExternalSourceBrokenException.class);
    }

    @Test
    @DisplayName("RULE-354 two units on one place: the active one is the parent candidate, the other is reported as sharing the place")
    void sharedPlace() {
        List<SourceOrgUnit> units = resolve(List.of(row(2, "00000001"),
                row(3, "000000010001", SourceRecordState.DISABLED, 1), row(4, "000000010001", SourceRecordState.ACTIVE, 2),
                row(5, "0000000100010001")));

        SourceOrgUnit child = units.stream().filter(u -> u.unitId().value() == 5).findFirst().orElseThrow();
        assertThat(child.parentUnitId()).isEqualTo(new SourceUnitId(4));
        assertThat(units.stream().filter(u -> u.unitId().value() == 4).findFirst().orElseThrow().duplicateOfUnitId()).isEqualTo(new SourceUnitId(3));
        assertThat(units.stream().filter(u -> u.unitId().value() == 3).findFirst().orElseThrow().duplicateOfUnitId()).isNull();
    }

    @Test
    @DisplayName("M2 the api record carries no path at all: only ids and role-named fields")
    void theApiRecordHasNoPath() {
        assertThat(Arrays.stream(SourceOrgUnit.class.getRecordComponents()).map(RecordComponent::getName))
                .noneMatch(n -> n.toLowerCase().contains("path"))
                .contains("parentUnitId");
    }
}
