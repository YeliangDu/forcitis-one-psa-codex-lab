package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #959 security follow-up (user decision of 2026-11-02, WeCom rebind against impersonation, all three controls): employees.wecom_user_id
 * is the WeCom login binding, so a profile change must not let one person sign in as another.
 * ① nobody changes their own employee record; ② a rebind or clear of a non-empty WeCom user id of an employee whose account holds any
 * role beyond employee needs a second person, and until that flow exists (lead sequencing) it is refused with an error of its own, while
 * filling in an empty one stays single-person; ③ any change of the WeCom user id revokes that employee's sessions and browser identifiers
 * in the same transaction, as separation does (I-13).
 */
class EmployeeWecomRebindIT extends EmployeeHireSupport {

    static final TestDatabase postgres = TestPostgres.database(EmployeeWecomRebindIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#959 用户 2026-11-02 第①道：人事专员改自己的档案被拒（只改姓名也拒、兼另一角色也拒），员工根、会话、审计都不变")
    void nobodyChangesTheirOwnRecord() throws Exception {
        assertRefusedUnchanged(hr, hr.employeeId(), "Synthetic self", wecomOf(hr));
        assertRefusedUnchanged(hr, hr.employeeId(), "Synthetic self", syntheticWecom("self-moved"));

        Staff both = staff(PERSONNEL_SPECIALIST_ROLE, "org_data_admin");
        assertRefusedUnchanged(both, both.employeeId(), "Synthetic self two roles", wecomOf(both));

        Staff other = staff();
        actIn(hr);
        change(other.employeeId(), "Synthetic changed by hr", null, null, wecomOf(other), TODAY);
        assertThat(root(other.employeeId()).get("display_name")).as("the same caller still changes someone else").isEqualTo("Synthetic changed by hr");
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {"super_admin", "org_data_admin", "business_admin", "security_auditor", "process_administrator", PERSONNEL_SPECIALIST_ROLE})
    @DisplayName("#959 用户 2026-11-02 第②道（#959 先一律拒收）：账号持有普通员工以外角色的人，企业微信账号改绑与清空都被拒，错误与重复占用、本人修改不同；员工根、会话、浏览器标识、审计都不变")
    void aPrivilegedRebindOrClearIsRefusedWithItsOwnError(String role) throws Exception {
        Staff privileged = staff(role);
        String held = wecomOf(privileged);
        ErrorCode taken = errorOf(() -> change(staff().employeeId(), "Synthetic duplicate", null, null, held, TODAY));
        actIn(hr);
        Throwable ownRecord = catchThrowable(() -> change(hr.employeeId(), "Synthetic self", null, null, wecomOf(hr), TODAY));
        ErrorCode self = ownRecord instanceof DomainException domain ? domain.errorCode() : null;

        Throwable rebind = assertRefusedUnchanged(hr, privileged.employeeId(), "Synthetic rebound", syntheticWecom("rebind-" + role));
        Throwable clear = assertRefusedUnchanged(hr, privileged.employeeId(), "Synthetic cleared", null);

        for (Throwable failure : new Throwable[] {rebind, clear}) {
            assertThat(failure).as(String.valueOf(failure)).isInstanceOf(DomainException.class);
            ErrorCode code = ((DomainException) failure).errorCode();
            assertThat(code).as("an error of its own: not the duplicate refusal").isNotEqualTo(taken);
            assertThat(code).as("an error of its own: not the own-record refusal").isNotEqualTo(self);
        }
        assertThat(((DomainException) rebind).errorCode()).as("rebind and clear are the same control").isEqualTo(((DomainException) clear).errorCode());
    }

    @Test
    @DisplayName("#959 用户 2026-11-02 第②道：有角色的人档案里其他项照改、企业微信账号原样保留不算改绑，照常生效；会话与浏览器标识都保留")
    void aPrivilegedPersonsOtherFieldsStillChangeAndKeepTheirSessions() throws Exception {
        Staff privileged = staff("super_admin");
        browserIdentifier(privileged);
        actIn(hr);

        change(privileged.employeeId(), "Synthetic admin renamed", "FEMALE", "admin@example.invalid", wecomOf(privileged), TODAY);

        assertThat(root(privileged.employeeId()).get("display_name")).isEqualTo("Synthetic admin renamed");
        assertThat(root(privileged.employeeId()).get("wecom_user_id")).isEqualTo(wecomOf(privileged));
        assertThat(profileStatus(privileged)).as("no WeCom change, no revocation").isEqualTo(200);
        assertThat(browserIdentifiers(privileged)).isEqualTo(1);
    }

    @Test
    @DisplayName("#959 用户 2026-11-02 第②③道：有角色的人企业微信账号为空时，人事专员一人即可补填；补填也是改动，该员工已有会话与浏览器标识同事务作废；补填的账号能登录进其原账号")
    void aPrivilegedPersonsEmptyWecomUserIdIsFilledInBySpecialistAlone() throws Exception {
        Staff privileged = staff("super_admin");
        browserIdentifier(privileged);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set wecom_user_id = null where id = ?", stored(privileged.employeeId()));
        }
        String filled = syntheticWecom("fill-in");
        actIn(hr);

        change(privileged.employeeId(), (String) root(privileged.employeeId()).get("display_name"), null, null, filled, TODAY);

        assertThat(root(privileged.employeeId()).get("wecom_user_id")).isEqualTo(filled);
        assertThat(profileStatus(privileged)).as("the sessions of the employee are revoked").isIn(401, 403);
        assertThat(browserIdentifiers(privileged)).as("the browser identifiers of the employee are gone").isZero();
        assertThat(signInWith(wecomCodeOf(filled), privileged.employeeId()).userId()).as("the filled-in WeCom user id opens the same account").isEqualTo(privileged.userId());
    }

