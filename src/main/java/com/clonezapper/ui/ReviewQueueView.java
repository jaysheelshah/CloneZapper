package com.clonezapper.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Per-pair review UI for low-confidence duplicate candidates.
 *
 * TODO: Implement per-pair diff view — show similarity %, side-by-side file preview,
 *       confirm or dismiss buttons, link to canonical file location.
 *
 * Depends on: ClusterStage (Stage ④) being implemented.
 */
@Route(value = "review", layout = MainLayout.class)
@PageTitle("Review Queue — CloneZapper")
public class ReviewQueueView extends VerticalLayout {

    public ReviewQueueView() {
        setSpacing(true);
        setPadding(true);

        add(new H2("Review Queue"));

        Paragraph msg = new Paragraph(
            "Low-confidence duplicate pairs will appear here for per-pair review once " +
            "the compare and cluster stages are implemented (Stages ③ and ④).");
        msg.addClassNames(LumoUtility.TextColor.SECONDARY);
        add(msg);
    }
}
