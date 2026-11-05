package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.organization.workforce.application.AttributionCountersignDeadlineJob;
import com.forcitis.one.organization.workforce.application.OrgStructureCommandDueJob;
import com.forcitis.one.organization.workforce.application.PersonnelCommandDueJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Card #1478: under the profile local the clock can be read, set to a golden date and reset through its endpoint. */
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class LocalClockIT extends IdentityIntegrationTest {

    static final String CLOCK = "/api/v1/platform/local-clocks";

    @MockitoSpyBean
    PersonnelCommandDueJob personnelJob;

    @MockitoSpyBean
    OrgStructureCommandDueJob orgJob;

    @MockitoSpyBean
    AttributionCountersignDeadlineJob countersignJob;

    @Test
    @DisplayName("#1478 the clock endpoint reads, sets a golden date and resets")
    void setAndReset() throws Exception {
        var before = body(mvc.perform(get(CLOCK)).andReturn());
        assertThat(before.path("adjusted").asBoolean()).isFalse();

        var set = body(mvc.perform(post(CLOCK + ":set").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{\"at\":\"2026-03-15T08:00:00Z\"}")).andReturn());
        assertThat(set.path("adjusted").asBoolean()).isTrue();
        assertThat(set.path("instant").asString()).startsWith("2026-03-15T08:00");

        // the pretend month has an audit partition, so what the jobs and the requests write for that day is not refused
        assertThat(jdbc.sql("select to_regclass('platform_audit.audit_entries_p202603') is not null").query(Boolean.class).single()).isTrue();

        var inOrder = org.mockito.Mockito.inOrder(personnelJob, orgJob, countersignJob);
        inOrder.verify(personnelJob).run();
        inOrder.verify(orgJob).run();
        inOrder.verify(countersignJob).run();

        org.mockito.Mockito.clearInvocations(personnelJob, orgJob, countersignJob);
        var reset = body(mvc.perform(post(CLOCK + ":reset").with(xsrf())).andReturn());
        assertThat(reset.path("adjusted").asBoolean()).isFalse();
        org.mockito.Mockito.verify(personnelJob, org.mockito.Mockito.never()).run();
        org.mockito.Mockito.verify(orgJob, org.mockito.Mockito.never()).run();
        org.mockito.Mockito.verify(countersignJob, org.mockito.Mockito.never()).run();
    }
}
