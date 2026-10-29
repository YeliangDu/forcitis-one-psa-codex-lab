package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Review M-16: what the server shows people (cards, to-do titles, messages) is defined in classes named *Texts, and every Chinese
 * text there is cut into registered terms and the allowed non-business words of the front end - the same rule the front end follows.
 */
class OutwardTextGuardTest {
    static final Path MAIN = Path.of("src/main/java");
    static final Path ALLOW_LIST = Path.of("../frontend/scripts/copy-allow-list.yaml");
    static final Pattern CHINESE = Pattern.compile("[\\u4e00-\\u9fff]+");
    static final Pattern STRING = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);

    @SuppressWarnings("unchecked")
    static Set<String> vocabulary() throws IOException {
        Set<String> words = new LinkedHashSet<>();
        Glossary.terms().forEach(term -> {
            Matcher chunk = CHINESE.matcher(term.zh() == null ? "" : term.zh());
            while (chunk.find()) {
                words.add(chunk.group());
            }
        });
        try (var in = Files.newInputStream(ALLOW_LIST)) {
            Object allowed = ((java.util.Map<String, Object>) new Yaml().load(in)).get("words");
            ((List<String>) allowed).forEach(words::add);
        }
        return words;
    }

    /** The smallest set of pieces of the run that no word of the vocabulary explains. */
    static List<String> uncovered(String run, Set<String> vocabulary) {
        int longest = vocabulary.stream().mapToInt(String::length).max().orElse(1);
        int[] missing = new int[run.length() + 1];
        int[] from = new int[run.length() + 1];
        boolean[] covered = new boolean[run.length() + 1];
        covered[0] = true;
        for (int end = 1; end <= run.length(); end++) {
            missing[end] = missing[end - 1] + 1;
            from[end] = end - 1;
            for (int start = Math.max(0, end - longest); start < end; start++) {
                if (vocabulary.contains(run.substring(start, end)) && missing[start] < missing[end]) {
                    missing[end] = missing[start];
                    from[end] = start;
                    covered[end] = true;
                }
            }
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder open = new StringBuilder();
        for (int end = run.length(); end > 0; end = from[end]) {
            String piece = run.substring(from[end], end);
            if (covered[end]) {
                if (!open.isEmpty()) {
                    pieces.addFirst(open.toString());
                    open.setLength(0);
                }
            } else {
                open.insert(0, piece);
            }
        }
        if (!open.isEmpty()) {
            pieces.addFirst(open.toString());
        }
        return pieces;
    }

    static List<String> problems(String file, String source, Set<String> vocabulary) {
        List<String> problems = new ArrayList<>();
        Matcher string = STRING.matcher(StateWritePolicy.withoutComments(source));
        while (string.find()) {
            String text = string.group(1) != null ? string.group(1) : string.group(2);
            Matcher run = CHINESE.matcher(text);
            while (run.find()) {
                uncovered(run.group(), vocabulary).forEach(piece -> problems.add(file + ": \"" + piece + "\" in \"" + text
                        + "\" is neither a registered term nor on the allow list"));
            }
        }
        return problems;
    }

    @Test
    @DisplayName("every Chinese text in a *Texts class is made of registered terms and allowed words")
    void outwardTextsUseTheGlossary() throws IOException {
        Set<String> vocabulary = vocabulary();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith("Texts.java")).sorted().toList()) {
                problems.addAll(problems(MAIN.relativize(file).toString(), Files.readString(file), vocabulary));
            }
        }
        assertThat(problems).as(GuardMessage.of("CS-2.12 (review M-16)", "a Chinese text in a *Texts class has a piece that is neither a registered term nor an allowed non-business word",
                "use the registered Chinese term, register the term in glossary/terms.yaml, or add a non-business word to frontend/scripts/copy-allow-list.yaml")).isEmpty();
    }

    @Test
    @DisplayName("injected: a text with a word the glossary does not know is found, one made of terms and allowed words is not")
    void unknownWordsAreFound() throws IOException {
        Set<String> vocabulary = vocabulary();
        assertThat(vocabulary).contains("审批", "工时单");
        assertThat(problems("T.java", "String a = \"审批\";", vocabulary)).isEmpty();
        assertThat(problems("T.java", "String a = \"工时单 \" + id; // 随便写写", vocabulary)).isEmpty();
        assertThat(problems("T.java", "String a = \"审批赳赳\";", vocabulary)).singleElement().asString().contains("赳赳");
    }
}
