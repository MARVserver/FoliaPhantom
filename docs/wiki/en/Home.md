# pasta Wiki

[日本語](../ja/Home.md) · [Getting Started](Getting-Started.md) · [Repository README](../../../README.md)

## What is pasta?

**pasta** is a bytecode transformation toolkit for adapting legacy Bukkit plugins to Folia's region-threaded execution model. It rewrites compiled plugin JARs, so plugin source code is not required.

The project supports several frontends:

- **Browser** — local drag-and-drop patching through the static web app.
- **GitHub Actions** — patch plugin artifacts in CI with the repository action.
- **CLI** — batch and scripted patching.
- **GUI** — desktop JavaFX workflow.
- **Server plugin** — patch plugins from a compatible Paper/Bukkit server environment.

## Safety boundary

A successful transformation does **not** prove that an arbitrary Bukkit plugin is fully Folia-safe. Bytecode rewriting can adapt known scheduler and thread-sensitive patterns, but plugin-specific mutable state may still be unsafe.

Always test patched plugins on a staging server before production use.

## Browser privacy

The browser frontend runs the patcher locally through CheerpJ. Plugin JAR contents are not intended to be uploaded to a pasta conversion service.

## Main capabilities

- Bytecode-level transformation without plugin source code.
- Removal of invalidated JAR signatures after rewriting.
- `folia-supported: true` metadata injection where appropriate.
- Runtime bridge bundling for rewritten scheduler calls.
- Fast-fail class scanning and parallel transformation where supported.
- Shared transformation core across browser, CLI, GUI, server plugin, and GitHub Actions.

## Requirements

For Java-based builds and packaged workflows, use **JDK 21+**. Maven 3.8+ is required for building the project. pasta-owned classes currently target Java 17 bytecode, while the build JDK must still be Java 21 or newer to resolve current Paper dependencies.

## Next

Start with [Getting Started](Getting-Started.md) for browser, GitHub Actions, CLI, and build instructions.
