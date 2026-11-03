package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
        Set<String> handlers = DataScopeReadPaths.getHandlers(mapProbe());

        assertThat(handlers).containsExactlyInAnyOrder("GET /api/v1/probe/rows", "GET /api/v1/probe/rows/{id}", "GET /api/v1/probe/any");
        assertThat(DataScopeReadPaths.problems(handlers, List.of())).hasSize(3).allMatch(text -> text.contains("not in the register"));
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /api/v1/probe/rows | SCOPED | ", "GET /api/v1/probe/rows/{id} | SCOPED |")))
                .as("only the one left out").singleElement().asString().contains("GET /api/v1/probe/any");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /api/v1/probe/rows | SCOPED |", "GET /api/v1/probe/rows/{id} | SELF |",
                "GET /api/v1/probe/any | NOT_SCOPE_BOUND | a public lookup table"))).as("control: all listed").isEmpty();
    }

    @Test
    @DisplayName("#730 G1: a register line that matches no handler is reported, as are a duplicate, an unknown mode, a non-GET method and a NOT_SCOPE_BOUND line without a reason")
    void badRegisterLinesAreCaught() {
        Set<String> handlers = Set.of("GET /a");

        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | SCOPED |", "GET /gone | SELF |"))).singleElement().asString().contains("GET /gone");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | SCOPED |", "GET /a | SELF |"))).singleElement().asString().contains("two lines");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | MAYBE |"))).hasSize(2).anyMatch(text -> text.contains("not `GET <path>"));
        assertThat(DataScopeReadPaths.problems(handlers, List.of("POST /a | SCOPED |", "GET /a | SCOPED |"))).singleElement().asString().contains("not `GET <path>");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | NOT_SCOPE_BOUND |   "))).singleElement().asString().contains("without a reason");
        assertThat(DataScopeReadPaths.problems(handlers, List.of("GET /a | NOT_SCOPE_BOUND | numbering rules", "# a comment", ""))).isEmpty();
    }

    @Test
    @DisplayName("#730 G3: the register file itself has well-formed lines with a reason on every NOT_SCOPE_BOUND one")
    void theRegisterFileIsWellFormed() throws IOException {
        List<String> problems = new java.util.ArrayList<>();
        assertThat(DataScopeReadPaths.parse(Files.readAllLines(REGISTER), problems)).isNotEmpty();
        assertThat(problems).isEmpty();
    }
}
