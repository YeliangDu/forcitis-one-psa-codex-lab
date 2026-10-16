package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.AcceptanceSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P3b, ADR-0033 guard 7 and section 6 item 1 (EmployeeCodeNotKeyTest of the card): the employee code is an attribute. No primary
 * key, unique constraint, unique index, foreign key or exclusion constraint of any schema has a column named employee_code, and no
 * repository or query interface of the main sources fetches a single object by it. That two people with the same code both get their
 * entry is in SourceIdMatchingIT, through the real sync.
 */
@Testcontainers
class EmployeeCodeNotKeyIT extends AcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    static final String EMPLOYEE_CODE = "employee_code";
    static final Path MAIN = Path.of("src/main/java/com/forcitis/one");
    static final Pattern BY_EMPLOYEE_CODE = Pattern.compile("\\b\\w*(?:By|For|With)EmployeeCode\\w*\\s*\\(");

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 7：employee_code 不出现在任何主键、唯一约束、外键、排他约束、唯一索引中（只保留普通索引）")
    void theEmployeeCodeIsInNoKey() throws Exception {
        List<String> keys = new ArrayList<>();
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
                     where i.indisunique and a.attname = ?""")) {
                select.setString(1, EMPLOYEE_CODE);
                select.setString(2, EMPLOYEE_CODE);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        keys.add(rs.getString(1));
                    }
                }
            }
            try (PreparedStatement select = catalogue.prepareStatement("""
                    select count(*) from information_schema.columns
                     where table_schema = 'organization_workforce' and table_name = 'employees' and column_name = ?""")) {
                select.setString(1, EMPLOYEE_CODE);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).as("the employees table still has the code as an attribute").isEqualTo(1);
                }
            }
        }

        assertThat(keys).as("keys over employee_code").isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 7：主代码中没有按员工编号取对象的方法（findByEmployeeCode、byEmployeeCode 之类）")
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
    @DisplayName("R-1b P3b ADR-0033 守护 7（注入）：方法名检查能认出 findByEmployeeCode、employeeForEmployeeCode，不误认 employeeCode() 读属性")
    void theMethodCheckCatchesALookup() {
        assertThat(BY_EMPLOYEE_CODE.matcher("Optional<Employee> findByEmployeeCode(String code);").find()).isTrue();
        assertThat(BY_EMPLOYEE_CODE.matcher("EmployeeId employeeForEmployeeCode(String code) {").find()).isTrue();
        assertThat(BY_EMPLOYEE_CODE.matcher("String code = employee.employeeCode();").find()).isFalse();
    }
}
