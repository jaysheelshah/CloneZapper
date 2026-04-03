package com.clonezapper.ui;

import com.clonezapper.db.ActionRepository;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.Action;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard — CloneZapper")
public class DashboardView extends VerticalLayout {

    public DashboardView(ScanRepository scanRepository,
                         FileRepository fileRepository,
                         DuplicateGroupRepository groupRepository,
                         ActionRepository actionRepository) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Dashboard"));

        // Reconnect banner — shown when a scan was in-progress the last time the user was here
        scanRepository.findInProgress().ifPresent(inProgress ->
            add(buildReconnectBanner(inProgress)));

        Optional<ScanRun> latest = scanRepository.findLatest();

        if (latest.isPresent()) {
            ScanRun run = latest.get();
            long fileCount  = fileRepository.countByScanId(run.getId());
            long totalBytes = fileRepository.totalBytesByScanId(run.getId());

            add(buildStatCard("Last Run", run.getRunLabel()));
            add(buildStatCard("Phase", run.getPhase()));
            add(buildStatCard("Files Indexed", String.format("%,d", fileCount)));
            add(buildStatCard("Total Size", formatBytes(totalBytes)));
            if (run.getCreatedAt() != null) {
                add(buildStatCard("Started", run.getCreatedAt().toString()));
            }

            // Duplicate statistics — only meaningful once clustering is done
            List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
            if (!groups.isEmpty()) {
                add(new Hr());

                long dupFileCount    = 0;
                long recoverableBytes = 0;
                for (DuplicateGroup g : groups) {
                    for (DuplicateMember m : g.getMembers()) {
                        if (!m.getFileId().equals(g.getCanonicalFileId())) {
                            ScannedFile f = fileRepository.findById(m.getFileId()).orElse(null);
                            if (f != null) {
                                dupFileCount++;
                                recoverableBytes += f.getSize();
                            }
                        }
                    }
                }

                add(buildStatCard("Duplicate Groups", String.valueOf(groups.size())));
                add(buildStatCard("Files to Remove", String.valueOf(dupFileCount)));
                add(buildStatCard("Recoverable Space", formatBytes(recoverableBytes)));

                // Action / staging statistics
                List<Action> actions = actionRepository.findByScanId(run.getId());
                long staged = actions.stream()
                    .filter(a -> a.getActionType() == Action.Type.MOVE)
                    .count();
                if (staged > 0) {
                    add(new Hr());
                    add(buildStatCard("Staged (moved)", staged + " file(s)"));
                    if (run.getArchiveRoot() != null) {
                        add(buildStatCard("Archive", run.getArchiveRoot()));
                    }
                }

                Button viewResults = new Button("View Results",
                    e -> UI.getCurrent().navigate(ResultsView.class));
                viewResults.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
                add(viewResults);
            }
        } else {
            Paragraph noRuns = new Paragraph("No scans yet. Start your first scan below.");
            noRuns.addClassNames(LumoUtility.TextColor.SECONDARY);
            add(noRuns);
        }

        Button startScan = new Button("Start New Scan", e -> UI.getCurrent().navigate(ScanView.class));
        startScan.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(startScan);
    }

    // ── Reconnect banner ──────────────────────────────────────────────────────

    /**
     * Returns a banner describing the state of an in-progress scan based on
     * how long ago the heartbeat was written.
     * <p>
     * Thresholds:
     *   < 30 s   → scan is actively running (machine was sleeping, now awake)
     *   30 s–5 m → probably resuming after sleep or a brief disruption
     *   > 5 m    → scan appears interrupted (JVM likely crashed or killed)
     */
    private Div buildReconnectBanner(ScanRun run) {
        HeartbeatStatus status = evaluateHeartbeat(run.getLastHeartbeat());

        String age = run.getLastHeartbeat() != null
            ? formatAge(Duration.between(run.getLastHeartbeat(), LocalDateTime.now()))
            : "unknown";

        String message = switch (status) {
            case RUNNING   -> "Scan is running — last heartbeat " + age + " ago.";
            case RESUMING  -> "Scan was running — last heartbeat " + age + " ago. " +
                              "It may be resuming after sleep.";
            case INTERRUPTED -> "Scan appears interrupted — last heartbeat " + age + " ago. " +
                                "The process may have been killed. " +
                                "You can start a new scan when ready.";
            case UNKNOWN   -> "A scan is in progress (no heartbeat recorded).";
        };

        Div banner = new Div(new Span(message));
        banner.getStyle()
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("margin-bottom", "var(--lumo-space-m)");

        switch (status) {
            case RUNNING   -> banner.getStyle().set("background", "var(--lumo-success-color-10pct)")
                                               .set("color", "var(--lumo-success-text-color)");
            case RESUMING  -> banner.getStyle().set("background", "var(--lumo-warning-color-10pct)")
                                               .set("color", "var(--lumo-warning-text-color)");
            case INTERRUPTED, UNKNOWN ->
                              banner.getStyle().set("background", "var(--lumo-error-color-10pct)")
                                               .set("color", "var(--lumo-error-text-color)");
        }

        return banner;
    }

    /** Evaluates the heartbeat timestamp against fixed staleness thresholds. */
    public static HeartbeatStatus evaluateHeartbeat(LocalDateTime lastHeartbeat) {
        if (lastHeartbeat == null) return HeartbeatStatus.UNKNOWN;
        long seconds = Duration.between(lastHeartbeat, LocalDateTime.now()).toSeconds();
        if (seconds < 30)   return HeartbeatStatus.RUNNING;
        if (seconds < 300)  return HeartbeatStatus.RESUMING;
        return HeartbeatStatus.INTERRUPTED;
    }

    public enum HeartbeatStatus { RUNNING, RESUMING, INTERRUPTED, UNKNOWN }

    private static String formatAge(Duration d) {
        long s = d.toSeconds();
        if (s < 60)   return s + "s";
        if (s < 3600) return (s / 60) + "m";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }

    private HorizontalLayout buildStatCard(String label, String value) {
        Span labelSpan = new Span(label + ": ");
        labelSpan.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
        Span valueSpan = new Span(value);
        HorizontalLayout card = new HorizontalLayout(labelSpan, valueSpan);
        card.setSpacing(false);
        return card;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
