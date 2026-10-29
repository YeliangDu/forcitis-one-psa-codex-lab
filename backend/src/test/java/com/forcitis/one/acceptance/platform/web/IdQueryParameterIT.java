package com.forcitis.one.acceptance.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.acceptance.P5Names;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * R-1b P5 G-A1 (card acceptance item 7): an id given as a query parameter is a canonical lower-case UUID. The old number, free text,
 * an upper-case UUID and the nil UUID are a malformed request, 400 as a ProblemDetail with PLATFORM_WEB_REQUEST_INVALID, and not an
 * empty 200 list, a 404 or a 500. The same request with a fresh well-formed id is answered 200, so that the id is the only thing wrong.
 */
class IdQueryParameterIT extends AcceptanceSupport {

    static final String LEGACY_NUMBERS = "/api/v1/platform/legacy-numbers";
    static final String AUDIT_EXPORT = "/api/v1/platform/audit-entries:export";
    static final String SOURCE_REFERENCES = "/api/v1/platform/source-references";
    static final List<String> MALFORMED = List.of("42", "not-a-uuid", UUID.randomUUID().toString().toUpperCase(Locale.ROOT),
            "00000000-0000-0000-0000-000000000000");

    static final RequestPostProcessor LEGACY_READER = person(41, Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ);
    static final RequestPostProcessor AUDITOR = person(42, Permission.Codes.PLATFORM_AUDIT_ENTRY_READ, Permission.Codes.PLATFORM_AUDIT_ENTRY_EXPORT);
    static final RequestPostProcessor REFERENCE_APPROVER = person(43, Permission.Codes.PLATFORM_INTEGRATION_SOURCE_REFERENCE_APPROVE);

    static final TestDatabase postgres = TestPostgres.database(IdQueryParameterIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** path, parameter, the other query parameters the request needs to be otherwise valid, caller. */
    record Case(String path, String parameter, Map<String, String> others, RequestPostProcessor caller) {
        String name() {
            return path + " ?" + parameter;
        }
    }

    static Map<String, String> params(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    static List<Case> cases() {
        return List.of(
                new Case(LEGACY_NUMBERS, "documentId", params("documentType", "QUOTE"), LEGACY_READER),
                new Case(AUDIT_ENTRIES, "actorId", params(), AUDITOR),
                new Case(AUDIT_ENTRIES, "onBehalfOfId", params(), AUDITOR),
                new Case(AUDIT_ENTRIES, "subjectId", params("subjectKind", P5Names.USER_ACCOUNT), AUDITOR),
                new Case(AUDIT_EXPORT, "actorId", params(), AUDITOR),
                new Case(AUDIT_EXPORT, "onBehalfOfId", params(), AUDITOR),
                new Case(AUDIT_EXPORT, "subjectId", params("subjectKind", P5Names.USER_ACCOUNT), AUDITOR),
                new Case(SOURCE_REFERENCES, "ownId", params("sourceSystem", "ACCOUNTING_SYSTEM", "sourceObjectKind", "LEGAL_ENTITY"), REFERENCE_APPROVER));
    }

    private MockHttpServletResponse ask(Case request, String value) throws Exception {
        MockHttpServletRequestBuilder builder = get(request.path()).with(request.caller());
        request.others().forEach(builder::param);
        builder.param(request.parameter(), value);
        return mvc.perform(builder).andReturn().getResponse();
    }

    @Test
    @DisplayName("R-1b P5 G-A1：ID 类查询参数不是规范 uuid 时答 400 ProblemDetail——legacy-numbers 的 documentId（必含）、audit-entries 的 actorId / onBehalfOfId / subjectId、audit-entries:export 的 actorId / onBehalfOfId / subjectId、source-references 的 ownId；取值 42、not-a-uuid、大写 uuid、nil uuid")
    void aMalformedIdIsABadRequest() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Case request : cases()) {
            for (String value : MALFORMED) {
                MockHttpServletResponse answer = ask(request, value);
                String body = answer.getContentAsString();
                if (answer.getStatus() != 400) {
                    wrong.add(request.name() + "=" + value + " answered " + answer.getStatus() + ": " + body);
                } else if (answer.getContentType() == null || !answer.getContentType().startsWith("application/problem+json")) {
                    wrong.add(request.name() + "=" + value + " answered " + answer.getContentType());
                } else if (!P5Names.PROBLEM_CODE_INVALID_REQUEST.equals(JsonPath.read(body, "$.errorCode"))) {
                    wrong.add(request.name() + "=" + value + " errorCode " + JsonPath.read(body, "$.errorCode"));
                }
            }
        }

        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("R-1b P5 G-A1（对照）：同样的请求带规范 uuid 时答 200，所以上一条的 400 只因为 ID 不合格")
    void aWellFormedIdIsAccepted() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Case request : cases()) {
            MockHttpServletResponse answer = ask(request, UUID.randomUUID().toString());
            if (answer.getStatus() != 200) {
                wrong.add(request.name() + " answered " + answer.getStatus() + ": " + answer.getContentAsString());
            }
        }

        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("R-1b P5 G-A1 / ADR-0033 第 6 项第 2 条：审计查询的旧参数 subjectKey 已不存在，传入答 400（未登记的查询参数）")
    void theOldSubjectKeyParameterIsRefused() throws Exception {
        for (String path : List.of(AUDIT_ENTRIES, AUDIT_EXPORT)) {
            MockHttpServletResponse answer = mvc.perform(get(path).param("subjectKey", "user-account:" + UUID.randomUUID()).with(AUDITOR)).andReturn().getResponse();

            assertThat(answer.getStatus()).as(path + ": " + answer.getContentAsString()).isEqualTo(400);
            assertThat((String) JsonPath.read(answer.getContentAsString(), "$.errorCode")).isEqualTo(P5Names.PROBLEM_CODE_INVALID_REQUEST);
            assertThat((String) JsonPath.read(answer.getContentAsString(), "$.invalidParameters")).contains("subjectKey");
        }
    }
}
