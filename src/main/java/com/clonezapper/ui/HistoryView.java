package com.clonezapper.ui;

import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.ScanRun;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;

@Route(value = "history", layout = MainLayout.class)
@PageTitle("History — CloneZapper")
public class HistoryView extends VerticalLayout {

    record RunRow(ScanRun run, long fileCount, long groupCount) {}

    public HistoryView(ScanRepository scanRepository,
                       FileRepository fileRepository,
                       DuplicateGroupRepository groupRepository) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Scan History"));

        List<ScanRun> runs = scanRepository.findAll();
        if (runs.isEmpty()) {
            Paragraph msg = new Paragraph("No scan runs yet. Start your first scan from the Scan page.");
            msg.addClassNames(LumoUtility.TextColor.SECONDARY);
            add(msg);
            return;
        }

        List<RunRow> rows = runs.stream()
            .map(r -> new RunRow(r,
                fileRepository.countByScanId(r.getId()),
                groupRepository.countByScanId(r.getId())))
            .toList();

        Grid<RunRow> grid = new Grid<>();
        grid.addColumn(row -> row.run().getId()).setHeader("ID").setWidth("60px").setFlexGrow(0);
        grid.addColumn(row -> row.run().getRunLabel()).setHeader("Label").setFlexGrow(2);
        grid.addColumn(row -> row.run().getPhase()).setHeader("Phase").setWidth("110px").setFlexGrow(0);
        grid.addColumn(row -> String.format("%,d", row.fileCount())).setHeader("Files").setWidth("90px").setFlexGrow(0);
        grid.addColumn(row -> row.groupCount()).setHeader("Dup groups").setWidth("110px").setFlexGrow(0);
        grid.addColumn(row -> row.run().getCreatedAt() != null ? row.run().getCreatedAt().toString() : "")
            .setHeader("Started").setFlexGrow(1);

        grid.addComponentColumn(row -> {
            Button view = new Button("View Results");
            view.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            view.addClickListener(e -> UI.getCurrent().navigate(
                ResultsView.class, new com.vaadin.flow.router.RouteParameters("runId",
                    String.valueOf(row.run().getId()))));
            return view;
        }).setHeader("").setWidth("130px").setFlexGrow(0);

        grid.setItems(rows);
        grid.setWidthFull();
        grid.setAllRowsVisible(false);
        grid.setHeight("600px");
        add(grid);
    }
}
