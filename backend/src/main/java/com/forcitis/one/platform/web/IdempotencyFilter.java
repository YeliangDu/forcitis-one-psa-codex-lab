package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdempotentResponses;
import com.forcitis.one.kernel.UserId;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A write that carries Idempotency-Key gets the first answer again when it is sent again with the same key and the same request
 * within 24 hours; the same key with another request is refused (422). Runs after the security filters, so the key is the signed-in
 * user's own. The request body is read once into a SpooledBody, a temporary file when it is large (a file upload), and is limited in size. An answer longer than the stored limit is not stored and the key stays "in progress" until it expires: the write has been
 * done, and running it again on a retry would do it twice.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
class IdempotencyFilter extends OncePerRequestFilter {
    static final String HEADER = "Idempotency-Key";
    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyFilter.class);
    static final Duration LIFETIME = Duration.ofHours(24);
    private static final int MAX_KEY_LENGTH = 128;
    private static final List<String> REPLAYED_HEADERS = List.of("ETag", "Location");

    private final IdempotentResponses responses;
    private final Clock clock;
    private final JsonMapper json;
    private final Duration inProgressTimeout;
    private final IdempotencyScope scope;
    private final MeterRegistry meters;
    private final long maxBody;

    IdempotencyFilter(IdempotentResponses responses, Clock clock, JsonMapper json, IdempotencyScope scope, MeterRegistry meters,
            @Value("${forcitis.platform.web.idempotency.in-progress-timeout}") Duration inProgressTimeout,
            @Value("${forcitis.platform.web.idempotency.max-body-size}") DataSize maxBodySize) {
        if (maxBodySize.toBytes() < 1) {
            throw new IllegalArgumentException("forcitis.platform.web.idempotency.max-body-size must be positive");
        }
        this.maxBody = maxBodySize.toBytes();
        if (inProgressTimeout.isNegative() || inProgressTimeout.isZero()) {
            throw new IllegalArgumentException("forcitis.platform.web.idempotency.in-progress-timeout must be positive");
        }
        this.scope = scope;
        this.meters = meters;
        this.responses = responses;
        this.clock = clock;
        this.json = json;
        this.inProgressTimeout = inProgressTimeout;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(HEADER) == null || !scope.applies(request.getMethod(), request.getRequestURI());
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
        if (request.getContentLengthLong() > maxBody) {
            refuse(response, WebError.PLATFORM_WEB_REQUEST_INVALID, ErrorKind.INVALID);
            return;
        }
        SpooledBody body;
        try {
            body = SpooledBody.read(request.getInputStream(), prefix(request), maxBody);
        } catch (SpooledBody.TooLarge tooLarge) {
            refuse(response, WebError.PLATFORM_WEB_REQUEST_INVALID, ErrorKind.INVALID);
            return;
        }
        try (body) {
            claimAndRun(request, response, chain, userId, key, body);
        }
    }

    private void claimAndRun(HttpServletRequest request, HttpServletResponse response, FilterChain chain, UserId userId, String key, SpooledBody body)
            throws ServletException, IOException {
        CachedBodyRequest cached = new CachedBodyRequest(request, body);
        String requestHash = body.hash();
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
                String text = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
                if (text.length() > IdempotentResponses.MAX_RESPONSE_LENGTH) {
                    LOG.error("ALERT platform.idempotency response of {} exceeds {} characters: not stored", request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),
                            IdempotentResponses.MAX_RESPONSE_LENGTH);
                    meters.counter("forcitis.platform.web.idempotency.response.oversize").increment();
                    responses.holdUntilExpiry(userId, key);
                } else {
                    responses.finish(userId, key, wrapper.getStatus(), wrapper.getContentType(), text, replayedHeaders(wrapper));
                }
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

    private static String prefix(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI() + "?" + request.getQueryString() + "\n";
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final SpooledBody body;

        CachedBodyRequest(HttpServletRequest request, SpooledBody body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            InputStream in = body.open();
            return new ServletInputStream() {
                private boolean finished;

                @Override
                public boolean isFinished() {
                    return finished;
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
                public int read() throws IOException {
                    int next = in.read();
                    finished = next == -1;
                    return next;
                }

                @Override
                public int read(byte[] target, int offset, int length) throws IOException {
                    int count = in.read(target, offset, length);
                    finished = count == -1;
                    return count;
                }

                @Override
                public void close() throws IOException {
                    in.close();
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
