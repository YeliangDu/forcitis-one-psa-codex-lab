package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.api.EmployeeLabel;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class EmployeeLabelsIT {
    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IdGenerator ids;

    @Autowired
    EmployeeLabels labels;

    @Test
    @DisplayName("#1173 employee labels come from employees rows and omit missing ids")
    void labelsIncludeOnlyPresentEmployees() {
        EmployeeId first = employee("Example Cedar", "QL-CEDAR");
        EmployeeId second = employee("Example Willow", "QL-WILLOW");
        EmployeeId missing = new EmployeeId(ids.next());
        EmployeeId bystander = employee("Example Birch", "QL-BIRCH");

        Map<EmployeeId, EmployeeLabel> result = labels.labelsOf(Set.of(first, second, missing));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                first, new EmployeeLabel("Example Cedar", "QL-CEDAR"),
                second, new EmployeeLabel("Example Willow", "QL-WILLOW")));
        assertThat(result).doesNotContainKeys(missing, bystander);
    }

    @Test
    @DisplayName("#1173 an empty employee id set returns an empty label map")
    void emptySetReturnsEmptyMap() {
        assertThat(labels.labelsOf(Set.of())).isEmpty();
    }

    private EmployeeId employee(String displayName, String employeeCode) {
        EmployeeId employee = new EmployeeId(ids.next());
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, employment_status)
                values (:id, :code, :name, 'EMPLOYED')
                """).param("id", employee.value()).param("code", employeeCode).param("name", displayName).update();
        return employee;
    }
}
