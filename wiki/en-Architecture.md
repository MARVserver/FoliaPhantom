# Architecture

[Home](https://github.com/MARVserver/pasta/wiki/en-Home) · [Getting Started](https://github.com/MARVserver/pasta/wiki/en-Getting-Started)

## Pipeline

```text
Input JAR
  ↓
remove invalidated signatures + update plugin.yml
  ↓
ScanningClassVisitor
  ↓
ThreadSafetyTransformer
WorldGenClassTransformer
EntitySchedulerTransformer
PlayerSafetyTransformer
SchedulerClassTransformer
  ↓
bundle runtime bridge
  ↓
patched JAR
```

## Modules

| Module | Responsibility |
| --- | --- |
| `core` | ASM visitors, transformer chain, runtime bridge |
| `cli` | command-line frontend |
| `gui` | JavaFX desktop frontend |
| `plugin` | Bukkit/Paper server frontend |
| `web` | browser bridge |

Transformer order is intentional. Later passes may depend on earlier normalization. Runtime testing remains necessary because bytecode rewriting cannot prove arbitrary plugin state thread-safe.
