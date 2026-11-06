package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1436 acceptance part, user ruling on the card (comment issuecomment-6669745823): the second stage goes to a holder of the payroll role who is not the
 * initiator, so a request by the one and only holder has nobody to approve it and is refused when it is made. Its own database (class), because the
 * holders of the role are counted over the whole database.
 */
class BonusInputSoleApproverIT extends BonusInputSupport {

    static final TestDatabase postgres = TestPostgres.database(BonusInputSoleApproverIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Override
    boolean secondHolderFromTheStart() {
        return false;
    }

    @Test
    @DisplayName("#1436 PY3 用户裁决：发起人是薪资专员角色唯一持有人时申请得 ORGANIZATION_PAYROLL_BONUS_INPUT_NO_APPROVER 且不发起审批；有了第二位持有人后同样的申请被接受")
    void aRequestByTheOnlyHolderOfThePayrollRoleHasNobodyToApproveIt() throws Exception {
        UUID relationship = employedRelationship();
        int before = bonusApprovals().size();

        assertRefusedWith(() -> request(specialistAccount, requestOf(relationship, legalEntity.value(), OCTOBER, "100.00", "CNY", "YEAR_END", UUID.randomUUID())),
                "ORGANIZATION_PAYROLL_BONUS_INPUT_NO_APPROVER");
        assertThat(bonusApprovals()).as("no approval for a request nobody could approve").hasSize(before);

        grantByOperations(secondSpecialist, PAYROLL_ROLE);
        request(specialistAccount, requestOf(relationship, legalEntity.value(), OCTOBER, "100.00", "CNY", "YEAR_END", UUID.randomUUID()));
        assertThat(bonusApprovals()).as("with a second holder the same request is accepted").hasSize(before + 1);
    }
}
