package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationAttestationTest {

    @Test
    @DisplayName("the witness must be another person: case, surrounding and repeated spaces and full-width letters do not make a different one")
    void sameNameInOtherSpellingIsTheSamePerson() {
        assertThatThrownBy(() -> new OperationAttestation("Alice", "alice")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new OperationAttestation("Alice Wang", "  alice   wang ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new OperationAttestation("alice", "ａｌｉｃｅ")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("two different people are accepted and recorded in the normalised spelling")
    void differentPeopleAreAccepted() {
        OperationAttestation attestation = new OperationAttestation(" Alice  Wang ", "Bob");

        assertThat(attestation.operator()).isEqualTo("Alice Wang");
        assertThat(attestation.describe()).isEqualTo("operator=Alice Wang witness=Bob");
    }
}
