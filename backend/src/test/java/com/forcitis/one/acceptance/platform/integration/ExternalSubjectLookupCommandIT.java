package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P3b, ADR-0033 section 3 item 6 and RULING-P3B-D8 / RULING-P3B-7: the read-only operations command that lists the employee ids and
 * user ids of the people an external subject (an organisation source login name or a WeCom user id) belongs to. The provider kinds are
 * external identity values and live in platform.integration only (ADR-0039 items 1 and 4), so the command's entry is there: a bean of
 * platform.integration with {@code int run(ApplicationArguments, PrintStream out, PrintStream err)}, the same shape as the operations
 * commands of identity; it is looked up at run time, and a missing one fails the test.
 */
@Testcontainers
class ExternalSubjectLookupCommandIT extends AccountsAcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    /** Command and argument names of RULING-P3B-D8 (one-glossary 2026-10-15): the subject and the kind of its identity provider. */
    static final String FIND_ACCOUNTS = "find-accounts-by-external-subject";
    static final String EXTERNAL_SUBJECT = "external-subject";
    static final String PROVIDER_KIND = "provider-kind";
    /** Values of --provider-kind (provider_kind, one-glossary 2026-10-15). */
    static final String SEEYON_LOGIN_NAME = "SEEYON_LOGIN_NAME";
    static final String WECOM_USER_ID = "WECOM_USER_ID";
    static final String INTEGRATION = "com.forcitis.one.platform.integration";

    @Autowired
    ApplicationContext context;

    UUID unit;

    record Outcome(int exit, String out, String err) {
    }

    @BeforeEach
    void aDepartment() throws SQLException {
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
    }

    private Outcome find(String providerKind, String subject) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ApplicationArguments arguments = new DefaultApplicationArguments("--operation=" + FIND_ACCOUNTS, "--" + PROVIDER_KIND + "=" + providerKind,
                "--" + EXTERNAL_SUBJECT + "=" + subject, "--operator=acceptance-operator", "--witness=acceptance-witness");
        int exit = runCommand(arguments, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Outcome(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private int runCommand(ApplicationArguments arguments, PrintStream out, PrintStream err) {
        List<String> seen = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null || !ClassUtils.getUserClass(type).getPackageName().startsWith(INTEGRATION)) {
                continue;
            }
            for (Method method : ClassUtils.getUserClass(type).getMethods()) {
                if (method.getName().equals("run") && method.getReturnType() == int.class && method.getParameterCount() == 3
                        && method.getParameterTypes()[0] == ApplicationArguments.class && method.getParameterTypes()[1] == PrintStream.class
                        && method.getParameterTypes()[2] == PrintStream.class) {
                    seen.add(name);
                    try {
                        return (int) method.invoke(context.getBean(name), arguments, out, err);
                    } catch (InvocationTargetException failure) {
                        throw new AssertionError("the command of bean " + name + " threw", failure.getCause());
                    } catch (IllegalAccessException inaccessible) {
                        throw new AssertionError(inaccessible);
                    }
                }
            }
        }
        throw new AssertionError("RULING-P3B-7: no operations command bean in " + INTEGRATION + " with int run(ApplicationArguments, PrintStream, PrintStream) "
                + seen);
    }

    /** Rows of the identity tables a command could write. */
    private List<Integer> identityRows() throws SQLException {
        List<Integer> rows = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity")) {
            for (String table : List.of("user_accounts", "account_roles", "emergency_admins", "authorization_requests")) {
                try (PreparedStatement count = identity.prepareStatement("select count(*) from " + table); ResultSet rs = count.executeQuery()) {
                    rs.next();
                    rows.add(rs.getInt(1));
                }
            }
        }
        return rows;
    }

    private void loginName(UUID employee, String loginName) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce");
             PreparedStatement update = workforce.prepareStatement("update employees set login_name = ? where id = ?")) {
            update.setString(1, loginName);
            update.setObject(2, stored(employee));
            update.executeUpdate();
        }
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 6 条 / RULING-P3B-D8：只读命令按组织源登录名返回匹配的员工 ID 与 user_id 列表（同名两人都列出），不写任何数据")
    void theReadOnlyCommandListsEveryoneWithTheLoginName() throws Exception {
        UUID first = newEmployeeId();
        UUID second = newEmployeeId();
        UUID other = newEmployeeId();
        employee(first, unit, null);
        employee(second, unit, null);
        employee(other, unit, null);
        String shared = "acceptance-login-" + serialOf(first);
        loginName(first, shared);
        loginName(second, shared);
        loginName(other, "acceptance-login-" + serialOf(other));
        UUID firstAccount = signIn(first).userId();
        List<Integer> before = identityRows();

        Outcome found = find(SEEYON_LOGIN_NAME, shared);

        assertThat(found.exit()).as(found.err()).isZero();
        assertThat(found.out()).contains(first.toString(), firstAccount.toString(), second.toString()).doesNotContain(other.toString());
        assertThat(identityRows()).as("a read-only command writes nothing").isEqualTo(before);
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 6 条 / RULING-P3B-D8：只读命令按企业微信 userid 返回员工 ID 与 user_id；查无此人时不列出任何员工")
    void theReadOnlyCommandFindsThePersonByWecomUserId() throws Exception {
        UUID person = newEmployeeId();
        UUID other = newEmployeeId();
        employee(person, unit, null);
        employee(other, unit, null);
        UUID account = signIn(person).userId();

        Outcome found = find(WECOM_USER_ID, wecomUserIdOf(person));
        Outcome nobody = find(WECOM_USER_ID, "acceptance-nobody-" + serialOf(person));

        assertThat(found.exit()).as(found.err()).isZero();
        assertThat(found.out()).contains(person.toString(), account.toString()).doesNotContain(other.toString());
        assertThat(nobody.out()).doesNotContain(person.toString()).doesNotContain(other.toString());
    }
}
