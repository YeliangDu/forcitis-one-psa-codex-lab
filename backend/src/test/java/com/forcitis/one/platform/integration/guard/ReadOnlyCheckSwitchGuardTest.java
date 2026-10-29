package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.GuardMessage;
import com.forcitis.one.JavaCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR37-1 (ADR-0037 item 4.3, implementation card: no switch): once a read-only connection is configured it is always checked, so neither
 * INTEGRATION_*_VALIDATE_ON_STARTUP nor any property, record component or @ConditionalOnProperty that could turn the check off may exist.
 */
class ReadOnlyCheckSwitchGuardTest {

    static final Pattern SWITCH = Pattern.compile("validate[-_.]?on[-_.]?startup", Pattern.CASE_INSENSITIVE);
    static final Pattern CONDITIONAL_CHECK = Pattern.compile("@ConditionalOnProperty\\s*\\([^)]*(validat|startup-check|check-on|guard|verif)",
            Pattern.CASE_INSENSITIVE);

    static final Path MAIN = Path.of("src/main");
    static final Path TEST = Path.of("src/test");
    static final Path SELF = Path.of("src/test/java/com/forcitis/one/platform/integration/guard/ReadOnlyCheckSwitchGuardTest.java");
    static final List<Path> DEPLOYMENT = List.of(
            Path.of("../tools/ops/ecs/files/one.env.template"),
            Path.of("../docs/ops/infra-runbook.md"));

    static List<Path> textFiles(Path root) throws IOException {
        assertThat(root).as(GuardMessage.of("ADR37-1", "the scan root " + root + " is not a directory", "run the test from the backend directory")).isDirectory();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> Stream.of(".java", ".properties", ".yaml", ".yml", ".xml", ".json")
                            .anyMatch(s -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(s)))
                    .sorted().toList();
        }
    }

    static List<String> lines(Path file, Pattern pattern) throws IOException {
        String text = Files.readString(file);
        if (file.toString().endsWith(".java")) {
            text = JavaCode.withoutComments(text);
        }
        List<String> hits = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                hits.add(file + ":" + (i + 1) + ": " + lines[i].strip());
            }
        }
        return hits;
    }

    @Test
    @DisplayName("ADR37-1 不提供关闭开关：主代码与配置中没有 validate-on-startup 键、属性或记录组件")
    void mainHasNoValidateOnStartupSwitch() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : textFiles(MAIN)) {
            hits.addAll(lines(file, SWITCH));
        }

        assertThat(hits).as(GuardMessage.of("ADR37-1", "main code or configuration has a validate-on-startup switch", "delete the switch from the listed lines; the check runs whenever the connection is configured")).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 不提供关闭开关：只读连接的校验 Bean 不挂在 @ConditionalOnProperty 上")
    void noCheckIsConditionalOnAProperty() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : textFiles(MAIN.resolve("java"))) {
            String code = JavaCode.withoutComments(Files.readString(file));
            if (CONDITIONAL_CHECK.matcher(code.replace("\n", " ")).find()) {
                hits.add(file.toString());
            }
        }

        assertThat(hits).as(GuardMessage.of("ADR37-1", "a read-only connection check is conditional on a property", "remove the @ConditionalOnProperty from the listed class so the check always runs")).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 不提供关闭开关：部署模板与运行手册不再列出 *_VALIDATE_ON_STARTUP")
    void deploymentTemplatesHaveNoSwitch() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : DEPLOYMENT) {
            assertThat(file).as(GuardMessage.of("ADR37-1", "the deployment file " + file + " is missing", "restore the file or update DEPLOYMENT in the guard if it moved")).exists();
            hits.addAll(lines(file, SWITCH));
        }

        assertThat(hits).as(GuardMessage.of("ADR37-1", "the deployment template or runbook still lists a VALIDATE_ON_STARTUP variable", "delete the variable from tools/ops/ecs/files/one.env.template and docs/ops/infra-runbook.md")).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 不提供关闭开关：测试不再设置已删除的键（设置了也不生效，只会让人以为校验被关掉）")
    void testsDoNotSetTheRemovedKey() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : textFiles(TEST)) {
            if (!file.normalize().equals(SELF.normalize())) {
                hits.addAll(lines(file, SWITCH));
            }
        }

        assertThat(hits).as(GuardMessage.of("ADR37-1", "a test sets the removed validate-on-startup key", "delete the key from the listed test; it has no effect and only suggests the check is off")).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 开关守护自检：键名的三种写法都被识别，注释中的提及不算")
    void detectorRecognisesEverySpelling() {
        assertThat(SWITCH.matcher("forcitis.platform.integration.legacy.validate-on-startup=true").find()).isTrue();
        assertThat(SWITCH.matcher("INTEGRATION_SEEYON_VALIDATE_ON_STARTUP=true").find()).isTrue();
        assertThat(SWITCH.matcher("@DefaultValue(\"false\") boolean validateOnStartup,").find()).isTrue();
        assertThat(SWITCH.matcher(JavaCode.withoutComments("// validateOnStartup was removed\nint x;")).find()).isFalse();
        assertThat(CONDITIONAL_CHECK.matcher("@ConditionalOnProperty(prefix = \"forcitis.platform.integration.legacy\", name = \"validate-on-startup\", havingValue = \"true\")").find())
                .isTrue();
        assertThat(CONDITIONAL_CHECK.matcher("@ConditionalOnProperty(prefix = \"forcitis.platform.integration.legacy\", name = \"enabled\", havingValue = \"true\")").find())
                .isFalse();
    }
}
