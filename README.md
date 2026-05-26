# pasta

pasta is the renamed successor to FoliaPhantom. It patches legacy Bukkit plugin JARs for Folia-compatible server environments by rewriting selected bytecode patterns and adding local audit metadata.

## Rename Notice

FoliaPhantom has been renamed to **pasta**.

The old package/module paths remain in source for compatibility during the transition, but public documentation, release artifacts, and user-facing names now use pasta. Existing FoliaPhantom users should download pasta releases going forward.

Related projects:

- [MultiPaper/ShreddedPaper](https://github.com/MultiPaper/ShreddedPaper)
- [CraftCanvasMC/Canvas](https://github.com/CraftCanvasMC/Canvas)
- [MARVserver/pasta](https://github.com/MARVserver/pasta)

## Editions

- **Windows GUI**: JavaFX desktop launcher for Windows users.
- **Linux GUI**: JavaFX desktop launcher for Linux users.
- **CLI**: Headless command-line tool for automation and servers.
- **Plugin**: Bukkit/Paper plugin module for server-side workflows.

## Server Compatibility Builds

Release ZIPs are produced for these server families:

- Folia
- ShreddedPaper
- Canvas
- Horizon

Each server-family package contains the same pasta patching engine plus `SERVER-COMPATIBILITY.txt` so operators can pick the package that matches their staging target.

## Build

Requires JDK 21+ and Maven.

```bash
mvn -f folia-phantom/pom.xml clean package
```

## Release Artifacts

Create local release ZIPs:

```powershell
.\scripts\release.ps1
```

The generated files are written to `dist/`:

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

## Documentation

The GitHub Wiki is being removed. All documentation now lives in this repository under `docs/`.

- [Documentation index](docs/README.md)
- [Next Safe Profile](docs/next-safe-profile.md)
- [Release guide](docs/release.md)

## License

Licensed under the MARV License. See [LICENSE](LICENSE).
