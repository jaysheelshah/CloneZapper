package com.clonezapper.cli;

import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ExecuteStage;
import com.clonezapper.model.ScanRun;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Component
@Command(name = "cleanup", description = "Restore duplicate files from archive to their original locations (undo stage).", mixinStandardHelpOptions = true)
public class CleanupCommand implements Runnable {

    @Parameters(description = "Run ID to restore", arity = "0..1")
    private Long runId;

    @Option(names = {"--all"}, description = "Restore all staged runs")
    private boolean all;

    private final ExecuteStage executeStage;
    private final ScanRepository scanRepository;

    public CleanupCommand(ExecuteStage executeStage, ScanRepository scanRepository) {
        this.executeStage = executeStage;
        this.scanRepository = scanRepository;
    }

    @Override
    public void run() {
        if (all) {
            List<Long> ids = scanRepository.findAll().stream()
                .map(ScanRun::getId)
                .toList();
            ids.forEach(id -> {
                System.out.println("Restoring run " + id + "...");
                executeStage.cleanup(id);
            });
            System.out.println("All runs restored.");
        } else if (runId != null) {
            System.out.println("Restoring run " + runId + "...");
            executeStage.cleanup(runId);
            System.out.println("Done.");
        } else {
            System.err.println("Specify a run ID or --all.");
        }
    }
}
