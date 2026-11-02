package com.forcitis.one.organization.workforce.infrastructure.operations;

import com.forcitis.one.organization.workforce.application.WorkforceOperationsCommands;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Starts only with the profile workforce-operations (see application-workforce-operations.properties: no listening port, no jobs), runs the one
 * command given on the command line and ends the process with its exit code. It is workforce's own runner because identity's belongs to the profile
 * identity-operations and workforce may not use identity's classes.
 */
@Component
@Profile("workforce-operations")
class WorkforceOperationsRunner implements ApplicationRunner {

    private final WorkforceOperationsCommands commands;
    private final ConfigurableApplicationContext context;

    WorkforceOperationsRunner(WorkforceOperationsCommands commands, ConfigurableApplicationContext context) {
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
