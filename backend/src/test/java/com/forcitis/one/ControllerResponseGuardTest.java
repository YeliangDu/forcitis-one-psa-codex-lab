package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.web.ControllerAnswerSamples;
import com.forcitis.one.kernel.CursorPage;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A handler answers with a *Response record of a web package (coding-core B, Controller row), or a sealed *Response interface of a
 * web package whose permitted types all are such answers, wrapped at most in ResponseEntity, CursorPage, List or Optional, or answers
 * nothing (void / Void): an application view or domain type returned as-is becomes the contract and changes with the use case
 * (#810 item 5). Handlers inherited from a superclass count too.
 */
class ControllerResponseGuardTest {
    /** Handlers that answer otherwise, with the reason; an entry whose handler now complies or is gone fails the stale check. */
    static final Map<String, String> OTHER_ANSWERS = Map.of(
            "com.forcitis.one.platform.audit.web.AuditExportController.export", "a file download (byte[] with its content type)");

    static final List<Class<?>> WRAPPERS = List.of(ResponseEntity.class, CursorPage.class, List.class, Optional.class);

    static boolean answersWithResponse(Type type) {
        if (type == void.class || type == Void.class) {
            return true;
        }
        if (type instanceof Class<?> raw) {
            if (!raw.getSimpleName().endsWith("Response") || !isWebPackage(raw)) {
                return false;
            }
            if (raw.isRecord()) {
                return true;
            }
            return raw.isInterface() && raw.isSealed() && Arrays.stream(raw.getPermittedSubclasses()).allMatch(ControllerResponseGuardTest::answersWithResponse);
        }
        if (type instanceof ParameterizedType parameterized && WRAPPERS.contains(parameterized.getRawType())) {
            return Arrays.stream(parameterized.getActualTypeArguments()).allMatch(ControllerResponseGuardTest::answersWithResponse);
        }
        return false;
    }

    static boolean isWebPackage(Class<?> type) {
        String name = type.getPackageName();
        return name.endsWith(".web") || name.contains(".web.");
    }

    static List<Method> handlers(Class<?> controller) {
        return Arrays.stream(ReflectionUtils.getUniqueDeclaredMethods(controller, ReflectionUtils.USER_DECLARED_METHODS))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)).toList();
    }

    static List<String> otherAnswers(Iterable<Class<?>> controllers) {
        List<String> found = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method handler : handlers(controller)) {
                if (!answersWithResponse(handler.getGenericReturnType())) {
                    found.add(controller.getName() + "." + handler.getName() + " -> " + handler.getGenericReturnType().getTypeName());
                }
            }
        }
        return found;
    }

    static List<Class<?>> controllers() {
        return ArchitectureRulesTest.CLASSES.stream().filter(type -> type.isAnnotatedWith(RestController.class))
                .<Class<?>>map(type -> type.reflect()).toList();
    }

    static String handlerName(String finding) {
        return finding.substring(0, finding.indexOf(" -> "));
    }

    @Test
    @DisplayName("every handler answers with a *Response record (or nothing), except the registered ones")
    void handlersAnswerWithResponseRecords() {
        assertThat(otherAnswers(controllers()).stream().filter(finding -> !OTHER_ANSWERS.containsKey(handlerName(finding))).toList())
                .as(GuardMessage.of("coding-core B (Controller)", "a handler answers with a type that is not a *Response record of the web package",
                        "map the result to a *Response record (wrapped at most in ResponseEntity, CursorPage, List or Optional),"
                                + " or register it in OTHER_ANSWERS with its reason"))
                .isEmpty();
    }

    @Test
    @DisplayName("every OTHER_ANSWERS entry still names a handler that answers otherwise")
    void noStaleEntries() {
        List<String> current = otherAnswers(controllers()).stream().map(ControllerResponseGuardTest::handlerName).toList();
        assertThat(OTHER_ANSWERS.keySet()).as(GuardMessage.of("coding-core B (Controller)", "an OTHER_ANSWERS entry names a handler that is gone or now answers"
                + " with a *Response record", "delete the entry")).allMatch(current::contains);
    }

    /** Right name, outside a web package. */
    record OutsideResponse(String value) {
    }

    static class OutsideController {
        @GetMapping("/o")
        OutsideResponse outside() {
            return new OutsideResponse("o");
        }
    }

    @Test
    @DisplayName("injection: views, wildcards, open or leaky *Response interfaces, inherited and non-web answers are reported;"
            + " a wrapped *Response record, a sealed *Response interface of them, void and non-handlers are not")
    void otherAnswersAreCaught() {
        String sample = ControllerAnswerSamples.SampleController.class.getName() + ".";
        assertThat(otherAnswers(List.of(ControllerAnswerSamples.SampleController.class, OutsideController.class)))
                .extracting(ControllerResponseGuardTest::handlerName)
                .containsExactlyInAnyOrder(sample + "view", sample + "anything", sample + "leaky", sample + "open", sample + "inherited",
                        OutsideController.class.getName() + ".outside");
    }
}
