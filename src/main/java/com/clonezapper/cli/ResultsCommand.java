package com.clonezapper.cli;

import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.Optional;

@Component
@Command(name = "results", description = "Print duplicate groups from the latest (or specified) scan.", mixinStandardHelpOptions = true)
public class ResultsCommand implements Runnable {

    @Option(names = {"--run-id"}, description = "Specific run ID (defaults to latest)")
    private Long runId;

    @Option(names = {"--output", "-o"}, description = "Output format: text, json, csv (default: text)", defaultValue = "text")
    private String outputFormat;

    private final ScanRepository scanRepository;
    private final DuplicateGroupRepository groupRepository;
    private final FileRepository fileRepository;

    public ResultsCommand(ScanRepository scanRepository,
                          DuplicateGroupRepository groupRepository,
                          FileRepository fileRepository) {
        this.scanRepository = scanRepository;
        this.groupRepository = groupRepository;
        this.fileRepository = fileRepository;
    }

    @Override
    public void run() {
        Optional<ScanRun> runOpt = runId != null
            ? scanRepository.findById(runId)
            : scanRepository.findLatest();

        if (runOpt.isEmpty()) {
            System.out.println("No scan runs found. Run 'clonezapper scan <path>' first.");
            return;
        }
        ScanRun run = runOpt.get();
        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());

        switch (outputFormat.toLowerCase()) {
            case "json"  -> outputJson(run, groups);
            case "csv"   -> outputCsv(run, groups);
            default      -> outputText(run, groups);
        }
    }

    private void outputText(ScanRun run, List<DuplicateGroup> groups) {
        System.out.printf("%nRun %-6d  %s  [%s]%n", run.getId(), run.getRunLabel(), run.getPhase());
        System.out.println("─".repeat(72));
        if (groups.isEmpty()) {
            System.out.println("  No duplicate groups found.");
            return;
        }
        for (int i = 0; i < groups.size(); i++) {
            DuplicateGroup g = groups.get(i);
            System.out.printf("  Group %-3d  strategy=%-12s  confidence=%.0f%%%n",
                i + 1, g.getStrategy(), g.getConfidence() * 100);
            for (DuplicateMember m : g.getMembers()) {
                ScannedFile f = fileRepository.findById(m.getFileId()).orElse(null);
                if (f == null) continue;
                String role = f.getId().equals(g.getCanonicalFileId()) ? "★ keep     " : "  duplicate";
                System.out.printf("    %s  %s  (%s)%n", role, f.getPath(), formatBytes(f.getSize()));
            }
            System.out.println();
        }
        System.out.printf("  %d group(s) found.%n", groups.size());
    }

    private void outputJson(ScanRun run, List<DuplicateGroup> groups) {
        System.out.println("{");
        System.out.printf("  \"runId\": %d,%n", run.getId());
        System.out.printf("  \"runLabel\": \"%s\",%n", run.getRunLabel());
        System.out.printf("  \"phase\": \"%s\",%n", run.getPhase());
        System.out.printf("  \"groupCount\": %d,%n", groups.size());
        System.out.println("  \"groups\": [");
        for (int i = 0; i < groups.size(); i++) {
            DuplicateGroup g = groups.get(i);
            System.out.println("    {");
            System.out.printf("      \"strategy\": \"%s\",%n", g.getStrategy());
            System.out.printf("      \"confidence\": %.4f,%n", g.getConfidence());
            System.out.println("      \"files\": [");
            List<DuplicateMember> members = g.getMembers();
            for (int j = 0; j < members.size(); j++) {
                ScannedFile f = fileRepository.findById(members.get(j).getFileId()).orElse(null);
                if (f == null) continue;
                boolean isCanonical = f.getId().equals(g.getCanonicalFileId());
                System.out.printf("        {\"role\": \"%s\", \"path\": \"%s\", \"size\": %d}%s%n",
                    isCanonical ? "canonical" : "duplicate",
                    f.getPath().replace("\\", "\\\\"),
                    f.getSize(),
                    j < members.size() - 1 ? "," : "");
            }
            System.out.println("      ]");
            System.out.print("    }");
            System.out.println(i < groups.size() - 1 ? "," : "");
        }
        System.out.println("  ]");
        System.out.println("}");
    }

    private void outputCsv(ScanRun run, List<DuplicateGroup> groups) {
        System.out.println("RunId,RunLabel,GroupIndex,Strategy,Confidence,Role,Path,Size");
        for (int i = 0; i < groups.size(); i++) {
            DuplicateGroup g = groups.get(i);
            for (DuplicateMember m : g.getMembers()) {
                ScannedFile f = fileRepository.findById(m.getFileId()).orElse(null);
                if (f == null) continue;
                String role = f.getId().equals(g.getCanonicalFileId()) ? "canonical" : "duplicate";
                System.out.printf("%d,\"%s\",%d,\"%s\",%.4f,\"%s\",\"%s\",%d%n",
                    run.getId(), run.getRunLabel(), i + 1, g.getStrategy(),
                    g.getConfidence(), role,
                    f.getPath().replace("\"", "\"\""),
                    f.getSize());
            }
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
