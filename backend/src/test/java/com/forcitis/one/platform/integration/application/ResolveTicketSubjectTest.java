package com.forcitis.one.platform.integration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResolveTicketSubjectTest {

    @Test
    @DisplayName("P3B-L3 only the canonical signed 64-bit form is looked up: +123, 0123, -0 and a number past 64 bits find nobody and ask nothing")
    void onlyTheCanonicalFormIsLookedUp() {
        InMemorySourceReferences references = new InMemorySourceReferences()
                .confirm(ExternalSource.ORG_SOURCE, SourceObjectKind.EMPLOYEE, 123, TestAccounts.employee(123).value())
                .confirm(ExternalSource.ORG_SOURCE, SourceObjectKind.EMPLOYEE, 0, TestAccounts.employee(7).value())
                .confirm(ExternalSource.ORG_SOURCE, SourceObjectKind.EMPLOYEE, Long.MIN_VALUE, TestAccounts.employee(8).value());
        ResolveTicketSubject subjects = new ResolveTicketSubject(references);

        String pastSixtyFourBits = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE).toString();
        for (String notCanonical : new String[] {"+123", "0123", "-0", pastSixtyFourBits, " 123", ""}) {
            assertThat(subjects.employeeOf(notCanonical)).as(notCanonical).isEmpty();
        }
        assertThat(references.lookups()).as("nothing is looked up for a subject that is not canonical").isEmpty();

        assertThat(subjects.employeeOf("123")).contains(TestAccounts.employee(123));
        assertThat(subjects.employeeOf("0")).contains(TestAccounts.employee(7));
        assertThat(subjects.employeeOf(Long.toString(Long.MIN_VALUE))).contains(TestAccounts.employee(8));
        assertThat(subjects.employeeOf("124")).isEmpty();
        assertThat(references.allocations()).as("a lookup never allocates").isEmpty();
    }
}
