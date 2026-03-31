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
