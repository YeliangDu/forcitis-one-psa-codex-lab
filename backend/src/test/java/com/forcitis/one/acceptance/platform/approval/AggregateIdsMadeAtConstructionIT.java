package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Support;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P5 ADR-0033 section 1 item 9, written like RULING-P4-3 (card acceptance item 6): the work calendar year, the legal entity
 * attribution and the numbering rule get their id when the aggregate is built, from the injected IdGenerator, in domain or application
 * code; the repository does not make it. The generator is replaced by one that remembers who asked for each id.
 */
class AggregateIdsMadeAtConstructionIT extends P5Support {

    @TestConfiguration
    static class FallingIds {
        @Bean
        @Primary
        DescendingIds descendingIds() {
            return new DescendingIds();
        }
    }

    static final TestDatabase postgres = TestPostgres.database(AggregateIdsMadeAtConstructionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    DescendingIds ids;

    private List<UUID> keysOf(String schema, String table, String column) throws Exception {
        List<UUID> keys = new ArrayList<>();
        for (Map<String, Object> row : query(schema, "select " + column + " from " + table)) {
            keys.add((UUID) row.get(column));
        }
        return keys;
    }

    private void assertMadeBeforeTheRepository(String what, List<UUID> keys, boolean requireAny) {
        List<String> wrong = new ArrayList<>();
        int found = 0;
        for (UUID key : keys) {
            DescendingIds.Made made = ids.madeAs(key);
            if (made == null) {
                continue;
            }
            found++;
            if (made.callers().stream().anyMatch(caller -> caller.contains(".infrastructure.persistence."))) {
                wrong.add(key + " was asked for below a repository");
            } else if (made.callers().stream().noneMatch(caller -> caller.contains(".domain.") || caller.contains(".application."))) {
                wrong.add(key + " was asked for outside domain and application code");
            }
        }
        assertThat(wrong).as("ids of %s", what).isEmpty();
        if (requireAny) {
            assertThat(found).as("rows of %s whose id came from the injected generator", what).isPositive();
        }
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 1 项第 9 条：工作日历年度、法人归属、编号规则的 ID 在构造时取得，仓储不生成——ID 出自注入的 IdGenerator，取号调用栈不在 infrastructure.persistence")
    void theIdsAreMadeBeforeTheRepositorySeesTheAggregate() throws Exception {
        UUID maintainerEmployee = newEmployeeId();
        employee(maintainerEmployee, unit, null);
        grantByOperations(maintainerEmployee, "org_data_admin");
        grantByOperations(maintainerEmployee, "business_admin");
        SignedIn maintainer = signIn(maintainerEmployee);
        attributeEmployee(maintainer, initiator, legalEntity.value());
        confirmSeededNumberingRule("TIMESHEET", maintainer);

        assertMadeBeforeTheRepository("work_calendar_years", keysOf("organization_attendance", "work_calendar_years", "id"), true);
        assertMadeBeforeTheRepository("legal_entity_attributions", keysOf("organization_workforce", "legal_entity_attributions", "attribution_id"), true);
        assertMadeBeforeTheRepository("numbering_rules", keysOf("platform_numbering", "numbering_rules", "numbering_rule_id"), false);
    }
}
