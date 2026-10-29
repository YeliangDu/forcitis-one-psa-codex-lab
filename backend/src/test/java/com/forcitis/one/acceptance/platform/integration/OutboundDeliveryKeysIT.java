package com.forcitis.one.acceptance.platform.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P4Names;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.integration.application.DeliverWecomCards;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * R-1b P4 ADR-0033 section 7 item 3 and card decision D-4/D-5 (acceptance item 8): an outbound card is identified by the structured
 * delivery subject (kind, object id, purpose, trigger event id), not by a text key, and not by its recipients. The same task's
 * assignment card sent twice is one row; two transfers of one task by two different events are two rows; a handled note replaces
 * a card at most once and points at the card's delivery id; the WeCom task id of a message does not change when the same row is sent again.
 * Runs with the real gateway against a stub of the WeCom server (like WecomDeliveryIT); the structured key is built by the api's own
 * constructors found at run time, so this class compiles before the key type exists and fails at run time until it does.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true", "spring.datasource.hikari.connection-timeout=2000"})
class OutboundDeliveryKeysIT {

    static final String TOKEN_PATH = "/cgi-bin/gettoken";
    static final String SEND_PATH = "/cgi-bin/message/send";
    static final String UPDATE_PATH = "/cgi-bin/message/update_template_card";
    static final String DELIVERIES = "platform_integration.outbound_deliveries";

