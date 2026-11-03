package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemActorsTest {

    @Test
    @DisplayName("RULING-P2-C5 a declared job acts with a name-based UUID (version 5): the same for every call, another for another job, never a version 7 account id")
    void aJobHasOneStableId() {
        UUID job = SystemActors.idOf("organization.workforce.snapshot.reconcile");

        assertThat(SystemActors.idOf("organization.workforce.snapshot.reconcile")).isEqualTo(job);
        assertThat(SystemActors.idOf("platform.identity.account-provisioning")).isNotEqualTo(job);
        assertThat(job.version()).isEqualTo(5);
        assertThat(job.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("RULING-P2-C5 any id of the catalogue names its job, without the job having run in this instance")
    void theIdNamesTheJob() {
        assertThat(SystemActors.nameOf(SystemActors.fromName("platform.approval.deadline.enforce"))).contains("platform.approval.deadline.enforce");
        assertThat(SystemActors.nameOf(UUID.fromString("01900000-0000-7000-8000-000000000001"))).isEmpty();
    }

    @Test
    @DisplayName("RULING-P2-C5 a name that is not declared cannot act")
    void anUndeclaredNameIsRefused() {
        assertThatThrownBy(() -> SystemActors.idOf("no.such.job")).isInstanceOf(IllegalStateException.class).hasMessageContaining("not declared");
    }

    @Test
    @DisplayName("RULING-P2-C5 a catalogue line whose UUID is not v5 of its name, or a name declared twice, stops the catalogue")
    void aWrongCatalogueIsRefused() {
        Map<String, UUID> ids = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        String wrongId = "a.job | " + UUID.randomUUID() + " | a | reason";
        assertThatThrownBy(() -> SystemActors.read(wrongId, "test", ids, names)).isInstanceOf(IllegalStateException.class);
        String twice = "a.job | " + SystemActors.fromName("a.job") + " | a | r\na.job | " + SystemActors.fromName("a.job") + " | a | r";
        assertThatThrownBy(() -> SystemActors.read(twice, "test", new HashMap<>(), new HashMap<>())).hasMessageContaining("twice");
    }
}
