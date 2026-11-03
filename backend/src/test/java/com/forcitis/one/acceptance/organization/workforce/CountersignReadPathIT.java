package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.AttributionCountersigns;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #895 leftover (a), the decider read path (one-design contract on #895, comment 6603037487, DD amendment B): two api methods on
 * AttributionCountersigns, {@code list(status, PageRequest)} and {@code get(AttributionCountersignId)}. The caller must hold the approve
 * permission; the visible rows are those of the legal entities the caller is finance head of today, plus, for a business administrator,
 * rows at escalation level 2 or more of any entity. The methods are reached by reflection (like the other probes), so the class compiles
 * before they exist; the result records are read by component name.
 */
class CountersignReadPathIT extends CountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignReadPathIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final LocalDate DAY = TODAY.plusDays(10);
    /** Words that name L3 or contact data (amendment B "never shown"); no component of the summary, the detail or the rows may contain one. */
    private static final List<String> NEVER_SHOWN = List.of("idnumber", "bankaccount", "pay", "salary", "wage", "contractamount", "phone", "mobile", "email", "address",
            "withdrawalreason");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    private ApplicationContext readContext;

    // ---- reflection access to list and get ----

    private Method method(String name) {
        for (Method candidate : AttributionCountersigns.class.getMethods()) {
            if (candidate.getName().equals(name)) {
                return candidate;
            }
        }
        throw new AssertionError("#895 contract: AttributionCountersigns has no method " + name);
    }

    private Object invoke(Method method, Object... arguments) throws Throwable {
        try {
            return method.invoke(readContext.getBean(AttributionCountersigns.class), arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    /** list(status, first page of 200) as the caller; status null = the default (OPEN). */
    private List<Map<String, Object>> list(Staff who, String status) throws Throwable {
        actIn(who);
        Method list = method("list");
        Object statusArgument = status == null ? null : enumValue(list.getParameterTypes()[0], status);
        CursorPage<?> page = (CursorPage<?>) invoke(list, statusArgument, new PageRequest(200, null));
        List<Map<String, Object>> rows = new ArrayList<>();
        page.items().forEach(item -> rows.add(plain(item)));
        return rows;
    }

    private Map<String, Object> get(Staff who, UUID id) throws Throwable {
        actIn(who);
        return plain(invoke(method("get"), new AttributionCountersignId(id)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type, name);
    }

    /** A record as name -> value: single-component id wrappers unwrapped, enums as names, nested records and lists converted. */
    private static Map<String, Object> plain(Object record) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                fields.put(component.getName(), value(component.getAccessor().invoke(record)));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
        return fields;
    }

    private static Object value(Object raw) {
        if (raw == null || raw instanceof String || raw instanceof Number || raw instanceof Boolean || raw instanceof LocalDate || raw instanceof UUID
                || raw instanceof java.time.temporal.Temporal) {
            return raw;
        }
        if (raw instanceof Enum<?> constant) {
            return constant.name();
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(CountersignReadPathIT::value).toList();
        }
        if (raw.getClass().isRecord()) {
            RecordComponent[] components = raw.getClass().getRecordComponents();
            if (components.length == 1 && components[0].getName().equals("value")) {
                try {
                    return value(components[0].getAccessor().invoke(raw));
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }
            return plain(raw);
        }
        return raw.toString();
    }

    private static List<String> idsOf(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> String.valueOf(row.get("attributionCountersignId"))).toList();
    }

    private String idOf(UUID command, UUID entity) throws Exception {
        return requestId(command, KIND_CHANGE, entity).toString();
    }

    // ---- cases ----

    @Test
    @DisplayName("#895 决定人读取 (1)(9)：E1 的财务负责人在 list 里看到 E1 的未决行、看不到 E2 的行；get 返回行的摘要字段（命令、员工、前后法人、前后部门、请求人、到期日、升级等级），rows 含同一命令两个法人的行")
    void aFinanceHeadSeesOnlyTheRowsOfItsOwnEntity() throws Throwable {
        Awaiting waiting = crossEntityTransfer(DAY);
        String ownRow = idOf(waiting.command(), e1);
        String otherRow = idOf(waiting.command(), e2);

        List<Map<String, Object>> seen = list(headE1, null);

        assertThat(idsOf(seen)).contains(ownRow).doesNotContain(otherRow);
        Map<String, Object> row = seen.stream().filter(candidate -> ownRow.equals(String.valueOf(candidate.get("attributionCountersignId")))).findFirst().orElseThrow();
        assertThat(row).containsEntry("requestKind", KIND_CHANGE).containsEntry("status", OPEN).containsEntry("effectiveDate", DAY);
        assertThat(String.valueOf(row.get("personnelCommandId"))).isEqualTo(waiting.command().toString());
        assertThat(String.valueOf(row.get("legalEntityId"))).isEqualTo(e1.toString());
        assertThat(String.valueOf(row.get("previousLegalEntityId"))).isEqualTo(e1.toString());
        assertThat(String.valueOf(row.get("newLegalEntityId"))).isEqualTo(e2.toString());
        assertThat(row.get("employeeDisplayName")).isNotNull();
        assertThat(row.get("employeeNumber")).isNotNull();
        assertThat(row.get("legalEntityName")).isNotNull();
        assertThat(row.get("requestedByDisplayName")).isNotNull();
        assertThat(row.get("dueDate")).isNotNull();
        assertThat(((Number) row.get("escalationLevel")).intValue()).isZero();

        Map<String, Object> detail = get(headE1, UUID.fromString(ownRow));
        assertThat(String.valueOf(detail.get("previousOrgUnitId"))).isEqualTo(unitE1.toString());
        assertThat(String.valueOf(detail.get("newOrgUnitId"))).isEqualTo(unitE2.toString());
        assertThat(detail.get("requestReason")).as("only a withdrawal request has a request reason").isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) detail.get("rows");
        assertThat(idsOf(rows)).as("every countersign row of the same command, both entities'").containsExactlyInAnyOrder(ownRow, otherRow);
    }

    @Test
    @DisplayName("#895 决定人读取 (2)：同一人同时是 E1 与 E2 的财务负责人，看到两个法人的行")
    void aHeadOfTwoEntitiesSeesBoth() throws Throwable {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff both = account(FINANCE_HEAD_ROLE);
        bind(both, e1);
        bind(both, e2);

        assertThat(idsOf(list(both, null))).contains(idOf(waiting.command(), e1), idOf(waiting.command(), e2));
    }

    @Test
    @DisplayName("#895 决定人读取 (3)(空范围)：持有会签权限但不是任何法人财务负责人的人，list 得到空页（不是错误），get 一个存在的行得 NOT_FOUND")
    void aPermissionHolderWhoHeadsNoEntitySeesNothing() throws Throwable {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff unbound = account(FINANCE_HEAD_ROLE);

        assertThat(list(unbound, null)).isEmpty();
        Throwable failure = refusal(() -> get(unbound, UUID.fromString(idOf(waiting.command(), e1))));
        assertThat(failure).isInstanceOf(DomainException.class);
        assertThat(((DomainException) failure).errorCode().code()).isEqualTo("ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND");
    }

    @Test
    @DisplayName("#895 决定人读取 (4)(边界)：业务管理员看不到升级等级 0、1 的行，看得到等级 2、3 的行（任何法人）；get 等级 1 的行得 NOT_FOUND")
    void aBusinessAdministratorSeesRowsFromLevelTwo() throws Throwable {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff escalated = account(ESCALATED_ROLE);
        String level1 = idOf(waiting.command(), e1);
        String level2 = idOf(waiting.command(), e2);
        escalateTo(waiting.command(), KIND_CHANGE, e1, 1);
        escalateTo(waiting.command(), KIND_CHANGE, e2, 2);

        assertThat(idsOf(list(escalated, null))).contains(level2).doesNotContain(level1);
        Throwable failure = refusal(() -> get(escalated, UUID.fromString(level1)));
        assertThat(failure).isInstanceOf(DomainException.class);
        assertThat(((DomainException) failure).errorCode().code()).isEqualTo("ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND");
        assertThat(get(escalated, UUID.fromString(level2)).get("escalationLevel")).isEqualTo(2);

        escalateTo(waiting.command(), KIND_CHANGE, e1, 3);
        assertThat(idsOf(list(escalated, null))).contains(level1, level2);
    }

    @Test
    @DisplayName("#895 决定人读取 (5)(越权)：不持有会签权限的账号（默认员工、人事专员、组织数据管理员）调用 list 与 get 都被拒绝（权限拒绝）")
    void callersWithoutThePermissionAreRefused() throws Throwable {
        Awaiting waiting = crossEntityTransfer(DAY);
        UUID row = UUID.fromString(idOf(waiting.command(), e1));

        for (Staff caller : List.of(account(), account("personnel_specialist"), account("org_data_admin"))) {
            Throwable onList = refusal(() -> list(caller, null));
            Throwable onGet = refusal(() -> get(caller, row));
            assertThat(onList).as("list").isNotNull();
            assertThat(isPermissionRefusal(onList)).as("list refused as a permission refusal: " + onList).isTrue();
            assertThat(onGet).as("get").isNotNull();
            assertThat(isPermissionRefusal(onGet)).as("get refused as a permission refusal: " + onGet).isTrue();
        }
    }

    @Test
    @DisplayName("#895 决定人读取 (6)：财务负责人变更后，新负责人看到未决行、旧负责人看不到（行属于法人，不属于人）")
    void aNewHeadSeesTheOpenRowAndTheOldHeadDoesNot() throws Throwable {
        Awaiting waiting = crossEntityTransfer(DAY);
        String row = idOf(waiting.command(), e1);
        Staff successor = account(FINANCE_HEAD_ROLE);

        bind(successor, e1);

        assertThat(idsOf(list(successor, null))).contains(row);
        assertThat(idsOf(list(headE1, null))).doesNotContain(row);
        Throwable failure = refusal(() -> get(headE1, UUID.fromString(row)));
        assertThat(failure).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#895 决定人读取 (7)：get 一个存在但看不到的行与 get 一个随机 UUID，错误码与消息完全相同（NOT_FOUND）")
    void anInvisibleRowAndAnUnknownIdLookAlike() throws Throwable {
        Awaiting waiting = crossEntityTransfer(DAY);
        UUID invisible = UUID.fromString(idOf(waiting.command(), e2));

        Throwable hidden = refusal(() -> get(headE1, invisible));
        Throwable unknown = refusal(() -> get(headE1, UUID.randomUUID()));

        assertThat(hidden).isInstanceOf(DomainException.class);
        assertThat(unknown).isInstanceOf(DomainException.class);
        DomainException first = (DomainException) hidden;
        DomainException second = (DomainException) unknown;
        assertThat(first.errorCode().code()).isEqualTo("ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND");
        assertThat(first.errorCode()).isEqualTo(second.errorCode());
        assertThat(first.kind()).isEqualTo(second.kind());
        assertThat(first.getMessage()).isEqualTo(second.getMessage());
    }

    @Test
    @DisplayName("#895 决定人读取 (8)：摘要、详情与 rows 的记录类型没有任何身份证号、银行账户、薪酬、日薪、合同金额、联系方式或旧撤回理由的成分")
    void theResultTypesCarryNoSensitiveComponent() {
        Method list = method("list");
        Class<?> summary = (Class<?>) ((ParameterizedType) list.getGenericReturnType()).getActualTypeArguments()[0];
        Class<?> detail = method("get").getReturnType();
        List<String> names = new ArrayList<>();
        for (Class<?> type : List.of(summary, detail)) {
            collect(type, names);
        }
        assertThat(names).as("components of the summary and the detail").isNotEmpty();
        for (String name : names) {
            assertThat(NEVER_SHOWN).as("component " + name).noneMatch(word -> name.toLowerCase().contains(word));
        }
    }

    private static void collect(Class<?> type, List<String> names) {
        for (RecordComponent component : type.getRecordComponents()) {
            names.add(component.getName());
            if (component.getType().isRecord()) {
                collect(component.getType(), names);
            }
            if (component.getGenericType() instanceof ParameterizedType generic && generic.getActualTypeArguments()[0] instanceof Class<?> element && element.isRecord()) {
                collect(element, names);
            }
        }
    }

    @Test
    @DisplayName("#895 决定人读取 (10)：list(status = APPROVED) 只返回调用者法人的已决行；默认（OPEN）不含已决行；别的法人已决的行看不到")
    void decidedRowsAreListedByStatusForTheCallersEntityOnly() throws Throwable {
        Awaiting waiting = crossEntityTransfer(DAY);
        String decidedOwn = idOf(waiting.command(), e1);
        String decidedOther = idOf(waiting.command(), e2);
        approve(headE1, waiting.command(), KIND_CHANGE, e1);
        approve(headE2, waiting.command(), KIND_CHANGE, e2);

        assertThat(idsOf(list(headE1, APPROVED))).contains(decidedOwn).doesNotContain(decidedOther);
        assertThat(idsOf(list(headE1, null))).as("the default is OPEN").doesNotContain(decidedOwn);
        assertThat(idsOf(list(headE3, APPROVED))).as("an unrelated head sees none").doesNotContain(decidedOwn, decidedOther);
    }
}
