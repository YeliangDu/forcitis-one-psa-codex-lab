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
    CreateLegalEntity create;

    @Autowired
    UpdateLegalEntity update;

    @BeforeEach
    void empty() {
        VersionTables.truncate(jdbc, "truncate table organization_workforce.legal_entities restart identity cascade");
        VersionTables.truncate(jdbc, "truncate table organization_workforce.org_units restart identity cascade");
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
