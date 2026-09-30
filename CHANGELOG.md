# Change Log

All notable changes to this project will be documented in this file.

The format is based on *Keep a Changelog* (https://keepachangelog.com/en/1.1.0/) and this project adheres to *Semantic Versioning*.

## [Unreleased]

### Added

• MAJOR Show YAC notes in the PhpStorm editor: a gutter icon on every annotated line and a tinted block with the note text above the statement
  - Notes are resolved by the project's own `yac context --stdin`, against the unsaved editor text, 500 ms after typing stops and immediately when a file opens
  - Orphaned, ambiguous and invalid notes get a red icon on line 1; while the file does not parse, the last good notes stay visible
  - Hovering the icon shows ID, scope, status, the full note and any problems; the text block shows at most 3 lines
• Gutter menu actions: Promote to PHPDoc (resolved notes only) and Remove Note
• `YAC` group in the editor and Project view context menus: Extract Inline Notes, Inject Notes as Inline Comments, Yeet Notes… (with confirmation), for files, directories or the whole project
  - Documents are saved first; changed files are reloaded from disk and a notification shows yac's own message (info, warning or error by exit code)
• Settings | Tools | YAC: yac binary path (default `vendor/bin/yac`, then `bin/yac`), fallback PHP executable, show or hide the note text
  - PHP comes from the project's local CLI interpreter; remote and Docker interpreters fall back to the setting or `php`
• Supports PhpStorm 2025.3 and newer (verified against 2025.3.3 and 2026.2); requires a yac version with `context --stdin`
