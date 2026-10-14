package com.forcitis.one.platform.identity;

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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductionConfigSanityTest {

    static final Path RESOURCES = Path.of("src/main/resources");
    static final Path JAVA = Path.of("src/main/java");

    static final Pattern AUTH_KEY = Pattern.compile("(?i).*(auth|login|sms|captcha|verif|otp|totp|password|passwd|secret|token|ticket|credential|sso|session|issuer|signing|key|mock|bypass).*");
    static final Pattern WEAK_VALUE = Pattern.compile("(?i).*(9999|0000|123456|\\btest\\b|\\bmock\\b|fixed|\\bdemo\\b|changeme|password123|\\bdefault-password\\b|\\bdummy\\b).*");

    @Test
    @DisplayName("I-4 no fixed, test or mock value for any authentication-related property in the shipped configuration")
    void noWeakAuthenticationValues() throws IOException {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Path file : files(RESOURCES, ".properties")) {
            Properties properties = new Properties();
            properties.load(new StringReader(Files.readString(file)));
            for (String key : properties.stringPropertyNames()) {
                checked++;
                String value = properties.getProperty(key);
                if (AUTH_KEY.matcher(key).matches() && WEAK_VALUE.matcher(value).matches()) {
                    problems.add(file + ": " + key + " = " + value);
                }
                if (Pattern.compile("(?i).*(mock|bypass|skip).*(login|auth|sso|csrf).*|.*(login|auth|sso|csrf).*(mock|bypass|skip).*").matcher(key).matches()) {
                    problems.add(file + ": " + key + " looks like a way around authentication");
                }
            }
        }
        for (Path file : files(RESOURCES, ".yaml", ".yml")) {
            String text = Files.readString(file);
            if (AUTH_KEY.matcher(text).find() && WEAK_VALUE.matcher(text.replace("\n", " ")).find()) {
                problems.add(file + ": review authentication values by hand");
            }
        }
        assertThat(checked).as("the guard must look at real configuration").isGreaterThan(10);
        assertThat(problems).as("I-4 weak authentication configuration").isEmpty();
    }

    @Test
    @DisplayName("I-4 no profile or file switches authentication off or fixes a verification code")
    void noFixedCodeOrBypassInCode() throws IOException {
        List<String> problems = new ArrayList<>();
        Pattern fixedCode = Pattern.compile("\"(9999|0000|123456|000000)\"");
        Pattern bypass = Pattern.compile("(?i)(mock-?login|skip-?auth|bypass-?auth|permitAll\\(\\)\\s*;\\s*//\\s*dev|local-?only-?login)");
        for (Path file : files(JAVA, ".java")) {
            String text = Files.readString(file);
            if (fixedCode.matcher(text).find()) {
                problems.add(file + ": built-in verification code");
            }
            if (bypass.matcher(text).find()) {
                problems.add(file + ": authentication bypass switch");
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("I-4 no SMS or verification-code login exists in the new system (D-01, ADR-0019)")
    void noSmsLogin() throws IOException {
        List<String> problems = new ArrayList<>();
        Pattern sms = Pattern.compile("(?i)(sms[-_ ]?(code|login)|verification[-_ ]?code|captcha|sendSms|sms-login)");
        for (Path file : files(JAVA, ".java")) {
            if (sms.matcher(Files.readString(file)).find()) {
                problems.add(file.toString());
            }
        }
        assertThat(problems).isEmpty();
    }

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
    @DisplayName("release pipeline: the prod profile never migrates by itself; migrations run in the separate migrate step")
    void productionServiceDoesNotMigrate() throws IOException {
        Properties prod = new Properties();
        prod.load(new StringReader(Files.readString(RESOURCES.resolve("application-prod.properties"))));
        assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo("false");
        Properties shared = new Properties();
        shared.load(new StringReader(Files.readString(RESOURCES.resolve("application.properties"))));
        assertThat(shared.getProperty("spring.flyway.default-schema")).isEqualTo("one_migration");
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
