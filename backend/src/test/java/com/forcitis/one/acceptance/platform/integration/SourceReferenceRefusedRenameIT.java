package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Names;
import com.forcitis.one.acceptance.P5Support;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P5 rename B2-B05 (card section 14, RULING-P5-4): the refusal of a hand-made source reference is published as
 * platform.integration.SourceReferenceRefused. The old name is in neither the event catalogues, the aggregate model, the list of actions
 * that must be audited, the registry of published events nor the audit trail.
 */
class SourceReferenceRefusedRenameIT extends P5Support {

    static final Path EVENTS = Path.of("../docs/modernization/events");
    static final Path AGGREGATES = Path.of("../docs/modernization/architecture/ssot/i03-aggregates/built.yaml");
    static final Path MUST_AUDIT = Path.of("../docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml");

    static final TestDatabase postgres = TestPostgres.database(SourceReferenceRefusedRenameIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("R-1b P5 随带改名 B2-B05：事件目录、聚合模型与审计清单只有新名 SourceReferenceRefused，旧名 SourceReferenceRegistrationRefused 不在")
    void theCataloguesUseOnlyTheNewName() throws Exception {
        for (Path file : List.of(EVENTS.resolve("asyncapi.yaml"), EVENTS.resolve("integration.yaml"), MUST_AUDIT)) {
            assertThat(Files.readString(file)).as(file.toString()).contains(P5Names.REFUSED_EVENT).doesNotContain(P5Names.OLD_REFUSED_EVENT);
        }
        assertThat(Files.readString(AGGREGATES)).as(AGGREGATES.toString()).contains("SourceReferenceRefused").doesNotContain(P5Names.OLD_REFUSED_EVENT);
    }

    @Test
    @DisplayName("R-1b P5 随带改名 B2-B05：手工登记被拒后发布的事件类型是 platform.integration.SourceReferenceRefused，审计动作同名，旧名不出现")
    void aRefusedRegistrationIsPublishedAsSourceReferenceRefused() throws Exception {
        MockHttpServletResponse answer = mvc.perform(post("/api/v1/platform/source-references").with(csrf())
                .with(person(11, "platform.integration:source-reference:update")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceSystem\":\"ORG_SOURCE\",\"sourceObjectKind\":\"EMPLOYEE\",\"sourceId\":\"-42\",\"ownId\":\""
                        + UUID.randomUUID() + "\",\"effectiveFrom\":\"" + TODAY + "\"}")).andReturn().getResponse();
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(422);

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> auditedActions("platform.integration.").contains(P5Names.REFUSED_EVENT));
        assertThat(registeredEnvelopes().stream().map(envelope -> envelope.path("eventType").asString())).contains(P5Names.REFUSED_EVENT)
                .noneMatch(type -> type.endsWith("." + P5Names.OLD_REFUSED_EVENT));
        assertThat(auditedActions("platform.integration.")).noneMatch(action -> action.endsWith("." + P5Names.OLD_REFUSED_EVENT));
    }
}
