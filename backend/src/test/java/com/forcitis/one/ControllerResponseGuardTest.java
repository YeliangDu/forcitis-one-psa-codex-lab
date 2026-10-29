package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A handler answers with a *Response record of its web package (coding-core B, Controller row), wrapped at most in ResponseEntity,
 * CursorPage, List or Optional, or answers nothing (void / Void): an application view or domain type returned as-is becomes the
 * contract and changes with the use case (#810 item 5).
 */
class ControllerResponseGuardTest {
    /** Handlers that answer otherwise, with the reason; an entry whose handler now complies or is gone fails the stale check. */
    static final Map<String, String> OTHER_ANSWERS = Map.of(
            "com.forcitis.one.platform.audit.web.AuditExportController.export", "a file download (byte[] with its content type)",
            "com.forcitis.one.platform.identity.web.AuthorizationRequestController.pending",
            "answers the application's AuthorizationRequestView; needs its own *Response (an OpenAPI change, own PR)",
            "com.forcitis.one.platform.identity.web.RoleController.create", "ResponseEntity<?>: RoleResponse or a problem per outcome",
            "com.forcitis.one.platform.identity.web.RoleController.update", "ResponseEntity<?>: RoleResponse or a problem per outcome",
            "com.forcitis.one.platform.identity.web.RoleController.replaceRoles", "ResponseEntity<?>: no content or a problem per outcome");

    static final List<Class<?>> WRAPPERS = List.of(ResponseEntity.class, CursorPage.class, List.class, Optional.class);

    static boolean answersWithResponse(Type type) {
        if (type == void.class || type == Void.class) {
            return true;
        }
        if (type instanceof Class<?> raw) {
            return raw.isRecord() && raw.getSimpleName().endsWith("Response");
        }
        if (type instanceof ParameterizedType parameterized && WRAPPERS.contains(parameterized.getRawType())) {
            return Arrays.stream(parameterized.getActualTypeArguments()).allMatch(ControllerResponseGuardTest::answersWithResponse);
        }
        return false;
    }

    static List<Method> handlers(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods()).filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)).toList();
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

    record SampleResponse(String value) {
    }

    record SampleView(String value) {
    }

    /** Not a @RestController, so component scanning of the Spring tests never registers it; otherAnswers takes the class directly. */
    static class SampleController {
        @GetMapping("/a")
        SampleView view() {
            return new SampleView("a");
        }

        @GetMapping("/b")
        ResponseEntity<CursorPage<SampleResponse>> page() {
            return ResponseEntity.ok(null);
        }

        @GetMapping("/c")
        ResponseEntity<?> anything() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/d")
        void nothing() {
        }

        String notAHandler() {
            return "x";
        }
    }

    @Test
    @DisplayName("injection: a view, and a wildcard ResponseEntity, are reported; a wrapped *Response, void and non-handlers are not")
    void otherAnswersAreCaught() {
        assertThat(otherAnswers(List.of(SampleController.class))).extracting(ControllerResponseGuardTest::handlerName)
                .containsExactlyInAnyOrder(SampleController.class.getName() + ".view", SampleController.class.getName() + ".anything");
    }
}
