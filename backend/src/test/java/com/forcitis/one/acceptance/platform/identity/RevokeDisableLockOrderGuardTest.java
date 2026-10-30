package com.forcitis.one.acceptance.platform.identity;

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

/**
 * #732 part B, lock ruling section 2(c) item 3 (text guard, turns the binding condition into a guard): an identity class that takes the
 * separation advisory lock also calls SeparationReview, and in every method the lock comes before any FOR UPDATE / FOR SHARE. Identity must take
 * the lock at all, because the manual revoke and the disable now decide on the count. The scanner is proven on samples first.
 */
class RevokeDisableLockOrderGuardTest {

    private static final Path IDENTITY = Path.of("src/main/java/com/forcitis/one/platform/identity");
    private static final Pattern LOCK = Pattern.compile("pg_advisory_xact_lock");
    private static final Pattern ROW_LOCK = Pattern.compile("(?i)for\\s+(update|share)\\b");
    private static final Pattern MEMBER_START = Pattern.compile("(?m)^    [^\\s*/@}][^\\n]*\\)[^\\n;]*\\{\\s*$");

    /** Whether the text takes the separation lock (the call and the kernel key, by name or by literal). */
    static boolean takesTheSeparationLock(String source) {
        return LOCK.matcher(source).find() && (source.contains("SEPARATION_LOCK_KEY") || source.contains("separation-decision"));
    }

    /** Problems of one class: no SeparationReview, or a method with a row lock before its advisory lock. */
    static List<String> problems(String name, String source) {
        List<String> found = new ArrayList<>();
        if (!takesTheSeparationLock(source)) {
            return found;
        }
        if (!source.contains("SeparationReview")) {
            found.add(name + " takes the separation lock but does not call SeparationReview");
        }
        List<Integer> starts = new ArrayList<>();
        Matcher member = MEMBER_START.matcher(source);
        while (member.find()) {
            starts.add(member.start());
        }
        if (starts.isEmpty()) {
            starts.add(0);
        }
        starts.add(source.length());
        for (int i = 0; i + 1 < starts.size(); i++) {
            String body = source.substring(starts.get(i), starts.get(i + 1));
            Matcher lock = LOCK.matcher(body);
            Matcher row = ROW_LOCK.matcher(body);
            if (lock.find() && row.find() && row.start() < lock.start()) {
                found.add(name + ": a row lock comes before the advisory lock in " + body.lines().findFirst().orElse("").strip());
            }
        }
        return found;
    }

    private static List<Path> identitySources() throws IOException {
        assertThat(IDENTITY).as("run from backend/").isDirectory();
        try (Stream<Path> files = Files.walk(IDENTITY)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("#732B guard self-test: the scanner flags a class without SeparationReview and a method that locks a row before the advisory lock, and passes the right order")
    void theScannerFlagsTheWrongShapes() {
        String key = "select pg_advisory_xact_lock(hashtextextended(:key, 0))";
        String wrong = "class A {\n    void m() {\n        sql(\"select 1 from user_accounts for update\");\n        sql(\"" + key + "\"); SEPARATION_LOCK_KEY; SeparationReview r;\n    }\n}\n";
        String right = "class A {\n    void m() {\n        sql(\"" + key + "\"); SEPARATION_LOCK_KEY; SeparationReview r;\n        sql(\"select 1 from user_accounts for update\");\n    }\n}\n";
        String noReview = "class A {\n    void m() {\n        sql(\"" + key + "\"); SEPARATION_LOCK_KEY;\n    }\n}\n";
        assertThat(problems("A", wrong)).hasSize(1);
        assertThat(problems("A", right)).isEmpty();
        assertThat(problems("A", noReview)).hasSize(1);
        assertThat(problems("A", "class B { }")).isEmpty();
    }

    @Test
    @DisplayName("#732B lock order guard: every identity class that takes the separation advisory lock also calls SeparationReview, and takes the lock before any FOR UPDATE / FOR SHARE of the same method")
    void identityLockUsersFollowTheProtocol() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path source : identitySources()) {
            problems.addAll(problems(source.getFileName().toString(), Files.readString(source)));
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("#732B binding condition: identity takes the separation advisory lock (kernel key) in the paths that now decide on the count (manual revoke, disable)")
    void identityTakesTheSeparationLock() throws IOException {
        List<String> users = new ArrayList<>();
        for (Path source : identitySources()) {
            if (takesTheSeparationLock(Files.readString(source))) {
                users.add(source.getFileName().toString());
            }
        }
        assertThat(users).as("identity classes that take pg_advisory_xact_lock on SeparationReview.SEPARATION_LOCK_KEY").isNotEmpty();
    }
}
