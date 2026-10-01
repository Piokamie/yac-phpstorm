# YAC for PhpStorm — agent instructions

Plans live in `.plans/` (gitignored); read the current one before working and tick steps as you go.

## Architecture

- Thin client of the `yac` CLI (repo `Piokamie/yac`, locally `~/projects/yac`). Never reimplement anchor resolution, sidecar parsing or source edits in Kotlin: read through `yac context <file> --stdin --format=json`, change through `yac promote|remove|yeet|extract|inject`. If the plugin needs more data, add it to the CLI's JSON (schema-versioned) first.
- `YacEnvironment`: the only place that maps files to yac. Per file: root (nearest `.yac/` or `.git`, like the CLI's `ProjectRoot::discover`), binary, PHP, and `sourceOf` (root → `.`). Lookups go through the VFS, never `java.nio`, so they are cheap under read actions.
- `YacNotifier` (project service): all notifications, HTML-escaped, group `dev.icebear.yac` (display name from `messages/YacBundle.properties`). `report(key, messages)` shows only messages new for that key, so per-keystroke refreshes do not repeat them; `notifyOnce` for one-off notices; settings apply resets both. User-facing texts shared across packages live in `YacMessages`.
- `cli/`: `YacCli` runs processes (stdin is written on a pooled thread while `run` polls `checkCanceled` and the deadline, killing the process on either; `context` has a timeout, mutating commands none; PHP errors go to stderr so they cannot corrupt the JSON), `YacProcessResult`, `YacCliException`, JSON types, `NoteStatus`, `YacCommands`, `PhpInterpreterPath` (the project's PHP interpreter setting).
- `notes/`: `YacNotesService` (debounced refresh per document; results are dropped when the document text changed meanwhile; `NotesSelection` returns null to keep what is shown while the buffer does not parse), editor/document listeners (main editors only), `YacSidecarListener` (any VFS change under `.yac/` except `.lock` and `.cache/`, or to a `bin/yac`, refreshes open editors; renames and moves refresh that file). The service loads each root's `.yac/` subtree once, because the VFS reports changes only in loaded directories.
- `presentation/`: gutter icons, block inlays, `NoteText` (shared cut-off rules). `actions/`: `YacCommandRunner` (save, cancellable background task, async VFS refresh that refreshes open editors when done, notification), gutter note actions, file actions and `YacFileActionGroup` (hidden when no action applies). `settings/`: per-project settings in the workspace file.
- `actions/undo/`: undo for every action. The runner (`YacCommandRunner`) does a `--dry-run --diff` for promote/extract/inject and trusts `DiffPaths` when the dry run exits 0 or 1 (1 = some notes skipped, the rest still changes); the action's own files are always added. `DiskSnapshot` captures the bytes of those files, of every file under `.yac/` except `.lock`/`.cache/` (single-note actions: only that note's sidecar), and of both `.gitignore` files, before and after the run. It reads with `java.nio` on purpose (VFS bytes can be stale), ignores `.yac-tmp-*` and records which directories existed. `UndoPlan` is computed in the background task. `UndoRecorder` registers `YacRedoGuard` before the document edits and `YacUndoableAction` after them: undo runs steps in reverse and redo in order, so in both directions the exact-bytes check runs before any document changes. `ByteReplay` writes via temp file plus atomic move; IO errors become refusals. `PendingSources` (project service, per-file counts, plus an app-wide index) feeds `PendingSourcesVetoer`, which blocks reload and save of held files while yac runs; after a run without an undo step, held documents that still have their pre-run text are reloaded, and documents the user edited meanwhile are left to the platform's conflict handling. `YacRunState` allows one mutating run per project and makes undo/redo refuse while it runs. Undo/redo do small file IO on the EDT by design.
- Deliberate exception to the rule that the plugin never writes sidecars or source: undo/redo replay exactly the bytes from before or after a yac action, nothing else, and refuse (`UnexpectedUndoException`, nothing written) unless every byte-replayed path (sidecars, `.gitignore` files, sources without a document edit) still holds the other side's exact bytes; PHP text edited through documents follows normal document undo. The JVM cannot take yac's `flock` lock on macOS (`FileChannel.lock` uses `fcntl`), so that check is the only guard against concurrent yac runs.
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
