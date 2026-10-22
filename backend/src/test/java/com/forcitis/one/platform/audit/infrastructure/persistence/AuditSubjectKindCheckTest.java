package com.forcitis.one.platform.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.infrastructure.persistence.AuditSubjectKindCheck.Row;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditSubjectKindCheckTest {

    private static Map<String, Row> registryOf(List<AuditSubjectKind> kinds) {
        Map<String, Row> rows = new HashMap<>();
        kinds.forEach(k -> rows.put(k.name(), new Row("ACTIVE", k.hasObject())));
        return rows;
    }

    @Test
    @DisplayName("a registry that has every constant ACTIVE with the same has_object passes")
    void matchingRegistryPasses() {
        List<AuditSubjectKind> all = Arrays.asList(AuditSubjectKind.values());

        assertThat(AuditSubjectKindCheck.problems(registryOf(all), all)).isEmpty();
    }

    @Test
    @DisplayName("a constant that is not registered, one that is retired, and one with a different has_object are each reported")
    void disagreementsAreReported() {
        List<AuditSubjectKind> all = Arrays.asList(AuditSubjectKind.values());
        Map<String, Row> rows = registryOf(all);
        rows.remove(AuditSubjectKind.STORED_FILE.name());
        rows.put(AuditSubjectKind.SYNC_RUN.name(), new Row("RETIRED", true));
        rows.put(AuditSubjectKind.REQUEST.name(), new Row("ACTIVE", true));

        assertThat(AuditSubjectKindCheck.problems(rows, all)).hasSize(3)
                .anyMatch(p -> p.contains("STORED_FILE") && p.contains("not registered"))
                .anyMatch(p -> p.contains("SYNC_RUN") && p.contains("RETIRED"))
                .anyMatch(p -> p.contains("REQUEST") && p.contains("has_object"));
    }

    @Test
    @DisplayName("a retired kind with its constant removed passes: the retirement is one row plus dropping the writers")
    void retiredWithoutConstantPasses() {
        List<AuditSubjectKind> without = Arrays.stream(AuditSubjectKind.values()).filter(k -> k != AuditSubjectKind.SYNC_RUN).toList();
        Map<String, Row> rows = registryOf(without);
        rows.put(AuditSubjectKind.SYNC_RUN.name(), new Row("RETIRED", true));

        assertThat(AuditSubjectKindCheck.problems(rows, without)).isEmpty();
    }
}
