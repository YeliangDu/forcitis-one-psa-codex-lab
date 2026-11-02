package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.http.MediaType;
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

    @org.springframework.beans.factory.annotation.Autowired
    private com.forcitis.one.platform.identity.application.OperationsCommands identityOperations;

    static final TestDatabase postgres = TestPostgres.database(EmployeeWecomRebindIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#959 用户 2026-11-02 第①道：人事专员改自己的档案被拒（只改姓名也拒、兼另一角色也拒），员工根、会话、审计都不变")
    void nobodyChangesTheirOwnRecord() throws Exception {
        assertRefusedUnchanged(hr, hr, "Synthetic self", wecomOf(hr));
        assertRefusedUnchanged(hr, hr, "Synthetic self", syntheticWecom("self-moved"));

        Staff both = staff(PERSONNEL_SPECIALIST_ROLE, "org_data_admin");
        assertRefusedUnchanged(both, both, "Synthetic self two roles", wecomOf(both));

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
        ErrorCode tooEarly = errorOf(() -> change(staff().employeeId(), "Synthetic too early", null, null, null, TODAY.plusDays(1)));

        Throwable rebind = assertRefusedUnchanged(hr, privileged, "Synthetic rebound", syntheticWecom("rebind-" + role));
        Throwable clear = assertRefusedUnchanged(hr, privileged, "Synthetic cleared", null);

        for (Throwable failure : new Throwable[] {rebind, clear}) {
            assertThat(failure).as(String.valueOf(failure)).isInstanceOf(DomainException.class);
            ErrorCode code = ((DomainException) failure).errorCode();
            assertThat(code).as("an error of its own: not the duplicate refusal").isNotEqualTo(taken);
            assertThat(code).as("an error of its own: not the own-record refusal").isNotEqualTo(self);
            assertThat(code).as("an error of its own: not the refusal of an invalid change (a future date)").isNotEqualTo(tooEarly);
            assertThat(code.code()).as("the code of this control").isEqualTo(WECOM_CHANGE_NOT_ALLOWED);
        }
        assertThat(((DomainException) rebind).errorCode()).as("rebind and clear are the same control").isEqualTo(((DomainException) clear).errorCode());
    }

    @Test
    @DisplayName("#959 用户 2026-11-02 第②道「普通员工以外的任何角色」：自定义角色的持有人、以及账号上没有 employee 角色行的超级管理员，企业微信账号改绑同样被拒，错误与种子角色时相同")
    void aCustomRoleOrAnAdminWithoutTheEmployeeRoleCountsAsPrivileged() throws Exception {
        ErrorCode seeded = ((DomainException) assertRefusedUnchanged(hr, staff("org_data_admin"), "Synthetic rebound", syntheticWecom("seeded"))).errorCode();

        Staff custom = staff(customRole());
        Throwable customRefusal = assertRefusedUnchanged(hr, custom, "Synthetic rebound", syntheticWecom("custom"));
        assertThat(customRefusal).as(String.valueOf(customRefusal)).isInstanceOf(DomainException.class);
        assertThat(((DomainException) customRefusal).errorCode()).isEqualTo(seeded);

        Staff admin = staff("super_admin");
        ensureAttestors();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int revoked = identityOperations.run(new org.springframework.boot.DefaultApplicationArguments("--operation=revoke-role", "--employee-id=" + text(admin.employeeId()),
                "--role-code=employee", "--operator=01900000-0000-7000-8000-00000000a001", "--witness=01900000-0000-7000-8000-00000000a002"),
                new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8),
                new java.io.PrintStream(err, true, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(revoked).as("premise: the employee role is taken from the administrator: " + err.toString(java.nio.charset.StandardCharsets.UTF_8)).isZero();
        admin = new Staff(admin.employeeId(), signIn(admin.employeeId())); // a role change ends the sessions; the case needs a live one
        Throwable adminRefusal = assertRefusedUnchanged(hr, admin, "Synthetic rebound", syntheticWecom("admin-only"));
        assertThat(adminRefusal).as(String.valueOf(adminRefusal)).isInstanceOf(DomainException.class);
        assertThat(((DomainException) adminRefusal).errorCode()).isEqualTo(seeded);
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
        Staff secondLogin = new Staff(person.employeeId(), signIn(person.employeeId()));
        actIn(hr);

        change(person.employeeId(), (String) root(person.employeeId()).get("display_name"), null, null, rebound, TODAY);

        assertThat(root(person.employeeId()).get("wecom_user_id")).isEqualTo(rebound);
        assertThat(profileStatus(person)).as("the employee's session is revoked").isIn(401, 403);
        assertThat(profileStatus(secondLogin)).as("every session of the employee is revoked, not one").isIn(401, 403);
        assertThat(browserIdentifiers(person)).as("the employee's browser identifiers are gone").isZero();
        assertThat(profileStatus(bystander)).as("someone else's session stays").isEqualTo(200);
        assertThat(profileStatus(hr)).as("the caller's session stays").isEqualTo(200);
        assertThat(browserIdentifiers(bystander)).as("someone else's browser identifier stays").isEqualTo(1);

        assertOldLoginClosed(old, person);
        assertThat(signInWith(wecomCodeOf(rebound), person.employeeId()).userId()).as("the new WeCom user id opens the same account").isEqualTo(person.userId());
    }

    @Test
    @DisplayName("#959 用户 2026-11-02 第③道：只有普通员工角色的人，人事专员一人可清空其企业微信账号；清空也是改动，同事务作废该员工全部会话与浏览器标识，别人的不动；旧企业微信账号再登录打不开该账号")
    void aClearRevokesTheEmployeesSessionsAndTheOldWecomLogin() throws Exception {
        Staff person = staff();
        Staff bystander = staff();
        String old = wecomOf(person);
        browserIdentifier(person);
        browserIdentifier(bystander);
        Staff secondLogin = new Staff(person.employeeId(), signIn(person.employeeId()));
        actIn(hr);

        change(person.employeeId(), (String) root(person.employeeId()).get("display_name"), null, null, null, TODAY);

        assertThat(root(person.employeeId()).get("wecom_user_id")).isNull();
        assertThat(profileStatus(person)).as("the employee's session is revoked").isIn(401, 403);
        assertThat(profileStatus(secondLogin)).as("every session of the employee is revoked, not one").isIn(401, 403);
        assertThat(browserIdentifiers(person)).as("the employee's browser identifiers are gone").isZero();
        assertThat(profileStatus(bystander)).as("someone else's session stays").isEqualTo(200);
        assertThat(browserIdentifiers(bystander)).as("someone else's browser identifier stays").isEqualTo(1);
        assertOldLoginClosed(old, person);
    }

    @Test
    @DisplayName("#959 opus 复审必改 1 的攻击链：人事专员把自己的企业微信账号挪走、写到超级管理员档案上、或清空超级管理员的账号都被拒；其企业微信登录仍只进自己的账号")
    void aSpecialistCannotTakeOverASuperAdmin() throws Exception {
        Staff admin = staff("super_admin");
        String own = wecomOf(hr);

        assertRefusedUnchanged(hr, hr, "Synthetic moved away", syntheticWecom("moved-away"));
        assertRefusedUnchanged(hr, admin, "Synthetic takeover", own);
        assertRefusedUnchanged(hr, admin, "Synthetic takeover", null);

        Staff accomplice = staff(PERSONNEL_SPECIALIST_ROLE);
        assertRefusedUnchanged(accomplice, hr, "Synthetic moved by accomplice", syntheticWecom("moved-by-accomplice"));
        assertRefusedUnchanged(accomplice, hr, "Synthetic cleared by accomplice", null);

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
     * The change by this caller of the target's profile is refused; the target's root, session and browser identifiers, the caller's
     * session and the audit stay as they were. The refusal is returned.
     */
    private Throwable assertRefusedUnchanged(Staff caller, Staff target, String displayName, String wecomUserId) throws Exception {
        browserIdentifier(target);
        UUID person = target.employeeId();
        Map<String, Object> rootBefore = root(person);
        int audited = auditCount(AUDIT_PROFILE_CHANGED);
        long identifiers = browserIdentifiers(target);
        actIn(caller);

        Throwable failure = catchThrowable(() -> change(person, displayName, null, null, wecomUserId, TODAY));

        assertThat(failure).as("refused").isNotNull();
        assertThat(root(person)).as("the root is unchanged").isEqualTo(rootBefore);
        assertThat(auditCount(AUDIT_PROFILE_CHANGED)).isEqualTo(audited);
        assertThat(profileStatus(target)).as("the target's session stays").isEqualTo(200);
        assertThat(browserIdentifiers(target)).as("no browser identifier of the target revoked").isEqualTo(identifiers);
        assertThat(profileStatus(caller)).as("the caller's session stays").isEqualTo(200);
        return failure;
    }

    /** A custom role (no permission codes) made through the role use case by a super_admin in administrator mode; returns its code. */
    private String customRole() throws Exception {
        Staff administrator = staff("super_admin");
        String code = "wecom_custom_" + UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletResponse created = mvc.perform(post("/api/v1/platform/roles").with(csrf()).with(session(administratorMode(administrator.session())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Synthetic custom " + code + "\",\"enabled\":true,\"accessScope\":\"SELF\",\"permissionCodes\":[]}"))
                .andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        return code;
    }

    private void assertOldLoginClosed(String oldWecomUserId, Staff person) throws Exception {
        MockHttpServletResponse oldLogin = wecomLogin(wecomCodeOf(oldWecomUserId));
        assertThat(oldLogin.getStatus() != 200 || !text(person.userId()).equals(String.valueOf(JsonPath.read(oldLogin.getContentAsString(), "$.userId"))))
                .as("the old WeCom user id no longer opens the account: " + oldLogin.getStatus()).isTrue();
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
}
