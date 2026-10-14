package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.AggregateWriteGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DDD-1: a transaction changes one aggregate; the few that cannot are listed with their reason, and the list names no use case that is gone. */
class SingleAggregateGuardTest {

    static final Path EXCEPTIONS = Path.of("architecture/multi-aggregate-exceptions.txt");

    @Test
    @DisplayName("DDD-1 every registered multi-aggregate transaction names an existing use case, at least two aggregates and a reason")
    void theRegisterIsCurrent() throws Exception {
        List<String> lines = Files.readAllLines(EXCEPTIONS).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertThat(lines).isNotEmpty();
        for (String line : lines) {
            String[] parts = line.split("\\|");
            assertThat(parts).as(line).hasSize(3);
            assertThat(Class.forName(parts[0].trim(), false, getClass().getClassLoader()).getName()).isEqualTo(parts[0].trim());
            assertThat(parts[1].trim().split("\\+")).as(line).hasSizeGreaterThan(1);
            assertThat(parts[2].trim()).as(line).isNotBlank();
        }
    }

    @Test
    @DisplayName("DDD-1 injected: a set of aggregates outside the register is not allowed, one inside is")
    void anUnregisteredSetIsCaught() throws IOException {
        List<Set<String>> registered = AggregateWriteGuard.entries(Files.readAllLines(EXCEPTIONS));
        assertThat(registered).anyMatch(set -> set.containsAll(Set.of("Timesheet", "NumberingRule")));
        assertThat(registered).noneMatch(set -> set.containsAll(Set.of("ApprovalRequest", "Timesheet")));
    }
}
