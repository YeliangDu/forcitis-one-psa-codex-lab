package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0043 section 4, item 5 and U-7 (DATA-REPAIR-timesheet-audit TB-01): no direct identifier or person name is joined into an audit text; there is no exception. */
class AuditDetailGuardTest {
    static final Path MAIN = Path.of("src/main/java");
    static final Path REGISTER = Path.of("architecture/audit-detail-implementations.txt");
    static final Path AUDIT_DETAIL = Path.of("src/main/java/com/forcitis/one/platform/audit/api/AuditDetail.java");
    static final Pattern IMPLEMENTS = Pattern.compile("\\b(?:implements|extends)\\b[^{]*\\bAuditDetail\\b");

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: no main code joins an address, browser, login name, WeCom user id or person name into an audit text")
    void noDirectIdentifierInAuditTexts() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                List<String> found = AuditDetailPolicy.breaches(Files.readString(file));
                if (!found.isEmpty()) {
                    problems.add(file + " " + found);
                }
            }
        }
        assertThat(problems).as(GuardMessage.of("DATA-REPAIR-timesheet-audit TB-01", "main code joins an address, browser, login name, WeCom user id or person name into an audit text", "keep the address in the source_address column and write ids, not names, into the detail")).isEmpty();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01 injected: each forbidden form in an audit-bound place is caught, a clean entry and unrelated text are not")
    void breachesAreCaught() {
        String ipInDetail = "audit.record(new AuditEntry(\"a\", u, null, s, \"method=\" + m + \" address=\" + origin.address()));";
        String clean = "audit.record(new AuditEntry(\"a\", u, null, s, \"method=\" + m).withTrace(origin.address(), OUT, null));";
        assertThat(AuditDetailPolicy.breaches(ipInDetail)).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches(clean)).as("the address in the source_address column is the right place").isEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"agent=\" + ua)")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, x.userAgent())")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, req.getRemoteAddr())")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"x\" + loginName)")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, d + \" loginName=\" + n)")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("return admission.reject(E, m, null, \"no employee for login name \" + name.value());")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new Outcome.Failed(E, \"no employee for WeCom user id \" + id.value())")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String describe() { return \"operator=\" + operator; }")).as("a person name").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String describe() { return \"x witness=\" + witness; }")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String describe() { return part(\"sourceAddress\", sourceAddress); }")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String describe() { return \"operatorId=\" + operator + \" witnessId=\" + witness; }"))
                .as("employee ids are the allowed form").isEmpty();
        assertThat(AuditDetailPolicy.breaches("return admission.reject(E, m, null, \"login name unknown\");")).isEmpty();
        assertThat(AuditDetailPolicy.breaches("return new Outcome.Failed(E, \"wecom user not bound\");")).isEmpty();
        assertThat(AuditDetailPolicy.breaches("throw new DomainException(E, K, \"the login name \" + loginName + \" is taken\");"))
                .as("an exception message is no audit text").isEmpty();
        assertThat(AuditDetailPolicy.breaches("audit.record(new AuditEntry(\"a\", u, null, s, \"granted to \" + employee.get().name()));")).as("a person name through Optional").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("audit.record(new AuditEntry(\"a\", u, null, s, \"to \" + directory.findEmployee(id).get().name()));")).as("a person name through a lookup chain").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("EmployeeProfile x = y; audit.record(new AuditEntry(\"a\", u, null, s, x.name()));")).as("by the declared type").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("Optional<EmployeeProfile> p = y; String describe() { return \"by \" + p.get().name(); }")).as("a generic holder").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String describe() { return \"by \" + staff.displayName(); }")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"mail \" + user.email())")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"code \" + employeeCode)")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"tel \" + mobile)")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"kind \" + kind.name() + \" status \" + run.status().name()))")).as("enum names are allowed").isEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"employeeStatus \" + employeeStatus.name())")).as("a status of an employee is no name").isEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"the email was sent\")")).as("the word in a text is no accessor").isEmpty();
        assertThat(AuditDetailPolicy.breaches("new AuditEntry(\"a\", u, null, s, \"failed: \" + e.getMessage())")).as("an exception message may carry anything").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String detail = \"by \" + employee.name(); record.execute(id, actor, detail);")).as("built first, handed over later").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String text = \"x \" + profile.get().name(); audit.record(new AuditEntry(\"a\", u, null, s, text));")).as("any String handed to an entry").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String detail = \"reason: \" + reason.strip() + \"; held: \" + ids; record.execute(id, actor, detail);")).isEmpty();
        assertThat(AuditDetailPolicy.breaches("String detail = \"a; b \" + employee.name(); record.execute(id, actor, detail);")).as("a ; inside a literal does not end the statement").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String note = \"a; b \" + employee.name(); audit.record(new AuditEntry(\"a\", u, null, s, note));")).as("the same for a text handed to an entry").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("String detail = \"reason: \" + r + \"; held: \" + holder.name(); record.execute(id, actor, detail);")).isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("public String auditDetail() { return changes.stream().map(c -> c.before() + c.holder().name()).collect(joining()); }")).as("auditDetail() bodies are scanned").isNotEmpty();
        assertThat(AuditDetailPolicy.breaches("public String auditDetail() { return changes.size() + \" days\"; }")).isEmpty();
        assertThat(AuditDetailPolicy.breaches("// new AuditEntry(\"a\", u, null, s, \"address=\" + a)")).isEmpty();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: the classes that supply an audit text through AuditDetail are exactly the registered ones")
    void auditDetailImplementationsAreRegistered() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).filter(f -> !f.equals(AUDIT_DETAIL)).sorted().toList()) {
                if (implementsAuditDetail(Files.readString(file))) {
                    found.add(file.toString());
                }
            }
        }
        List<String> registered = Files.readAllLines(REGISTER).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).map(String::strip).toList();
        assertThat(found).as(GuardMessage.of("DATA-REPAIR-timesheet-audit TB-01", "the classes that implement AuditDetail differ from the register", "add a row of the allowed-content table on the card and a line to " + REGISTER + ", or delete the register line of a class that no longer implements AuditDetail")).containsExactlyInAnyOrderElementsOf(registered);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01 injected: an implementation of AuditDetail is recognised, a mere mention is not")
    void implementationsAreRecognised() {
        assertThat(implementsAuditDetail("public record E(int a) implements DomainEvent, AuditDetail {}")).isTrue();
        assertThat(implementsAuditDetail("class E implements AuditDetail { }")).isTrue();
        assertThat(implementsAuditDetail("interface E extends AuditDetail { }")).isTrue();
        assertThat(implementsAuditDetail("event instanceof AuditDetail d ? d.auditDetail() : \"x\"")).isFalse();
        assertThat(implementsAuditDetail("// class E implements AuditDetail")).isFalse();
    }

    static boolean implementsAuditDetail(String source) {
        return IMPLEMENTS.matcher(JavaCode.withoutComments(source)).find();
    }
}
