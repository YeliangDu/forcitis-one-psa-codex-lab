package com.forcitis.one.accounting.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** #1439 (accounting-spine.md section 11 item 1): OpenFiscalYear opens twelve OPEN periods for one entity and year, once. */
class FiscalYearIT extends AcceptanceSupport {
    static final TestDatabase postgres = TestPostgres.database(FiscalYearIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String YEARS = "/api/v1/accounting/fiscal-years";
    private static final RequestPostProcessor FINANCE = person(51, Permission.Codes.ACCOUNTING_PERIOD_FISCAL_YEAR_CREATE);
    private static final String ACTION = "accounting.period.fiscal-year.opened";

    @Autowired
    JdbcClient jdbc;

    private MockHttpServletResponse open(RequestPostProcessor caller, UUID entity, Object year) throws Exception {
        String body = "{\"legalEntityId\":" + (entity == null ? "null" : "\"" + entity + "\"") + ",\"fiscalYear\":" + year + "}";
        return mvc.perform(post(YEARS).with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private int periodCount(UUID entity, int year) {
        return jdbc.sql("select count(*) from accounting_period.fiscal_periods where legal_entity_id = :e and fiscal_year_number = :y")
                .param("e", entity).param("y", year).query(Integer.class).single();
    }

    private static String errorCode(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.errorCode");
    }

    private UUID entity(String code) throws Exception {
        return createLegalEntity(code, "Synthetic " + code);
    }

    @Test
    @DisplayName("#1439 opening a year creates the twelve monthly periods, all OPEN, and one audit entry")
    void opensTwelveOpenPeriods() throws Exception {
        UUID entity = entity("FYA");
        int audited = auditedActions(ACTION).size();

        MockHttpServletResponse created = open(FINANCE, entity, 2026);

        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        assertThat((List<?>) JsonPath.read(created.getContentAsString(), "$.periods")).hasSize(12);
        assertThat(jdbc.sql("select string_agg(period_number::text || ':' || status, ',' order by period_number) from accounting_period.fiscal_periods "
                + "where legal_entity_id = :e and fiscal_year_number = 2026").param("e", entity).query(String.class).single())
                .isEqualTo("1:OPEN,2:OPEN,3:OPEN,4:OPEN,5:OPEN,6:OPEN,7:OPEN,8:OPEN,9:OPEN,10:OPEN,11:OPEN,12:OPEN");
        assertThat(auditedActions(ACTION)).hasSize(audited + 1);
    }

    @Test
    @DisplayName("#1439 a second call for the same year is refused with 409 and changes nothing; another entity may open the same year")
    void secondCallIsRefused() throws Exception {
        UUID entity = entity("FYB");
        assertThat(open(FINANCE, entity, 2026).getStatus()).isEqualTo(201);
        int audited = auditedActions(ACTION).size();

        MockHttpServletResponse again = open(FINANCE, entity, 2026);

        assertThat(again.getStatus()).as(again.getContentAsString()).isEqualTo(409);
        assertThat(errorCode(again)).isEqualTo("ACCOUNTING_PERIOD_YEAR_ALREADY_OPENED");
        assertThat(periodCount(entity, 2026)).isEqualTo(12);
        assertThat(auditedActions(ACTION)).as("a refused call leaves no audit entry").hasSize(audited);
        assertThat(open(FINANCE, entity("FYC"), 2026).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("#1439 two simultaneous calls for the same year: one wins with twelve rows, the other is a 409 and leaves no partial year")
    void simultaneousCallsOpenOneYear() throws Exception {
        UUID entity = entity("FYD");
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> {
                go.await();
                return open(FINANCE, entity, 2026).getStatus();
            };
            Future<Integer> first = pool.submit(call);
            Future<Integer> second = pool.submit(call);
            go.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(periodCount(entity, 2026)).isEqualTo(12);
    }

    @Test
    @DisplayName("#1439 a year more than one after the latest opened year is refused; the next year and the first year are accepted; a year before the latest is refused (the spine opens no gaps and no history)")
    void yearsAheadAreChecked() throws Exception {
        UUID entity = entity("FYE");
        assertThat(open(FINANCE, entity, 2026).getStatus()).as("the first year is accepted").isEqualTo(201);
        MockHttpServletResponse typo = open(FINANCE, entity, 2062);
        assertThat(typo.getStatus()).as(typo.getContentAsString()).isEqualTo(422);
        assertThat(errorCode(typo)).isEqualTo("ACCOUNTING_PERIOD_YEAR_TOO_FAR_AHEAD");
        assertThat(periodCount(entity, 2062)).isZero();
        assertThat(open(FINANCE, entity, 2028).getStatus()).as("two ahead").isEqualTo(422);
        assertThat(open(FINANCE, entity, 2027).getStatus()).as("one ahead").isEqualTo(201);
        assertThat(open(FINANCE, entity, 2028).getStatus()).as("one ahead of the new latest").isEqualTo(201);
        MockHttpServletResponse earlier = open(FINANCE, entity, 2024);
        assertThat(earlier.getStatus()).as(earlier.getContentAsString()).isEqualTo(422);
        assertThat(errorCode(earlier)).isEqualTo("ACCOUNTING_PERIOD_YEAR_BEFORE_LATEST");
        assertThat(periodCount(entity, 2024)).isZero();
        assertThat(open(FINANCE, entity, 2027).getStatus()).as("an already opened year in range stays a conflict").isEqualTo(409);
    }

    @Test
    @DisplayName("#1439 a year outside 2000 to 2999 is refused; a missing field is a 400; an unknown legal entity is a 400 and stores nothing")
    void invalidInputIsRefused() throws Exception {
        UUID entity = entity("FYF");
        MockHttpServletResponse early = open(FINANCE, entity, 1999);
        assertThat(early.getStatus()).as(early.getContentAsString()).isEqualTo(422);
        assertThat(errorCode(early)).isEqualTo("ACCOUNTING_PERIOD_YEAR_OUT_OF_RANGE");
        assertThat(open(FINANCE, entity, 3000).getStatus()).isEqualTo(422);
        assertThat(open(FINANCE, entity, "null").getStatus()).isEqualTo(400);
        assertThat(open(FINANCE, null, 2026).getStatus()).isEqualTo(400);

        UUID unknown = UUID.randomUUID();
        MockHttpServletResponse refused = open(FINANCE, unknown, 2026);
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(400);
        assertThat(errorCode(refused)).isEqualTo("ACCOUNTING_PERIOD_LEGAL_ENTITY_UNKNOWN");
        assertThat(periodCount(unknown, 2026)).isZero();
    }

    @Test
    @DisplayName("#1551 a year that is not a JSON integer (fractional, a string, a boolean) is a 400 and stores nothing; an integer still opens the year")
    void theYearIsStrictlyAnInteger() throws Exception {
        UUID entity = entity("FYS");
        for (String year : new String[] {"2026.9", "2026.0", "\"2027\"", "true", "[2027]", "{}"}) {
            MockHttpServletResponse refused = open(FINANCE, entity, year);
            assertThat(refused.getStatus()).as(year + ": " + refused.getContentAsString()).isEqualTo(400);
        }
        assertThat(periodCount(entity, 2026)).isZero();
        assertThat(periodCount(entity, 2027)).isZero();
        assertThat(open(FINANCE, entity, 2026).getStatus()).as("a JSON integer opens the year").isEqualTo(201);
        assertThat(periodCount(entity, 2026)).isEqualTo(12);
    }

    @Test
    @DisplayName("#1439 without accounting.period:fiscal-year:create the call is 403 and stores nothing")
    void permissionIsRequired() throws Exception {
        UUID entity = entity("FYG");
        MockHttpServletResponse refused = open(person(52, Permission.Codes.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_READ), entity, 2026);
        assertThat(refused.getStatus()).isEqualTo(403);
        assertThat(periodCount(entity, 2026)).isZero();
    }
}
