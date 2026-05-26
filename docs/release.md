# Release Guide

## Build Release ZIPs

Run from the repository root:

```powershell
.\scripts\release.ps1
```

The script runs Maven, assembles launchers, and writes ZIP files to `dist/`.

## Release Files

- `pasta-folia-windows-gui-<version>.zip`
- `pasta-folia-linux-gui-<version>.zip`
- `pasta-folia-cli-<version>.zip`
- `pasta-shreddedpaper-windows-gui-<version>.zip`
- `pasta-shreddedpaper-linux-gui-<version>.zip`
- `pasta-shreddedpaper-cli-<version>.zip`
- `pasta-canvas-windows-gui-<version>.zip`
- `pasta-canvas-linux-gui-<version>.zip`
- `pasta-canvas-cli-<version>.zip`
- `pasta-horizon-windows-gui-<version>.zip`
- `pasta-horizon-linux-gui-<version>.zip`
- `pasta-horizon-cli-<version>.zip`

## Publishing Checklist

- Upload the server-family ZIP files from `dist/`.
- Include the rename notice in release notes.
- Link to `docs/README.md` instead of the removed GitHub Wiki.
