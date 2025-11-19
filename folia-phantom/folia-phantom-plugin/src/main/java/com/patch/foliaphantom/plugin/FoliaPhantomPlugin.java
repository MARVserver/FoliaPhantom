package com.patch.foliaphantom.plugin;

import com.patch.foliaphantom.core.PluginPatcher;
import com.patch.foliaphantom.core.patcher.FoliaPatcher;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class FoliaPhantomPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        FoliaPatcher.plugin = this;
        getLogger().info("FoliaPhantom enabled. This plugin patches other plugins on the fly.");
    }

    @Override
    public void onDisable() {
        getLogger().info("FoliaPhantom disabled.");
    }
}
