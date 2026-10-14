package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.WorkContext;
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
}
