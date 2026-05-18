package com.patch.foliaphantom.cli;

import com.patch.foliaphantom.core.PluginPatcher;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class CLI {

    private static final Logger LOGGER = Logger.getLogger("FoliaPhantom-CLI");

    public static void main(String[] args) {
        setupLogger();
        printBanner();

        InputOptions options = parseArgs(args);
        if (!options.rightsConfirmed()) {
            LOGGER.warning("Copyright safety: patch only plugins you own, administer, or are licensed to modify.");
            LOGGER.warning("Use --rights-confirmed to record that confirmation in automated runs.");
        }

        File inputFile = getInputFile(options.inputPath());
        if (inputFile == null) {
            return;
        }

        File outputDir = new File("patched-plugins");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            LOGGER.severe("Error: Failed to create the output directory at " + outputDir.getAbsolutePath());
            return;
        }

        LOGGER.info("Output directory set to: " + outputDir.getAbsolutePath());

        PluginPatcher patcher = new PluginPatcher(LOGGER);

        if (inputFile.isDirectory()) {
            patchDirectory(patcher, inputFile, outputDir);
        } else if (inputFile.getName().toLowerCase().endsWith(".jar")) {
            patchJar(patcher, inputFile, outputDir);
        } else {
            LOGGER.severe("Error: The provided file is not a JAR file.");
        }

        LOGGER.info("Patching process completed.");
    }

    private static void setupLogger() {
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(java.util.logging.LogRecord lr) {
                return String.format("[%s] %s%n", lr.getLevel().getLocalizedName(), lr.getMessage());
            }
        });
        LOGGER.addHandler(handler);
    }

    private static void printBanner() {
        System.out.println("FoliaPhantom Next Safe");
        System.out.println("CLI utility for local Bukkit-to-Folia compatibility patching.\n");
    }

    private static InputOptions parseArgs(String[] args) {
        String inputPath = null;
        boolean rightsConfirmed = false;
        for (String arg : args) {
            if ("--rights-confirmed".equals(arg)) {
                rightsConfirmed = true;
            } else if (inputPath == null) {
                inputPath = arg;
            } else {
                LOGGER.warning("Ignoring unknown argument: " + arg);
            }
        }
        return new InputOptions(inputPath, rightsConfirmed);
    }

    private static File getInputFile(String inputPath) {
        File inputFile;
        if (inputPath == null) {
            try (Scanner scanner = new Scanner(System.in)) {
                System.out.print(">> Enter the path to a JAR file or a directory of JARs: ");
                String path = scanner.nextLine();
                if (path.isBlank()) {
                    LOGGER.severe("Error: No path provided.");
                    return null;
                }
                inputFile = new File(path);
            }
        } else {
            inputFile = new File(inputPath);
        }

        if (!inputFile.exists()) {
            LOGGER.severe("Error: The specified file or directory does not exist: " + inputFile.getAbsolutePath());
            return null;
        }
        return inputFile;
    }

    private static void patchDirectory(PluginPatcher patcher, File inputDir, File outputDir) {
        File[] jarsToPatch = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        if (jarsToPatch == null) {
            LOGGER.severe("Error: Could not list files in the directory. Please check permissions.");
            return;
        }

        if (jarsToPatch.length == 0) {
            LOGGER.warning("No JAR files were found in the specified directory.");
            return;
        }

        LOGGER.info("Found " + jarsToPatch.length + " JAR file(s) to patch in the directory.");
        int successCount = 0;
        for (File inputJar : jarsToPatch) {
            if (patchJar(patcher, inputJar, outputDir)) {
                successCount++;
            }
        }
        LOGGER.info("Successfully patched " + successCount + " out of " + jarsToPatch.length + " JAR file(s).");
    }

    private static boolean patchJar(PluginPatcher patcher, File inputJar, File outputDir) {
        try {
            String pluginName = PluginPatcher.getPluginNameFromJar(inputJar);
            if (pluginName == null) {
                pluginName = inputJar.getName();
            }

            LOGGER.info("----------------------------------------------------------------------");
            LOGGER.info("Patching: " + pluginName);

            if (PluginPatcher.isFoliaSupported(inputJar)) {
                LOGGER.warning("This plugin already appears to be Folia-supported. Patching anyway.");
            }

            File outputJar = new File(outputDir, "patched-" + inputJar.getName());
            patcher.patchPlugin(inputJar, outputJar);
            LOGGER.info("Successfully patched " + pluginName + ". Patched file at: " + outputJar.getAbsolutePath());
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "An error occurred while patching " + inputJar.getName() + ":", e);
            return false;
        }
    }

    private record InputOptions(String inputPath, boolean rightsConfirmed) {
    }
}
