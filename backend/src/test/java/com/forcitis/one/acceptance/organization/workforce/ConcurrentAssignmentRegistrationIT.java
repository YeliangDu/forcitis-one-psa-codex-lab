package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.acceptance.DataScopeSupport;
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
 * #711 acceptance part, done-when 9a (one-design ruling on #711 comment 6590431312; DD revision 2 section 4.3 M5): registration on a login
 * miss, the writer of assignment versions that remains after #934 removed the periodic sync, creates exactly one PRIMARY root with its version
 * and no CONCURRENT root. The periodic-sync cases of done-when 9 were dropped with the sync, as that ruling says; 9b is
 * ConcurrentAssignmentMigrationIT. Units and posts come from the fixture's seeded organisation; the newcomer comes from the organisation
 * source double's login lookup. Every key and unit code here is new per test (#1038).
 */
class ConcurrentAssignmentRegistrationIT extends DataScopeSupport {

    static final TestDatabase postgres = TestPostgres.database(ConcurrentAssignmentRegistrationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

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




    @Autowired
    private OrgDirectory directory;

    @Test
    @DisplayName("#711 完成标准 9a（one-design 裁定 #711 comment 6590431312）：登录未命中时按组织源登记新员工：恰好一个主任职根、它的任职版本都挂在其上，没有兼职根")
    void registrationOnALoginMissCreatesOnePrimaryRoot() throws Exception {
        unit("C711-R3", null);
        unit("C711-E", "C711-R3");
        position("C711-S");
        person("c711-anchor", "C711-E", "C711-S");
        seedOrganisation();
        UUID unitId = unitId("C711-E");
        UUID positionId = (UUID) select("select id from positions where code = ?", "C711-S").get(0).get(0);
        UUID newcomer = UUID.randomUUID();
        Mockito.when(orgSource.findStaffByLogin(ArgumentMatchers.any())).thenReturn(List.of(new SourcePerson(new OwnObjectId(newcomer), "C711-NEW",
                "Synthetic newcomer", new OwnObjectId(unitId), new OwnObjectId(positionId), null, "c711-newcomer", "c711-newcomer@example.test",
                SourceGender.FEMALE, new WecomUserId("c711-wx-newcomer"), false)));

        // A sign-in runs inside a request's work context: a correlation id and no system actor, as the real login request has.
        try (WorkContext.Scope login = WorkContext.enter(UUID.randomUUID(), null, null)) {
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
