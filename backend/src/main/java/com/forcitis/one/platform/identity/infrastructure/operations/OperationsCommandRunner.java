package com.forcitis.one.platform.identity.infrastructure.operations;

import com.forcitis.one.platform.identity.application.OperationsCommands;
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
 * Starts only with the profile identity-operations (see application-identity-operations.properties: no web server, no jobs), runs the
 * one command given on the command line and ends the process with its exit code.
 */
@Component
@Profile("identity-operations")
class OperationsCommandRunner implements ApplicationRunner {

    private final OperationsCommands commands;
    private final ConfigurableApplicationContext context;

    OperationsCommandRunner(OperationsCommands commands, ConfigurableApplicationContext context) {
        this.commands = commands;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int code = commands.run(arguments, new PrintStream(new FileOutputStream(FileDescriptor.out), true), new PrintStream(new FileOutputStream(FileDescriptor.err), true));
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
