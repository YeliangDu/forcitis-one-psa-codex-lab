package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.acceptance.AcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * K-3a pilot (NEXT_BATCH_PLAN section 8, row "工作日历年份列表试点"): the maintained-years list pages by key in the database.
 * The answers are black-box; that the database does the cutting is read from the statement log of the test container
 * (log_statement=all), because a list sliced in memory gives the same answers.
 * Years 2027 to 2036 are maintained (the fixed clock is 2026-11-02, so none is in the past); the list is newest year first.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkCalendarYearsPagingIT extends AcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database().withCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all");

    private static final String CALENDARS = "/api/v1/organization/work-calendars";
    private static final RequestPostProcessor MAINTAINER = person(21, "organization.attendance:work-calendar:read",
            "organization.attendance:work-calendar:update");
    private static final Pattern BOUNDED = Pattern.compile("(?is)\\blimit\\b|\\bfetch\\s+first\\b");

    @BeforeAll
    void maintainTenYears() throws Exception {
        for (int year = 2027; year <= 2036; year++) {
            MockHttpServletResponse saved = mvc.perform(put(CALENDARS + "/" + year).header("If-Match", "\"0\"").with(MAINTAINER).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"days\": []}")).andReturn().getResponse();
            if (saved.getStatus() != 200) {
                throw new AssertionError("maintaining " + year + " answered " + saved.getStatus() + ": " + saved.getContentAsString());
            }
        }
    }

    private MockHttpServletResponse page(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(CALENDARS).with(MAINTAINER);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return response;
    }

    private static List<Integer> years(MockHttpServletResponse page) throws Exception {
        return JsonPath.read(page.getContentAsString(), "$.items[*].calendarYear");
    }

    private static String nextCursor(MockHttpServletResponse page) throws Exception {
        return JsonPath.read(page.getContentAsString(), "$.nextCursor");
    }

    @Test
    @DisplayName("K-3a 试点 A-3 limit=3 逐页取完十个年份：3+3+3+1，由新到旧，无重复无遗漏，末页 nextCursor 为 null（特征测试）")
    void walkingPagesOfThreeCoversEveryYearOnce() throws Exception {
        List<List<Integer>> pages = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletResponse response = cursor == null ? page("limit", "3") : page("limit", "3", "cursor", cursor);
            pages.add(years(response));
            cursor = nextCursor(response);
            assertThat(pages.size()).as("paging must terminate").isLessThanOrEqualTo(10);
        } while (cursor != null);

        assertThat(pages).containsExactly(List.of(2036, 2035, 2034), List.of(2033, 2032, 2031), List.of(2030, 2029, 2028), List.of(2027));
    }

    @Test
    @DisplayName("K-3a 试点 A-3 limit 恰好等于总数时 nextCursor 为 null；少一个时有 nextCursor 且下一页只剩一行（特征测试）")
    void lastPageEndsExactlyAtTheBoundary() throws Exception {
        MockHttpServletResponse exact = page("limit", "10");
        MockHttpServletResponse oneShort = page("limit", "9");
        MockHttpServletResponse remainder = page("limit", "9", "cursor", nextCursor(oneShort));

        assertThat(years(exact)).hasSize(10);
        assertThat(nextCursor(exact)).isNull();
        assertThat(years(oneShort)).hasSize(9);
        assertThat(years(remainder)).containsExactly(2027);
        assertThat(nextCursor(remainder)).isNull();
    }

    @Test
    @DisplayName("K-3a 试点 A-3 limit=1 只返回最新的一年，不带 limit 时十年一页取完（特征测试）")
    void limitTakesEffect() throws Exception {
        assertThat(years(page("limit", "1"))).containsExactly(2036);
        assertThat(years(page())).hasSize(10);
    }

    @Test
    @DisplayName("K-3a 试点 A-3 数据库端键集分页：首页和带游标的下一页读年份表的语句都带 LIMIT，不把全部年份取回再切")
    void bothPagesAreCutInTheDatabase() throws Exception {
        String firstWindow = statementLogAround(() -> page("limit", "3"));
        String cursor = nextCursor(page("limit", "3"));
        String secondWindow = statementLogAround(() -> page("limit", "3", "cursor", cursor));

        for (String window : List.of(firstWindow, secondWindow)) {
            List<String> readsOfYears = statementsTouching(window, "work_calendar_years");
            assertThat(readsOfYears).as("the list reads the maintained years from the database").isNotEmpty();
            assertThat(readsOfYears).as("every read of the years table is bounded by LIMIT")
                    .allSatisfy(sql -> assertThat(BOUNDED.matcher(sql).find()).as(sql).isTrue());
        }
    }

    /** Marker statements bracket the call so that only the statements of that call are read, whatever the log latency. */
    private String statementLogAround(ThrowingRunnable call) throws Exception {
        String before = "mark-" + UUID.randomUUID();
        String after = "mark-" + UUID.randomUUID();
        marker(before);
        call.run();
        marker(after);
        await().atMost(Duration.ofSeconds(10)).until(() -> postgres.getLogs().contains(after));
        String log = postgres.getLogs();
        return log.substring(log.indexOf(before), log.indexOf(after));
    }

    private void marker(String text) throws Exception {
        try (Connection connection = schemaConnection("organization_attendance"); Statement statement = connection.createStatement()) {
            statement.execute("select '" + text + "'");
        }
    }

    private static List<String> statementsTouching(String log, String table) {
        List<String> found = new ArrayList<>();
        for (String entry : log.split("(?m)^.*LOG:\\s+")) {
            if (entry.contains(table) && entry.toLowerCase().contains("select")) {
                found.add(entry);
            }
        }
        return found;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
