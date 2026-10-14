package com.forcitis.one.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** After the security chain (order -100): puts the signed-in user's id, never a name, into the log context. */
@Component
@Order(-50)
class UserLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean identified = authentication != null && authentication.isAuthenticated() && authentication.getName() != null
                && authentication.getName().matches("-?\\d+");
        if (identified) {
            MDC.put("userId", authentication.getName());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}
