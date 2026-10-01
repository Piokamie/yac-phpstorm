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
  - Only main editors show notes (not previews or diffs); renaming or moving a file, creating or deleting `.yac/` directories and installing yac later all refresh open editors
  - Accessible: the gutter icon has a spoken name and its menu opens from the keyboard
• Gutter menu actions: Promote to PHPDoc (resolved notes only) and Remove Note
• `YAC` group in the editor and Project view context menus: Extract Inline Notes, Inject Notes as Inline Comments, Yeet Notes… (with confirmation), for files, directories or the whole project; shown only when a yac binary is found and the selection is under one yac root
  - Documents are saved first; the command has no timeout and can be cancelled from the progress bar; changed files and `.yac/` are reloaded from disk, and a notification shows yac's output and messages (info, warning or error by exit code)
• yac root per file, found like the CLI finds it: the nearest directory with `.yac/` or `.git`; the binary is the nearest `vendor/bin/yac` between the file and that root, then `bin/yac` in the root
• Settings | Tools | YAC: yac binary path, PHP executable, show or hide the note text
  - PHP: the PHP executable setting, else the project's local CLI interpreter, else `php` from the PATH; a remote interpreter is not used, and the plugin says so once
• Undo for every YAC action: Cmd+Z (or Edit | Undo) reverts Promote, Inject, Extract, Remove Note and Yeet in one step, redo re-applies
  - PHP text in open documents is undone like a normal edit; sidecars, both `.gitignore` files and files without an open document get back exactly their bytes from before the action, and undoing a first Extract removes `.yac/` again
  - Undo and redo are refused, writing nothing, when such a file changed since the action (e.g. an agent's `yac add`) or while a yac action is running; only one yac action runs at a time per project
  - Promote, Inject and Extract do a `--dry-run --diff` first to learn which files will change; runs whose preview fails (exit 2), that change more than 200 PHP files, that you cancel, or whose files you edit while yac runs are not undoable, and the plugin says so
  - While yac runs, the IDE neither reloads nor saves the affected documents
• Supports PhpStorm 2025.3 and newer (verified against 2025.3.3 and 2026.2); requires a yac version with `context --stdin`
