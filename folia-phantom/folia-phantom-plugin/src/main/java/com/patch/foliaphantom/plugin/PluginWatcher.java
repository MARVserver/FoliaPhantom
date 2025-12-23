package com.patch.foliaphantom.plugin;

import com.patch.foliaphantom.core.PluginPatcher;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginWatcher implements Runnable {
    private final Logger logger;
    private final PluginPatcher patcher;
    private final File watchFolder;
    private final File outputFolder;
    private final File backupFolder;
    private final FileConfiguration config;

    private final Map<String, Long> processedFiles = new ConcurrentHashMap<>();
    private int patchedCount = 0;
    private int skippedCount = 0;
    private int failedCount = 0;

    public PluginWatcher(FoliaPhantomPlugin plugin) {
        this.logger = plugin.getLogger();
        this.config = plugin.getConfig();
        this.patcher = new PluginPatcher(logger);

        // Initialize folders
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        this.watchFolder = new File(serverRoot,
                config.getString("auto-patch.watch-folder", "plugins/folia-patch-queue"));
        this.outputFolder = new File(serverRoot, config.getString("auto-patch.output-folder", "plugins/patched"));
        this.backupFolder = new File(serverRoot,
                config.getString("advanced.backup-folder", "plugins/folia-phantom-backups"));

        // Create folders if they don't exist
        createFolders();
    }

    private void createFolders() {
        if (!watchFolder.exists() && !watchFolder.mkdirs()) {
            logger.warning("Failed to create watch folder: " + watchFolder.getAbsolutePath());
        }
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            logger.warning("Failed to create output folder: " + outputFolder.getAbsolutePath());
        }
        if (config.getBoolean("advanced.create-backup", true)) {
            if (!backupFolder.exists() && !backupFolder.mkdirs()) {
                logger.warning("Failed to create backup folder: " + backupFolder.getAbsolutePath());
            }
        }
    }

    @Override
    public void run() {
        if (!config.getBoolean("auto-patch.enabled", true)) {
            return;
        }

        try {
            scanAndPatchPlugins();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in plugin watcher", e);
        }
    }

    private void scanAndPatchPlugins() {
        File[] jarFiles = watchFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            return;
        }

        boolean verbose = config.getBoolean("logging.verbose", false);
        if (verbose) {
            logger.info("Scanning " + jarFiles.length + " plugin(s) in watch folder...");
        }

        for (File jarFile : jarFiles) {
            // Check if we've already processed this version of the file
            String fileName = jarFile.getName();
            long lastModified = jarFile.lastModified();

            if (processedFiles.containsKey(fileName) && processedFiles.get(fileName) == lastModified) {
                continue; // Already processed this exact file
            }

            try {
                if (shouldPatchPlugin(jarFile)) {
                    patchPlugin(jarFile);
                    processedFiles.put(fileName, lastModified);
                } else {
                    if (config.getBoolean("logging.log-skipped", true)) {
                        logger.info("Skipped: " + fileName + " (already Folia-supported or blacklisted)");
                    }
                    skippedCount++;
                    processedFiles.put(fileName, lastModified);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to patch " + fileName, e);
                failedCount++;
            }
        }
    }

    private boolean shouldPatchPlugin(File jarFile) throws IOException {
        String fileName = jarFile.getName();

        // Check blacklist
        List<String> blacklist = config.getStringList("filters.blacklist");
        for (String pattern : blacklist) {
            if (fileName.matches(pattern.replace("*", ".*"))) {
                return false;
            }
        }

        // Check whitelist (if not empty)
        List<String> whitelist = config.getStringList("filters.whitelist");
        if (!whitelist.isEmpty()) {
            boolean inWhitelist = false;
            for (String pattern : whitelist) {
                if (fileName.matches(pattern.replace("*", ".*"))) {
                    inWhitelist = true;
                    break;
                }
            }
            if (!inWhitelist) {
                return false;
            }
        }

        // Check if already Folia-supported
        if (config.getBoolean("filters.skip-folia-supported", true)) {
            if (PluginPatcher.isFoliaSupported(jarFile)) {
                return false;
            }
        }

        return true;
    }

    private void patchPlugin(File jarFile) throws IOException {
        String fileName = jarFile.getName();
        String pluginName = PluginPatcher.getPluginNameFromJar(jarFile);
        if (pluginName == null) {
            pluginName = fileName.replace(".jar", "");
        }

        logger.info("Patching: " + pluginName + "...");

        // Create backup if enabled
        if (config.getBoolean("advanced.create-backup", true)) {
            File backup = new File(backupFolder, fileName);
            Files.copy(jarFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (config.getBoolean("logging.verbose", false)) {
                logger.info("Created backup: " + backup.getName());
            }
        }

        // Patch the plugin
        File outputFile = new File(outputFolder, "patched-" + fileName);
        patcher.patchPlugin(jarFile, outputFile);

        // Delete original if configured
        if (config.getBoolean("advanced.delete-original", false)) {
            if (jarFile.delete()) {
                if (config.getBoolean("logging.verbose", false)) {
                    logger.info("Deleted original: " + fileName);
                }
            }
        }

        patchedCount++;
        if (config.getBoolean("logging.log-success", true)) {
            logger.info("Successfully patched: " + pluginName + " -> " + outputFile.getName());
        }
    }

    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("patched", patchedCount);
        stats.put("skipped", skippedCount);
        stats.put("failed", failedCount);
        stats.put("total", patchedCount + skippedCount + failedCount);
        return stats;
    }

    public void reset() {
        processedFiles.clear();
        patchedCount = 0;
        skippedCount = 0;
        failedCount = 0;
    }
}
