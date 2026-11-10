package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.organization.attendance.domain.StandardHours;
import com.forcitis.one.organization.attendance.domain.StandardHoursRepository;
import com.forcitis.one.organization.attendance.events.StandardHoursVersionPublished;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Independent #71 acceptance.
 *
 * Baseline: c0d54a72a203e2df365b6690cc29585b46f39c99.
 * Author execution: not run.
 *
 * Frozen lock contract, after tail and before trigger/INSERT:
 * pg_advisory_xact_lock(hashtextextended(
 *   'organization.attendance.standard-hours:' || scope_uuid_text, 0))
 * DEFAULT uses the literal suffix DEFAULT.
 *
 * CommittedEvents observes AFTER_COMMIT, not attempted publication.
 */
@Import(StandardHoursScopeSerializationIT.EventConfiguration.class)
class StandardHoursScopeSerializationIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(
            StandardHoursScopeSerializationIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    private static final String URL =
            "/api/v1/organization/standard-hours-versions";
    private static final String LOCK_PREFIX =
            "organization.attendance.standard-hours:";
    private static final String AUDIT_ACTION =
            "organization.attendance.standard-hours.published";

    private static final RequestPostProcessor PUBLISHER = person(
            43,
            "organization.attendance:standard-hours:publish",
            "organization.attendance:standard-hours:read");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @MockitoSpyBean
    StandardHoursRepository repository;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CommittedEvents committedEvents;

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfiguration {
        @Bean
        CommittedEvents standardHoursCommittedEvents() {
            return new CommittedEvents();
        }
    }

    static class CommittedEvents {
        private final ConcurrentLinkedQueue<UUID> ids =
                new ConcurrentLinkedQueue<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void published(StandardHoursVersionPublished event) {
            ids.add(event.standardHoursVersionId().value());
        }

        List<UUID> among(Set<UUID> attempts) {
            return ids.stream().filter(attempts::contains).toList();
        }
    }

    @AfterEach
    void releaseSpy() {
        reset(repository);
    }

    @Test
    void sameTailPublishersWaitBeforeInsertAndOnlyOneCommits() throws Exception {
        UUID entity = createLegalEntity(entityCode(), "Synthetic lock scope");
        UUID otherEntity = createLegalEntity(entityCode(), "Synthetic other scope");
        LocalDate today = BusinessDay.today();
        int auditsBefore = auditedActions(AUDIT_ACTION).size();
        ConcurrentLinkedQueue<Integer> pids = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<UUID> attempts = new ConcurrentLinkedQueue<>();
        CountDownLatch bothReadTail = new CountDownLatch(2);

        doAnswer(call -> {
            Object tail = call.callRealMethod();
            pids.add(jdbc.sql("select pg_backend_pid()")
                    .query(Integer.class).single());
            bothReadTail.countDown();
            return tail;
        }).when(repository).tail(any());

        doAnswer(call -> {
            StandardHours version = call.getArgument(0);
            attempts.add(version.id());
            return call.callRealMethod();
        }).when(repository).insert(any());

        ExecutorService workers = Executors.newFixedThreadPool(3);
        try (Connection holder = schemaConnection("organization_attendance");
                Connection observer = schemaConnection("organization_attendance")) {
            holder.setAutoCommit(false);
            int holderPid = pid(holder);
            try (PreparedStatement lock = holder.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                lock.setString(1, LOCK_PREFIX + entity);
                lock.execute();
            }

            Future<MockHttpServletResponse> first = workers.submit(
                    () -> publish(entity, today.plusDays(5), "Synthetic first"));
            Future<MockHttpServletResponse> second = workers.submit(
                    () -> publish(entity, today.plusDays(6), "Synthetic second"));

            assertTrue(
                    bothReadTail.await(10, TimeUnit.SECONDS),
                    "PUBLISHERS_DID_NOT_REACH_REAL_TAIL");

            AtomicBoolean observedWait = new AtomicBoolean();
            try {
                Awaitility.await()
                        .atMost(Duration.ofSeconds(5))
                        .pollInterval(Duration.ofMillis(20))
                        .until(() -> {
                            List<Integer> current = List.copyOf(pids);
                            if (current.size() == 2
                                    && waitsOnHolder(
                                            observer, current.get(0), holderPid)
                                    && waitsOnHolder(
                                            observer, current.get(1), holderPid)) {
                                observedWait.set(true);
                                return true;
                            }
                            return first.isDone() || second.isDone();
                        });
            } catch (ConditionTimeoutException timeout) {
                // Convert missing required wait evidence to the named assertion.
            }

            assertTrue(
                    observedWait.get(),
                    "STANDARD_HOURS_SCOPE_LOCK_NOT_OBSERVED");
            assertThat(versionIds(observer, entity)).isEmpty();

            Future<MockHttpServletResponse> independent = workers.submit(
                    () -> publish(otherEntity, today.plusDays(5),
                            "Synthetic independent"));
            MockHttpServletResponse other = independent.get(10, TimeUnit.SECONDS);
            assertThat(other.getStatus())
                    .as(other.getContentAsString()).isEqualTo(200);
            assertThat(first.isDone()).isFalse();
            assertThat(second.isDone()).isFalse();

            holder.rollback();

            List<MockHttpServletResponse> answers = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));
            assertThat(answers.stream()
                    .map(MockHttpServletResponse::getStatus).sorted().toList())
                    .as(responseBodies(answers))
                    .containsExactly(200, 409);

            MockHttpServletResponse loser = answers.stream()
                    .filter(answer -> answer.getStatus() == 409)
                    .findFirst().orElseThrow();
            assertThat((String) JsonPath.read(
                    loser.getContentAsString(), "$.errorCode"))
                    .isEqualTo("ORGANIZATION_ATTENDANCE_STANDARD_HOURS_OVERLAP");

            Set<UUID> stored = new HashSet<>(versionIds(observer, entity));
            stored.addAll(versionIds(observer, otherEntity));
            assertThat(versionIds(observer, entity)).hasSize(1);
            assertThat(versionIds(observer, otherEntity)).hasSize(1);
            assertThat(attempts).hasSize(3);

            assertThat(auditedActions(AUDIT_ACTION).size() - auditsBefore)
                    .as("failed publication leaves no audit entry")
                    .isEqualTo(2);
            assertCommittedExactly(attempts, stored);

            reset(repository);
            MockHttpServletResponse next = publish(
                    entity, today.plusDays(8), "Synthetic legal successor");
            assertThat(next.getStatus())
                    .as(next.getContentAsString()).isEqualTo(200);
            UUID nextId = UUID.fromString(JsonPath.read(
                    next.getContentAsString(), "$.standardHoursVersionId"));
            attempts.add(nextId);
            stored.add(nextId);

            assertThat(versionIds(observer, entity)).hasSize(2);
            assertThat(auditedActions(AUDIT_ACTION).size() - auditsBefore)
                    .isEqualTo(3);
            assertCommittedExactly(attempts, stored);
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS),
                    "PUBLISHER_WORKERS_DID_NOT_TERMINATE");
        }
    }

    @Test
    void unrelatedDeadlockAtPublishIsNot409AndRollsBackEverything()
            throws Exception {
        UUID entity = createLegalEntity(entityCode(), "Synthetic deadlock scope");
        int auditsBefore = auditedActions(AUDIT_ACTION).size();
        AtomicReference<UUID> attemptedId = new AtomicReference<>();

        doAnswer(call -> {
            StandardHours version = call.getArgument(0);
            attemptedId.set(version.id());
            throw new PessimisticLockingFailureException(
                    "synthetic unrelated deadlock",
                    new SQLException("synthetic unrelated deadlock", "40P01"));
        }).when(repository).insert(any());

        MockHttpServletResponse response = publish(
                entity, BusinessDay.today().plusDays(5),
                "Synthetic unrelated deadlock");
        assertThat(response.getStatus())
                .as(response.getContentAsString())
                .isBetween(500, 599)
                .isNotEqualTo(409);
        assertThat(attemptedId.get()).isNotNull();

        try (Connection observer = schemaConnection("organization_attendance")) {
            assertThat(versionIds(observer, entity)).isEmpty();
        }
        assertThat(auditedActions(AUDIT_ACTION).size()).isEqualTo(auditsBefore);
        assertThat(committedEvents.among(Set.of(attemptedId.get()))).isEmpty();
    }

    private void assertCommittedExactly(
            ConcurrentLinkedQueue<UUID> attempts, Set<UUID> stored) {
        List<UUID> committed =
                committedEvents.among(new HashSet<>(attempts));
        assertThat(committed)
                .as("only committed version IDs produce committed events")
                .containsExactlyInAnyOrderElementsOf(stored);
        Set<UUID> failed = new HashSet<>(attempts);
        failed.removeAll(stored);
        assertThat(committedEvents.among(failed)).isEmpty();
    }

    private MockHttpServletResponse publish(
            UUID entity, LocalDate from, String basis) throws Exception {
        String body = "{\"legalEntityId\":\"" + entity
                + "\",\"effectiveFromDate\":\"" + from
                + "\",\"workdayMinutes\":480,\"adjustedWorkdayMinutes\":420,"
                + "\"basisRef\":\"" + basis + "\"}";
        return mvc.perform(post(URL).with(PUBLISHER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private static String responseBodies(
            List<MockHttpServletResponse> responses) {
        return responses.stream().map(response -> {
            try {
                return response.getContentAsString();
            } catch (Exception failure) {
                return failure.toString();
            }
        }).toList().toString();
    }

    private static int pid(Connection connection) throws SQLException {
        try (PreparedStatement query =
                connection.prepareStatement("select pg_backend_pid()");
                ResultSet rows = query.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static boolean waitsOnHolder(
            Connection observer, int waiter, int holder) throws SQLException {
        try (PreparedStatement query = observer.prepareStatement("""
                select exists (
                    select 1 from pg_locks
                     where pid = ? and locktype = 'advisory' and not granted
                ) and ? = any(pg_blocking_pids(?))
                """)) {
            query.setInt(1, waiter);
            query.setInt(2, holder);
            query.setInt(3, waiter);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private static List<UUID> versionIds(
            Connection observer, UUID entity) throws SQLException {
        try (PreparedStatement query = observer.prepareStatement("""
                select id
                  from organization_attendance.standard_hours_versions
                 where legal_entity_id = ?
                 order by version_number
                """)) {
            query.setObject(1, entity);
            try (ResultSet rows = query.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (rows.next()) {
                    ids.add(rows.getObject(1, UUID.class));
                }
                return ids;
            }
        }
    }

    private static String entityCode() {
        StringBuilder result = new StringBuilder("LK");
        for (int index = 0; index < 8; index++) {
            result.append((char) ('A'
                    + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
        }
        return result.toString();
    }
}
