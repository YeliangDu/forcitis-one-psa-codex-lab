package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Publication refusals that the card's acceptance tests do not reach. */
class StandardHoursPublishRefusalIT extends AcceptanceSupport {
    static final TestDatabase postgres = TestPostgres.database(StandardHoursPublishRefusalIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String VERSIONS = "/api/v1/organization/standard-hours-versions";
    private static final RequestPostProcessor PUBLISHER = person(41, "organization.attendance:standard-hours:publish",
            "organization.attendance:standard-hours:read");

    @Test
    @DisplayName("#1310 publishing for a legal entity that does not exist answers 400 INVALID and stores nothing")
    void unknownLegalEntityIsRefusedAndNothingIsStored() throws Exception {
        String body = "{\"legalEntityId\":\"" + UUID.randomUUID() + "\",\"effectiveFromDate\":\"2030-01-01\",\"workdayMinutes\":480,"
                + "\"adjustedWorkdayMinutes\":420,\"basisRef\":\"Synthetic basis\"}";

        MockHttpServletResponse refused = mvc.perform(post(VERSIONS).with(PUBLISHER).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();

        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(400);
        assertThat((String) JsonPath.read(refused.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID");
        MockHttpServletResponse listed = mvc.perform(get(VERSIONS).param("legalEntityId", JsonPath.<String>read(body, "$.legalEntityId")).with(PUBLISHER))
                .andReturn().getResponse();
        assertThat((java.util.List<?>) JsonPath.read(listed.getContentAsString(), "$.items")).as("nothing is stored for the unknown entity").isEmpty();
        assertThat(auditedActions("organization.attendance.standard-hours.")).as("a refused publication leaves no audit entry").isEmpty();
    }

    @Test
    @DisplayName("#1310 minutes that are not whole numbers (7.5) are refused with 400 INVALID instead of being cut to 7, in the version and in an override")
    void fractionalMinutesAreRefusedNotTruncated() throws Exception {
        String head = "{\"effectiveFromDate\":\"2030-01-01\",\"basisRef\":\"Synthetic basis\",";
        for (String body : new String[] {head + "\"workdayMinutes\":7.5,\"adjustedWorkdayMinutes\":420}",
                head + "\"workdayMinutes\":480,\"adjustedWorkdayMinutes\":7.5}",
                head + "\"workdayMinutes\":480,\"adjustedWorkdayMinutes\":420,\"dateOverrides\":[{\"workDate\":\"2030-01-02\",\"minutes\":7.5,\"reason\":\"Synthetic\"}]}"}) {
            MockHttpServletResponse refused = mvc.perform(post(VERSIONS).with(PUBLISHER).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse();

            assertThat(refused.getStatus()).as(body + " -> " + refused.getContentAsString()).isEqualTo(400);
            assertThat((String) JsonPath.read(refused.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID");
        }
        MockHttpServletResponse listed = mvc.perform(get(VERSIONS).with(PUBLISHER)).andReturn().getResponse();
        assertThat((java.util.List<?>) JsonPath.read(listed.getContentAsString(), "$.items")).as("nothing is stored").isEmpty();
    }
}
