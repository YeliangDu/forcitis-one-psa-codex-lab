package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.WorkContext;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

final class ProblemDetails {
    static final String TYPE_PREFIX = "urn:forcitis:error:";

    private ProblemDetails() {
    }

    static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(java.net.URI.create(TYPE_PREFIX + code.code()));
        problem.setProperty("errorCode", code.code());
        WorkContext.correlationId().ifPresent(id -> problem.setProperty("correlationId", id.toString()));
        return problem;
    }

    static ProblemDetail of(ErrorCode code, ErrorKind kind, Map<String, String> extensions) {
        ProblemDetail problem = of(HttpStatusCode.valueOf(kind.status()), code, outwardText(kind));
        extensions.forEach(problem::setProperty);
        return problem;
    }

    /** What the caller reads is fixed per kind of refusal; the internal message goes to the log, never into the response. */
    static String outwardText(ErrorKind kind) {
        return switch (kind) {
            case INVALID -> "The request is not valid.";
            case UNAUTHENTICATED -> "Sign in first.";
            case FORBIDDEN -> "You may not do this.";
            case NOT_FOUND -> "There is no such resource.";
            case TOO_LARGE -> "The request is larger than the limit.";
            case CONFLICT -> "The state of the resource does not allow this.";
            case PRECONDITION_FAILED -> "The resource changed since you read it; read it again.";
            case UNPROCESSABLE -> "The request is understood but cannot be carried out.";
            case PRECONDITION_REQUIRED -> "The request must say which version it is based on.";
            case TOO_MANY_REQUESTS -> "Too many requests; try again later.";
            case UNAVAILABLE -> "The service is not available now; try again later.";
        };
    }
}
