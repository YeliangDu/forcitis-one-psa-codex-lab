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
    @DisplayName("release pipeline: the prod profile never migrates by itself; migrations run in the separate migrate step")
    void productionServiceDoesNotMigrate() throws IOException {
        Properties prod = new Properties();
        prod.load(new StringReader(Files.readString(RESOURCES.resolve("application-prod.properties"))));
        assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo("false");
        Properties dev = new Properties();
        dev.load(new StringReader(Files.readString(RESOURCES.resolve("application-dev.properties"))));
        assertThat(dev.getProperty("spring.flyway.enabled")).as("dev is deployed like production").isEqualTo("false");
        Properties shared = new Properties();
        shared.load(new StringReader(Files.readString(RESOURCES.resolve("application.properties"))));
        assertThat(shared.getProperty("spring.flyway.default-schema")).isEqualTo("one_migration");
    }

    static final Path ENV_TEMPLATE = Path.of("../tools/ops/ecs/files/one.env.template");

    @Test
    @DisplayName("D1-3 生产配置模板暴露 prometheus 端点")
    void productionTemplatesExposePrometheusEndpoint() throws IOException {
        String effective = effectiveProductionValue("management.endpoints.web.exposure.include");
        assertThat(exposed(effective)).as("management.endpoints.web.exposure.include effective in the prod profile: %s", effective)
                .contains("prometheus");
        assertThat(exposed(envTemplateValue("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE"))).as("one.env.template").contains("prometheus");
    }

    @Test
    @DisplayName("D1-3 生产数据源连接串含 logServerErrorDetail=false")
    void productionDataSourceUrlsSwitchOffServerErrorDetail() throws IOException {
        Pattern switchedOff = Pattern.compile("[?&;]logServerErrorDetail=false(?:[&;\\s']|$)");
        List<String> problems = new ArrayList<>();
        for (Path file : files(RESOURCES, ".properties")) {
            if (!file.getFileName().toString().startsWith("application")) {
                continue;
            }
            for (String line : Files.readAllLines(file)) {
                if (line.contains("jdbc:postgresql:") && !switchedOff.matcher(line).find()) {
                    problems.add(file + ": " + line.strip());
                }
            }
        }
        String templateUrl = envTemplateValue("SPRING_DATASOURCE_URL");
        assertThat(templateUrl).as("one.env.template SPRING_DATASOURCE_URL").isNotNull().startsWith("jdbc:postgresql:");
        if (!switchedOff.matcher(templateUrl).find()) {
            problems.add(ENV_TEMPLATE + ": SPRING_DATASOURCE_URL has no logServerErrorDetail=false");
        }
        assertThat(problems).as("PostgreSQL error detail (the conflicting value) must not travel in exception messages").isEmpty();
    }

    @Test
    @DisplayName("D1-3b 生产管理端口绑定回环地址并使用独立端口")
    void productionManagementServerListensOnLoopbackOnly() throws IOException {
        assertThat(effectiveProductionValue("management.server.address")).isEqualTo("127.0.0.1");
        String port = effectiveProductionValue("management.server.port");
        assertThat(port).as("management.server.port effective in the prod profile").isNotNull();
        assertThat(port).isEqualTo(envTemplateValue("MANAGEMENT_SERVER_PORT"));
        assertThat(envTemplateValue("MANAGEMENT_SERVER_ADDRESS")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("D1-3 共用配置默认关闭数据库驱动的服务端错误详情，不依赖连接串")
    void sharedConfigurationSwitchesOffServerErrorDetail() throws IOException {
        assertThat(effectiveProductionValue("spring.datasource.hikari.data-source-properties.logServerErrorDetail")).isEqualTo("false");
    }

    private static List<String> exposed(String value) {
        return value == null ? List.of() : Stream.of(value.split(",")).map(String::strip).toList();
    }

    /** The prod profile file wins over the module files imported by application.properties, which win over application.properties itself. */
    private static String effectiveProductionValue(String key) throws IOException {
        List<Path> byPrecedence = new ArrayList<>();
        byPrecedence.add(RESOURCES.resolve("application-prod.properties"));
        byPrecedence.addAll(files(RESOURCES.resolve("config"), ".properties"));
        byPrecedence.add(RESOURCES.resolve("application.properties"));
        for (Path file : byPrecedence) {
            Properties properties = new Properties();
            properties.load(new StringReader(Files.readString(file)));
            if (properties.getProperty(key) != null) {
                return properties.getProperty(key);
            }
        }
        return null;
    }

    private static String envTemplateValue(String name) throws IOException {
        for (String line : Files.readAllLines(ENV_TEMPLATE)) {
            if (line.startsWith(name + "=")) {
                String value = line.substring(name.length() + 1).strip();
                return value.length() >= 2 && value.startsWith("'") && value.endsWith("'") ? value.substring(1, value.length() - 1) : value;
            }
        }
        return null;
    }

    private static List<Path> files(Path root, String... suffixes) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).filter(p -> Stream.of(suffixes).anyMatch(s -> p.toString().endsWith(s))).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