    @Test
    @DisplayName("#959 用户 2026-11-02 第③道：只有普通员工角色的人可由人事专员一人改绑；改绑同事务作废该员工全部会话与浏览器标识，别人的不动；旧企业微信账号再登录打不开该账号，新账号登录进同一账号")
    void aRebindRevokesTheEmployeesSessionsAndTheOldWecomLogin() throws Exception {
        Staff person = staff();
        Staff bystander = staff();
        String old = wecomOf(person);
        browserIdentifier(person);
        browserIdentifier(bystander);
        String rebound = syntheticWecom("rebound");
        actIn(hr);

        change(person.employeeId(), (String) root(person.employeeId()).get("display_name"), null, null, rebound, TODAY);

        assertThat(root(person.employeeId()).get("wecom_user_id")).isEqualTo(rebound);
        assertThat(profileStatus(person)).as("the employee's session is revoked").isIn(401, 403);
        assertThat(browserIdentifiers(person)).as("the employee's browser identifiers are gone").isZero();
        assertThat(profileStatus(bystander)).as("someone else's session stays").isEqualTo(200);
        assertThat(profileStatus(hr)).as("the caller's session stays").isEqualTo(200);
        assertThat(browserIdentifiers(bystander)).as("someone else's browser identifier stays").isEqualTo(1);

        MockHttpServletResponse oldLogin = wecomLogin(wecomCodeOf(old));
        assertThat(oldLogin.getStatus() != 200 || !text(person.userId()).equals(String.valueOf(JsonPath.read(oldLogin.getContentAsString(), "$.userId"))))
                .as("the old WeCom user id no longer opens the account: " + oldLogin.getStatus()).isTrue();
        assertThat(signInWith(wecomCodeOf(rebound), person.employeeId()).userId()).as("the new WeCom user id opens the same account").isEqualTo(person.userId());
    }

    @Test
    @DisplayName("#959 opus 复审必改 1 的攻击链：人事专员把自己的企业微信账号挪走、写到超级管理员档案上、或清空超级管理员的账号都被拒；其企业微信登录仍只进自己的账号")
    void aSpecialistCannotTakeOverASuperAdmin() throws Exception {
        Staff admin = staff("super_admin");
        String own = wecomOf(hr);

        assertRefusedUnchanged(hr, hr.employeeId(), "Synthetic moved away", syntheticWecom("moved-away"));
        assertRefusedUnchanged(hr, admin.employeeId(), "Synthetic takeover", own);
        assertRefusedUnchanged(hr, admin.employeeId(), "Synthetic takeover", null);

        Staff accomplice = staff(PERSONNEL_SPECIALIST_ROLE);
        assertRefusedUnchanged(accomplice, hr.employeeId(), "Synthetic moved by accomplice", syntheticWecom("moved-by-accomplice"));
        assertRefusedUnchanged(accomplice, hr.employeeId(), "Synthetic cleared by accomplice", null);

        assertThat(signInWith(wecomCodeOf(own), hr.employeeId()).userId()).as("the specialist's WeCom login opens only their own account").isEqualTo(hr.userId());
    }

