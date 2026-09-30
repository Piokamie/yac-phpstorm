# Change Log

All notable changes to this project will be documented in this file.

The format is based on *Keep a Changelog* (https://keepachangelog.com/en/1.1.0/) and this project adheres to *Semantic Versioning*.

## [Unreleased]

## [0.1.0] - 2026-09-30

First release: YAC notes in the PhpStorm editor, backed by the project's own `yac` CLI.

### Added

• MAJOR Show YAC notes in the PhpStorm editor: a gutter icon on every annotated line and a tinted block with the note text above the statement
  - Notes are resolved by the project's own `yac context --stdin` against the unsaved editor text: 500 ms after typing stops, when a file opens, and when `.yac/` sidecars change on disk (an agent's `yac add`, a `git checkout`)
  - Unresolved notes (orphaned, ambiguous, invalid anchor, missing or unparseable source) get a red icon on line 1; while the edited file does not parse, the notes on screen stay as they are
  - Hovering the icon shows ID, scope, the status of unresolved notes, the full note and any problems; the text block shows at most 3 lines
  - When yac cannot run, the notes are cleared and the reason is shown once until yac works again
• Gutter menu actions: Promote to PHPDoc (resolved notes only) and Remove Note
• `YAC` group in the editor and Project view context menus: Extract Inline Notes, Inject Notes as Inline Comments, Yeet Notes… (with confirmation), for files, directories or the whole project; shown only where a yac binary is found
  - Documents are saved first; the command has no timeout and can be cancelled from the progress bar; changed files and `.yac/` are reloaded from disk, and a notification shows yac's output and messages (info, warning or error by exit code)
• yac root per file, found like the CLI finds it: the nearest directory with `.yac/` or `.git`; the binary is the nearest `vendor/bin/yac` between the file and that root, then `bin/yac` in the root
• Settings | Tools | YAC: yac binary path, PHP executable, show or hide the note text
  - PHP: the PHP executable setting, else the project's local CLI interpreter, else `php` from the PATH; a remote interpreter is not used, and the plugin says so once
• Supports PhpStorm 2025.3 and newer (verified against 2025.3.3 and 2026.2); requires a yac version with `context --stdin`
