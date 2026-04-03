package com.clonezapper;

import com.clonezapper.cli.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * CloneZapper application entry point.
 *
 * Starts an embedded Spring Boot / Vaadin web server on localhost:8080.
 * CLI commands are also available when the app is run with arguments.
 *
 * Usage:
 *   java -jar clonezapper.jar                    → web UI at http://localhost:8080
 *   java -jar clonezapper.jar scan /path/to/dir  → scan from CLI
 */
@SpringBootApplication
@Command(
    name = "clonezapper",
    description = "Content-aware file deduplication engine",
    subcommands = {
        ScanCommand.class,
        ResultsCommand.class,
        StageCommand.class,
        CleanupCommand.class,
        PurgeCommand.class,
        ReportCommand.class,
        HistoryCommand.class,
        CommandLine.HelpCommand.class
    },
    mixinStandardHelpOptions = true
)
public class CloneZapperApp implements Runnable {

    public static void main(String[] args) {
        // Must be set before AWT is touched — spring.main.headless=false alone
        // is too late because AWT can initialise before Spring's context starts.
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(CloneZapperApp.class, args);
    }

    @Override
    public void run() {
        System.out.println("CloneZapper is running.");
        System.out.println("  Web UI : http://localhost:8080");
        System.out.println("  CLI    : run with --help to see available commands");
    }
}
