package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0066 decision 4: platform code and tables do not enumerate business documents; the registry is the only place that lists them. */
class DocumentTypeRegistryGuardTest {

    static final Path MAIN = Path.of("src/main/java/com/forcitis/one");
    static final Path PROCESSES = Path.of("src/main/resources/processes");
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    static final Pattern BUSINESS_DOCUMENT_LITERAL = Pattern.compile("\\b(TIMESHEET|QUOTE|JOURNAL_VOUCHER)\\b");
    /** The audit subject kinds still list TIMESHEET until the registry table replaces the enum (REG-3, ADR-0069 decision 14); the replay report title names the one process it replays. */
    static final List<String> PENDING_OR_TECHNICAL = List.of("platform/audit/api/AuditSubjectKind.java", "platform/approval/infrastructure/replay/ReplayReport.java");

    @Test
    @DisplayName("kernel and platform code hold no business document type literal")
    void platformCodeNamesNoBusinessDocument() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String area : List.of("kernel", "platform")) {
            try (Stream<Path> files = Files.walk(MAIN.resolve(area))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String relative = MAIN.relativize(file).toString();
                    if (PENDING_OR_TECHNICAL.contains(relative)) {
                        continue;
                    }
                    Matcher m = BUSINESS_DOCUMENT_LITERAL.matcher(Files.readString(file));
                    if (m.find()) {
                        problems.add(relative + " names the business document " + m.group(1) + "; read it from the numbering registry or take the code from the declaring context");
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("no table lists document types in a CHECK, and only platform_numbering points a foreign key at the registry")
    void tablesDoNotEnumerateDocumentTypes() throws IOException {
        List<String> problems = new ArrayList<>();
        Pattern enumerated = Pattern.compile("document_type\\w*\\s+text[^,;]*CHECK\\s*\\(\\s*document_type\\w*\\s+IN\\s*\\(|CHECK\\s*\\(\\s*document_type\\w*\\s+IN\\s*\\(", Pattern.CASE_INSENSITIVE);
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(file);
                if (enumerated.matcher(sql).find()) {
                    problems.add(file.getFileName() + " enumerates document types in a CHECK");
                }
                boolean pointsAtRegistry = sql.contains("REFERENCES platform_numbering.document_types");
                if (pointsAtRegistry && !file.toString().contains("/platform_numbering/")) {
                    problems.add(file.getFileName() + " has a foreign key across schemas to platform_numbering.document_types");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("kernel and platform code hold no business process key and no business process name (REG-4, ADR-0066 decision 4)")
    void platformCodeNamesNoBusinessProcess() throws IOException, javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException {
        List<String> literals = new ArrayList<>();
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (Stream<Path> definitions = Files.walk(PROCESSES)) {
            for (Path file : definitions.filter(f -> f.toString().endsWith(".bpmn20.xml")).toList()) {
                org.w3c.dom.NodeList processes = factory.newDocumentBuilder().parse(file.toFile()).getElementsByTagNameNS("*", "process");
                for (int i = 0; i < processes.getLength(); i++) {
                    org.w3c.dom.Element process = (org.w3c.dom.Element) processes.item(i);
                    literals.add(process.getAttribute("id"));
                    literals.add(process.getAttribute("name"));
                }
            }
        }
        assertThat(literals).as("the guard reads at least one business process").isNotEmpty().doesNotContain("");
        List<String> problems = new ArrayList<>();
        for (String area : List.of("kernel", "platform")) {
            try (Stream<Path> files = Files.walk(MAIN.resolve(area))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file);
                    for (String literal : processLiteralsIn(text, literals)) {
                        problems.add(MAIN.relativize(file) + " names the business process '" + literal + "'; the process owns its key in its context's api and its name in its definition");
                    }
                }
            }
        }
        Path migrations = Path.of("src/main/resources/db/migration");
        if (Files.isDirectory(migrations)) {
            try (Stream<Path> scripts = Files.walk(migrations)) {
                for (Path file : scripts.filter(f -> f.toString().endsWith(".sql") && f.toString().contains("/platform_")).toList()) {
                    for (String literal : processLiteralsIn(Files.readString(file), literals)) {
                        problems.add(file.getFileName() + " enumerates the business process '" + literal + "'");
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("the process-literal guard catches an id in any letter case and a process name (injection)")
    void processLiteralGuardCatchesInjectedLiterals() {
        List<String> literals = List.of("cost_timesheet_approval", "工时审批");
        assertThat(processLiteralsIn("if (\"COST_TIMESHEET_APPROVAL\".equals(x)) {}", literals)).containsExactly("cost_timesheet_approval");
        assertThat(processLiteralsIn("String t = \"工时审批\";", literals)).containsExactly("工时审批");
        assertThat(processLiteralsIn("String t = \"another_process\";", literals)).isEmpty();
    }

    private static List<String> processLiteralsIn(String text, List<String> literals) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return literals.stream().filter(l -> lower.contains(l.toLowerCase(java.util.Locale.ROOT))).toList();
    }
}

