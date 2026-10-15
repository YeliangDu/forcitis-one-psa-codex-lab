package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

class UnknownQueryParametersTest {
    private static final UnknownQueryParameters NO_RESOLVERS = new UnknownQueryParameters(List::of);

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

        void withLocale(@RequestParam(required = false) Integer limit, Locale locale) {
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
        assertThat(NO_RESOLVERS.declaredNames(handler("declared"))).containsExactlyInAnyOrder("limit", "q");
        assertThat(NO_RESOLVERS.declaredNames(handler("plainSimple"))).containsExactly("status");
    }

    @Test
    @DisplayName("K-3a a handler whose query parameters cannot be known fails, naming the method, instead of letting requests through or refusing good ones")
    void unaccountableParametersFailNamingTheMethod() {
        for (String name : new String[] {"modelAttribute", "recordWithoutAnnotation", "mapOfAll", "multiValueMapOfAll", "rawRequest", "dateWithoutRequestParam"}) {
            assertThatThrownBy(() -> NO_RESOLVERS.declaredNames(handler(name))).as(name).isInstanceOf(IllegalStateException.class)
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

    /** Fills Filter and String parameters from the session, as a resolver of the application marked @NotFromQuery would. */
    @NotFromQuery(source = "the session")
    static class FromTheSession implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == Filter.class || parameter.getParameterType() == String.class;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request, WebDataBinderFactory binders) {
            return null;
        }
    }

    /** A resolver of the application that does not say where its values come from, so it may read the query. */
    static class MayReadTheQuery implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == Filter.class;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request, WebDataBinderFactory binders) {
            return null;
        }
    }

    private static UnknownQueryParameters resolvedBy(HandlerMethodArgumentResolver... ordered) {
        return new UnknownQueryParameters(() -> List.of(ordered));
    }

    private static RequestMappingInfo apiGet() {
        return RequestMappingInfo.paths("/api/v1/x").methods(RequestMethod.GET).build();
    }

    @Test
    @DisplayName("P2-M1 (one-1a-par) a parameter whose first resolver is marked @NotFromQuery is not read from the query and is skipped")
    void aParameterOfAResolverNotFromQueryIsSkipped() {
        assertThat(resolvedBy(new FromTheSession()).declaredNames(handler("recordWithoutAnnotation"))).isEmpty();
        assertThatCode(() -> QueryParameterContract.check(Map.of(apiGet(), handler("recordWithoutAnnotation")), resolvedBy(new FromTheSession())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("P2-M1 (one-1a-par) the same kind of parameter without a resolver that supports it still stops the start")
    void theSameParameterWithoutAResolverStillFails() {
        assertThatThrownBy(() -> NO_RESOLVERS.declaredNames(handler("recordWithoutAnnotation"))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("an object bound from the query");
        assertThatThrownBy(() -> QueryParameterContract.check(Map.of(apiGet(), handler("recordWithoutAnnotation")), NO_RESOLVERS))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("recordWithoutAnnotation");
    }

    @Test
    @DisplayName("P2 recheck (a) a resolver of the application that may read the query stops the start, naming the resolver")
    void aResolverThatMayReadTheQueryStopsTheStart() {
        assertThatThrownBy(() -> QueryParameterContract.check(Map.of(apiGet(), handler("recordWithoutAnnotation")), resolvedBy(new MayReadTheQuery())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MayReadTheQuery").hasMessageContaining("@NotFromQuery");
    }

    @Test
    @DisplayName("P2 recheck (a) resolvers are asked in Spring's order: a @RequestParam taken by Spring's resolver first stays a query parameter")
    void theFirstResolverThatSupportsAParameterDecides() {
        assertThat(resolvedBy(new RequestParamMethodArgumentResolver(false), new FromTheSession()).declaredNames(handler("fromTheSession")))
                .containsExactly("q");
        assertThat(resolvedBy(new FromTheSession(), new RequestParamMethodArgumentResolver(false)).declaredNames(handler("fromTheSession"))).isEmpty();
    }

    /** The resolvers of a plain Spring MVC adapter, in the order it asks them. */
    private static UnknownQueryParameters springMvc() {
        GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext());
        context.refresh();
        RequestMappingHandlerAdapter adapter = new RequestMappingHandlerAdapter();
        adapter.setApplicationContext(context);
        adapter.afterPropertiesSet();
        return new UnknownQueryParameters(adapter::getArgumentResolvers);
    }

    @Test
    @DisplayName("P2 recheck L-7 (K-3a, one-1a-par) a Locale parameter comes from the request, not the query: ?locale=x is refused as unknown (400 PLATFORM_WEB_REQUEST_INVALID)")
    void aLocaleParameterIsNotAQueryParameter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        request.setParameter("locale", "x");
        assertThatThrownBy(() -> springMvc().preHandle(request, new MockHttpServletResponse(), handler("withLocale")))
                .isInstanceOfSatisfying(DomainException.class, refused -> {
                    assertThat(refused.errorCode()).isEqualTo(WebError.PLATFORM_WEB_REQUEST_INVALID);
                    assertThat(refused.kind()).isEqualTo(ErrorKind.INVALID);
                    assertThat(refused.extensions()).containsEntry(UnknownQueryParameters.INVALID_PARAMETERS, "locale");
                });
        assertThat(springMvc().declaredNames(handler("withLocale"))).containsExactly("limit");
    }

    @Test
    @DisplayName("P2 recheck L-7 (K-3a, one-1a-par) with Spring's own resolvers, what reads the query is still registered or still stops the start")
    void springResolversThatReadTheQueryAreStillChecked() {
        assertThat(springMvc().declaredNames(handler("declared"))).containsExactlyInAnyOrder("limit", "q");
        assertThat(springMvc().declaredNames(handler("plainSimple"))).containsExactly("status");
        for (String name : new String[] {"modelAttribute", "recordWithoutAnnotation", "mapOfAll", "multiValueMapOfAll", "rawRequest", "dateWithoutRequestParam"}) {
            assertThatThrownBy(() -> springMvc().declaredNames(handler(name))).as(name).isInstanceOf(IllegalStateException.class).hasMessageContaining(name);
        }
    }
}

