package com.forcitis.one.platform.integration.infrastructure.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductionTransportConfigTest {
    static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    @DisplayName("N-3 no shipped property file sets a database URL that skips encryption or trusts any certificate")
    void shippedDatabaseUrlsAreNotWeak() throws IOException {
        Pattern weak = Pattern.compile("(?i)sslmode=(disable|allow|prefer)|trustServerCertificate=true|encrypt=false");
        List<String> problems = new ArrayList<>();
        for (Path file : files(RESOURCES, ".properties")) {
            Properties properties = new Properties();
            properties.load(new StringReader(Files.readString(file)));
            for (String key : properties.stringPropertyNames()) {
                if (properties.getProperty(key).startsWith("jdbc:") && weak.matcher(properties.getProperty(key)).find()) {
                    problems.add(file + ": " + key);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("N-3 the application refuses to start under the prod profile unless the primary database is reached with sslmode=verify-full and a readable CA file")
    void productionVerifiesTheDatabaseServer(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Class<?> guard = Class.forName("com.forcitis.one.platform.integration.infrastructure.transport.DatabaseTransportConfiguration");
        Path ca = Files.writeString(directory.resolve("rds-ca.pem"), "the check only needs a readable file");
        var prod = new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(guard)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"));

        prod.withPropertyValues("spring.datasource.url=jdbc:postgresql://db/one?sslmode=require&sslrootcert=" + ca)
                .run(context -> assertThat(context).hasFailed());
        prod.withPropertyValues("spring.datasource.url=jdbc:postgresql://db/one?sslmode=verify-full")
                .run(context -> assertThat(context).as("the CA path is mandatory").hasFailed());
        prod.withPropertyValues("spring.datasource.url=jdbc:postgresql://db/one?sslmode=verify-full&sslrootcert=" + ca)
                .run(context -> assertThat(context).hasNotFailed());
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(guard)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://db/one?sslmode=require")
                .run(context -> assertThat(context).as("dev may use require").hasNotFailed());
    }

    private static List<Path> files(Path root, String... suffixes) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).filter(p -> Stream.of(suffixes).anyMatch(s -> p.toString().endsWith(s))).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
