package com.clonezapper.cli;

import com.clonezapper.db.ActionRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ExecuteStage;
import com.clonezapper.model.ScanRun;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Scanner;

@Component
@Command(name = "purge", description = "Permanently delete the archive for a run. This cannot be undone.", mixinStandardHelpOptions = true)
public class PurgeCommand implements Runnable {

    @Parameters(description = "Run ID to purge", arity = "0..1")
    private Long runId;

    @Option(names = {"--all"}, description = "Purge all staged runs")
    private boolean all;

    @Option(names = {"--yes"}, description = "Skip confirmation prompt")
    private boolean yes;

    private final ExecuteStage executeStage;
    private final ScanRepository scanRepository;
    private final ActionRepository actionRepository;

    public PurgeCommand(ExecuteStage executeStage,
                        ScanRepository scanRepository,
                        ActionRepository actionRepository) {
        this.executeStage = executeStage;
        this.scanRepository = scanRepository;
        this.actionRepository = actionRepository;
    }

    @Override
    public void run() {
        List<ScanRun> targets;
        if (all) {
            targets = scanRepository.findAll();
        } else if (runId != null) {
            targets = scanRepository.findById(runId).map(List::of).orElse(List.of());
            if (targets.isEmpty()) {
                System.err.println("Run ID " + runId + " not found.");
                return;
            }
        } else {
            System.err.println("Specify a run ID or --all.");
            return;
        }

        System.out.println("⚠  The following run archives will be PERMANENTLY deleted:");
        for (ScanRun run : targets) {
            long count = actionRepository.countByScanId(run.getId());
            System.out.printf("   run %-6d  %s  (%d staged file(s))%n",
                run.getId(), run.getRunLabel(), count);
        }

        if (!yes && !confirm()) {
            System.out.println("Cancelled.");
            return;
        }

        for (ScanRun run : targets) {
            System.out.println("Purging run " + run.getId() + "...");
            executeStage.purge(run.getId());
        }
        System.out.println("Done. Archives permanently deleted.");
    }

    private boolean confirm() {
        System.out.print("Type 'yes' to confirm: ");
        try (Scanner in = new Scanner(System.in)) {
            return "yes".equalsIgnoreCase(in.nextLine().trim());
        }
    }
}
