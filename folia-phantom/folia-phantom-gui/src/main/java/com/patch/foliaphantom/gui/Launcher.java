/*
 * Folia Phantom - GUI Launcher
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.gui;

/**
 * Main entry point for the GUI application.
 * 
 * <p>
 * This launcher separates the Application class from the main method
 * to ensure compatibility with various Java runtime environments and
 * packaging methods (like shadow jars).
 * </p>
 */
public class Launcher {
    /**
     * Launches the Folia Phantom GUI.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        FoliaPhantomApp.main(args);
    }
}
