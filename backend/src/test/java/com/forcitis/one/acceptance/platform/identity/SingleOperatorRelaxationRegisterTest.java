package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.JavaCode;
import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The closed list of relaxations and the registers that must know the new audit actions. The enum is reached by reflection, in the api, domain or
 * application package of platform.identity, so that this class compiles before it exists and fails with a message that names where it looked.
 */
class SingleOperatorRelaxationRegisterTest {

    static final String TYPE = "SingleOperatorRelaxation";
    static final List<String> PACKAGES = List.of("com.forcitis.one.platform.identity.api", "com.forcitis.one.platform.identity.domain",
            "com.forcitis.one.platform.identity.application");
    static final Set<String> TABLE_ROWS_SO_FAR = Set.of("WITNESS_OMITTED", "EMERGENCY_ACCOUNT_MINIMUM_ONE", "SUPER_ADMIN_MINIMUM_ONE");
    static final String FOURTH_IN_P3 = "TEMPLATE_VERSION_WITHOUT_AUTHOR";
    static final Path MAIN = Path.of("src/main/java");
    static final Path IDENTITY = MAIN.resolve("com/forcitis/one/platform/identity");
    static final Path WRITERS = Path.of("architecture/audit-text-writers.txt");
    static final Path MUST_AUDIT = Path.of("../docs/architecture/must-audit-actions.yaml");
    static final String APPLIED = "platform.identity.single-operator-mode.applied";
    static final Pattern NEW_ENTRY_WITH_APPLIED = Pattern.compile("new\\s+AuditEntry\\s*\\(\\s*\"[^\"]*single-operator-mode\\.applied\"");

    static Class<?> relaxationType() {
        for (String pack : PACKAGES) {
            try {
                return Class.forName(pack + "." + TYPE);
            } catch (ClassNotFoundException notHere) {
                continue;
            }
        }
        throw new AssertionError("the enum of the relaxations is expected as " + TYPE + " in one of " + PACKAGES);
    }

