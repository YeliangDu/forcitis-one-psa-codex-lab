package com.forcitis.one.scenario;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One HTTP request: the event it publishes, the handler that runs on it, the deliveries both queue and the outbound call share one correlation id. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
@Import(CorrelationIT.Probe.class)
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIT {

    static final com.forcitis.one.platform.integration.api.DeliverySubject PROBE_REQUEST = com.forcitis.one.platform.integration.api.DeliverySubject.of(
            com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_ASSIGNED, TestIds.uuid(901));
    static final com.forcitis.one.platform.integration.api.DeliverySubject PROBE_HANDLER = com.forcitis.one.platform.integration.api.DeliverySubject.of(
            com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_ASSIGNED, TestIds.uuid(902));

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
        registry.add("forcitis.platform.integration.wecom.recipients.restricted", () -> "false");
        registry.add("forcitis.platform.integration.wecom.delivery.interval", () -> "PT1H");
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    static final TestDatabase postgres = TestPostgres.database(CorrelationIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Probe.Seen seen;

    @Autowired
    com.forcitis.one.platform.integration.application.DeliverWecomCards deliver;

    @NotAudited(reason = "a probe event of this test; it is about no business object")
    record Thing(EventEnvelope envelope, UserId userId) implements DomainEvent {
    }

    static final class ThingAggregate extends AggregateRoot {
        ThingAggregate() {
            super(0, 0);
        }

        void happen() {
            record((envelopes, version) -> new Thing(envelopes.wrap(new EventDraft("platform.eventing.Thing", 1, "Thing", TestIds.uuid(1), version, null, null, List.of())), TestAccounts.account(1)));
        }
    }

    @TestConfiguration
    static class Probe {
        @Component
        static class Seen {
            final List<Thing> events = new CopyOnWriteArrayList<>();
            final List<Map<String, String>> handlerContext = new CopyOnWriteArrayList<>();
        }

        @RestController
        @RequestMapping("/api/v1/probe/things")
        static class ProbeController {
            private final DomainEvents events;
            private final WecomGateway gateway;

            ProbeController(DomainEvents events, WecomGateway gateway) {
                this.events = events;
                this.gateway = gateway;
            }

            @PostMapping
            @Transactional
            String happen() {
                org.slf4j.LoggerFactory.getLogger(ProbeController.class).info("probe request handled");
                ThingAggregate thing = new ThingAggregate();
                thing.happen();
                events.publish(thing);
                gateway.send(new WecomCard(List.of(new WecomUserId("alice")), "Probe", "from the request", "https://one.example.test/probe", PROBE_REQUEST));
                return "ok";
            }
        }

        @Component
        @com.forcitis.one.kernel.SystemActorName("test.correlation.probe")
        static class ProbeListener {
            private final Seen seen;
            private final WecomGateway gateway;

            ProbeListener(Seen seen, WecomGateway gateway) {
                this.seen = seen;
                this.gateway = gateway;
            }

            @ApplicationModuleListener
            void on(Thing thing) {
                seen.events.add(thing);
                seen.handlerContext.add(Map.of("correlation", String.valueOf(WorkContext.correlationId().orElse(null)),
                        "causation", String.valueOf(WorkContext.causationId().orElse(null)),
                        "actor", String.valueOf(WorkContext.systemActor().orElse(null))));
                gateway.send(new WecomCard(List.of(new WecomUserId("bob")), "Probe", "from the handler", "https://one.example.test/probe", PROBE_HANDLER));
            }
        }
    }

    @Test
    @DisplayName("one HTTP request: the event, the handler, both queued deliveries and the outbound WeCom call carry the request's correlation id")
    void oneRequestOneCorrelationId(CapturedOutput output) throws Exception {
        wecom.stubFor(get(urlPathEqualTo("/cgi-bin/gettoken")).willReturn(okJson("{\"errcode\":0,\"access_token\":\"token-1\",\"expires_in\":7200}")));
        wecom.stubFor(post(urlPathEqualTo("/cgi-bin/message/send")).willReturn(okJson("{\"errcode\":0,\"errmsg\":\"ok\",\"response_code\":\"rc\"}")));
        UUID correlation = UUID.randomUUID();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/probe/things").with(csrf()).with(user(TestAccounts.account(9001).value().toString())).header(WorkContext.HEADER, correlation.toString()))
                .andExpect(status().isOk()).andExpect(header().string(WorkContext.HEADER, correlation.toString()));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(seen.events).hasSize(1));
        Thing thing = seen.events.getFirst();
        assertThat(thing.envelope().correlationId()).isEqualTo(correlation);
        assertThat(thing.envelope().actorType()).isEqualTo(ActorType.USER);
        assertThat(thing.envelope().actorId()).isEqualTo(TestAccounts.account(9001).value());
        assertThat(thing.envelope().causationId()).as("the request is the root").isNull();
        assertThat(thing.envelope().eventId().version()).isEqualTo(7);

        Map<String, String> inHandler = seen.handlerContext.getFirst();
        assertThat(inHandler.get("correlation")).isEqualTo(correlation.toString());
        assertThat(inHandler.get("causation")).isEqualTo(thing.envelope().eventId().toString());
        assertThat(inHandler.get("actor")).isEqualTo("test.correlation.probe");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                jdbc.sql("select count(*) from platform_integration.outbound_deliveries where subject_id in (:ids)")
                        .param("ids", List.of(PROBE_REQUEST.id(), PROBE_HANDLER.id())).query(Integer.class).single()).isEqualTo(2));
        assertThat(jdbc.sql("select distinct correlation_id from platform_integration.outbound_deliveries where subject_id in (:ids)")
                .param("ids", List.of(PROBE_REQUEST.id(), PROBE_HANDLER.id())).query(UUID.class).list()).containsExactly(correlation);

        deliver.execute();

        wecom.verify(2, postRequestedFor(urlPathEqualTo("/cgi-bin/message/send")).withHeader(WorkContext.HEADER, equalTo(correlation.toString())));
        assertThat(output.getOut()).contains("\"correlationId\":\"" + correlation + "\"");
    }

    @Test
    @DisplayName("a request without the header gets a generated id, echoed in the response, and its event carries that id")
    void generatedCorrelationId() throws Exception {
        int before = seen.events.size();

        String generated = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/probe/things").with(csrf()).with(user(TestAccounts.account(9001).value().toString()))).andExpect(status().isOk())
                .andReturn().getResponse().getHeader(WorkContext.HEADER);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(seen.events).hasSizeGreaterThan(before));
        assertThat(seen.events.getLast().envelope().correlationId()).hasToString(generated);
    }
}
