package com.forcitis.one;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test container images come from backend/architecture/test-images.txt, the one list the CI pre-pull step reads too; a missing file or line fails the test, there is no fallback tag. */
public final class TestImages {

    static final Path LIST = Path.of("architecture/test-images.txt");

    public static final String MSSQL_PREFIX = "mcr.microsoft.com/mssql/server";

    private TestImages() {
    }

    public static String startingWith(String repository) {
        try {
            return Files.readAllLines(LIST).stream().map(String::strip).filter(line -> line.startsWith(repository)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(LIST + " has no line starting with " + repository));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + LIST.toAbsolutePath(), e);
        }
    }
}