    @Test
    @DisplayName("#959 用户 Q1、2026-11-02：入职时填别人（含有角色的人）已绑的企业微信账号被拒，什么都不留下")
    void aHireCannotTakeAnAccountsWecomUserId() throws Exception {
        Staff admin = staff("super_admin");
        Counts before = counts();

        Throwable failure = catchThrowable(() -> hire("Synthetic hire takeover", null, null, wecomOf(admin)));

        assertThat(failure).as(String.valueOf(failure)).isInstanceOf(DomainException.class);
        assertThat(counts()).isEqualTo(before);
        assertThat(root(admin.employeeId()).get("wecom_user_id")).isEqualTo(wecomOf(admin));
        assertThat(signInWith(wecomCodeOf(wecomOf(admin)), admin.employeeId()).userId()).isEqualTo(admin.userId());
    }

    // ---- helpers ----

    private static String wecomOf(Staff who) {
        return wecomUserIdOf(who.employeeId());
    }

    /**
     * The change by this caller is refused; the root, the employee's session and browser identifiers and the audit stay as they were.
     * The refusal is returned.
     */
    private Throwable assertRefusedUnchanged(Staff caller, UUID person, String displayName, String wecomUserId) throws Exception {
        Map<String, Object> rootBefore = root(person);
        int audited = auditCount(AUDIT_PROFILE_CHANGED);
        long identifiers = browserIdentifiersOfEmployee(person);
        actIn(caller);

        Throwable failure = catchThrowable(() -> change(person, displayName, null, null, wecomUserId, TODAY));

        assertThat(failure).as("refused").isNotNull();
        assertThat(root(person)).as("the root is unchanged").isEqualTo(rootBefore);
        assertThat(auditCount(AUDIT_PROFILE_CHANGED)).isEqualTo(audited);
        assertThat(browserIdentifiersOfEmployee(person)).as("no browser identifier revoked").isEqualTo(identifiers);
        assertThat(profileStatus(caller)).as("the caller's session stays").isEqualTo(200);
        return failure;
    }

    private ErrorCode errorOf(ThrowingRunnable call) throws Exception {
        actIn(hr);
        Throwable failure = catchThrowable(call::run);
        assertThat(failure).as(String.valueOf(failure)).isInstanceOf(DomainException.class);
        return ((DomainException) failure).errorCode();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** The status of the person's own profile in their login session: 200 while it lives, 401 or 403 when it is revoked. */
    private int profileStatus(Staff who) throws Exception {
        return mvc.perform(get("/api/v1/platform/sessions:current").with(session(who.session())))
                .andReturn().getResponse().getStatus();
    }

    /** A browser identifier of the person's account, as the confirmation of an external login leaves one (I-13). */
    private void browserIdentifier(Staff who) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection identity = schemaConnection("platform_identity")) {
            update(identity, "insert into browser_identifiers (browser_identifier_id, user_id, identifier_hash, last_login_at) values (?, ?, ?, ?)",
                    Uuids.timeOrdered(NOW), who.userId(), "synthetic-hash-" + UUID.randomUUID(), at);
        }
    }

    private long browserIdentifiers(Staff who) throws Exception {
        try (Connection identity = schemaConnection("platform_identity")) {
            return (long) query(identity, "select count(*) as n from browser_identifiers where user_id = ?", who.userId()).get(0).get("n");
        }
    }

    private long browserIdentifiersOfEmployee(UUID employee) throws Exception {
        long total = 0;
        for (UUID account : accountsOfEmployee(employee)) {
            try (Connection identity = schemaConnection("platform_identity")) {
                total += (long) query(identity, "select count(*) as n from browser_identifiers where user_id = ?", account).get(0).get("n");
            }
        }
        return total;
    }
}
