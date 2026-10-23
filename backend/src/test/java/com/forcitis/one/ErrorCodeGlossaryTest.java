package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.ErrorCode;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DATA_DESIGN_CHECKLIST section 14 item 10 (review H-3): an error code is a term, with its Chinese name and its meaning. */
class ErrorCodeGlossaryTest {
    /** Modules whose error entries are written by their own work stream; remove the entry when the entries are merged. */
    static final Set<String> PENDING_MODULES = Set.of("com.forcitis.one.platform.approval", "com.forcitis.one.cost.timesheet");

    static List<String> codes() {
        List<String> codes = new ArrayList<>();
        for (JavaClass type : ArchitectureRulesTest.CLASSES) {
            if (!type.isEnum() || !type.isAssignableTo(ErrorCode.class) || PENDING_MODULES.stream().anyMatch(m -> type.getPackageName().startsWith(m))) {
                continue;
            }
            for (Object constant : type.reflect().getEnumConstants()) {
                codes.add(((ErrorCode) constant).code());
            }
        }
        return codes;
    }

    @Test
    @DisplayName("every ErrorCode constant has an error__<code> term in the glossary")
    void everyErrorCodeIsAGlossaryTerm() {
        Set<String> ids = Glossary.terms().stream().map(Glossary.Term::id).collect(Collectors.toSet());
        List<String> codes = codes();
        assertThat(codes).as("the guard must see the error codes").hasSizeGreaterThan(40);
        assertThat(codes.stream().filter(code -> !ids.contains("error__" + code.toLowerCase(Locale.ROOT))).toList())
                .as("add an error__<code> term (kind: error_code) to glossary/proposed/<module>.yaml").isEmpty();
    }

    @Test
    @DisplayName("injected: a code without its term is found")
    void missingTermIsFound() {
        Set<String> ids = Set.of("error__platform_web_unexpected");
        assertThat(Set.of("PLATFORM_WEB_UNEXPECTED", "PLATFORM_WEB_NEW").stream().filter(c -> !ids.contains("error__" + c.toLowerCase(Locale.ROOT))).toList())
                .containsExactly("PLATFORM_WEB_NEW");
    }
}
