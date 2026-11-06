package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.organization.attendance.domain.StandardHoursRepository;
import com.forcitis.one.organization.attendance.domain.StandardHoursScopeTail;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** #1335: publication gaps that the #1310 reviews left open: a lost race, a NUL character, override bounds. */
class StandardHoursPublishGapsIT extends AcceptanceSupport {
    static final TestDatabase postgres = TestPostgres.database(StandardHoursPublishGapsIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String VERSIONS = "/api/v1/organization/standard-hours-versions";
    private static final RequestPostProcessor PUBLISHER = person(43, "organization.attendance:standard-hours:publish",
            "organization.attendance:standard-hours:read");

    @MockitoSpyBean
    StandardHoursRepository repository;

    @AfterEach
    void releaseTheRepository() {
        reset(repository);
    }

    /** A legal entity code is 2 to 10 upper-case letters: GP and eight random letters. */
    private static String newEntityCode() {
        StringBuilder code = new StringBuilder("GP");
        for (int i = 0; i < 8; i++) {
            code.append((char) ('A' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
        }
        return code.toString();
    }

    private static LocalDate shanghaiToday() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }

    private MockHttpServletResponse publish(String body) throws Exception {
        return mvc.perform(post(VERSIONS).with(PUBLISHER).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private static String body(UUID entity, LocalDate from, String basis, String overrides) {
        return "{\"legalEntityId\":\"" + entity + "\",\"effectiveFromDate\":\"" + from + "\",\"workdayMinutes\":480,\"adjustedWorkdayMinutes\":420,\"basisRef\":\"" + basis + "\""
                + (overrides == null ? "" : ",\"dateOverrides\":" + overrides) + "}";
    }

    private int storedVersionCount(UUID entity) throws Exception {
        MockHttpServletResponse listed = mvc.perform(get(VERSIONS).param("legalEntityId", entity.toString()).with(PUBLISHER)).andReturn().getResponse();
        return ((List<?>) JsonPath.read(listed.getContentAsString(), "$.items")).size();
    }

    @Test
    @DisplayName("#1335 two publishes that both read the same tail: the one that loses answers 409 OVERLAP, not 500, and exactly one version is stored")
    void losingTheRaceIsAConflict() throws Exception {
        LocalDate today = shanghaiToday();
        UUID entity = createLegalEntity(newEntityCode(), "Synthetic gap entity");
        CyclicBarrier bothHaveReadTheTail = new CyclicBarrier(2);
        doAnswer(call -> {
            Object tail = call.callRealMethod();
            bothHaveReadTheTail.await(10, TimeUnit.SECONDS);
            return tail;
        }).when(repository).tail(any());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MockHttpServletResponse> one = pool.submit(() -> publish(body(entity, today.plusDays(5), "Synthetic basis one", null)));
            Future<MockHttpServletResponse> other = pool.submit(() -> publish(body(entity, today.plusDays(6), "Synthetic basis two", null)));
            List<MockHttpServletResponse> answers = List.of(one.get(30, TimeUnit.SECONDS), other.get(30, TimeUnit.SECONDS));

            assertThat(answers.stream().map(MockHttpServletResponse::getStatus).sorted().toList())
                    .as(answers.stream().map(a -> {
                        try {
                            return a.getContentAsString();
                        } catch (Exception e) {
                            return e.toString();
                        }
                    }).toList().toString()).containsExactly(200, 409);
            MockHttpServletResponse loser = answers.stream().filter(a -> a.getStatus() == 409).findFirst().orElseThrow();
            assertThat((String) JsonPath.read(loser.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_ATTENDANCE_STANDARD_HOURS_OVERLAP");
        } finally {
            pool.shutdownNow();
        }
        reset(repository);
        assertThat(storedVersionCount(entity)).as("only the winner is stored").isEqualTo(1);
    }

    @Test
    @DisplayName("#1335 a publish whose tail is stale because the winner committed before its insert: the ordering trigger refuses it and the answer is 409, not 500")
    void losingToAnAlreadyCommittedPublishIsAConflict() throws Exception {
        LocalDate today = shanghaiToday();
        UUID entity = createLegalEntity(newEntityCode(), "Synthetic gap entity");
        assertThat(publish(body(entity, today.plusDays(5), "Synthetic basis one", null)).getStatus()).isEqualTo(200);
        doAnswer(call -> StandardHoursScopeTail.empty()).when(repository).tail(any());
        for (int day : new int[] {3, 5, 8}) {
            MockHttpServletResponse loser = publish(body(entity, today.plusDays(day), "Synthetic basis stale", null));

            assertThat(loser.getStatus()).as("stale tail, date +" + day + ": " + loser.getContentAsString()).isEqualTo(409);
            assertThat((String) JsonPath.read(loser.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_ATTENDANCE_STANDARD_HOURS_OVERLAP");
        }
        reset(repository);
        assertThat(storedVersionCount(entity)).as("only the first publish is stored").isEqualTo(1);
    }

    @Test
    @DisplayName("#1335 a NUL character in the basis, an override reason or a void reason is refused at the request edge with 400 PLATFORM_WEB_REQUEST_INVALID (#1557), not 500, and nothing is stored")
    void nulCharacterIsRefused() throws Exception {
        LocalDate today = shanghaiToday();
        UUID entity = createLegalEntity(newEntityCode(), "Synthetic gap entity");
        MockHttpServletResponse inBasis = publish(body(entity, today.plusDays(1), "Synthetic\\u0000basis", null));
        MockHttpServletResponse inReason = publish(body(entity, today.plusDays(1), "Synthetic basis",
                "[{\"workDate\":\"" + today.plusDays(2) + "\",\"minutes\":300,\"reason\":\"Synthetic\\u0000reason\"}]"));

        for (MockHttpServletResponse refused : List.of(inBasis, inReason)) {
            assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(400);
            assertThat((String) JsonPath.read(refused.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_WEB_REQUEST_INVALID");
        }
        assertThat(storedVersionCount(entity)).as("nothing is stored").isZero();

        MockHttpServletResponse published = publish(body(entity, today.plusDays(1), "Synthetic basis", null));
        assertThat(published.getStatus()).as(published.getContentAsString()).isEqualTo(200);
        String id = JsonPath.read(published.getContentAsString(), "$.standardHoursVersionId");
        MockHttpServletResponse voided = mvc.perform(post(VERSIONS + "/" + id + ":void").with(PUBLISHER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Synthetic\\u0000reason\"}")).andReturn().getResponse();
        assertThat(voided.getStatus()).as(voided.getContentAsString()).isEqualTo(400);
        assertThat((String) JsonPath.read(voided.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_WEB_REQUEST_INVALID");
    }

    @Test
    @DisplayName("#1335 override minutes of 0, 1441 or a value beyond int are refused with 400 INVALID, like the version minutes")
    void overrideMinutesOutsideTheBoundsAreRefused() throws Exception {
        LocalDate today = shanghaiToday();
        UUID entity = createLegalEntity(newEntityCode(), "Synthetic gap entity");
        for (String minutes : new String[] {"0", "-1", "1441", "99999999999"}) {
            MockHttpServletResponse refused = publish(body(entity, today.plusDays(1), "Synthetic basis",
                    "[{\"workDate\":\"" + today.plusDays(2) + "\",\"minutes\":" + minutes + ",\"reason\":\"Synthetic\"}]"));

            assertThat(refused.getStatus()).as(minutes + " -> " + refused.getContentAsString()).isEqualTo(400);
            assertThat((String) JsonPath.read(refused.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID");
        }
        assertThat(storedVersionCount(entity)).as("nothing is stored").isZero();
    }
}
