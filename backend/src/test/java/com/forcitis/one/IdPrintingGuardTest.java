package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An id value object is printed as its value wherever it is turned into text (String.valueOf, concatenation, logging, messages):
 * a record's default toString is "RoleId[value=…]", which once reached an API response. The call sites cannot all be told apart
 * from the bytecode, so the rule is on the type: every one-component *Id record in production code prints exactly its value.
 */
class IdPrintingGuardTest {
    private static final List<JavaClass> ID_RECORDS = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.forcitis.one").stream()
            .filter(c -> c.isRecord() && c.getSimpleName().endsWith("Id") && c.reflect().getRecordComponents().length == 1)
            .toList();

    static Object sample(Class<?> type) {
        if (type == UUID.class) {
            return TestIds.uuid(7);
        }
        if (type == long.class) {
            return Long.MIN_VALUE + 42;
        }
        if (type == String.class) {
            return "sample-7";
        }
        throw new IllegalArgumentException("add a sample value for " + type);
    }

    static List<String> problems(List<Class<?>> records) throws ReflectiveOperationException {
        List<String> problems = new ArrayList<>();
        for (Class<?> type : records) {
            RecordComponent component = type.getRecordComponents()[0];
            Constructor<?> constructor = type.getDeclaredConstructor(component.getType());
            Object value = sample(component.getType());
            Object id = constructor.newInstance(value);
            if (!id.toString().equals(String.valueOf(value))) {
                problems.add(type.getName() + " prints " + id + " instead of " + value + "; override toString to return the value");
            }
        }
        return problems;
    }

    @Test
    @DisplayName("every id value object prints as its value")
    void idsPrintTheirValue() throws ReflectiveOperationException {
        assertThat(ID_RECORDS).as(GuardMessage.of("CS-2.7", "the guard sees 15 or fewer id records, so it is scanning the wrong classes",
                "check the ID_RECORDS filter in IdPrintingGuardTest")).hasSizeGreaterThan(15);
        assertThat(problems(ID_RECORDS.stream().<Class<?>>map(JavaClass::reflect).toList())).as(GuardMessage.of("CS-2.7",
                "an id record prints as Name[value=...] instead of its value",
                "override toString in the id record to return the value")).isEmpty();
    }

    @Test
    @DisplayName("M-1 an id value object made of a UUID refuses null and the nil UUID")
    void uuidIdsRefuseNilAndNull() throws ReflectiveOperationException {
        List<Class<?>> uuidIds = ID_RECORDS.stream().<Class<?>>map(JavaClass::reflect).filter(c -> c.getRecordComponents()[0].getType() == UUID.class).toList();
        assertThat(uuidIds).as(GuardMessage.of("CS-2.7", "the guard sees 10 or fewer UUID id records, so it is scanning the wrong classes",
                "check the ID_RECORDS filter in IdPrintingGuardTest")).hasSizeGreaterThan(10);
        for (Class<?> type : uuidIds) {
            Constructor<?> constructor = type.getDeclaredConstructor(UUID.class);
            assertThat(catchThrowable(() -> constructor.newInstance((Object) null)).getCause()).as(GuardMessage.of("CS-2.7", type.getSimpleName() + " accepts null",
                    "validate in the compact constructor with Objects.requireNonNull")).isInstanceOf(NullPointerException.class);
            assertThat(catchThrowable(() -> constructor.newInstance(new UUID(0, 0))).getCause()).as(GuardMessage.of("CS-2.7", type.getSimpleName() + " accepts the nil UUID",
                    "reject the nil UUID with IllegalArgumentException in the compact constructor"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    record PlainId(UUID value) {
    }

    @Test
    @DisplayName("injected: an id record that keeps the default toString is caught")
    void aDefaultToStringIsCaught() throws ReflectiveOperationException {
        assertThat(problems(List.of(PlainId.class))).singleElement().asString().contains("PlainId");
    }
}
