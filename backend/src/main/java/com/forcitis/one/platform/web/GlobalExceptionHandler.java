package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.VersionMalformedException;
import com.forcitis.one.kernel.VersionMismatchException;
import com.forcitis.one.kernel.VersionRequiredException;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.ExternalSourceBrokenException;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The only error handler. A DomainException carries its ErrorCode and kind; framework request errors become
 * PLATFORM_WEB_REQUEST_INVALID; anything else is a 500 that tells the caller nothing but the correlation id.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final int MAX_CAUSE_DEPTH = 10;

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> refused(DomainException refusal) {
        LOG.info("refused {}: {}", refusal.errorCode().code(), refusal.getMessage());
        return ResponseEntity.status(refusal.kind().status())
                .body(ProblemDetails.of(refusal.errorCode(), refusal.kind(), refusal.extensions()));
    }

    @ExceptionHandler(VersionRequiredException.class)
    ResponseEntity<ProblemDetail> versionRequired(VersionRequiredException failure) {
        return refused(new DomainException(WebError.PLATFORM_WEB_VERSION_REQUIRED, ErrorKind.PRECONDITION_REQUIRED, failure.getMessage()));
    }

    @ExceptionHandler(VersionMismatchException.class)
    ResponseEntity<ProblemDetail> versionMismatch(VersionMismatchException failure) {
        return refused(new DomainException(WebError.PLATFORM_WEB_VERSION_MISMATCH, ErrorKind.PRECONDITION_FAILED, failure.getMessage()));
    }

    @ExceptionHandler(VersionMalformedException.class)
    ResponseEntity<ProblemDetail> versionMalformed(VersionMalformedException failure) {
        return refused(new DomainException(WebError.PLATFORM_WEB_VERSION_MALFORMED, ErrorKind.INVALID, failure.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> invalidRequest(InvalidRequestException failure) {
        return refused(new DomainException(WebError.PLATFORM_WEB_REQUEST_INVALID, ErrorKind.INVALID, failure.getMessage()));
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<ProblemDetail> invalidCursor(InvalidCursorException failure) {
        return refused(new DomainException(WebError.PLATFORM_WEB_CURSOR_INVALID, ErrorKind.INVALID, failure.getMessage()));
    }

    @ExceptionHandler({AccessDeniedException.class})
    ResponseEntity<ProblemDetail> denied(AccessDeniedException failure) {
        return refused(new DomainException(WebError.PLATFORM_WEB_ACCESS_DENIED, ErrorKind.FORBIDDEN, failure.getMessage()));
    }

    @ExceptionHandler({AuthenticationException.class})
    ResponseEntity<ProblemDetail> unauthenticated(AuthenticationException failure) {
        return refused(new DomainException(WebError.PLATFORM_WEB_UNAUTHENTICATED, ErrorKind.UNAUTHENTICATED, failure.getMessage()));
    }

    @ExceptionHandler(ExternalSystemUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(ExternalSystemUnavailableException failure) {
        LOG.warn("external system unavailable: {}", failure.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE,
                WebError.PLATFORM_WEB_SOURCE_UNAVAILABLE, "An external system is unavailable; try again later."));
    }

    @ExceptionHandler(ExternalSourceBrokenException.class)
    ResponseEntity<ProblemDetail> broken(ExternalSourceBrokenException failure) {
        LOG.error("external source broken: {}", failure.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ProblemDetails.of(HttpStatus.BAD_GATEWAY,
                WebError.PLATFORM_WEB_SOURCE_BROKEN, "An external system returned data that cannot be used."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception failure) {
        LOG.error("unexpected failure {} causes={} correlationId={}", failure.getClass().getName(), causes(failure),
                WorkContext.correlationId().map(Object::toString).orElse("none"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR,
                WebError.PLATFORM_WEB_UNEXPECTED, "Unexpected error. Quote the correlation id when reporting it."));
    }

    /** Class names and SQLState only, never a message: messages can carry row values. */
    private static List<String> causes(Throwable failure) {
        List<String> layers = new ArrayList<>();
        Throwable cause = failure.getCause();
        while (cause != null && cause != failure && layers.size() < MAX_CAUSE_DEPTH) {
            String state = sqlState(cause);
            layers.add(state == null ? cause.getClass().getName() : cause.getClass().getName() + "[sqlState=" + state + "]");
            cause = cause.getCause();
        }
        return layers;
    }

    /** Read by name because the web layer may not depend on java.sql (ArchitectureRulesTest). */
    private static String sqlState(Throwable cause) {
        try {
            return cause.getClass().getMethod("getSQLState").invoke(cause) instanceof String state ? state : null;
        } catch (ReflectiveOperationException absent) {
            return null;
        }
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception failure, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail given ? given : ProblemDetail.forStatus(status);
        if (failure instanceof HttpMessageNotReadableException unreadable && unreadable.getCause() instanceof tools.jackson.databind.DatabindException databind) {
            String field = databind.getPath().stream().map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                    .collect(java.util.stream.Collectors.joining(".")).replace(".[", "[");
            if (!field.isEmpty()) {
                problem.setDetail("Failed to read request: field " + field); // property names only, never the value
            }
        }
        problem.setType(java.net.URI.create(ProblemDetails.TYPE_PREFIX + WebError.PLATFORM_WEB_REQUEST_INVALID.code()));
        problem.setProperty("errorCode", WebError.PLATFORM_WEB_REQUEST_INVALID.code());
        com.forcitis.one.kernel.WorkContext.correlationId().ifPresent(id -> problem.setProperty("correlationId", id.toString()));
        return super.handleExceptionInternal(failure, problem, headers, status, request);
    }
}
