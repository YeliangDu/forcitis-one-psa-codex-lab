package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R-1b P3b, ADR-0033 guard 3 (card section 4 item 4; RULING-P3B-D12): source ids and the source snapshot types live in platform.integration.
 * Written here as a black-box scan of the main sources, so it stands whatever the guard tests outside the acceptance directory become.
 * <ul>
 *   <li>{@link Rule#SOURCE_TYPE}: outside platform.integration, no file of a domain, application, api, events or web package names a
 *       Source* type of platform.integration.api or the workforce SourceTerms (kernel's SourceSystem was not one, and P5 removed it). The files
 *       that still do are listed in {@link #EXCEPTIONS}, one line each with the reason and where it goes ("拆分 a"); the list only shrinks and a
 *       line whose file no longer needs it fails.</li>
 *   <li>{@link Rule#SOURCE_OR_LEGACY_ID}: outside platform.integration no type is named Source*Id or Legacy*Id. No exceptions.</li>
 * </ul>
 */
class SourceTypeBoundaryTest {

    static final Path EXCEPTIONS = Path.of("architecture/source-type-exceptions.txt");
    static final String DESTINATION = "拆分 a";
    static final String ONLY_SHRINKS = "只减不增";

    static final String JAVA = "src/main/java/com/forcitis/one/";
    static final String INTEGRATION = JAVA + "platform/integration/";
    static final String INTEGRATION_API = INTEGRATION + "api/";
    static final String SOURCE_TERMS = "SourceTerms";
    static final Set<String> LAYERS = Set.of("domain", "application", "api", "events", "web");

    enum Rule {
        SOURCE_TYPE,
        SOURCE_OR_LEGACY_ID
    }

    record Hit(String file, Rule rule, String name) {
        @Override
        public String toString() {
            return file + " [" + rule + "] " + name;
        }
    }

    record Allowed(String file, String reason, String destination) {
    }

    static final Pattern SOURCE_NAME = Pattern.compile("(?<![A-Za-z0-9_$])Source[A-Z][A-Za-z0-9_]*");
    static final Pattern DECLARATION = Pattern.compile("\\b(?:class|record|interface|enum)\\s+([A-Z][A-Za-z0-9_]*)");
    static final Pattern FORBIDDEN_ID = Pattern.compile("^(?:Source|Legacy)[A-Za-z0-9_]*Id$");
    static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    static List<Path> javaFiles(Path project) {
        Path root = project.resolve(JAVA);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(f -> f.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The Source* types of platform.integration.api, read from the tree itself, and the workforce SourceTerms. */
    static Set<String> integrationSourceTypes(Path project) {
        Set<String> names = new TreeSet<>();
        names.add(SOURCE_TERMS);
        for (Path file : javaFiles(project)) {
            String relative = relative(project, file);
            if (relative.startsWith(INTEGRATION_API)) {
                Matcher declared = DECLARATION.matcher(code(read(file)));
                while (declared.find()) {
                    if (declared.group(1).startsWith("Source")) {
                        names.add(declared.group(1));
                    }
                }
            }
        }
        return names;
    }

    static List<Hit> scan(Path project) {
        Set<String> sourceTypes = integrationSourceTypes(project);
        List<Hit> hits = new ArrayList<>();
        for (Path file : javaFiles(project)) {
            String relative = relative(project, file);
            if (relative.startsWith(INTEGRATION)) {
                continue;
            }
            String code = code(read(file));
            Matcher declared = DECLARATION.matcher(code);
            while (declared.find()) {
                if (FORBIDDEN_ID.matcher(declared.group(1)).matches()) {
                    hits.add(new Hit(relative, Rule.SOURCE_OR_LEGACY_ID, declared.group(1)));
                }
            }
            if (!inLayer(code)) {
                continue;
            }
            Set<String> named = new TreeSet<>();
            Matcher reference = SOURCE_NAME.matcher(code);
            while (reference.find()) {
                if (sourceTypes.contains(reference.group())) {
                    named.add(reference.group());
                }
            }
            if (!named.isEmpty()) {
                hits.add(new Hit(relative, Rule.SOURCE_TYPE, String.join(",", named)));
            }
        }
        return hits;
    }

    private static boolean inLayer(String code) {
        Matcher pkg = PACKAGE.matcher(code);
        return pkg.find() && Stream.of(pkg.group(1).split("\\.")).anyMatch(LAYERS::contains);
    }

    /** Java without comments, string and character literals, so that only what the code names is matched. */
    static String code(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (text.startsWith("/*", i)) {
                int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
                out.append(' ');
            } else if (text.startsWith("//", i)) {
                int end = text.indexOf('\n', i);
                i = end < 0 ? text.length() : end;
            } else if (text.startsWith("\"\"\"", i)) {
                int end = text.indexOf("\"\"\"", i + 3);
                i = end < 0 ? text.length() : end + 3;
                out.append("\"\"");
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) != c && text.charAt(j) != '\n') {
                    j += text.charAt(j) == '\\' ? 2 : 1;
                }
                i = Math.min(j + 1, text.length());
                out.append(c).append(c);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    static List<Allowed> exceptions(Path file) {
        List<Allowed> list = new ArrayList<>();
        for (String line : readLines(file)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            assertThat(parts).as("path | reason | destination: %s", line).hasSize(3);
            list.add(new Allowed(parts[0].trim(), parts[1].trim(), parts[2].trim()));
        }
        return list;
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 3（SOURCE_TYPE）：platform.integration 以外的 domain、application、api、events、web 包不引用集成层的 Source* 类型与 SourceTerms，例外清单之外无一处")
    void noSourceTypeOutsideIntegrationBeyondTheList() {
        List<Allowed> listed = exceptions(EXCEPTIONS);
        Set<String> allowed = listed.stream().map(Allowed::file).collect(Collectors.toSet());

        List<Hit> violations = scan(Path.of(".")).stream().filter(h -> h.rule() == Rule.SOURCE_TYPE && !allowed.contains(h.file())).toList();

        assertThat(violations).as("files that name a source type outside platform.integration (move the translation into platform.integration; a file of "
                + "the split-a backlog is listed in %s):\n  %s", EXCEPTIONS, violations.stream().map(Hit::toString).collect(Collectors.joining("\n  "))).isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 3（SOURCE_OR_LEGACY_ID）：platform.integration 以外没有名为 Source*Id、Legacy*Id 的类型，无例外清单")
    void noSourceOrLegacyIdOutsideIntegration() {
        List<Hit> violations = scan(Path.of(".")).stream().filter(h -> h.rule() == Rule.SOURCE_OR_LEGACY_ID).toList();

        assertThat(violations).as("Source*Id / Legacy*Id types outside platform.integration (an external id is translated in platform.integration and never "
                + "leaves it):\n  %s", violations.stream().map(Hit::toString).collect(Collectors.joining("\n  "))).isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 守护 3：例外清单 source-type-exceptions.txt 存在，文件头写明只减不增，每行一个文件、写理由与去向「拆分 a」，没有过期行")
    void theExceptionListIsWellFormedAndNoLineIsStale() {
        assertThat(Files.isRegularFile(EXCEPTIONS)).as("%s exists", EXCEPTIONS).isTrue();
        String header = readLines(EXCEPTIONS).stream().filter(line -> line.startsWith("#")).collect(Collectors.joining("\n"));
        assertThat(header).as("the header says the list only shrinks").contains(ONLY_SHRINKS);
        List<Allowed> listed = exceptions(EXCEPTIONS);
        Map<String, Set<Rule>> hit = new LinkedHashMap<>();
        scan(Path.of(".")).forEach(h -> hit.computeIfAbsent(h.file(), k -> new TreeSet<>()).add(h.rule()));

        for (Allowed line : listed) {
            assertThat(Files.isRegularFile(Path.of(line.file()))).as("%s exists", line.file()).isTrue();
            assertThat(line.file()).as("an exception is never for platform.integration").doesNotStartWith(INTEGRATION);
            assertThat(line.reason()).as("reason of %s", line.file()).isNotBlank();
            assertThat(line.destination()).as("destination of %s", line.file()).isEqualTo(DESTINATION);
            assertThat(hit.getOrDefault(line.file(), Set.of())).as("%s still needs its exception (a stale line is deleted)", line.file()).contains(Rule.SOURCE_TYPE);
        }
        assertThat(listed.stream().map(Allowed::file).distinct().count()).as("one line per file").isEqualTo(listed.size());
    }

    static final String SAMPLE_API = INTEGRATION_API + "SourcePerson.java";
    static final String SAMPLE_API_TEXT = "package com.forcitis.one.platform.integration.api;\npublic record SourcePerson(String name) { }\n";

    @Test
    @DisplayName("R-1b P3b CS-5.9 注入：工时 domain 引用 SourcePerson、workforce application 引用 SourceTerms、审批 domain 声明 LegacyInstanceId、工时 api 声明 SourceUnitId，都被拦下")
    void injectedViolationsAreCaught(@TempDir Path project) throws IOException {
        write(project, SAMPLE_API, SAMPLE_API_TEXT);
        write(project, JAVA + "organization/workforce/domain/SourceTerms.java",
                "package com.forcitis.one.organization.workforce.domain;\npublic final class SourceTerms { }\n");
        write(project, JAVA + "cost/timesheet/domain/Sheet.java",
                "package com.forcitis.one.cost.timesheet.domain;\nimport com.forcitis.one.platform.integration.api.SourcePerson;\nclass Sheet { SourcePerson p; }\n");
        write(project, JAVA + "organization/workforce/application/Apply.java",
                "package com.forcitis.one.organization.workforce.application;\nclass Apply { Object t = SourceTerms.class; }\n");
        write(project, JAVA + "platform/approval/domain/LegacyInstanceId.java",
                "package com.forcitis.one.platform.approval.domain;\npublic record LegacyInstanceId(String value) { }\n");
        write(project, JAVA + "cost/timesheet/api/SourceUnitId.java",
                "package com.forcitis.one.cost.timesheet.api;\npublic record SourceUnitId(long value) { }\n");

        List<Hit> hits = scan(project);

        assertThat(hits).extracting(Hit::file, Hit::rule).contains(
                org.assertj.core.groups.Tuple.tuple(JAVA + "cost/timesheet/domain/Sheet.java", Rule.SOURCE_TYPE),
                org.assertj.core.groups.Tuple.tuple(JAVA + "organization/workforce/application/Apply.java", Rule.SOURCE_TYPE),
                org.assertj.core.groups.Tuple.tuple(JAVA + "platform/approval/domain/LegacyInstanceId.java", Rule.SOURCE_OR_LEGACY_ID),
                org.assertj.core.groups.Tuple.tuple(JAVA + "cost/timesheet/api/SourceUnitId.java", Rule.SOURCE_OR_LEGACY_ID));
    }

    @Test
    @DisplayName("R-1b P3b CS-5.9 反例：集成层自己的 Source*、Legacy*Id，kernel 的 SourceSystem，基础设施包里的引用，注释与字符串里的名字，都不算违规")
    void counterExamplesAreLeftAlone(@TempDir Path project) throws IOException {
        write(project, SAMPLE_API, SAMPLE_API_TEXT);
        write(project, INTEGRATION + "infrastructure/legacy/LegacyInstanceId.java",
                "package com.forcitis.one.platform.integration.infrastructure.legacy;\nrecord LegacyInstanceId(String value) { }\n");
        write(project, INTEGRATION + "api/SourceUnitId.java", "package com.forcitis.one.platform.integration.api;\npublic record SourceUnitId(long value) { }\n");
        write(project, JAVA + "kernel/event/SourceSystem.java", "package com.forcitis.one.kernel.event;\npublic enum SourceSystem { ONE }\n");
        write(project, JAVA + "cost/timesheet/domain/Sheet.java", "package com.forcitis.one.cost.timesheet.domain;\n"
                + "import com.forcitis.one.kernel.event.SourceSystem;\n// a SourcePerson in a comment\nclass Sheet { SourceSystem s; String t = \"SourcePerson\"; }\n");
        write(project, JAVA + "organization/workforce/infrastructure/external/Adapter.java",
                "package com.forcitis.one.organization.workforce.infrastructure.external;\n"
                        + "import com.forcitis.one.platform.integration.api.SourcePerson;\nclass Adapter { SourcePerson p; }\n");

        assertThat(scan(project)).isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b CS-5.9：例外只放过 SOURCE_TYPE 且只放过所列文件；SOURCE_OR_LEGACY_ID 不受例外清单影响")
    void anExceptionCoversOneFileAndOnlyTheSourceTypeRule(@TempDir Path project) throws IOException {
        write(project, SAMPLE_API, SAMPLE_API_TEXT);
        String listedFile = JAVA + "organization/workforce/domain/Listed.java";
        String otherFile = JAVA + "organization/workforce/domain/Other.java";
        write(project, listedFile, "package com.forcitis.one.organization.workforce.domain;\nrecord LegacyThingId(String v) { SourcePerson p() { return null; } }\n");
        write(project, otherFile, "package com.forcitis.one.organization.workforce.domain;\nclass Other { SourcePerson p; }\n");
        Path list = project.resolve("exceptions.txt");
        Files.writeString(list, "# " + ONLY_SHRINKS + "\n" + listedFile + " | test | " + DESTINATION + "\n");
        Set<String> allowed = exceptions(list).stream().map(Allowed::file).collect(Collectors.toSet());

        List<Hit> left = scan(project).stream().filter(h -> h.rule() == Rule.SOURCE_OR_LEGACY_ID || !allowed.contains(h.file())).toList();

        assertThat(left).extracting(Hit::file, Hit::rule).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(listedFile, Rule.SOURCE_OR_LEGACY_ID),
                org.assertj.core.groups.Tuple.tuple(otherFile, Rule.SOURCE_TYPE));
    }

    private static void write(Path project, String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String relative(Path project, Path file) {
        return project.relativize(file).toString().replace('\\', '/');
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> readLines(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
