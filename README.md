# YAC for PhpStorm

Shows [YAC](https://github.com/Piokamie/yac) agent notes next to the PHP code they describe, and lets you promote, remove, extract, inject and yeet them without leaving the editor.

YAC keeps comments written by coding agents out of your source: they live in `.yac/` sidecars and are anchored to statements by their code. This plugin is a thin client of the project's own `yac` CLI. It never works out anchors itself and never writes sidecars or source files; every read goes through `yac context` and every change through the matching `yac` command.

## What you get

- **Gutter icon** on every line with a note. Blue means resolved; red means orphaned, ambiguous or invalid (those have no line and are shown on line 1). Hover for ID, scope, status and the full note.
- **Note block** above the statement: the note icon, a light blue tint and the note text (at most 3 lines). Turn it off in the settings if you only want icons.
- **Live while you type**: the notes are resolved against the unsaved editor text 500 ms after you stop typing. While the file does not parse, the last good notes stay on screen.
- **Gutter menu** (click the icon): Promote to PHPDoc, Remove Note. A line with several notes gets one submenu per note.
- **`YAC` context menu** in the editor and the Project view: Extract Inline Notes, Inject Notes as Inline Comments, Yeet Notes… Works on files, directories and the project root.

Each action saves your documents, runs the CLI in the background, reloads the changed files from disk and reports yac's own message: an info notification on success, a warning when yac refused (exit 1), an error when it could not run (exit 2).

## Requirements

- PhpStorm 2025.3 or newer.
- A project with [`icebear/yac`](https://github.com/Piokamie/yac) installed (`vendor/bin/yac`) in a version that has `yac context --stdin`.
- A local PHP CLI interpreter configured for the project. Remote and Docker interpreters are not supported yet; the plugin then falls back to the PHP executable from its settings, or `php` on the PATH.

Projects without yac are left alone. If a project has `.yac/` but no `yac` binary, the plugin says so once.

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
| yac binary | `vendor/bin/yac`, then `bin/yac` (relative to the project root) |
| PHP executable | Used only without a local project interpreter; empty means `php` from the PATH |
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
