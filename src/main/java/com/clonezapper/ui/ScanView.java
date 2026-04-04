package com.clonezapper.ui;

import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.clonezapper.service.ScanProgressTracker;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import org.springframework.beans.factory.annotation.Value;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Route(value = "scan", layout = MainLayout.class)
@PageTitle("Scan — CloneZapper")
public class ScanView extends VerticalLayout {

    /** Ordered pipeline stages shown in the progress table. */
    private static final List<String> STAGE_ORDER =
        List.of("SCANNING", "CANDIDATES", "COMPARING", "CLUSTERING", "COMPLETE");

    private static final Map<String, String> STAGE_LABELS = Map.of(
        "SCANNING",   "Index files",
        "CANDIDATES", "Find candidates",
        "COMPARING",  "Compare content",
        "CLUSTERING", "Group duplicates",
        "COMPLETE",   "Complete"
    );

    private static final DateTimeFormatter TIME_FMT    = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");

    // ── Spring-managed dependencies ───────────────────────────────────────────
    private final UnifiedScanner scanner;
    private final ScanProgressTracker progressTracker;

    // ── Form section (hidden while a scan runs) ───────────────────────────────
    private final VerticalLayout formSection;

    // ── Progress section (shown while a scan runs) ────────────────────────────
    private final VerticalLayout progressSection;
    private final Span scanMetaLabel = new Span();
    private final Grid<StageRow> stageGrid = new Grid<>();

    // ── Active poll registration (cleaned up on detach) ───────────────────────
    private Registration pollReg;

    public ScanView(UnifiedScanner scanner,
                    ScanProgressTracker progressTracker,
                    @Value("${clonezapper.archive.root}") String defaultArchiveRoot) {
        this.scanner         = scanner;
        this.progressTracker = progressTracker;

        setSpacing(true);
        setPadding(true);

        // ── Form ──────────────────────────────────────────────────────────────
        TextArea pathsField = new TextArea("Folders to scan");
        pathsField.setPlaceholder("C:\\Users\\you\\Documents\nC:\\Users\\you\\Downloads");
        pathsField.setWidthFull();
        pathsField.setMinHeight("120px");

        Button browseButton = new Button("Browse…");
        browseButton.addClickListener(e -> openFolderPickerAppend(pathsField, UI.getCurrent()));
        HorizontalLayout pathsToolbar = new HorizontalLayout(browseButton);
        pathsToolbar.setAlignItems(Alignment.CENTER);

        TextField archiveField = new TextField("Archive destination");
        archiveField.setPlaceholder("E.g. D:\\CloneZapper-Archive");
        archiveField.setValue(defaultArchiveRoot);
        archiveField.setWidthFull();
        archiveField.setHelperText("Duplicate files will be moved here. Can be an external drive.");

        Button archiveBrowseButton = new Button("Browse…");
        archiveBrowseButton.addClickListener(e -> openFolderPickerReplace(archiveField, UI.getCurrent()));
        HorizontalLayout archiveToolbar = new HorizontalLayout(archiveBrowseButton);
        archiveToolbar.setAlignItems(Alignment.CENTER);

        Button startButton = new Button("Scan");
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        startButton.addClickListener(event -> {
            String raw = pathsField.getValue().trim();
            if (raw.isBlank()) {
                Notification.show("Please enter at least one path.", 3000, Notification.Position.MIDDLE);
                return;
            }
            String archiveRoot = archiveField.getValue().trim();
            if (archiveRoot.isBlank()) {
                Notification.show("Please enter an archive destination.", 3000, Notification.Position.MIDDLE);
                return;
            }
            List<String> paths = Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim).filter(s -> !s.isBlank()).toList();

            startButton.setEnabled(false);
            pathsField.setEnabled(false);
            archiveField.setEnabled(false);

            UI ui = UI.getCurrent();
            enterProgressMode(ui);

            Consumer<String> onPhase = phase -> {
                ui.access(() -> refreshProgress());
            };

            Thread scanThread = new Thread(() -> {
                try {
                    ScanRun run = scanner.startScan(paths, archiveRoot, onPhase);
                    ui.access(() -> {
                        refreshProgress();
                        exitProgressMode(ui);
                        pathsField.setEnabled(true);
                        archiveField.setEnabled(true);
                        startButton.setEnabled(true);
                        Notification ok = new Notification(
                            "Scan complete! Run ID: " + run.getId(),
                            4000, Notification.Position.BOTTOM_START);
                        ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        ok.open();
                        UI.getCurrent().navigate(DashboardView.class);
                    });
                } catch (Exception e) {
                    ui.access(() -> {
                        exitProgressMode(ui);
                        pathsField.setEnabled(true);
                        archiveField.setEnabled(true);
                        startButton.setEnabled(true);
                        Notification.show("Scan failed: " + e.getMessage(),
                            5000, Notification.Position.MIDDLE);
                    });
                }
            });
            scanThread.setDaemon(true);
            scanThread.setName("clonezapper-scan");
            scanThread.start();
        });

