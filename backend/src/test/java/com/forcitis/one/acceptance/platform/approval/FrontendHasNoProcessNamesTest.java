package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IMP-1023-NX5-01 完成标准第 1 条：前端不再自带流程显示名表。REG-4 裁定第 3 项第 1、3 条：平台不列业务流程，显示名是流程定义数据。
 * 判据与实现命名无关：前端生产代码（不含测试文件与测试夹具）里不出现业务流程键字面量，也不出现该流程的显示名。
 */
class FrontendHasNoProcessNamesTest {

    static final Path FRONTEND = Path.of("../frontend");
    static final List<String> BUSINESS_PROCESS_WORDS = List.of("cost_timesheet_approval", "工时单审批", "工时审批");

    static boolean production(Path file) {
        String path = FRONTEND.relativize(file).toString().replace('\\', '/');
        String name = file.getFileName().toString();
        return (name.endsWith(".ts") || name.endsWith(".tsx")) && !path.contains("node_modules/") && !path.contains("/dist/")
                && !path.contains("/testing/") && !name.contains(".test.") && !name.contains(".spec.") && !name.endsWith(".d.ts")
                && !path.contains("/generated/");
    }

    static List<String> hits(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(FrontendHasNoProcessNamesTest::production).flatMap(file -> {
                try {
                    String text = Files.readString(file);
                    return BUSINESS_PROCESS_WORDS.stream().filter(text::contains).map(word -> FRONTEND.relativize(file) + " 含 " + word);
                } catch (IOException unreadable) {
                    throw new AssertionError(unreadable);
                }
            }).toList();
        }
    }

    @Test
    @DisplayName("IMP-1023-NX5-01 完成标准第 1 条：前端生产代码不含业务流程键与流程显示名")
    void frontendCarriesNoProcessKeysOrNames() throws IOException {
        assertThat(Files.isDirectory(FRONTEND.resolve("packages"))).as("前端目录在 ../frontend").isTrue();

        assertThat(hits(FRONTEND)).isEmpty();
    }
}
