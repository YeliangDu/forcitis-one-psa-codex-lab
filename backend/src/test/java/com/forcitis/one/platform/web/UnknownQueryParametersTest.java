package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

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

        void fromTheSession(@RequestParam("q") String search, Filter caller) {
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

    /** Supports a Filter parameter as an argument resolver registered by the application would, filling it from the session. */
    static final HandlerMethodArgumentResolver FILTER_FROM_THE_SESSION = new HandlerMethodArgumentResolver() {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == Filter.class;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request, WebDataBinderFactory binders) {
            return new Filter("session");
        }
    };

    @Test
    @DisplayName("P2-M1 (one-1a-par) a parameter a registered argument resolver supports is not read from the query and is skipped")
    void aParameterOfARegisteredResolverIsSkipped() {
        assertThat(UnknownQueryParameters.declaredNames(handler("fromTheSession"), List.of(FILTER_FROM_THE_SESSION))).containsExactly("q");
        assertThat(UnknownQueryParameters.declaredNames(handler("recordWithoutAnnotation"), List.of(FILTER_FROM_THE_SESSION))).isEmpty();
        assertThatCode(() -> QueryParameterContract.check(Map.of(apiGet(), handler("fromTheSession")), List.of(FILTER_FROM_THE_SESSION)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("P2-M1 (one-1a-par) the same kind of parameter without a resolver that supports it still stops the start")
    void theSameParameterWithoutAResolverStillFails() {
        assertThatThrownBy(() -> UnknownQueryParameters.declaredNames(handler("fromTheSession"), List.of())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("an object bound from the query");
        assertThatThrownBy(() -> QueryParameterContract.check(Map.of(apiGet(), handler("fromTheSession")), List.of())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fromTheSession");
    }

    private static RequestMappingInfo apiGet() {
        return RequestMappingInfo.paths("/api/v1/x").methods(RequestMethod.GET).build();
    }
}
