package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support.DescendingIds;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * R-1b P5 ADR-0033 section 1 item 10 (card acceptance item 11, 3.2 item 6): the source reference list is paged by the moment a row was
 * made (and its id only to break a tie), not by its id. The ids are made to fall, so a row made later has the smaller id; paging one
 * row at a time must still give every row once, oldest first. The cursor stays an opaque string.
 */
class SourceReferenceCursorOrderIT extends SourceReferenceHttpSupport {

    @TestConfiguration
    static class FallingIds {
        @Bean
        @Primary
        DescendingIds descendingIds() {
            return new DescendingIds();
        }
    }

    static final TestDatabase postgres = TestPostgres.database(SourceReferenceCursorOrderIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private MockHttpServletResponse page(String cursor) throws Exception {
        MockHttpServletRequestBuilder request = get(SOURCE_REFERENCES).param("sourceSystem", ACCOUNTING_SYSTEM).param("sourceObjectKind", LEGAL_ENTITY)
                .param("limit", "1").with(RECORDER_WHO_ALSO_APPROVES);
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 1 项第 10 条：来源对照列表翻页不依赖 ID 次序——递减 ID 写入多条，逐条翻页无遗漏、无重复，次序为创建时刻")
    void pagingFollowsTheMomentsNotTheIds() throws Exception {
        List<UUID> inOrderOfMaking = new ArrayList<>();
        for (int n = 0; n < 5; n++) {
            clock.set(NOW.plusSeconds(60L * (n + 1)));
            UUID legalEntity = createLegalEntity("SC" + (char) ('A' + n),"Synthetic cursor " + n);
            inOrderOfMaking.add(registerAccountingCode(RECORDER_WHO_ALSO_APPROVES, 9100L + n, legalEntity, TODAY).sourceReferenceId());
        }
        assertThat(inOrderOfMaking).as("the premise: the ids fall while the rows are made").isSortedAccordingTo((a, b) -> b.toString().compareTo(a.toString()));

        List<String> seen = new ArrayList<>();
        String cursor = null;
        for (int pages = 0; pages < 20; pages++) {
            MockHttpServletResponse one = page(cursor);
            assertThat(one.getStatus()).as(one.getContentAsString()).isEqualTo(200);
            seen.addAll(JsonPath.read(one.getContentAsString(), "$.items[*].sourceReferenceId"));
            cursor = JsonPath.read(one.getContentAsString(), "$.nextCursor");
            if (cursor == null) {
                break;
            }
            assertThat(cursor).as("the cursor is an opaque string").isNotBlank();
        }

        assertThat(seen).as("every row once").doesNotHaveDuplicates().containsAll(inOrderOfMaking.stream().map(UUID::toString).toList());
        assertThat(seen.stream().filter(id -> inOrderOfMaking.contains(UUID.fromString(id))).toList()).as("in the order the rows were made")
                .containsExactlyElementsOf(inOrderOfMaking.stream().map(UUID::toString).toList());
    }
}
