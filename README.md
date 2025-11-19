
# folia-Phantom

FoliaPhantom-extra Important • License has changed FoliaPhantom-extra is
a manual patching utility designed to run legacy Bukkit, Spigot, and
Paper plugins on a Folia server (the multi-threaded version of PaperMC).
This plugin modifies a target plugin's JAR file, replacing scheduler API
calls incompatible with Folia's threading model with their native Folia
equivalents. This allows many plugins, even those not updated by their
developers for Folia, to run successfully.

## 🚀 Key Features

-   Bytecode Transformation Technology: Directly analyzes plugin class
    files and rewrites BukkitScheduler calls to use Folia's
    RegionScheduler and AsyncScheduler.
-   Automatic plugin.yml Patching: During the patching process, it
    automatically adds or corrects the folia-supported: true flag in the
    target plugin's plugin.yml, ensuring it is recognized as a compliant
    plugin by the Folia server.
-   Broad Scheduler Compatibility: Supports major methods like runTask
    and runTaskTimer, as well as legacy methods like
    scheduleSyncDelayedTask, properly converting both synchronous and
    asynchronous tasks.
-   Terrain & World Gen Compatibility: Attempts to wrap calls to
    ChunkGenerator and synchronous createWorld to work within Folia's
    asynchronous environment.

## ⚙️ Installation and Usage

FoliaPhantom-extra is a tool that manually patches specified JAR files,
rather than automatically scanning them.

1.  Install FoliaPhantom-extra: Download the latest
    FoliaPhantom-extra.jar from the Releases page and place it in your
    server's plugins folder.
2.  Generate Directories: Start the server once. This will automatically
    create two directories: input and output inside the
    plugins/FoliaPhantom-extra/ folder.
3.  Place Target JARs: Stop the server. Move the plugin JAR files you
    want to patch into the input directory.
4.  Run the Patcher: Start the server again. FoliaPhantom-extra will
    detect all JARs in the input directory and perform the patching
    process.
5.  Retrieve Patched JARs: The patched JAR files will be saved to the
    output directory. Once a JAR is successfully processed, the original
    file in the input directory is deleted.
6.  Install the Patched Plugin: Take the patched JAR file from the
    output directory and move it to your main server plugins folder for
    use.

## ⚠️ Limitations & Disclaimers

-   Risk of Synchronous World Generation: Wrapping a plugin that calls
    time-consuming methods like createWorld synchronously may cause the
    server to freeze or hang temporarily.
-   NMS/CB Dependencies: Does not guarantee compatibility for plugins
    depending directly on NMS or CB code.
-   Advanced Class-loading: May conflict with certain security plugins
    or plugins with complex class-loader manipulations.
-   Not a 100% Guarantee: Although highly effective, not every plugin
    will work.

## 🛠️ How It Works (Technical Deep Dive)

1.  JAR Detection: Scans for JAR files in the input directory.
2.  Output JAR Creation: Creates patched output JARs.
3.  Bytecode Transformation (ASM): Patches targeted APIs such as:
    -   org.bukkit.scheduler.BukkitScheduler
    -   org.bukkit.plugin.Plugin#getDefaultWorldGenerator
    -   org.bukkit.Server#createWorld
4.  Scheduler Redirection & World Gen Wrapping.
5.  plugin.yml Patching: Adds folia-supported: true.
6.  Saving: Writes the patched JAR to output.

## 📦 Building from Source

### Plugin Only

mvn clean package

### CLI Version

mvn clean package -Pcli

## 📅 Future Plans

-   Development will resume
-   Modular design refactor
