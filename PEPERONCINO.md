# Peperoncino

Peperoncino is the GitHub Actions integration update for pasta.

The first Peperoncino milestone makes pasta usable directly from a plugin repository's CI pipeline without copying CLI commands into every project.

## GitHub Action

A workflow can patch a built Bukkit plugin JAR with pasta:

```yaml
name: Folia artifact

on:
  pull_request:
  push:
    branches: [main]

jobs:
  folia:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build plugin
        run: mvn --batch-mode --no-transfer-progress package

      - name: Patch for Folia
        id: pasta
        uses: MARVserver/pasta@develop
        with:
          input: target/my-plugin.jar
          output: build/pasta

      - name: Upload Folia artifact
        uses: actions/upload-artifact@v4
        with:
          name: my-plugin-folia
          path: ${{ steps.pasta.outputs.output_directory }}/patched-my-plugin.jar
```

`@develop` is appropriate while Peperoncino is unreleased. Production workflows should pin a released tag or immutable commit SHA once a Peperoncino release is published.

## Inputs

| Input | Required | Default | Description |
|---|---:|---|---|
| `input` | yes | — | Plugin JAR or directory. Relative paths are resolved from `GITHUB_WORKSPACE`. |
| `output` | no | `patched-plugins` | Output directory for patched JARs. |
| `java-version` | no | `21` | JDK used to build and run pasta. |

## Outputs

| Output | Description |
|---|---|
| `output_directory` | Absolute directory containing the patched JARs. |
| `patched_count` | Number of `patched-*.jar` files produced. |

The action fails when the input does not exist, the pasta CLI cannot be built unambiguously, or no patched JAR is produced. This makes path/configuration mistakes visible as CI failures instead of silently succeeding.

## Current execution model

The composite action intentionally builds the pasta CLI from the exact action revision being used, then invokes the existing CLI with `--no-banner` and `--output`.

This has two useful properties:

1. the action and transformer code cannot drift to different revisions;
2. Peperoncino does not introduce another binary distribution channel yet.

The trade-off is build time. Maven dependency caching is enabled, but a future Peperoncino milestone may use signed release artifacts after the release pipeline and provenance model are ready.

## Safety boundary

Peperoncino automates pasta's existing transformation. It does **not** prove that arbitrary plugin state is thread-safe under Folia.

A green Action run means that pasta successfully produced the transformed artifact. It must not be presented as a formal Folia compatibility certification.

Future Peperoncino work can add static-analysis/reporting signals separately from transformation so CI can distinguish:

- transformation success;
- suspicious shared mutable state;
- region/entity/global scheduler ownership findings;
- manual-review requirements.

## Verification

The repository includes `.github/workflows/action-smoke.yml`, which:

1. creates a minimal plugin JAR fixture;
2. runs the repository's own `action.yml`;
3. verifies exactly one output JAR is created;
4. verifies the patched `plugin.yml` contains `folia-supported: true`.
