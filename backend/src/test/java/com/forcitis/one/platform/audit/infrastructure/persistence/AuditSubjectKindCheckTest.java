package com.forcitis.one.platform.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.infrastructure.persistence.AuditSubjectKindCheck.Row;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditSubjectKindCheckTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    private static Map<String, Row> registryOf(List<AuditSubjectKind> kinds) {
        Map<String, Row> rows = new HashMap<>();
        kinds.forEach(k -> rows.put(k.name(), new Row("ACTIVE", k.hasObject(), LocalDate.of(2000, 1, 1))));
        return rows;
    }

    @Test
    @DisplayName("a registry that has every constant with the same has_object and a date in force passes")
    void matchingRegistryPasses() {
        List<AuditSubjectKind> all = Arrays.asList(AuditSubjectKind.values());

        assertThat(AuditSubjectKindCheck.problems(registryOf(all), all, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("a constant that is not registered, one with a different has_object and one not yet in force are each reported")
    void disagreementsAreReported() {
        List<AuditSubjectKind> all = Arrays.asList(AuditSubjectKind.values());
        Map<String, Row> rows = registryOf(all);
        rows.remove(AuditSubjectKind.STORED_FILE.name());
        rows.put(AuditSubjectKind.REQUEST.name(), new Row("ACTIVE", true, LocalDate.of(2000, 1, 1)));
        rows.put(AuditSubjectKind.TIMESHEET.name(), new Row("ACTIVE", true, TODAY.plusDays(1)));

        assertThat(AuditSubjectKindCheck.problems(rows, all, TODAY)).hasSize(3)
                .anyMatch(p -> p.contains("STORED_FILE") && p.contains("not registered"))
                .anyMatch(p -> p.contains("REQUEST") && p.contains("has_object"))
                .anyMatch(p -> p.contains("TIMESHEET") && p.contains("not yet in force"));
    }

    @Test
    @DisplayName("a retired kind may keep its constant (old entries stay readable), and a retired kind with no constant passes")
    void retiredKindsAreAllowedWithOrWithoutTheirConstant() {
        List<AuditSubjectKind> all = Arrays.asList(AuditSubjectKind.values());
        Map<String, Row> withConstant = registryOf(all);
        withConstant.put(AuditSubjectKind.SYNC_RUN.name(), new Row("RETIRED", true, LocalDate.of(2000, 1, 1)));
        assertThat(AuditSubjectKindCheck.problems(withConstant, all, TODAY)).isEmpty();

        List<AuditSubjectKind> without = all.stream().filter(k -> k != AuditSubjectKind.SYNC_RUN).toList();
        Map<String, Row> withoutConstant = registryOf(without);
        withoutConstant.put(AuditSubjectKind.SYNC_RUN.name(), new Row("RETIRED", true, LocalDate.of(2000, 1, 1)));
        assertThat(AuditSubjectKindCheck.problems(withoutConstant, without, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("a write naming a retired kind is refused, a write naming a live kind is not")
    void writesToARetiredKindAreRefused() {
        Set<String> retired = Set.of(AuditSubjectKind.SYNC_RUN.name());

        assertThatThrownBy(() -> AuditSubjectKindCheck.refuse(retired, AuditSubjectKind.SYNC_RUN)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SYNC_RUN").hasMessageContaining("retired");
        assertThatCode(() -> AuditSubjectKindCheck.refuse(retired, AuditSubjectKind.ROLE)).doesNotThrowAnyException();
    }
}