    static Set<String> constants(Class<?> type) {
        assertThat(type.isEnum()).as(TYPE + " is an enum").isTrue();
        Set<String> names = new TreeSet<>();
        for (Object constant : type.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return names;
    }

    static List<String> differencesFromTheTable(Set<String> actual) {
        List<String> problems = new ArrayList<>();
        for (String expected : TABLE_ROWS_SO_FAR) {
            if (!actual.contains(expected)) {
                problems.add("missing " + expected);
            }
        }
        for (String found : actual) {
            if (!TABLE_ROWS_SO_FAR.contains(found)) {
                problems.add("unexpected " + found);
            }
        }
        return problems;
    }

    @Test
    @DisplayName("SINGLE-OP P2-20 放宽项枚举恰好三个取值，与 ADR-0030 第 2 项表前三行对应（见证人省略、应急账号下限一个、超级管理员下限一个）；第四个（模板版本无作者）在 P3 之前不得出现")
    void p220_theEnumHasExactlyTheThreeValuesOfTheTableSoFar() {
        Set<String> actual = constants(relaxationType());

        assertThat(differencesFromTheTable(actual)).as("the closed list: %s", actual).isEmpty();
        assertThat(actual).as("the fourth value is added in P3, together with this guard").doesNotContain(FOURTH_IN_P3);
    }

    @Test
    @DisplayName("SINGLE-OP P2-20 守护不是空转：多一个、少一个、改名的取值集合都被报出")
    void p220_theComparisonReportsAnAddedMissingOrRenamedValue() {
        Set<String> added = new TreeSet<>(TABLE_ROWS_SO_FAR);
        added.add(FOURTH_IN_P3);
        Set<String> missing = new TreeSet<>(TABLE_ROWS_SO_FAR);
        missing.remove("WITNESS_OMITTED");
        Set<String> renamed = new TreeSet<>(TABLE_ROWS_SO_FAR);
        renamed.remove("SUPER_ADMIN_MINIMUM_ONE");
        renamed.add("SUPER_ADMINISTRATOR_MINIMUM_ONE");

        assertThat(differencesFromTheTable(TABLE_ROWS_SO_FAR)).isEmpty();
        assertThat(differencesFromTheTable(added)).containsExactly("unexpected " + FOURTH_IN_P3);
        assertThat(differencesFromTheTable(missing)).containsExactly("missing WITNESS_OMITTED");
        assertThat(differencesFromTheTable(renamed)).containsExactly("missing SUPER_ADMIN_MINIMUM_ONE", "unexpected SUPER_ADMINISTRATOR_MINIMUM_ONE");
    }

    static Map<String, String> writersOfTheMark() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(MAIN)) {
            for (Path file : walk.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String source = JavaCode.withoutComments(Files.readString(file));
                if (NEW_ENTRY_WITH_APPLIED.matcher(source).find()) {
                    files.put(file.toString().replace('\\', '/'), source);
                }
            }
        }
        return files;
    }

    private static Class<?> classOf(String path) throws ClassNotFoundException {
        String name = path.substring("src/main/java/".length(), path.length() - ".java".length()).replace('/', '.');
        return Class.forName(name);
    }

    @Test
    @DisplayName("SINGLE-OP P2-20 每个标注写入点只接受该枚举，不接受自由文本：写标注审计的类引用放宽项枚举，且没有任何方法或构造器带名为 relaxation、detail、reason 的 String 参数")
    void p220_everyMarkWriterTakesTheEnumNotText() throws Exception {
        Map<String, String> writers = writersOfTheMark();

        assertThat(writers).as("at least one main class writes %s with the action as a literal", APPLIED).isNotEmpty();
        for (Map.Entry<String, String> writer : writers.entrySet()) {
            assertThat(writer.getValue()).as("%s names %s", writer.getKey(), TYPE).contains(TYPE);
            Class<?> declaring = classOf(writer.getKey());
            List<Executable> executables = new ArrayList<>(Arrays.<Executable>asList(declaring.getDeclaredMethods()));
            executables.addAll(Arrays.<Executable>asList(declaring.getDeclaredConstructors()));
            for (Executable executable : executables) {
                for (Parameter parameter : executable.getParameters()) {
                    boolean freeText = parameter.getType() == String.class && parameter.getName().matches("(?i).*(relax|detail|reason).*");
                    assertThat(freeText).as("%s.%s takes free text %s", declaring.getSimpleName(), executable.getName(), parameter.getName()).isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("SINGLE-OP P2-21 新增审计动作 platform.identity.single-operator-mode.applied 与 platform.identity.operations.account.role.revoked 已登记在 MUST_AUDIT_ACTIONS.yaml（类别「权限与角色」，不是 planned）")
    void p221_newActionsAreInTheMustAuditRegister() throws IOException {
        String register = Files.readString(MUST_AUDIT);

        for (String action : List.of(APPLIED, "platform.identity.operations.account.role.revoked")) {
            assertThat(register).as(action).containsPattern("(?m)^\\s*- " + Pattern.quote(action) + "\\s*$");
        }
    }

    static Map<String, List<String>> registeredWriters() throws IOException {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(WRITERS)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            rows.put(parts[0].strip(), Arrays.stream(parts[2].split(",")).map(String::strip).toList());
        }
        return rows;
    }

    @Test
    @DisplayName("SINGLE-OP P2-21 audit-text-writers.txt：RevokeRoleByOperations 登记在允许内容表行 15；写标注审计的每个主代码文件登记在行 21（放宽项标注写入器）")
    void p221_writersAreRegisteredAgainstTheRowsOfTheContentTable() throws IOException {
        Map<String, List<String>> registered = registeredWriters();

        assertThat(Files.exists(IDENTITY.resolve("application/RevokeRoleByOperations.java"))).as("RevokeRoleByOperations is where its sibling GrantRoleByOperations is").isTrue();
        assertThat(registered.get("src/main/java/com/forcitis/one/platform/identity/application/RevokeRoleByOperations.java")).containsExactly("15");
        Map<String, String> markWriters = writersOfTheMark();
        assertThat(markWriters).isNotEmpty();
        for (String file : markWriters.keySet()) {
            assertThat(registered.get(file)).as("%s is registered", file).isNotNull().contains("21");
        }
    }

    @Test
    @DisplayName("SINGLE-OP P2-10 模式关闭时既有运维命令用例原样重跑：三个既有测试类仍在原处（被删除即失败）；它们本身随全量后端测试执行，不改断言")
    void p210_theExistingOperationsTestsStayWhereTheyAre() {
        for (String file : List.of("platform/identity/OperationsCommandsIntegrationTest.java", "platform/identity/application/OperationAttestationTest.java",
                "acceptance/platform/identity/OperationsCommandEmployeeIdIT.java")) {
            assertThat(Files.exists(Path.of("src/test/java/com/forcitis/one").resolve(file))).as(file).isTrue();
        }
    }

    @Test
    @DisplayName("SINGLE-OP P2-20 SingleOperatorMarks 的全部方法与构造器没有任何 String 参数：枚举参数以外不接受自由文本，且至少一个方法收放宽项枚举")
    void p220_theMarkWriterTakesNoStringAtAll() throws Exception {
        Class<?> marks = Class.forName("com.forcitis.one.platform.identity.application.SingleOperatorMarks");
        Class<?> relaxation = relaxationType();
        List<Executable> executables = new ArrayList<>(Arrays.<Executable>asList(marks.getDeclaredMethods()));
        executables.addAll(Arrays.<Executable>asList(marks.getDeclaredConstructors()));
        boolean takesTheEnum = false;
        for (Executable executable : executables) {
            if (executable instanceof java.lang.reflect.Method method && method.isSynthetic()) {
                continue;
            }
            for (Class<?> type : executable.getParameterTypes()) {
                assertThat(type).as("%s.%s", marks.getSimpleName(), executable.getName()).isNotEqualTo(String.class).isNotEqualTo(CharSequence.class);
                takesTheEnum |= type == relaxation;
            }
        }
        assertThat(takesTheEnum).isTrue();
    }
}
