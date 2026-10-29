package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The generated client treats a field that is not declared nullable as always present. A response component of a boxed type exists
 * only to be able to say "no value", so it must say so with @Nullable (which the contract then declares as nullable).
 */
class NullableResponseGuardTest {
    static final Set<Class<?>> BOXED = Set.of(Integer.class, Long.class, Boolean.class, Double.class, Short.class, Optional.class);

    static List<String> unmarked(Class<?> record) {
        List<String> problems = new ArrayList<>();
        for (RecordComponent component : record.getRecordComponents()) {
            if (BOXED.contains(component.getType()) && !component.getAnnotatedType().isAnnotationPresent(Nullable.class)
                    && !component.isAnnotationPresent(Nullable.class)) {
                problems.add(record.getName() + "." + component.getName() + " is a boxed type without @Nullable");
            }
        }
        return problems;
    }

    @Test
    @DisplayName("a boxed component of a web response record carries @Nullable")
    void boxedResponseComponentsAreNullable() {
        List<String> problems = new ArrayList<>();
        for (JavaClass type : ArchitectureRulesTest.CLASSES) {
            if (type.isRecord() && (type.getPackageName().endsWith(".web") || type.getPackageName().contains(".web.")) && type.getSimpleName().contains("Response")
                    || type.isRecord() && type.getEnclosingClass().map(c -> c.getSimpleName().endsWith("Response")).orElse(false)) {
                problems.addAll(unmarked(type.reflect()));
            }
        }
        assertThat(problems).as(GuardMessage.of("CS-2.23", "a boxed component of a web response record has no @Nullable, so the generated client treats it as always present",
                "annotate the component with org.jspecify.annotations.Nullable, or make it a primitive when it always has a value")).isEmpty();
    }

    record Sample(Long number, @Nullable Integer other, long plain) {
    }

    @Test
    @DisplayName("injected: a boxed component without the mark is found, a marked one and a primitive are not")
    void unmarkedIsFound() {
        assertThat(unmarked(Sample.class)).containsExactly(Sample.class.getName() + ".number is a boxed type without @Nullable");
    }
}
