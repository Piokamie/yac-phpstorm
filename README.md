# YAC for PhpStorm

Shows [YAC](https://github.com/Piokamie/yac) agent notes next to the PHP code they describe, and lets you promote, remove, extract, inject and yeet them without leaving the editor.

YAC keeps comments written by coding agents out of your source: they live in `.yac/` sidecars and are anchored to statements by their code. This plugin is a thin client of the project's own `yac` CLI. It never works out anchors itself and never decides what a sidecar or a source file should contain: every read goes through `yac context` and every change through the matching `yac` command. To make actions undoable it only replays yac's own results: yac's new PHP text into the open documents after a run, and the exact bytes from before or after a run on undo/redo.

## What you get

- **Gutter icon** on every line with a note. Blue means every note on that line is resolved; red means at least one is not (orphaned, ambiguous, invalid anchor, or missing or unparseable source). Unresolved notes have no line and are shown on line 1. Hover for ID, scope, the status of unresolved notes, the full note and any problems.
- **Note block** above the statement: the note icon, a light blue tint and the note text (at most 3 lines). Turn it off in the settings if you only want icons.
- **Live**: notes are resolved against the unsaved editor text 500 ms after you stop typing, when a file opens, and when `.yac/` sidecars change on disk (an agent ran `yac add`, you switched branches). While the edited file does not parse, the notes on screen stay as they are. Only main editors show notes; previews and diffs do not. Renaming or moving a file refreshes its notes.
- **Gutter menu** (click the icon): Promote to PHPDoc, Remove Note. A line with several notes gets one submenu per note.
- **`YAC` context menu** in the editor and the Project view: Extract Inline Notes, Inject Notes as Inline Comments, Yeet Notes… Works on files, directories and the project root, and only appears when a yac binary is found and the whole selection is under one yac root.

Each action saves your documents, runs the CLI in the background (cancel it from the progress bar; there is no timeout), reloads the changed files and `.yac/` from disk and reports yac's own output: an info notification on success, a warning when yac refused (exit 1), an error when it could not run (exit 2).

## Undo

Every action is undoable. Cmd+Z in the affected editor, or Edit | Undo anywhere in the project, reverts the whole action in one step: the PHP text (as a normal editor undo) and the files under `.yac/`, plus the root `.gitignore` if yac's first run created or extended it. Redo re-applies it. Reverted files are saved.

- Sidecars, the `.gitignore` files and any file without an open document get back exactly their bytes from before the action. If one of them was changed again since (an agent ran `yac add`, you switched branches), undo is refused and nothing is written: "Cannot undo: <path> changed after this YAC action. Restore it or discard the change, then try again." Redo works the same way. PHP text in open documents follows normal editor undo.
- Undo and redo are also refused while a yac action is running ("yac is still running; try again when it finishes."). Only one action runs at a time per project; a second one is refused with "Another yac action is still running."
- Promote, Inject and Extract run yac twice: a `--dry-run --diff` first, to learn which files will change, then the real command. A run that skips some notes (exit 1) is still undoable.
- Undoing the first Extract of a project removes `.yac/` again, unless something else has been put in it since.
- Some runs are not undoable, and the plugin says so: the dry run failed ("yac could not preview it first"), the action changes more than 200 PHP files, a file was edited in the editor before yac finished (your typed text is kept), or the changes could not be captured. A cancelled run is not undoable either.
- Limits: the plugin cannot take yac's own file lock; the exact-bytes check is the only guard against a yac run started elsewhere. A restore that fails halfway (an IO error) is reported but not rolled back.

## How yac is found

The plugin finds the yac root for each file the way the CLI does: the nearest directory above it with `.yac/` or `.git`. The CLI runs there, and sources are paths relative to it, so monorepos and projects opened above or below the repository root work.

- **yac binary**: the nearest `vendor/bin/yac` between the file and the root, then `bin/yac` in the root (the yac repository itself). A configured path replaces this lookup.
- **PHP**: the PHP executable from the settings, else the project's local CLI interpreter, else `php` from the PATH. Remote and Docker interpreters are not supported yet; the plugin says so once and uses `php` from the PATH.

Notes are shown only below a root that has `.yac/`. If such a root has no yac binary, or yac cannot run, the notes are cleared and the plugin says why, once, until yac works again.

## Requirements

- PhpStorm 2025.3 or newer.
- A project with [`icebear/yac`](https://github.com/Piokamie/yac) installed (`vendor/bin/yac`) in a version that has `yac context --stdin`.
- A local PHP CLI (the project interpreter, or the PHP executable setting).

## Installation

Until the plugin is on the JetBrains Marketplace, build it and install the zip:

```bash
./gradlew buildPlugin
```

Then **Settings | Plugins | ⚙ | Install Plugin from Disk…** and pick `build/distributions/yac-phpstorm-0.1.0.zip`.

## Settings

**Settings | Tools | YAC** (stored per project in your workspace file, not in shared project files):

| Setting | Default |
|---|---|
| yac binary | Empty: the nearest `vendor/bin/yac`, then `bin/yac` in the root. Otherwise an absolute path, or one relative to the yac root |
| PHP executable | Empty: the project's local interpreter, else `php` from the PATH. Otherwise it overrides the project interpreter |
| Show note text above the code | On |

## Development

Requires JDK 21 (the 2025.3 platform's Java version):

```bash
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
./gradlew build          # compile (warnings are errors) and test
./gradlew verifyPlugin   # compatibility with PhpStorm 2025.3.3 and 2026.2
./gradlew runIde         # sandbox PhpStorm with the plugin installed
```

## License

MIT, see [LICENSE](LICENSE).
