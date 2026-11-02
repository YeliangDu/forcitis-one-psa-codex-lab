package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Ground of the #980 tests (design note wecom-rebind-second-person.md sections 4 to 6; user answers Q1 a and Q2 a of 2026-11-02; DD
 * sign-off on #980 comment 6587943265): profile changes, confirmations, rejections and the list go through their HTTP endpoints as the
 * signed-in caller, the request table is read by its signed names, and the sessions and browser identifiers of the subject are watched.
 */
abstract class ProfileChangeRequestSupport extends EmployeeHireSupport {

    // Names of the note (section 5), the DD sign-off and one-design's HTTP ruling on #980 (comment 6591359266).
    static final String REQUESTS = "employee_profile_change_requests";
    static final String REQUESTS_PATH = "/api/v1/organization/employee-profile-change-requests";
    static final String PENDING_CODE = "ORGANIZATION_WORKFORCE_PROFILE_CHANGE_PENDING";
    static final String CONFIRMER_CODE = "ORGANIZATION_WORKFORCE_PROFILE_CHANGE_CONFIRMER_NOT_ALLOWED";
    static final String AUDIT_REQUESTED = "organization.workforce.employee-profile.change-requested";
    static final String AUDIT_REJECTED = "organization.workforce.employee-profile.change-rejected";
    static final String AUDIT_VOIDED = "organization.workforce.employee-profile.change-voided";

    // ---- calls ----

    /** The profile change through :change-profile by the caller: the whole new profile (gender and email left out). */
    protected MockHttpServletResponse changeOver(Staff caller, UUID person, String displayName, String wecomUserId) throws Exception {
        String wecom = wecomUserId == null ? "null" : "\"" + wecomUserId + "\"";
        return mvc.perform(post("/api/v1/organization/employees/" + text(person) + ":change-profile").with(csrf()).with(session(caller.session()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"" + displayName + "\",\"wecomUserId\":" + wecom + ",\"effectiveDate\":\"" + TODAY + "\"}"))
                .andReturn().getResponse();
    }

    /** A change that waits for a second person: 202 with the request id. */
    protected UUID requested(Staff caller, UUID person, String displayName, String wecomUserId) throws Exception {
        MockHttpServletResponse answer = changeOver(caller, person, displayName, wecomUserId);
        assertThat(answer.getStatus()).as("the change waits for a second person: " + answer.getContentAsString()).isEqualTo(202);
        return UUID.fromString(JsonPath.read(answer.getContentAsString(), "$.requestId"));
    }

    protected MockHttpServletResponse confirm(Staff caller, UUID request) throws Exception {
        return confirmWith(caller, request, "\"0\"");
    }

    /** :confirm with the given If-Match value (null leaves the header out); one-design ruling on #980 comment 6591359266 item 1. */
    protected MockHttpServletResponse confirmWith(Staff caller, UUID request, String ifMatch) throws Exception {
        var call = post(REQUESTS_PATH + "/" + request + ":confirm").with(csrf()).with(session(caller.session()));
        if (ifMatch != null) {
            call = call.header("If-Match", ifMatch);
        }
        return mvc.perform(call).andReturn().getResponse();
    }

    protected MockHttpServletResponse reject(Staff caller, UUID request, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        return mvc.perform(post(REQUESTS_PATH + "/" + request + ":reject").header("If-Match", "\"0\"").with(csrf()).with(session(caller.session()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    protected MockHttpServletResponse listPending(Staff caller) throws Exception {
        return mvc.perform(get(REQUESTS_PATH).param("status", "PENDING").with(session(caller.session()))).andReturn().getResponse();
    }

    protected static String codeOf(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.code");
    }

    // ---- reading ----

    protected Map<String, Object> request(UUID request) throws SQLException {
        List<Map<String, Object>> found = rows("select * from " + REQUESTS + " where employee_profile_change_request_id = ?", request);
        assertThat(found).as("a row of " + REQUESTS + " for " + request).hasSize(1);
        return found.get(0);
    }

    protected long requestsOf(UUID employee) throws SQLException {
        return (long) rows("select count(*) as n from " + REQUESTS + " where employee_id = ?", stored(employee)).get(0).get("n");
    }

    protected static String wecomOf(Staff who) {
        return wecomUserIdOf(who.employeeId());
    }

    /** 200 while the person's login session lives, 401 or 403 once it is revoked. */
    protected int sessionStatus(Staff who) throws Exception {
        return mvc.perform(get("/api/v1/platform/sessions:current").with(session(who.session()))).andReturn().getResponse().getStatus();
    }

    /** A browser identifier of the person's account, as the confirmation of an external login leaves one (I-13). */
    protected void browserIdentifier(Staff who) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection identity = schemaConnection("platform_identity")) {
            update(identity, "insert into browser_identifiers (browser_identifier_id, user_id, identifier_hash, last_login_at) values (?, ?, ?, ?)",
                    Uuids.timeOrdered(NOW), who.userId(), "synthetic-hash-" + UUID.randomUUID(), at);
        }
    }

    protected long browserIdentifiers(Staff who) throws Exception {
        try (Connection identity = schemaConnection("platform_identity")) {
            return (long) query(identity, "select count(*) as n from browser_identifiers where user_id = ?", who.userId()).get(0).get("n");
        }
    }

    /** A privileged subject (a role beyond employee) with a WeCom id, a live session and one browser identifier. */
    protected Staff privileged() throws Exception {
        Staff who = staff("org_data_admin");
        browserIdentifier(who);
        return who;
    }

    /** The subject's root, sessions and browser identifiers are as before. */
    protected void assertUntouched(Staff subject, Map<String, Object> rootBefore, long identifiersBefore) throws Exception {
        assertThat(root(subject.employeeId())).as("the profile is unchanged").isEqualTo(rootBefore);
        assertThat(sessionStatus(subject)).as("the subject's session stays").isEqualTo(200);
        assertThat(browserIdentifiers(subject)).as("no browser identifier revoked").isEqualTo(identifiersBefore);
    }
}