        formSection = new VerticalLayout(
            new H2("Start a Scan"),
            new Paragraph("Enter one path per line, or use the Browse button to pick folders."),
            pathsField, pathsToolbar, archiveField, archiveToolbar, startButton);
        formSection.setSpacing(true);
        formSection.setPadding(false);

        // ── Progress ──────────────────────────────────────────────────────────
        buildStageGrid();

        scanMetaLabel.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)");

        progressSection = new VerticalLayout(
            new H2("Scan in progress"),
            scanMetaLabel,
            stageGrid);
        progressSection.setSpacing(true);
        progressSection.setPadding(false);
        progressSection.setVisible(false);

        add(formSection, progressSection);
    }

    // ── Stage grid setup ──────────────────────────────────────────────────────

    private void buildStageGrid() {
        stageGrid.addColumn(StageRow::label)
            .setHeader("Step")
            .setWidth("160px").setFlexGrow(0);

        stageGrid.addColumn(
                LitRenderer.<StageRow>of("<span theme=\"badge ${item.theme}\">${item.status}</span>")
                    .withProperty("status", StageRow::status)
                    .withProperty("theme",  StageRow::statusTheme))
            .setHeader("Status")
            .setWidth("100px").setFlexGrow(0);

        stageGrid.addColumn(StageRow::detail)
            .setHeader("Details")
            .setWidth("160px").setFlexGrow(0);

        stageGrid.addColumn(StageRow::startedAt)
            .setHeader("Start")
            .setWidth("90px").setFlexGrow(0);

        stageGrid.addColumn(StageRow::endedAt)
            .setHeader("End")
            .setWidth("90px").setFlexGrow(0);

        stageGrid.addColumn(StageRow::elapsed)
            .setHeader("Duration")
            .setWidth("90px").setFlexGrow(0);

        stageGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        stageGrid.setAllRowsVisible(true);
        stageGrid.setWidthFull();
    }

    // ── Reconnect on navigate-back ────────────────────────────────────────────

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        if (progressTracker.isActive()) {
            enterProgressMode(event.getUI());
        }
    }

    @Override
    protected void onDetach(DetachEvent event) {
        super.onDetach(event);
        stopPolling(event.getUI());
    }

    // ── Progress mode helpers ─────────────────────────────────────────────────

    private void enterProgressMode(UI ui) {
        formSection.setVisible(false);
        progressSection.setVisible(true);
        refreshProgress();

        ui.setPollInterval(500);
        pollReg = ui.addPollListener(e -> {
            refreshProgress();
            if (!progressTracker.isActive()) {
                exitProgressMode(ui);
                String phase = progressTracker.getPhase();
                if ("COMPLETE".equals(phase)) {
                    Notification ok = new Notification(
                        "Scan complete!", 4000, Notification.Position.BOTTOM_START);
                    ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    ok.open();
                    ui.navigate(DashboardView.class);
                } else {
                    Notification.show("Scan ended with status: " + phase,
                        5000, Notification.Position.MIDDLE);
                }
            }
        });
    }

    private void exitProgressMode(UI ui) {
        stopPolling(ui);
        progressSection.setVisible(false);
        formSection.setVisible(true);
    }

    private void stopPolling(UI ui) {
        if (pollReg != null) {
            pollReg.remove();
            pollReg = null;
        }
        ui.setPollInterval(-1);
    }

    private void refreshProgress() {
        String currentPhase = progressTracker.getPhase();
        int currentIdx = STAGE_ORDER.indexOf(currentPhase);
        if (currentIdx < 0) currentIdx = 0;

        Instant scanStart = progressTracker.getScanStartTime();
        Map<String, Instant> stageTimes = progressTracker.getStageStartTimes();

        // Update scan-level metadata line
        if (scanStart != null) {
            String startStr = DISPLAY_FMT.format(LocalDateTime.ofInstant(scanStart, ZoneId.systemDefault()));
            String elapsedStr = formatDuration(Duration.between(scanStart, Instant.now()));
            scanMetaLabel.setText("Started: " + startStr + "   ·   Elapsed: " + elapsedStr);
        }

        // Build one row per pipeline stage
        List<StageRow> rows = new ArrayList<>();
        for (int i = 0; i < STAGE_ORDER.size(); i++) {
            String phaseKey = STAGE_ORDER.get(i);
            String label = STAGE_LABELS.get(phaseKey);

            String status, statusTheme;
            if (i < currentIdx) {
                status = "Done";    statusTheme = "success";
            } else if (i == currentIdx) {
                status = "Active";  statusTheme = "";
            } else {
                status = "Pending"; statusTheme = "contrast";
            }

            // Detail: file count while scanning or after scanning completes
            String detail = "";
            if ("SCANNING".equals(phaseKey) && i <= currentIdx) {
                int count = progressTracker.getFilesIndexed();
                if (count > 0) detail = String.format("%,d files", count);
            }

            // Start time of this stage
            Instant stageStart = stageTimes.get(phaseKey);
            String startedAt = stageStart != null ? formatTime(stageStart) : "—";

            // End time = start time of the next stage (or "—" if not yet reached)
            String endedAt = "—";
            Instant stageEnd = null;
            if (i + 1 < STAGE_ORDER.size()) {
                stageEnd = stageTimes.get(STAGE_ORDER.get(i + 1));
                if (stageEnd != null) endedAt = formatTime(stageEnd);
            }

            // Elapsed: for done stages use stage end; for active stage use now; pending = "—"
            String elapsed = "—";
            if (stageStart != null) {
                if (i < currentIdx) {
                    Instant end = stageEnd != null ? stageEnd : Instant.now();
                    elapsed = formatDuration(Duration.between(stageStart, end));
                } else if (i == currentIdx) {
                    elapsed = formatDuration(Duration.between(stageStart, Instant.now()));
                }
            }

            rows.add(new StageRow(label, phaseKey, status, statusTheme, detail, startedAt, endedAt, elapsed));
        }
        stageGrid.setItems(rows);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String formatTime(Instant instant) {
        return TIME_FMT.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }

    private static String formatDuration(Duration d) {
        long s = d.toSeconds();
        if (s < 60)   return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m " + (s % 60) + "s";
    }

    /** Opens a folder picker and APPENDS the chosen path to a multi-line TextArea. */
    private void openFolderPickerAppend(TextArea target, UI ui) {
        openPicker("Select a folder to scan", ui, selected -> {
            String existing = target.getValue().trim();
            ui.access(() -> target.setValue(existing.isEmpty() ? selected : existing + "\n" + selected));
        });
    }

    /** Opens a folder picker and REPLACES the value of a single-line TextField. */
    private void openFolderPickerReplace(TextField target, UI ui) {
        openPicker("Select archive destination", ui, selected ->
            ui.access(() -> target.setValue(selected)));
    }

    private void openPicker(String title, UI ui, Consumer<String> onSelected) {
        Thread picker = new Thread(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle(title);
            chooser.setAcceptAllFileFilterUsed(false);

            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                onSelected.accept(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        picker.setDaemon(true);
        picker.setName("clonezapper-folder-picker");
        picker.start();
    }

    // ── Stage row model ───────────────────────────────────────────────────────

    private record StageRow(
        String label,
        String phaseKey,
        String status,
        String statusTheme,
        String detail,
        String startedAt,
        String endedAt,
        String elapsed
    ) {}
}
