package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

class UnknownQueryParametersTest {
    record Filter(String name) {
    }

    @SuppressWarnings("unused")
    static class Handlers {
        void declared(@RequestParam(required = false) Integer limit, @RequestParam("q") String search, @PathVariable UUID id, Principal caller) {
        }

        void plainSimple(String status) {
        }

        void modelAttribute(@ModelAttribute Filter filter) {
        }

        void recordWithoutAnnotation(Filter filter) {
        }

        void mapOfAll(@RequestParam Map<String, String> all) {
        }

        void multiValueMapOfAll(@RequestParam MultiValueMap<String, String> all) {
        }

        void rawRequest(HttpServletRequest request) {
        }

        void dateWithoutRequestParam(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
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

    @Test
    @DisplayName("K-3a the declared names are the @RequestParam names and plain simple parameters; path variables and the caller are not query parameters")
    void declaredNamesAreWhatTheHandlerReads() {
        assertThat(UnknownQueryParameters.declaredNames(handler("declared"))).containsExactlyInAnyOrder("limit", "q");
        assertThat(UnknownQueryParameters.declaredNames(handler("plainSimple"))).containsExactly("status");
    }

    @Test
    @DisplayName("K-3a a handler whose query parameters cannot be known fails, naming the method, instead of letting requests through or refusing good ones")
    void unaccountableParametersFailNamingTheMethod() {
        for (String name : new String[] {"modelAttribute", "recordWithoutAnnotation", "mapOfAll", "multiValueMapOfAll", "rawRequest", "dateWithoutRequestParam"}) {
            assertThatThrownBy(() -> UnknownQueryParameters.declaredNames(handler(name))).as(name).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(name);
        }
    }

    @Test
    @DisplayName("K-3a the response names at most 10 parameters of at most 64 characters, and marks what it cut")
    void namedParametersAreBounded() {
        Set<String> many = new TreeSet<>();
        for (int i = 0; i < 12; i++) {
            many.add("p" + String.format("%02d", i));
        }
        assertThat(UnknownQueryParameters.named(many)).isEqualTo("p00,p01,p02,p03,p04,p05,p06,p07,p08,p09,...");
        assertThat(UnknownQueryParameters.named(Set.of("x".repeat(100)))).isEqualTo("x".repeat(64) + "...");
        assertThat(UnknownQueryParameters.named(Set.of("bogus"))).isEqualTo("bogus");
    }
}
