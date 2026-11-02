package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #959 (one-design DD on #959, section 2; ADR-0027 four-eyes confirmation of numbering templates): until a business administrator has
 * confirmed the employee code template, a HIRE of a new person is refused and writes nothing. A class of its own: the confirmation of the
 * other tests would otherwise be in force in the same database.
 */
class EmployeeHireUnconfirmedCodeTemplateIT extends EmployeeHireSupport {

    static final TestDatabase postgres = TestPostgres.database(EmployeeHireUnconfirmedCodeTemplateIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#959 one-design 数据设计第 2 节：工号模板未经业务管理员确认时新人入职拒收（编号模块的「规则未确认」），不留任何行")
    void aHireBeforeTheTemplateIsConfirmedIsRefused() throws Exception {
        Counts before = counts();

        Throwable failure = catchThrowable(() -> hire("Synthetic too early", null, null, null));

        assertThat(failure).isInstanceOf(DomainException.class);
        assertThat(((DomainException) failure).errorCode().name()).isEqualTo("PLATFORM_NUMBERING_RULE_NOT_CONFIRMED");
        assertThat(counts()).isEqualTo(before);
    }
}
