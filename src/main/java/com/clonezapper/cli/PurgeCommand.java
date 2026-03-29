package com.clonezapper.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Command(name = "purge", description = "Permanently delete the archive for a run. This cannot be undone.", mixinStandardHelpOptions = true)
public class PurgeCommand implements Runnable {

    @Parameters(description = "Run ID to purge", arity = "0..1")
    private Long runId;

    @Option(names = {"--all"}, description = "Purge all staged runs")
    private boolean all;

    @Override
    public void run() {
        // TODO: Implement — show summary, require 'yes' confirmation, call ExecuteStage.purge()
        throw new UnsupportedOperationException("purge command not yet implemented");
    }
}
