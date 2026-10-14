package com.forcitis.one.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * A failure in a filter happens before the dispatcher and would reach the container's error page, which answers in its own format. This
 * filter hands it to the same resolver as every other failure, so that it too is a ProblemDetail with the correlation id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class FilterFailureFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver resolver;

    FilterFailureFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException failure) {
            if (response.isCommitted()) {
                throw failure;
            }
            Exception cause = failure instanceof ServletException servlet && servlet.getCause() instanceof Exception inner ? inner : failure;
            if (resolver.resolveException(request, response, null, cause) == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
}
