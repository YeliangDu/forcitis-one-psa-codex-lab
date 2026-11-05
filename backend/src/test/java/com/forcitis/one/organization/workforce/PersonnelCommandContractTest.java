package com.forcitis.one.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import com.forcitis.one.organization.workforce.events.EmployeeHired;
import com.forcitis.one.organization.workforce.events.EmployeeRegularized;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import com.forcitis.one.organization.workforce.events.EmployeeUnpaidLeaveStarted;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.organization.workforce.events.EmployeeTransferred;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipCorrected;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipEnded;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipPhaseChanged;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipStarted;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipUnpaidLeaveChanged;
import com.forcitis.one.organization.workforce.events.SeparationHeld;
import com.forcitis.one.organization.workforce.events.SeparationHoldReleased;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The personnel command contract (SPLIT_PLAN A, #737): the command id, the events ADR-0068 names and their catalogue entries. Nothing
 * publishes these events yet, so the catalogue names this class as their publisher test until the personnel command card (#738) brings
 * the publisher and its own test.
 */
class PersonnelCommandContractTest {
    static final Path FRAGMENT = Path.of("../docs/architecture/events/workforce.yaml");
    static final String EVENTS = "com.forcitis.one.organization.workforce.events";
    static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    static final PersonnelCommandId COMMAND = new PersonnelCommandId(TestIds.uuid(737));
    static final EmployeeId EMPLOYEE = new EmployeeId(TestIds.uuid(738));
    static final LegalEntityId LEGAL_ENTITY = new LegalEntityId(TestIds.uuid(739));
    static final SeparationHoldId HOLD = new SeparationHoldId(TestIds.uuid(740));
    static final UserId RELEASER = new UserId(TestIds.uuid(741));
    static final String LEGAL_ENTITY_RULE = "信封 legalEntityId 为员工在生效日的法人";

    /** ADR-0068 evidence attachment, 事件契约: the seven named events plus EmployeeCorrected, and, named by #918 (item 3), EmployeeUnpaidLeaveStarted; the end of a leave has no event. */
    static final Set<String> ADR_0068_EVENTS = Set.of("EmployeeHired", "EmployeeRegularized", "EmployeeTransferred", "EmployeeSeparated",
            "EmployeeReinstated", "SeparationHeld", "SeparationHoldReleased", "EmployeeCorrected", "EmployeeUnpaidLeaveStarted");
    /** ADR-0086 design 6.1 adds the relationship-level events; they follow the same payload and catalogue rules as the events above. */
    static final Set<String> ADR_0086_EVENTS = Set.of("EmploymentRelationshipStarted", "EmploymentRelationshipEnded", "EmploymentRelationshipPhaseChanged",
            "EmploymentRelationshipUnpaidLeaveChanged", "EmploymentRelationshipCorrected");
    static final Set<String> ALL_EVENTS = Stream.concat(ADR_0068_EVENTS.stream(), ADR_0086_EVENTS.stream()).collect(Collectors.toCollection(TreeSet::new));
    static final String REL_FIELD = "employmentRelationshipId:EmploymentRelationshipId";
    static final EmploymentRelationshipId RELATIONSHIP = new EmploymentRelationshipId(TestIds.uuid(742));
    /** ADR-0073 item 3.7 adds a ninth event that names the command; its contract is the publisher test of #895, not the eight of ADR-0068. */
    static final Set<String> ADR_0073_EVENTS = Set.of("EmployeeLegalEntityChanged");
    static final List<String> PERSONAL_DATA_WORDS = List.of("name", "email", "mail", "gender", "phone", "mobile", "birth", "address", "card",
            "certificate", "salary", "wage", "login");

    static List<Class<?>> personnelEvents() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(EVENTS).stream()
                .<Class<?>>map(JavaClass::reflect)
                .filter(type -> type.isRecord() && DomainEvent.class.isAssignableFrom(type))
                .filter(type -> Arrays.stream(type.getRecordComponents()).anyMatch(c -> c.getType() == PersonnelCommandId.class))
                .filter(type -> !ADR_0073_EVENTS.contains(type.getSimpleName()))
                .toList();
    }

    static Set<String> names(List<Class<?>> events) {
        Set<String> names = new TreeSet<>();
        events.forEach(event -> names.add(event.getSimpleName()));
        return names;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> catalogue() throws IOException {
        try (InputStream in = Files.newInputStream(FRAGMENT)) {
            Map<String, Object> document = new Yaml().load(in);
            return (Map<String, Map<String, Object>>) ((Map<String, Object>) document.get("components")).get("messages");
        }
    }

    /** What is wrong with the payload of one event: ids first, then only ids, enums and days, nothing that reads like personal data. */
    static List<String> payloadProblems(Class<?> event) {
        List<String> problems = new ArrayList<>();
        RecordComponent[] components = event.getRecordComponents();
        String where = event.getSimpleName() + ": ";
        if (components.length < 3 || components[1].getType() != PersonnelCommandId.class || !components[1].getName().equals("personnelCommandId")
                || components[2].getType() != EmployeeId.class || !components[2].getName().equals("employeeId")) {
            problems.add(where + "the payload starts with personnelCommandId and employeeId");
        }
        for (int i = 1; i < components.length; i++) {
            RecordComponent component = components[i];
            if (!allowed(component.getGenericType())) {
                problems.add(where + component.getName() + " is " + component.getGenericType().getTypeName()
                        + ", not a typed id, an enum or a LocalDate (a day: the personnel effective date is DECLARED by the day)");
            }
            String lower = component.getName().toLowerCase(Locale.ROOT);
            PERSONAL_DATA_WORDS.stream().filter(lower::contains)
                    .forEach(word -> problems.add(where + component.getName() + " looks like personal data (" + word + ")"));
        }
        return problems;
    }

    private static boolean allowed(Type type) {
        if (type instanceof ParameterizedType list) {
            return list.getRawType() == List.class && list.getActualTypeArguments()[0] instanceof Class<?> item && item.isEnum();
        }
        if (!(type instanceof Class<?> raw)) {
            return false;
        }
        boolean typedId = raw.isRecord() && raw.getSimpleName().endsWith("Id") && raw.isAnnotationPresent(ValueObject.class)
                && raw.getRecordComponents().length == 1 && raw.getRecordComponents()[0].getType() == UUID.class;
        return typedId || raw.isEnum() || raw == LocalDate.class;
    }

    /** The day an event carries as its effective date: effectiveDate, or for a correction the first affected day. */
    static String dateComponent(Class<?> event) {
        return event == EmployeeCorrected.class ? "affectedFrom" : "effectiveDate";
    }

    @SuppressWarnings("unchecked")
    static List<String> catalogueProblems(Class<?> event, Map<String, Object> message) {
        List<String> problems = new ArrayList<>();
        String where = event.getSimpleName() + ": ";
        if (message == null) {
            return List.of(where + "not in " + FRAGMENT);
        }
        Map<String, Object> x = (Map<String, Object>) message.get("x-event");
        if (!"Employee".equals(x.get("aggregate"))) {
            problems.add(where + "the envelope's aggregate is the employee (ADR-0068 item 1.9), catalogue says " + x.get("aggregate"));
        }
        String businessDate = String.valueOf(x.get("businessDate"));
        if (businessDate.startsWith("无") || !businessDate.startsWith("人事业务生效日")) {
            problems.add(where + "businessDate is the personnel effective date (人事业务生效日), catalogue says " + businessDate);
        }
        if (!String.valueOf(message.get("description")).contains(LEGAL_ENTITY_RULE)) {
            problems.add(where + "the description states that the envelope's legal entity is the employee's on the effective date");
        }
        Map<String, Map<String, Object>> properties =
                (Map<String, Map<String, Object>>) ((Map<String, Object>) message.get("payload")).get("properties");
        List<String> declared = new ArrayList<>(properties.keySet());
        List<String> actual = Arrays.stream(event.getRecordComponents()).skip(1).map(RecordComponent::getName).toList();
        if (!declared.equals(actual)) {
            problems.add(where + "catalogue payload " + declared + " is not the record's " + actual + " in that order");
            return problems;
        }
        for (RecordComponent component : event.getRecordComponents()) {
            Map<String, Object> property = properties.get(component.getName());
            if (property == null) {
                continue;
            }
            Map<String, Object> schema = "array".equals(property.get("type")) ? (Map<String, Object>) property.get("items") : property;
            String type = component.getGenericType() instanceof ParameterizedType list
                    ? ((Class<?>) list.getActualTypeArguments()[0]).getSimpleName() : component.getType().getSimpleName();
            if (!type.equals(schema.get("x-type"))) {
                problems.add(where + component.getName() + " has x-type " + schema.get("x-type") + ", the record says " + type);
            }
            if (type.endsWith("Id") && !"uuid".equals(schema.get("format"))) {
                problems.add(where + component.getName() + " is an id, so format: uuid");
            }
            boolean nullableInCode = component.getAnnotatedType().isAnnotationPresent(Nullable.class);
            if (nullableInCode != Boolean.TRUE.equals(property.get("nullable"))) {
                problems.add(where + component.getName() + (nullableInCode ? " is @Nullable, so nullable: true" : " is never null, so not nullable"));
            }
        }
        return problems;
    }

    static EventEnvelope seal(String name, LocalDate businessDate) {
        return TestEnvelopes.INSTANCE.wrap(new EventDraft("organization.workforce." + name, 1, "Employee", EMPLOYEE.value(), 1, LEGAL_ENTITY,
                businessDate, List.of()));
    }

    static List<DomainEvent> sealedSamples() {
        return List.of(
                new EmployeeHired(seal("EmployeeHired", DAY), COMMAND, EMPLOYEE, DAY),
                new EmployeeRegularized(seal("EmployeeRegularized", DAY), COMMAND, EMPLOYEE, DAY),
                new EmployeeTransferred(seal("EmployeeTransferred", DAY), COMMAND, EMPLOYEE, DAY, List.of(TriggerFieldKind.ORG_UNIT),
                        new OrgUnitId(TestIds.uuid(1)), new OrgUnitId(TestIds.uuid(2)), new PositionId(TestIds.uuid(3)), null),
                new EmployeeSeparated(seal("EmployeeSeparated", DAY), COMMAND, EMPLOYEE, DAY),
                new EmployeeReinstated(seal("EmployeeReinstated", DAY), COMMAND, EMPLOYEE, DAY),
                new EmployeeUnpaidLeaveStarted(seal("EmployeeUnpaidLeaveStarted", DAY), COMMAND, EMPLOYEE, DAY),
                new EmployeeCorrected(seal("EmployeeCorrected", DAY), COMMAND, EMPLOYEE, DAY.minusMonths(1), null, List.of(TriggerFieldKind.ORG_UNIT),
                        new OrgUnitId(TestIds.uuid(1)), new OrgUnitId(TestIds.uuid(2)), null, null),
                new EmploymentRelationshipStarted(seal("EmploymentRelationshipStarted", DAY), COMMAND, EMPLOYEE, RELATIONSHIP, DAY),
                new EmploymentRelationshipEnded(seal("EmploymentRelationshipEnded", DAY), COMMAND, EMPLOYEE, RELATIONSHIP, DAY),
                new EmploymentRelationshipCorrected(seal("EmploymentRelationshipCorrected", DAY), COMMAND, EMPLOYEE, RELATIONSHIP, DAY),
                new EmploymentRelationshipPhaseChanged(seal("EmploymentRelationshipPhaseChanged", DAY), COMMAND, EMPLOYEE, RELATIONSHIP, DAY, EmploymentPhaseKind.REGULAR),
                new EmploymentRelationshipUnpaidLeaveChanged(seal("EmploymentRelationshipUnpaidLeaveChanged", DAY), COMMAND, EMPLOYEE, RELATIONSHIP, DAY,
                        PersonnelCommandKind.UNPAID_LEAVE_START),
                new SeparationHeld(seal("SeparationHeld", DAY), COMMAND, EMPLOYEE, DAY, HOLD),
                new SeparationHoldReleased(seal("SeparationHoldReleased", DAY), COMMAND, EMPLOYEE, DAY, HOLD, RELEASER));
    }

    @Test
    @DisplayName("ADR-0068 第 1 条第 9 项：带人事命令 ID 的事件恰为附件「事件契约」列出的八个加第 3 条的停薪留职开始（#918），不多不少")
    void theEventsAreExactlyThoseAdr0068Names() {
        assertThat(names(personnelEvents())).isEqualTo(ALL_EVENTS);
    }

    @Test
    @DisplayName("ADR-0068 第 1 条第 9 项、第 5 项：负载以人事命令 ID、员工 ID 开头，只有类型化 ID、枚举与按日的日期，不含个人信息")
    void payloadsCarryIdsAndDaysOnly() {
        List<String> problems = new ArrayList<>();
        personnelEvents().forEach(event -> problems.addAll(payloadProblems(event)));
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ADR-0068 第 6 条：生效日是人事业务生效日，按日（LocalDate）；除更正外都带 effectiveDate，更正带受影响区间")
    void theEffectiveDateIsADay() throws NoSuchMethodException {
        for (Class<?> event : personnelEvents()) {
            assertThat(event.getMethod(dateComponent(event)).getReturnType()).as(event.getSimpleName()).isEqualTo(LocalDate.class);
        }
        assertThat(EmployeeCorrected.class.getMethod("affectedTo").getReturnType()).isEqualTo(LocalDate.class);
        assertThat(Arrays.stream(EmployeeCorrected.class.getRecordComponents()).map(RecordComponent::getName)).doesNotContain("effectiveDate");
    }

    @Test
    @DisplayName("事件目录：每个人事事件已登记，聚合为员工，businessDate 为人事业务生效日，负载字段与 x-type 与记录一致")
    void theCatalogueDescribesTheContract() throws IOException {
        Map<String, Map<String, Object>> catalogue = catalogue();
        List<String> problems = new ArrayList<>();
        personnelEvents().forEach(event -> problems.addAll(catalogueProblems(event, catalogue.get(event.getSimpleName()))));
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("ADR-0068 第 1 条第 9 项：封装信封时 businessDate 取生效日；目录声明有业务日，信封不带日期即被拦下")
    void theEnvelopeCarriesTheEffectiveDate() {
        for (DomainEvent event : sealedSamples()) {
            assertThat(event.envelope().aggregateType()).isEqualTo("Employee");
            assertThat(event.envelope().aggregateId()).isEqualTo(EMPLOYEE.value());
            assertThat(event.envelope().businessDate()).isEqualTo(DAY);
            assertThat(event.envelope().legalEntityId()).as("lead 2026-10-28 Q6: the employee's legal entity on the effective date")
                    .isEqualTo(LEGAL_ENTITY);
        }
        assertThat(names(sealedSamples().stream().<Class<?>>map(Object::getClass).toList())).isEqualTo(ALL_EVENTS);
        for (String name : ALL_EVENTS) {
            assertThatThrownBy(() -> seal(name, null)).as(name).isInstanceOf(AssertionError.class).hasMessageContaining(name);
        }
    }

    @Test
    @DisplayName("ADR-0068 第 1 条第 2 项：人事命令 ID 是 workforce api 中以 UUID 为值的类型化 ID，拒绝空值与全零 UUID，对外写出标准 UUID 文本")
    void thePersonnelCommandIdIsATypedUuid() {
        assertThat(PersonnelCommandId.class.getPackageName()).isEqualTo("com.forcitis.one.organization.workforce.api");
        assertThat(PersonnelCommandId.class.isAnnotationPresent(ValueObject.class)).isTrue();
        assertThat(PersonnelCommandId.class.getRecordComponents()).extracting(RecordComponent::getType).containsExactly(UUID.class);
        assertThat(COMMAND.toString()).isEqualTo(TestIds.uuid(737).toString());
        assertThatThrownBy(() -> new PersonnelCommandId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PersonnelCommandId(new UUID(0, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("调岗的触发字段只有部门与岗位，负载中的列表不随调用方改变")
    void theTriggerFieldsAreFixed() {
        assertThat(TriggerFieldKind.values()).containsExactly(TriggerFieldKind.ORG_UNIT, TriggerFieldKind.POSITION);
        List<TriggerFieldKind> fields = new ArrayList<>(List.of(TriggerFieldKind.POSITION));
        EmployeeTransferred transferred = new EmployeeTransferred(seal("EmployeeTransferred", DAY), COMMAND, EMPLOYEE, DAY, fields,
                new OrgUnitId(TestIds.uuid(1)), new OrgUnitId(TestIds.uuid(1)), new PositionId(TestIds.uuid(3)), new PositionId(TestIds.uuid(4)));
        fields.add(TriggerFieldKind.ORG_UNIT);
        assertThat(transferred.triggerFieldKinds()).containsExactly(TriggerFieldKind.POSITION);
        assertThatThrownBy(() -> transferred.triggerFieldKinds().add(TriggerFieldKind.ORG_UNIT)).isInstanceOf(UnsupportedOperationException.class);
    }

    static List<String> extraPayload(Class<?> event) {
        return Arrays.stream(event.getRecordComponents()).skip(event == EmployeeCorrected.class ? 3 : 4)
                .map(c -> c.getName() + ":" + c.getType().getSimpleName()).toList();
    }

    @Test
    @DisplayName("lead 2026-10-28 Q4、Q5：调岗另带前后部门与前后岗位，暂停带待放行离职记录 ID，放行另带放行人账号 ID（运维命令放行时为空）")
    void theExtraPayloadIsWhatTheLeadDecided() {
        assertThat(extraPayload(EmployeeTransferred.class)).containsExactly("triggerFieldKinds:List", "previousOrgUnitId:OrgUnitId",
                "newOrgUnitId:OrgUnitId", "previousPositionId:PositionId", "newPositionId:PositionId", REL_FIELD);
        assertThat(extraPayload(SeparationHeld.class)).containsExactly("separationHoldId:SeparationHoldId");
        assertThat(extraPayload(SeparationHoldReleased.class)).containsExactly("separationHoldId:SeparationHoldId", "releasedBy:UserId");
        assertThat(extraPayload(EmployeeCorrected.class)).containsExactly("affectedFrom:LocalDate", "affectedTo:LocalDate", "triggerFieldKinds:List",
                "previousOrgUnitId:OrgUnitId", "newOrgUnitId:OrgUnitId", "previousPositionId:PositionId", "newPositionId:PositionId", REL_FIELD);
        for (Class<?> event : List.of(EmployeeHired.class, EmployeeRegularized.class, EmployeeSeparated.class, EmployeeReinstated.class, EmployeeUnpaidLeaveStarted.class)) {
            assertThat(extraPayload(event)).as(event.getSimpleName() + " carries the relationship it belongs to and nothing else").containsExactly(REL_FIELD);
        }
        assertThat(SeparationHoldId.class.getPackageName()).isEqualTo(PersonnelCommandId.class.getPackageName());
        assertThat(PositionId.class.getPackageName()).isEqualTo(PersonnelCommandId.class.getPackageName());
    }

    record WithName(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate, String fullName)
            implements DomainEvent {
    }

    record WithTimestamp(EventEnvelope envelope, EmployeeId employeeId, PersonnelCommandId personnelCommandId, java.time.Instant effectiveDate)
            implements DomainEvent {
    }

    @Test
    @DisplayName("injection：带姓名文本、带时刻、ID 次序颠倒的负载，与目录对不上的负载都被拦下")
    void injectedSamplesAreCaught() throws IOException {
        assertThat(payloadProblems(WithName.class)).anySatisfy(p -> assertThat(p).contains("fullName is java.lang.String"))
                .anySatisfy(p -> assertThat(p).contains("looks like personal data (name)"));
        assertThat(payloadProblems(WithTimestamp.class)).anySatisfy(p -> assertThat(p).contains("starts with personnelCommandId and employeeId"))
                .anySatisfy(p -> assertThat(p).contains("effectiveDate is java.time.Instant"));
        Map<String, Object> hired = catalogue().get("EmployeeHired");
        assertThat(catalogueProblems(WithName.class, hired)).anySatisfy(p -> assertThat(p).contains("is not the record's"));
        assertThat(catalogueProblems(EmployeeSeparated.class, null)).anySatisfy(p -> assertThat(p).contains("not in"));
        Map<String, Object> corrected = catalogue().get("EmployeeCorrected");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> properties =
                (Map<String, Map<String, Object>>) ((Map<String, Object>) corrected.get("payload")).get("properties");
        properties.get("affectedTo").remove("nullable");
        corrected.put("description", "无法人说明");
        assertThat(catalogueProblems(EmployeeCorrected.class, corrected)).anySatisfy(p -> assertThat(p).contains("affectedTo is @Nullable"))
                .anySatisfy(p -> assertThat(p).contains("legal entity is the employee's"));
    }
}
