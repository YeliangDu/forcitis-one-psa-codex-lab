package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.domain.ContractKind;
import com.forcitis.one.organization.workforce.domain.ContractStatus;
import com.forcitis.one.organization.workforce.domain.EmploymentContract;
import com.forcitis.one.organization.workforce.domain.EmploymentContractId;
import com.forcitis.one.organization.workforce.domain.EmploymentContractRepository;
import com.forcitis.one.organization.workforce.domain.SeparationBasisQueries;
import com.forcitis.one.organization.workforce.domain.SeparationInitiator;
import com.forcitis.one.organization.workforce.domain.SeparationRequest;
import com.forcitis.one.organization.workforce.domain.SeparationRequestId;
import com.forcitis.one.organization.workforce.domain.SeparationRequestRepository;
import com.forcitis.one.organization.workforce.domain.SeparationRequestSource;
import com.forcitis.one.organization.workforce.domain.SeparationRequestStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #1438 (WC1): the three repositories read and write the tables as the data design signed them; the state-changing writes come with WC2 and WC3. */
class ContractSeparationRepositoriesIntegrationTest extends WorkforceIntegrationSupport {
    private static final UserId HR = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000a1"));

    @Autowired
    EmploymentContractRepository contracts;
    @Autowired
    SeparationRequestRepository requests;
    @Autowired
    SeparationBasisQueries bases;

    @Test
    @DisplayName("#1438 a stored draft contract reads back whole, and the relationship lists it with its renewal in term order")
    void aContractRoundTrips() {
        UUID employee = PersonnelSeedSupport.employed(jdbc, clock, "RC-1").employee();
        var relationship = new EmploymentRelationshipId(EmploymentRelationshipSeed.relationship(jdbc, employee));
        var first = new EmploymentContract(new EmploymentContractId(UUID.randomUUID()), relationship, new EmployeeId(employee), ContractKind.FIXED_PERIOD, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), null, 3, null, null, ContractStatus.DRAFT, null, null, null, null, 0);
        var renewal = new EmploymentContract(new EmploymentContractId(UUID.randomUUID()), relationship, new EmployeeId(employee), ContractKind.OPEN_ENDED, LocalDate.of(2027, 1, 1),
                null, null, 0, first.id(), null, ContractStatus.DRAFT, null, null, null, null, 0);
        contracts.insert(first);
        contracts.insert(renewal);
        assertThat(contracts.find(first.id())).contains(first);
        assertThat(contracts.ofRelationship(relationship)).containsExactly(first, renewal);
        assertThat(contracts.find(new EmploymentContractId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("#1438 a stored request reads back whole; a SYSTEM request with a system-only basis is stored as approved")
    void aRequestRoundTrips() {
        UUID employee = PersonnelSeedSupport.employed(jdbc, clock, "RC-2").employee();
        var relationship = new EmploymentRelationshipId(EmploymentRelationshipSeed.relationship(jdbc, employee));
        var human = new SeparationRequest(new SeparationRequestId(UUID.randomUUID()), relationship, new EmployeeId(employee), SeparationInitiator.EMPLOYEE, "RESIGN_WITH_NOTICE",
                SeparationRequestSource.HR_ON_BEHALF, HR, null, LocalDate.of(2026, 3, 1), null, LocalDate.of(2026, 3, 31), null, null, null, null, null, null, "synthetic note", null,
                SeparationRequestStatus.REQUESTED, 0);
        requests.insert(human);
        assertThat(requests.find(human.id())).contains(human);
        var system = new SeparationRequest(new SeparationRequestId(UUID.randomUUID()), relationship, new EmployeeId(employee), SeparationInitiator.BOTH, "MIGRATION",
                SeparationRequestSource.SYSTEM, HR, HR, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), null, null, null, null, null, null, null, null,
                SeparationRequestStatus.COMPLETED, 0);
        requests.insert(system);
        assertThat(requests.ofRelationship(relationship)).containsExactly(system, human);
    }

    @Test
    @DisplayName("#1438 the basis list reads its seed: thirteen keys, two of them system-only, a key found by name")
    void theBasisListReads() {
        assertThat(bases.findAll()).hasSize(13).filteredOn(b -> b.systemOnly()).extracting(b -> b.key()).containsExactlyInAnyOrder("EMPLOYER_CHANGE", "MIGRATION");
        assertThat(bases.find("NO_FAULT_DISMISSAL")).get().satisfies(b -> {
            assertThat(b.employerInitiated()).isTrue();
            assertThat(b.defaultInitiator()).isEqualTo(SeparationInitiator.EMPLOYER);
        });
        assertThat(bases.find("NO_SUCH_KEY")).isEmpty();
    }
}
