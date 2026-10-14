package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SESSION_PLAYBOOK section 14, items 5 and 12 (review B-1): the session context never answers a legal entity it made up. */
class CurrentUserGuardTest {
    static final Path MAIN = Path.of("src/main/java");
    static final Pattern IMPLEMENTS = Pattern.compile("\\bimplements\\b[^{]*\\bCurrentUser\\b");
    static final Pattern LITERAL_LEGAL_ENTITY = Pattern.compile("new\\s+LegalEntityId\\(\\s*[0-9][0-9_]*[lL]?\\s*\\)");

    static List<String> literalLegalEntities(String file, String source) {
        List<String> problems = new ArrayList<>();
        if (IMPLEMENTS.matcher(StateWritePolicy.withoutComments(source)).find()
                && LITERAL_LEGAL_ENTITY.matcher(StateWritePolicy.withoutComments(source)).find()) {
            problems.add(file + ": a CurrentUser implementation builds a LegalEntityId from a literal; resolve it from the organisation");
        }
        return problems;
    }

    @Test
    @DisplayName("no CurrentUser implementation builds new LegalEntityId(<literal>)")
    void noLiteralLegalEntityInTheSessionContext() throws IOException {
        List<String> problems = new ArrayList<>();
        int implementations = 0;
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (IMPLEMENTS.matcher(source).find()) {
                    implementations++;
                }
                problems.addAll(literalLegalEntities(MAIN.relativize(file).toString(), source));
            }
        }
        assertThat(implementations).as("the guard must see the CurrentUser implementation").isGreaterThanOrEqualTo(1);
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("injected: a literal in a CurrentUser implementation is caught; a lookup and an unrelated class are not")
    void literalIsCaught() {
        String hardCoded = "class X implements CurrentUser { public LegalEntityId legalEntityId() { return new LegalEntityId(1L); } }";
        String resolved = "class Y implements CurrentUser { public LegalEntityId legalEntityId() { return directory.on(userId(), today); } }";
        String unrelated = "class Z { LegalEntityId id = new LegalEntityId(1); }";
        String commented = "class W implements CurrentUser { // new LegalEntityId(1)\n }";
        assertThat(literalLegalEntities("X.java", hardCoded)).hasSize(1);
        assertThat(literalLegalEntities("Y.java", resolved)).isEmpty();
        assertThat(literalLegalEntities("Z.java", unrelated)).isEmpty();
        assertThat(literalLegalEntities("W.java", commented)).isEmpty();
    }
}
