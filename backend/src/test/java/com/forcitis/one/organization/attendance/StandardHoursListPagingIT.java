package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The cursor pages of the version list: they are new behaviour that the guard's shape check does not prove. */
class StandardHoursListPagingIT extends AcceptanceSupport {
    static final TestDatabase postgres = TestPostgres.database(StandardHoursListPagingIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String VERSIONS = "/api/v1/organization/standard-hours-versions";
    private static final RequestPostProcessor ADMIN = person(42, "organization.attendance:standard-hours:publish",
            "organization.attendance:standard-hours:read");

    @Test
    @DisplayName("#1310 the version list pages newest first: the cursor of one answer gives the rest, no version twice, no cursor on the last page")
    void secondPageContinuesWithoutOverlap() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        for (int i = 0; i < 3; i++) {
            String body = "{\"effectiveFromDate\":\"" + today.plusDays(i) + "\",\"workdayMinutes\":480,\"adjustedWorkdayMinutes\":420,\"basisRef\":\"Synthetic basis\"}";
            MockHttpServletResponse published = mvc.perform(post(VERSIONS).with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn().getResponse();
            assertThat(published.getStatus()).as(published.getContentAsString()).isEqualTo(200);
        }

        MockHttpServletResponse first = mvc.perform(get(VERSIONS).param("limit", "2").with(ADMIN)).andReturn().getResponse();
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);
        List<Integer> firstNumbers = JsonPath.read(first.getContentAsString(), "$.items[*].versionNumber");
        String cursor = JsonPath.read(first.getContentAsString(), "$.nextCursor");
        assertThat(firstNumbers).as("first page, newest first").containsExactly(3, 2);
        assertThat(cursor).as("a further page exists").isNotBlank();

        MockHttpServletResponse second = mvc.perform(get(VERSIONS).param("limit", "2").param("cursor", cursor).with(ADMIN)).andReturn().getResponse();
        assertThat(second.getStatus()).as(second.getContentAsString()).isEqualTo(200);
        List<Integer> secondNumbers = JsonPath.read(second.getContentAsString(), "$.items[*].versionNumber");
        assertThat(secondNumbers).as("the rest, without the versions of the first page").containsExactly(1);
        assertThat(JsonPath.<Object>read(second.getContentAsString(), "$.nextCursor")).as("no cursor on the last page").isNull();
        List<Integer> all = new ArrayList<>(firstNumbers);
        all.addAll(secondNumbers);
        assertThat(all).as("every version once").containsExactly(3, 2, 1);

        MockHttpServletResponse exact = mvc.perform(get(VERSIONS).param("limit", "3").with(ADMIN)).andReturn().getResponse();
        assertThat((List<?>) JsonPath.read(exact.getContentAsString(), "$.items")).as("a page that holds exactly all versions").hasSize(3);
        assertThat(JsonPath.<Object>read(exact.getContentAsString(), "$.nextCursor")).as("and has no further page").isNull();
    }
}
