package com.clonezapper.cli;

import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;

@Component
@Command(
    name = "scan",
    description = "Scan one or more paths for duplicate files (incremental by default).",
    mixinStandardHelpOptions = true
)
public class ScanCommand implements Runnable {

    @Parameters(description = "Paths to scan", arity = "1..*")
    private List<String> paths;

    private final UnifiedScanner scanner;

    public ScanCommand(UnifiedScanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public void run() {
        System.out.println("Scanning " + paths.size() + " path(s)...");
        ScanRun run = scanner.startScan(paths);
        System.out.printf("Scan complete. Run ID: %d  Phase: %s%n", run.getId(), run.getPhase());
    }
}
