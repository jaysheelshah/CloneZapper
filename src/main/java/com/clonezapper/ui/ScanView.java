package com.clonezapper.ui;

import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.clonezapper.service.ScanProgressTracker;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import org.springframework.beans.factory.annotation.Value;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Route(value = "scan", layout = MainLayout.class)
@PageTitle("Scan — CloneZapper")
public class ScanView extends VerticalLayout {

    private static final Map<String, Double> PHASE_PROGRESS = Map.of(
        "SCANNING",   0.2,
        "CANDIDATES", 0.4,
        "COMPARING",  0.6,
        "CLUSTERING", 0.8,
        "COMPLETE",   1.0,
        "FAILED",     0.0
    );

    // ── Spring-managed dependencies ───────────────────────────────────────────
    private final UnifiedScanner scanner;
    private final ScanProgressTracker progressTracker;

    // ── Form section (hidden while a scan runs) ───────────────────────────────
    private final VerticalLayout formSection;

    // ── Progress section (shown while a scan runs) ────────────────────────────
    private final VerticalLayout progressSection;
    private final ProgressBar progressBar = new ProgressBar();
    private final Span phaseLabel = new Span();
    private final Span fileCountLabel = new Span();

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

            // onPhase callback gives fast progress bar updates from the scan thread
            Consumer<String> onPhase = phase -> {
                double pct = PHASE_PROGRESS.getOrDefault(phase, 0.0);
                ui.access(() -> {
                    progressBar.setValue(pct);
                    phaseLabel.setText(friendlyPhase(phase));
                });
            };

            Thread scanThread = new Thread(() -> {
                try {
                    ScanRun run = scanner.startScan(paths, archiveRoot, onPhase);
                    ui.access(() -> {
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
        progressBar.setWidthFull();

        progressSection = new VerticalLayout(
            new H2("Scan in progress"),
            progressBar, phaseLabel, fileCountLabel);
        progressSection.setSpacing(true);
        progressSection.setPadding(false);
        progressSection.setVisible(false);

        add(formSection, progressSection);
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
                // Scan finished while the user was watching this reconnected view
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
        String phase = progressTracker.getPhase();
        progressBar.setValue(PHASE_PROGRESS.getOrDefault(phase, 0.0));
        phaseLabel.setText(friendlyPhase(phase));
        if ("SCANNING".equals(phase)) {
            fileCountLabel.setText(
                String.format("%,d files found so far…", progressTracker.getFilesIndexed()));
            fileCountLabel.setVisible(true);
        } else {
            fileCountLabel.setVisible(false);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String friendlyPhase(String phase) {
        return switch (phase) {
            case "SCANNING"   -> "Indexing files…";
            case "CANDIDATES" -> "Finding candidates…";
            case "COMPARING"  -> "Comparing content…";
            case "CLUSTERING" -> "Grouping duplicates…";
            case "COMPLETE"   -> "Complete";
            case "FAILED"     -> "Failed";
            default           -> phase;
        };
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
}
