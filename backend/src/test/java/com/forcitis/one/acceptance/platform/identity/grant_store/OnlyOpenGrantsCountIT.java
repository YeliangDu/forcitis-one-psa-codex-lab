package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #732 part A: 0034-G3-01 and G3-02 (ADR-0034 decision 4 items 1 and 2, DD sign-off section D). Every read of "who holds what" sees the open
 * grants only, whatever the injected clock reads against the row times: the open row is current even when the reading clock is behind its
 * start, and a closed row is never valid even when the clock is behind its end. Readers reached here: the session's permissions, the account
 * view, the occupancy check of role delete, the administrator mode entry, and the I-12 self check.
 */
class OnlyOpenGrantsCountIT extends GrantStoreSupport {

    static final TestDatabase postgres = TestPostgres.database(OnlyOpenGrantsCountIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final Duration DAY = Duration.ofDays(1);

    @Test
    @DisplayName("0034-G3-01 ADR-0034 第 4 项第 2 条：夹具含已关闭授予与开放授予：会话的权限码只来自开放授予，账号视图的角色只列开放授予")
    void sessionPermissionsAndAccountViewFollowTheOpenGrants() throws Exception {
        SignedIn administrator = administrator();
        String open = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        String closed = customRole(administrator, "SELF", PLAIN_CODES.get(1));
        SignedIn person = person();
        insertGrant(person.userId(), roleId(open), NOW.minus(DAY), null);
        insertGrant(person.userId(), roleId(closed), NOW.minus(DAY.multipliedBy(3)), NOW.minus(DAY));

        SignedIn fresh = signIn(person.employeeId());

        assertThat(permissionsOf(fresh)).contains(PLAIN_CODES.get(0)).doesNotContain(PLAIN_CODES.get(1));
        assertThat(roleCodesOfAccount(administrator, person.userId())).contains(open, "employee").doesNotContain(closed);
    }

    @Test
    @DisplayName("0034-G3-01 ADR-0034 第 1 项第 5 条、第 4 项第 2 条（角色删除前的占用检查）：只有已关闭授予的角色可删除（204）；有开放授予的角色删除被拒 409 PLATFORM_IDENTITY_ROLE_IN_USE")
    void occupancyCountsOpenGrantsOnly() throws Exception {
        SignedIn administrator = administrator();
        String history = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        String held = customRole(administrator, "SELF", PLAIN_CODES.get(1));
        SignedIn person = person();
        insertGrant(person.userId(), roleId(history), NOW.minus(DAY.multipliedBy(3)), NOW.minus(DAY));
        insertGrant(person.userId(), roleId(held), NOW.minus(DAY), null);

        MockHttpServletResponse inUse = deleteRole(administrator, held);
        MockHttpServletResponse deleted = deleteRole(administrator, history);

        assertThat(inUse.getStatus()).as(inUse.getContentAsString()).isEqualTo(409);
        assertThat(inUse.getContentAsString()).contains("PLATFORM_IDENTITY_ROLE_IN_USE");
        assertThat(deleted.getStatus()).as(deleted.getContentAsString()).isEqualTo(204);
    }

    @Test
    @DisplayName("0034-G3-01 ADR-0034 第 4 项第 2 条（管理模式进入）、ADR-0027 I-11：super_admin 的授予已关闭，不能进入管理模式；开放则可以")
    void aClosedSuperAdminGrantOpensNoAdministratorMode() throws Exception {
        SignedIn person = person("super_admin");
        closeGrant(grants(person.userId(), "super_admin").getFirst().id(), NOW);

        assertThatThrownBy(() -> administratorMode(signIn(person.employeeId()))).isInstanceOf(AssertionError.class);

        SignedIn holder = person("super_admin");
        assertThat(administratorMode(holder).session()).isNotNull();
    }

    @Test
    @DisplayName("0034-G3-02 ADR-0034 第 4 项第 1 条：读取时钟早于开放授予的开始时刻，该授予仍是当前有效（权限码生效）")
    void anOpenGrantIsCurrentWhenTheClockReadsBeforeItsStart() throws Exception {
        SignedIn administrator = administrator();
        String role = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        SignedIn person = person();
        insertGrant(person.userId(), roleId(role), NOW.plus(Duration.ofHours(1)), null);

        SignedIn fresh = signIn(person.employeeId());

        assertThat(permissionsOf(fresh)).contains(PLAIN_CODES.get(0));
        assertThat(roleCodesOfAccount(administrator, person.userId())).contains(role);
    }

    @Test
    @DisplayName("0034-G3-02 ADR-0034 第 4 项第 1 条：读取时钟早于已关闭授予的关闭时刻，该授予仍不算有效（权限码不生效）")
    void aClosedGrantIsNotValidWhenTheClockReadsBeforeItsEnd() throws Exception {
        SignedIn administrator = administrator();
        String role = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        SignedIn person = person();
        Instant end = NOW.plus(Duration.ofHours(1));
        UUID grant = insertGrant(person.userId(), roleId(role), NOW.minus(DAY), null);
        closeGrant(grant, end);

        SignedIn fresh = signIn(person.employeeId());

        assertThat(permissionsOf(fresh)).doesNotContain(PLAIN_CODES.get(0));
        assertThat(roleCodesOfAccount(administrator, person.userId())).doesNotContain(role);
    }

    @Test
    @DisplayName("0034-G3-02 ADR-0034 第 4 项第 2 条（I-12 自查）、I-12：持有该角色的开放授予开始时刻晚于读取时钟，修改自己持有的角色定义仍被拒绝")
    void theSelfCheckHoldsWhenTheGrantStartsAfterTheClock() throws Exception {
        SignedIn administrator = administrator();
        String role = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        insertGrant(administrator.userId(), roleId(role), NOW.plus(Duration.ofMinutes(30)), null);
        UUID roleId = roleId(role);
        MockHttpServletResponse read = mvc.perform(get(ROLES + "/" + roleId).with(session(administrator))).andReturn().getResponse();
        assertThat(read.getStatus()).isEqualTo(200);

        MockHttpServletResponse changed = mvc.perform(put(ROLES + "/" + roleId).header("If-Match", read.getHeader("ETag")).with(csrf()).with(session(administrator))
                .contentType(MediaType.APPLICATION_JSON).content(definitionJson(null, "Grant store renamed", "SELF", List.of(PLAIN_CODES.get(0))))).andReturn().getResponse();

        assertThat(changed.getStatus()).as(changed.getContentAsString()).isBetween(400, 499);
    }

    @Test
    @DisplayName("0034-G3-01 I-12：自己持有的授予已关闭后，该角色不再算自己持有，可以修改其定义（只看开放授予）")
    void aClosedGrantIsNotHeldForTheSelfCheck() throws Exception {
        SignedIn administrator = administrator();
        String role = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        UUID roleId = roleId(role);
        insertGrant(administrator.userId(), roleId, NOW.minus(DAY.multipliedBy(2)), NOW.minus(DAY));
        MockHttpServletResponse read = mvc.perform(get(ROLES + "/" + roleId).with(session(administrator))).andReturn().getResponse();

        MockHttpServletResponse changed = mvc.perform(put(ROLES + "/" + roleId).header("If-Match", read.getHeader("ETag")).with(csrf()).with(session(administrator))
                .contentType(MediaType.APPLICATION_JSON).content(definitionJson(null, "Grant store renamed", "SELF", List.of(PLAIN_CODES.get(0))))).andReturn().getResponse();

        assertThat(changed.getStatus()).as(changed.getContentAsString()).isEqualTo(200);
    }
}
