package com.forcitis.one.organization.workforce.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("#910 S3 the named enums of the countersign read paths cover exactly the values of the enums they merge")
class CountersignReadEnumsTest {
    private static List<String> names(Enum<?>[]... sets) {
        return Arrays.stream(sets).flatMap(Arrays::stream).map(Enum::name).distinct().toList();
    }

    @Test
    void theCommandKindIsThePersonnelAndTheOrgStructureKindsTogether() {
        assertThat(names(CountersignCommandKind.values())).containsExactlyInAnyOrderElementsOf(names(PersonnelCommandKind.values(), OrgStructureCommandKind.values()));
    }

    @Test
    void theCommandStatusIsThePersonnelAndTheOrgStructureStatusesTogether() {
        assertThat(names(CountersignCommandStatus.values())).containsExactlyInAnyOrderElementsOf(names(CommandStatus.values(), OrgCommandStatus.values()));
    }

    @Test
    void theDecisionKindIsTheCountersignStatusWithoutOpen() {
        List<String> decided = Stream.of(CountersignStatus.values()).filter(s -> s != CountersignStatus.OPEN).map(Enum::name).toList();
        assertThat(names(CountersignDecisionKind.values())).containsExactlyInAnyOrderElementsOf(decided);
    }
}
