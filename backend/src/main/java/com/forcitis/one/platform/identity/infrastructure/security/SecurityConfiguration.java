package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.application.SessionSettings;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Default deny: only @PublicEndpoint handlers and the health probe are open; identity comes from the session only, never a request header. */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, PublicEndpointRegistry publicEndpoints, CachedSessionPrincipals principals,
            SessionSettings sessionSettings, Clock clock, CookieCsrfTokenRepository csrfTokenRepository,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(new SessionGuardFilter(principals, sessionSettings, clock), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers("/actuator/health").permitAll();
                    publicEndpoints.entries().forEach(entry -> requests
                            .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(entry.method(), entry.pattern()))
                            .permitAll());
                    for (String own : List.of("current", "confirm-identity", "logout")) {
                        requests.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.valueOf("current".equals(own) ? "GET" : "POST"),
                                "/api/v1/platform/sessions:" + own)).authenticated();
                    }
                    requests.anyRequest().access((authentication, context) -> {
                        var who = authentication.get();
                        boolean signedIn = who != null && who.isAuthenticated() && !(who instanceof AnonymousAuthenticationToken);
                        return new AuthorizationDecision(signedIn && (!(who instanceof IdentityAuthentication identity) || identity.identityConfirmed()));
                    });
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> resolve(exceptionResolver, request, response, failure))
                        .accessDeniedHandler((request, response, failure) -> resolve(exceptionResolver, request, response, failure)))
                // An API never replays a saved request after login; the default cache would create a session row for every anonymous 401.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    /** A refusal that no resolver takes is a fault of ours, not of the caller: answer 500 instead of an empty 200. */
    private static void resolve(HandlerExceptionResolver resolver, HttpServletRequest request, HttpServletResponse response, Exception failure)
            throws IOException {
        if (resolver.resolveException(request, response, null, failure) == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
