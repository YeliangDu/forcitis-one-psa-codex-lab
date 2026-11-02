package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceGender;
import com.forcitis.one.platform.integration.api.SourcePerson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #711 acceptance part, done-when 9 (DD revision 2 on #711 section 4.3 M5): the sync writer acts on the PRIMARY root only. It finds or
 * creates that root (sync is also how new staff arrive without a HIRE) and closes only its versions, so a sync change of the primary
 * leaves an open concurrent post open, and a newly synced employee has exactly one PRIMARY root. The organisation enters through the real
 * sync from the source port double of {@link DataScopeSupport}; the concurrent post is seeded in the schema, because no source carries
 * concurrent posts. Every key and unit code here is new per test (#1038).
 *
 * <p>Done-when 9a (one-design ruling on #711 comment 6590431312): registration on a login miss, the writer that remains when #934 removes
 * the periodic sync, creates exactly one PRIMARY root with its version and no CONCURRENT root. The periodic-sync cases are to be dropped
 * when #934 (PR #1018) merges, under the same ruling.
 */
class ConcurrentAssignmentSyncIT extends DataScopeSupport {

    static final TestDatabase postgres = TestPostgres.database(ConcurrentAssignmentSyncIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private List<List<Object>> select(String sql, Object... args) throws SQLException {
        List<List<Object>> found = new ArrayList<>();
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement statement = workforce.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                int columns = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int c = 1; c <= columns; c++) {
                        row.add(rows.getObject(c));
                    }
                    found.add(row);
                }
            }
        }
        return found;
    }

    private UUID employee(String key) {
        return UUID.fromString(employeeIdOf(key));
    }

    private UUID unitId(String code) throws SQLException {
        return (UUID) select("select id from org_units where name = ?", unitName(code)).get(0).get(0);
    }

    private List<UUID> roots(UUID employee, String kind) throws SQLException {
        return select("select assignment_id from " + AssignmentRoots.TABLE + " where employee_id = ? and assignment_kind = ?", employee, kind).stream()
                .map(row -> (UUID) row.get(0)).toList();
    }

    /** Seeds a concurrent root with one open version from today in the unit (revision 1: the sync's primary version may start today too). */
    private UUID concurrentPost(UUID employee, UUID orgUnit) throws SQLException {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            UUID root = AssignmentRoots.create(workforce, employee, AssignmentRoots.CONCURRENT);
            try (PreparedStatement insert = workforce.prepareStatement("""
                    insert into assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date, revision_number,
                      org_unit_id, change_channel_kind, created_at, updated_at) values (?, ?, ?, 'CONCURRENT', ?, 1, ?, 'MIGRATION', ?, ?)""")) {
                insert.setObject(1, Uuids.timeOrdered(NOW));
                insert.setObject(2, employee);
                insert.setObject(3, root);
                insert.setObject(4, TODAY);
                insert.setObject(5, orgUnit);
                insert.setObject(6, at);
                insert.setObject(7, at);
                insert.executeUpdate();
            }
            return root;
        }
    }

    @Test
    @DisplayName("#711 完成标准 9（M5：同步只动主任职）：新同步进来的员工恰好有一个主任职根，同步写的任职版本都挂在它上面；同步把此人的主任职调到另一单元后，原先开着的兼职版本仍开着、单元不变，仍只有一个主任职根，主任职的开放版本在新单元")
    void theSyncTouchesOnlyThePrimaryRoot() throws Exception {
        unit("C711-ROOT", null);
        unit("C711-A", "C711-ROOT");
        unit("C711-B", "C711-ROOT");
        unit("C711-C", "C711-ROOT");
        position("C711-P");
        person("c711-p", "C711-A", "C711-P");
        synchronise();
        UUID person = employee("c711-p");

        assertThat(roots(person, AssignmentRoots.PRIMARY)).as("a newly synced employee gets exactly one PRIMARY root").hasSize(1);
        UUID primary = roots(person, AssignmentRoots.PRIMARY).get(0);
        assertThat(select("select assignment_id, assignment_kind from assignment_versions where employee_id = ?", person))
                .isNotEmpty().allSatisfy(row -> assertThat(row).containsExactly(primary, AssignmentRoots.PRIMARY));
        UUID concurrent = concurrentPost(person, unitId("C711-C"));

        moveTo("c711-p", "C711-B");
        synchronise();

        assertThat(roots(person, AssignmentRoots.PRIMARY)).containsExactly(primary);
        assertThat(select("select org_unit_id from assignment_versions where assignment_id = ? and effective_to_date is null and superseded_at is null", concurrent))
                .as("the concurrent post stays open where it was").containsExactly(List.of(unitId("C711-C")));
        assertThat(select("select org_unit_id from assignment_versions where assignment_id = ? and effective_to_date is null and superseded_at is null", primary))
                .as("the primary moved").containsExactly(List.of(unitId("C711-B")));
    }

    @Test
    @DisplayName("#711 完成标准 9：同步再跑一遍（来源无变化）不给已有员工再建主任职根，兼职根不受影响")
    void aRepeatedSyncCreatesNoSecondRoot() throws Exception {
        unit("C711-R2", null);
        unit("C711-D", "C711-R2");
        position("C711-Q");
        person("c711-q", "C711-D", "C711-Q");
        synchronise();
        UUID person = employee("c711-q");
        UUID concurrent = concurrentPost(person, unitId("C711-R2"));

        synchronise();

        assertThat(roots(person, AssignmentRoots.PRIMARY)).hasSize(1);
        assertThat(roots(person, AssignmentRoots.CONCURRENT)).containsExactly(concurrent);
    }

    @Autowired
    private OrgDirectory directory;

    @Test
    @DisplayName("#711 完成标准 9a（one-design 裁定 #711 comment 6590431312）：登录未命中时按组织源登记新员工：恰好一个主任职根、它的任职版本都挂在其上，没有兼职根")
    void registrationOnALoginMissCreatesOnePrimaryRoot() throws Exception {
        unit("C711-R3", null);
        unit("C711-E", "C711-R3");
        position("C711-S");
        person("c711-anchor", "C711-E", "C711-S");
        synchronise();
        UUID unitId = unitId("C711-E");
        UUID positionId = (UUID) select("select id from positions where code = ?", "C711-S").get(0).get(0);
        UUID newcomer = UUID.randomUUID();
        Mockito.when(orgSource.findStaffByLogin(ArgumentMatchers.any())).thenReturn(List.of(new SourcePerson(new OwnObjectId(newcomer), "C711-NEW",
                "Synthetic newcomer", new OwnObjectId(unitId), new OwnObjectId(positionId), null, "c711-newcomer", "c711-newcomer@example.test",
                SourceGender.FEMALE, new WecomUserId("c711-wx-newcomer"), false)));

        // A sign-in runs inside a request's work context (correlation id), as the real login does.
        try (WorkContext.Scope login = WorkContext.enter(UUID.randomUUID(), null, "platform.identity.login")) {
            assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("c711-wx-newcomer")))).as("registered on the login miss").isPresent();
        }

        UUID registered = (UUID) select("select id from employees where employee_code = ?", "C711-NEW").get(0).get(0);
        assertThat(roots(registered, AssignmentRoots.PRIMARY)).hasSize(1);
        assertThat(roots(registered, AssignmentRoots.CONCURRENT)).isEmpty();
        UUID primary = roots(registered, AssignmentRoots.PRIMARY).get(0);
        assertThat(select("select assignment_id, assignment_kind from assignment_versions where employee_id = ?", registered))
                .isNotEmpty().allSatisfy(row -> assertThat(row).containsExactly(primary, AssignmentRoots.PRIMARY));
    }
}
