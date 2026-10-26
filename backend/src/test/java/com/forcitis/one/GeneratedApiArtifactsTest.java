package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.GeneratedApiArtifacts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedApiArtifactsTest {
    @TempDir
    Path api;

    @Test
    @DisplayName("GOV-1023-QUA-02：缺失或空生成物报生成命令，四件齐备才允许读取")
    void missingOrEmptyArtifactsFailWithTheGenerationCommand() throws Exception {
        for (String file : List.of("openapi.json", "src/generated/schema.ts", "src/generated/validators.ts", "src/generated/operations.ts")) {
            assertThatThrownBy(() -> GeneratedApiArtifacts.require(api)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(file).hasMessageContaining("bash scripts/generate-api.sh");
            Path path = api.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(path, "");
            assertThatThrownBy(() -> GeneratedApiArtifacts.require(api)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(file);
            Files.writeString(path, "generated");
        }
        assertThatCode(() -> GeneratedApiArtifacts.require(api)).doesNotThrowAnyException();
    }
}
