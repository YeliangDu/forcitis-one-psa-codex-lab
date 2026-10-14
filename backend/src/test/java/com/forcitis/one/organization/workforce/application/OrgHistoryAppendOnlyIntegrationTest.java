package com.forcitis.one.organization.workforce.application;

import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrgHistoryAppendOnlyIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    SyncOrgSnapshot sync;

    private void synced(Long parentOfUnit3) {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, parentOfUnit3), unit(4, null)),
                List.of(head(2, 1), manager(2, 2)), List.of(post(100), post(101)),
                List.of(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 2L)));
        sync.execute(SyncTrigger.SCHEDULED, false);
    }

    private List<String> unit3Rows() {
        return jdbc.sql("""
                select revision_number || ':' || coalesce(parent_org_unit_id::text, '-') || ':' || (effective_to_date is not null)
                  from organization_workforce.org_unit_versions where org_unit_id = 3 order by effective_from_date, revision_number
                """).query(String.class).list();
    }

    @Test
    @DisplayName("T1 a second change on the same day closes that day's version as an empty range and appends the next revision; nothing is deleted")
    void sameDayChangeIsAClosingAndAnAppend() {
        synced(2L);
        synced(4L);

        assertThat(unit3Rows()).containsExactly("0:2:true", "1:4:false");
        assertThat(jdbc.sql("""
                select count(*) from organization_workforce.org_unit_versions
                 where org_unit_id = 3 and effective_to_date = effective_from_date
                """).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("T1 the database refuses to delete a version, to change a closed one, or to change anything of an open one but its end")
    void triggersProtectTheHistory() {
        synced(2L);
        synced(4L);

        assertThatThrownBy(() -> jdbc.sql("delete from organization_workforce.org_unit_versions where org_unit_id = 3").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.org_unit_versions set name = 'x' where org_unit_id = 3 and revision_number = 0").update())
                .hasMessageContaining("closed version row cannot change");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.org_unit_versions set name = 'x' where org_unit_id = 3 and revision_number = 1").update())
                .hasMessageContaining("can only be closed");
        assertThatThrownBy(() -> jdbc.sql("delete from organization_workforce.assignment_versions where employee_id = 1").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.assignment_versions set org_unit_id = 4 where employee_id = 1").update())
                .hasMessageContaining("can only be closed");

        assertThat(jdbc.sql("update organization_workforce.org_unit_versions set effective_to_date = effective_from_date + 1 where org_unit_id = 3 and revision_number = 1")
                .update()).isEqualTo(1);
    }
}
