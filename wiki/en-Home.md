# pasta — English

[Languages](https://github.com/MARVserver/pasta/wiki/Home) · [Getting Started](https://github.com/MARVserver/pasta/wiki/en-Getting-Started) · [Architecture](https://github.com/MARVserver/pasta/wiki/en-Architecture)

## What is pasta?

**pasta** (formerly Folia Phantom) transforms compiled Bukkit plugin JARs for Folia's region-threaded execution model. It rewrites bytecode with ASM without requiring plugin source code or recompilation.

## Ways to use it

- Browser: local drag-and-drop patching.
- GitHub Actions: patch artifacts in CI.
- CLI: automation and batch conversion.
- GUI: JavaFX desktop workflow.
- Server plugin: patch from a compatible Paper/Bukkit server.

## Requirements

Use **JDK 21+** for builds and packaged Java workflows. Maven 3.8+ is required for source builds.

## Safety boundary

pasta adapts known compatibility patterns but cannot prove plugin-owned shared state to be thread-safe. Validate patched plugins on a staging Folia server before production.
