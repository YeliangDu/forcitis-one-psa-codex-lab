package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #732 part A, 0034-G7-04 (RoleGrantLockIntegrationTest): one request deletes a role while another grants it. ADR-0034 decision 1 item 5 and
 * decision 2 item 1: exactly one of them succeeds, and a deleted role never carries an open grant. Repeated, because the outcome depends on timing.
 */
class RoleDeleteGrantRaceIT extends GrantStoreSupport {

    static final TestDatabase postgres = TestPostgres.database(RoleDeleteGrantRaceIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final int ROUNDS = 12;

    @Test
    @DisplayName("0034-G7-04 ADR-0034 第 1 项第 5 条、第 2 项第 1 条：同时删除角色与授予该角色，恰有一个成功，另一个被拒为 4xx（不是 5xx）；任何时候没有「已删除角色上的开放授予」")
    void exactlyOneOfDeleteAndGrantWins() throws Exception {
        SignedIn administrator = administrator();
        SignedIn target = person();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String role = customRole(administrator, "SELF", PLAIN_CODES.get(round % PLAIN_CODES.size()));
                String etag = roleEtag(administrator, role);
                CountDownLatch start = new CountDownLatch(1);
                Future<MockHttpServletResponse> deletion = pool.submit(() -> {
                    start.await();
                    return deleteRole(administrator, role, etag);
                });
                Future<MockHttpServletResponse> grant = pool.submit(() -> {
                    start.await();
                    return replaceRoles(administrator, target.userId(), List.of("employee", role));
                });
                start.countDown();
                MockHttpServletResponse deleted = deletion.get(60, TimeUnit.SECONDS);
                MockHttpServletResponse granted = grant.get(60, TimeUnit.SECONDS);

                boolean deleteWon = deleted.getStatus() == 204;
                boolean grantWon = granted.getStatus() == 204;
                assertThat(deleteWon ^ grantWon).as("round %d: delete %d %s / grant %d %s", round, deleted.getStatus(), deleted.getContentAsString(),
                        granted.getStatus(), granted.getContentAsString()).isTrue();
                assertThat(deleteWon ? granted.getStatus() : deleted.getStatus()).as("round %d: the loser is refused", round).isBetween(400, 499);
                UUID roleId = roleId(role);
                long deletedWithOpenGrants = ((Number) single("""
                        select count(*) from account_role_grants g where g.role_id = ? and g.effective_to_at is null
                           and not exists (select 1 from role_versions v where v.role_id = g.role_id and v.effective_to_at is null)""", roleId)).longValue();
                assertThat(deletedWithOpenGrants).as("round %d: no open grant on a deleted role", round).isZero();
                if (deleteWon) {
                    assertThat(grants(target.userId(), role)).isEmpty();
                } else {
                    assertThat(grants(target.userId(), role)).singleElement().matches(Grant::open);
                    replacedRoles(administrator, target.userId(), List.of("employee"));
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
