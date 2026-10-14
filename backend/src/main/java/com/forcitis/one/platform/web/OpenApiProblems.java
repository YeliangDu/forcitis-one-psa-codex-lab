package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;


/**
 * The contract of every failure: each operation answers application/problem+json described by ProblemDetail, whose errorCode is the
 * closed set of ErrorCode (every constant of every module). The generated client checks both.
 */
@Configuration
class OpenApiProblems {
    static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenApiCustomizer problemContract() {
        List<String> codes = errorCodes();
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new io.swagger.v3.oas.models.Components());
            }
            openApi.getComponents().addSchemas("ErrorCode", new StringSchema()._enum(codes));
            Schema<?> problem = new ObjectSchema()
                    .addProperty("type", new StringSchema())
                    .addProperty("title", new StringSchema())
                    .addProperty("status", new Schema<Integer>().type("integer").format("int32"))
                    .addProperty("detail", new StringSchema())
                    .addProperty("instance", new StringSchema())
                    .addProperty("errorCode", new Schema<String>().$ref("#/components/schemas/ErrorCode"))
                    .addProperty("correlationId", new StringSchema().format("uuid"))
                    .addProperty("legacyLoginUrl", new StringSchema());
            problem.setRequired(List.of("status", "errorCode"));
            openApi.getComponents().addSchemas("ProblemDetail", problem);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                declareProblem(operation);
                if (IdempotencyScope.applies(method.name(), path)) {
                    declareIdempotencyKey(operation);
                }
            }));
        };
    }

    private static void declareProblem(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        Content content = new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail")));
        operation.getResponses().addApiResponse("default", new ApiResponse().description("The request was refused").content(content));
    }

    private static void declareIdempotencyKey(Operation operation) {
        boolean declared = operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(p -> "Idempotency-Key".equals(p.getName()));
        if (!declared) {
            operation.addParametersItem(new HeaderParameter().name("Idempotency-Key").required(false)
                    .description("The same key with the same request within 24 hours gets the first answer again").schema(new StringSchema()));
        }
    }

    static List<String> errorCodes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
        TreeSet<String> codes = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.forcitis.one")) {
            try {
                Class<?> type = Class.forName(candidate.getBeanClassName());
                if (type.isEnum()) {
                    for (Object constant : type.getEnumConstants()) {
                        codes.add(((ErrorCode) constant).code());
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("cannot load " + candidate.getBeanClassName(), e);
            }
        }
        return new ArrayList<>(codes);
    }
}
