package com.clonezapper.ui;

import com.clonezapper.service.ScanSettings;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.beans.factory.annotation.Value;

@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings — CloneZapper")
public class SettingsView extends VerticalLayout {

    public SettingsView(ScanSettings scanSettings,
                        @Value("${clonezapper.archive.root}") String archiveRoot,
                        @Value("${spring.datasource.url}") String datasourceUrl) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Settings"));

        // ── Confidence threshold ──────────────────────────────────────────────
        add(new H3("Duplicate Detection"));

        Paragraph thresholdDesc = new Paragraph(
            "Confidence threshold — groups at or above this value go straight to the Auto Queue; "
            + "lower-confidence matches appear in the Review Queue for manual inspection.");
        thresholdDesc.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        add(thresholdDesc);

        NumberField thresholdField = new NumberField("Confidence threshold (0.0 – 1.0)");
        thresholdField.setValue(scanSettings.getConfidenceThreshold());
        thresholdField.setMin(0.0);
        thresholdField.setMax(1.0);
        thresholdField.setStep(0.05);
        thresholdField.setStepButtonsVisible(true);
        thresholdField.setWidth("260px");

        Button saveThreshold = new Button("Save");
        saveThreshold.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        saveThreshold.addClickListener(e -> {
            Double v = thresholdField.getValue();
            if (v == null || v < 0.0 || v > 1.0) {
                Notification.show("Value must be between 0.0 and 1.0.", 3000, Notification.Position.MIDDLE);
                return;
            }
            scanSettings.setConfidenceThreshold(v);
            Notification ok = new Notification("Threshold updated to " + v, 3000,
                Notification.Position.BOTTOM_START);
            ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            ok.open();
        });

        HorizontalLayout thresholdRow = new HorizontalLayout(thresholdField, saveThreshold);
        thresholdRow.setAlignItems(Alignment.END);
        add(thresholdRow);

        // ── Read-only info ────────────────────────────────────────────────────
        add(new H3("Storage"));

        add(readOnlyRow("Archive root", archiveRoot,
            "Configure via clonezapper.archive.root in application.properties"));
        add(readOnlyRow("Database", datasourceUrl.replace("jdbc:sqlite:", ""),
            "SQLite database file path"));
    }

    private VerticalLayout readOnlyRow(String label, String value, String hint) {
        TextField field = new TextField(label);
        field.setValue(value);
        field.setReadOnly(true);
        field.setWidthFull();
        Span hintSpan = new Span(hint);
        hintSpan.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.XSMALL);
        VerticalLayout row = new VerticalLayout(field, hintSpan);
        row.setSpacing(false);
        row.setPadding(false);
        return row;
    }
}
