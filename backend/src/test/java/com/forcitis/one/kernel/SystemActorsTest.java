package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemActorsTest {

    @Test
    @DisplayName("R-1b-P2-C5 a job acts with a name-based UUID (version 5): the same for every call, another for another job, never a version 7 account id")
    void aJobHasOneStableId() {
        UUID sync = SystemActors.idOf("organization.workforce.snapshot.sync");

        assertThat(SystemActors.idOf("organization.workforce.snapshot.sync")).isEqualTo(sync);
        assertThat(SystemActors.idOf("platform.identity.account-provisioning")).isNotEqualTo(sync);
        assertThat(sync.version()).isEqualTo(5);
        assertThat(sync.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("R-1b-P2-C5 the id names the job again, for the audit trail")
    void theIdNamesTheJob() {
        UUID id = SystemActors.idOf("platform.approval.deadline.enforce");

        assertThat(SystemActors.nameOf(id)).contains("platform.approval.deadline.enforce");
        assertThat(SystemActors.nameOf(UUID.fromString("01900000-0000-7000-8000-000000000001"))).isEmpty();
    }

    @Test
    @DisplayName("R-1b-P2-C5 a job without a name is a programming error")
    void aNameIsRequired() {
        assertThatThrownBy(() -> SystemActors.idOf(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
