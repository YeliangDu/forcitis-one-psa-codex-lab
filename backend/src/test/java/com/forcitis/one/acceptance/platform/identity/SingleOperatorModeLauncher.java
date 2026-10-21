package com.forcitis.one.acceptance.platform.identity;

import com.forcitis.one.OneApplication;
import org.springframework.boot.SpringApplication;

/** What the application's main does; started in a child JVM so that the exit code, the output and the open ports are the real ones. */
public final class SingleOperatorModeLauncher {
    private SingleOperatorModeLauncher() {
    }

    public static void main(String[] args) {
        SpringApplication.run(OneApplication.class, args);
    }
}
