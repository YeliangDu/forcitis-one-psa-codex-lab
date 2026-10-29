package com.forcitis.one;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GlossaryTest {
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    static final Path DRAFT = Path.of("target/glossary-draft.yaml");
    /** Code types without a (correct) glossary term: only shrinks (B2); one line per problem, see the header of the file. */
    static final Path CODE_BASELINE = Path.of("glossary/code-term-baseline.txt");
    static final String AGGREGATE_ROOT = "com.forcitis.one.kernel.AggregateRoot";
    static final String DOMAIN_EVENT = "com.forcitis.one.kernel.event.DomainEvent";
    static final Set<String> GENERIC_FIRST = Set.of("create", "update", "delete");
    static final Set<String> GENERIC_LAST = Set.of("data", "info", "manager", "handler", "helper", "util", "utils", "processor");
    static final Set<String> PARTICLES = Set.of("off", "out", "up", "down", "in", "over", "back", "away", "on");
    static final Set<String> PREPOSITIONS = Set.of("for", "to", "by", "from", "in", "on", "at", "with", "of", "into", "after", "before");
    // keep in step with IRREGULAR_PAST in scripts/guards/term_rules.py
    static final Set<String> IRREGULAR_PAST = Set.of("sent", "built", "held", "made", "paid", "set", "put", "read", "lost", "kept", "begun",
            "taken", "given", "written", "shown", "run", "bound", "found", "left", "met", "spent", "split", "cut", "hit", "closed", "reset",
            "frozen", "chosen", "broken", "undone", "overridden", "withdrawn", "drawn", "known", "seen", "gone", "become", "come",
            "won", "done", "sold", "bought", "withheld", "repaid", "prepaid", "overpaid", "underpaid", "told", "brought", "shut", "let", "rebuilt", "overdrawn");
    // a linking verb in the past plus one complement is an event name too: ApprovalTaskBecameOverdue; same as LINKING_PAST in term_rules.py
    static final Set<String> LINKING_PAST = Set.of("became");
    static final Set<String> NOT_PARTICIPLES = Set.of("need", "speed", "feed", "seed", "embed", "proceed", "exceed", "succeed", "bed", "red", "shed");
    static final Map<String, String> codeProblems = new TreeMap<>();
    static final Set<String> BUILT_INS = Set.of("equals", "hashCode", "toString", "values", "valueOf", "compareTo",
            "ordinal", "name", "getClass", "describeConstable", "getDeclaringClass", "main");
    static final Pattern SCANNED_PACKAGE = Pattern.compile("com\\.forcitis\\.one\\.(kernel|.*\\.(domain|api|events|web))(\\..*)?");
    static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?([a-z0-9_]+)\\.([a-z0-9_]+)\\s*\\((.*?)\\n\\)(?:\\s*PARTITION BY\\s+(?:RANGE|LIST|HASH)\\s*\\(\\s*[a-z0-9_]+\\s*\\))?\\s*;",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    static final Pattern ADD_COLUMN = Pattern.compile("ALTER TABLE\\s+([a-z0-9_]+)\\.([a-z0-9_]+)\\s+ADD COLUMN\\s+(?:IF NOT EXISTS\\s+)?([A-Za-z0-9_]+)",
            Pattern.CASE_INSENSITIVE);
    static final Pattern COMMENT = Pattern.compile("COMMENT ON (TABLE|COLUMN)\\s+([a-z0-9_.]+)\\s+IS\\s+'((?:[^']|'')*)'", Pattern.CASE_INSENSITIVE);
    static final Set<String> SQL_KEYWORDS = Set.of("primary", "constraint", "unique", "foreign", "check");

    record Occurrence(String identifier, String where, String bc) {
    }

    static final List<Glossary.Term> terms = Glossary.terms();
    static final Set<String> commonWords = Glossary.commonWords();
    static final List<String> glossaryProblems = Glossary.problems();
    static List<Occurrence> scanned = new ArrayList<>();
    static List<Occurrence> everywhere = new ArrayList<>();
    /** Latest COMMENT wins: migrations are read in version order. */
    static Map<String, String> comments = new LinkedHashMap<>();
    static Map<String, String> commentFiles = new LinkedHashMap<>();
    static Map<String, List<Occurrence>> unregistered = new TreeMap<>();
    static Map<String, List<String>> proposedOnly = new TreeMap<>();
    static Map<String, List<String>> deprecatedOnly = new TreeMap<>();
    static Set<String> deprecatedIds = new HashSet<>();

    @BeforeAll
    static void scan() throws IOException {
        scanCode();
        scanMigrations();
        Set<String> approved = new LinkedHashSet<>();
        Map<String, String> proposed = new LinkedHashMap<>();
        commonWords.forEach(w -> approved.add(singular(w)));
        Glossary.standardActions().forEach(w -> approved.add(singular(w)));
        for (Glossary.Term t : terms) {
            for (String w : t.words()) {
                if (t.status().equals("approved")) {
                    approved.add(singular(w));
                } else {
                    if (t.status().equals("deprecated")) {
                        deprecatedIds.add(t.id());
                    }
                    proposed.putIfAbsent(singular(w), t.id());
                }
            }
        }
        for (Occurrence o : scanned) {
            for (String word : split(o.identifier())) {
                String key = singular(word);
                if (approved.contains(key)) {
                    continue;
                }
                if (proposed.containsKey(key)) {
                    String id = proposed.get(key);
                    (deprecatedIds.contains(id) ? deprecatedOnly : proposedOnly)
                            .computeIfAbsent(word + " (term " + id + ")", k -> new ArrayList<>()).add(o.where());
                } else {
                    unregistered.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
                }
            }
        }
        writeDraft();
    }

    @Test
    void domainWordsAreRegistered() {
        if (!proposedOnly.isEmpty()) {
            StringBuilder warn = new StringBuilder("GLOSSARY WARNING: words backed only by status: proposed terms (approve them in glossary/terms.yaml):");
            proposedOnly.forEach((w, where) -> warn.append("\n  ").append(w).append(" -> ").append(String.join(", ", new LinkedHashSet<>(where))));
            System.out.println(warn);
        }
        if (!deprecatedOnly.isEmpty()) {
            StringBuilder warn = new StringBuilder("GLOSSARY WARNING: old names still in code, backed only by status: deprecated terms (rename them to replaced_by before until; owner in the term):");
            deprecatedOnly.forEach((w, where) -> warn.append("\n  ").append(w).append(" -> ").append(String.join(", ", new LinkedHashSet<>(where))));
            System.out.println(warn);
        }
        if (unregistered.isEmpty()) {
            return;
        }
        StringBuilder msg = new StringBuilder(GuardMessage.of("G37", "domain words used in code or migrations are not registered in the glossary",
                "add a term for each word to docs/glossary/terms.yaml (status: proposed is enough, the draft entries below can be pasted) or rename to the glossary word") + "\nWords and where they are used:");
        unregistered.forEach((word, where) -> {
            msg.append("\n  ").append(word).append(" -> ");
            msg.append(String.join(", ", where.stream().map(Occurrence::where).distinct().limit(5).toList()));
            if (where.size() > 5) {
                msg.append(", ... (").append(where.size()).append(" places)");
            }
        });
        msg.append("\n\nDraft entries (also in ").append(DRAFT).append("), fill zh / definition and paste under terms:\n").append(draft());
        throw new AssertionError(msg);
    }

    @Test
    void forbiddenSpellingsAreNotUsed() {
        List<String> problems = new ArrayList<>();
        for (Occurrence o : everywhere) {
            List<String> words = split(o.identifier());
            for (Glossary.Term t : terms) {
                for (Map.Entry<String, List<String>> bad : t.forbidden().entrySet()) {
                    int at = indexOf(words, bad.getValue());
                    if (at >= 0 && !coveredByRegisteredTerm(words, at, bad.getValue().size())) {
                        problems.add(o.where() + ": '" + bad.getKey() + "' is a forbidden spelling, use " + t.en()
                                + " / " + t.snake() + " (" + t.zh() + ")");
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new AssertionError(GuardMessage.of("G38", "a forbidden legacy or pinyin spelling of the glossary (terms.yaml forbidden) is used in an identifier",
                    "rename the identifier to the spelling each line names (class, field, method, table or column)") + "\n  "
                    + String.join("\n  ", new LinkedHashSet<>(problems)));
        }
    }

    @Test
    void commentsStartWithTheGlossaryChineseName() {
        List<String> problems = new ArrayList<>();
        comments.forEach((target, comment) -> {
            String name = target.substring(target.indexOf(' ') + 1);
            String last = name.substring(name.lastIndexOf('.') + 1);
            boolean table = target.startsWith("TABLE");
            for (Glossary.Term t : terms) {
                boolean named = table ? singular(last).equals(t.snake()) || last.equals(t.snake())
                        : last.equals(t.snake()) || last.equals(t.snake() + "_id");
                if (named && !comment.startsWith(t.zh())) {
                    problems.add(target + " (" + commentFiles.get(target) + "): COMMENT '" + comment + "' must start with '" + t.zh()
                            + "' (term " + t.id() + "); fix the COMMENT in a new migration, or fix zh in glossary/terms.yaml");
                }
            }
        });
        if (!problems.isEmpty()) {
            throw new AssertionError(GuardMessage.of("G39", "a COMMENT of a table or column does not start with the Chinese name of the glossary term",
                    "start the COMMENT with the Chinese name each line names (new migration), or correct zh in docs/glossary/terms.yaml") + "\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    void glossaryIsWellFormed() {
        if (!glossaryProblems.isEmpty()) {
            throw new AssertionError(GuardMessage.of("CS-1.4", "glossary/terms.yaml or a proposed glossary file is malformed",
                    "correct the entries each line names (required fields, unique ids, known kind and status)") + "\n  " + String.join("\n  ", glossaryProblems));
        }
    }

    @Test
    void aggregatesAndEventsAreGlossaryTerms() throws IOException {
        // G40 every aggregate root class has an aggregate term, G41 every domain event class a domain_event term whose name is past tense,
        // G42 no technical generic word in their names (B2, intent 2026-10-15-171959 items 3, 5, 8: code types can be traced to a term id)
        List<String> lines = Files.exists(CODE_BASELINE)
                ? Files.readAllLines(CODE_BASELINE).stream().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList() : List.of();
        List<String> failures = new ArrayList<>();
        // "count N" must equal the number of lines: the baseline can only shrink, lowering N together with deleting a line
        List<String> baseline = lines.stream().filter(l -> !l.startsWith("count ")).toList();
        String declared = lines.stream().filter(l -> l.startsWith("count ")).findFirst().orElse("count -1");
        if (!declared.equals("count " + baseline.size())) {
            failures.add("baseline declares '" + declared + "' but has " + baseline.size() + " lines (delete a line = lower the count, never raise it)");
        }
        if (baseline.size() != new HashSet<>(baseline).size()) {
            failures.add("duplicate line in " + CODE_BASELINE);
        }
        codeProblems.forEach((key, msg) -> {
            if (!baseline.contains(key)) {
                failures.add("new problem " + key + ": " + msg);
            }
        });
        baseline.stream().filter(k -> !codeProblems.containsKey(k)).forEach(k -> failures.add(
                "stale baseline line (fixed, remove it from " + CODE_BASELINE + "): " + k));
        if (!failures.isEmpty()) {
            throw new AssertionError(GuardMessage.of("G40-G42", "an aggregate or domain event class does not match a glossary term (aggregate term, past-tense domain_event term, no technical generic word), or the baseline is stale",
                    "add or fix the term in docs/glossary/terms.yaml or glossary/proposed/<module>.yaml, or rename the class; delete a fixed line from " + CODE_BASELINE + " and lower its count, never add one")
                    + "\n  " + String.join("\n  ", failures));
        }
    }

    /** Past participle at the end, optionally followed by a particle (WrittenOff) or a prepositional tail (SubmittedForApproval), or a linking verb in the past plus one complement (BecameOverdue); same rule as term_rules.py. */
    private static boolean pastTense(List<String> words) {
        List<String> ws = new ArrayList<>(words);
        while (ws.size() > 1 && PARTICLES.contains(ws.get(ws.size() - 1))) {
            ws.remove(ws.size() - 1);
        }
        for (int i = ws.size() - 1; i >= 0; i--) {
            if (LINKING_PAST.contains(ws.get(i))) {
                return i == ws.size() - 2;
            }
            if ((ws.get(i).endsWith("ed") && !NOT_PARTICIPLES.contains(ws.get(i))) || IRREGULAR_PAST.contains(ws.get(i))) {
                return i == ws.size() - 1 || PREPOSITIONS.contains(ws.get(i + 1));
            }
        }
        return false;
    }

    private static void collectCodeTermProblems(JavaClasses classes) {
        Map<String, List<Glossary.Term>> byBcAndSnake = new LinkedHashMap<>();
        for (Glossary.Term t : terms) {
            if (t.bc() != null && !t.status().equals("deprecated")) {
                byBcAndSnake.computeIfAbsent(t.bc() + "|" + t.snake(), k -> new ArrayList<>()).add(t);
            }
        }
        for (JavaClass c : classes) {
            String pkg = c.getPackageName();
            if (!pkg.startsWith("com.forcitis.one.") || c.isInterface() || c.isAnonymousClass() || c.getName().contains("$")
                    || c.getModifiers().contains(JavaModifier.ABSTRACT)) {
                continue;
            }
            boolean aggregate = !c.getName().equals(AGGREGATE_ROOT) && c.isAssignableTo(AGGREGATE_ROOT);
            boolean event = pkg.matches(".*\\.events(\\..*)?") && c.isAssignableTo(DOMAIN_EVENT);
            if (!aggregate && !event) {
                continue;
            }
            String bc = bc(pkg);
            String kind = aggregate ? "aggregate" : "domain_event";
            List<String> words = split(c.getSimpleName());
            String who = (aggregate ? "G40 " : "G41 ") + bc + " " + c.getSimpleName();
            boolean found = byBcAndSnake.getOrDefault(bc + "|" + String.join("_", words), List.of()).stream().anyMatch(t -> kind.equals(t.kind()));
            if (!found) {
                codeProblems.put(who + " no " + kind + " term", "no " + kind + " term with snake '" + String.join("_", words) + "' in bc " + bc
                        + ": add a proposed term (the class name is the English name)");
            }
            if (event) {
                if (!pastTense(words)) {
                    codeProblems.put(who + " event name not past tense", "domain event names are an aggregate name plus a past-tense business verb");
                }
            }
            if (GENERIC_FIRST.contains(words.get(0)) || (words.size() > 1 && GENERIC_LAST.contains(words.get(words.size() - 1)))) {
                codeProblems.put("G42 " + bc + " " + c.getSimpleName() + " generic word", "technical generic words (Create / Update / Delete first, Data / Info / Manager / Handler ... last) are not domain names");
            }
        }
    }

    private static void scanCode() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one");
        collectCodeTermProblems(classes);
        for (JavaClass c : classes) {
            if (c.getSimpleName().isEmpty() || c.getName().contains("$$") || c.getSimpleName().equals("package-info")
                    || c.getModifiers().contains(JavaModifier.SYNTHETIC) || c.isAnonymousClass()) {
                continue;
            }
            String bc = bc(c.getPackageName());
            List<Occurrence> found = new ArrayList<>();
            found.add(new Occurrence(c.getSimpleName(), c.getName() + " (type)", bc));
            for (JavaField f : c.getFields()) {
                if (f.getModifiers().contains(JavaModifier.SYNTHETIC) || f.getName().contains("$")) {
                    continue;
                }
                boolean enumConstant = c.isEnum() && f.getRawType().equals(c);
                boolean component = c.isRecord() && !f.getModifiers().contains(JavaModifier.STATIC);
                String kind = enumConstant ? "enum constant" : component ? "record component" : "field";
                found.add(new Occurrence(f.getName(), c.getName() + "." + f.getName() + " (" + kind + ")", bc));
            }
            for (JavaMethod m : c.getMethods()) {
                if (m.getModifiers().contains(JavaModifier.SYNTHETIC) || m.getModifiers().contains(JavaModifier.BRIDGE)
                        || m.getName().contains("$") || BUILT_INS.contains(m.getName())) {
                    continue;
                }
                boolean hidden = m.getModifiers().contains(JavaModifier.PRIVATE);
                found.add(new Occurrence(m.getName(), c.getName() + "." + m.getName() + "() (" + (hidden ? "private" : "method") + ")", bc));
            }
            everywhere.addAll(found);
            if (SCANNED_PACKAGE.matcher(c.getPackageName()).matches()) {
                // G37 reads the published vocabulary only; constants and private helpers are covered by G38.
                found.stream().filter(o -> !o.where().endsWith("(field)") && !o.where().endsWith("(private)")).forEach(scanned::add);
            }
        }
    }

    private static void scanMigrations() throws IOException {
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString())).toList()) {
                String sql = Files.readString(file);
                String name = file.getFileName().toString();
                Matcher t = CREATE_TABLE.matcher(sql);
                while (t.find()) {
                    String table = t.group(1) + "." + t.group(2);
                    String bc = t.group(1).replaceFirst("_", ".");
                    everywhere.add(new Occurrence(t.group(1), name + " schema " + t.group(1), bc));
                    sqlName(t.group(2), name + " table " + table, bc);
                    for (String raw : t.group(3).split(",\\s*\\n")) {
                        String column = raw.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                        if (!column.isEmpty() && !SQL_KEYWORDS.contains(column)) {
                            sqlName(column, name + " column " + table + "." + column, bc);
                        }
                    }
                }
                Matcher a = ADD_COLUMN.matcher(sql);
                while (a.find()) {
                    String column = a.group(3).toLowerCase(Locale.ROOT);
                    sqlName(column, name + " column " + a.group(1) + "." + a.group(2) + "." + column, a.group(1).replaceFirst("_", "."));
                }
                Matcher c = COMMENT.matcher(sql);
                while (c.find()) {
                    String target = c.group(1).toUpperCase(Locale.ROOT) + " " + c.group(2);
                    comments.put(target, c.group(3).replace("''", "'"));
                    commentFiles.put(target, name);
                }
            }
        }
    }

    private static void sqlName(String identifier, String where, String bc) {
        Occurrence o = new Occurrence(identifier, where, bc);
        scanned.add(o);
        everywhere.add(o);
    }

    private static void writeDraft() throws IOException {
        Files.createDirectories(DRAFT.getParent());
        Files.writeString(DRAFT, "# Generated by GlossaryTest: unregistered words found in code and migrations.\n"
                + "# Fill zh / definition, merge words that form one concept, then paste under terms: in glossary/terms.yaml.\n"
                + (unregistered.isEmpty() ? "terms: []\n" : "terms:\n" + draft()));
    }

    private static String draft() {
        StringBuilder out = new StringBuilder();
        unregistered.forEach((word, where) -> {
            List<String> seenIn = where.stream().map(Occurrence::identifier).distinct().limit(5).toList();
            String bc = where.getFirst().bc();
            out.append("  # seen in: ").append(String.join(", ", seenIn)).append('\n')
                    .append("  - id: ").append(word).append('\n')
                    .append("    zh: TODO\n")
                    .append("    en: ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append('\n')
                    .append("    snake: ").append(word).append('\n')
                    .append("    kind: TODO\n")
                    .append("    bc: ").append(bc).append('\n')
                    .append("    definition: TODO\n")
                    .append("    standard_ref: []\n")
                    .append("    confidence: low\n")
                    .append("    aliases: []\n")
                    .append("    forbidden: []\n")
                    .append("    status: proposed\n");
        });
        return out.toString();
    }

    static List<String> split(String identifier) {
        return Glossary.split(identifier);
    }

    static String singular(String word) {
        return Glossary.singular(word);
    }

    private static int indexOf(List<String> words, List<String> sequence) {
        for (int i = 0; i + sequence.size() <= words.size(); i++) {
            if (words.subList(i, i + sequence.size()).equals(sequence)) {
                return i;
            }
        }
        return -1;
    }

    /** A forbidden word inside a longer registered term (e.g. QuotationResultRef) is that term, not the legacy spelling. */
    private static boolean coveredByRegisteredTerm(List<String> words, int at, int length) {
        for (Glossary.Term t : terms) {
            List<String> termWords = split(t.en());
            if (termWords.size() <= length) {
                continue;
            }
            for (int i = Math.max(0, at + length - termWords.size()); i <= at && i + termWords.size() <= words.size(); i++) {
                if (words.subList(i, i + termWords.size()).equals(termWords)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String bc(String packageName) {
        String rest = packageName.replaceFirst("^com\\.forcitis\\.one\\.?", "");
        String[] parts = rest.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : parts[0];
    }
}
