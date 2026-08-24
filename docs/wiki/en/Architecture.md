# Architecture

[Home](Home.md) · [Getting Started](Getting-Started.md) · [日本語](../ja/Architecture.md)

pasta transforms plugin JARs at the bytecode level and packages the required runtime bridge into the result.

## Processing pipeline

```text
Input JAR
  │
  ▼
JarInputStream ──► remove invalid signatures
  │
  ├──────────────► update plugin.yml compatibility metadata
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
Bundle runtime bridge classes
  │
  ▼
JarOutputStream ──► patched JAR
```

## Modules

| Module | Responsibility |
| --- | --- |
| `core` | ASM visitors, transformation engine, runtime bridge |
| `cli` | Command-line frontend |
| `gui` | JavaFX desktop frontend |
| `plugin` | Bukkit/Paper server plugin frontend |
| `web` | Java bridge used by the browser application |

## Transformer order

The transformer chain is intentionally ordered:

1. `ThreadSafetyTransformer`
2. `WorldGenClassTransformer`
3. `EntitySchedulerTransformer`
4. `PlayerSafetyTransformer`
5. `SchedulerClassTransformer`

Later rewrites may depend on normalization performed by earlier transformers, so order changes should be treated as semantic changes and verified carefully.

## Runtime bridge

Patched JARs include pasta-owned runtime adapter classes. These adapters route work to Folia region/global/async schedulers, adapt BukkitRunnable-style scheduling, schedule thread-sensitive mutations on the appropriate region, support world-generation compatibility paths, and track wrapped tasks.

## Safety model

The transformer can rewrite known bytecode patterns, but it cannot prove arbitrary plugin state is thread-safe. In particular, plugin-specific shared mutable state can remain unsafe even when scheduler calls are adapted correctly.

Use staging tests as part of the compatibility workflow.
