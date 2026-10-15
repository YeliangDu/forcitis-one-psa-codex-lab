package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.guard.CredentialColumnGuardTest.DeniedColumn;
import com.forcitis.one.platform.integration.guard.ExternalReadRegister.Row;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The credential guard reads the register line by line; a row of the current format names table and column in separate fields. */
class RegisterRowCredentialGuardTest {

    static String row(DeniedColumn denied, String object) {
        return "  - { database: " + denied.database() + ", object: " + object + ", column: " + denied.column() + ", source: dbo." + denied.table() + "."
                + denied.column() + ", category: EMPLOYEE, sensitivity: RESTRICTED, purpose: MIGRATION }";
    }

    @Test
    @DisplayName("R-1b-0 凭据列守护自检：按外部读取登记现行写法（object: dbo.表, column: 列）登记拒绝清单第一列，被报出")
    void aCredentialRowOfTheCurrentFormatIsFound() throws IOException {
        List<DeniedColumn> denied = CredentialColumnGuardTest.deniedColumns();
        DeniedColumn first = denied.getFirst();
        List<String> lines = List.of("databases: [" + first.database() + "]", "columns:", row(first, "dbo." + first.table()),
                row(first, ExternalReadRegister.VIEW_SCHEMA + ".v_probe"));

        List<Row> rows = ExternalReadRegister.rows(lines);
        assertThat(rows).as("precondition: both rows are otherwise legal, only the credential guard stops them")
                .allSatisfy(r -> assertThat(ExternalReadRegister.problems(r, ExternalReadRegister.databases(lines))).isEmpty());
        for (Row r : rows) {
            assertThat(CredentialColumnGuardTest.namedDeniedColumns(lines.get(r.line() - 1), denied)).as("line %s", r.line()).contains(first.key());
        }
    }
}
