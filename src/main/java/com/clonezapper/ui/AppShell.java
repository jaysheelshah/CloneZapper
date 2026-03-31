package com.clonezapper.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Configures the Vaadin application shell.
 * Sets Lumo dark theme globally for all views.
 */
@Push
@Theme(variant = Lumo.DARK)
public class AppShell implements AppShellConfigurator {
}
