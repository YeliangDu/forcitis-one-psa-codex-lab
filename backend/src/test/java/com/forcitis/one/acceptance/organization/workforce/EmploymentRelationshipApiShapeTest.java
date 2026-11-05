package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #1371 acceptance part, done-when 1, 7 and 10 of the employment-relationship design (ADR-0086) (ruled names of the lead ruling on
 * #1371): the glossary names, the read api {@code EmploymentRelationships} with exactly on, get and employerOn, and the five relationship
 * events. Types are looked up by name, so that this file compiles before the implementation exists.
 */
class EmploymentRelationshipApiShapeTest {

    private static final String API = "com.forcitis.one.organization.workforce.api.";
    private static final String EVENTS = "com.forcitis.one.organization.workforce.events.";
    private static final List<String> EVENT_NAMES = List.of("EmploymentRelationshipStarted", "EmploymentRelationshipEnded",
            "EmploymentRelationshipPhaseChanged", "EmploymentRelationshipUnpaidLeaveChanged", "EmploymentRelationshipCorrected");

    private static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("the type " + name + " does not exist yet", missing);
        }
    }

    private static Method declared(Class<?> owner, String name) {
        List<Method> found = Arrays.stream(owner.getMethods()).filter(m -> m.getName().equals(name) && m.getDeclaringClass() == owner).toList();
        assertThat(found).as(owner.getSimpleName() + " declares exactly one method " + name).hasSize(1);
        return found.get(0);
    }

    @Test
    @DisplayName("#1371 完成标准 7 与 10：EmploymentRelationships 的公开方法恰为 on、get、employerOn，且没有返回单一用人单位的 employerOf")
    void theReadApiIsExactlyOnGetAndEmployerOn() {
        Class<?> api = type(API + "EmploymentRelationships");
        Class<?> view = type(API + "EmploymentRelationshipView");
        Class<?> id = type(API + "EmploymentRelationshipId");

        List<String> names = Arrays.stream(api.getMethods()).filter(m -> !Modifier.isStatic(m.getModifiers())).map(Method::getName).sorted().toList();
        assertThat(names).as("design 6.2 and done-when 10: the public methods are exactly on, get, employerOn").containsExactly("employerOn", "get", "on");
        assertThat(names).as("done-when 7: no scalar employerOf(employee, date)").doesNotContain("employerOf");

        Method on = declared(api, "on");
        assertThat(on.getParameterTypes()).as("on(EmployeeId, LocalDate)").containsExactly(com.forcitis.one.kernel.EmployeeId.class, java.time.LocalDate.class);
        assertThat(on.getReturnType()).as("on returns a List of views").isEqualTo(List.class);
        assertThat(on.getGenericReturnType().getTypeName()).as("on returns List<EmploymentRelationshipView>").contains(view.getName());

        for (String name : List.of("get", "employerOn")) {
            Method method = declared(api, name);
            assertThat(method.getReturnType()).as(name + " returns an Optional").isEqualTo(Optional.class);
            assertThat(method.getGenericReturnType().getTypeName()).as(name + " returns Optional<EmploymentRelationshipView>").contains(view.getName());
            assertThat(method.getParameterTypes()[0]).as(name + " takes the relationship id first").isEqualTo(id);
        }
        assertThat(declared(api, "employerOn").getParameterTypes()).as("employerOn(EmploymentRelationshipId, LocalDate)").containsExactly(id, java.time.LocalDate.class);
        assertThat(declared(api, "get").getParameterCount()).as("get(EmploymentRelationshipId)").isEqualTo(1);
    }

    @Test
    @DisplayName("#1371 设计 6.2：EmploymentRelationshipView 的字段为 id、employerLegalEntityId、employerConfirmed、phaseKind、onUnpaidLeave")
    void theViewCarriesTheRuledComponents() {
        Class<?> view = type(API + "EmploymentRelationshipView");

        assertThat(view.isRecord()).as("the view is a record").isTrue();
        assertThat(Arrays.stream(view.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toList()))
                .as("design 6.2 and appendix A: id, employer, whether the employer is confirmed, phase, unpaid leave")
                .containsExactly("id", "employerLegalEntityId", "employerConfirmed", "phaseKind", "onUnpaidLeave");
    }

    @Test
    @DisplayName("#1371 完成标准 10：五个关系事件存在，每个都有 employmentRelationshipId()")
    void theFiveRelationshipEventsNameTheirRelationship() {
        Class<?> id = type(API + "EmploymentRelationshipId");
        for (String name : EVENT_NAMES) {
            Class<?> event = type(EVENTS + name);
            Method accessor;
            try {
                accessor = event.getMethod("employmentRelationshipId");
            } catch (NoSuchMethodException missing) {
                throw new AssertionError(name + " has no accessor employmentRelationshipId()", missing);
            }
            assertThat(accessor.getReturnType()).as(name + ".employmentRelationshipId() returns the relationship id type").isIn(id, java.util.UUID.class);
        }
    }

    @Test
    @DisplayName("#1371 完成标准 1：拟议词汇表文件列出新名称，状态为 proposed")
    void theProposedGlossaryListsTheNames() throws IOException {
        Path file = Path.of("glossary", "proposed", "employment-relationship.yaml");
        if (!Files.exists(file)) {
            file = Path.of("backend").resolve(file);
        }
        String yaml = Files.readString(file);

        for (String name : List.of("EmploymentRelationship", "EmploymentRelationships", "EmploymentRelationshipStarted", "EmploymentRelationshipEnded",
                "EmploymentRelationshipPhaseChanged", "EmploymentRelationshipUnpaidLeaveChanged", "EmploymentRelationshipCorrected")) {
            int at = yaml.indexOf("en: " + name + "\n");
            assertThat(at).as("done-when 1: the glossary file names " + name).isGreaterThanOrEqualTo(0);
            int next = yaml.indexOf("\n- id:", at);
            String entry = yaml.substring(at, next < 0 ? yaml.length() : next);
            assertThat(entry).as("done-when 1: " + name + " is proposed").contains("status: proposed");
        }
    }
}
