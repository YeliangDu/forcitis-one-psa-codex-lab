package com.forcitis.one.organization.workforce.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.UpdateLegalEntity;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LegalEntityHistoryIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    CreateLegalEntity create;

    @Autowired
    UpdateLegalEntity update;

    @BeforeEach
    void empty() {
        com.forcitis.one.organization.workforce.application.VersionTables.truncate(jdbc, "truncate table organization_workforce.legal_entities restart identity cascade");
    }

    private com.forcitis.one.kernel.ExpectedVersion at(long id) {
        return com.forcitis.one.kernel.ExpectedVersion.of(jdbc.sql("select version from organization_workforce.legal_entities where id = :id").param("id", id).query(Long.class).single());
    }

    private List<String> versions(long id) {
        return jdbc.sql("""
                select revision_number || ':' || name || ':' || status || ':' || (effective_to_date is not null)
                  from organization_workforce.legal_entity_versions where legal_entity_id = :id order by effective_from_date, revision_number
                """).param("id", id).query(String.class).list();
    }

    @Test
    @DisplayName("K4 a legal entity starts with one open version; a rename or a status change closes it and appends the next, a no-op adds nothing")
    void everyChangeIsAVersion() {
        long id = create.execute("FST", "First", null).legalEntityId();
        assertThat(versions(id)).containsExactly("0:First:ACTIVE:false");

        update.execute(id, at(id), null, "First", "ACTIVE", null);
        assertThat(versions(id)).containsExactly("0:First:ACTIVE:false");

        update.execute(id, at(id), null, "First renamed", "ACTIVE", null);
        update.execute(id, at(id), null, "First renamed", "INACTIVE", null);

        assertThat(versions(id)).containsExactly("0:First:ACTIVE:true", "1:First renamed:ACTIVE:true", "2:First renamed:INACTIVE:false");
    }

    @Test
    @DisplayName("K4 the history cannot be rewritten: no delete, no change of a closed version")
    void historyIsAppendOnly() {
        long id = create.execute("FIT", "Second", null).legalEntityId();
        update.execute(id, at(id), null, "Second renamed", "ACTIVE", null);

        assertThatThrownBy(() -> jdbc.sql("delete from organization_workforce.legal_entity_versions where legal_entity_id = :id").param("id", id).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.legal_entity_versions set name = 'x' where legal_entity_id = :id and revision_number = 0")
                .param("id", id).update()).hasMessageContaining("closed version row cannot change");
    }
}
