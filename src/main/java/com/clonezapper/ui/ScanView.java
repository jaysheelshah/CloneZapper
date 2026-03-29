package com.clonezapper.ui;

import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.ScanRun;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.Arrays;
import java.util.List;

@Route(value = "scan", layout = MainLayout.class)
@PageTitle("Scan — CloneZapper")
public class ScanView extends VerticalLayout {

    public ScanView(UnifiedScanner scanner) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Start a Scan"));
        add(new Paragraph("Enter one path per line. Only local filesystem paths are supported in this release."));

        TextArea pathsField = new TextArea("Paths to scan");
        pathsField.setPlaceholder("C:\\Users\\you\\Documents\nC:\\Users\\you\\Downloads");
        pathsField.setWidthFull();
        pathsField.setMinHeight("120px");

        Button startButton = new Button("Scan", event -> {
            String raw = pathsField.getValue().trim();
            if (raw.isBlank()) {
                Notification.show("Please enter at least one path.", 3000, Notification.Position.MIDDLE);
                return;
            }
            List<String> paths = Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

            startButton(event.getSource()).setEnabled(false);
            Notification.show("Scan started for " + paths.size() + " path(s)...", 3000, Notification.Position.BOTTOM_START);

            // Run scan (blocking for now — TODO: push to background thread + progress bar)
            ScanRun run = scanner.startScan(paths);

            Notification ok = new Notification("Scan complete! Run ID: " + run.getId(), 4000, Notification.Position.BOTTOM_START);
            ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            ok.open();

            UI.getCurrent().navigate(DashboardView.class);
        });
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(pathsField, startButton);
    }

    private Button startButton(com.vaadin.flow.component.Component source) {
        return (Button) source;
    }
}
