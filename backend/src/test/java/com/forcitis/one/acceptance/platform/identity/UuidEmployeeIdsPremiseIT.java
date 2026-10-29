package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P3b ★ (ADR-0033 section 3 items 1 and 3; card section 4 items 8 and 9): the reverse tests of the "本人" gates (EmergencyLoginEmploymentIT,
 * HeldSeparationReleaseIT, OwnAttributionIT, ApprovalAccountsIT, RetractionInitiatorAccountsIT, TimesheetOwnerAccountsIT, OwnRolesIT,
 * AuthorizationConfirmerAccountsIT, AccountingCodeConfirmerAccountsIT, NumberingRuleConfirmerIT) and AccountEnsureConcurrencyIT run again
 * unchanged once the employee ids are UUIDs. This case pins that they then really run with UUIDs: the build's EmployeeId wraps a UUID,
 * the employee id an account names is the UUID the employee entry has, and it is not the account's own UUID.
 */
class UuidEmployeeIdsPremiseIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(UuidEmployeeIdsPremiseIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 3 条：「本人」闸门反向测试的前提——员工 ID 为 UUID，账号所指员工 ID 即员工条目的 UUID，且与用户 ID 不同")
    void theGatesRunWithUuidEmployeeIds() throws Exception {
        assertThat(UUID_IDS).as("kernel EmployeeId wraps a UUID (ADR-0033 section 1)").isTrue();
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);

        SignedIn signedIn = signIn(person);

        Object named;
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement select = identity.prepareStatement("select employee_id from user_accounts where user_id = ?")) {
            select.setObject(1, signedIn.userId());
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                named = rs.getObject(1);
            }
        }
        assertThat(named).as("user_accounts.employee_id is a uuid").isInstanceOf(UUID.class).isEqualTo(person);
        assertThat(signedIn.userId()).as("the account id is not the employee id").isNotEqualTo(person);
    }
}
