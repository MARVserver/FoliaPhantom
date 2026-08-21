# pasta browser frontend

The static `web/` frontend runs the same Java patcher core through CheerpJ. Plugin JAR bytes stay in the browser; there is no upload or conversion backend.

## Runtime behavior

When one or more patchable JARs are selected, the page starts warming the CheerpJ Java runtime immediately. The Patch button reuses that same initialization promise, so selection time overlaps the cold-start cost instead of waiting to initialize Java only after the user clicks.

Browser mode constructs `PluginPatcher` with parallel transformation disabled. In this mode, the core patcher reads one retained JAR entry, transforms it, writes it to the output JAR, and releases the entry before reading the next one. Desktop and CLI paths keep the existing parallel preparation path.

Both `plugin.yml` and `paper-plugin.yml` receive `folia-supported: true`. Transformation success still does not certify arbitrary plugin state as Folia thread-safe.

## Local performance metrics

The UI records, using `performance.now()`:

- CheerpJ runtime initialization time;
- end-to-end processing time for each selected JAR;
- input JAR byte size.

These metrics are displayed locally and included in `pasta-report.csv` as `runtime_init_ms`, `patch_ms`, and `input_bytes`. They are not sent anywhere.

## Browser end-to-end test

The Playwright test exercises the real static UI with Chromium and CheerpJ:

1. drag-and-drop selection;
2. clear/reset behavior;
3. `paper-plugin.yml` transformation and downloaded JAR inspection;
4. a deliberately malformed class that must produce a partial result rather than aborting the JAR;
5. locally generated timing columns in the CSV report.

Prerequisites:

- JDK 21, including the `jar` command;
- Node.js 22+;
- Python 3;
- `unzip`.

Build and run:

```bash
cd folia-phantom
mvn --batch-mode --no-transfer-progress -pl folia-phantom-web -am package
cd ..
cp folia-phantom/folia-phantom-web/target/pasta-web.jar web/pasta-web.jar

cd web
npm install --no-package-lock --no-audit --no-fund
npx playwright install chromium
npm run test:e2e
```

The GitHub Pages workflow runs the same Chromium E2E test on pull requests targeting `develop` and gates deployment on `develop` pushes.
