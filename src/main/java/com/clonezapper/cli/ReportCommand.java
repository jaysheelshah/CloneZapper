package com.clonezapper.cli;

import com.clonezapper.db.ActionRepository;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.Action;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Optional;

@Component
@Command(name = "report", description = "Print the session report for a completed run.", mixinStandardHelpOptions = true)
public class ReportCommand implements Runnable {

    @Parameters(description = "Run ID (defaults to latest)", arity = "0..1")
    private Long runId;

    private final ScanRepository scanRepository;
    private final FileRepository fileRepository;
    private final DuplicateGroupRepository groupRepository;
    private final ActionRepository actionRepository;

    public ReportCommand(ScanRepository scanRepository,
                         FileRepository fileRepository,
                         DuplicateGroupRepository groupRepository,
                         ActionRepository actionRepository) {
        this.scanRepository = scanRepository;
        this.fileRepository = fileRepository;
        this.groupRepository = groupRepository;
        this.actionRepository = actionRepository;
    }

    @Override
    public void run() {
        Optional<ScanRun> runOpt = runId != null
            ? scanRepository.findById(runId)
            : scanRepository.findLatest();

        if (runOpt.isEmpty()) {
            System.out.println("No scan runs found.");
            return;
        }
        ScanRun run = runOpt.get();

        long fileCount    = fileRepository.countByScanId(run.getId());
        long totalBytes   = fileRepository.totalBytesByScanId(run.getId());
        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        List<Action> actions = actionRepository.findByScanId(run.getId());

        // Compute recoverable space: sum sizes of non-canonical members
        long recoverableBytes = 0;
        long explicitCopies   = 0;
        for (DuplicateGroup g : groups) {
            for (DuplicateMember m : g.getMembers()) {
                if (!m.getFileId().equals(g.getCanonicalFileId())) {
                    ScannedFile f = fileRepository.findById(m.getFileId()).orElse(null);
                    if (f != null) {
                        recoverableBytes += f.getSize();
                        if ("explicit_copy".equals(f.getCopyHint())) explicitCopies++;
                    }
                }
            }
        }

        long staged = actions.stream().filter(a -> a.getActionType() == Action.Type.MOVE).count();

        String bar = "━".repeat(46);
        System.out.println(bar);
        System.out.println("  CloneZapper — Session Report");
        System.out.println(bar);
        System.out.printf("  Run           %s  [%s]%n", run.getRunLabel(), run.getPhase());
        if (run.getCreatedAt() != null) {
            System.out.printf("  Started       %s%n", run.getCreatedAt());
        }
        System.out.println();
        System.out.printf("  Scanned       %,d files   (%s total)%n", fileCount, formatBytes(totalBytes));
        System.out.println();
        System.out.printf("  Duplicate groups       %d%n", groups.size());
        System.out.printf("    Exact matches        %d     %s recoverable%n",
            groups.size(), formatBytes(recoverableBytes));
        if (explicitCopies > 0) {
            System.out.printf("    Explicit copies      %d     (copy_hint tagged)%n", explicitCopies);
        }
        System.out.println();
        if (staged > 0) {
            System.out.printf("  Actions taken%n");
            System.out.printf("    Staged (moved)       %d files%n", staged);
            if (run.getArchiveRoot() != null) {
                // OSC 8 terminal hyperlink for clickable folder path
                String path = run.getArchiveRoot();
                String link = "\u001b]8;;" + "file:///" + path.replace('\\', '/') + "\u001b\\\u001b[4m" + path + "\u001b[0m\u001b]8;;\u001b\\";
                System.out.printf("    Archive              %s%n", link);
            }
        } else {
            System.out.println("  No actions taken yet.  Run 'stage " + run.getId() + "' to move duplicates.");
        }
        System.out.println(bar);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
