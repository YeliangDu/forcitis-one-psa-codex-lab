package com.forcitis.one.platform.integration.infrastructure.operations;

import com.forcitis.one.platform.integration.application.IntegrationOperationsCommands;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Starts only with the profile integration-operations (application-integration-operations.properties), runs the one command and ends the process with its exit code. */
@Component
@Profile("integration-operations")
class IntegrationOperationsRunner implements ApplicationRunner {

    private final IntegrationOperationsCommands commands;
    private final ConfigurableApplicationContext context;

    IntegrationOperationsRunner(IntegrationOperationsCommands commands, ConfigurableApplicationContext context) {
        this.commands = commands;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int code = commands.run(arguments, new PrintStream(new FileOutputStream(FileDescriptor.out), true),
                new PrintStream(new FileOutputStream(FileDescriptor.err), true));
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
