package com.clonezapper.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "history", description = "List all past scan runs with their status and statistics.", mixinStandardHelpOptions = true)
public class HistoryCommand implements Runnable {

    @Override
    public void run() {
        // TODO: Implement — query all ScanRuns from DB, print table with id, date, file count, phase
        throw new UnsupportedOperationException("history command not yet implemented");
    }
}
