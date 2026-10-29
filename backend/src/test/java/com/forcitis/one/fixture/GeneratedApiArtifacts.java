package com.forcitis.one.fixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Gives existing acceptance readers an actionable failure without changing their tests. */
public final class GeneratedApiArtifacts implements BeforeAllCallback {
    private static final Set<String> READERS = Set.of("ApprovalIdContractTest", "AuditIdContractTest",
            "EmployeeIdContractTest", "ScalarIdContractTest", "UserIdContractTest", "LegalEntityAccountingCodeMovedIT",
            "OldTodoRemovalGuardTest", "K3b4ContractTest", "LegacyNumberSourceIT", "OpenApiUuidParametersIT");

    @Override
    public void beforeAll(ExtensionContext context) throws IOException {
        if (READERS.contains(context.getRequiredTestClass().getSimpleName())) {
            require(Path.of("..", "frontend", "packages", "api"));
        }
    }

    public static void require(Path api) throws IOException {
        for (String file : List.of("openapi.json", "src/generated/schema.ts", "src/generated/validators.ts", "src/generated/operations.ts")) {
            Path path = api.resolve(file);
            if (!Files.isRegularFile(path) || Files.size(path) == 0) {
                throw new IllegalStateException("缺少生成产物 " + path
                        + "；请在仓库根运行 bash scripts/codegen/generate_api.sh（需 Docker 和已安装的前端依赖；离线 Maven 加 -o）");
            }
        }
    }
}
