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
            "How certain does CloneZapper need to be before automatically marking files as duplicates? "
            + "At 0.95 (the default), only very obvious duplicates are handled automatically — "
            + "anything CloneZapper is less sure about is placed in the Review Queue for you to check. "
            + "Lower this if you want more files handled automatically; raise it if you are seeing "
            + "too many incorrect matches.");
        thresholdDesc.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        add(thresholdDesc);

        NumberField thresholdField = new NumberField("Certainty needed for auto-action (0.0 – 1.0)");
        thresholdField.setValue(scanSettings.getConfidenceThreshold());
        thresholdField.setMin(0.0);
        thresholdField.setMax(1.0);
        thresholdField.setStep(0.05);
        thresholdField.setStepButtonsVisible(true);
        thresholdField.setWidth("300px");

        Button saveThreshold = new Button("Save");
        saveThreshold.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        saveThreshold.addClickListener(e -> {
            Double v = thresholdField.getValue();
            if (v == null || v < 0.0 || v > 1.0) {
                Notification.show("Please enter a value between 0.0 and 1.0.", 3000, Notification.Position.MIDDLE);
                return;
            }
            scanSettings.setConfidenceThreshold(v);
            Notification ok = new Notification("Setting saved.", 3000, Notification.Position.BOTTOM_START);
            ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            ok.open();
        });

        HorizontalLayout thresholdRow = new HorizontalLayout(thresholdField, saveThreshold);
        thresholdRow.setAlignItems(Alignment.END);
        add(thresholdRow);

        // ── Near-dup similarity floor ─────────────────────────────────────────
        Paragraph simDesc = new Paragraph(
            "How similar do two files need to look before CloneZapper considers them near-duplicates? "
            + "At 0.50 (the default), files that share roughly half their content will be flagged — "
            + "this can sometimes catch files that merely share a common layout or template "
            + "(e.g. monthly bills from the same company) rather than being true copies. "
            + "Raising this to 0.65 or higher means only files that are very alike will be flagged, "
            + "which reduces false matches. Takes effect on the next scan.");
        simDesc.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
        add(simDesc);

        NumberField simField = new NumberField("How similar files must be to be flagged (0.0 – 1.0)");
        simField.setValue(scanSettings.getMinNearDupSimilarity());
        simField.setMin(0.0);
        simField.setMax(1.0);
        simField.setStep(0.05);
        simField.setStepButtonsVisible(true);
        simField.setWidth("300px");

        Button saveSim = new Button("Save");
        saveSim.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        saveSim.addClickListener(e -> {
            Double v = simField.getValue();
            if (v == null || v < 0.0 || v > 1.0) {
                Notification.show("Please enter a value between 0.0 and 1.0.", 3000, Notification.Position.MIDDLE);
                return;
            }
            scanSettings.setMinNearDupSimilarity(v);
            Notification ok = new Notification("Setting saved.", 3000, Notification.Position.BOTTOM_START);
            ok.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            ok.open();
        });

        HorizontalLayout simRow = new HorizontalLayout(simField, saveSim);
        simRow.setAlignItems(Alignment.END);
        add(simRow);

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
