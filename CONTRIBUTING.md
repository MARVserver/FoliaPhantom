# Contributing to pasta

Thanks for contributing to `pasta` / Folia Phantom. Changes should prioritize Folia correctness, predictable binary transformation, and simple user workflows.

## Prerequisites

- JDK 17 or newer; JDK 21 is recommended.
- Maven 3.8+.
- Git.
- A modern browser for changes under `web/`.

## Build and verify

From the repository root:

```bash
cd folia-phantom
mvn clean verify
```

To package distributable JARs:

```bash
mvn clean package
```

Artifacts are produced under each module's `target/` directory. Do not commit build outputs.

## Development workflow

1. Branch from `develop`.
2. Keep the change focused on one feature or fix.
3. Follow `AGENTS.md` and any more specific rules in the subtree you modify.
4. Build and test locally.
5. Update user-facing documentation when behavior or commands change.
6. Open a PR against `develop` with verification notes and compatibility risks.

Suggested branch names:

- `feature/<short-name>`
- `fix/<short-name>`
- `docs/<short-name>`
- `refactor/<short-name>`

## Code style

### Java

- Java 17-compatible language/API features only unless a coordinated compatibility change says otherwise.
- 4 spaces, no tabs.
- No wildcard imports.
- Prefer explicit control flow over clever compact expressions in transformer code.
- Use SLF4J for runtime logging.
- Keep exceptions informative; include the class/file being processed when available.
- Avoid broad catch-and-ignore behavior. If a class transformation fails, preserve the original class and report the failure.

### JavaScript and HTML

- Keep `web/` framework-free unless the project intentionally adopts a build pipeline.
- Use `const` by default and `let` only when reassignment is required.
- Prefer small functions with one responsibility.
- Use DOM APIs safely; assign user-controlled text through `textContent`, not `innerHTML`.
- Keep keyboard operation and visible focus states working.
- Do not upload plugin JARs or add hidden telemetry.

### Documentation

- Keep commands copy-pasteable.
- Prefer concrete examples over long conceptual prose.
- Mention the affected module when a workflow differs between CLI, GUI, plugin, and browser use.

## Testing expectations

For core transformer changes, verify at minimum:

- a class that should be transformed;
- a class that should not be transformed;
- malformed or unsupported input behavior;
- no regression in JAR/resource copying;
- the patched JAR remains readable and loadable.

For CLI changes, verify:

- a single JAR path;
- a directory path;
- interactive mode with no arguments;
- `--help` and any new option error paths.

For browser changes, verify:

- file-picker selection;
- drag-and-drop when supported;
- rejection of non-JAR/already-patched files;
- progress and completion messages;
- successful download;
- partial/failed result rendering;
- reset/clear behavior.

## Commit messages

Conventional Commit-style subjects are preferred:

```text
feat: add browser drag and drop
fix: preserve original class after transform failure
docs: document Folia threading rules
```

Keep commits reviewable and avoid unrelated formatting churn.

## Pull requests

A good PR description includes:

- **Problem:** what user/developer issue is being solved.
- **Change:** the important behavioral or architectural decisions.
- **Verification:** commands and manual checks performed.
- **Risk:** compatibility, bytecode, scheduler, or deployment concerns.

Changes that alter transformation semantics should include a focused explanation of why the rewrite is safe under Folia's ownership model.