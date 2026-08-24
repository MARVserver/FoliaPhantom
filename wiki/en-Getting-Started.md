# Getting Started

[Home](https://github.com/MARVserver/pasta/wiki/en-Home) · [Architecture](https://github.com/MARVserver/pasta/wiki/en-Architecture)

## Browser

1. Open the pasta web app.
2. Drop one or more plugin JARs into the patch area.
3. Review ready and skipped files.
4. Patch and download `patched-*.jar`.
5. For batches, download `pasta-report.csv` when needed.

The browser workflow is designed to process plugin JARs locally on the user's device.

## GitHub Actions

```yaml
- name: Patch for Folia
  id: pasta
  uses: MARVserver/pasta@develop
  with:
    input: target/my-plugin.jar
    output: build/pasta
```

For production CI, pin a release tag or immutable commit SHA.

## CLI

```bash
java -jar Folia-Phantom-CLI-2.0.0.jar path/to/plugin.jar
java -jar Folia-Phantom-CLI-2.0.0.jar --output ./converted path/to/plugin.jar
```

## Build

```bash
git clone https://github.com/MARVserver/pasta.git
cd pasta/folia-phantom
mvn clean verify
```

Use JDK 21+ and Maven 3.8+.
