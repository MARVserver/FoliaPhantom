# Release Guide

## Build Release ZIPs

Run from the repository root:

```powershell
.\scripts\release.ps1
```

The script runs Maven, assembles launchers, and writes ZIP files to `dist/`.

## Release Files

- `pasta-windows-gui-<version>.zip`: Windows GUI launcher and runtime dependencies.
- `pasta-linux-gui-<version>.zip`: Linux GUI launcher and runtime dependencies.
- `pasta-cli-<version>.zip`: CLI launcher scripts and runnable shaded JAR.

## Publishing Checklist

- Upload the three ZIP files from `dist/`.
- Include the rename notice in release notes.
- Link to `docs/README.md` instead of the removed GitHub Wiki.
