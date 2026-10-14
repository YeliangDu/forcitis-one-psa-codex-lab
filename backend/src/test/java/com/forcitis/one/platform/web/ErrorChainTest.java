package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExternalSourceBrokenException;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WorkContext;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class ErrorChainTest {

    enum StubError implements ErrorCode {
        COST_TIMESHEET_PROJECT_MANAGER_INVALID
    }

    @RestController
    static class StubController {
        @GetMapping("/stub/refusal")
        String refusal(@RequestParam ErrorKind kind) {
            throw new DomainException(StubError.COST_TIMESHEET_PROJECT_MANAGER_INVALID, kind, "the project manager field is not a person");
        }

        @GetMapping("/stub/unavailable")
        String unavailable() {
            throw new ExternalSystemUnavailableException("source down");
        }

        @GetMapping("/stub/broken")
        String broken() {
            throw new ExternalSourceBrokenException("tree has a hole");
        }

        @GetMapping("/stub/crash")
        String crash() {
            throw new IllegalStateException("secret internals");
        }

        @GetMapping("/stub/denied")
        String denied() {
            throw new AccessDeniedException("no");
        }

        @GetMapping("/stub/number")
        String number(@RequestParam int count) {
            return "x";
        }
    }

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("clock", Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new StubController()).setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter(Clock.systemUTC())).build();
    }

    @Test
    @DisplayName("a domain refusal of kind UNPROCESSABLE answers 422 with the ErrorCode in a ProblemDetail, not a 500")
    void unprocessableRefusal() throws Exception {
        mvc.perform(get("/stub/refusal").param("kind", "UNPROCESSABLE"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.errorCode").value("COST_TIMESHEET_PROJECT_MANAGER_INVALID"))
                .andExpect(jsonPath("$.type").value("urn:forcitis:error:COST_TIMESHEET_PROJECT_MANAGER_INVALID"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("every ErrorKind maps to its own HTTP status")
    void everyKindHasItsStatus() throws Exception {
        for (ErrorKind kind : ErrorKind.values()) {
            mvc.perform(get("/stub/refusal").param("kind", kind.name())).andExpect(status().is(kind.status()));
        }
    }

    @Test
    @DisplayName("external system failures are 503 and 502 and the message of the failure stays out of the answer")
    void externalFailures() throws Exception {
        mvc.perform(get("/stub/unavailable")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_SOURCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("source down"))));
        mvc.perform(get("/stub/broken")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_SOURCE_BROKEN"));
    }

    @Test
    @DisplayName("an unexpected failure is a 500 that reveals nothing of the exception")
    void unexpectedFailure() throws Exception {
        String body = mvc.perform(get("/stub/crash")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_UNEXPECTED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret internals");
    }

    @Test
    @DisplayName("a malformed request is a 400 ProblemDetail with the request error code")
    void malformedRequest() throws Exception {
        mvc.perform(get("/stub/number").param("count", "many")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_REQUEST_INVALID"));
    }

    @Test
    @DisplayName("access denial is a 403 ProblemDetail with its own error code, not a 500")
    void accessDeniedIsNotSwallowed() throws Exception {
        mvc.perform(get("/stub/denied")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_ACCESS_DENIED"))
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }

    @Test
    @DisplayName("the correlation id of the request header is echoed, and a missing one is generated")
    void correlationIdIsEchoedOrGenerated() throws Exception {
        UUID given = UUID.randomUUID();
        mvc.perform(get("/stub/crash").header(WorkContext.HEADER, given.toString()))
                .andExpect(header().string(WorkContext.HEADER, given.toString()))
                .andExpect(jsonPath("$.correlationId").value(given.toString()));
        String generated = mvc.perform(get("/stub/crash")).andReturn().getResponse().getHeader(WorkContext.HEADER);
        assertThat(UUID.fromString(generated)).isNotNull();
    }

    @Test
    @DisplayName("a correlation id that is not a UUID is refused with 400, never replaced silently")
    void malformedCorrelationIdIsRefused() throws Exception {
        mvc.perform(get("/stub/crash").header(WorkContext.HEADER, "not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_CORRELATION_INVALID"));
    }

    @Test
    @DisplayName("the correlation scope ends with the request")
    void scopeEndsWithTheRequest() throws Exception {
        mvc.perform(get("/stub/crash"));
        assertThat(WorkContext.correlationId()).isEmpty();
    }
}
