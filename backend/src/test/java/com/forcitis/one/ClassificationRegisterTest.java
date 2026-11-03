package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The register parser and name rules of ADR-0043 each have an injection that goes red when the rule is removed. */
class ClassificationRegisterTest {

    private static final Path NO_ROOT = Path.of("no-such-root");

    private static void rejects(String line, String... messages) {
        assertThatThrownBy(() -> ClassificationRegister.parse(List.of(line), NO_ROOT)).isInstanceOf(AssertionError.class).hasMessageContainingAll(messages);
    }

    @Test
    @DisplayName("ADR-0043 item 2: a three-segment line, an unknown level or mark, a | in the reason, an empty reason and a repeated column are each reported with the line")
    void malformedLinesAreReported() {
        rejects("a.b.c | INTERNAL | 用途：x", "line 1 has 3 segments");
        rejects("a.b.c | INTERNAL | PI | 用途：x | y", "line 1 has 5 segments");
        rejects("a.b.c | CONFIDENTIAL | NONE | x", "level 'CONFIDENTIAL'");
        rejects("a.b.c | INTERNAL | SECRET | x", "mark 'SECRET'");
        rejects("a.b.c | INTERNAL | NONE | ", "no reason");
        assertThatThrownBy(() -> ClassificationRegister.parse(List.of("a.b.c | INTERNAL | NONE | x", "a.b.c | INTERNAL | NONE | y"), NO_ROOT)).hasMessageContaining("registered twice");
    }

    @Test
    @DisplayName("ADR-0043 item 1.3, R-1: SPI below SENSITIVE, and a PI or SPI reason that does not start with the purpose, are refused")
    void rowRulesAreEnforced() {
        rejects("a.b.c | INTERNAL | SPI | 用途：x docs/privacy/x.md", "below SENSITIVE");
        rejects("a.b.c | INTERNAL | PI | 个人信息：x", "starts with '用途：'");
        rejects("a.b.c | SENSITIVE | SPI | 个人信息：x", "starts with '用途：'");
        assertThat(ClassificationRegister.parse(List.of("a.b.c | INTERNAL | PI | 用途：x", "a.b.d | INTERNAL | NONE | x"), NO_ROOT)).hasSize(2);
    }

    @Test
    @DisplayName("ADR-0043 R-4: an SPI reason must name an impact assessment report that exists")
    void spiNeedsAnExistingReport(@TempDir Path root) throws IOException {
        String line = "a.b.c | SENSITIVE | SPI | 用途：x；评估 docs/privacy/id-number.md";
        rejects(line, "impact assessment report");
        assertThatThrownBy(() -> ClassificationRegister.parse(List.of(line), root)).hasMessageContaining("impact assessment report");
        Files.createDirectories(root.resolve("docs/privacy"));
        Files.writeString(root.resolve("docs/privacy/id-number.md"), "report");
        assertThat(ClassificationRegister.parse(List.of(line), root)).containsKey("a.b.c");
    }

    @Test
    @DisplayName("ADR-0043 guard rules 1 and 2: names that look sensitive need SENSITIVE or higher, names of sensitive personal information need SPI, pay needs a mark, stale rows are found")
    void nameRulesAreEnforced() {
        Map<String, ClassificationRegister.Row> register = Map.of(
                "s.t.bank_account", new ClassificationRegister.Row("SENSITIVE", "PI", "用途：x"),
                "s.t.passport_no", new ClassificationRegister.Row("INTERNAL", "SPI", "x"),
                "s.t.salary", new ClassificationRegister.Row("SENSITIVE", "NONE", "x"),
                "s.t.gone", new ClassificationRegister.Row("INTERNAL", "NONE", "x"));
        List<String> problems = ClassificationRegister.problems(Set.of("s.t.bank_account", "s.t.passport_no", "s.t.salary", "s.t.sick_leave_days", "s.t.title"), register);
        assertThat(problems).anyMatch(p -> p.startsWith("s.t.bank_account") && p.contains("mark it SPI"))
                .anyMatch(p -> p.startsWith("s.t.passport_no") && p.contains("SENSITIVE or RESTRICTED"))
                .anyMatch(p -> p.startsWith("s.t.salary") && p.contains("PI or SPI"))
                .anyMatch(p -> p.startsWith("s.t.sick_leave_days"))
                .anyMatch(p -> p.startsWith("s.t.gone") && p.contains("does not exist"))
                .noneMatch(p -> p.startsWith("s.t.title"));
    }

    @Test
    @DisplayName("ADR-0043 D-6: email and gender need the mark PI, not a level; an exemption row is NONE with a reason that starts with the exemption word")
    void personalNamesNeedAMarkNotALevel() {
        assertThat(ClassificationRegister.PERSON_NAME.matcher("email").find()).isTrue();
        assertThat(ClassificationRegister.SENSITIVE_NAME.matcher("email").find()).isFalse();
        assertThat(new ClassificationRegister.Row("SENSITIVE", "NONE", "豁免：代理键").exempt()).isTrue();
        assertThat(new ClassificationRegister.Row("SENSITIVE", "NONE", "x").exempt()).isFalse();
    }

    @Test
    @DisplayName("#980 ADR-0043 :202: the class word of an email column is not a physical address: email_address needs only INTERNAL with the mark PI; every other address stays sensitive")
    void anEmailAddressColumnIsNotAPhysicalAddress() {
        Map<String, ClassificationRegister.Row> register = Map.of(
                "x.t.email_address", new ClassificationRegister.Row("INTERNAL", "PI", "用途：x"),
                "x.t.base_email_address", new ClassificationRegister.Row("INTERNAL", "PI", "用途：x"),
                "x.t.home_address", new ClassificationRegister.Row("INTERNAL", "PI", "用途：x"),
                "x.t.last_login_address", new ClassificationRegister.Row("INTERNAL", "PI", "用途：x"),
                "x.t.email_address_hash", new ClassificationRegister.Row("INTERNAL", "PI", "用途：x"),
                "x.t.voicemail_address", new ClassificationRegister.Row("INTERNAL", "PI", "用途：x"),
                "x.t.remail_address", new ClassificationRegister.Row("INTERNAL", "PI", "用途：x"));
        List<String> problems = ClassificationRegister.problems(
                Set.of("x.t.email_address", "x.t.base_email_address", "x.t.home_address", "x.t.last_login_address", "x.t.email_address_hash", "x.t.voicemail_address", "x.t.remail_address"), register);
        assertThat(problems).noneMatch(p -> p.startsWith("x.t.email_address ") || p.startsWith("x.t.base_email_address"))
                .anyMatch(p -> p.startsWith("x.t.home_address") && p.contains("SENSITIVE or RESTRICTED"))
                .anyMatch(p -> p.startsWith("x.t.last_login_address") && p.contains("SENSITIVE or RESTRICTED"))
                .anyMatch(p -> p.startsWith("x.t.email_address_hash") && p.contains("SENSITIVE or RESTRICTED"))
                .anyMatch(p -> p.startsWith("x.t.voicemail_address") && p.contains("SENSITIVE or RESTRICTED"))
                .anyMatch(p -> p.startsWith("x.t.remail_address") && p.contains("SENSITIVE or RESTRICTED"));
        assertThat(ClassificationRegister.PERSON_NAME.matcher("email_address").find()).as("the mark PI stays required (DataArchitectureTest D5)").isTrue();
    }

    @Test
    @DisplayName("the real register parses with the strict four-segment rules")
    void theRealRegisterParses() throws IOException {
        assertThat(ClassificationRegister.read()).isNotEmpty();
    }
}
