package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs before security so a refused request is traceable too. A supplied id that is not a UUID is refused, not replaced. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

    private final Clock clock;

    CorrelationIdFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(WorkContext.HEADER);
        UUID correlationId;
        if (supplied == null || supplied.isBlank()) {
            correlationId = Uuids.timeOrdered(clock);
        } else {
            try {
                correlationId = Uuids.parseCanonical(supplied.trim());
            } catch (IllegalArgumentException malformed) {
                refuse(response);
                return;
            }
        }
        response.setHeader(WorkContext.HEADER, correlationId.toString());
        try (WorkContext.Scope scope = WorkContext.enter(correlationId, null, null)) {
            chain.doFilter(request, response);
        }
    }

    private static void refuse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"" + ProblemDetails.TYPE_PREFIX + WebError.PLATFORM_WEB_CORRELATION_INVALID.code()
                + "\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"" + WorkContext.HEADER
                + " must be a UUID\",\"errorCode\":\"" + WebError.PLATFORM_WEB_CORRELATION_INVALID.code() + "\"}");
    }
}
