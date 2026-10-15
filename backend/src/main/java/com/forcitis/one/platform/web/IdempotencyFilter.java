package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdempotentResponses;
import com.forcitis.one.kernel.UserId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A write that carries Idempotency-Key gets the first answer again when it is sent again with the same key and the same request
 * within 24 hours; the same key with another request is refused (422). Runs after the security filters, so the key is the signed-in
 * user's own.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
class IdempotencyFilter extends OncePerRequestFilter {
    static final String HEADER = "Idempotency-Key";
    static final Duration LIFETIME = Duration.ofHours(24);
    private static final int MAX_KEY_LENGTH = 128;
    private static final List<String> REPLAYED_HEADERS = List.of("ETag", "Location");

    private final IdempotentResponses responses;
    private final Clock clock;
    private final JsonMapper json;
    private final Duration inProgressTimeout;

    IdempotencyFilter(IdempotentResponses responses, Clock clock, JsonMapper json,
            @Value("${forcitis.platform.web.idempotency.in-progress-timeout}") Duration inProgressTimeout) {
        if (inProgressTimeout.isNegative() || inProgressTimeout.isZero()) {
            throw new IllegalArgumentException("forcitis.platform.web.idempotency.in-progress-timeout must be positive");
        }
        this.responses = responses;
        this.clock = clock;
        this.json = json;
        this.inProgressTimeout = inProgressTimeout;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(HEADER) == null || !IdempotencyScope.applies(request.getMethod(), request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        UserId userId;
        try {
            userId = UserId.parse(authentication.getName());
        } catch (IllegalArgumentException brokenSession) {
            refuse(response, WebError.PLATFORM_WEB_UNAUTHENTICATED, ErrorKind.UNAUTHENTICATED);
            return;
        }
        String key = request.getHeader(HEADER).trim();
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH || !key.chars().allMatch(c -> c > 32 && c < 127)) {
            refuse(response, WebError.PLATFORM_WEB_REQUEST_INVALID, ErrorKind.INVALID);
            return;
        }
        CachedBodyRequest cached = new CachedBodyRequest(request);
        String requestHash = hash(request, cached.body());
        Instant now = clock.instant();
        if (responses.claim(userId, key, requestHash, now, now.plus(LIFETIME), inProgressTimeout)) {
            runAndStore(userId, key, cached, response, chain);
            return;
        }
        Optional<IdempotentResponses.Stored> stored = responses.find(userId, key);
        if (stored.isEmpty()) {
            runAndStore(userId, key, cached, response, chain);
        } else if (!stored.get().requestHash().equals(requestHash)) {
            refuse(response, WebError.PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED, ErrorKind.UNPROCESSABLE);
        } else if (!stored.get().finished()) {
            refuse(response, WebError.PLATFORM_WEB_IDEMPOTENCY_IN_PROGRESS, ErrorKind.CONFLICT);
        } else {
            replay(response, stored.get());
        }
    }

    private void runAndStore(UserId userId, String key, CachedBodyRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        try {
            chain.doFilter(request, wrapper);
            completed = true;
        } finally {
            if (completed && wrapper.getStatus() < 500) {
                responses.finish(userId, key, wrapper.getStatus(), wrapper.getContentType(),
                        new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8), replayedHeaders(wrapper));
            } else {
                responses.release(userId, key);
            }
            wrapper.copyBodyToResponse();
        }
    }

    private static Map<String, String> replayedHeaders(HttpServletResponse response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : REPLAYED_HEADERS) {
            String value = response.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private static void replay(HttpServletResponse response, IdempotentResponses.Stored stored) throws IOException {
        response.setStatus(stored.responseNumber());
        if (stored.responseName() != null) {
            response.setContentType(stored.responseName());
        }
        stored.responseHeaders().forEach(response::setHeader);
        response.setHeader("Idempotent-Replayed", "true");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(stored.responseText() == null ? "" : stored.responseText());
    }

    private void refuse(HttpServletResponse response, WebError code, ErrorKind kind) throws IOException {
        response.setStatus(kind.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(ProblemDetails.of(code, kind, Map.of())));
    }

    private static String hash(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((request.getMethod() + " " + request.getRequestURI() + "?" + request.getQueryString() + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        byte[] body() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("blocking reads only");
                }

                @Override
                public int read() {
                    return in.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
