package com.forcitis.one.platform.integration.infrastructure.transport;

import com.forcitis.one.OneApplication;
import org.springframework.boot.SpringApplication;

/** What the application's main does; the test starts it in a child JVM so that the exit code and the output are the real ones. */
public final class MigrateOnlyLauncher {
    private MigrateOnlyLauncher() {
    }

    public static void main(String[] args) {
        SpringApplication.run(OneApplication.class, args);
    }
}
