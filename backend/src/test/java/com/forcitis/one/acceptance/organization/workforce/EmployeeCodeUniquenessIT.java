package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR-0078 (replaces the unique-key part of ADR-0033 guard 7): the employee code is unique over all employees, including the separated,
 * and is fixed once issued. The only key over it is uk_employees_employee_code, a single-column unique constraint without a condition;
 * no primary key, foreign key, exclusion constraint, other unique constraint or unique index has the column, and no repository or query
 * interface of the main sources fetches a single object by it. A second person with a code already issued is isolated by the sync, which
 * is in SourceIdMatchingIT.
 */
class EmployeeCodeUniquenessIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(EmployeeCodeUniquenessIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String EMPLOYEE_CODE = "employee_code";
    static final Path MAIN = Path.of("src/main/java/com/forcitis/one");
    static final Pattern BY_EMPLOYEE_CODE = Pattern.compile("\\b\\w*(?:By|For|With)EmployeeCode\\w*\\s*\\(");

    @Test
    @DisplayName("ADR-0078 守护：employee_code 上的键恰为 uk_employees_employee_code 一项，且是只含该列、无条件的唯一约束")
    void theEmployeeCodeIsInExactlyTheOneUniqueKey() throws Exception {
        List<String> keys = new ArrayList<>();
        String definition;
        try (Connection catalogue = schemaConnection("public")) {
            try (PreparedStatement select = catalogue.prepareStatement("""
                    select n.nspname || '.' || t.relname || ' ' || c.conname || ' (' || c.contype::text || ')'
                      from pg_constraint c
                      join pg_class t on t.oid = c.conrelid
                      join pg_namespace n on n.oid = t.relnamespace
                      join pg_attribute a on a.attrelid = t.oid and a.attnum = any (c.conkey)
                     where c.contype in ('p', 'u', 'f', 'x') and a.attname = ?
                    union
                    select n.nspname || '.' || t.relname || ' ' || x.relname || ' (unique index)'
                      from pg_index i
                      join pg_class x on x.oid = i.indexrelid
                      join pg_class t on t.oid = i.indrelid
                      join pg_namespace n on n.oid = t.relnamespace
                      join pg_attribute a on a.attrelid = t.oid and a.attnum = any (i.indkey)
                     where i.indisunique and a.attname = ?
                       and not exists (select 1 from pg_constraint k where k.conindid = i.indexrelid and k.contype in ('p', 'u', 'x'))""")) {
                select.setString(1, EMPLOYEE_CODE);
                select.setString(2, EMPLOYEE_CODE);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        keys.add(rs.getString(1));
                    }
                }
            }
            try (PreparedStatement select = catalogue.prepareStatement("""
                    select pg_get_constraintdef(c.oid), pg_get_indexdef(c.conindid)
                      from pg_constraint c
                     where c.conrelid = 'organization_workforce.employees'::regclass and c.conname = 'uk_employees_employee_code'""")) {
                try (ResultSet rs = select.executeQuery()) {
                    assertThat(rs.next()).as("the key exists").isTrue();
                    definition = rs.getString(1) + " / " + rs.getString(2);
                }
            }
        }

        assertThat(keys).as("keys over employee_code")
                .containsExactly("organization_workforce.employees uk_employees_employee_code (u)");
        assertThat(definition).as("single column, no condition")
                .isEqualTo("UNIQUE (employee_code) / CREATE UNIQUE INDEX uk_employees_employee_code ON organization_workforce.employees USING btree (employee_code)");
    }

    @Test
    @DisplayName("ADR-0078 守护：同工号插入第二个员工失败，已离职员工的工号也算")
    void aSecondEmployeeWithAnIssuedCodeIsRefused() throws Exception {
        try (Connection c = schemaConnection("organization_workforce")) {
            c.setAutoCommit(false);
            try (var statement = c.createStatement()) {
                statement.execute("insert into employees (id, employee_code, display_name, employment_status, separated_at) "
                        + "values (uuidv7(), 'UNIQ-GONE', 'Gone', 'SEPARATED', now())");
                statement.execute("insert into employees (id, employee_code, display_name, employment_status) values (uuidv7(), 'UNIQ-LIVE', 'Live', 'EMPLOYED')");
                assertThatThrownBy(() -> statement.execute("insert into employees (id, employee_code, display_name, employment_status) "
                        + "values (uuidv7(), 'UNIQ-GONE', 'Back', 'EMPLOYED')")).hasMessageContaining("uk_employees_employee_code");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("ADR-0078 守护：直接 SQL 改 employee_code 被库层拒绝，改别的列与写回同值不受影响")
    void theIssuedCodeCannotBeChanged() throws Exception {
        try (Connection c = schemaConnection("organization_workforce")) {
            c.setAutoCommit(false);
            try (var statement = c.createStatement()) {
                statement.execute("insert into employees (id, employee_code, display_name, employment_status) values (uuidv7(), 'FIXED-1', 'Fixed', 'EMPLOYED')");
                assertThat(statement.executeUpdate("update employees set display_name = 'Renamed', employee_code = 'FIXED-1' where employee_code = 'FIXED-1'")).isEqualTo(1);
                statement.execute("savepoint before_change");
                assertThatThrownBy(() -> statement.execute("update employees set employee_code = 'FIXED-2' where employee_code = 'FIXED-1'"))
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23001"))
                        .hasMessageContaining("cannot be changed once issued");
                statement.execute("rollback to savepoint before_change");
                try (ResultSet rs = statement.executeQuery("select employee_code from employees where display_name = 'Renamed'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("FIXED-1");
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("ADR-0078 守护：主代码中没有按员工编号取对象的方法（findByEmployeeCode、byEmployeeCode 之类）")
    void nothingFetchesByTheEmployeeCode() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                Matcher method = BY_EMPLOYEE_CODE.matcher(Files.readString(file));
                while (method.find()) {
                    found.add(MAIN.relativize(file) + ": " + method.group());
                }
            }
        }

        assertThat(found).as("methods that look an object up by the employee code").isEmpty();
    }

    @Test
    @DisplayName("ADR-0078 守护（注入）：方法名检查能认出 findByEmployeeCode、employeeForEmployeeCode，不误认 employeeCode() 读属性")
    void theMethodCheckCatchesALookup() {
        assertThat(BY_EMPLOYEE_CODE.matcher("Optional<Employee> findByEmployeeCode(String code);").find()).isTrue();
        assertThat(BY_EMPLOYEE_CODE.matcher("EmployeeId employeeForEmployeeCode(String code) {").find()).isTrue();
        assertThat(BY_EMPLOYEE_CODE.matcher("String code = employee.employeeCode();").find()).isFalse();
    }
}
