package com.akshaya.shopsphere.db;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

// Runs DatabaseInitializer.initialize() exactly once at webapp startup, before any servlet
// is initialized or request served, regardless of which URL gets hit first.
@WebListener
public class StartupListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        DatabaseInitializer.initialize();
    }
}
