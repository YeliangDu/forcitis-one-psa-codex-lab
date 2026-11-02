package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.DataArchitectureTest.Column;
import com.forcitis.one.DataArchitectureTest.Entry;
import com.forcitis.one.DataArchitectureTest.Table;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D5 (ADR-0043): a column whose name looks personal needs the mark PI or SPI, or an exemption row; a NONE row without the exemption word is not enough. */
class PersonalMarkGuardTest {

    private static List<String> d5(Map<String, ClassificationRegister.Row> register) {
        Table table = new Table("platform_audit.demo_rows");
        table.columns.put("email", new Column("email", "text", false, "mail"));
        Entry entry = new Entry(table.name, new LinkedHashMap<>(Map.of("role", "BUSINESS_DOCUMENT", "mutability", "MUTABLE",
                "retention", Map.of("policy", "90d"), "lineage", Map.of("source", "OLD"))), List.of());
        return DataArchitectureTest.violations(Map.of(table.name, table), Map.of(table.name, entry), register, Set.of()).stream()
                .filter(v -> v.rule().equals("D5")).map(v -> v.table() + "." + v.column()).toList();
    }

    @Test
    @DisplayName("ADR-0043 D5: unregistered or NONE without exemption is reported; PI, SPI and an exemption row are not")
    void markIsRequired() {
        String column = "platform_audit.demo_rows.email";
        assertThat(d5(Map.of())).containsExactly(column);
        assertThat(d5(Map.of(column, new ClassificationRegister.Row("INTERNAL", "NONE", "x")))).containsExactly(column);
        assertThat(d5(Map.of(column, new ClassificationRegister.Row("INTERNAL", "PI", "用途：x")))).isEmpty();
        assertThat(d5(Map.of(column, new ClassificationRegister.Row("SENSITIVE", "SPI", "用途：x docs/privacy/x.md")))).isEmpty();
        assertThat(d5(Map.of(column, new ClassificationRegister.Row("INTERNAL", "NONE", "豁免：布尔标志，不是邮箱")))).isEmpty();
    }
}
