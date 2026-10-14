package com.forcitis.one.platform.approval.web;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/** Every operation of the approval controllers declares what a refusal looks like: application/problem+json with the typed errorCode. */
@Component
class ProblemResponses implements OperationCustomizer {

    private static final Map<String, String> REFUSALS = Map.of(
            "400", "The request is malformed",
            "401", "Nobody is signed in",
            "403", "The caller may not do this",
            "404", "No such task, or the caller may not know it",
            "409", "The task or the approval is not in a state that allows this",
            "412", "The task changed since the caller read it (If-Match)",
            "422", "The request breaks a rule; errorCode says which",
            "428", "If-Match with the ETag of the task as read is required");

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (!handlerMethod.getBeanType().getPackageName().equals(ProblemResponses.class.getPackageName())) {
            return operation;
        }
        Schema<?> problem = new ObjectSchema().addProperty("type", new StringSchema()).addProperty("title", new StringSchema())
                .addProperty("status", new Schema<Integer>().type("integer")).addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema()).addProperty("errorCode", new StringSchema());
        REFUSALS.forEach((status, description) -> {
            if (operation.getResponses().get(status) == null) {
                operation.getResponses().addApiResponse(status, new ApiResponse().description(description)
                        .content(new Content().addMediaType("application/problem+json", new MediaType().schema(problem))));
            }
        });
        return operation;
    }
}
