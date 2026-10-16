package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0043 section 4, item 5 and U-7 (DATA-REPAIR-timesheet-audit TB-01): no direct identifier or person name is joined into an audit text; there is no exception. */
class AuditDetailGuardTest {
    static final Path MAIN = Path.of("src/main/java");

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
        assertThat(problems).as("keep the address in source_address and write ids, not names, into detail").isEmpty();
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
        assertThat(AuditDetailPolicy.breaches("// new AuditEntry(\"a\", u, null, s, \"address=\" + a)")).isEmpty();
    }
}
