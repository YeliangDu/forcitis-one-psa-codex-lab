package com.forcitis.one;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Module boundaries: no cycles, cross-module access only through each module's api / events named interfaces. */
class ModularityTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(OneApplication.class).verify();
    }
}
