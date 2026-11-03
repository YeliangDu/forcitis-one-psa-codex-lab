package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** #730 G1/G2/G3 without a database: the register file is well formed, and the comparison catches an unlisted handler, a dangling line and a NOT_SCOPE_BOUND line without a reason. */
class DataScopeReadPathsGuardTest {
    static final Path REGISTER = Path.of("architecture/data-scope-read-paths.txt");

    @RestController
    @RequestMapping("/api/v1/probe")
    static class ProbeController {
        @GetMapping("/rows")
        List<String> rows() {
            return List.of();
        }

        @GetMapping("/rows/{id}")
        String row() {
            return "";
        }

        @RequestMapping("/any")
        String any() {
            return "";
        }

        @PostMapping("/write")
        void write() {
        }
    }

    @RestController
    @RequestMapping("/api/v1/variant")
    static class VariantController {
        @GetMapping("/rows")
        List<String> rows() {
            return List.of();
        }

        @GetMapping(path = "/rows", params = "all")
        List<String> everyRow() {
            return List.of();
        }

        @GetMapping(path = "/rows", produces = "text/csv")
        String csv() {
            return "";
        }

        @RequestMapping(path = "/head", method = RequestMethod.HEAD)
        void head() {
        }
    }

    private static RequestMappingHandlerMapping mapProbe() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ProbeController.class);
        RequestMappingHandlerMapping mappings = new RequestMappingHandlerMapping();
        mappings.setApplicationContext(context);
        mappings.afterPropertiesSet();
        return mappings;
    }

    @Test
    @DisplayName("#730 G2: a test-only controller's GET handlers are enumerated (a mapping without a method too), its POST is not, and left unlisted they are reported")
    void aTestOnlyControllerLeftUnlistedIsCaught() {
        List<String> handlers = DataScopeReadPaths.getHandlers(List.of(mapProbe()), new ArrayList<>());

        assertThat(handlers).containsExactlyInAnyOrder("GET /api/v1/probe/rows", "GET /api/v1/probe/rows/{id}", "GET /api/v1/probe/any");
        assertThat(DataScopeReadPaths.problems(handlers, List.of())).hasSize(3).allMatch(text -> text.contains("not in the register"));
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /api/v1/probe/rows | SCOPED | ", "GET /api/v1/probe/rows/{id} | SCOPED |")))
                .as("only the one left out").singleElement().asString().contains("GET /api/v1/probe/any");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /api/v1/probe/rows | SCOPED |", "GET /api/v1/probe/rows/{id} | SELF |",
                "GET /api/v1/probe/any | NOT_SCOPE_BOUND | a public lookup table"))).as("control: all listed").isEmpty();
    }

    @Test
    @DisplayName("#730 G1: two GET handlers on one path (params, produces) are reported, not folded into one register line; a HEAD-only mapping never matches a line")
    void handlersSharingAPathAreCaught() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(VariantController.class);
        RequestMappingHandlerMapping mappings = new RequestMappingHandlerMapping();
        mappings.setApplicationContext(context);
        mappings.afterPropertiesSet();

        List<String> handlers = DataScopeReadPaths.getHandlers(List.of(mappings), new ArrayList<>());

        assertThat(handlers).containsExactlyInAnyOrder("GET /api/v1/variant/rows", "GET /api/v1/variant/rows", "GET /api/v1/variant/rows", "HEAD /api/v1/variant/head");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /api/v1/variant/rows | SCOPED |"))).hasSize(2)
                .anyMatch(text -> text.contains("served by 3 handlers")).anyMatch(text -> text.contains("HEAD /api/v1/variant/head"));
    }

    @RestController
    static class RowsOnError {
        @GetMapping(path = "/error", params = "rows")
        List<String> rows() {
            return List.of();
        }
    }

    @Configuration
    static class FrameworkErrorController {
        @Bean
        BasicErrorController basicErrorController() {
            return new BasicErrorController(new DefaultErrorAttributes(), new ErrorProperties());
        }
    }

    private static List<String> handlersOf(Class<?>... controllers) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(controllers);
        RequestMappingHandlerMapping mappings = new RequestMappingHandlerMapping();
        mappings.setEmbeddedValueResolver(context.getEnvironment()::resolvePlaceholders);
        mappings.setApplicationContext(context);
        mappings.afterPropertiesSet();
        return DataScopeReadPaths.getHandlers(List.of(mappings), new ArrayList<>());
    }

    @Test
    @DisplayName("#730 G1: the framework's pair of /error handlers is one key, but a third handler on /error is reported")
    void aThirdHandlerOnErrorIsCaught() {
        List<String> register = List.of("GET /error | NOT_SCOPE_BOUND | the failed request's status");

        assertThat(DataScopeReadPaths.problems(handlersOf(FrameworkErrorController.class), register)).isEmpty();
        assertThat(DataScopeReadPaths.problems(handlersOf(FrameworkErrorController.class, RowsOnError.class), register)).singleElement().asString().contains("served by 2 handlers");
    }

    @Test
    @DisplayName("#730 G1: a RouterFunction in the context is reported, and a url mapping's patterns are enumerated")
    void routerFunctionsAndUrlMappingsAreCaught() {
        RouterFunctionMapping router = new RouterFunctionMapping(RouterFunctions.route().GET("/fn", request -> ServerResponse.ok().build()).build());
        List<String> problems = new ArrayList<>();
        SimpleUrlHandlerMapping urls = new SimpleUrlHandlerMapping(Map.of("/static/**", new Object()));
        urls.setApplicationContext(new StaticApplicationContext());

        assertThat(DataScopeReadPaths.getHandlers(List.of(router, new RouterFunctionMapping()), problems)).isEmpty();
        assertThat(problems).singleElement().asString().contains("RouterFunction");
        assertThat(DataScopeReadPaths.getHandlers(List.of(urls), problems)).containsExactly("GET /static/**");
        problems.clear();
        AbstractHandlerMapping custom = new AbstractHandlerMapping() {
            @Override
            protected @Nullable Object getHandlerInternal(HttpServletRequest request) {
                return null;
            }
        };
        assertThat(DataScopeReadPaths.getHandlers(List.of(custom), problems)).isEmpty();
        assertThat(problems).singleElement().asString().contains("cannot enumerate");
        problems.clear();
        SimpleUrlHandlerMapping withRoot = new SimpleUrlHandlerMapping(Map.of());
        withRoot.setRootHandler(new Object());
        withRoot.setDefaultHandler(new Object());
        assertThat(DataScopeReadPaths.getHandlers(List.of(withRoot), problems)).containsExactly("GET /", "GET <default handler of SimpleUrlHandlerMapping>");
    }

    @Test
    @DisplayName("#730 G1: a register line that matches no handler is reported, as are a duplicate, an unknown mode, a non-GET method and a NOT_SCOPE_BOUND line without a reason")
    void badRegisterLinesAreCaught() {
        List<String> handlers = List.of("GET /a");

        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | SCOPED |", "GET /gone | SELF |"))).singleElement().asString().contains("GET /gone");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | SCOPED |", "GET /a | SELF |"))).singleElement().asString().contains("two lines");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | MAYBE |"))).hasSize(2).anyMatch(text -> text.contains("not `GET <path>"));
        assertThat(DataScopeReadPaths.problems(handlers, List.of("POST /a | SCOPED |", "GET /a | SCOPED |"))).singleElement().asString().contains("not `GET <path>");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | SCOPED | x | y", "GET /a SCOPED"))).as("a line with a missing or extra column").filteredOn(text -> text.contains("not `GET <path>")).hasSize(2);
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a b | SCOPED |", "GET a | SCOPED |"))).as("a key that is not one path starting with /").filteredOn(text -> text.contains("not `GET <path>")).hasSize(2);
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | NOT_SCOPE_BOUND |   "))).singleElement().asString().contains("without a reason");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | NOT_SCOPE_BOUND | numbering rules", "# a comment", ""))).isEmpty();
    }

    @Test
    @DisplayName("#730 G3: the register file itself has well-formed lines with a reason on every NOT_SCOPE_BOUND one")
    void theRegisterFileIsWellFormed() throws IOException {
        List<String> problems = new ArrayList<>();
        assertThat(DataScopeReadPaths.parse(Files.readAllLines(REGISTER), problems)).isNotEmpty();
        assertThat(problems).isEmpty();
    }
}
