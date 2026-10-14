package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.application.SessionSettings;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * The only Spring Security configuration of the application (G33). Default deny: every request needs a login session, except
 * the handlers marked with @PublicEndpoint (login callbacks, listed in architecture/public-endpoints.txt) and the health probe.
 * Every handler additionally needs @PreAuthorize or @PublicEndpoint with a reason (ArchitectureRulesTest). Sessions are
 * server-side (Spring Session JDBC, cookie HttpOnly / Secure / SameSite=Lax, see config/platform_identity.properties);
 * CSRF uses the XSRF-TOKEN cookie written back in a header; identity comes from the session only - never from a request
 * header (RULE-336).
 */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, PublicEndpointRegistry publicEndpoints, CachedSessionPrincipals principals,
            SessionSettings sessionSettings, Clock clock, CookieCsrfTokenRepository csrfTokenRepository) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(new SessionGuardFilter(principals, sessionSettings, clock), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers("/actuator/health").permitAll();
                    publicEndpoints.entries().forEach(entry -> requests
                            .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(entry.method(), entry.pattern()))
                            .permitAll());
                    requests.anyRequest().authenticated();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // An API never replays a saved request after login; the default cache would create a session row for every anonymous 401.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
