package com.clonezapper.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Command(name = "stage", description = "Move duplicate files to the archive staging area (reversible).", mixinStandardHelpOptions = true)
public class StageCommand implements Runnable {

    @Parameters(description = "Run ID to stage", arity = "1")
    private long runId;

    @Option(names = {"--archive"}, description = "Archive root directory", required = true)
    private String archiveRoot;

    @Override
    public void run() {
        // TODO: Implement — call ExecuteStage with confirmed Auto Queue groups
        throw new UnsupportedOperationException("stage command not yet implemented");
    }
}