    static final WireMockServer wecom = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        wecom.start();
    }

    @DynamicPropertySource
    static void wecomSettings(DynamicPropertyRegistry registry) {
        registry.add("forcitis.platform.integration.wecom.enabled", () -> "true");
        registry.add("forcitis.platform.integration.wecom.base-url", wecom::baseUrl);
        registry.add("forcitis.platform.integration.wecom.corp-id", () -> "corp-" + UUID.randomUUID());
        registry.add("forcitis.platform.integration.wecom.secret", () -> "secret-" + UUID.randomUUID());
        registry.add("forcitis.platform.integration.wecom.agent-id", () -> "1000002");
        registry.add("forcitis.platform.integration.wecom.recipients.restricted", () -> "true");
        registry.add("forcitis.platform.integration.wecom.recipients.allowed", () -> "alice,carol");
        registry.add("forcitis.platform.integration.wecom.delivery.interval", () -> "PT1H");
        registry.add("forcitis.platform.integration.wecom.delivery.duplicate-task-error-codes", () -> "42014");
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    static final TestDatabase postgres = TestPostgres.database(OutboundDeliveryKeysIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    WecomGateway gateway;

    @Autowired
    DeliverWecomCards deliver;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    static final WecomUserId ALICE = new WecomUserId("alice");
    static final WecomUserId CAROL = new WecomUserId("carol");

    @BeforeEach
    void cleanSlate() {
        wecom.resetAll();
        wecom.stubFor(get(urlPathEqualTo(TOKEN_PATH)).willReturn(okJson("{\"errcode\":0,\"access_token\":\"token-1\",\"expires_in\":7200}")));
        jdbc.sql("delete from " + DELIVERIES).update();
    }

    /** The structured key: kind, object id, purpose, trigger event id, filled in the order the card names them. */
    static Object subject(String kind, UUID id, String purpose, UUID triggerEvent) {
        try {
            Class<?> type = Class.forName(P4Names.DELIVERY_SUBJECT_TYPE);
            RecordComponent[] components = type.getRecordComponents();
            Object[] arguments = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            int names = 0;
            int uuids = 0;
            for (int i = 0; i < components.length; i++) {
                Class<?> component = components[i].getType();
                types[i] = component;
                if (component.isEnum() || component == String.class) {
                    String name = names++ == 0 ? kind : purpose;
                    arguments[i] = component == String.class ? name : enumConstant(component, name);
                } else if (component == UUID.class) {
                    arguments[i] = uuids++ == 0 ? id : triggerEvent;
                } else if (component == Optional.class) {
                    arguments[i] = Optional.ofNullable(triggerEvent);
                } else {
                    throw new AssertionError("unexpected component " + components[i] + " of " + type);
                }
            }
            return type.getDeclaredConstructor(types).newInstance(arguments);
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("the delivery subject of the integration api is not there yet: " + missing, missing);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class<Enum>) type, name);
    }

    static Object card(Object subject, String title, WecomUserId... recipients) {
        try {
            Class<?> type = Class.forName(P4Names.WECOM_CARD_TYPE);
            RecordComponent[] components = type.getRecordComponents();
            Object[] arguments = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            int strings = 0;
            for (int i = 0; i < components.length; i++) {
                Class<?> component = components[i].getType();
                types[i] = component;
                if (component == List.class) {
                    arguments[i] = List.of(recipients);
                } else if (component == String.class) {
                    arguments[i] = strings++ == 0 ? title : strings == 2 ? "Synthetic description" : "https://one.example.test/tasks/synthetic";
                } else {
                    arguments[i] = subject;
                }
            }
            Constructor<?> constructor = type.getDeclaredConstructor(types);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("the WeCom card cannot be built from a delivery subject yet: " + missing, missing);
        }
    }

    private void queue(Object card) {
        tx.executeWithoutResult(status -> invoke("send", card));
    }

    private void markHandled(Object subject, String note) {
        tx.executeWithoutResult(status -> invoke("markHandled", subject, note));
    }

    private void invoke(String name, Object... arguments) {
        for (Method method : WecomGateway.class.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
                try {
                    method.invoke(gateway, arguments);
                    return;
                } catch (java.lang.reflect.InvocationTargetException failed) {
                    throw failed.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(failed.getCause());
                } catch (IllegalAccessException | IllegalArgumentException unexpected) {
                    throw new AssertionError(name + " does not take " + List.of(arguments), unexpected);
                }
            }
        }
        throw new AssertionError("WecomGateway has no " + name);
    }

    private void sendSucceeds(String responseCodeJson) {
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"" + responseCodeJson + "}")));
    }

    private long rowsOf(UUID subjectId, String purpose) {
        return jdbc.sql("select count(*) from " + DELIVERIES + " where " + P4Names.SUBJECT_KIND + " = :kind and " + P4Names.SUBJECT_ID + " = :id and "
                + P4Names.PURPOSE_KIND + " = :purpose and kind = 'CARD'").param("kind", P4Names.SUBJECT_APPROVAL_TASK).param("id", subjectId)
                .param("purpose", purpose).query(Long.class).single();
    }

    private void makeDue(UUID deliveryId) {
        jdbc.sql("update " + DELIVERIES + " set next_attempt_at = now() - interval '1 second' where id = :id").param("id", deliveryId).update();
    }

    private UUID cardIdOf(UUID subjectId, String purpose) {
        return jdbc.sql("select id from " + DELIVERIES + " where " + P4Names.SUBJECT_ID + " = :id and " + P4Names.PURPOSE_KIND + " = :purpose and kind = 'CARD'")
                .param("id", subjectId).param("purpose", purpose).query(UUID.class).single();
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 7 项第 3 条：外发投递按对象、用途去重——同一任务的指派卡重复发送只一行、只发一次；收件人不进去重键")
    void theSameTaskAndPurposeIsOneRowWhoeverTheRecipientsAre() {
        UUID task = UUID.randomUUID();
        sendSucceeds(",\"response_code\":\"rc-1\"");

        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_ASSIGNED, null), "Approval to do", ALICE));
        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_ASSIGNED, null), "Approval to do", ALICE));
        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_ASSIGNED, null), "Approval to do", CAROL));
        deliver.execute();
        deliver.execute();

        assertThat(rowsOf(task, P4Names.PURPOSE_ASSIGNED)).isEqualTo(1);
        wecom.verify(1, postRequestedFor(urlPathEqualTo(SEND_PATH)));
        assertThat(jdbc.sql("select count(*) from " + DELIVERIES).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 7 项第 3 条：同一任务两次转交（不同事件）各一行，同一事件重复只一行；不同用途各自一行")
    void twoTransfersByTwoEventsAreTwoRows() {
        UUID task = UUID.randomUUID();
        UUID firstEvent = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();

        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_TRANSFERRED, firstEvent), "Transferred", ALICE));
        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_TRANSFERRED, secondEvent), "Transferred", CAROL));
        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_TRANSFERRED, firstEvent), "Transferred", ALICE));
        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_ASSIGNED, null), "Assigned", ALICE));
        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_OVERDUE, null), "Overdue", ALICE));

        assertThat(rowsOf(task, P4Names.PURPOSE_TRANSFERRED)).isEqualTo(2);
        assertThat(jdbc.sql("select " + P4Names.TRIGGER_EVENT_ID + " from " + DELIVERIES + " where " + P4Names.PURPOSE_KIND + " = :purpose")
                .param("purpose", P4Names.PURPOSE_TRANSFERRED).query(UUID.class).list()).containsExactlyInAnyOrder(firstEvent, secondEvent);
        assertThat(rowsOf(task, P4Names.PURPOSE_ASSIGNED)).isEqualTo(1);
        assertThat(rowsOf(task, P4Names.PURPOSE_OVERDUE)).isEqualTo(1);
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 7 项第 3 条：并发重复投递同一张卡只得一行，且都不报错")
    void concurrentDuplicatesLeaveOneRow() throws Exception {
        UUID task = UUID.randomUUID();
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                done.add(pool.submit(() -> {
                    go.await();
                    queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_ASSIGNED, null), "Approval to do", ALICE));
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> one : done) {
                one.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(rowsOf(task, P4Names.PURPOSE_ASSIGNED)).isEqualTo(1);
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 7 项第 3 条 / D-4：已处理说明每张卡至多一条，指向原卡的投递 ID（target_delivery_id），对象与用途取原卡的值；重复标记不再多出一行，也只更新一次企业微信")
    void aCardIsReplacedByOneNoteThatPointsAtIt() {
        UUID task = UUID.randomUUID();
        Object subject = subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_ASSIGNED, null);
        sendSucceeds(",\"response_code\":\"rc-12\"");
        wecom.stubFor(post(urlPathEqualTo(UPDATE_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"}")));
        queue(card(subject, "Approval to do", ALICE, CAROL));
        deliver.execute();
        UUID cardId = cardIdOf(task, P4Names.PURPOSE_ASSIGNED);

        markHandled(subject, "handled by Dave");
        markHandled(subject, "handled by Dave");
        deliver.execute();
        deliver.execute();

        List<Map<String, Object>> notes = jdbc.sql("select * from " + DELIVERIES + " where kind = 'HANDLED_NOTE'").query().listOfRows();
        assertThat(notes).hasSize(1);
        Map<String, Object> note = notes.getFirst();
        assertThat(note.get(P4Names.TARGET_DELIVERY_ID)).isEqualTo(cardId);
        assertThat(note.get(P4Names.SUBJECT_ID)).isEqualTo(task);
        assertThat(note.get(P4Names.SUBJECT_KIND)).isEqualTo(P4Names.SUBJECT_APPROVAL_TASK);
        assertThat(note.get(P4Names.PURPOSE_KIND)).isEqualTo(P4Names.PURPOSE_ASSIGNED);
        assertThat(note.get("status")).isEqualTo("SENT");
        wecom.verify(1, postRequestedFor(urlPathEqualTo(UPDATE_PATH)));
        assertThat(jdbc.sql("select handled_result from " + DELIVERIES + " where id = :id").param("id", cardId).query(String.class).single()).isEqualTo("UPDATED");
        assertThat(rowsOf(task, P4Names.PURPOSE_ASSIGNED)).as("the card is still one").isEqualTo(1);
    }

    @Test
    @DisplayName("R-1b P4 D-5：同一行重试时企业微信 task_id 不变；不同投递行、原卡与其说明行的 task_id 各自独立")
    void theWeComTaskIdOfARowDoesNotChangeOnRetry() {
        UUID task = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Object subject = subject(P4Names.SUBJECT_APPROVAL_TASK, task, P4Names.PURPOSE_ASSIGNED, null);
        wecom.stubFor(post(urlPathEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(500)));
        queue(card(subject, "Approval to do", ALICE));
        queue(card(subject(P4Names.SUBJECT_APPROVAL_TASK, other, P4Names.PURPOSE_ASSIGNED, null), "Another approval", ALICE));
        deliver.execute();
        sendSucceeds(",\"response_code\":\"rc-1\"");
        makeDue(cardIdOf(task, P4Names.PURPOSE_ASSIGNED));
        makeDue(cardIdOf(other, P4Names.PURPOSE_ASSIGNED));
        deliver.execute();

        List<String> taskIds = new ArrayList<>();
        for (LoggedRequest request : wecom.findAll(postRequestedFor(urlPathEqualTo(SEND_PATH)))) {
            taskIds.add(JsonPath.read(request.getBodyAsString(), "$.template_card.task_id"));
        }
        assertThat(taskIds).as("two attempts for each of the two rows").hasSize(4);
        assertThat(taskIds).allSatisfy(id -> assertThat(id).matches("[A-Za-z0-9-]{1,128}"));
        assertThat(taskIds.stream().distinct().count()).as("one task id for each row, the same on both attempts").isEqualTo(2);
        assertThat(taskIds.get(0)).as("the first row's attempts").isIn(taskIds.get(2), taskIds.get(3));

        wecom.stubFor(post(urlPathEqualTo(UPDATE_PATH)).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\"}")));
        markHandled(subject, "handled by Dave");
        deliver.execute();
        List<LoggedRequest> updates = wecom.findAll(postRequestedFor(urlPathEqualTo(UPDATE_PATH)));
        assertThat(updates).hasSize(1);
        assertThat((String) JsonPath.read(updates.getFirst().getBodyAsString(), "$.template_card.task_id")).as("the note has a task id of its own")
                .doesNotContain("handled").isNotEqualTo(taskIds.get(0)).isNotEqualTo(taskIds.get(1)).isNotNull();
    }
}
