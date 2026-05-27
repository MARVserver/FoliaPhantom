<div align="center">

  ██████╗  █████╗ ███████╗████████╗ █████╗ 
  ██╔══██╗██╔══██╗██╔════╝╚══██╔══╝██╔══██╗
  ██████╔╝███████║███████╗   ██║   ███████║
  ██╔═══╝ ██╔══██║╚════██║   ██║   ██╔══██║
  ██║     ██║  ██║███████║   ██║   ██║  ██║
  ╚═╝     ╚═╝  ╚═╝╚══════╝   ╚═╝   ╚═╝  ╚═╝

**pasta — Folia Phantom**  
Bukkit → Folia bytecode transformer
v2.0.0

</div>

## Overview

**pasta** (formerly Folia Phantom) is a bytecode transformation toolkit that automatically converts legacy Bukkit plugins to be compatible with [Folia](https://github.com/PaperMC/Folia), PaperMC's region-based multithreaded server implementation.

Using the ASM 9.7 library, it rewrites class files at the bytecode level — replacing non-thread-safe API calls with Folia's region-based and async schedulers, wrapping `Block.setType` operations, and migrating world generation. No source code, no recompilation, no plugin developer involvement required.

## Features

- **Bytecode-level transformation** — patches compiled `.class` files without source
- **Signature removal** — strips `.SF`, `.DSA`, `.RSA` from signed JARs
- **`plugin.yml` injection** — automatically adds `folia-supported: true`
- **Runtime bridge bundling** — includes `FoliaPatcher` runtime classes in the output JAR
- **Fast-fail scanning** — `ScanningClassVisitor` skips classes that don't need patching
- **Parallel transformation** — uses `ForkJoinPool` for concurrent class processing

## Modules

| Module | Description | Output |
|--------|-------------|--------|
| **core** | Transformation engine, ASM visitors, runtime bridge | Library JAR |
| **cli** | Command-line tool for batch patching | Fat JAR (standalone) |
| **gui** | JavaFX desktop application with glassmorphism UI | Fat JAR (standalone) |
| **plugin** | Bukkit server plugin for runtime auto-patching | Plugin JAR |

### CLI

```
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/jars/
java -jar Folia-Phantom-CLI-2.0.0.jar    (interactive mode)
```

### GUI

```
java -jar Folia-Phantom-GUI-2.0.0.jar
```

Or double-click `run-gui.bat` (Windows).

Features: drag-and-drop file list, per-file state indicators, configurable output directory, verbose logging toggle, autoscroll console, keyboard shortcuts (`Ctrl+O` / `Ctrl+E` / `Ctrl+L`).

### Plugin (server-side)

```
/fpatch <plugin-name>   — Patch a specific plugin
/fpatch list            — List all patchable plugins
/fpatch status          — Show patching statistics
/fpatch reload          — Reload configuration
```

## Transformers

Applied in order during patching:

1. **ThreadSafetyTransformer** — wraps `Block.setType()` calls with region-scheduler-safe wrappers
2. **WorldGenClassTransformer** — migrates `WorldCreator` and `getDefaultWorldGenerator` to async execution
3. **EntitySchedulerTransformer** — adapts entity scheduling (extensible, currently base implementation)
4. **SchedulerClassTransformer** — replaces `BukkitRunnable` instance methods with static `FoliaPatcher` equivalents

## Requirements

- **Java 17+** (JDK 21+ recommended)
- **Maven 3.8+** (for building)
- **Paper API 1.21.1** (for plugin module, `provided` scope)

## Build

```bash
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean package
```

Output JARs:

| Artifact | Path |
|----------|------|
| CLI | `folia-phantom-cli/target/Folia-Phantom-CLI-2.0.0.jar` |
| GUI | `folia-phantom-gui/target/folia-phantom-gui-2.0.0.jar` |
| Plugin | `folia-phantom-plugin/target/folia-phantom-plugin-2.0.0.jar` |

## Architecture

```
Input JAR
  │
  ▼
JarInputStream ──► Signature removal (.SF/.DSA/.RSA)
  │                    │
  │                    ▼
  │              plugin.yml injection (folia-supported: true)
  │                    │
  │                    ▼
  │              ScanningClassVisitor (fast-fail check)
  │                    │
  │         ┌──────────┴──────────┐
  │         ▼                     ▼
  │    Needs patch?          Skip (copy as-is)
  │         │
  │         ▼
  │    Transformer chain (ForkJoinPool)
  │    ┌─ ThreadSafetyTransformer
  │    ├─ WorldGenClassTransformer
  │    ├─ EntitySchedulerTransformer
  │    └─ SchedulerClassTransformer
  │         │
  │         ▼
  │    Runtime bundle (FoliaPatcher.class)
  │         │
  │         ▼
  ▼
JarOutputStream ──► Patched JAR
```

## Runtime Bridge

`FoliaPatcher` is bundled into every patched JAR. It provides:

- BukkitScheduler → Folia `RegionScheduler` / `GlobalRegionScheduler` / `AsyncScheduler` routing
- BukkitRunnable instance methods → static method invocations
- Thread-safe `Block.setType()` with automatic region scheduling
- Dedicated single-thread executor for world generation
- Task lifecycle management (cancel, bulk cancel, tracking)

## License

MARV License v1.0.0
