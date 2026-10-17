package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkforceRowConstraintsIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    RefreshShortNames refreshShortNames;

    @Autowired
    CreateLegalEntity create;

    @Autowired
    UpdateLegalEntity update;

    @BeforeEach
    void empty() {
        VersionTables.truncate(jdbc, "truncate table organization_workforce.legal_entities restart identity cascade");
        VersionTables.truncate(jdbc, "truncate table organization_workforce.org_units restart identity cascade");
    }

    private UUID orgUnit(String shortName) {
        UUID id = com.forcitis.one.kernel.Uuids.timeOrdered(java.time.Instant.now());
        jdbc.sql("insert into organization_workforce.org_units (id, name, status, short_name) values (:id, 'Synthetic unit', 'ACTIVE', :short)")
                .param("id", id).param("short", shortName).update();
        return id;
    }

    private String shortName(UUID id) {
        return jdbc.sql("select short_name from organization_workforce.org_units where id = :id").param("id", id).query(String.class).optional().orElse(null);
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-11：RefreshShortNames 把老系统给的空串、纯空格、null 规范为没有简称，不抛，带内容的原样写入")
    void blankShortNamesBecomeNull() {
        UUID blank = orgUnit("Old");
        UUID spaces = orgUnit("Old");
        UUID missing = orgUnit("Old");
        UUID named = orgUnit(null);
        Map<OrgUnitId, String> names = new HashMap<>();
        names.put(new OrgUnitId(blank), "");
        names.put(new OrgUnitId(spaces), "   ");
        names.put(new OrgUnitId(missing), null);
        names.put(new OrgUnitId(named), "HQ");
        oldSystem.names = names;

        assertThat(refreshShortNames.execute()).isEqualTo(4);

        assertThat(shortName(blank)).isNull();
        assertThat(shortName(spaces)).isNull();
        assertThat(shortName(missing)).isNull();
        assertThat(shortName(named)).isEqualTo("HQ");
        assertThat(refreshShortNames.execute()).isZero();
    }

    private ExpectedVersion at(LegalEntityId id) {
        return ExpectedVersion.of(jdbc.sql("select version from organization_workforce.legal_entities where id = :id").param("id", id.value()).query(Long.class).single());
    }

    private void assertRootEqualsItsOpenVersion() {
        List<String> mismatches = jdbc.sql("""
                select e.code from organization_workforce.legal_entities e
                  left join organization_workforce.legal_entity_versions v on v.legal_entity_id = e.id and v.effective_to_date is null
                 where v.legal_entity_version_id is null or (e.code, e.name, e.status) is distinct from (v.code, v.name, v.status)
                """).query(String.class).list();
        assertThat(mismatches).as("legal entities whose root differs from the open version").isEmpty();
        assertThat(jdbc.sql("""
                select count(*) from (select legal_entity_id from organization_workforce.legal_entity_versions
                 where effective_to_date is null group by legal_entity_id having count(*) <> 1) t
                """).query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("DATA-REPAIR-workforce WF-10 LE-2：法人根表的代码、名称、状态在建、改名、停用、启用、更正代码后始终等于开放版本，且每个法人恰有一个开放版本")
    void legalEntityRootEqualsItsOpenVersion() {
        LegalEntityId first = create.execute("FST", "First").legalEntityId();
        LegalEntityId second = create.execute("SND", "Second").legalEntityId();
        assertRootEqualsItsOpenVersion();

        update.execute(first, at(first), null, "First renamed", "ACTIVE");
        assertRootEqualsItsOpenVersion();
        update.execute(first, at(first), null, "First renamed", "INACTIVE");
        assertRootEqualsItsOpenVersion();
        update.execute(first, at(first), null, "First renamed", "ACTIVE");
        assertRootEqualsItsOpenVersion();
        update.execute(second, at(second), "SNX", "Second", "ACTIVE");
        assertRootEqualsItsOpenVersion();

        assertThat(jdbc.sql("select count(*) from organization_workforce.legal_entities").query(Long.class).single()).isEqualTo(2);
    }
}
