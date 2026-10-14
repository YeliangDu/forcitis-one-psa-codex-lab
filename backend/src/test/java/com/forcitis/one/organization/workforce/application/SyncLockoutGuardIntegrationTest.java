package com.forcitis.one.organization.workforce.application;

import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.integration.api.SourcePerson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("SyncLockoutGuard: ADR-0027 section 1 item 7, Q-3 (a wrong source cannot take every administrator away)")
class SyncLockoutGuardIntegrationTest extends WorkforceIntegrationSupport {

    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    ReleaseSyncRun release;

    private List<SourcePerson> everyone;

    @BeforeEach
    void tenPeopleTwoOfThemSuperAdministrators() {
        everyone = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            everyone.add(person(i, 2, 100L, null));
        }
        syncWith(everyone);
        awaitEventListeners();
        jdbc.sql("insert into platform_identity.user_accounts (user_id) values (1), (2), (3), (7) on conflict do nothing").update();
        jdbc.sql("""
                insert into platform_identity.account_roles (user_id, role_id)
                select v.user_id, r.role_id from (values (1, 'super_admin'), (2, 'super_admin'), (3, 'security_auditor'), (7, 'employee')) as v (user_id, code)
                  join platform_identity.roles r on r.code = v.code
                on conflict do nothing
                """).update();
    }

    @AfterEach
    void removeTheIdentityData() {
        awaitEventListeners();
        jdbc.sql("delete from platform_identity.account_roles where user_id in (1, 2, 3, 7)").update();
        jdbc.sql("delete from platform_identity.user_accounts where user_id in (1, 2, 3, 7)").update();
    }

    private SyncResult syncWith(List<SourcePerson> people) {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L)), List.of(head(2, 1)), List.of(post(100)), people);
        return sync.execute(SyncTrigger.SCHEDULED, false);
    }

    private List<SourcePerson> without(long... ids) {
        return everyone.stream().filter(p -> java.util.Arrays.stream(ids).noneMatch(id -> p.personId().value() == id)).toList();
    }

    private String employment(long userId) {
        return jdbc.sql("select employment_status from organization_workforce.employees where id = :id").param("id", userId).query(String.class).single();
    }

    @Test
    @DisplayName("people who hold none of the two roles leave without any confirmation")
    void ordinaryLeaversGoThrough() {
        SyncResult result = syncWith(without(7));

        assertThat(result.status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(employment(7)).isEqualTo("SEPARATED");
    }

    @Test
    @DisplayName("a run that would separate a super_admin holder stays HELD, separates nobody and says why")
    void aSuperAdministratorLeavingHoldsTheRun() {
        SyncResult result = syncWith(without(1, 7));

        assertThat(result.status()).isEqualTo(SyncRunStatus.HELD);
        assertThat(result.note()).contains("hold super_admin or security_auditor").contains("only 1 usable super_admin holder(s) would remain");
        assertThat(employment(1)).isEqualTo("EMPLOYED");
        assertThat(employment(7)).as("not even the ordinary leaver of the same run").isEqualTo("EMPLOYED");
        assertThat(jdbc.sql("select held_reason from organization_workforce.sync_runs where status = 'HELD'").query(String.class).single())
                .contains("another holder must confirm");
    }

    @Test
    @DisplayName("the next scheduled runs keep waiting while the source still says so, and add no row")
    void theHoldIsRefreshedNotRepeated() {
        syncWith(without(1));

        SyncResult again = syncWith(without(1));

        assertThat(again.status()).isEqualTo(SyncRunStatus.HELD);
        assertThat(count("sync_runs")).as("the first run and the one hold").isEqualTo(2);
        assertThat(employment(1)).isEqualTo("EMPLOYED");
    }

    @Test
    @DisplayName("a security auditor's departure waits too")
    void anAuditorLeavingHoldsTheRun() {
        assertThat(syncWith(without(3)).status()).isEqualTo(SyncRunStatus.HELD);
    }

    @Test
    @DisplayName("another holder releases it; the person who is leaving and an organisation data administrator who holds neither role cannot")
    void onlyAnotherHolderReleases() {
        long held = syncWith(without(1)).syncRunId().value();

        assertThat(release.execute(held, 1)).isInstanceOf(ReleaseResult.NotApplied.class);
        assertThat(release.execute(held, 7)).as("an ordinary employee or an organisation data administrator").isInstanceOf(ReleaseResult.NotApplied.class);
        assertThat(employment(1)).isEqualTo("EMPLOYED");
        assertThat(jdbc.sql("select status from organization_workforce.sync_runs where id = :id").param("id", held).query(String.class).single())
                .isEqualTo("HELD");

        assertThat(release.execute(held, 2)).isInstanceOf(ReleaseResult.Released.class);

        assertThat(employment(1)).isEqualTo("SEPARATED");
        assertThat(jdbc.sql("select status from organization_workforce.sync_runs where id = :id").param("id", held).query(String.class).single())
                .isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("an auditor can release as well")
    void anAuditorMayRelease() {
        long held = syncWith(without(1)).syncRunId().value();

        assertThat(release.execute(held, 3)).isInstanceOf(ReleaseResult.Released.class);
    }
}
