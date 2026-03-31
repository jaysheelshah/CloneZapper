package com.clonezapper.ui;

import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ExecuteStage;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@Route(value = "results/:runId?", layout = MainLayout.class)
@PageTitle("Results — CloneZapper")
public class ResultsView extends VerticalLayout implements BeforeEnterObserver {

    private final ScanRepository scanRepository;
    private final DuplicateGroupRepository groupRepository;
    private final FileRepository fileRepository;
    private final ExecuteStage executeStage;
    private final String archiveRoot;

    private final VerticalLayout content = new VerticalLayout();

    public ResultsView(ScanRepository scanRepository,
                       DuplicateGroupRepository groupRepository,
                       FileRepository fileRepository,
                       ExecuteStage executeStage,
                       @Value("${clonezapper.archive.root}") String archiveRoot) {
        this.scanRepository = scanRepository;
        this.groupRepository = groupRepository;
        this.fileRepository = fileRepository;
        this.executeStage = executeStage;
        this.archiveRoot = archiveRoot;

        setSpacing(true);
        setPadding(true);
        content.setSpacing(true);
        content.setPadding(false);
        add(new H2("Scan Results"), buildRunSelector(), content);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String runIdParam = event.getRouteParameters().get("runId").orElse(null);
        ScanRun run;
        if (runIdParam != null) {
            run = scanRepository.findById(Long.parseLong(runIdParam)).orElse(null);
        } else {
            run = scanRepository.findLatest().orElse(null);
        }
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

        Paragraph info = new Paragraph(
            "Run: " + run.getRunLabel() + "  |  Phase: " + run.getPhase());
        info.addClassNames(LumoUtility.TextColor.SECONDARY);
        content.add(info);

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());

        if (groups.isEmpty()) {
            Paragraph none = new Paragraph("No duplicate groups found in this scan.");
            none.addClassNames(LumoUtility.TextColor.SECONDARY);
            content.add(none);
            return;
        }

        long duplicateCount = groups.stream()
            .mapToLong(g -> g.getMembers().size() - 1)
            .sum();

        // ── Action bar ───────────────────────────────────────────────────────
        Span summary = new Span(groups.size() + " duplicate group(s)  |  "
            + duplicateCount + " file(s) to remove");
        summary.addClassNames(LumoUtility.FontWeight.SEMIBOLD);

        Button stageButton = new Button("Stage duplicates →");
        stageButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        stageButton.addClickListener(e -> {
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Stage duplicates?");
            dialog.setText(
                duplicateCount + " file(s) will be moved to: " + archiveRoot
                + "\nCanonical (★ keep) files are untouched. You can restore everything with Cleanup.");
            dialog.setConfirmText("Stage");
            dialog.setCancelable(true);
            dialog.addConfirmListener(ce -> {
                stageButton.setEnabled(false);
                try {
                    executeStage.execute(run.getId(), archiveRoot);
                    Notification ok = new Notification(
                        duplicateCount + " file(s) staged to " + archiveRoot,
                        4000, Notification.Position.BOTTOM_START);
                    ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    ok.open();
                    UI.getCurrent().navigate(DashboardView.class);
                } catch (Exception ex) {
                    Notification.show("Staging failed: " + ex.getMessage(),
                        5000, Notification.Position.MIDDLE);
                    stageButton.setEnabled(true);
                }
            });
            dialog.open();
        });

        content.add(new HorizontalLayout(summary, stageButton));

        // ── Group list ───────────────────────────────────────────────────────
        for (int i = 0; i < groups.size(); i++) {
            content.add(buildGroupSection(i + 1, groups.get(i)));
        }
    }

    private VerticalLayout buildGroupSection(int index, DuplicateGroup group) {
        VerticalLayout section = new VerticalLayout();
        section.setSpacing(false);
        section.setPadding(false);

        String label = String.format("Group %d — %s  |  confidence: %.0f%%  |  %d files",
            index,
            group.getStrategy(),
            group.getConfidence() * 100,
            group.getMembers().size());
        section.add(new H3(label));

        List<ScannedFile> files = group.getMembers().stream()
            .map(DuplicateMember::getFileId)
            .map(fileRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        Grid<ScannedFile> grid = new Grid<>(ScannedFile.class, false);
        grid.addColumn(f -> f.getId().equals(group.getCanonicalFileId()) ? "★ keep" : "duplicate")
            .setHeader("Role").setWidth("90px").setFlexGrow(0);
        grid.addColumn(ScannedFile::getPath).setHeader("Path").setFlexGrow(3);
        grid.addColumn(f -> formatBytes(f.getSize())).setHeader("Size").setWidth("90px").setFlexGrow(0);
        grid.addColumn(ScannedFile::getMimeType).setHeader("Type").setFlexGrow(1);
        grid.addColumn(ScannedFile::getCopyHint).setHeader("Copy Hint").setFlexGrow(1);
        grid.setItems(files);
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        section.add(grid);

        return section;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
