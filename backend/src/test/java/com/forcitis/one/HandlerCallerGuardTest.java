package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.UserId;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * A handler takes no UserId without a binding annotation (coding-core B, Caller row; #812 GS-2): the caller comes from kernel.CurrentUser
 * in the use case. No resolver fills a bare UserId any more, so Spring would bind it from the request's parameters (?value=) on a write.
 * A UserId named by @PathVariable or @RequestParam is an explicit request value and is allowed.
 */
class HandlerCallerGuardTest {

    static List<String> bareUserIds(Iterable<Class<?>> controllers) {
        List<String> found = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method handler : ControllerResponseGuardTest.handlers(controller)) {
                for (Parameter parameter : handler.getParameters()) {
                    if (parameter.getType() == UserId.class && parameter.getAnnotations().length == 0) {
                        found.add(controller.getName() + "." + handler.getName());
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("no handler takes a bare UserId")
    void handlersTakeNoBareUserId() {
        assertThat(bareUserIds(ControllerResponseGuardTest.controllers()))
                .as(GuardMessage.of("coding-core B (Caller)", "a handler takes a UserId without a binding annotation, which Spring binds from the request",
                        "drop the parameter and inject kernel.CurrentUser in the use case"))
                .isEmpty();
    }

    /** Not a @RestController, so component scanning of the Spring tests never registers it; bareUserIds takes the class directly. */
    static class SampleController {
        @PostMapping("/a")
        void caller(UserId caller) {
        }

        @PostMapping("/b/{userId}")
        void path(@PathVariable UserId userId) {
        }

        void notAHandler(UserId caller) {
        }
    }

    @Test
    @DisplayName("injection: a bare UserId on a handler is reported; an annotated one and non-handlers are not")
    void bareUserIdsAreCaught() {
        assertThat(bareUserIds(List.of(SampleController.class))).containsExactly(SampleController.class.getName() + ".caller");
    }
}
