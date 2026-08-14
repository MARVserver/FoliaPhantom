<div align="center">

```text
  ██████╗  █████╗ ███████╗████████╗ █████╗
  ██╔══██╗██╔══██╗██╔════╝╚══██╔══╝██╔══██╗
  ██████╔╝███████║███████╗   ██║   ███████║
  ██╔═══╝ ██╔══██║╚════██║   ██║   ██╔══██║
  ██║     ██║  ██║███████║   ██║   ██║  ██║
  ╚═╝     ╚═╝  ╚═╝╚══════╝   ╚═╝   ╚═╝  ╚═╝
```

**pasta — Folia Phantom**  
Bukkit → Folia bytecode transformer  
v2.0.0

</div>

## Overview

**pasta** (formerly Folia Phantom) is a bytecode transformation toolkit that converts legacy Bukkit plugins for use with [Folia](https://github.com/PaperMC/Folia), PaperMC's region-based multithreaded server implementation.

Using ASM 9.7, pasta rewrites compiled `.class` files without requiring plugin source code or recompilation. It adapts scheduler calls, thread-sensitive block operations, world generation behavior, and other compatibility points while preserving unrelated JAR contents.

> Bytecode transformation cannot guarantee that every Bukkit plugin is Folia-safe. Test patched plugins on a staging server before production use.

## Carbonara update

**Carbonara** is the current usability and contributor-experience update. It adds:

- repository-wide engineering and coding guidance in [`AGENTS.md`](AGENTS.md);
- contributor workflow and verification guidance in [`CONTRIBUTING.md`](CONTRIBUTING.md);
- consistent editor defaults via [`.editorconfig`](.editorconfig);
- a clearer browser workflow with drag-and-drop, selected-file visibility, progress, reset controls, responsive layout, and improved accessibility;
- CLI quality-of-life options such as `--help`, `--output`, and `--no-banner`.

Carbonara does not change the project's `2.0.0` version by itself; versioning remains a separate release decision.

## Choose how to use pasta

| Mode | Best for | Requires |
|------|----------|----------|
| **Browser** | Quick local patching without installing a desktop app | Modern browser |
| **CLI** | Automation and batch patching | Java 17+ |
| **GUI** | Desktop drag-and-drop workflows | Java 17+ |
| **Server plugin** | Patching from a Bukkit/Paper server | Paper-compatible server |

### Browser

The static app under `web/` runs the Java patcher through CheerpJ. Plugin JARs are processed locally in the browser and are not uploaded to pasta.

1. Open the deployed pasta web app.
2. Drop one or more plugin JARs onto the patch area, or use the file picker.
3. Review which files are ready or will be skipped.
4. Patch and download the resulting `patched-*.jar` files.
5. Download `pasta-report.csv` when you need a batch transformation report.

### CLI

Patch one JAR:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
```

Patch every JAR in a directory:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/jars/
```

Choose an output directory:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar --output ./converted path/to/plugin.jar
```

Show CLI help:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar --help
```

Run with no input path to use interactive mode:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar
```

The default output directory is `patched-plugins/`.

### GUI

```bash
java -jar folia-phantom-gui-2.0.0.jar
```

The JavaFX app provides drag-and-drop file selection, per-file state indicators, configurable output, verbose logging, an autoscrolling console, and keyboard shortcuts.

### Plugin (server-side)

```text
/fpatch <plugin-name>   — Patch a specific plugin
/fpatch list            — List patchable plugins
/fpatch status          — Show patching statistics
/fpatch reload          — Reload configuration
```

## Features

- **Bytecode-level transformation** — patches compiled `.class` files without source.
- **Signature removal** — strips `.SF`, `.DSA`, and `.RSA` signatures that would become invalid after rewriting.
- **`plugin.yml` injection** — adds `folia-supported: true` when appropriate.
- **Runtime bridge bundling** — includes Folia runtime adapter classes in patched JARs.
- **Fast-fail scanning** — skips classes that do not require rewriting.
- **Parallel transformation** — uses a `ForkJoinPool` for CPU-heavy class processing outside constrained browser execution.
- **Multiple frontends** — browser, CLI, desktop GUI, and server plugin workflows share the same transformation core.

## Modules

| Module | Description | Output |
|--------|-------------|--------|
| **core** | Transformation engine, ASM visitors, runtime bridge | Library JAR |
| **cli** | Command-line frontend | Fat JAR |
| **gui** | JavaFX desktop frontend | Fat JAR |
| **plugin** | Bukkit/Paper server plugin | Plugin JAR |
| **web** | Java bridge used by the browser app | Browser support JAR |

## Transformers

The core patcher currently applies the transformer chain in this order:

1. **ThreadSafetyTransformer** — adapts thread-sensitive Bukkit operations such as block mutation.
2. **WorldGenClassTransformer** — migrates world-generation-related execution paths.
3. **EntitySchedulerTransformer** — adapts entity-owned scheduling.
4. **PlayerSafetyTransformer** — applies player-related Folia safety rewrites.
5. **SchedulerClassTransformer** — replaces legacy scheduler/BukkitRunnable patterns with runtime bridge calls.

Transformer order is intentional because rewrites may depend on earlier normalization. See [`AGENTS.md`](AGENTS.md) before changing transformation semantics.

## Requirements

- **Java 17+** compatibility target; JDK 21+ recommended for development.
- **Maven 3.8+** for building.
- **Paper API 1.21.1** for the plugin module (`provided` scope).

## Build

```bash
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

Package release-style artifacts with:

```bash
mvn clean package
```

Typical outputs:

| Artifact | Path |
|----------|------|
| CLI | `folia-phantom-cli/target/Folia-Phantom-CLI-2.0.0.jar` |
| GUI | `folia-phantom-gui/target/folia-phantom-gui-2.0.0.jar` |
| Plugin | `folia-phantom-plugin/target/folia-phantom-plugin-2.0.0.jar` |
| Web bridge | `folia-phantom-web/target/` |

## Architecture

```text
Input JAR
  │
  ▼
JarInputStream ──► signature removal
  │
  ├──────────────► plugin.yml compatibility metadata
  │
  ▼
ScanningClassVisitor
  │
  ├── no relevant Bukkit bytecode ──► copy unchanged
  │
  ▼
Transformer chain
  ├─ ThreadSafetyTransformer
  ├─ WorldGenClassTransformer
  ├─ EntitySchedulerTransformer
  ├─ PlayerSafetyTransformer
  └─ SchedulerClassTransformer
  │
  ▼
Runtime bridge classes bundled into output
  │
  ▼
JarOutputStream ──► patched JAR
```

## Runtime bridge

`FoliaPatcher` is bundled into patched JARs and provides runtime adaptations such as:

- routing work to Folia region/global/async schedulers;
- adapting BukkitRunnable-style scheduling;
- scheduling thread-sensitive block mutations on the appropriate region;
- supporting world-generation compatibility paths;
- tracking and cancelling wrapped tasks.

## Contributing

Read [`AGENTS.md`](AGENTS.md) for engineering invariants and [`CONTRIBUTING.md`](CONTRIBUTING.md) for the development workflow. Pull requests should target `develop` unless the maintainers specify otherwise.

## License

MIT License. See [LICENSE](LICENSE).
