package com.clonezapper.ui;

import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
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

import java.util.Optional;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard — CloneZapper")
public class DashboardView extends VerticalLayout {

    public DashboardView(ScanRepository scanRepository, FileRepository fileRepository) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Dashboard"));

        Optional<ScanRun> latest = scanRepository.findLatest();

        if (latest.isPresent()) {
            ScanRun run = latest.get();
            long fileCount = fileRepository.countByScanId(run.getId());
            long totalBytes = fileRepository.totalBytesByScanId(run.getId());

            add(buildStatCard("Last Run", run.getRunLabel()));
            add(buildStatCard("Phase", run.getPhase()));
            add(buildStatCard("Files Indexed", String.valueOf(fileCount)));
            add(buildStatCard("Total Size", formatBytes(totalBytes)));
            if (run.getCreatedAt() != null) {
                add(buildStatCard("Started", run.getCreatedAt().toString()));
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
