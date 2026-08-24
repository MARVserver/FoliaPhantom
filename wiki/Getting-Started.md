# Getting Started

[English](English.md) · [Architecture](Architecture.md) · [日本語版](はじめに.md)

Choose the workflow that matches your use case.

## Browser

1. Open the deployed pasta web app.
2. Drop one or more plugin JARs into the patch area or use the file picker.
3. Review files that are ready or skipped.
4. Run the patch operation.
5. Download the generated `patched-*.jar` files.
6. For batches, download `pasta-report.csv` when needed.

The browser path is designed to process plugin JARs locally on the user's device.

## GitHub Actions

```yaml
- name: Patch for Folia
  id: pasta
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta

- name: Upload patched artifact
  uses: actions/upload-artifact@v4
  with:
    name: my-plugin-folia
    path: ${{ steps.pasta.outputs.output_directory }}/patched-my-plugin.jar
```

For production CI, prefer a release tag or immutable commit SHA when an appropriate release is available.

## CLI

Patch one JAR:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
```

Patch a directory:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/jars/
```

Choose an output directory:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar --output ./converted path/to/plugin.jar
```

Show help:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar --help
```

The default output directory is `patched-plugins/`.

## Build from source

Requirements:

- JDK 21+
- Maven 3.8+

```bash
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

For release-style packages:

```bash
mvn clean package
```

## Verify before production

Exercise scheduler behavior, entity operations, world/block mutations, and plugin-specific shared state on a staging Folia server before deployment.