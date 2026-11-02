package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeAccountAccess;
import com.forcitis.one.organization.workforce.application.ReleaseSyncRun;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.SyncResult;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.organization.workforce.domain.SourceTerms;
import com.forcitis.one.platform.integration.api.SourcePerson;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * #961 with #980 (user intent 2026-11-02 企业微信账号改绑防冒用 ③: any change of the WeCom user id revokes the employee's sessions and
 * browser identifiers; #959 review carry-over: the org sync upsert writes wecom_user_id without revocation). Once the scheduled sync is
 * stopped, the paths that can still write employees.wecom_user_id from the organisation source are the release of a held run (it reads
 * the source afresh) and the first-login registration of a person the snapshot does not know by that WeCom id. On each, a set WeCom user id
 * of an existing employee is either left as it was, or the change revokes that employee's sessions through the identity port. Either
 * answer passes; moving it silently does not.
 */
class WecomUserIdWritersRevokeIT extends WorkforceIntegrationSupport {

    static final String SYNC_RUN_EXECUTE = "organization.workforce:sync-run:execute";

    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    ReleaseSyncRun release;

    @Autowired
    OrgDirectory directory;

    @MockitoSpyBean
    EmployeeAccountAccess access;

    private static List<SourcePerson> people(int n) {
        List<SourcePerson> people = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            people.add(person(i, 2, 100L, i == 1 ? null : 1L));
        }
        return people;
    }

    private Optional<String> wecomOf(long sourceId) {
        EmployeeId employee = SourceTerms.person(person(sourceId, 2, 100L, null).personId());
        return jdbc.sql("select wecom_user_id from organization_workforce.employees where id = ?").param(employee.value()).query(String.class).optional();
    }

    private boolean revoked(long sourceId) {
        EmployeeId employee = SourceTerms.person(person(sourceId, 2, 100L, null).personId());
        return Mockito.mockingDetails(access).getInvocations().stream()
                .anyMatch(call -> call.getMethod().getName().equals("revokeSessions") && employee.equals(call.getArguments()[0]));
    }

    /** The invariant: the employee's set WeCom user id is unchanged, or its change revoked the employee's sessions. */
    private void unchangedOrRevoked(long sourceId, String before) {
        Optional<String> after = wecomOf(sourceId);
        assertThat(after).as("premise: the employee row is there").isPresent();
        if (!before.equals(after.orElse(null))) {
            assertThat(revoked(sourceId)).as("employee " + sourceId + ": WeCom user id moved from " + before + " to " + after + " without revoking the sessions").isTrue();
        }
    }

    private void releaseAsSyncRunExecutor(com.forcitis.one.organization.workforce.api.SyncRunId held) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(TestAccounts.account(9001L).value().toString(), "x",
                SYNC_RUN_EXECUTE));
        try {
            assertThat(release.execute(held, "synthetic: source checked by hand")).as("premise: the release applies the source")
                    .isInstanceOf(com.forcitis.one.organization.workforce.application.ReleaseResult.Released.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("#961/#980 放行被扣留的同步运行会重读来源：来源里员工 2 的企业微信账号改了、员工 3 的被清空，放行后两人的账号要么不变，要么已吊销其会话")
    void releasingAHeldRunDoesNotMoveAWecomIdSilently() {
        orgSource.snapshot = tree(people(10).toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        assertThat(wecomOf(2)).contains("wx-2");
        assertThat(wecomOf(3)).contains("wx-3");
        List<SourcePerson> shrunk = new ArrayList<>(people(7));
        shrunk.set(1, person(2, 2, 100L, 1L, "1392", "wx-2-elsewhere"));
        shrunk.set(2, person(3, 2, 100L, 1L, "1393", null));
        orgSource.snapshot = tree(shrunk.toArray(SourcePerson[]::new));
        SyncResult held = sync.execute(SyncTrigger.SCHEDULED, false);
        assertThat(held.status()).as("premise: the shrink holds the run").isEqualTo(SyncRunStatus.HELD);
        Mockito.clearInvocations(access);

        releaseAsSyncRunExecutor(held.syncRunId());

        unchangedOrRevoked(2, "wx-2");
        unchangedOrRevoked(3, "wx-3");
    }

    @Test
    @DisplayName("#961/#980 首次登录补登记：用快照里没有的企业微信账号登录，来源按该账号查到的是已在册的员工 5，补登记后员工 5 原有的企业微信账号要么不变，要么已吊销其会话")
    void aLoginRegistrationDoesNotMoveAWecomIdSilently() {
        orgSource.snapshot = tree(people(6).toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        assertThat(wecomOf(5)).contains("wx-5");
        orgSource.singleLookupResult = List.of(person(5, 2, 100L, 1L, "1395", "wx-5-elsewhere"));
        Mockito.clearInvocations(access);
        int lookups = orgSource.singleLookups.get();

        directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-5-elsewhere")));

        assertThat(orgSource.singleLookups.get()).as("premise: the login asked the source by the new WeCom user id").isGreaterThan(lookups);

        unchangedOrRevoked(5, "wx-5");
    }
}
