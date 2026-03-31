package com.clonezapper.cli;

import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ExecuteStage;
import com.clonezapper.model.ScanRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Optional;

@Component
@Command(name = "stage", description = "Move duplicate files to the archive staging area (reversible).", mixinStandardHelpOptions = true)
public class StageCommand implements Runnable {

    @Parameters(description = "Run ID to stage", arity = "1")
    private long runId;

    @Option(names = {"--archive"}, description = "Archive root directory (default: ${DEFAULT-VALUE})")
    private String archiveRoot;

    @Option(names = {"--dry-run"}, description = "Print what would be staged without moving any files")
    private boolean dryRun;

    private final ExecuteStage executeStage;
    private final ScanRepository scanRepository;
    private final String defaultArchiveRoot;

    public StageCommand(ExecuteStage executeStage,
                        ScanRepository scanRepository,
                        @Value("${clonezapper.archive.root}") String defaultArchiveRoot) {
        this.executeStage = executeStage;
        this.scanRepository = scanRepository;
        this.defaultArchiveRoot = defaultArchiveRoot;
    }

    @Override
    public void run() {
        Optional<ScanRun> runOpt = scanRepository.findById(runId);
        if (runOpt.isEmpty()) {
            System.err.println("Run ID " + runId + " not found.");
            return;
        }
        String target = archiveRoot != null ? archiveRoot : defaultArchiveRoot;

        if (dryRun) {
            System.out.println("Dry run — no files will be moved.");
            System.out.println("Archive root: " + target);
            System.out.println();
            java.util.List<String> lines = executeStage.preview(runId, target);
            if (lines.isEmpty()) {
                System.out.println("  No files would be staged (no duplicate groups found).");
            } else {
                System.out.printf("  %d file(s) would be moved:%n", lines.size());
                lines.forEach(l -> System.out.println("  " + l));
            }
            return;
        }

        System.out.println("Staging duplicates for run " + runId + " → " + target);
        executeStage.execute(runId, target);
        System.out.println("Done. Use 'cleanup " + runId + "' to undo, or 'purge " + runId + "' to confirm permanently.");
    }
}
