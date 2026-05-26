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

- `pasta-windows-gui-<version>.zip`
- `pasta-linux-gui-<version>.zip`
- `pasta-cli-<version>.zip`

## Documentation

The GitHub Wiki is being removed. All documentation now lives in this repository under `docs/`.

- [Documentation index](docs/README.md)
- [Next Safe Profile](docs/next-safe-profile.md)
- [Release guide](docs/release.md)

## License

Licensed under the MARV License. See [LICENSE](LICENSE).
