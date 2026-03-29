package com.clonezapper.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "results", description = "Print duplicate groups from the latest scan.", mixinStandardHelpOptions = true)
public class ResultsCommand implements Runnable {

    @Option(names = {"--run-id"}, description = "Specific run ID (defaults to latest)")
    private Long runId;

    @Override
    public void run() {
        // TODO: Implement — query DuplicateGroups from DB and print formatted output
        throw new UnsupportedOperationException("results command not yet implemented");
    }
}
