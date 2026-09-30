# YAC for PhpStorm — agent instructions

Plans live in `.plans/` (gitignored); read the current one before working and tick steps as you go.

## Architecture

- Thin client of the `yac` CLI (repo `Piokamie/yac`, locally `~/projects/yac`). Never reimplement anchor resolution, sidecar parsing or source edits in Kotlin: read through `yac context <file> --stdin --format=json`, change through `yac promote|remove|yeet|extract|inject`. If the plugin needs more data, add it to the CLI's JSON (schema-versioned) first.
- `YacEnvironment`: the only place that maps files to yac. Per file: root (nearest `.yac/` or `.git`, like the CLI's `ProjectRoot::discover`), binary, PHP, and `sourceOf` (root → `.`). Lookups go through the VFS, never `java.nio`, so they are cheap under read actions.
- `YacNotifier` (project service): all notifications, HTML-escaped, group `dev.icebear.yac` (display name from `messages/YacBundle.properties`). `report(key, messages)` shows only messages new for that key, so per-keystroke refreshes do not repeat them; `notifyOnce` for one-off notices; settings apply resets both. User-facing texts shared across packages live in `YacMessages`.
- `cli/`: `YacCli` runs processes (stdin is written on a pooled thread while `run` polls `checkCanceled` and the deadline, killing the process on either; `context` has a timeout, mutating commands none; PHP errors go to stderr so they cannot corrupt the JSON), `YacProcessResult`, `YacCliException`, JSON types, `NoteStatus`, `YacCommands`, `PhpInterpreterPath` (the project's PHP interpreter setting).
- `notes/`: `YacNotesService` (debounced refresh per document; results are dropped when the document text changed meanwhile; `NotesSelection` returns null to keep what is shown while the buffer does not parse), editor/document listeners (main editors only), `YacSidecarListener` (any VFS change under `.yac/` except `.lock` and `.cache/`, or to a `bin/yac`, refreshes open editors; renames and moves refresh that file). The service loads each root's `.yac/` subtree once, because the VFS reports changes only in loaded directories.
- `presentation/`: gutter icons, block inlays, `NoteText` (shared cut-off rules). `actions/`: `YacCommandRunner` (save, cancellable background task, async VFS refresh that refreshes open editors when done, notification), gutter note actions, file actions and `YacFileActionGroup` (hidden when no action applies). `settings/`: per-project settings in the workspace file.
- Rendering must never block the EDT: the CLI runs on `Dispatchers.IO` or in a `Task.Backgroundable`.
- `YacCli` recognises a yac without `--stdin` by Symfony Console's `The "--stdin" option does not exist.`; if yac's console layer changes, update `MISSING_STDIN_OPTION`.

## Code Standards

- Kotlin 2.2.20 is pinned to the stdlib bundled with the lowest supported platform (2025.3). Do not bundle the Kotlin stdlib, kotlinx.coroutines or kotlinx.serialization; the platform provides them.
- Compiler warnings are errors. No deprecated, internal or experimental platform API; `verifyPlugin` must stay clean.
- No comments in code. Constants for magic values, as in the yac repo.

## Testing

- `export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home` first; Gradle's own default JVM on this machine is Homebrew's openjdk 27.
- `./gradlew build` (tests included) and `./gradlew verifyPlugin` (PhpStorm 2025.3.3 and 2026.2) before every commit.
- `buildSearchableOptions` starts a headless IDE on the same sandbox as `runIde` and fails while a `runIde` window is open. Close it, or add `-x buildSearchableOptions`.
- CLI behaviour is tested against `src/test/resources/fake-yac.sh` run through `/bin/sh` (`YacTestCase.useFakePhp()`; `TemporaryTree.fakeYac()` installs it as `vendor/bin/yac` in a real temp directory). Extend `YacTestCase`: it resets settings and the notifier. Assert exact values.

## Git

- Personal project: commit as `Piotr Kamieniecki <piokamie@gmail.com>` (repo-local config), push through the `github-piokamie` SSH alias, no co-author trailer.
