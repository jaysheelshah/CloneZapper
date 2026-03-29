package com.clonezapper.cli;

import com.clonezapper.CloneZapperApp;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Bridges Spring Boot startup with picocli command execution.
 * If CLI arguments are provided, the matching command runs and the web server
 * continues serving (both interfaces remain available simultaneously).
 */
@Component
public class CliRunner implements CommandLineRunner {

    private final CommandLine.IFactory factory;
    private final CloneZapperApp app;

    public CliRunner(CommandLine.IFactory factory, CloneZapperApp app) {
        this.factory = factory;
        this.app = app;
    }

    @Override
    public void run(String... args) {
        if (args.length > 0) {
            new CommandLine(app, factory).execute(args);
        }
    }
}
