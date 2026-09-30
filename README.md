# YAC for PhpStorm

Shows [YAC](https://github.com/Piokamie/yac) agent notes next to the PHP code they describe, and lets you promote, remove, extract, inject and yeet them without leaving the editor.

YAC keeps comments written by coding agents out of your source: they live in `.yac/` sidecars and are anchored to statements by their code. This plugin is a thin client of the project's own `yac` CLI. It never works out anchors itself and never writes sidecars or source files; every read goes through `yac context` and every change through the matching `yac` command.

## What you get

- **Gutter icon** on every line with a note. Blue means resolved. Red means the note is not resolved: orphaned, ambiguous, invalid anchor, or missing or unparseable source; those have no line and are shown on line 1. Hover for ID, scope, the status of unresolved notes, the full note and any problems.
- **Note block** above the statement: the note icon, a light blue tint and the note text (at most 3 lines). Turn it off in the settings if you only want icons.
- **Live**: notes are resolved against the unsaved editor text 500 ms after you stop typing, when a file opens, and when `.yac/` sidecars change on disk (an agent ran `yac add`, you switched branches). While the edited file does not parse, the notes on screen stay as they are.
- **Gutter menu** (click the icon): Promote to PHPDoc, Remove Note. A line with several notes gets one submenu per note.
- **`YAC` context menu** in the editor and the Project view: Extract Inline Notes, Inject Notes as Inline Comments, Yeet Notes… Works on files, directories and the project root, and only appears where a yac binary is found.

Each action saves your documents, runs the CLI in the background (cancel it from the progress bar; there is no timeout), reloads the changed files and `.yac/` from disk and reports yac's own output: an info notification on success, a warning when yac refused (exit 1), an error when it could not run (exit 2).

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

**Settings | Tools | YAC** (stored per machine, not in shared project files):

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
