package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.fixture.doubles.MutableClock;
import java.time.ZoneOffset;
import org.springframework.test.context.bean.override.convention.TestBean;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.forcitis.one.acceptance.organization.attendance.StandardHoursVersionRequests.Published;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Card 1310: contract 1 checks publish validation, per-scope numbering, overlap, Shanghai backdating and audit;
 * contract 2 checks scoped newest-first lists, every published field, confirmer identity and immutable predecessors;
 * contract 3 checks future/latest-only voiding, predecessor reopening, replacement publication and audit.
 * All fixtures and observations use HTTP. Only the DEFAULT round-trip test writes DEFAULT; other tests create
 * independent synthetic legal entities, so no SQL cleanup or test execution order is required.
 */
class StandardHoursVersionIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(StandardHoursVersionIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    /** Deliberately stale JVM startup; only individual writes are calibrated to their transaction day. */
    private static final LocalDate TODAY = StandardHoursVersionStaleFixture.capturedDay(postgres);

    @TestBean(name = "acceptanceClock", methodName = "staleStartupClock")
    MutableClock startupClock;

    static MutableClock staleStartupClock() {
        return new MutableClock(TODAY.atTime(10, 0).atZone(BusinessDay.ZONE).toInstant(), ZoneOffset.UTC);
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String VERSIONS = "/api/v1/organization/standard-hours-versions";
    private static final String PUBLISH = "organization.attendance:standard-hours:publish";
    private static final String READ = "organization.attendance:standard-hours:read";
    private static final String ERROR_PREFIX = "ORGANIZATION_ATTENDANCE_STANDARD_HOURS_";
    private static final String AUDIT_PREFIX = "organization.attendance.standard-hours.";
    private static final int CONFIRMER = 31;
    private static final RequestPostProcessor PUBLISHER = person(CONFIRMER, PUBLISH);
    private static final RequestPostProcessor READER = person(32, READ);
    private static final RequestPostProcessor ORDINARY = person(33);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    DataSource source;

    @Autowired
    PlatformTransactionManager transactions;

    @BeforeEach
    void useShanghaiToday() {
        setToday(TODAY);
        assertThat(clock).as("calibration uses the stale-startup application clock").isSameAs(startupClock);
        assertThat(LocalDate.ofInstant(clock.instant(), BusinessDay.ZONE)).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("默认范围首版与两个日期例外完整回读，确认人为登录用户且发布留审计")
    void defaultVersionRoundTripsAllFieldsAndAuditsPublication() throws Exception {
        int auditBefore = auditedActions(AUDIT_PREFIX + "published").size();
        Published published = publish(day -> {
            Map<String, Object> body = publication(null, day);
            body.put("dateOverrides", List.of(
                    override(day.plusDays(5), 240, "Synthetic half-day schedule"),
                    override(day.plusDays(6), 360, "Synthetic adjusted schedule")));
            return body;
        }, 1);
        String id = published.id();
        Map<String, Object> body = published.postedBody();
        LocalDate effectiveDate = published.effectiveDate();

        List<Map<String, Object>> found = versions(null);
        assertThat(found).as("DEFAULT contains exactly its first published version").hasSize(1);
        Map<String, Object> item = found.get(0);
        assertThat(item.get("standardHoursVersionId")).as("list returns the published identifier").isEqualTo(id);
        assertThat(item.get("versionNumber")).as("first DEFAULT version is numbered one").isEqualTo(1);
        assertThat(item.get("scopeKind")).as("omitting legalEntityId selects DEFAULT").isEqualTo("DEFAULT");
        assertThat(item.get("legalEntityId")).as("DEFAULT is not bound to a legal entity").isNull();
        assertThat(item.get("status")).as("published version is IN_EFFECT").isEqualTo("IN_EFFECT");
        assertThat(item.get("effectiveFromDate")).as("effective start round trips").isEqualTo(body.get("effectiveFromDate"));
        assertThat(item.get("effectiveToDate")).as("first version has an open end").isNull();
        assertThat(item.get("workdayMinutes")).as("ordinary workday minutes round trip").isEqualTo(480);
        assertThat(item.get("adjustedWorkdayMinutes")).as("adjusted workday minutes round trip").isEqualTo(420);
        assertThat(item.get("basisRef")).as("publication basis round trips").isEqualTo(body.get("basisRef"));
        assertThat(item.get("confirmedBy")).as("confirmer is taken from the authenticated principal").isEqualTo(principalName(CONFIRMER));
        assertThat(item.get("confirmedAt")).as("confirmation includes its timestamp").isInstanceOf(String.class);
        assertThat(Instant.parse((String) item.get("confirmedAt"))).as("confirmedAt is an ISO instant").isNotNull();
        assertThat(item.get("dateOverrides")).as("both overrides preserve workDate, minutes and reason independent of order")
                .isInstanceOf(List.class);
        List<Map<String, Object>> overrides = JsonPath.read(JSON.writeValueAsString(item), "$.dateOverrides");
        assertThat(overrides).as("exactly two date overrides are returned").hasSize(2);
        assertThat(overrides).as("all override fields round trip")
                .containsExactlyInAnyOrder(
                        override(effectiveDate.plusDays(5), 240, "Synthetic half-day schedule"),
                        override(effectiveDate.plusDays(6), 360, "Synthetic adjusted schedule"));
        assertThat(auditedActions(AUDIT_PREFIX + "published"))
                .as("this publication adds a published audit action regardless of audit list ordering")
                .hasSize(auditBefore + 1).contains(AUDIT_PREFIX + "published");
    }

    @Test
    @DisplayName("同范围后续版本递增并倒序列出，前版关闭区间但保留生效状态与原工时")
    void successorClosesButNeverEditsPredecessorMinutes() throws Exception {
        UUID scope = newScope();
        Published firstPublication = publish(day -> publication(scope, day), 1);
        String firstId = firstPublication.id();
        String secondId = publish(day -> {
            Map<String, Object> body = publication(scope, firstPublication.effectiveDate().plusDays(10));
            body.put("workdayMinutes", 450);
            body.put("adjustedWorkdayMinutes", 390);
            return body;
        }, 2).id();
        String thirdId = publish(day -> {
            Map<String, Object> body = publication(scope, firstPublication.effectiveDate().plusDays(20));
            body.put("workdayMinutes", 360);
            return body;
        }, 3).id();

        List<Map<String, Object>> found = versions(scope);
        assertThat(found).as("three successful publications are retained").hasSize(3);
        assertThat(found).as("versions are newest first")
                .extracting(item -> item.get("standardHoursVersionId")).containsExactly(thirdId, secondId, firstId);
        assertThat(found).as("numbers grow monotonically within a scope")
                .extracting(item -> item.get("versionNumber")).containsExactly(3, 2, 1);
        assertThat(found.get(0).get("effectiveToDate")).as("latest version remains open").isNull();
        assertThat(found.get(1).get("effectiveToDate")).as("third publication closes the second").isNotNull();
        assertThat(found.get(1).get("workdayMinutes")).as("second version keeps its own minutes").isEqualTo(450);
        assertThat(found.get(1).get("adjustedWorkdayMinutes")).as("second version keeps its own adjusted minutes").isEqualTo(390);
        Map<String, Object> first = found.get(2);
        assertThat(first.get("effectiveToDate")).as("successor closes the first version").isNotNull();
        assertThat(first.get("status")).as("closed predecessor stays IN_EFFECT").isEqualTo("IN_EFFECT");
        assertThat(first.get("workdayMinutes")).as("first version is never rewritten to successor minutes").isEqualTo(480);
        assertThat(first.get("adjustedWorkdayMinutes")).as("first adjusted minutes are immutable").isEqualTo(420);
        assertThat(first.get("effectiveFromDate")).as("first effective date is immutable").isEqualTo(firstPublication.effectiveDate().toString());
    }

    @Test
    @DisplayName("法人范围独立从一编号，空范围返回空列表且默认与不同法人互不混入")
    void entityScopesAreIndependentOfDefaultAndEachOther() throws Exception {
        List<Map<String, Object>> defaultBefore = versions(null);
        UUID entity = newScope();
        UUID otherEntity = newScope();
        assertThat(versions(entity)).as("a new entity scope is an empty items list").isEmpty();
        assertThat(versions(otherEntity)).as("another new entity scope is independently empty").isEmpty();

        String entityId = publish(day -> publication(entity, day), 1).id();
        String otherId = publish(day -> publication(otherEntity, day), 1).id();

        List<Map<String, Object>> entityItems = versions(entity);
        assertThat(entityItems).as("entity list contains only its own version").hasSize(1);
        assertThat(entityItems.get(0).get("standardHoursVersionId")).as("entity list excludes DEFAULT and other entities").isEqualTo(entityId);
        assertThat(entityItems.get(0).get("versionNumber")).as("entity numbering starts at one").isEqualTo(1);
        assertThat(entityItems.get(0).get("scopeKind")).as("entity publication declares LEGAL_ENTITY scope").isEqualTo("LEGAL_ENTITY");
        assertThat(entityItems.get(0).get("legalEntityId")).as("entity identifier round trips").isEqualTo(entity.toString());
        List<Map<String, Object>> otherItems = versions(otherEntity);
        assertThat(otherItems).as("other entity has exactly its own first version").hasSize(1);
        assertThat(otherItems.get(0).get("standardHoursVersionId")).as("other entity list does not mix versions").isEqualTo(otherId);
        assertThat(otherItems.get(0).get("legalEntityId")).as("other entity keeps its own scope identifier").isEqualTo(otherEntity.toString());
        assertThat(versions(null)).as("publishing entity versions leaves the DEFAULT list unchanged").isEqualTo(defaultBefore);
        assertThat(defaultBefore).as("DEFAULT list never contains an entity-scoped version")
                .allSatisfy(item -> assertThat(item.get("scopeKind")).as("each DEFAULT item declares DEFAULT scope").isEqualTo("DEFAULT"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPublications")
    @DisplayName("发布参数缺失、形状或范围错误返回400及INVALID，且范围保持为空")
    void invalidPublicationStoresNothing(String rule, Function<LocalDate, Map<String, Object>> input) throws Exception {
        UUID scope = newScope();
        assertError(rejectedPublication(day -> {
            Map<String, Object> body = input.apply(day);
            body.put("legalEntityId", scope.toString());
            return body;
        }, PUBLISHER), 400, "INVALID", rule);

        assertThat(versions(scope)).as(rule + ": rejected publication stores no version").isEmpty();
    }

    private static Stream<Arguments> invalidPublications() {
        return Stream.of(
                invalid("workdayMinutes is zero", "workdayMinutes", 0),
                invalid("workdayMinutes exceeds one day", "workdayMinutes", 1441),
                invalid("adjustedWorkdayMinutes is zero", "adjustedWorkdayMinutes", 0),
                invalid("adjustedWorkdayMinutes exceeds one day", "adjustedWorkdayMinutes", 1441),
                missing("basisRef is required", "basisRef"),
                invalid("basisRef is blank", "basisRef", "   "),
                invalid("basisRef exceeds 200 characters", "basisRef", "b".repeat(201)),
                invalid("override minutes is zero", "dateOverrides", day -> List.of(override(day, 0, "Synthetic reason"))),
                invalid("override minutes exceeds one day", "dateOverrides", day -> List.of(override(day, 1441, "Synthetic reason"))),
                invalid("override reason exceeds 200 characters", "dateOverrides", day -> List.of(override(day, 240, "r".repeat(201)))),
                invalid("override reason is empty", "dateOverrides", day -> List.of(override(day, 240, ""))),
                invalid("override precedes effectiveFromDate", "dateOverrides", day -> List.of(override(day.minusDays(1), 240, "Synthetic reason"))),
                missing("effectiveFromDate is required", "effectiveFromDate"),
                missing("workdayMinutes is required", "workdayMinutes"),
                missing("adjustedWorkdayMinutes is required", "adjustedWorkdayMinutes"));
    }

    @Test
    @DisplayName("同范围起始日期等于或早于最新生效版返回409及OVERLAP，不新增或改写版本")
    void equalOrEarlierEffectiveDateOverlaps() throws Exception {
        UUID scope = newScope();
        Published latest = publish(day -> publication(scope, day.plusDays(10)), 1);
        LocalDate latestDate = latest.effectiveDate();
        List<Map<String, Object>> before = versions(scope);

        for (LocalDate date : List.of(latestDate, latestDate.minusDays(1))) {
            assertError(rejectedPublication(day -> {
                assertThat(date).as("overlap input remains non-backdated").isAfterOrEqualTo(day);
                return publication(scope, date);
            }, PUBLISHER), 409, "OVERLAP", "overlap at " + date);
            assertThat(versions(scope)).as("overlap at " + date + " leaves the full scope unchanged").isEqualTo(before);
        }
    }

    @Test
    @DisplayName("任何早于上海今天的生效日一律422及BACKDATED：无版本的范围、仅有已作废版本的范围，今天生效则接受")
    void noVersionMayStartBeforeShanghaiToday() throws Exception {
        UUID empty = newScope();
        assertError(rejectedPublication(day -> publication(empty, day.minusDays(1)), PUBLISHER), 422, "BACKDATED",
                "a scope with no version yet still refuses yesterday");
        assertThat(versions(empty)).as("backdated first publication stores nothing").isEmpty();

        UUID voided = newScope();
        Published future = publish(day -> publication(voided, day.plusDays(5)), 1);
        String futureId = future.id();
        assertStatus(voidFuture(future, Map.of("reason", "Synthetic cancellation"), PUBLISHER, false), 200, "the only version is voided");
        List<Map<String, Object>> before = versions(voided);
        assertError(rejectedPublication(day -> publication(voided, day.minusDays(1)), PUBLISHER), 422, "BACKDATED",
                "a scope whose versions are all voided refuses yesterday");
        assertThat(versions(voided)).as("backdated publication into an all-voided scope stores nothing").isEqualTo(before);

        String todayId = publish(day -> publication(voided, day), 2).id();
        assertThat(versions(voided)).as("today is an accepted boundary even after the voided version: the scope holds exactly the new version and the voided one, newest first")
                .extracting(item -> item.get("standardHoursVersionId")).containsExactly(todayId, futureId);
    }

    @Test
    @DisplayName("普通角色无发布读取作废权，发布权不蕴含读取权，读取权不允许写入且拒绝不改变数据")
    void readAndPublishPermissionsAreIndependent() throws Exception {
        UUID scope = newScope();
        Function<LocalDate, Map<String, Object>> input = day -> publication(scope, day.plusDays(10));
        assertStatus(rejectedPublication(input, ORDINARY), 403, "ordinary role cannot publish");
        assertStatus(rejectedPublication(input, READER), 403, "read authority cannot publish");
        assertThat(versions(scope)).as("refused publication calls store no versions").isEmpty();
        assertStatus(list(scope, ORDINARY), 403, "ordinary role cannot list");
        assertStatus(list(scope, PUBLISHER), 403, "publish authority does not imply read authority");

        Published published = publish(input, 1);
        List<Map<String, Object>> before = versions(scope);
        assertThat(before).as("read-only caller can list the publisher's successful publication").hasSize(1);
        for (RequestPostProcessor caller : List.of(ORDINARY, READER)) {
            assertStatus(voidFuture(published, Map.of("reason", "Synthetic refused cancellation"), caller, true), 403,
                    "caller without publish authority cannot void an existing future version");
            assertStatus(rejectedPublication(day -> publication(scope, published.effectiveDate().plusDays(10)), caller), 403,
                    "caller without publish authority cannot publish a successor");
            assertThat(versions(scope)).as("refused write calls preserve the entire scope").isEqualTo(before);
        }
        assertStatus(list(scope, PUBLISHER), 403, "publisher still cannot read after publishing");
    }

    @Test
    @DisplayName("最新未来版可作废且保留VOIDED记录，前版重新开放并可在原日期发布新版本，作废留审计")
    void voidFutureLatestReopensPredecessorAndAllowsReplacement() throws Exception {
        UUID scope = newScope();
        Published firstPublication = publish(day -> publication(scope, day), 1);
        String firstId = firstPublication.id();
        Published future = publish(day -> publication(scope, firstPublication.effectiveDate().plusDays(10)), 2);
        LocalDate futureDate = future.effectiveDate();
        String futureId = future.id();
        assertThat(versions(scope).get(1).get("effectiveToDate")).as("future publication initially closes predecessor").isNotNull();
        int auditBefore = auditedActions(AUDIT_PREFIX + "voided").size();

        MockHttpServletResponse response = voidFuture(future, Map.of("reason", "  Synthetic policy correction  "), PUBLISHER, false);
        assertStatus(response, 200, "publisher can void the latest future version with a trimmed reason");
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.standardHoursVersionId"))
                .as("void returns the affected version identifier").isEqualTo(futureId);
        List<Map<String, Object>> afterVoid = versions(scope);
        assertThat(afterVoid).as("void keeps both versions listed newest first")
                .extracting(item -> item.get("standardHoursVersionId")).containsExactly(futureId, firstId);
        assertThat(afterVoid.get(0).get("status")).as("voided version remains visible as VOIDED").isEqualTo("VOIDED");
        assertThat(afterVoid.get(1).get("status")).as("predecessor remains IN_EFFECT").isEqualTo("IN_EFFECT");
        assertThat(afterVoid.get(1).get("effectiveToDate")).as("void reopens the predecessor").isNull();
        assertThat(auditedActions(AUDIT_PREFIX + "voided"))
                .as("successful void adds a voided audit action regardless of audit list ordering")
                .hasSize(auditBefore + 1).contains(AUDIT_PREFIX + "voided");

        String replacementId = publish(day -> {
            assertThat(futureDate).as("replacement reuses the still-future immutable voided date").isAfter(day);
            Map<String, Object> replacement = publication(scope, futureDate);
            replacement.put("workdayMinutes", 360);
            return replacement;
        }, 3).id();
        List<Map<String, Object>> afterReplacement = versions(scope);
        assertThat(afterReplacement).as("replacement adds a new row without deleting the voided history").hasSize(3);
        assertThat(afterReplacement.get(0).get("standardHoursVersionId")).as("replacement is the newest version").isEqualTo(replacementId);
        assertThat(afterReplacement.get(0).get("effectiveFromDate")).as("replacement may reuse the voided start date").isEqualTo(futureDate.toString());
        assertThat(afterReplacement.get(0).get("status")).as("replacement is IN_EFFECT").isEqualTo("IN_EFFECT");
        assertThat(afterReplacement.get(0).get("workdayMinutes")).as("correction is carried by a new version").isEqualTo(360);
        assertThat(afterReplacement).as("voided version is retained once with its original minutes")
                .filteredOn(item -> futureId.equals(item.get("standardHoursVersionId")))
                .singleElement().satisfies(item -> {
                    assertThat(item.get("status")).as("replacement never revives the voided version").isEqualTo("VOIDED");
                    assertThat(item.get("workdayMinutes")).as("voided version minutes are never edited").isEqualTo(480);
                });
        assertThat(afterReplacement).as("replacement closes the reopened predecessor again")
                .filteredOn(item -> firstId.equals(item.get("standardHoursVersionId")))
                .singleElement().satisfies(item -> assertThat(item.get("effectiveToDate"))
                        .as("predecessor has an end after replacement publication").isNotNull());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonFutureDates")
    @DisplayName("今天开始的最新生效版不能作废，返回422及NOT_VOIDABLE且数据不变")
    void todayAndPastVersionsCannotBeVoided(int dayOffset) throws Exception {
        UUID scope = newScope();
        Published published = publish(day -> publication(scope, day.plusDays(dayOffset)), 1);
        LocalDate effectiveDate = published.effectiveDate();
        String id = published.id();
        assertThat(effectiveDate).as("the single provider row publishes exactly on its request day")
                .isEqualTo(published.requestDay());
        List<Map<String, Object>> before = versions(scope);

        assertError(write(true, day -> {
            assertThat(effectiveDate).as("immutable published date is today or past at void time").isBeforeOrEqualTo(day);
            org.slf4j.LoggerFactory.getLogger(StandardHoursVersionIT.class).info(
                    "VERSION31_NONFUTURE_BRANCH published={} voidDay={} branch={}",
                    effectiveDate, day, effectiveDate.equals(day) ? "TODAY" : "PAST");
            return voidVersion(id, Map.of("reason", "Synthetic cancellation"), PUBLISHER);
        }), 422, "NOT_VOIDABLE",
                "effective date " + effectiveDate + " is not after Shanghai today");
        assertThat(versions(scope)).as("refused non-future void preserves every field").isEqualTo(before);
    }

    private static Stream<Integer> nonFutureDates() {
        return Stream.of(0);
    }

    @Test
    @DisplayName("未来生效但不是本范围最新版本时不可作废，返回422及NOT_VOIDABLE")
    void futureNonLatestVersionCannotBeVoided() throws Exception {
        UUID scope = newScope();
        Published firstPublication = publish(day -> publication(scope, day.plusDays(10)), 1);
        publish(day -> publication(scope, firstPublication.effectiveDate().plusDays(10)), 2);
        List<Map<String, Object>> before = versions(scope);

        assertError(voidFuture(firstPublication, Map.of("reason", "Synthetic cancellation"), PUBLISHER, true), 422, "NOT_VOIDABLE",
                "future IN_EFFECT predecessor is not the latest version");
        assertThat(versions(scope)).as("refused non-latest void preserves both versions").isEqualTo(before);
    }

    @Test
    @DisplayName("已作废版本不能重复作废，返回422及NOT_VOIDABLE且历史不变")
    void alreadyVoidedVersionCannotBeVoidedAgain() throws Exception {
        UUID scope = newScope();
        Published published = publish(day -> publication(scope, day.plusDays(10)), 1);
        assertStatus(voidFuture(published, Map.of("reason", "Synthetic initial cancellation"), PUBLISHER, false), 200,
                "a future first and latest version is voidable");
        List<Map<String, Object>> before = versions(scope);
        assertThat(before.get(0).get("status")).as("first void actually changes status to VOIDED").isEqualTo("VOIDED");

        assertError(voidFuture(published, Map.of("reason", "Synthetic repeated cancellation"), PUBLISHER, true), 422, "NOT_VOIDABLE",
                "VOIDED status disallows a second void");
        assertThat(versions(scope)).as("repeated void cannot change the retained history").isEqualTo(before);
    }

    @Test
    @DisplayName("作废不存在的版本返回404及NOT_FOUND")
    void unknownVersionCannotBeVoided() throws Exception {
        assertError(write(true, day -> voidVersion(UUID.randomUUID().toString(), Map.of("reason", "Synthetic unknown cancellation"), PUBLISHER)),
                404, "NOT_FOUND", "unknown standardHoursVersionId");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidVoidReasons")
    @DisplayName("作废理由缺失、去空白后为空或超过五百字返回400，版本保持生效")
    void invalidVoidReasonDoesNotVoid(String rule, Map<String, Object> body) throws Exception {
        UUID scope = newScope();
        Published published = publish(day -> publication(scope, day.plusDays(10)), 1);
        List<Map<String, Object>> before = versions(scope);

        assertStatus(voidFuture(published, body, PUBLISHER, true), 400, rule);
        assertThat(versions(scope)).as(rule + ": invalid void reason leaves the version unchanged").isEqualTo(before);
    }

    private static Stream<Arguments> invalidVoidReasons() {
        return Stream.of(
                Arguments.of("reason is required", Map.of()),
                Arguments.of("reason is empty", Map.of("reason", "")),
                Arguments.of("reason is blank after trimming", Map.of("reason", "   ")),
                Arguments.of("reason exceeds 500 characters", Map.of("reason", "r".repeat(501))));
    }

    @Test
    @DisplayName("分钟上下界及依据和例外理由二百字均可接受，未提供日期例外时返回空列表")
    void inclusivePublicationBoundsAndOptionalOverridesAreAccepted() throws Exception {
        UUID scope = newScope();
        Published firstPublication = publish(day -> {
            Map<String, Object> first = publication(scope, day);
            first.put("workdayMinutes", 1);
            first.put("adjustedWorkdayMinutes", 1440);
            first.put("basisRef", "b".repeat(200));
            return first;
        }, 1);
        Map<String, Object> first = firstPublication.postedBody();
        List<Map<String, Object>> firstItems = versions(scope);
        assertThat(firstItems.get(0).get("workdayMinutes")).as("minimum ordinary minutes is inclusive").isEqualTo(1);
        assertThat(firstItems.get(0).get("adjustedWorkdayMinutes")).as("maximum adjusted minutes is inclusive").isEqualTo(1440);
        assertThat(firstItems.get(0).get("basisRef")).as("basisRef accepts exactly 200 characters").isEqualTo(first.get("basisRef"));
        assertThat(firstItems.get(0).get("dateOverrides")).as("omitted optional overrides return an empty list").isEqualTo(List.of());

        List<Map<String, Object>> overrides = List.of(
                override(firstPublication.effectiveDate().plusDays(1), 1, "r"),
                override(firstPublication.effectiveDate().plusDays(2), 1440, "r".repeat(200)));
        publish(day -> {
            Map<String, Object> second = publication(scope, firstPublication.effectiveDate().plusDays(1));
            second.put("workdayMinutes", 1440);
            second.put("adjustedWorkdayMinutes", 1);
            second.put("dateOverrides", overrides);
            return second;
        }, 2);
        Map<String, Object> latest = versions(scope).get(0);
        assertThat(latest.get("workdayMinutes")).as("maximum ordinary minutes is inclusive").isEqualTo(1440);
        assertThat(latest.get("adjustedWorkdayMinutes")).as("minimum adjusted minutes is inclusive").isEqualTo(1);
        List<Map<String, Object>> actualOverrides = JsonPath.read(JSON.writeValueAsString(latest), "$.dateOverrides");
        assertThat(actualOverrides).as("override accepts minutes 1 and 1440, reasons 1 and 200 characters and the effective start date")
                .containsExactlyInAnyOrderElementsOf(overrides);
    }

    private UUID newScope() throws Exception {
        return createLegalEntity(newEntityCode(), "Synthetic card 1310 entity");
    }

    /** A legal entity code is 2 to 10 upper-case letters: SH and eight random letters. */
    private static String newEntityCode() {
        StringBuilder code = new StringBuilder("SH");
        for (int i = 0; i < 8; i++) {
            code.append((char) ('A' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
        }
        return code.toString();
    }

    private static Map<String, Object> publication(UUID scope, LocalDate effectiveDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (scope != null) {
            body.put("legalEntityId", scope.toString());
        }
        body.put("effectiveFromDate", effectiveDate.toString());
        body.put("workdayMinutes", 480);
        body.put("adjustedWorkdayMinutes", 420);
        body.put("basisRef", "Synthetic card 1310 policy");
        return body;
    }

    private static Map<String, Object> override(LocalDate date, int minutes, String reason) {
        return Map.of("workDate", date.toString(), "minutes", minutes, "reason", reason);
    }

    private static Arguments invalid(String rule, String field, Object value) {
        return invalid(rule, field, day -> value);
    }

    private static Arguments invalid(String rule, String field, Function<LocalDate, Object> value) {
        Function<LocalDate, Map<String, Object>> input = day -> {
            Map<String, Object> body = publication(null, day);
            body.put(field, value.apply(day));
            return body;
        };
        return Arguments.of(rule, input);
    }

    private static Arguments missing(String rule, String field) {
        Function<LocalDate, Map<String, Object>> input = day -> {
            Map<String, Object> body = publication(null, day);
            body.remove(field);
            return body;
        };
        return Arguments.of(rule, input);
    }

    private <T> T write(boolean expectedRejection, StandardHoursVersionRequests.Work<T> work) {
        return StandardHoursVersionRequests.request(source, transactions, clock, expectedRejection, day -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.hasResource(source)).as("same application DataSource is enlisted").isTrue();
            assertThat(TODAY).as("forced stale startup remains earlier than every real request day").isBefore(day);
            assertThat(LocalDate.ofInstant(clock.instant(), BusinessDay.ZONE)).isEqualTo(day);
            return work.run(day);
        });
    }

    private MockHttpServletResponse rejectedPublication(Function<LocalDate, Map<String, Object>> input,
            RequestPostProcessor caller) {
        return write(true, day -> postPublication(input.apply(day), caller));
    }

    private Published publish(Function<LocalDate, Map<String, Object>> input, int expectedNumber) throws Exception {
        Published published = write(false, day -> {
            Map<String, Object> body = input.apply(day);
            return new Published(publishBody(body, expectedNumber), day, body);
        });
        StandardHoursVersionRequests.committed(postgres, published, expectedNumber);
        return published;
    }

    private MockHttpServletResponse voidFuture(Published published, Map<String, Object> body,
            RequestPostProcessor caller, boolean expectedRejection) {
        return write(expectedRejection, day -> {
            assertThat(published.effectiveDate()).as("this void scenario still addresses a future version").isAfter(day);
            return voidVersion(published.id(), body, caller);
        });
    }

    private MockHttpServletResponse postPublication(Map<String, Object> body, RequestPostProcessor caller) throws Exception {
        var result = mvc.perform(post(VERSIONS).with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn();
        if (result.getResponse().getStatus() == 500) {
            StandardHoursWriteDates.assertDatabaseDateRefusal(result, postgres);
            StandardHoursVersionStaleFixture.confirmedInsertCause();
        }
        return result.getResponse();
    }

    private String publishBody(Map<String, Object> body, int expectedNumber) throws Exception {
        MockHttpServletResponse response = postPublication(body, PUBLISHER);
        assertStatus(response, 200, "valid publication returns 200");
        String content = response.getContentAsString();
        String id = JsonPath.read(content, "$.standardHoursVersionId");
        assertThat(id).as("publication returns a UUID standardHoursVersionId")
                .matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        assertThat((Integer) JsonPath.read(content, "$.versionNumber")).as("publication returns its per-scope version number")
                .isEqualTo(expectedNumber);
        return id;
    }

    private MockHttpServletResponse list(UUID scope, RequestPostProcessor caller) throws Exception {
        MockHttpServletRequestBuilder request = get(VERSIONS).with(caller);
        if (scope != null) {
            request.param("legalEntityId", scope.toString());
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private List<Map<String, Object>> versions(UUID scope) throws Exception {
        MockHttpServletResponse response = list(scope, READER);
        assertStatus(response, 200, "read authority can list scope " + scope);
        List<Map<String, Object>> items = JsonPath.read(response.getContentAsString(), "$.items");
        assertThat(items).as("list response always contains a non-null items array").isNotNull();
        return items;
    }

    private MockHttpServletResponse voidVersion(String id, Map<String, Object> body, RequestPostProcessor caller) throws Exception {
        var result = mvc.perform(post(VERSIONS + "/" + id + ":void").with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn();
        if (result.getResponse().getStatus() == 500) {
            StandardHoursVersionStaleFixture.assertVoidDateRefusal(result, postgres);
        }
        return result.getResponse();
    }

    private static void assertStatus(MockHttpServletResponse response, int expected, String rule) throws Exception {
        assertThat(response.getStatus()).as(rule + "; response: " + response.getContentAsString()).isEqualTo(expected);
    }

    private static void assertError(MockHttpServletResponse response, int status, String suffix, String rule) throws Exception {
        assertStatus(response, status, rule);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.errorCode")).as(rule + ": exact errorCode")
                .isEqualTo(ERROR_PREFIX + suffix);
    }
}
