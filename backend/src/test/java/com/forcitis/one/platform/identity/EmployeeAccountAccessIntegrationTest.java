package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeAccountAccess;
import com.forcitis.one.kernel.EmployeeId;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class EmployeeAccountAccessIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    EmployeeAccountAccess access;

    @Autowired
    TransactionTemplate tx;

    private int currentStatus(String session) throws Exception {
        return mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("#959 the revocation of an employee's sessions is part of the caller's transaction: it rolls back with it, and takes effect when it commits")
    void revokingSessionsJoinsTheCallersTransaction() throws Exception {
        String session = loginWithWecom(1001);
        EmployeeId employee = TestAccounts.employee(1001);
        assertThat(currentStatus(session)).isEqualTo(200);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            access.revokeSessions(employee);
            throw new IllegalStateException("the profile write fails after the revocation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(currentStatus(session)).as("rolled back with the caller").isEqualTo(200);

        tx.executeWithoutResult(status -> access.revokeSessions(employee));
        assertThat(currentStatus(session)).as("committed with the caller").isEqualTo(401);
    }
}
