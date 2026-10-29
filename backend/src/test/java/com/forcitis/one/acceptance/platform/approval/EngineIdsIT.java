package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support;
import com.forcitis.one.acceptance.P4Names;
import com.forcitis.one.kernel.Uuids;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P4 ADR-0033 guard 10 (FlowableIdTest), item 8 item 1: the engine takes its ids from the kernel id generator, so the process
 * instance, its executions, its tasks and their identity links all have version 7 ids made at the injected clock's time (RULE-386).
 * The clock is moved to an instant of its own before each start so that the millisecond counter of the generator has not been used up
 * by what the test class did before.
 */
class EngineIdsIT extends ApprovalP4Support {

    static final TestDatabase postgres = TestPostgres.database(EngineIdsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static boolean canonical(String text) {
        try {
            return UUID.fromString(text).toString().equals(text);
        } catch (IllegalArgumentException notAUuid) {
            return false;
        }
    }

    private List<String> engineIdsOf(String instanceId) throws SQLException {
        List<String> ids = new ArrayList<>();
        for (String sql : List.of("select id_ from act_ru_execution where proc_inst_id_ = ?", "select id_ from act_ru_task where proc_inst_id_ = ?",
                "select id_ from act_ru_identitylink where proc_inst_id_ = ? or task_id_ in (select id_ from act_ru_task where proc_inst_id_ = ?)")) {
            Object[] parameters = sql.contains("task_id_ in") ? new Object[] {instanceId, instanceId} : new Object[] {instanceId};
            for (Map<String, Object> row : rows(sql, parameters)) {
                ids.add((String) row.get("id_"));
            }
        }
        return ids;
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 守护 10 FlowableIdTest：新启动的流程实例与其任务的 ID 都是 version 7 的 uuid；固定注入 Clock 时 ID 的前 48 位等于该时刻毫秒（RULE-386）")
    void instancesTasksAndExecutionsGetTimeOrderedIdsAtTheInjectedTime() throws Exception {
        for (int day = 1; day <= 2; day++) {
            clock.set(NOW.plus(Duration.ofHours(day)));
            var document = startProjectHours(projectManager, secondProjectManager);

            List<Map<String, Object>> requests = rows("select current_instance_id from approval_requests where " + P4Names.DOCUMENT_ID + " = ?",
                    uuidOf(document));
            assertThat(requests).hasSize(1);
            String instance = requests.getFirst().get("current_instance_id").toString();
            List<UUID> tasks = openTaskIds(document);
            assertThat(tasks).as("one task for each project manager").hasSize(2);

            List<String> ids = new ArrayList<>(engineIdsOf(instance));
            ids.add(instance);
            tasks.forEach(task -> ids.add(task.toString()));
            assertThat(ids).as("the engine rows of the instance").hasSizeGreaterThan(4).allSatisfy(id -> {
                assertThat(canonical(id)).as("%s is a lower-case canonical UUID", id).isTrue();
                UUID parsed = UUID.fromString(id);
                assertThat(parsed.version()).as("version of %s", id).isEqualTo(7);
                assertThat(parsed.getMostSignificantBits() >>> 16).as("the time of %s", id).isEqualTo(clock.instant().toEpochMilli());
                assertThat(Uuids.instantOf(parsed)).isEqualTo(java.time.Instant.ofEpochMilli(clock.instant().toEpochMilli()));
            });
        }
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 8 项第 1 条：引擎的部署 ID 与流程定义也走 kernel 生成器，部署 ID 是 version 7 的 uuid（act_* 其余 ID 不另豁免）")
    void deploymentIdsAreTimeOrderedToo() throws Exception {
        List<Map<String, Object>> deployments = rows("select id_ from act_re_deployment");

        assertThat(deployments).isNotEmpty().allSatisfy(row -> {
            String id = (String) row.get("id_");
            assertThat(canonical(id)).as("%s is a lower-case canonical UUID", id).isTrue();
            assertThat(UUID.fromString(id).version()).as("version of deployment %s", id).isEqualTo(7);
        });
    }
}
