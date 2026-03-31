package com.clonezapper.ui;

import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ClusterStage;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.service.ScanSettings;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;
import java.util.Optional;

/**
 * Per-pair review UI for low-confidence duplicate candidates.
 * Shows groups whose confidence is below the auto-queue threshold
 * (see {@link ClusterStage#DEFAULT_CONFIDENCE_THRESHOLD}).
 * Actions:
 *   Confirm — the match is accepted; the group remains in the DB and will
 *             appear in ResultsView for staging.
 *   Dismiss — the match is rejected; the group is deleted from the DB.
 */
@Route(value = "review", layout = MainLayout.class)
@PageTitle("Review Queue — CloneZapper")
public class ReviewQueueView extends VerticalLayout {

    public ReviewQueueView(ScanRepository scanRepository,
                           DuplicateGroupRepository groupRepository,
                           FileRepository fileRepository,
                           ScanSettings scanSettings) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Review Queue"));

        Optional<ScanRun> latest = scanRepository.findLatest();
        if (latest.isEmpty()) {
            addSecondary("No scan runs found. Run a scan first.");
            return;
        }

        ScanRun run = latest.get();
        List<DuplicateGroup> groups = groupRepository.findReviewQueueByScanId(
            run.getId(), scanSettings.getConfidenceThreshold());

        Paragraph info = new Paragraph(
            "Run: " + run.getRunLabel() + "  |  Phase: " + run.getPhase());
        info.addClassNames(LumoUtility.TextColor.SECONDARY);
        add(info);

        if (groups.isEmpty()) {
            addSecondary("No items in the review queue — all matches were high-confidence.");
            Button toResults = new Button("View confirmed results",
                e -> UI.getCurrent().navigate(ResultsView.class));
            toResults.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            add(toResults);
            return;
        }

        Paragraph count = new Paragraph(
            groups.size() + " pair(s) need your review before staging.");
        count.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
        add(count);

        // Render one card per group — rebuilds on each action via page reload
        for (int i = 0; i < groups.size(); i++) {
            add(buildCard(i + 1, groups.get(i), groupRepository, fileRepository));
        }
    }

    private VerticalLayout buildCard(int index, DuplicateGroup group,
                                     DuplicateGroupRepository groupRepository,
                                     FileRepository fileRepository) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
            .set("border", "1px solid var(--lumo-contrast-20pct)")
            .set("border-radius", "var(--lumo-border-radius-m)");

        // Heading
        String heading = String.format("Pair %d — %s  |  similarity: %.0f%%",
            index, group.getStrategy(), group.getConfidence() * 100);
        add(new H3(heading));
        card.add(new H3(heading));

        // File detail rows
        List<DuplicateMember> members = group.getMembers();
        for (DuplicateMember m : members) {
            Optional<ScannedFile> fileOpt = fileRepository.findById(m.getFileId());
            if (fileOpt.isEmpty()) continue;
            ScannedFile f = fileOpt.get();

            boolean isCanonical = f.getId().equals(group.getCanonicalFileId());
            String role = isCanonical ? "★ keep" : "candidate";

            HorizontalLayout row = new HorizontalLayout();
            row.setDefaultVerticalComponentAlignment(Alignment.CENTER);

            Span roleSpan = new Span(role);
            roleSpan.addClassNames(LumoUtility.FontWeight.SEMIBOLD);
            if (isCanonical) roleSpan.addClassNames(LumoUtility.TextColor.SUCCESS);
            else             roleSpan.addClassNames(LumoUtility.TextColor.SECONDARY);
            roleSpan.getStyle().set("min-width", "80px");

            Span pathSpan = new Span(f.getPath());
            pathSpan.getStyle().set("word-break", "break-all");

            Span sizeSpan = new Span(formatBytes(f.getSize()));
            sizeSpan.addClassNames(LumoUtility.TextColor.SECONDARY);
            sizeSpan.getStyle().set("min-width", "80px").set("text-align", "right");

            row.add(roleSpan, pathSpan, sizeSpan);
            row.setFlexGrow(1, pathSpan);
            card.add(row);
        }

        // Action buttons
        Button confirm = new Button("✓ Confirm as duplicate");
        confirm.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        confirm.addClickListener(e -> {
            Notification ok = new Notification(
                "Confirmed. Group will appear in Results for staging.",
                3000, Notification.Position.BOTTOM_START);
            ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            ok.open();
            UI.getCurrent().navigate(ResultsView.class);
        });

        Button dismiss = new Button("✗ Not a duplicate");
        dismiss.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        dismiss.addClickListener(e -> {
            groupRepository.deleteById(group.getId());
            Notification msg = new Notification(
                "Dismissed. Group removed.", 3000, Notification.Position.BOTTOM_START);
            msg.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            msg.open();
            // Reload the same view to reflect removal
            UI.getCurrent().getPage().reload();
        });

        card.add(new HorizontalLayout(confirm, dismiss));
        return card;
    }

    private void addSecondary(String text) {
        Paragraph p = new Paragraph(text);
        p.addClassNames(LumoUtility.TextColor.SECONDARY);
        add(p);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
