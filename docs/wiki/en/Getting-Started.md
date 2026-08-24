# Getting Started

[Home](Home.md) · [日本語](../ja/Getting-Started.md)

Choose the workflow that best matches how you use pasta.

## Browser

Use the browser app when you want a quick local conversion without installing a desktop application.

1. Open the deployed pasta web app.
2. Drop one or more plugin JARs onto the patch area, or use the file picker.
3. Review which files are ready and which will be skipped.
4. Run the patch operation.
5. Download the generated `patched-*.jar` files.
6. For batch jobs, download `pasta-report.csv` when you need a transformation report.

The browser path is designed to keep plugin JAR bytes on the user's device.

## GitHub Actions

Use the composite action from a plugin project's workflow:

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

While a change is unreleased, `develop` can be used for evaluation. For production CI, prefer a release tag or immutable commit SHA once the intended release is available.

## CLI

Patch one JAR:

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
```

Patch every JAR in a directory:

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

With no input path, the CLI enters interactive mode. The default output directory is `patched-plugins/`.

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

Treat transformed plugins as compatibility candidates, not as automatically certified Folia-safe plugins. Exercise plugin behavior on a staging server, especially scheduling, entity operations, world/block mutations, and plugin-specific shared state.
