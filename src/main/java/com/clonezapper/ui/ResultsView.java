package com.clonezapper.ui;

import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;

@Route(value = "results", layout = MainLayout.class)
@PageTitle("Results — CloneZapper")
public class ResultsView extends VerticalLayout {

    public ResultsView(ScanRepository scanRepository, FileRepository fileRepository) {
        setSpacing(true);
        setPadding(true);

        add(new H2("Scan Results"));

        scanRepository.findLatest().ifPresentOrElse(run -> {
            showResults(run, fileRepository);
        }, () -> {
            Paragraph msg = new Paragraph("No scan results yet. Run a scan first.");
            msg.addClassNames(LumoUtility.TextColor.SECONDARY);
            add(msg);
        });
    }

    private void showResults(ScanRun run, FileRepository fileRepository) {
        Paragraph info = new Paragraph(
            "Showing files from run: " + run.getRunLabel() + "  |  Phase: " + run.getPhase());
        info.addClassNames(LumoUtility.TextColor.SECONDARY);
        add(info);

        // TODO: Replace with DuplicateGroup grid once Stages ②–⑤ are implemented.
        // Currently shows all indexed files as a placeholder.
        Paragraph placeholder = new Paragraph(
            "Duplicate groups will appear here once the candidate and compare stages are implemented. " +
            "Showing raw file index for now.");
        add(placeholder);

        List<ScannedFile> files = fileRepository.findByScanId(run.getId());
        Grid<ScannedFile> grid = new Grid<>(ScannedFile.class, false);
        grid.addColumn(ScannedFile::getPath).setHeader("Path").setFlexGrow(3);
        grid.addColumn(f -> formatBytes(f.getSize())).setHeader("Size");
        grid.addColumn(ScannedFile::getMimeType).setHeader("Type");
        grid.addColumn(ScannedFile::getCopyHint).setHeader("Copy Hint");
        grid.addColumn(ScannedFile::getHashPartial).setHeader("Partial Hash").setFlexGrow(1);
        grid.setItems(files);
        grid.setWidthFull();
        add(grid);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
