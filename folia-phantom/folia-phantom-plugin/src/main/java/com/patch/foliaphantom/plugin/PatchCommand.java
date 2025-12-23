package com.patch.foliaphantom.plugin;

import com.patch.foliaphantom.core.PluginPatcher;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PatchCommand implements CommandExecutor, TabCompleter {
    private final FoliaPhantomPlugin plugin;
    private final PluginPatcher patcher;

    public PatchCommand(FoliaPhantomPlugin plugin) {
        this.plugin = plugin;
        this.patcher = new PluginPatcher(plugin.getLogger());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foliaphantom.patch")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                listPlugins(sender);
                break;
            case "status":
                showStatus(sender);
                break;
            case "reload":
                reloadConfig(sender);
                break;
            default:
                // Treat as plugin name to patch
                patchPlugin(sender, args[0]);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== FoliaPhantom Commands ===");
        sender.sendMessage(
                ChatColor.YELLOW + "/foliapatch <plugin-name>" + ChatColor.WHITE + " - Patch a specific plugin");
        sender.sendMessage(ChatColor.YELLOW + "/foliapatch list" + ChatColor.WHITE + " - List all patchable plugins");
        sender.sendMessage(ChatColor.YELLOW + "/foliapatch status" + ChatColor.WHITE + " - Show patching statistics");
        sender.sendMessage(ChatColor.YELLOW + "/foliapatch reload" + ChatColor.WHITE + " - Reload configuration");
    }

    private void listPlugins(CommandSender sender) {
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        File watchFolder = new File(serverRoot,
                plugin.getConfig().getString("auto-patch.watch-folder", "plugins/folia-patch-queue"));

        if (!watchFolder.exists()) {
            sender.sendMessage(ChatColor.RED + "Watch folder does not exist: " + watchFolder.getAbsolutePath());
            return;
        }

        File[] jarFiles = watchFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "No plugins found in watch folder.");
            sender.sendMessage(ChatColor.GRAY + "Place plugins in: " + watchFolder.getAbsolutePath());
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Plugins in Watch Folder ===");
        for (File jarFile : jarFiles) {
            try {
                String pluginName = PluginPatcher.getPluginNameFromJar(jarFile);
                boolean foliaSupported = PluginPatcher.isFoliaSupported(jarFile);

                String status = foliaSupported ? ChatColor.GREEN + "[Folia-Supported]"
                        : ChatColor.RED + "[Needs Patching]";

                sender.sendMessage(
                        ChatColor.WHITE + "• " + (pluginName != null ? pluginName : jarFile.getName()) + " " + status);
            } catch (IOException e) {
                sender.sendMessage(ChatColor.WHITE + "• " + jarFile.getName() + ChatColor.GRAY + " [Error reading]");
            }
        }
        sender.sendMessage(ChatColor.GRAY + "Total: " + jarFiles.length + " plugin(s)");
    }

    private void showStatus(CommandSender sender) {
        PluginWatcher watcher = plugin.getWatcher();
        if (watcher == null) {
            sender.sendMessage(ChatColor.RED + "Plugin watcher is not initialized.");
            return;
        }

        Map<String, Integer> stats = watcher.getStatistics();
        boolean autoEnabled = plugin.getConfig().getBoolean("auto-patch.enabled", true);

        sender.sendMessage(ChatColor.GOLD + "=== FoliaPhantom Status ===");
        sender.sendMessage(ChatColor.YELLOW + "Auto-Patching: " +
                (autoEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
        sender.sendMessage(ChatColor.WHITE + "Total Processed: " + ChatColor.AQUA + stats.get("total"));
        sender.sendMessage(ChatColor.WHITE + "Successfully Patched: " + ChatColor.GREEN + stats.get("patched"));
        sender.sendMessage(ChatColor.WHITE + "Skipped: " + ChatColor.YELLOW + stats.get("skipped"));
        sender.sendMessage(ChatColor.WHITE + "Failed: " + ChatColor.RED + stats.get("failed"));

        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        File watchFolder = new File(serverRoot, plugin.getConfig().getString("auto-patch.watch-folder"));
        File outputFolder = new File(serverRoot, plugin.getConfig().getString("auto-patch.output-folder"));

        sender.sendMessage(ChatColor.GRAY + "Watch Folder: " + watchFolder.getAbsolutePath());
        sender.sendMessage(ChatColor.GRAY + "Output Folder: " + outputFolder.getAbsolutePath());
    }

    private void patchPlugin(CommandSender sender, String pluginIdentifier) {
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        File watchFolder = new File(serverRoot,
                plugin.getConfig().getString("auto-patch.watch-folder", "plugins/folia-patch-queue"));
        File outputFolder = new File(serverRoot,
                plugin.getConfig().getString("auto-patch.output-folder", "plugins/patched"));

        // Find the plugin jar
        File targetJar = null;
        String searchName = pluginIdentifier.toLowerCase();

        File[] jarFiles = watchFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                try {
                    String pluginName = PluginPatcher.getPluginNameFromJar(jarFile);
                    if (pluginName != null && pluginName.equalsIgnoreCase(searchName)) {
                        targetJar = jarFile;
                        break;
                    }
                } catch (IOException e) {
                    // Continue searching
                }

                // Also check by filename
                if (jarFile.getName().toLowerCase().contains(searchName)) {
                    targetJar = jarFile;
                    break;
                }
            }
        }

        if (targetJar == null) {
            sender.sendMessage(ChatColor.RED + "Plugin not found: " + pluginIdentifier);
            sender.sendMessage(ChatColor.YELLOW + "Use '/foliapatch list' to see available plugins.");
            return;
        }

        // Check if already Folia-supported
        try {
            if (PluginPatcher.isFoliaSupported(targetJar)) {
                sender.sendMessage(ChatColor.YELLOW + "Warning: This plugin is already marked as Folia-supported.");
                sender.sendMessage(ChatColor.GRAY + "Patching anyway...");
            }
        } catch (IOException e) {
            // Continue with patching
        }

        sender.sendMessage(ChatColor.YELLOW + "Patching plugin: " + targetJar.getName() + "...");

        try {
            if (!outputFolder.exists() && !outputFolder.mkdirs()) {
                sender.sendMessage(ChatColor.RED + "Failed to create output folder.");
                return;
            }

            File outputJar = new File(outputFolder, "patched-" + targetJar.getName());
            patcher.patchPlugin(targetJar, outputJar);

            sender.sendMessage(ChatColor.GREEN + "✓ Successfully patched!");
            sender.sendMessage(ChatColor.WHITE + "Output: " + ChatColor.AQUA + outputJar.getName());
            sender.sendMessage(ChatColor.GRAY + "Location: " + outputFolder.getAbsolutePath());
            sender.sendMessage(ChatColor.YELLOW + "Remember to restart the server to load the patched plugin.");
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "✗ Failed to patch plugin: " + e.getMessage());
            plugin.getLogger().severe("Patching error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void reloadConfig(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("foliaphantom.patch")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("list", "status", "reload"));

            // Add plugin names
            File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
            File watchFolder = new File(serverRoot,
                    plugin.getConfig().getString("auto-patch.watch-folder", "plugins/folia-patch-queue"));

            File[] jarFiles = watchFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    try {
                        String pluginName = PluginPatcher.getPluginNameFromJar(jarFile);
                        if (pluginName != null) {
                            completions.add(pluginName);
                        }
                    } catch (IOException e) {
                        // Skip this file
                    }
                }
            }

            String input = args[0].toLowerCase();
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
