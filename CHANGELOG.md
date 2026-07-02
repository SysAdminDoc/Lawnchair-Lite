# Changelog

All notable changes to Lawnchair-Lite will be documented in this file.

## [v2.27.0] - 2026-07-02

- Added Auto Backup/Data Extraction rules so launcher DataStore state is not silently sent to cloud backup.
- Added manual backup privacy controls for search history, app usage/recents, and hidden apps.
- Preserved omitted private sections during restore instead of clearing them from partial backups.
- Removed the system-only widget-bind permission declaration and documented the Android bind prompt fallback.
- Added an Advanced Settings permission audit for package visibility, crash notifications, notification badges, contacts, calendar, location, and quick-action degradation.
- Added backup export metadata and unit tests for privacy defaults.
- Updated release version metadata to v2.27.0.

## [v2.26.0] - 2026-06-28

- Added Android AppWidget bind permission recovery for third-party widgets that require host approval.
- Added provider configuration flow handling before widget placement.
- Deleted abandoned widget IDs when bind/config flows are canceled or unavailable.
- Added unit coverage for widget grid span placement.
- Updated release version metadata to v2.26.0.

## [v2.25.0] - 2026-06-27

- Added optional dock labels for apps and folders.
- Added dock label opacity control in Dock settings.
- Persisted dock label settings through DataStore and backup export/import.
- Updated release version metadata to v2.25.0.

## [v2.24.0] - 2026-06-27

- Added folder covers with emoji and folder-app icon choices from the folder context menu.
- Added cover rendering for home/dock folder icons while preserving preview fallback behavior.
- Added backward-compatible folder cover serialization and stale-cover cleanup.
- Added model tests for legacy folder serialization and cover round-trips.
- Updated release version metadata to v2.24.0.

## [v2.23.0] - 2026-06-27

- Added Material 3 drawer tabs for All, Recent, Favorites, and Work profile app views.
- Added persisted drawer favorites with context-menu add/remove and backup export/import.
- Added LauncherApps profile-aware app loading and launch support for managed-profile apps.
- Added profile-key model tests while preserving existing personal-profile app keys.
- Updated release version metadata to v2.23.0.

## [v2.22.0] - 2026-06-27

- Added Drawer category rules with app-name regex, package prefix, and install-source matchers.
- Added Settings UI for adding, disabling, and removing category rules.
- Persisted category rules through DataStore and backup export/import.
- Added categorizer unit tests for rule overrides and fallback behavior.
- Updated release version metadata to v2.22.0.

## [v2.21.0] - 2026-06-27

- Added first-party Smartspace weather and next-calendar-event chips on the home clock surface.
- Added location/calendar runtime permission prompts with graceful degraded states.
- Raised Gradle JVM heap for reliable R8 release builds and fixed the release lint gate.
- Updated release version metadata to v2.21.0.

## [v2.20.0] - %Y->- (HEAD -> main, origin/main, origin/HEAD)

- Enable R8 minification for release build
- Changed: Update README.md
- Added: Add files via upload
- v2.20.0: bug fixes, UI polish, swipe-down settings dismiss, default icon shape
- Fixed: fix: faster settings exit animation, revert foreground extraction (native icons already correct)
- Added: feat: NONE icon shape default (native icons, no background), snappier settings dismiss
- Added: feat: swipe-down-to-dismiss settings panel with drag handle and overscroll detection
- ui: audit fixes — label text shadows, wider labels, search bar contrast, bigger fast scroller, brighter drawer handle
- Fixed: fix: remove settings gear from home screen, keep top bar for edit mode only
- Fixed: fix: add text shadow to clock/date for wallpaper readability across all themes
