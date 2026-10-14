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
        jdbc.sql("truncate table organization_workforce.legal_entities restart identity cascade").update();
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
        long id = create.execute("FST", "First").legalEntityId();
        assertThat(versions(id)).containsExactly("0:First:ACTIVE:false");

        update.execute(id, null, "First", "ACTIVE");
        assertThat(versions(id)).containsExactly("0:First:ACTIVE:false");

        update.execute(id, null, "First renamed", "ACTIVE");
        update.execute(id, null, "First renamed", "INACTIVE");

        assertThat(versions(id)).containsExactly("0:First:ACTIVE:true", "1:First renamed:ACTIVE:true", "2:First renamed:INACTIVE:false");
    }

    @Test
    @DisplayName("K4 the history cannot be rewritten: no delete, no change of a closed version")
    void historyIsAppendOnly() {
        long id = create.execute("FIT", "Second").legalEntityId();
        update.execute(id, null, "Second renamed", "ACTIVE");

        assertThatThrownBy(() -> jdbc.sql("delete from organization_workforce.legal_entity_versions where legal_entity_id = :id").param("id", id).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.legal_entity_versions set name = 'x' where legal_entity_id = :id and revision_number = 0")
                .param("id", id).update()).hasMessageContaining("closed version row cannot change");
    }
}
