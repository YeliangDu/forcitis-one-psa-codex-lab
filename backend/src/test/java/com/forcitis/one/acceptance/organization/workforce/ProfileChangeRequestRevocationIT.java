package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PERSONNEL_SPECIALIST_ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.EmployeeAccountAccess;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

/**
 * #980 acceptance, design note section 6 line 6 (failure half) and section 5 item 2: the write of the profile and the revocation of the
 * subject's sessions and browser identifiers are one transaction. When the identity port fails during a confirmation, the profile stays as
 * it was and the request stays PENDING. The port is a spy over the real implementation, made to fail only in this case; the stub is set on the
 * spy behind the port's transaction proxy.
 */
class ProfileChangeRequestRevocationIT extends ProfileChangeRequestSupport {

    static final TestDatabase postgres = TestPostgres.database(ProfileChangeRequestRevocationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @MockitoSpyBean
    EmployeeAccountAccess access;

    /**
     * The spy behind the transaction proxy: revokeSessions is @Transactional(MANDATORY), so stubbing through the proxy on a thread without a
     * transaction is refused by the aspect before Mockito sees it (RULING-LEAD-20261103-05). Calls still go through the proxy.
     */
    private EmployeeAccountAccess spy() {
        return AopTestUtils.getUltimateTargetObject(access);
    }

    @AfterEach
    void realPortAgain() {
        reset(spy());
    }

    @Test
    @DisplayName("#980 完成标准 6（失败半边）：确认时撤销会话失败，整笔回滚：档案不变、请求仍为 PENDING、会话与浏览器标识保留；端口恢复后同一请求可以确认")
    void aFailedRevocationLeavesNothingApplied() throws Exception {
        Staff subject = privileged();
        UUID request = requested(hr, subject.employeeId(), "Synthetic rollback", syntheticWecom("rollback"));
        Map<String, Object> before = root(subject.employeeId());
        long identifiers = browserIdentifiers(subject);
        Staff second = staff(PERSONNEL_SPECIALIST_ROLE);
        doThrow(new IllegalStateException("synthetic: identity is down")).when(spy()).revokeSessions(any());

        MockHttpServletResponse failed = confirm(second, request);

        assertThat(failed.getStatus()).as(failed.getContentAsString()).isGreaterThanOrEqualTo(400);
        assertUntouched(subject, before, identifiers);
        assertThat(request(request).get("request_status")).isEqualTo("PENDING");

        reset(spy());
        assertThat(confirm(second, request).getStatus()).isEqualTo(200);
        assertThat(request(request).get("request_status")).isEqualTo("CONFIRMED");
    }
}
