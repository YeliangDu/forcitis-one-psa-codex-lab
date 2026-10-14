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

/**
 * Ubiquitous-language guard (G37-G39). glossary/terms.yaml (loaded by {@link Glossary}) is the single source of truth:
 * every word in a domain / api / events / web / kernel identifier and in every table / column name must be registered;
 * legacy spellings listed as {@code forbidden} fail anywhere; a column named after a term carries the term's Chinese name.
 * Unregistered words are written to target/glossary-draft.yaml as paste-ready proposed entries.
 */
class GlossaryTest {

    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    static final Path DRAFT = Path.of("target/glossary-draft.yaml");
    /** Names the compiler or the JDK chooses, not the author. */
    static final Set<String> BUILT_INS = Set.of("equals", "hashCode", "toString", "values", "valueOf", "compareTo",
            "ordinal", "name", "getClass", "describeConstable", "getDeclaringClass", "main");
    static final Pattern SCANNED_PACKAGE = Pattern.compile("com\\.forcitis\\.one\\.(kernel|.*\\.(domain|api|events|web))(\\..*)?");
    static final Pattern CREATE_TABLE = Pattern.compile("CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?([a-z0-9_]+)\\.([a-z0-9_]+)\\s*\\((.*?)\\n\\);",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    static final Pattern ADD_COLUMN = Pattern.compile("ALTER TABLE\\s+([a-z0-9_]+)\\.([a-z0-9_]+)\\s+ADD COLUMN\\s+(?:IF NOT EXISTS\\s+)?([A-Za-z0-9_]+)",
            Pattern.CASE_INSENSITIVE);
    static final Pattern COMMENT = Pattern.compile("COMMENT ON (TABLE|COLUMN)\\s+([a-z0-9_.]+)\\s+IS\\s+'((?:[^']|'')*)'", Pattern.CASE_INSENSITIVE);
    static final Set<String> SQL_KEYWORDS = Set.of("primary", "constraint", "unique", "foreign", "check");

    /** One identifier, where it was found, and the bounded context it belongs to. */
    record Occurrence(String identifier, String where, String bc) {
    }

    static final List<Glossary.Term> terms = Glossary.terms();
    static final Set<String> commonWords = Glossary.commonWords();
    static final List<String> glossaryProblems = Glossary.problems();
    static List<Occurrence> scanned = new ArrayList<>();
    static List<Occurrence> everywhere = new ArrayList<>();
    /** "COLUMN schema.table.column" -> latest COMMENT (migrations are read in version order, so a later script wins). */
    static Map<String, String> comments = new LinkedHashMap<>();
    static Map<String, String> commentFiles = new LinkedHashMap<>();
    /** unregistered word -> locations. */
    static Map<String, List<Occurrence>> unregistered = new TreeMap<>();
    /** word covered only by a proposed term -> term id + locations. */
    static Map<String, List<String>> proposedOnly = new TreeMap<>();

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
                    proposedOnly.computeIfAbsent(word + " (term " + proposed.get(key) + ")", k -> new ArrayList<>()).add(o.where());
                } else {
                    unregistered.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
                }
            }
        }
        writeDraft();
    }

    // ---------- G37: every domain word is registered ----------

    @Test
    void domainWordsAreRegistered() {
        if (!proposedOnly.isEmpty()) {
            StringBuilder warn = new StringBuilder("GLOSSARY WARNING: words backed only by status: proposed terms (approve them in glossary/terms.yaml):");
            proposedOnly.forEach((w, where) -> warn.append("\n  ").append(w).append(" -> ").append(String.join(", ", new LinkedHashSet<>(where))));
            System.out.println(warn);
        }
        if (unregistered.isEmpty()) {
            return;
        }
        StringBuilder msg = new StringBuilder("G37 unregistered domain words (add a term to glossary/terms.yaml or rename to the glossary word):");
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

    // ---------- G38: forbidden spellings ----------

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
            throw new AssertionError("G38 forbidden legacy / pinyin spellings (glossary/terms.yaml forbidden):\n  "
                    + String.join("\n  ", new LinkedHashSet<>(problems)));
        }
    }

    // ---------- G39: column / table comments start with the term's Chinese name ----------

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
            throw new AssertionError("G39 data-dictionary Chinese differs from the glossary:\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    void glossaryIsWellFormed() {
        if (!glossaryProblems.isEmpty()) {
            throw new AssertionError("glossary/terms.yaml is malformed:\n  " + String.join("\n  ", glossaryProblems));
        }
    }

    // ---------- loading ----------

    private static void scanCode() {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one");
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
                // G37 reads the published vocabulary: types, record components, enum constants, non-private methods.
                // Constants and private helpers (regex TOKEN, Money.SCALE, Permission.Codes) are covered by G38 only.
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

    // ---------- draft output ----------

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

    // ---------- words ----------

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
