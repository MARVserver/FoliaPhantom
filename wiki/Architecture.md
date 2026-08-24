# Architecture

[English](English.md) · [Getting Started](Getting-Started.md) · [日本語版](アーキテクチャ.md)

## Processing pipeline

```text
Input JAR
  │
  ▼
JarInputStream ──► remove invalidated signatures
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
| `core` | ASM visitors, transformer chain, runtime bridge |
| `cli` | Command-line frontend |
| `gui` | JavaFX desktop frontend |
| `plugin` | Bukkit/Paper server-side frontend |
| `web` | Browser bridge used by the static app |

## Transformer order

The transformer order is intentional. Later transformations may depend on normalization performed by earlier passes.

1. `ThreadSafetyTransformer`
2. `WorldGenClassTransformer`
3. `EntitySchedulerTransformer`
4. `PlayerSafetyTransformer`
5. `SchedulerClassTransformer`

## Runtime bridge

The bundled `FoliaPatcher` runtime bridge adapts rewritten calls at runtime, including region/global/async scheduling, BukkitRunnable-style scheduling, thread-sensitive block operations, world-generation compatibility paths, and task tracking/cancellation.

## Safety model

The transformer can rewrite recognized bytecode patterns. It cannot establish a formal proof that arbitrary plugin-owned mutable state is safe under Folia's threading model. Runtime verification remains necessary.