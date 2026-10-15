package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static com.forcitis.one.fixture.doubles.TestOrgIds.ownEmployee;
import static com.forcitis.one.fixture.doubles.TestOrgIds.ownOrgUnit;
import static com.forcitis.one.fixture.doubles.TestOrgIds.ownPosition;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRelationship;
import com.forcitis.one.platform.integration.api.SourceRelationshipKind;
import com.forcitis.one.platform.integration.application.InMemorySourceReferences;
import com.forcitis.one.platform.integration.application.InMemorySourceReferences.Allocation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceIdTranslationTest {

    static final ExternalSource SOURCE = ExternalSource.ORG_SOURCE;

    static OrgUnitRow unit(long id) {
        return new OrgUnitRow(id, "C" + id, "Unit " + id, "path-" + id, 1, SourceRecordState.ACTIVE, List.of(), null, null, false, "Department");
    }

    static MemberRow member(long id, long unit, Long post, Long supervisor) {
        return new MemberRow(id, "E" + id, "Person " + id, unit, post, supervisor, null, null, null, null, false, false);
    }

    static Allocation allocation(SourceObjectKind kind, long sourceId) {
        return new Allocation(SOURCE, kind, sourceId);
    }

    @Test
    @DisplayName("ADR-0033 ids that have a reference are looked up, one lookup per kind, and allocate is called only for the ids without one")
    void onlyMissingIdsAreAllocated() {
        UUID knownUnit = UUID.randomUUID();
        UUID knownPerson = UUID.randomUUID();
        InMemorySourceReferences references = new InMemorySourceReferences()
                .confirm(SOURCE, SourceObjectKind.ORG_UNIT, 1, knownUnit)
                .confirm(SOURCE, SourceObjectKind.EMPLOYEE, 10, knownPerson);

        SourceOrgSnapshot snapshot = new SourceIdTranslation(references).snapshot(
                List.of(new PlacedUnit(unit(1), null, null), new PlacedUnit(unit(2), 1L, null)),
                List.of(),
                List.of(new PostRow(100, "P100", "Post", 1, SourceRecordState.ACTIVE)),
                List.of(member(10, 1, 100L, null), member(11, 2, 100L, 10L)));

        assertThat(references.allocations()).containsExactlyInAnyOrder(allocation(SourceObjectKind.ORG_UNIT, 2),
                allocation(SourceObjectKind.POSITION, 100), allocation(SourceObjectKind.EMPLOYEE, 11));
        assertThat(references.lookups()).extracting(InMemorySourceReferences.Lookup::kind)
                .containsExactlyInAnyOrder(SourceObjectKind.ORG_UNIT, SourceObjectKind.POSITION, SourceObjectKind.EMPLOYEE);
        assertThat(snapshot.units()).extracting(u -> u.unitId().value()).containsExactly(knownUnit, ownOrgUnit(2).value());
        assertThat(snapshot.units()).extracting(SourceOrgUnit::parentUnitId).containsExactly(null, new OwnObjectId(knownUnit));
        SourcePerson second = snapshot.staff().get(1);
        assertThat(second.personId()).isEqualTo(ownEmployee(11));
        assertThat(second.supervisorId().value()).isEqualTo(knownPerson);
        assertThat(second.positionId()).isEqualTo(ownPosition(100));
        assertThat(snapshot.staff().getFirst().personId().value()).isEqualTo(knownPerson);
    }

    @Test
    @DisplayName("a second read of the same objects allocates nothing more")
    void aSecondReadAllocatesNothing() {
        InMemorySourceReferences references = new InMemorySourceReferences();
        SourceIdTranslation translation = new SourceIdTranslation(references);
        List<PlacedUnit> units = List.of(new PlacedUnit(unit(1), null, null));
        List<MemberRow> members = List.of(member(10, 1, null, null));

        SourceOrgSnapshot first = translation.snapshot(units, List.of(), List.of(), members);
        int allocated = references.allocations().size();
        SourceOrgSnapshot second = translation.snapshot(units, List.of(), List.of(), members);

        assertThat(references.allocations()).hasSize(allocated);
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("RULING-P3B-D3 a parent, a unit shared with, a supervisor, a position or a relationship target outside the read gets its own id all the same")
    void idsOutsideTheReadAreAllocatedToo() {
        InMemorySourceReferences references = new InMemorySourceReferences();

        SourceOrgSnapshot snapshot = new SourceIdTranslation(references).snapshot(
                List.of(new PlacedUnit(unit(1), 7L, 8L)),
                List.of(new RelationshipRow(SourceRelationshipKind.UNIT_HEAD, 9, 900, false)),
                List.of(),
                List.of(member(10, 1, 200L, 300L)));

        assertThat(references.allocations()).contains(allocation(SourceObjectKind.ORG_UNIT, 7), allocation(SourceObjectKind.ORG_UNIT, 8),
                allocation(SourceObjectKind.ORG_UNIT, 9), allocation(SourceObjectKind.EMPLOYEE, 900), allocation(SourceObjectKind.EMPLOYEE, 300),
                allocation(SourceObjectKind.POSITION, 200));
        SourceOrgUnit only = snapshot.units().getFirst();
        assertThat(only.parentUnitId()).isEqualTo(ownOrgUnit(7));
        assertThat(only.duplicateOfUnitId()).isEqualTo(ownOrgUnit(8));
        assertThat(snapshot.relationships()).singleElement().satisfies(r -> {
            assertThat(r.unitId()).isEqualTo(ownOrgUnit(9));
            assertThat(r.personId()).isEqualTo(ownEmployee(900));
        });
        assertThat(snapshot.staff().getFirst().supervisorId()).isEqualTo(ownEmployee(300));
        assertThat(snapshot.staff().getFirst().positionId()).isEqualTo(ownPosition(200));
    }

    @Test
    @DisplayName("relationships are handed out in the order of the source person id, whatever order the view returned")
    void relationshipsInSourcePersonOrder() {
        SourceOrgSnapshot snapshot = new SourceIdTranslation(new InMemorySourceReferences()).snapshot(
                List.of(new PlacedUnit(unit(1), null, null)),
                List.of(new RelationshipRow(SourceRelationshipKind.UNIT_HEAD, 1, 803, false),
                        new RelationshipRow(SourceRelationshipKind.REGIONAL_RECEIVABLES_MANAGER, 1, 801, false),
                        new RelationshipRow(SourceRelationshipKind.UNIT_HEAD, 1, 802, true)),
                List.of(), List.of());

        assertThat(snapshot.relationships()).extracting(SourceRelationship::personId)
                .containsExactly(ownEmployee(801), ownEmployee(802), ownEmployee(803));
        assertThat(snapshot.relationships()).extracting(SourceRelationship::kind).containsExactly(
                SourceRelationshipKind.REGIONAL_RECEIVABLES_MANAGER, SourceRelationshipKind.UNIT_HEAD, SourceRelationshipKind.UNIT_HEAD);
        assertThat(snapshot.relationships().get(1).personDeleted()).isTrue();
    }

    @Test
    @DisplayName("a single-person lookup translates the same way and allocates only what is missing")
    void peopleLookup() {
        InMemorySourceReferences references = new InMemorySourceReferences().confirm(SOURCE, SourceObjectKind.ORG_UNIT, 1, ownOrgUnit(1).value());

        List<SourcePerson> people = new SourceIdTranslation(references).people(List.of(member(10, 1, null, null)));

        assertThat(people).singleElement().satisfies(p -> {
            assertThat(p.personId()).isEqualTo(ownEmployee(10));
            assertThat(p.unitId()).isEqualTo(ownOrgUnit(1));
            assertThat(p.positionId()).isNull();
            assertThat(p.supervisorId()).isNull();
        });
        assertThat(references.allocations()).containsExactly(allocation(SourceObjectKind.EMPLOYEE, 10));
    }
}
