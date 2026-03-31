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
        System.out.println("Staging duplicates for run " + runId + " → " + target);
        executeStage.execute(runId, target);
        System.out.println("Done. Use 'cleanup " + runId + "' to undo, or 'purge " + runId + "' to confirm permanently.");
    }
}
