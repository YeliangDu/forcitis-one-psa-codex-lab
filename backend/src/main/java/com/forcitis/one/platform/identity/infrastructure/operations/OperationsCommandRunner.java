package com.forcitis.one.platform.identity.infrastructure.operations;

import com.forcitis.one.platform.identity.application.OperationsCommands;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Starts only with the profile identity-operations (see application-identity-operations.properties: loopback-only web part, no jobs), runs the
 * one command given on the command line and ends the process with its exit code. Events the command published stay in the event
 * publication registry when their listeners did not finish: the running application delivers them again.
 */
@Component
@Profile("identity-operations")
class OperationsCommandRunner implements ApplicationRunner {

    private final OperationsCommands commands;
    private final ProcessExit exit;

    OperationsCommandRunner(OperationsCommands commands, ConfigurableApplicationContext context, ObjectProvider<ProcessExit> exit) {
        this.commands = commands;
        this.exit = exit.getIfAvailable(() -> code -> System.exit(SpringApplication.exit(context, () -> code)));
    }

    @Override
    public void run(ApplicationArguments arguments) {
        int code = commands.run(arguments, new PrintStream(new FileOutputStream(FileDescriptor.out), true),
                new PrintStream(new FileOutputStream(FileDescriptor.err), true));
        exit.exit(code);
    }
}
