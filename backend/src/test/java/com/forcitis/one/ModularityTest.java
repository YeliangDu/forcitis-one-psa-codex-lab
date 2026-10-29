package com.forcitis.one;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
    @Test
    void modulesRespectBoundaries() {
        try {
            ApplicationModules.of(OneApplication.class).verify();
        } catch (RuntimeException violations) {
            throw new AssertionError(GuardMessage.of("G1",
                    "modules break the Modulith boundaries (cross-module access outside api/events, a cycle, or a dependency not in allowedDependencies): "
                            + violations.getMessage(),
                    "call the other module through its api or events instead, remove the cycle, or declare the dependency in the module's allowedDependencies after design review"),
                    violations);
        }
    }
}
