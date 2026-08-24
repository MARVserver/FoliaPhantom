# pasta — English

[Home](Home.md) · [Getting Started](Getting-Started.md) · [Architecture](Architecture.md) · [日本語](日本語.md)

## What is pasta?

**pasta** (formerly Folia Phantom) converts compiled Bukkit plugin JARs for use with Folia's region-threaded execution model. It rewrites bytecode with ASM without requiring plugin source code or recompilation.

## Ways to use it

- **Browser** — local drag-and-drop patching through the static web app.
- **GitHub Actions** — patch plugin artifacts in CI.
- **CLI** — scripted and batch conversion.
- **GUI** — JavaFX desktop workflow.
- **Server plugin** — patch plugins from a compatible Paper/Bukkit server.

## Main capabilities

- Bytecode-level transformation without source code.
- Signature removal for rewritten JARs.
- `folia-supported: true` metadata injection where appropriate.
- Runtime bridge bundling for scheduler and thread-sensitive operations.
- Fast-fail scanning and parallel transformation where supported.

## Requirements

Use **JDK 21+** for builds and packaged Java workflows. Maven 3.8+ is required for building from source.

## Safety boundary

pasta adapts known compatibility patterns but cannot prove plugin-specific shared state to be thread-safe. Treat patched artifacts as compatibility candidates and test them on a staging Folia server before production.