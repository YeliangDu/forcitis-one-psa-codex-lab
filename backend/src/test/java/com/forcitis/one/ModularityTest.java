package com.forcitis.one;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(OneApplication.class).verify();
    }
}
