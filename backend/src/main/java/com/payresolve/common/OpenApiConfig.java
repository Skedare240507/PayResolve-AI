package com.payresolve.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SpringDoc OpenAPI specification configuration providing metadata, servers,
 * session/CSRF security schemes, and consistent RFC 9457 error responses across endpoints.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "PayResolve AI API",
        version = "1.0.0",
        description = "REST API for PayResolve AI — a PayPal Sandbox payment exception investigator that reconciles shop orders, PayPal captures, refunds, shipments, and webhooks into an evidence graph with read-only AI investigation and human-approved actions.",
        contact = @Contact(
            name = "PayResolve Team",
            url = "https://github.com/Skedare240507/PayResolve-AI"
        ),
        license = @License(
            name = "MIT",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(
            url = "https://payresolve-ai.onrender.com",
            description = "Production deployment on Render (static UI + API reverse proxy)"
        ),
        @Server(
            url = "http://localhost:8080",
            description = "Local development Spring Boot server"
        )
    }
)
@SecurityScheme(
    name = "cookieAuth",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.COOKIE,
    paramName = "JSESSIONID",
    description = "HTTP-only session cookie established via POST /api/auth/login."
)
@SecurityScheme(
    name = "csrfToken",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-XSRF-TOKEN",
    description = "CSRF protection token obtained from GET /api/auth/csrf for state-mutating operator actions."
)
public class OpenApiConfig {

    private static final String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/signup",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/csrf",
        "/api/webhooks/paypal"
    );

    private static final Set<String> OPERATOR_MUTATION_PATHS = Set.of(
        "/api/actions/{id}/approve",
        "/api/actions/{id}/reject"
    );

    @Bean
    public OpenApiCustomizer payResolveOpenApiCustomizer() {
        return openApi -> {
            ensureProblemDetailSchema(openApi);
            normalizeOpenMapSchemas(openApi);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    applySecurity(path, operation);
                    normalizeMediaTypes(operation);
                    ensureErrorResponses(path, operation);
                })
            );
        };
    }

    private void normalizeOpenMapSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        openApi.getComponents().getSchemas().values().forEach(this::fixAdditionalPropertiesRecursively);
    }

    private void fixAdditionalPropertiesRecursively(Schema<?> schema) {
        if (schema == null) {
            return;
        }
        Object addProps = schema.getAdditionalProperties();
        if (addProps instanceof Schema<?> addSchema
            && "object".equals(addSchema.getType())
            && (addSchema.getProperties() == null || addSchema.getProperties().isEmpty())
            && addSchema.get$ref() == null) {
            schema.setAdditionalProperties(Boolean.TRUE);
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(this::fixAdditionalPropertiesRecursively);
        }
        if (schema.getItems() != null) {
            fixAdditionalPropertiesRecursively(schema.getItems());
        }
    }

    private void ensureProblemDetailSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        if (schemas == null) {
            schemas = new LinkedHashMap<>();
            openApi.getComponents().setSchemas(schemas);
        }
        if (!schemas.containsKey("ProblemDetail")) {
            ObjectSchema problemDetail = new ObjectSchema();
            problemDetail.setDescription("RFC 9457 Problem Details error payload returned for failed API requests.");
            problemDetail.addProperty("type", new StringSchema().format("uri").description("URI identifying the problem type."));
            problemDetail.addProperty("title", new StringSchema().description("Short human-readable summary of the problem type."));
            problemDetail.addProperty("status", new IntegerSchema().format("int32").description("HTTP status code."));
            problemDetail.addProperty("detail", new StringSchema().description("Human-readable explanation specific to this occurrence."));
            problemDetail.addProperty("instance", new StringSchema().format("uri").description("URI reference identifying the specific occurrence."));
            problemDetail.addProperty("timestamp", new StringSchema().format("date-time").description("UTC timestamp when the error occurred."));
            schemas.put("ProblemDetail", problemDetail);
        }
    }

    private void applySecurity(String path, Operation operation) {
        if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
            return;
        }
        if (PUBLIC_PATHS.contains(path)) {
            operation.setSecurity(List.of(new SecurityRequirement()));
            return;
        }
        SecurityRequirement req = new SecurityRequirement().addList("cookieAuth");
        if (OPERATOR_MUTATION_PATHS.contains(path)) {
            req.addList("csrfToken");
        }
        operation.setSecurity(List.of(req));
    }

    private void normalizeMediaTypes(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return;
        }
        responses.forEach((code, response) -> {
            if ("204".equals(code)) {
                response.setContent(null);
                return;
            }
            Content content = response.getContent();
            if (content != null && content.containsKey("*/*") && !content.containsKey("application/json")) {
                MediaType wildcard = content.remove("*/*");
                content.addMediaType("application/json", wildcard);
            }
        });
    }

    private void ensureErrorResponses(String path, Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        addProblemResponseIfAbsent(responses, "400", "Invalid request parameters or payload");
        if (path.contains("{id}") || path.contains("{caseId}") || path.contains("{attachmentId}")) {
            addProblemResponseIfAbsent(responses, "404", "Requested resource was not found");
        }
        if (OPERATOR_MUTATION_PATHS.contains(path)) {
            addProblemResponseIfAbsent(responses, "401", "Authentication required");
            addProblemResponseIfAbsent(responses, "403", "Operator role required for this action");
        }
        addProblemResponseIfAbsent(responses, "500", "Unexpected internal server error");
    }

    private void addProblemResponseIfAbsent(ApiResponses responses, String code, String description) {
        if (responses.containsKey(code)) {
            return;
        }
        Schema<?> refSchema = new Schema<>().$ref(PROBLEM_DETAIL_REF);
        Content content = new Content().addMediaType("application/json", new MediaType().schema(refSchema));
        responses.addApiResponse(code, new ApiResponse().description(description).content(content));
    }
}
