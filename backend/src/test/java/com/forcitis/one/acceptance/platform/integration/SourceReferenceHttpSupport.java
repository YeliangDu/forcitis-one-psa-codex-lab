package com.forcitis.one.acceptance.platform.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.kernel.WorkContext;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * HTTP contract of the manual source references (ADR-0033 section 5 items 3 and 4), which only the accounting organization code of a
 * legal entity uses:
 * <ul>
 *   <li>{@code POST /api/v1/platform/source-references} with {@code {sourceSystem, sourceObjectKind, sourceId, ownId, effectiveFrom}},
 *       permission {@code platform.integration:source-reference:update}: 201 with {@code sourceReferenceId}, {@code confirmationStatus}
 *       PENDING and {@code sourceId} as a decimal string (64-bit ids do not fit a JSON number);</li>
 *   <li>{@code POST /api/v1/platform/source-references/{sourceReferenceId}:confirm} and {@code :reject}, permission
 *       {@code platform.integration:source-reference:approve}; the person who registered the row cannot decide it.</li>
 * </ul>
 * An ETag of the registration answer, if there is one, is sent back as If-Match on the decision.
 */
abstract class SourceReferenceHttpSupport extends AcceptanceSupport {

    static final String SOURCE_REFERENCES = "/api/v1/platform/source-references";
    static final String UPDATE = "platform.integration:source-reference:update";
    static final String APPROVE = "platform.integration:source-reference:approve";
    static final String ACCOUNTING_SYSTEM = "ACCOUNTING_SYSTEM";
    static final String LEGAL_ENTITY = "LEGAL_ENTITY";

    static final RequestPostProcessor RECORDER_WHO_ALSO_APPROVES = person(11, UPDATE, APPROVE);
    static final RequestPostProcessor APPROVER = person(12, APPROVE);
    static final RequestPostProcessor RECORDER_ONLY = person(13, UPDATE);

    record Registered(UUID sourceReferenceId, String etag) {
    }

    @Autowired
    ApplicationContext context;

    Connection integration;

    @BeforeEach
    void openIntegrationSchema() throws Exception {
        integration = schemaConnection("platform_integration");
    }

    @AfterEach
    void closeIntegrationSchema() throws Exception {
        if (integration != null) {
            integration.close();
        }
    }

    static String body(String sourceSystem, String kind, String sourceId, UUID ownId, LocalDate effectiveFrom) {
        StringBuilder json = new StringBuilder("{\"sourceSystem\":\"").append(sourceSystem).append("\",\"sourceObjectKind\":\"").append(kind)
                .append("\",\"sourceId\":\"").append(sourceId).append("\",\"ownId\":\"").append(ownId).append('"');
        if (effectiveFrom != null) {
            json.append(",\"effectiveFrom\":\"").append(effectiveFrom).append('"');
        }
        return json.append('}').toString();
    }

    MockHttpServletResponse postJson(RequestPostProcessor who, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(SOURCE_REFERENCES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
        if (who != null) {
            request.with(who);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    Registered registerAccountingCode(RequestPostProcessor who, long code, UUID legalEntity, LocalDate effectiveFrom) throws Exception {
        MockHttpServletResponse created = postJson(who, body(ACCOUNTING_SYSTEM, LEGAL_ENTITY, Long.toString(code), legalEntity, effectiveFrom));
        if (created.getStatus() != 201) {
            throw new AssertionError("registering accounting code " + code + " answered " + created.getStatus() + ": " + created.getContentAsString());
        }
        String status = JsonPath.read(created.getContentAsString(), "$.confirmationStatus");
        String sourceId = JsonPath.read(created.getContentAsString(), "$.sourceId");
        if (!"PENDING".equals(status) || !Long.toString(code).equals(sourceId)) {
            throw new AssertionError("a registration answers PENDING with the source id as a string: " + created.getContentAsString());
        }
        return new Registered(UUID.fromString(JsonPath.read(created.getContentAsString(), "$.sourceReferenceId")), created.getHeader("ETag"));
    }

    MockHttpServletResponse decide(RequestPostProcessor who, Registered row, String verb) throws Exception {
        MockHttpServletRequestBuilder request = post(SOURCE_REFERENCES + "/" + row.sourceReferenceId() + ":" + verb).with(csrf()).with(who);
        if (row.etag() != null) {
            request.header("If-Match", row.etag());
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    Registered confirmedAccountingCode(long code, UUID legalEntity, LocalDate effectiveFrom) throws Exception {
        Registered row = registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, code, legalEntity, effectiveFrom);
        MockHttpServletResponse confirmed = decide(APPROVER, row, "confirm");
        if (confirmed.getStatus() / 100 != 2) {
            throw new AssertionError("confirming accounting code " + code + " answered " + confirmed.getStatus() + ": " + confirmed.getContentAsString());
        }
        return row;
    }

    /** status, source id, effective from and to of one row, read from the module's own schema. */
    String row(UUID sourceReferenceId) throws Exception {
        try (PreparedStatement select = integration.prepareStatement("""
                select confirmation_status, source_id, own_id, effective_from_date, effective_to_date from source_references where source_reference_id = ?
                """)) {
            select.setObject(1, sourceReferenceId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return "absent";
                }
                return rs.getString(1) + " " + rs.getLong(2) + " " + rs.getObject(3, UUID.class) + " [" + rs.getObject(4, LocalDate.class) + ", "
                        + rs.getObject(5, LocalDate.class) + ")";
            }
        }
    }

    long referencesOf(UUID ownId) throws Exception {
        try (PreparedStatement count = integration.prepareStatement("select count(*) from source_references where own_id = ?")) {
            count.setObject(1, ownId);
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    long allReferences() throws Exception {
        try (PreparedStatement count = integration.prepareStatement("select count(*) from source_references"); ResultSet rs = count.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    long outboundAccountingCode(UUID legalEntity, LocalDate on) {
        SourceReferencesProbe references = SourceReferencesProbe.of(context);
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "platform.integration.outbound")) {
            return references.sourceIdFor(ACCOUNTING_SYSTEM, LEGAL_ENTITY, legalEntity, on);
        }
    }

    List<String> integrationAudit() throws Exception {
        return new ArrayList<>(auditedActions("platform.integration."));
    }
}
