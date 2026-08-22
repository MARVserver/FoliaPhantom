# AGENTS.md

This file defines the default engineering rules for humans and coding agents working in this repository. A more specific `AGENTS.md` in a subdirectory may override these rules for that subtree.

## Project intent

`pasta` converts legacy Bukkit plugins so they can run more safely on Folia. The core product constraint is correctness under Folia's region-threaded execution model. Convenience changes must not weaken thread-safety, bytecode integrity, or the promise that browser patching remains local to the user's device.

## Repository layout

- `folia-phantom/folia-phantom-core`: ASM transformation engine and runtime bridge.
- `folia-phantom/folia-phantom-cli`: command-line frontend.
- `folia-phantom/folia-phantom-gui`: JavaFX desktop frontend.
- `folia-phantom/folia-phantom-plugin`: Bukkit/Paper server plugin frontend.
- `folia-phantom/folia-phantom-web`: Java bridge used by the browser build.
- `web`: static browser UI loaded by GitHub Pages/CheerpJ.
- `.github/workflows`: CI, Pages, and release automation.

## Required toolchain

- JDK 21 is required for Maven builds. Paper API 1.21.x contains Java 21 class files and cannot be read by a JDK 17 compiler.
- Maven 3.8+.
- Build from `folia-phantom/` with `mvn clean verify`.
- The Maven compiler currently emits pasta-owned classes with `--release 17`. Treat the build JDK and emitted bytecode target as separate compatibility decisions.

Do not raise the emitted Java bytecode target or Paper API version as part of an unrelated change. The browser/CheerpJ path must be reviewed before changing the bytecode target.

## Java conventions

- Use 4 spaces; never tabs.
- UTF-8, LF line endings, final newline.
- Prefer small, single-purpose classes and methods.
- Keep public APIs explicit. Avoid widening visibility only to make implementation easier.
- Avoid wildcard imports.
- Use `final` for constants and for local variables/parameters when it improves clarity; do not add it mechanically everywhere.
- Use SLF4J for application logging. Do not introduce `System.out`/`System.err` outside CLI presentation code or tightly scoped bootstrap diagnostics.
- Log actionable context such as the input path or class name, but never dump plugin contents or sensitive environment data.
- New comments and Javadocs should explain constraints and non-obvious decisions rather than restating the code.
- Prefer English for identifiers, user-facing cross-platform messages, and new technical documentation. Existing Japanese comments do not need churn-only translation.

## Folia and Bukkit threading rules

Thread affinity is a correctness boundary.

- Never introduce legacy `BukkitScheduler` calls into transformed runtime paths when a Folia scheduler is required.
- Region-owned world/block work must run on the owning region scheduler.
- Entity-owned work should use the entity scheduler when ownership can move with the entity.
- Global operations must use the global region scheduler.
- Non-world blocking work belongs on the async scheduler or a deliberately managed executor.
- Do not add a synchronous wait that can deadlock a Folia region thread.
- If thread ownership is uncertain, preserve behavior rather than guessing. Add a focused abstraction or test before broadening a transformer.

## Bytecode transformation rules

- Treat input JARs as untrusted binary data.
- A transformer must either produce a valid class or leave the original class unchanged; do not emit partially transformed bytecode after an exception.
- Preserve unrelated methods, attributes, resources, and manifest data unless the feature explicitly requires a change.
- Keep transformation order intentional. Reordering transformers requires a compatibility rationale.
- Avoid matching only by method name when owner/descriptor information is available.
- Any new ASM rewrite should cover positive and negative cases so unrelated bytecode is not rewritten.
- Runtime bridge changes must remain compatible with classes embedded into patched JARs.

## Destruction-gated verification

This repository uses a non-AI destruction gate for high-risk correctness checks. The implementation side and the verification side are intentionally asymmetric: coding agents may construct production code, but they must not author or tune the oracle that judges that code.

### Trust boundary

