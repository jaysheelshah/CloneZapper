package com.clonezapper.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Command(name = "cleanup", description = "Restore duplicate files from archive to their original locations (undo stage).", mixinStandardHelpOptions = true)
public class CleanupCommand implements Runnable {

    @Parameters(description = "Run ID to restore (omit with --all for all runs)", arity = "0..1")
    private Long runId;

    @Option(names = {"--all"}, description = "Restore all staged runs")
    private boolean all;

    @Override
    public void run() {
        // TODO: Implement — call ExecuteStage.cleanup(), move files back, mark actions cleaned
        throw new UnsupportedOperationException("cleanup command not yet implemented");
    }
}
