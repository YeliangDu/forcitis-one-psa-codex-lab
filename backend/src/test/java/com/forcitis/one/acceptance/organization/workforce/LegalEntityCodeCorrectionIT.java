package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Characterization of behaviour that already exists and must survive R-1b P3a (expected green before and after): ADR-0033 section 5
 * item 5 and the user's 2026-10-15 answer "未发过号前可更正，发号后禁改". A number is issued through the public numbering api after
 * the seeded quote template was confirmed over HTTP; nothing is mocked.
 */
class LegalEntityCodeCorrectionIT extends AcceptanceSupport {

    static final RequestPostProcessor NUMBERING_APPROVER = person(21, Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE,
            Permission.Codes.PLATFORM_NUMBERING_RULE_READ);
    static final String CODE_CORRECTED = "organization.workforce.LegalEntityCodeCorrected";

    static final TestDatabase postgres = TestPostgres.database(LegalEntityCodeCorrectionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    DocumentNumbers numbers;

    @Autowired
    PlatformTransactionManager transactions;

    private MockHttpServletResponse read(UUID legalEntity) throws Exception {
        return mvc.perform(get(LEGAL_ENTITIES + "/" + legalEntity).with(LEGAL_ENTITY_MAINTAINER)).andReturn().getResponse();
    }

    private MockHttpServletResponse change(UUID legalEntity, String code, String name) throws Exception {
        String etag = read(legalEntity).getHeader("ETag");
        return mvc.perform(put(LEGAL_ENTITIES + "/" + legalEntity).header("If-Match", etag).with(csrf()).with(LEGAL_ENTITY_MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"status\":\"ACTIVE\"}"))
                .andReturn().getResponse();
    }

    private static String code(MockHttpServletResponse answer) throws Exception {
        return JsonPath.read(answer.getContentAsString(), "$.code");
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 5 条（特征测试，预期现在即绿）：法人代码发号前可更正并写审计，发号后禁改（409 LEGAL_ENTITY_CODE_LOCKED、无写入），更名仍可")
    void codeIsCorrectableUntilANumberCarriesIt() throws Exception {
        UUID legalEntity = createLegalEntity("CHA", "Synthetic CHA");
        int correctionsBefore = auditedActions(CODE_CORRECTED).size();

        MockHttpServletResponse corrected = change(legalEntity, "CHB", "Synthetic CHA");
        assertThat(corrected.getStatus()).as(corrected.getContentAsString()).isEqualTo(200);
        assertThat(code(corrected)).isEqualTo("CHB");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(auditedActions(CODE_CORRECTED)).hasSize(correctionsBefore + 1));

        MockHttpServletResponse confirmedTemplate = mvc.perform(post("/api/v1/platform/numbering-rules/QUOTE:confirm").with(csrf()).with(NUMBERING_APPROVER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"templateVersion\":1}")).andReturn().getResponse();
        assertThat(confirmedTemplate.getStatus()).as(confirmedTemplate.getContentAsString()).isEqualTo(200);
        String issued = new TransactionTemplate(transactions).execute(status -> numbers.next(new DocumentTypeCode("QUOTE"), new LegalEntityId(legalEntity), TODAY).value());
        assertThat(issued).startsWith("QT-CHB-");

        MockHttpServletResponse locked = change(legalEntity, "CHC", "Synthetic CHA");
        assertThat(locked.getStatus()).as(locked.getContentAsString()).isEqualTo(409);
        assertThat((String) JsonPath.read(locked.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED");
        assertThat(code(read(legalEntity))).isEqualTo("CHB");

        MockHttpServletResponse renamed = change(legalEntity, "CHB", "Synthetic CHA renamed");
        assertThat(renamed.getStatus()).as("renaming stays possible once numbers were issued: " + renamed.getContentAsString()).isEqualTo(200);
        assertThat(code(read(legalEntity))).isEqualTo("CHB");
        assertThat(auditedActions(CODE_CORRECTED)).as("the refused correction left no trace of a correction").hasSize(correctionsBefore + 1);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 5 项第 5 条（特征测试，预期现在即绿）：只有发过号的法人被锁定，未发号的法人仍可更正")
    void onlyTheNumberedLegalEntityIsLocked() throws Exception {
        UUID numbered = createLegalEntity("CHD", "Synthetic CHD");
        UUID unnumbered = createLegalEntity("CHE", "Synthetic CHE");
        MockHttpServletResponse confirmedTemplate = mvc.perform(post("/api/v1/platform/numbering-rules/TIMESHEET:confirm").with(csrf())
                .with(NUMBERING_APPROVER).contentType(MediaType.APPLICATION_JSON).content("{\"templateVersion\":1}")).andReturn().getResponse();
        assertThat(confirmedTemplate.getStatus()).as(confirmedTemplate.getContentAsString()).isEqualTo(200);
        new TransactionTemplate(transactions).execute(status -> numbers.next(new DocumentTypeCode("TIMESHEET"), new LegalEntityId(numbered), TODAY).value());

        assertThat(change(numbered, "CHX", "Synthetic CHD").getStatus()).isEqualTo(409);
        int correctionsBefore = auditedActions(CODE_CORRECTED).size();
        MockHttpServletResponse corrected = change(unnumbered, "CHY", "Synthetic CHE");
        assertThat(corrected.getStatus()).as(corrected.getContentAsString()).isEqualTo(200);
        assertThat(code(corrected)).isEqualTo("CHY");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(auditedActions(CODE_CORRECTED)).hasSize(correctionsBefore + 1));
    }
}
