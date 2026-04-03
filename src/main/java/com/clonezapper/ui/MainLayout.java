package com.clonezapper.ui;

import com.clonezapper.service.ScanProgressTracker;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Application shell: navigation drawer + top bar.
 * All views declare {@code layout = MainLayout.class} in their {@code @Route}.
 */
public class MainLayout extends AppLayout {

    private final ScanProgressTracker progressTracker;
    private final Span scanBadge = new Span();
    private Registration pollRegistration;

    public MainLayout(ScanProgressTracker progressTracker) {
        this.progressTracker = progressTracker;
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");

        H1 appName = new H1("CloneZapper");
        appName.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        scanBadge.getElement().getThemeList().add("badge contrast");
        scanBadge.addClassNames(LumoUtility.Margin.Left.AUTO, LumoUtility.Margin.Right.MEDIUM);
        scanBadge.setVisible(false);

        addToNavbar(true, toggle, appName, scanBadge);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        event.getUI().setPollInterval(1000);
        pollRegistration = event.getUI().addPollListener(e -> refreshBadge());
    }

    @Override
    protected void onDetach(DetachEvent event) {
        super.onDetach(event);
        if (pollRegistration != null) pollRegistration.remove();
        event.getUI().setPollInterval(-1);
    }

    private void refreshBadge() {
        if (progressTracker.isActive()) {
            String phase = progressTracker.getPhase();
            int count    = progressTracker.getFilesIndexed();
            String text  = "SCANNING".equals(phase)
                ? String.format("Scanning · %,d files", count)
                : "Scanning · " + friendlyPhase(phase);
            scanBadge.setText(text);
            scanBadge.setVisible(true);
        } else {
            scanBadge.setVisible(false);
        }
    }

    private static String friendlyPhase(String phase) {
        return switch (phase) {
            case "CANDIDATES" -> "finding candidates";
            case "COMPARING"  -> "comparing content";
            case "CLUSTERING" -> "grouping duplicates";
            default           -> phase.toLowerCase();
        };
    }

    private void addDrawerContent() {
        Span appTitle = new Span("CloneZapper");
        appTitle.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.MEDIUM);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard",    DashboardView.class,   VaadinIcon.HOME.create()));
        nav.addItem(new SideNavItem("Scan",          ScanView.class,        VaadinIcon.SEARCH.create()));
        nav.addItem(new SideNavItem("Results",       ResultsView.class,     VaadinIcon.LIST.create()));
        nav.addItem(new SideNavItem("Review Queue",  ReviewQueueView.class, VaadinIcon.QUESTION_CIRCLE.create()));
        nav.addItem(new SideNavItem("History",       HistoryView.class,     VaadinIcon.CLOCK.create()));
        nav.addItem(new SideNavItem("Settings",      SettingsView.class,    VaadinIcon.COG.create()));

        Scroller scroller = new Scroller(nav);
        addToDrawer(appTitle, scroller);
    }
}
