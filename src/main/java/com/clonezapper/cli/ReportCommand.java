package com.clonezapper.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Component
@Command(name = "report", description = "Print the session report for a completed run.", mixinStandardHelpOptions = true)
public class ReportCommand implements Runnable {

    @Parameters(description = "Run ID (defaults to latest)", arity = "0..1")
    private Long runId;

    @Override
    public void run() {
        // TODO: Implement — query FILES + ACTIONS, compute stats, print formatted report with OSC 8 folder links
        throw new UnsupportedOperationException("report command not yet implemented");
    }
}
