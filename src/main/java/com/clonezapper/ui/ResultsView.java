package com.clonezapper.ui;

import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ExecuteStage;
import com.clonezapper.model.ScanRun;
import com.clonezapper.model.preview.GroupPreviewRow;
import com.clonezapper.model.preview.MemberPreviewRow;
import com.clonezapper.model.preview.PreviewSummary;
import com.clonezapper.service.PreviewService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Route(value = "results/:runId?", layout = MainLayout.class)
@PageTitle("Results — CloneZapper")
public class ResultsView extends VerticalLayout implements BeforeEnterObserver {

    private final ScanRepository scanRepository;
    private final ExecuteStage executeStage;
    private final PreviewService previewService;
    private final String defaultArchiveRoot;  // fallback for scans created before archiveRoot was persisted

    private final VerticalLayout content = new VerticalLayout();

    public ResultsView(ScanRepository scanRepository,
                       ExecuteStage executeStage,
                       PreviewService previewService,
                       @Value("${clonezapper.archive.root}") String defaultArchiveRoot) {
        this.scanRepository = scanRepository;
        this.executeStage = executeStage;
        this.previewService = previewService;
        this.defaultArchiveRoot = defaultArchiveRoot;

        setSpacing(true);
        setPadding(true);
        content.setSpacing(true);
        content.setPadding(false);
        add(new H2("Scan Results"), buildRunSelector(), content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String runIdParam = event.getRouteParameters().get("runId").orElse(null);
        ScanRun run = runIdParam != null
            ? scanRepository.findById(Long.parseLong(runIdParam)).orElse(null)
            : scanRepository.findLatest().orElse(null);
        buildContent(run);
    }

    // ── Run selector ─────────────────────────────────────────────────────────

    private Select<ScanRun> buildRunSelector() {
        Select<ScanRun> select = new Select<>();
        select.setLabel("Scan run");
        select.setItemLabelGenerator(r -> r.getRunLabel() + "  [" + r.getPhase() + "]");
        List<ScanRun> runs = scanRepository.findAll();
        select.setItems(runs);
        if (!runs.isEmpty()) select.setValue(runs.getFirst());
        select.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                UI.getCurrent().navigate(ResultsView.class,
                    new RouteParameters("runId", String.valueOf(e.getValue().getId())));
            }
        });
        return select;
    }

    // ── Content builder ───────────────────────────────────────────────────────

    private void buildContent(ScanRun run) {
        content.removeAll();

        if (run == null) {
            Paragraph msg = new Paragraph("No scan results yet. Run a scan first.");
            msg.addClassNames(LumoUtility.TextColor.SECONDARY);
            content.add(msg);
            return;
        }

        PreviewSummary summary = previewService.buildSummary(run.getId());
        List<GroupPreviewRow> groups = previewService.buildGroups(run.getId());

        content.add(buildSummaryBar(summary));

        if (groups.isEmpty()) {
            Paragraph none = new Paragraph("No duplicate groups found in this scan.");
            none.addClassNames(LumoUtility.TextColor.SECONDARY);
            content.add(none);
            return;
        }

        content.add(new Hr());
        content.add(buildActionBar(run, summary));
        content.add(buildGroupGrid(groups));
    }

    // ── Summary bar ───────────────────────────────────────────────────────────

    private Component buildSummaryBar(PreviewSummary summary) {
        VerticalLayout bar = new VerticalLayout();
        bar.setSpacing(false);
        bar.setPadding(false);

        // Row 1 — counts and recoverable space
        HorizontalLayout row1 = new HorizontalLayout();
        row1.setSpacing(true);
        row1.add(
            stat(String.format("%,d", summary.totalFilesScanned()), "files scanned"),
            stat(String.valueOf(summary.totalGroups()), "duplicate groups"),
            stat(formatBytes(summary.reclaimableBytes()), "recoverable"),
            stat(String.format("%.0f%%", summary.avgConfidence() * 100), "avg confidence")
        );

        // Row 2 — queue breakdown and archive
        HorizontalLayout row2 = new HorizontalLayout();
        row2.setSpacing(true);
        row2.add(
            badge(summary.exactGroups() + " exact", "success"),
            badge(summary.nearDupGroups() + " near-dup", "contrast"),
            badge(summary.autoQueueCount() + " auto-queue", "success"),
            badge(summary.reviewQueueCount() + " review", summary.reviewQueueCount() > 0 ? "error" : "contrast")
        );

        if (!summary.archiveRoot().isBlank()) {
            String freeText = summary.archiveFreeBytes() >= 0
                ? formatBytes(summary.archiveFreeBytes()) + " free"
                : "unknown free space";
            Span archiveInfo = new Span("Archive: " + summary.archiveRoot() + "  (" + freeText + ")");
            archiveInfo.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
            row2.add(archiveInfo);
        }

        bar.add(row1, row2);
        return bar;
    }

    // ── Action bar ────────────────────────────────────────────────────────────

    private Component buildActionBar(ScanRun run, PreviewSummary summary) {
        String archiveRoot = run.getArchiveRoot() != null ? run.getArchiveRoot() : defaultArchiveRoot;

        Button archiveButton = new Button("Archive duplicates →");
        archiveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        archiveButton.addClickListener(e -> {
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Archive duplicates?");
            dialog.setText(
                summary.totalDupeCount() + " file(s) will be moved to: " + archiveRoot + "\n" +
                "Recoverable space: " + formatBytes(summary.reclaimableBytes()) + "\n" +
                "Canonical (★ keep) files are untouched. You can restore with Cleanup.");
            dialog.setConfirmText("Archive");
            dialog.setCancelable(true);
            dialog.addConfirmListener(ce -> {
                archiveButton.setEnabled(false);
                try {
                    executeStage.execute(run.getId(), archiveRoot);
                    Notification ok = new Notification(
                        summary.totalDupeCount() + " file(s) archived to " + archiveRoot,
                        4000, Notification.Position.BOTTOM_START);
                    ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    ok.open();
                    UI.getCurrent().navigate(DashboardView.class);
                } catch (Exception ex) {
                    Notification.show("Archive failed: " + ex.getMessage(),
                        5000, Notification.Position.MIDDLE);
                    archiveButton.setEnabled(true);
                }
            });
            dialog.open();
        });

        return archiveButton;
    }

    // ── Group grid ────────────────────────────────────────────────────────────

    private Grid<GroupPreviewRow> buildGroupGrid(List<GroupPreviewRow> groups) {
        Grid<GroupPreviewRow> grid = new Grid<>();

        grid.addColumn(LitRenderer.<GroupPreviewRow>of(
                "<span theme=\"badge ${item.theme}\">${item.pct}</span>")
                .withProperty("pct",   row -> String.format("%.0f%%", row.confidence() * 100))
                .withProperty("theme", row -> {
                    if ("exact-hash".equals(row.strategy())) return "success";
                    return row.confidence() >= 0.80 ? "contrast" : "error";
                }))
            .setHeader("Confidence").setWidth("130px").setFlexGrow(0);

        grid.addColumn(row -> friendlyStrategy(row.strategy()))
            .setHeader("Type").setWidth("140px").setFlexGrow(0);

        grid.addColumn(GroupPreviewRow::dupeCount)
            .setHeader("Dupes").setWidth("70px").setFlexGrow(0);

        grid.addColumn(row -> formatBytes(row.reclaimableBytes()))
            .setHeader("Recoverable").setWidth("110px").setFlexGrow(0);

        grid.addColumn(row -> truncatePath(row.canonicalPath()))
            .setHeader("Keeper (canonical)").setFlexGrow(1);

        // Expandable detail row — loads members lazily on first expand
        grid.setItemDetailsRenderer(new ComponentRenderer<>(this::buildMemberDetail));
        grid.setDetailsVisibleOnClick(true);

        grid.setItems(groups);
        grid.setWidthFull();

        return grid;
    }

    // ── Member detail (shown when a group row is expanded) ────────────────────

    private Component buildMemberDetail(GroupPreviewRow row) {
        List<MemberPreviewRow> members = previewService.buildMembers(row.groupId());

        Grid<MemberPreviewRow> detail = new Grid<>();
        detail.addClassNames(LumoUtility.Margin.Left.LARGE);

        detail.addColumn(new ComponentRenderer<>(m -> {
            Span role = new Span(m.isCanonical() ? "★ keep" : "duplicate");
            role.getElement().getThemeList().add("badge " + (m.isCanonical() ? "success" : "error"));
            return role;
        })).setHeader("Role").setWidth("100px").setFlexGrow(0);

        detail.addColumn(MemberPreviewRow::path)
            .setHeader("Path").setFlexGrow(2);

        detail.addColumn(m -> formatBytes(m.sizeBytes()))
            .setHeader("Size").setWidth("90px").setFlexGrow(0);

        detail.addColumn(m -> m.modifiedAt() != null ? m.modifiedAt().toLocalDate().toString() : "")
            .setHeader("Modified").setWidth("100px").setFlexGrow(0);

        detail.addColumn(m -> m.proposedArchivePath() != null ? m.proposedArchivePath() : "—")
            .setHeader("Archive destination").setFlexGrow(3);

        detail.setItems(members);
        detail.setWidthFull();
        detail.setAllRowsVisible(true);

        VerticalLayout wrapper = new VerticalLayout(detail);
        wrapper.setPadding(false);
        wrapper.setSpacing(false);
        return wrapper;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Component stat(String value, String label) {
        Span valueSpan = new Span(value);
        valueSpan.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.LARGE);
        Span labelSpan = new Span(" " + label);
        labelSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        HorizontalLayout cell = new HorizontalLayout(valueSpan, labelSpan);
        cell.setAlignItems(Alignment.BASELINE);
        cell.setSpacing(false);
        return cell;
    }

    private Span badge(String text, String variant) {
        Span s = new Span(text);
        s.getElement().getThemeList().add("badge " + variant);
        return s;
    }

    private String friendlyStrategy(String strategy) {
        return switch (strategy) {
            case "exact-hash"       -> "Exact";
            case "near-dup-image"   -> "Image";
            case "near-dup-document"-> "Document";
            default                 -> strategy;
        };
    }

    /** Truncate long paths to the last 2 segments with a leading ellipsis. */
    private String truncatePath(String path) {
        if (path == null) return "";
        String[] parts = path.replace('\\', '/').split("/");
        if (parts.length <= 3) return path;
        return "…/" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
