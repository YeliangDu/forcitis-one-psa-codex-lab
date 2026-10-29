package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.kernel.WorkContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** ADR-0033 section 2: allocating own ids to source objects, and the outbound lookup of a source id by own id. */
class SourceReferenceAllocationIT extends AcceptanceSupport {

    static final String ORG_SOURCE = "ORG_SOURCE";
    static final String ACCOUNTING_SYSTEM = "ACCOUNTING_SYSTEM";
    static final String EMPLOYEE = "EMPLOYEE";
    static final String ORG_UNIT = "ORG_UNIT";

    static final TestDatabase postgres = TestPostgres.database(SourceReferenceAllocationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    PlatformTransactionManager transactions;

    private SourceReferencesProbe references;
    private Connection integration;

    @BeforeEach
    void reachTheService() throws Exception {
        references = SourceReferencesProbe.of(context);
        integration = schemaConnection("platform_integration");
    }

    @AfterEach
    void closeConnection() throws Exception {
        if (integration != null) {
            integration.close();
        }
    }

    private static <T> T asSyncJob(Supplier<T> work) {
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            return work.get();
        }
    }

    private UUID allocate(String sourceSystem, String kind, long sourceId) {
        return asSyncJob(() -> references.allocate(sourceSystem, kind, sourceId));
    }

    private List<String> rows(String sourceSystem, String kind, long sourceId) throws Exception {
        try (PreparedStatement select = integration.prepareStatement("""
                select own_id, confirmation_status from source_references
                 where source_system = ? and source_object_kind = ? and source_id = ?
                """)) {
            select.setString(1, sourceSystem);
            select.setString(2, kind);
            select.setLong(3, sourceId);
            List<String> found = new ArrayList<>();
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    found.add(rs.getObject("own_id", UUID.class) + " " + rs.getString("confirmation_status"));
                }
            }
            return found;
        }
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 2 条：两个线程并发首次分配同一来源对象，只得到一个自有 ID、只留一行已确认对照")
    void concurrentFirstSightGivesOneOwnId() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                long sourceId = -7_000_000L - round;
                CyclicBarrier together = new CyclicBarrier(2);
                List<Future<UUID>> both = new ArrayList<>();
                for (int thread = 0; thread < 2; thread++) {
                    both.add(pool.submit(() -> {
                        together.await(10, TimeUnit.SECONDS);
                        return allocate(ORG_SOURCE, EMPLOYEE, sourceId);
                    }));
                }
                UUID first = both.get(0).get(20, TimeUnit.SECONDS);
                UUID second = both.get(1).get(20, TimeUnit.SECONDS);

                assertThat(second).as("round %d: both callers read back the one own id", round).isEqualTo(first);
                assertThat(first.version()).as("own ids are UUIDv7").isEqualTo(7);
                assertThat(rows(ORG_SOURCE, EMPLOYEE, sourceId)).as("round %d: one reference row, confirmed on allocation", round)
                        .containsExactly(first + " CONFIRMED");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条：组织源来源 ID 可为负数（含 Long.MIN_VALUE 与 -1），经来源对照映射到自有 UUIDv7，原值存取不变并可反查")
    void negativeSourceIdsAreOrdinary() throws Exception {
        long[] sourceIds = {Long.MIN_VALUE, -1L, 1L, Long.MAX_VALUE};
        Set<UUID> ownIds = new HashSet<>();
        for (long sourceId : sourceIds) {
            UUID own = allocate(ORG_SOURCE, EMPLOYEE, sourceId);

            assertThat(own.version()).isEqualTo(7);
            assertThat(allocate(ORG_SOURCE, EMPLOYEE, sourceId)).as("seeing %d again gives the same own id", sourceId).isEqualTo(own);
            assertThat(rows(ORG_SOURCE, EMPLOYEE, sourceId)).as("source id %d is stored as it is", sourceId).containsExactly(own + " CONFIRMED");
            assertThat(asSyncJob(() -> references.sourceIdFor(ORG_SOURCE, EMPLOYEE, own, TODAY))).as("the outbound lookup gives %d back", sourceId)
                    .isEqualTo(sourceId);
            ownIds.add(own);
        }
        assertThat(ownIds).as("-1 and 1 are two source objects; the sign is never dropped").hasSize(sourceIds.length);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 1 条：来源对象类别属于自然键，同一来源 ID 在员工与组织单元下各得一个自有 ID")
    void theObjectKindIsPartOfTheKey() {
        UUID person = allocate(ORG_SOURCE, EMPLOYEE, 7_100_001L);
        UUID unit = allocate(ORG_SOURCE, ORG_UNIT, 7_100_001L);

        assertThat(unit).isNotEqualTo(person);
        assertThat(allocate(ORG_SOURCE, ORG_UNIT, 7_100_001L)).isEqualTo(unit);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 2 条：对照先于领域写入提交，领域写入回滚后再次见到同一来源对象仍得到同一自有 ID")
    void theReferenceSurvivesARolledBackDomainWrite() throws Exception {
        TransactionTemplate domainWrite = new TransactionTemplate(transactions);
        UUID allocatedInside = domainWrite.execute(status -> {
            UUID own = allocate(ORG_SOURCE, EMPLOYEE, -7_200_001L);
            status.setRollbackOnly();
            return own;
        });

        assertThat(rows(ORG_SOURCE, EMPLOYEE, -7_200_001L)).as("the reference was committed on its own").containsExactly(allocatedInside + " CONFIRMED");
        assertThat(allocate(ORG_SOURCE, EMPLOYEE, -7_200_001L)).isEqualTo(allocatedInside);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 5 条：出站按 (来源系统, 类别, 自有 ID) 取外部标识，查不到即失败并告警，不返回占位值")
    void outboundWithoutAReferenceFailsAndAlerts() {
        UUID person = allocate(ORG_SOURCE, EMPLOYEE, 7_300_001L);
        UUID nobody = UUID.fromString("01900000-0000-7000-8000-00000000f001");

        int mark = logMark();
        Throwable unknownOwnId = catchThrowable(() -> asSyncJob(() -> references.sourceIdFor(ORG_SOURCE, EMPLOYEE, nobody, TODAY)));
        assertThat(unknownOwnId).as("no reference for this own id: the lookup fails").isNotNull();
        assertThat(alertsSince(mark)).as("and alerts").anyMatch(line -> line.startsWith("ALERT platform.integration"));

        mark = logMark();
        Throwable otherSystem = catchThrowable(() -> asSyncJob(() -> references.sourceIdFor(ACCOUNTING_SYSTEM, EMPLOYEE, person, TODAY)));
        assertThat(otherSystem).as("a reference in another source system is no answer").isNotNull();
        assertThat(alertsSince(mark)).anyMatch(line -> line.startsWith("ALERT platform.integration"));

        Throwable otherKind = catchThrowable(() -> asSyncJob(() -> references.sourceIdFor(ORG_SOURCE, ORG_UNIT, person, TODAY)));
        assertThat(otherKind).as("a reference of another object kind is no answer").isNotNull();
    }
}
