# Support

Use the channel that best matches what you need so maintainers and contributors can respond efficiently.

## Questions and usage help

Use [GitHub Discussions](https://github.com/MARVserver/pasta/discussions) for:

- setup and usage questions;
- compatibility questions that are not yet confirmed bugs;
- ideas that need exploration before becoming a concrete feature request;
- examples, experiments, and reports about plugins patched with pasta.

When asking for help, include the pasta version or commit, Java version, server implementation/version, how you ran pasta (browser, CLI, GUI, server plugin, or GitHub Action), and relevant logs or error output.

## Confirmed bugs

Use [GitHub Issues](https://github.com/MARVserver/pasta/issues) when you can describe a reproducible defect in pasta. Use the bug report template and provide a minimal reproduction when practical.

A transformed plugin failing under Folia does not automatically mean the transformer is defective: bytecode transformation cannot prove arbitrary plugin state is thread-safe. Please include enough evidence to distinguish a transformation problem from plugin-specific concurrency behavior.

## Feature requests

Use the feature request Issue template for concrete, scoped changes. Broader design ideas should usually start in Discussions so the problem and constraints can be refined first.

## Pull requests

Before opening a Pull Request, read [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md). Pull Requests should normally target `develop`.

## Security-sensitive reports

Do not publish exploit details, credentials, private server data, or other sensitive information in a public Issue or Discussion. Use a private GitHub contact/reporting path available for this repository or contact a maintainer privately before disclosure.
