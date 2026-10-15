package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

class QueryParameterContractTest {
    @SuppressWarnings("unused")
    static class Handlers {
        void fine(Integer limit) {
        }

        void bad(Map<String, String> unknown) {
        }
    }

    private static HandlerMethod handler(String name) {
        for (var method : Handlers.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return new HandlerMethod(new Handlers(), method);
            }
        }
        throw new AssertionError(name);
    }

    private static RequestMappingInfo info(String path) {
        return RequestMappingInfo.paths(path).methods(RequestMethod.GET).build();
    }

    private static RequestMappingInfo withoutPathPatterns() {
        return mock(RequestMappingInfo.class);
    }

    @Test
    @DisplayName("K-3a a mapping without path patterns stops the start: the /api GET handlers could not be found, so nothing may be assumed")
    void mappingWithoutPathPatternsFails() {
        assertThatThrownBy(() -> QueryParameterContract.check(Map.of(withoutPathPatterns(), handler("fine"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("path patterns").hasMessageContaining("fine");
    }

    @Test
    @DisplayName("K-3a /api GET handlers are checked, others are not")
    void onlyApiGetHandlersAreChecked() {
        assertThatCode(() -> QueryParameterContract.check(Map.of(info("/api/v1/x"), handler("fine")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> QueryParameterContract.check(Map.of(info("/api/v1/x"), handler("bad")))).hasMessageContaining("bad");
        assertThatCode(() -> QueryParameterContract.check(Map.of(info("/other/x"), handler("bad")))).doesNotThrowAnyException();
    }
}
