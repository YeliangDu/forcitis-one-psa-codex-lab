package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SingleOperatorModeNotPublishedTest {

    static final Path RESOURCES = Path.of("src/main/resources");
    static final Path JAVA = Path.of("src/main/java");
    static final Path IDENTITY = JAVA.resolve("com/forcitis/one/platform/identity");
    static final Path ENV_TEMPLATE = Path.of("../tools/ops/ecs/files/one.env.template");

    static final Pattern PUBLISHED = Pattern.compile("(?i)single[-_.]?operator");
    static final Pattern KEY_FORMS = Pattern.compile("(?i)single[-_.]?operator[-_.]?mode");
    static final Pattern STRING_LITERAL = Pattern.compile("\"\"\"(.*?)\"\"\"|\"(?:[^\"\\\\\\n]|\\\\.)*\"", Pattern.DOTALL);
    static final Pattern PUBLIC_TYPE = Pattern.compile("(?m)^public\\s+(?:(?:final|abstract|sealed)\\s+)*(?:class|record|interface|enum)\\s");

    static List<String> publishedIn(Map<String, String> files) {
        List<String> problems = new ArrayList<>();
        files.forEach((name, text) -> {
            if (PUBLISHED.matcher(text).find()) {
                problems.add(name);
            }
        });
        return problems;
    }

    static boolean namesTheKeyInAStringLiteral(String source) {
        Matcher literal = STRING_LITERAL.matcher(source);
        while (literal.find()) {
            if (KEY_FORMS.matcher(literal.group()).find()) {
                return true;
            }
        }
        return false;
    }

    static List<String> keyOutsideIdentity(Map<String, String> sources) {
        List<String> problems = new ArrayList<>();
        sources.forEach((name, text) -> {
            if (!name.replace('\\', '/').contains("com/forcitis/one/platform/identity/") && namesTheKeyInAStringLiteral(text)) {
                problems.add(name);
            }
        });
        return problems;
    }

    private static Map<String, String> read(Path root, Pattern fileName) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(f -> fileName.matcher(f.getFileName().toString()).matches()).sorted().toList()) {
                files.put(file.toString(), Files.readString(file));
            }
        }
        return files;
    }

    private static Map<String, String> shippedConfiguration() throws IOException {
        Map<String, String> files = read(RESOURCES, Pattern.compile(".*\\.(properties|yaml|yml)"));
        files.put(ENV_TEMPLATE.toString(), Files.readString(ENV_TEMPLATE));
        return files;
    }

    @Test
    @DisplayName("SINGLE-OP P1-18 仓库不发布该键：resources 下全部 properties 与 yaml（含 config/*.properties）与 one.env.template 都不匹配 (?i)single[-_.]?operator")
    void theRepositoryDoesNotPublishTheKey() throws IOException {
        Map<String, String> files = shippedConfiguration();

        assertThat(files.keySet()).as("the scan must see the real configuration").anyMatch(n -> n.endsWith("application.properties"))
                .anyMatch(n -> n.endsWith("application-dev.properties")).anyMatch(n -> n.endsWith("application-prod.properties"))
                .anyMatch(n -> n.endsWith("config/platform_identity.properties")).anyMatch(n -> n.endsWith("one.env.template"));
        assertThat(publishedIn(files)).as("files that publish the single operator mode key").isEmpty();
    }

    @Test
    @DisplayName("SINGLE-OP P1-18 扫描函数对注入的样例文件能报出（properties、yaml、环境变量模板、驼峰、下划线），证明不是空转")
    void theScanReportsInjectedSamples() {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("a.properties", "forcitis.platform.identity.single-operator-mode=true\n");
        samples.put("b.yaml", "forcitis:\n  platform:\n    identity:\n      singleOperatorMode: true\n");
        samples.put("c.env", "FORCITIS_PLATFORM_IDENTITY_SINGLE_OPERATOR_MODE=true\n");
        samples.put("d.properties", "x=Single.Operator\n");
        samples.put("clean.properties", "forcitis.platform.identity.emergency.check.enabled=false\n");

        assertThat(publishedIn(samples)).containsExactly("a.properties", "b.yaml", "c.env", "d.properties");
    }

    @Test
    @DisplayName("SINGLE-OP P1-19 源码扫描：platform.identity 以外的主代码不出现该键（点号、连字符、下划线、驼峰、环境变量写法）")
    void noMainCodeOutsideIdentityNamesTheKey() throws IOException {
        Map<String, String> sources = read(JAVA, Pattern.compile(".*\\.java"));

        assertThat(sources.keySet()).as("the scan must see the real sources").hasSizeGreaterThan(100);
        assertThat(keyOutsideIdentity(sources)).as("sources outside platform.identity that name the key").isEmpty();
    }

    @Test
    @DisplayName("SINGLE-OP P1-19 扫描函数对注入的样例能报出（字符串字面量里的各种写法），放过 platform.identity 内的同名文本，也放过类型引用与 import（api.SingleOperatorMode）")
    void theSourceScanReportsInjectedSamples() {
        Map<String, String> samples = new LinkedHashMap<>();
        String dir = "src/main/java/com/forcitis/one/";
        samples.put(dir + "platform/numbering/A.java", "\"forcitis.platform.identity.single-operator-mode\"");
        samples.put(dir + "platform/numbering/B.java", "\"FORCITIS_PLATFORM_IDENTITY_SINGLE_OPERATOR_MODE\"");
        samples.put(dir + "kernel/C.java", "String key = \"forcitis.platform.identity.singleOperatorMode\";");
        samples.put(dir + "cost/D.java", "\"single_operator_mode\"");
        samples.put(dir + "platform/identity/infrastructure/startup/E.java", "\"forcitis.platform.identity.single-operator-mode\"");
        samples.put(dir + "platform/numbering/Type.java", "import com.forcitis.one.platform.identity.api.SingleOperatorMode;\nclass Type { SingleOperatorMode mode; boolean singleOperatorMode; }");
        samples.put(dir + "kernel/Clean.java", "class Clean {}");

        assertThat(keyOutsideIdentity(samples)).containsExactly(dir + "platform/numbering/A.java", dir + "platform/numbering/B.java", dir + "kernel/C.java",
                dir + "cost/D.java");
    }

    @Test
    @DisplayName("SINGLE-OP P1-20 设置类型为 platform.identity 包内私有，不是 @RefreshScope，不带 @ConfigurationProperties；对外只有 api 下的只读模式查询")
    void theSettingsTypeIsPrivateToTheModuleAndNotRefreshable() throws IOException {
        Map<String, String> identity = read(IDENTITY, Pattern.compile(".*\\.java"));
        Map<String, String> modeTypes = new LinkedHashMap<>();
        identity.forEach((name, text) -> {
            if (name.contains("SingleOperatorMode")) {
                modeTypes.put(name.replace('\\', '/'), text);
            }
        });

        assertThat(modeTypes.keySet()).as("the read-only query in api").anyMatch(n -> n.endsWith("/identity/api/SingleOperatorMode.java"));
        assertThat(modeTypes.keySet()).as("the settings type and the listener outside api").anyMatch(n -> !n.contains("/identity/api/"));
        modeTypes.forEach((name, text) -> {
            assertThat(text).as(name).doesNotContain("RefreshScope").doesNotContain("@ConfigurationProperties");
            if (!name.contains("/identity/api/")) {
                assertThat(PUBLIC_TYPE.matcher(text).find()).as("%s declares a public type; only the api query may be public", name).isFalse();
            }
        });
        identity.forEach((name, text) -> assertThat(text.contains("RefreshScope") && KEY_FORMS.matcher(text).find()).as("%s", name).isFalse());
    }
}
