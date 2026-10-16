package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationAttestationTest {
    static final String ALICE = "01900000-0000-7000-8000-00000000a001";
    static final String BOB = "01900000-0000-7000-8000-00000000a002";

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: the witness must be another employee: the same id twice is refused")
    void sameIdIsTheSamePerson() {
        assertThatThrownBy(() -> OperationAttestation.of(ALICE, ALICE)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: a missing value, a name and a non-canonical id are refused: only employee ids are accepted")
    void onlyEmployeeIdsAreAccepted() {
        assertThatThrownBy(() -> OperationAttestation.of(null, BOB)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> OperationAttestation.of(ALICE, null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> OperationAttestation.of("alice", BOB)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> OperationAttestation.of(ALICE, BOB.toUpperCase())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> OperationAttestation.of(ALICE, "00000000-0000-0000-0000-000000000000")).as("the nil UUID").isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> OperationAttestation.of(" " + ALICE, BOB)).as("surrounding blanks").isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> OperationAttestation.of(ALICE, BOB + " ")).as("surrounding blanks").isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: two different employees are accepted and recorded by id, without any name")
    void differentEmployeesAreRecordedById() {
        OperationAttestation attestation = OperationAttestation.of(ALICE, BOB);

        assertThat(attestation.describe()).isEqualTo("operatorId=" + ALICE + " witnessId=" + BOB);
    }
}
