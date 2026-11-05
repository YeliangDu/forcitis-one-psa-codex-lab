package com.forcitis.one.acceptance.organization.workforce;

import com.forcitis.one.acceptance.EmploymentRelationshipSeeds;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #711 acceptance part, done-when 9b (one-design ruling on #711 comment 6590431312, after #934 removes the periodic sync; DD revision 2
 * section 4.3 M5): the initial version migration finds or creates the person's PRIMARY root and writes only that root's versions; an open
 * CONCURRENT version the person already has stays open and unchanged. Login-miss registration no longer exists (ADR-0069 section 9), so this
 * migration is the only path that finds or creates the PRIMARY root of a person who already has a concurrent post.
 */
class ConcurrentAssignmentMigrationIT extends InitialVersionMigrationSupport {

    static final TestDatabase postgres = TestPostgres.database(ConcurrentAssignmentMigrationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#711 完成标准 9b（one-design 裁定 #711 comment 6590431312）：快照里的人已有一条开着的兼职版本（无员工版本、无主任职版本）：迁移给他建且只建一个主任职根并补写主任职版本；兼职根与它的版本一行不动、仍开着；没有新的兼职根")
    void theMigrationWritesOnlyThePrimaryRoot() throws Exception {
        UUID person = snapshotPerson(false, null);
        UUID concurrentUnit = newUnit();
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        UUID concurrent;
        try (Connection workforce = schemaConnection("organization_workforce")) {
            concurrent = AssignmentRoots.create(workforce, stored(person), AssignmentRoots.CONCURRENT);
            update(workforce, """
                    insert into assignment_versions (employment_relationship_id, assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date, revision_number,
                      org_unit_id, change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, 'CONCURRENT', ?, 0, ?, 'MIGRATION', ?, ?)""", EmploymentRelationshipSeeds.relationshipOf(workforce, stored(person)), Uuids.timeOrdered(NOW), stored(person), concurrent, TODAY.minusDays(40), stored(concurrentUnit), at, at);
        }
        List<Map<String, Object>> concurrentBefore = rows("select * from assignment_versions where assignment_id = ?", concurrent);

        Result result = migrate();

        assertThat(result.exitCode()).as(result.text()).isZero();
        List<Map<String, Object>> roots = rows("select assignment_id, assignment_kind from " + AssignmentRoots.TABLE + " where employee_id = ?", stored(person));
        assertThat(roots).filteredOn(root -> AssignmentRoots.PRIMARY.equals(root.get("assignment_kind"))).hasSize(1);
        assertThat(roots).filteredOn(root -> AssignmentRoots.CONCURRENT.equals(root.get("assignment_kind"))).singleElement()
                .satisfies(root -> assertThat(root.get("assignment_id")).isEqualTo(concurrent));
        UUID primary = (UUID) roots.stream().filter(root -> AssignmentRoots.PRIMARY.equals(root.get("assignment_kind"))).findFirst().orElseThrow().get("assignment_id");
        assertThat(rows("select 1 from assignment_versions where assignment_id = ? and effective_to_date is null and superseded_at is null", primary))
                .as("the migration wrote the primary's open version").hasSize(1);
        assertThat(rows("select * from assignment_versions where assignment_id = ?", concurrent)).as("the concurrent post row by row unchanged")
                .isEqualTo(concurrentBefore);
        assertThat(rows("select 1 from assignment_versions where employee_id = ? and assignment_id is distinct from ? and assignment_id is distinct from ?",
                stored(person), primary, concurrent)).as("no version on any other root").isEmpty();
    }
}
