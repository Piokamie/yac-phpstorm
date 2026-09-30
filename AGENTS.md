# YAC for PhpStorm — agent instructions

Plans live in `.plans/` (gitignored); read the current one before working and tick steps as you go.

## Architecture

- Thin client of the `yac` CLI (repo `Piokamie/yac`, locally `~/projects/yac`). Never reimplement anchor resolution, sidecar parsing or source edits in Kotlin: read through `yac context <file> --stdin --format=json`, change through `yac promote|remove|yeet|extract|inject`. If the plugin needs more data, add it to the CLI's JSON (schema-versioned) first.
- `cli/`: process running and JSON types. `notes/`: debounced refresh per document. `presentation/`: gutter icons and block inlays. `actions/`: CLI-backed actions. `settings/`: per-machine settings. `YacEnvironment` is the single place that finds yac and PHP for a project.
- Rendering must never block the EDT: the CLI runs on `Dispatchers.IO`, results are dropped when the document changed meanwhile.

## Code Standards

- Kotlin 2.2.20 is pinned to the stdlib bundled with the lowest supported platform (2025.3). Do not bundle the Kotlin stdlib, kotlinx.coroutines or kotlinx.serialization; the platform provides them.
- Compiler warnings are errors. No deprecated, internal or experimental platform API; `verifyPlugin` must stay clean.
- No comments in code. Constants for magic values, as in the yac repo.

## Testing

- `export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home` first; Gradle's own default JVM on this machine is Homebrew's openjdk 27.
- `./gradlew build` (tests included) and `./gradlew verifyPlugin` (PhpStorm 2025.3.3 and 2026.2) before every commit.
- `buildSearchableOptions` starts a headless IDE on the same sandbox as `runIde` and fails while a `runIde` window is open. Close it, or add `-x buildSearchableOptions`.
- CLI behaviour is tested against `src/test/resources/fake-yac.sh` run through `/bin/sh`; assert exact values.

## Git

- Personal project: commit as `Piotr Kamieniecki <piokamie@gmail.com>` (repo-local config), push through the `github-piokamie` SSH alias, no co-author trailer.
