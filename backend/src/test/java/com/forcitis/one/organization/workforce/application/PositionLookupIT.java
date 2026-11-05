package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.PositionLookup;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class PositionLookupIT {
    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IdGenerator ids;

    @Autowired
    PositionLookup lookup;

    @Test
    @DisplayName("R0 only an ACTIVE position answers active; disabled, deleted and unknown positions answer not active")
    void onlyAnActivePositionIsActive() {
        assertThat(lookup.isActive(position("PL-1", "ACTIVE"))).isTrue();
        assertThat(lookup.isActive(position("PL-2", "DISABLED"))).isFalse();
        assertThat(lookup.isActive(position("PL-5", "ACTIVE", "DISABLED"))).as("a disable command changes the version, not the row").isFalse();
        assertThat(lookup.isActive(position("PL-3", "DELETED"))).isFalse();
        assertThat(lookup.isActive(new PositionId(ids.next()))).isFalse();
    }

    @Test
    @DisplayName("R0 a superseded ACTIVE version does not make a disabled position active")
    void aSupersededActiveVersionDoesNotCount() {
        PositionId id = position("PL-4", "DISABLED");
        UUID command = ids.next();
        jdbc.sql("""
                insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_position_id, effective_date, status,
                    applied_at, created_at, created_by, updated_at)
                values (:id, 'DISABLE', 'POSITION', :position, date '2026-01-01', 'EFFECTIVE', now(), now(), :actor, now())""")
                .param("id", command).param("position", id.value()).param("actor", ids.next()).update();
        jdbc.sql("""
                insert into organization_workforce.position_versions (position_version_id, position_id, effective_from_date, revision_number, code, name, status,
                    change_channel_kind, superseded_at, superseding_org_structure_command_id)
                values (:id, :position, date '2025-06-01', 1, 'PL-4', 'withdrawn', 'ACTIVE', 'MIGRATION', now(), :command)""")
                .param("id", ids.next()).param("position", id.value()).param("command", command).update();

        assertThat(lookup.isActive(id)).isFalse();
    }

    private PositionId position(String code, String status) {
        return position(code, status, status.equals("DISABLED") ? "DISABLED" : "ACTIVE");
    }

    private PositionId position(String code, String status, String versionStatus) {
        UUID id = ids.next();
        jdbc.sql("insert into organization_workforce.positions (id, code, name, sort_order, status) values (:id, :code, :name, 1, :status)")
                .param("id", id).param("code", code).param("name", "post " + code).param("status", status).update();
        // A deleted position keeps an ACTIVE version: versions only know ACTIVE and DISABLED.
        jdbc.sql("""
                insert into organization_workforce.position_versions (position_version_id, position_id, effective_from_date, revision_number, code, name, status, change_channel_kind)
                values (:version, :id, date '2025-01-01', 0, :code, :name, :versionStatus, 'MIGRATION')""")
                .param("version", ids.next()).param("id", id).param("code", code).param("name", "post " + code)
                .param("versionStatus", versionStatus).update();
        return new PositionId(id);
    }
}
