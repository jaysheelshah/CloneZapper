package com.clonezapper.cli;

import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.ScanRun;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.List;

@Component
@Command(name = "history", description = "List all past scan runs with their status and statistics.", mixinStandardHelpOptions = true)
public class HistoryCommand implements Runnable {

    private final ScanRepository scanRepository;
    private final FileRepository fileRepository;
    private final DuplicateGroupRepository groupRepository;

    public HistoryCommand(ScanRepository scanRepository,
                          FileRepository fileRepository,
                          DuplicateGroupRepository groupRepository) {
        this.scanRepository = scanRepository;
        this.fileRepository = fileRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public void run() {
        List<ScanRun> runs = scanRepository.findAll();
        if (runs.isEmpty()) {
            System.out.println("No scan runs found.");
            return;
        }

        System.out.println();
        System.out.printf("  %-6s  %-30s  %-12s  %8s  %8s%n",
            "ID", "Label", "Phase", "Files", "Groups");
        System.out.println("  " + "─".repeat(70));

        for (ScanRun run : runs) {
            long files  = fileRepository.countByScanId(run.getId());
            long groups = groupRepository.countByScanId(run.getId());
            System.out.printf("  %-6d  %-30s  %-12s  %8d  %8d%n",
                run.getId(), run.getRunLabel(), run.getPhase(), files, groups);
        }
        System.out.println();
    }
}
