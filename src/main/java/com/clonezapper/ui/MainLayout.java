package com.clonezapper.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Application shell: navigation drawer + top bar.
 * All views declare {@code layout = MainLayout.class} in their {@code @Route}.
 */
public class MainLayout extends AppLayout {

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");

        H1 appName = new H1("CloneZapper");
        appName.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        addToNavbar(true, toggle, appName);
    }

    private void addDrawerContent() {
        Span appTitle = new Span("CloneZapper");
        appTitle.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.MEDIUM);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard",  DashboardView.class,   VaadinIcon.HOME.create()));
        nav.addItem(new SideNavItem("Scan",        ScanView.class,        VaadinIcon.SEARCH.create()));
        nav.addItem(new SideNavItem("Results",     ResultsView.class,     VaadinIcon.LIST.create()));
        nav.addItem(new SideNavItem("Review Queue",ReviewQueueView.class, VaadinIcon.QUESTION_CIRCLE.create()));

        Scroller scroller = new Scroller(nav);
        addToDrawer(appTitle, scroller);
    }
}
