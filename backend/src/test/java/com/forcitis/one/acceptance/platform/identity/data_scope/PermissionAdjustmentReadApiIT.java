package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #730 S3 (F-1 contract items 10, 11, 13, 14, 16 and constraint table rows 22, 31, 33; ADR-0032 section 5 item 5): who lists and reads
 * which permission adjustment, what an item shows, paging, and the audit entry "viewed". Callers are real sessions of people holding the
 * roles named in the contract; the items are written directly in the valid shapes of the card.
 */
class PermissionAdjustmentReadApiIT extends PermissionAdjustmentApiSupport {

    static final TestDatabase postgres = TestPostgres.database(PermissionAdjustmentReadApiIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void organisation() throws Exception {
        apiWorld();
    }

    @Test
    @DisplayName("#730 S3 契约第 10、11、13 条：组织数据管理员与安全审计员都能列出全部类型的条目（人员条目带 userId，角色条目带 roleId 与 orgUnitId），形状为 {items, nextCursor}，条目与详情带当事人的显示名 userName，不含权限码、范围与条目之外的人名（用户裁定 B，2026-11-05）")
    void bothReadersSeeEveryKindAndNoPermissionCodeOrScope() throws Exception {
        UUID sensitive = sensitiveItemAbout("mover");
        UUID transfer = scopeReviewAbout("mover", "headB");
        UUID inactive = inactiveUnitItem();

        for (String reader : List.of("oda", "auditor")) {
            MockHttpServletResponse listed = listPage(session(reader), "limit", "200");
            assertThat(listed.getStatus()).as(reader + ": " + listed.getContentAsString()).isEqualTo(200);
            assertThat((Object) JsonPath.read(listed.getContentAsString(), "$.nextCursor")).as("last page has no cursor").isNull();
            List<Map<String, Object>> items = items(listed);
            assertThat(field(items, "permissionAdjustmentId")).as(reader).contains(sensitive.toString(), transfer.toString(), inactive.toString());
            Map<String, Object> person = items.stream().filter(item -> sensitive.toString().equals(item.get("permissionAdjustmentId"))).findFirst().orElseThrow();
            assertThat(person).containsEntry("kind", "SENSITIVE_ROLE_REVIEW").containsEntry("status", "OPEN").containsEntry("userId", userId("mover"));
            Map<String, Object> role = items.stream().filter(item -> inactive.toString().equals(item.get("permissionAdjustmentId"))).findFirst().orElseThrow();
            assertThat(role).containsEntry("kind", "SCOPE_REVIEW").containsKeys("roleId", "orgUnitId");
            assertThat(person).as(reader + ": the person's name is shown to a reader who sees the item").containsEntry("userName", "Synthetic mover");
            assertNoPermissionOrScopeOrOtherPeoplesNames(listed.getContentAsString(), "Synthetic mover");
            MockHttpServletResponse detail = read(session(reader), sensitive);
            assertThat(detail.getStatus()).isEqualTo(200);
            assertThat((String) JsonPath.read(detail.getContentAsString(), "$.userName")).isEqualTo("Synthetic mover");
            assertNoPermissionOrScopeOrOtherPeoplesNames(detail.getContentAsString(), "Synthetic mover");
        }
    }

    /**
     * Contract 13 (amended by user decision B of 2026-11-05): an item shows no permission code, no access scope and no personal identifier other than the
     * display names {@code userName} and {@code businessOwnerName}; those two carry the names of the people the item is about (the person and, once upheld, the
     * business owner) and of nobody else. Checked as: no such member at any depth, no permission code prefix, and no fixture person's display name outside
     * {@code allowedNames}.
     */
    private static void assertNoPermissionOrScopeOrOtherPeoplesNames(String body, String... allowedNames) {
        Object parsed = JsonPath.parse(body).json();
        assertThat(keysOf(parsed)).as("member names of the answer").noneMatch(key -> key.matches("(?i)(permissions?|permissioncodes?|accessscope|scope|displayname|employeename|personname|employeecode|loginid|wecom.*)"));
        assertThat(body).doesNotContain("organization.workforce:", "platform.identity:");
        List<String> allowed = List.of(allowedNames);
        PEOPLE.stream().filter(key -> !allowed.contains("Synthetic " + key))
                .forEach(key -> assertThat(body).as("display name of " + key + " is not in this item").doesNotContain("\"Synthetic " + key + "\""));
    }

    private static List<String> keysOf(Object node) {
        List<String> keys = new java.util.ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                keys.add(String.valueOf(entry.getKey()));
                keys.addAll(keysOf(entry.getValue()));
            }
        } else if (node instanceof List<?> list) {
            list.forEach(element -> keys.addAll(keysOf(element)));
        }
        return keys;
    }

    @Test
    @DisplayName("#730 S3 契约第 13 条（用户裁定 B）：维持授权后，能读到条目的人在条目与列表中看到当事人与业务责任人的显示名（userName、businessOwnerName），看不到别的人名；读不到条目的人什么也看不到")
    void anUpheldItemShowsThePersonAndTheBusinessOwnerAndNobodyElse() throws Exception {
        UUID item = sensitiveItemAbout("mover");
        MockHttpServletResponse upheld = uphold(session("oda"), item.toString(), uphold("still needed for the audit", employeeIdOf("owner")));
        assertThat(upheld.getStatus()).as(upheld.getContentAsString()).isIn(200, 204);

        for (String reader : List.of("oda", "auditor")) {
            MockHttpServletResponse detail = read(session(reader), item);
            assertThat(detail.getStatus()).as(reader).isEqualTo(200);
            assertThat((String) JsonPath.read(detail.getContentAsString(), "$.userName")).isEqualTo("Synthetic mover");
            assertThat((String) JsonPath.read(detail.getContentAsString(), "$.businessOwnerName")).isEqualTo("Synthetic owner");
            assertNoPermissionOrScopeOrOtherPeoplesNames(detail.getContentAsString(), "Synthetic mover", "Synthetic owner");
            Map<String, Object> listed = items(listPage(session(reader), "limit", "200")).stream().filter(each -> item.toString().equals(each.get("permissionAdjustmentId"))).findFirst().orElseThrow();
            assertThat(listed).containsEntry("userName", "Synthetic mover").containsEntry("businessOwnerName", "Synthetic owner");
        }
        for (String caller : List.of("plain", "headA", "mover")) {
            MockHttpServletResponse hidden = read(session(caller), item);
            assertThat(hidden.getStatus()).as(caller + " still cannot read the item").isEqualTo(404);
            assertThat(hidden.getContentAsString()).as("no name leaks through the refusal").doesNotContain("Synthetic");
        }
    }

    @Test
    @DisplayName("#730 S3 裁定 6602368110 第 3 条：没有权限码、也不是收件人的人（无角色者、原部门负责人、当事人本人）列表为 200 且为空（不是 403），读取别人的条目与不存在的 ID 同为 404 且错误体一致，条目不泄露，并写 platform.identity.access.denied 审计（ADR-0026 第 6 项第 5 条）")
    void callersOutOfScopeSeeAnEmptyListAndNotFoundAlike() throws Exception {
        UUID item = sensitiveItemAbout("mover");
        UUID transfer = scopeReviewAbout("mover", "headB");

        for (String caller : List.of("plain", "headA", "mover")) {
            MockHttpServletResponse listed = listPage(session(caller), "limit", "200");
            assertThat(listed.getStatus()).as(caller + " lists").isEqualTo(200);
            assertThat(items(listed)).as(caller + " sees nothing").isEmpty();
            assertThat(listed.getContentAsString()).doesNotContain(item.toString());
            MockHttpServletResponse missing = read(session(caller), UUID.randomUUID());
            for (UUID target : List.of(item, transfer)) {
                MockHttpServletResponse read = read(session(caller), target);
                assertThat(read.getStatus()).as(caller + " reads " + target).isEqualTo(404);
                assertThat(read.getContentAsString()).doesNotContain(target.toString());
                assertThat((String) JsonPath.read(read.getContentAsString(), "$.errorCode")).as("same body as a missing id")
                        .isEqualTo(JsonPath.read(missing.getContentAsString(), "$.errorCode"));
                assertThat((String) JsonPath.read(read.getContentAsString(), "$.title")).isEqualTo(JsonPath.read(missing.getContentAsString(), "$.title"));
            }
        }
        eventually(() -> {
            assertThat(denialsAbout(item.toString())).as("ADR-0026 item 6.5: an out-of-scope read by id is audited as denied").isNotEmpty();
            assertThat(denialsAbout(transfer.toString())).isNotEmpty();
        });
    }

    @Test
    @DisplayName("#730 S3 契约第 14 条、不变量 33：列表不含关于调用人本人的条目（持有读权限也一样），其他人的条目照常列出；读自己的条目 404")
    void aListLeavesOutTheCallersOwnItems() throws Exception {
        UUID own = sensitiveItemAbout("odaSubject");
        UUID other = sensitiveItemAbout("mover2");

        List<String> ids = listedIds(session("odaSubject"));

        assertThat(ids).doesNotContain(own.toString()).contains(other.toString());
        assertThat(listedIds(session("oda"))).as("another administrator sees the item").contains(own.toString());
        assertThat(read(session("odaSubject"), own).getStatus()).as("the person reads an item about themselves").isEqualTo(404);
    }

    @Test
    @DisplayName("#730 S3 契约第 16 条来源①：新部门负责人（无权限码）只列出收件人是本人的范围复核并能读取；别人的收件人条目、敏感角色复核、角色条目都不在其列表里，读取为 404；原部门负责人（真正的原部门 F1-A）列表为空、读取 404")
    void theNewHeadSeesOnlyTheItemsAddressedToThem() throws Exception {
        UUID forB = scopeReviewAbout("mover", "headB");
        UUID forC = scopeReviewAbout("mover2", "multi");
        UUID sensitive = sensitiveItemAbout("mover");
        UUID inactive = inactiveUnitItem();

        MockHttpServletResponse listed = listPage(session("headB"), "limit", "200");
        assertThat(listed.getStatus()).as(listed.getContentAsString()).isEqualTo(200);
        assertThat(field(items(listed), "permissionAdjustmentId")).contains(forB.toString()).doesNotContain(forC.toString(), sensitive.toString(), inactive.toString());
        assertThat(read(session("headB"), forB).getStatus()).isEqualTo(200);
        for (UUID notMine : List.of(forC, sensitive, inactive)) {
            assertThat(read(session("headB"), notMine).getStatus()).as("headB reads " + notMine).isEqualTo(404);
        }
        assertThat(read(session("headA"), forB).getStatus()).as("the old unit's head is not the recipient").isEqualTo(404);
        assertThat(items(listPage(session("headA"), "limit", "200"))).as("nor does the old head see it listed").isEmpty();
        assertThat(read(session("plain"), forB).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("#730 S3 契约第 16 条来源①（待裁定 R-5 的取向）：收件人为空的范围复核，组织数据管理员能读；多重角色（安全审计员兼新部门负责人）既读全部也读收件人条目")
    void anItemWithoutARecipientIsReadByTheAdministratorAndTwoRolesAddUp() throws Exception {
        UUID noHead = scopeReviewAbout("mover", null);
        UUID forMulti = scopeReviewAbout("mover2", "multi");

        assertThat(read(session("oda"), noHead).getStatus()).isEqualTo(200);
        assertThat(listedIds(session("oda"))).contains(noHead.toString(), forMulti.toString());
        assertThat(read(session("headB"), noHead).getStatus()).as("a head who is not the recipient").isEqualTo(404);
        assertThat(listedIds(session("multi"))).contains(noHead.toString(), forMulti.toString());
        assertThat(read(session("multi"), forMulti).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("#730 S3 契约第 10 条：详情带 ETag；不存在的条目为 404，ID 形式不对为 400")
    void aDetailCarriesAVersionAndAbsentIdsAreRefused() throws Exception {
        UUID item = sensitiveItemAbout("mover");

        MockHttpServletResponse found = read(session("oda"), item);
        assertThat(found.getStatus()).isEqualTo(200);
        assertThat(found.getHeader("ETag")).isNotBlank();
        assertThat((String) JsonPath.read(found.getContentAsString(), "$.permissionAdjustmentId")).isEqualTo(item.toString());
        assertThat(readAdjustment(session("oda"), UUID.randomUUID().toString()).getStatus()).isEqualTo(404);
        assertThat(readAdjustment(session("oda"), "not-an-id").getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("#730 S3 K-3a 分页：limit=2 逐页取（带 nextCursor）得到的条目与一次取全的相同，无重复无遗漏，最后一页没有 nextCursor；limit=1 恰好一条")
    void pagingWalksEveryItemOnceAndEndsWithoutACursor() throws Exception {
        for (int i = 0; i < 5; i++) {
            sensitiveItemAbout(i % 2 == 0 ? "mover" : "mover2");
        }
        List<String> everything = listedIds(session("oda"));
        assertThat(everything.size()).isGreaterThanOrEqualTo(5);

        List<String> walked = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletResponse page = cursor == null ? listPage(session("oda"), "limit", "2") : listPage(session("oda"), "limit", "2", "cursor", cursor);
            assertThat(page.getStatus()).as(page.getContentAsString()).isEqualTo(200);
            List<Map<String, Object>> items = items(page);
            assertThat(items.size()).isLessThanOrEqualTo(2);
            walked.addAll(field(items, "permissionAdjustmentId"));
            cursor = JsonPath.read(page.getContentAsString(), "$.nextCursor");
            assertThat(++pages).as("the walk ends").isLessThan(100);
        } while (cursor != null);

        assertThat(walked).as("no duplicates").doesNotHaveDuplicates();
        assertThat(new HashSet<>(walked)).isEqualTo(new HashSet<>(everything));
        assertThat(items(listPage(session("oda"), "limit", "1"))).hasSize(1);
    }

    @Test
    @DisplayName("#730 S3 ADR-0032 第 5 项第 5 条：读取详情写一条 viewed 审计（对象类别 PERMISSION_ADJUSTMENT、对象为该条目、操作人为读者）；范围外的读取不写 viewed")
    void readingAnItemIsAuditedAsViewed() throws Exception {
        UUID item = sensitiveItemAbout("mover");
        UUID refusedItem = sensitiveItemAbout("mover2");

        assertThat(read(session("auditor"), item).getStatus()).isEqualTo(200);
        assertThat(read(session("headA"), refusedItem).getStatus()).isEqualTo(404);

        List<Map<String, Object>> viewed = auditEntries(AUDIT_PREFIX + "viewed");
        assertThat(viewed).anySatisfy(entry -> {
            assertThat(entry).containsEntry("subjectKind", "PERMISSION_ADJUSTMENT").containsEntry("subjectId", item.toString())
                    .containsEntry("actorId", userId("auditor")).containsEntry("action", AUDIT_PREFIX + "viewed");
        });
        assertThat(viewed).noneSatisfy(entry -> assertThat(entry).containsEntry("subjectId", refusedItem.toString()));
        assertThat(viewed.stream().map(entry -> String.valueOf(entry.get("detail"))).toList()).noneMatch(detail -> detail.contains("Synthetic"));
    }
}
