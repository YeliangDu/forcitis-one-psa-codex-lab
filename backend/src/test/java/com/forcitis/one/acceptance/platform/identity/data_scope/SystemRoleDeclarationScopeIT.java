package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.OneApplication;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.DeclaredRole;
import com.forcitis.one.platform.identity.domain.RoleDefinition;
import com.forcitis.one.platform.identity.domain.RoleStatus;
import com.forcitis.one.platform.identity.domain.SystemRoleDeclaration;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * F-1 card (#728), constraint table row 5, "系统角色声明对账（启动失败）" (ADR-0044 section 1, F1-01): the start-up reconciliation of the
 * system role declaration refuses a role that holds a two-person permission and lists a custom org unit with descendants, and the start
 * stops with nothing written for that role. The declaration is the seam SystemRoleReconciliation documents: a SystemRoleDeclaration bean
 * replaces SystemRoleDeclaration.CURRENT, here CURRENT plus one more role. The application starts inside this test (no profile, the test
 * defaults), on a container of its own, and the start is observed from outside: it either comes up or throws.
 * <p>
 * Assumed contract (F1_728_ACCEPTANCE.md): RoleDefinition carries the custom org units as a collection of a record that holds the org unit
 * id (a UUID or a record around one) and a boolean "descendants included". The test finds that member by its shape, so the name the
 * implementation chooses is free; a RoleDefinition without such a member fails the tests with a message that says so.
 */
class SystemRoleDeclarationScopeIT {

    private static final String SENSITIVE = "platform.audit:audit-entry:read";
    private static final String ORDINARY = "organization.workforce:org-unit:read";

    private static final PostgreSQLContainer POSTGRES = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    @BeforeAll
    static void aDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void noDatabase() {
        POSTGRES.stop();
    }

    /** What a start on the container comes to: the application up (and closed again) or the failure that stopped it. */
    private record Start(Throwable failure) {
        boolean up() {
            return failure == null;
        }

        String story() {
            StringBuilder story = new StringBuilder();
            for (Throwable t = failure; t != null; t = t.getCause()) {
                story.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
                if (t.getCause() == t) {
                    break;
                }
            }
            return story.toString();
        }
    }

    private static Start start(SystemRoleDeclaration declaration) {
        ConfigurableApplicationContext context = null;
        try {
            context = new SpringApplicationBuilder(OneApplication.class)
                    .initializers(initializing -> ((GenericApplicationContext) initializing).registerBean("acceptanceSystemRoleDeclaration", SystemRoleDeclaration.class, () -> declaration))
                    .properties("spring.datasource.url=" + POSTGRES.getJdbcUrl(), "spring.datasource.username=" + POSTGRES.getUsername(),
                            "spring.datasource.password=" + POSTGRES.getPassword(), "server.port=0", "flowable.async-executor-activate=false")
                    .run();
            return new Start(null);
        } catch (Throwable failure) {
            return new Start(failure);
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    // ---- the declaration, with the custom org units of the assumed contract -------------------------------------------------------

    /** CURRENT and one more system role (revision 1) of the scope kind, the permissions and one custom org unit. */
    private static SystemRoleDeclaration declaration(UUID roleId, String code, Set<String> permissions, UUID orgUnit, boolean descendants) {
        List<DeclaredRole> roles = new ArrayList<>(SystemRoleDeclaration.CURRENT.roles());
        roles.add(new DeclaredRole(new RoleId(roleId), new RoleCode(code), false, 1, definition("Synthetic system role " + code, permissions, orgUnit, descendants)));
        return new SystemRoleDeclaration(roles);
    }

    private static RoleDefinition definition(String name, Set<String> permissions, UUID orgUnit, boolean descendants) {
        RecordComponent[] components = RoleDefinition.class.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        boolean unitsPlaced = false;
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            switch (components[i].getName()) {
                case "name" -> arguments[i] = name;
                case "status" -> arguments[i] = RoleStatus.ENABLED;
                case "accessScope" -> arguments[i] = AccessScopeKind.CUSTOM_ORG_UNITS;
                case "permissionCodes" -> arguments[i] = permissions;
                default -> {
                    arguments[i] = customOrgUnits(components[i], orgUnit, descendants);
                    unitsPlaced |= arguments[i] != null;
                }
            }
        }
        assertThat(unitsPlaced).as("F-1 假定契约：RoleDefinition 增加一个成员存放自定义部门（部门 ID 与「含下级」布尔值构成的元素的集合），现有成员："
                + List.of(components)).isTrue();
        try {
            Constructor<RoleDefinition> constructor = RoleDefinition.class.getDeclaredConstructor(types);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    /** A collection of one element built from the org unit and the flag, or null when the component is not such a collection. */
    private static Object customOrgUnits(RecordComponent component, UUID orgUnit, boolean descendants) {
        if (!Collection.class.isAssignableFrom(component.getType()) || !(component.getGenericType() instanceof ParameterizedType generic)
                || !(generic.getActualTypeArguments()[0] instanceof Class<?> element) || !element.isRecord()) {
            return null;
        }
        RecordComponent[] parts = element.getRecordComponents();
        Class<?>[] types = new Class<?>[parts.length];
        Object[] arguments = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            if (types[i] == boolean.class || types[i] == Boolean.class) {
                arguments[i] = descendants;
            } else if (types[i] == UUID.class) {
                arguments[i] = orgUnit;
            } else if (types[i] == String.class) {
                arguments[i] = orgUnit.toString();
            } else if (types[i].isRecord() && types[i].getRecordComponents().length == 1 && types[i].getRecordComponents()[0].getType() == UUID.class) {
                try {
                    arguments[i] = types[i].getDeclaredConstructor(UUID.class).newInstance(orgUnit);
                } catch (ReflectiveOperationException unexpected) {
                    throw new AssertionError(unexpected);
                }
            } else {
                return null;
            }
        }
        try {
            Object one = element.getDeclaredConstructor(types).newInstance(arguments);
            return Set.class.isAssignableFrom(component.getType()) ? Set.of(one) : List.of(one);
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static long rolesWithId(UUID roleId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement select = connection.prepareStatement("select count(*) from platform_identity.roles where role_id = ?")) {
            select.setObject(1, roleId);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ---- the tests ------------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("F-1 不变量 5 / ADR-0044 第 1 项「启动对账」：系统角色声明里含双人逐人级权限的角色，自定义部门选「含下级」，应用启动失败，库里没有该角色")
    void aDeclaredTwoPersonRoleWithDescendantsStopsTheStart() throws SQLException {
        UUID roleId = UUID.fromString("019a0000-0000-7000-8000-0000000f1a01");

        Start start = start(declaration(roleId, "f1_declared_below", Set.of(SENSITIVE), UUID.randomUUID(), true));

        assertThat(start.up()).as("the start must stop; it came up").isFalse();
        assertThat(rolesWithId(roleId)).as("nothing was written for the refused role: " + start.story()).isZero();
    }

    @Test
    @DisplayName("F-1 不变量 5 对照：同一个双人逐人级系统角色，自定义部门全部「仅本部门」，应用照常启动，角色写入")
    void aDeclaredTwoPersonRoleWithUnitsOnlyStarts() throws SQLException {
        UUID roleId = UUID.fromString("019a0000-0000-7000-8000-0000000f1a02");

        Start start = start(declaration(roleId, "f1_declared_units_only", Set.of(SENSITIVE), UUID.randomUUID(), false));

        assertThat(start.up()).as(start.story()).isTrue();
        assertThat(rolesWithId(roleId)).isEqualTo(1);
    }

    @Test
    @DisplayName("F-1 不变量 5 对照：不含双人逐人级权限的系统角色，自定义部门选「含下级」是允许的，应用照常启动（限制只针对双人逐人级角色）")
    void aDeclaredOrdinaryRoleWithDescendantsStarts() throws SQLException {
        UUID roleId = UUID.fromString("019a0000-0000-7000-8000-0000000f1a03");

        Start start = start(declaration(roleId, "f1_declared_ordinary_below", Set.of(ORDINARY), UUID.randomUUID(), true));

        assertThat(start.up()).as(start.story()).isTrue();
        assertThat(rolesWithId(roleId)).isEqualTo(1);
    }
}
