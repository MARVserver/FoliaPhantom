# pasta community

pasta is an open technical project for improving Bukkit-to-Folia compatibility through bytecode transformation and related tooling. The community is organized around reproducible engineering work: concrete examples, minimal test cases, clear compatibility constraints, and reviewable changes.

## Where to participate

- **GitHub Discussions** — questions, early-stage ideas, usage reports, experiments, and general project conversation: https://github.com/MARVserver/pasta/discussions
- **GitHub Issues** — reproducible bugs and concrete feature requests: https://github.com/MARVserver/pasta/issues
- **Pull Requests** — focused implementation and documentation changes. Read [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md) first.

## Good community reports

Useful reports usually include:

1. the pasta release, branch, or commit;
2. execution mode: browser, GitHub Action, CLI, GUI, server plugin, or custom integration;
3. Java version and server implementation/version when relevant;
4. a minimal or clearly described input plugin/JAR;
5. expected and actual behavior;
6. logs, stack traces, transformed output, or a minimal reproduction;
7. whether the problem also occurs with a current release or recent `develop` commit.

Do not publish credentials, private server data, proprietary artifacts you cannot share, or unrelated personal information.

## From idea to contribution

A typical contribution path is:

1. Start in Discussions if the problem or design is still unclear.
2. Open an Issue once there is a concrete defect or outcome to track.
3. Agree on scope and compatibility constraints for non-trivial changes.
4. Branch from `develop` and implement a focused change.
5. Run the relevant verification described in [CONTRIBUTING.md](CONTRIBUTING.md).
6. Open a Pull Request against `develop` with problem, change, verification, and risk notes.

Small documentation fixes can go directly to a Pull Request when the intended correction is unambiguous.

## Technical discussion standard

pasta changes can affect bytecode, scheduler behavior, Folia ownership rules, Java compatibility, and plugin runtime behavior. Strong technical disagreement is acceptable; unsupported personal criticism is not. Prefer evidence that can be reproduced and reviewed.

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for participation expectations and [SUPPORT.md](SUPPORT.md) for choosing the right support channel.
