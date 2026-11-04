package com.forcitis.one.organization.workforce.application;

import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("NITS-1 项5（DR-TB01-FU1-1）：员工存在 = employees 有该 ID 的行，不要求任职，也不要求在职")
class Nits1EmployeeExistenceIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    EmployeeLabels labels;

    @Autowired
    OrgDirectory directory;

    private void rowOnly(EmployeeId id, long serial) {
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (:id, :code, :name, 'EMPLOYED')")
                .param("id", id.value()).param("code", "ROW" + serial).param("name", "Synthetic row-only person " + serial).update();
    }

    private boolean exists(EmployeeId id) {
        return labels.labelsOf(java.util.Set.of(id)).containsKey(id);
    }

    @Test
    @DisplayName("NITS-1 employeeExists 对有行无任职、离职、在职员工为真，对不存在的 ID 为假")
    void anEmployeesRowIsEnough() {
        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 2, 100L, null));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        jdbc.sql("update organization_workforce.employees set employment_status = 'SEPARATED' where id = :id").param("id", TestAccounts.employee(2).value()).update();
        EmployeeId withoutAssignment = TestAccounts.employee(500);
        rowOnly(withoutAssignment, 500);

        assertThat(exists(TestAccounts.employee(1))).as("employed, with an assignment").isTrue();
        assertThat(exists(TestAccounts.employee(2))).as("separated").isTrue();
        assertThat(exists(withoutAssignment)).as("a row and no assignment").isTrue();
        assertThat(exists(TestAccounts.employee(999))).as("no row").isFalse();
    }

    @Test
    @DisplayName("NITS-1 findEmployee 对无任职员工仍返回空（语义不变）")
    void findEmployeeStillNeedsAnAssignment() {
        orgSource.snapshot = tree(person(1, 2, 100L, null));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        EmployeeId withoutAssignment = TestAccounts.employee(500);
        rowOnly(withoutAssignment, 500);

        assertThat(directory.findEmployee(withoutAssignment)).isEmpty();
        assertThat(directory.findEmployee(TestAccounts.employee(1))).isPresent();
    }
}
