package com.clonezapper.ui;

import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

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

    public ScanView(UnifiedScanner scanner) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Start a Scan"));
        add(new Paragraph("Enter one path per line. Only local filesystem paths are supported in this release."));

        TextArea pathsField = new TextArea("Paths to scan");
        pathsField.setPlaceholder("C:\\Users\\you\\Documents\nC:\\Users\\you\\Downloads");
        pathsField.setWidthFull();
        pathsField.setMinHeight("120px");

        ProgressBar progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setWidthFull();

        Span phaseLabel = new Span();
        phaseLabel.setVisible(false);

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

            UI ui = UI.getCurrent();

            Thread scanThread = new Thread(() -> {
                try {
                    ScanRun run = scanner.startScan(paths, phase -> {
                        double progress = PHASE_PROGRESS.getOrDefault(phase, 0.0);
                        ui.access(() -> {
                            progressBar.setValue(progress);
                            phaseLabel.setText(phase);
                        });
                    });

                    ui.access(() -> {
                        progressBar.setVisible(false);
                        phaseLabel.setVisible(false);
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
                        progressBar.setVisible(false);
                        phaseLabel.setVisible(false);
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

        add(pathsField, startButton, progressBar, phaseLabel);
    }
}