- AI/coding agents may create and modify production code, build configuration, and CI wiring.
- AI/coding agents must not create, edit, weaken, regenerate, or replace test sources, test fixtures, snapshots, golden files, fuzz corpora, destructive scenarios, or expected-output/oracle data.
- AI/coding agents must not inspect sealed destructive test source in order to make an implementation pass. Run the gate and work from the public failure category or invariant name instead.
- Never delete, skip, quarantine, narrow, or add `continue-on-error` to a failing destruction check merely to obtain a green build.
- If a production change requires new test coverage, leave the implementation coherent and explicitly record the missing invariant/test case for a human maintainer to add.

### Non-AI oracle rule

- Test execution and pass/fail judgment must be deterministic code. Do not call an LLM, ML model, hosted inference API, agent framework, or AI-assisted scoring service from test code.
- Do not add OpenAI, Anthropic, Gemini/GenAI, LangChain, Ollama, or equivalent AI SDKs to any test scope.
- A destruction test should encode an invariant rather than ask an intelligent system whether an output "looks correct."
- Randomized destruction must use a reproducible seed. A failure report may expose the violated invariant, but agents should not depend on hidden scenario details.

### Initial destruction surface

`PluginPatcherResourceLimitTest` is the initial sealed destructive suite used by CI. It mechanically attacks untrusted JAR handling with oversized compressed input, per-entry expansion, total expansion, discarded signature entries, and excessive entry counts. Coding agents may run this class through the CI gate but should not read or modify it while repairing production behavior.

Destructive suites should preserve these invariants as coverage grows:

1. Untrusted JAR input is bounded by explicit resource limits.
2. A failed patch operation does not leave a partial output artifact.
3. A class transformation failure leaves the original class bytes intact.
4. Unrelated JAR entries remain unchanged unless the product contract explicitly transforms them.
5. Thread-affinity rules remain valid under reordered, repeated, delayed, or failed operations where such fault injection is meaningful.
6. Verification is reproducible and independent from AI services.

The GitHub Actions `Destruction Gate (non-AI)` job is a required engineering signal. Treat a red destruction gate as an invariant violation, not as a test-generation prompt.

## Web rules

The browser frontend is intentionally dependency-light and privacy-preserving.

- Plugin JAR bytes must stay in the browser. Do not add an upload, analytics payload, remote conversion endpoint, or telemetry containing file names/content without an explicit product decision.
- Keep the static `web/` app usable without a framework build step.
- All file controls must be keyboard accessible and have visible focus states.
- Drag-and-drop must be an enhancement, never the only way to select a file.
- Errors should tell the user what happened and what they can do next.
- Revoke temporary object URLs when results are cleared.

## CLI rules

- `--help` must not require a valid input path or initialize the patcher.
- Flags with values must fail clearly when the value is missing.
- Paths containing spaces must work without special handling beyond normal shell quoting.
- Interactive mode should remain available when no input path is supplied.
- Existing one-argument usage must remain backward compatible.

## Change discipline

Before changing code:

1. Read the nearest relevant production source and its build configuration. Coding agents must respect the destruction-gate trust boundary and not inspect sealed test implementation.
2. Search for the same behavior in other production modules before adding a new abstraction.
3. Make the smallest coherent change that satisfies the requirement.
4. Do not combine version bumps, dependency upgrades, formatting sweeps, or package renames with feature work unless they are required.

Before opening a PR:

1. Use JDK 21 and run `mvn clean verify` from `folia-phantom/`.
2. Run the non-AI destruction gate without changing its oracle to fit the implementation.
3. For browser changes, exercise file selection, drag-and-drop, success, partial failure, and reset states in a modern browser.
4. Confirm generated/release artifacts are not committed.
5. Update README or user documentation when CLI/UI behavior changes.

## Commit and PR guidance

Use concise imperative commit subjects, preferably Conventional Commit style:

- `feat: ...`
- `fix: ...`
- `docs: ...`
- `refactor: ...`
- `test: ...`
- `chore: ...`

PR descriptions should state the user problem, the behavioral change, the verification performed, and any compatibility risk.
