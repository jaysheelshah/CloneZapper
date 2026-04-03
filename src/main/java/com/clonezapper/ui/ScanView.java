package com.clonezapper.ui;

import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.clonezapper.service.ScanProgressTracker;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    public ScanView(UnifiedScanner scanner, ScanProgressTracker progressTracker) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Start a Scan"));
        add(new Paragraph("Enter one path per line, or use the Browse button to pick folders."));

        TextArea pathsField = new TextArea("Folders to scan");
        pathsField.setPlaceholder("C:\\Users\\you\\Documents\nC:\\Users\\you\\Downloads");
        pathsField.setWidthFull();
        pathsField.setMinHeight("120px");

        Button browseButton = new Button("Browse…");
        browseButton.addClickListener(event -> openFolderPicker(pathsField, UI.getCurrent()));

        HorizontalLayout pathsToolbar = new HorizontalLayout(browseButton);
        pathsToolbar.setAlignItems(Alignment.CENTER);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setWidthFull();

        Span phaseLabel = new Span();
        phaseLabel.setVisible(false);

        Span fileCountLabel = new Span();
        fileCountLabel.setVisible(false);

        Button startButton = new Button("Scan");
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        startButton.addClickListener(event -> {
            String raw = pathsField.getValue().trim();
            if (raw.isBlank()) {
                Notification.show("Please enter at least one path.", 3000, Notification.Position.MIDDLE);
                return;
            }
            List<String> paths = Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

            startButton.setEnabled(false);
            pathsField.setEnabled(false);
            progressBar.setValue(0);
            progressBar.setVisible(true);
            phaseLabel.setText("Starting…");
            phaseLabel.setVisible(true);
            fileCountLabel.setVisible(false);

            UI ui = UI.getCurrent();

            // Poll tracker every 500 ms to update the file count label while scanning
            ui.setPollInterval(500);
            Registration[] pollReg = new Registration[1];
            pollReg[0] = ui.addPollListener(e -> {
                if ("SCANNING".equals(progressTracker.getPhase())) {
                    int count = progressTracker.getFilesIndexed();
                    fileCountLabel.setText(String.format("%,d files found so far…", count));
                    fileCountLabel.setVisible(true);
                } else {
                    fileCountLabel.setVisible(false);
                }
            });

            Thread scanThread = new Thread(() -> {
                try {
                    ScanRun run = scanner.startScan(paths, phase -> {
                        double progress = PHASE_PROGRESS.getOrDefault(phase, 0.0);
                        ui.access(() -> {
                            progressBar.setValue(progress);
                            phaseLabel.setText(friendlyPhase(phase));
                        });
                    });

                    ui.access(() -> {
                        pollReg[0].remove();
                        ui.setPollInterval(-1);
                        progressBar.setVisible(false);
                        phaseLabel.setVisible(false);
                        fileCountLabel.setVisible(false);
                        pathsField.setEnabled(true);
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
                        pollReg[0].remove();
                        ui.setPollInterval(-1);
                        progressBar.setVisible(false);
                        phaseLabel.setVisible(false);
                        fileCountLabel.setVisible(false);
                        pathsField.setEnabled(true);
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

        add(pathsField, pathsToolbar, startButton, progressBar, phaseLabel, fileCountLabel);
    }

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

    /**
     * Opens the OS-native folder picker using JFileChooser (valid because this
     * Vaadin app runs locally — the JVM has display access on the user's machine).
     * The picked path is appended to {@code pathsField} on the Vaadin UI thread.
     */
    private void openFolderPicker(TextArea pathsField, UI ui) {
        Thread picker = new Thread(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select a folder to scan");
            chooser.setAcceptAllFileFilterUsed(false);

            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                String selected = chooser.getSelectedFile().getAbsolutePath();
                ui.access(() -> {
                    String existing = pathsField.getValue().trim();
                    pathsField.setValue(existing.isEmpty() ? selected : existing + "\n" + selected);
                });
            }
        });
        picker.setDaemon(true);
        picker.setName("clonezapper-folder-picker");
        picker.start();
    }
}
