# Changelog

All notable changes to Lawnchair-Lite will be documented in this file.

## [v2.27.0] - 2026-07-02

- Added Auto Backup/Data Extraction rules so launcher DataStore state is not silently sent to cloud backup.
- Added manual backup privacy controls for search history, app usage/recents, and hidden apps.
- Added selective launcher backup sections for appearance, layout/widgets, drawer/search, gestures, feature settings, custom labels, and private data.
- Added cloud backup target support through Android document providers for Drive, OneDrive, WebDAV, and other JSON destinations.
- Added weekly local backup scheduling to the app-specific external `backups` folder with a manual run-now action.
- Preserved omitted private sections during restore instead of clearing them from partial backups.
- Removed the system-only widget-bind permission declaration and documented the Android bind prompt fallback.
- Added an Advanced Settings permission audit for package visibility, crash notifications, notification badges, contacts, calendar, location, and quick-action degradation.
- Added a restore preview that validates backup schema compatibility and reports sections, skipped values, unknown fields, and private-data handling before import.
- Added local diagnostics storage plus Settings copy/share/delete actions for crash reports and support bundles.
- Added work-profile app badges, profile-aware app info, and managed-profile uninstall blocking.
- Added a tablet/foldable drawer layout that keeps the workspace and dock/taskbar visible beside a bounded right-side drawer pane.
- Added local semantic drawer search for intent phrases such as "my gym app", "password vault", and "food delivery" without remote AI dependencies.
- Added an icon-pack theme metadata API so packs can suggest an accent color and wallpaper links in the Settings picker.
- Added unread notification aggregation to the first-party At-a-Glance card alongside weather, calendar, and alarm signals.
- Added a local adb smoke harness for launcher launch, drawer search, Settings, and widget-picker checks.
- Hardened the adb smoke harness against Compose UI attribute-order changes and duplicate home-menu labels.
- Added accessibility semantics for custom Compose controls plus instrumentation coverage for search and home menu actions.
- Added resource-backed UI strings, localized enum display labels, and a partial Spanish smoke resource file for translation workflow validation.
- Split search scoring, Smartspace loading, widget placement decisions, and backup restore orchestration into focused services with unit coverage.
- Improved app drawer search with diacritic folding, common transliteration, initials, aliases, and typo-tolerant matching.
- Added cached web search suggestions for engines with JSON suggestion APIs, surfaced inline in drawer search.
- Added drawer groups/folders that filter All/Recent/Favorites/Work tabs by selected apps or package-prefix rules and persist through launcher backups.
- Added widget picker preview imagery with icon/placeholder fallbacks, unavailable-preview copy, and confirmation before widget host IDs are removed.
- Added widget stacks so edit-mode widgets can add compatible widgets into the same grid slot, swipe between stack pages, and remove the active stack item without clearing the rest.
- Added shortcut/PWA icon overrides with pinned shortcut cells, live LauncherApps shortcut pinning, backup persistence, and safe source-app icon fallback.
- Added Nova Launcher backup migration import with previewed unsupported items before restore.
- Added Fastlane/F-Droid metadata plus a local libre audit gate for permission, backup-rule, repository, and proprietary-SDK checks.
- Added Android 12+ RenderEffect blur behind the app drawer with safe fallback on older devices.
- Added per-page wallpaper dim overrides with backup persistence and page-removal shifting.
- Added per-app swipe-up shortcut bindings from app long-press menus with backup persistence.
- Added a custom draw gesture recorder for freeform home-screen gesture actions with backup persistence.
- Added a global shortcut shelf for arbitrary app shortcuts, opened from the dock handle and persisted through backups.
- Added assistant replacement from bottom-corner swipe-up, with selectable app or system assistant fallback.
- Added a startup-focused Baseline Profile with ProfileInstaller for faster first-run launcher paths.
- Enabled explicit R8 full-mode release optimization with metadata keep rules for retained launcher models.
- Replaced the icon-pack drawable cache with a byte-bounded bitmap LRU that recycles evicted entries and keeps misses bounded.
- Added an icon pack mixer that applies multiple installed icon packs in priority order with fallback persistence through backups and theme files.
- Added custom font import from persisted local font files with validation, app-wide text application, and system-font reset.
- Added hidden-state app drawer grid pre-warming to compose the first offscreen rows before the initial scroll.
- Added an Android 12+ Material You dynamic color toggle that applies wallpaper-derived accents per selected theme.
- Added `.lawnchair-theme` import/export for theme mode, dynamic color, accent, icon pack, and icon appearance options.
- Updated the Android build lane to Gradle 9.4.1, AGP 9.2.1 built-in Kotlin, Compose compiler 2.3.21, Compose BOM 2026.06.01, compile SDK 37, target SDK 36, and current stable AndroidX libraries while removing Accompanist DrawablePainter.
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

## Roadmap archive — 2026-08-10 — ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# Lawnchair Lite Roadmap

Minimal, fast Android launcher built on Jetpack Compose with professional-grade stability (v2.26.0). Stability architecture already landed; roadmap pushes performance, visual polish, and feature-parity with premium launchers.

## Planned Features

### Home & Drawer

### Gestures & Actions

### Performance

### Themeing

### Widgets & Info

### Settings & Backup

### Stability

## Competitive Research
- **Lawnchair (upstream)** â€” Lite is derived from; pull performance/stability fixes from main branch, keep feature set intentionally smaller.
- **Nova Launcher** â€” gold-standard paid launcher; reference for gesture flexibility and backup granularity.
- **Smart Launcher 6** â€” excellent auto-categorization; borrow UX not implementation.
- **Niagara Launcher** â€” list-first minimalist; proves there's appetite for minimal. Lawnchair Lite should keep that ethos without becoming Niagara-shaped.
- **Kvaesitso** â€” open-source, search-first; reference for fuzzy-search ranking.

## Nice-to-Haves

## Open-Source Research (Round 2)

### Related OSS Projects
- LawnchairLauncher/lawnchair â€” https://github.com/LawnchairLauncher/lawnchair â€” upstream; Lawnchair 16 on Launcher3 from Android 16; this project's direct parent
- MM2-0/Kvaesitso â€” https://github.com/MM2-0/Kvaesitso â€” search-first launcher, universal search across apps/files/cloud/contacts/wikipedia; v1.40.1-fdroid (Apr 2026)
- Posidon â€” https://github.com/Posidon-Software/posidon_launcher â€” one-page launcher with RSS feed, One UI-inspired
- Olauncher â€” https://github.com/tanujnotes/Olauncher â€” minimal text-only launcher (no icons)
- NeoApplications/Neo-Launcher â€” https://github.com/NeoApplications/Neo-Launcher â€” highly customizable FOSS launcher, another Launcher3 derivative
- KISS Launcher â€” https://github.com/Neamar/KISS â€” <2MB, Android 5+, zero-background
- Rootless Pixel Launcher (amirzaidi/Launcher3) â€” closest-to-AOSP reference; minimal patch over Launcher3
- T-UI Launcher â€” https://github.com/ossrs/t-ui-launcher â€” terminal-shell style, type-to-launch
- Smartspacer â€” https://github.com/KieronQuinn/Smartspacer â€” system-level At-a-Glance replacement; Lawnchair integrates with it
- QuickSwitch â€” Lawnchair's recents bridge; reference for Android 10-15 integration (root)

### Features to Borrow
- **Universal search bar from home screen** (Kvaesitso) â€” apps + contacts + files + calendar + web results in one query field; top-tier QoL addition
- **Scrollable widget canvas as optional home page** (Kvaesitso) â€” decouples widgets from grid cells
- **Local calculator + unit converter in search** (Kvaesitso) â€” no network, instant answers
- **At-a-Glance via Smartspacer integration** (Lawnchair upstream) â€” weather / calendar / media card at top of home; "Lite" can still include it
- **Per-app icon override** (Neo Launcher, Lawnchair) â€” with icon-pack support; already a Lawnchair feature, make sure Lite preserves
- **Material You wallpaper-color theming** (Lawnchair upstream) â€” inherit free from base
- **App drawer search with fuzzy match** (KISS) â€” typo-tolerant; KISS's CompareString algorithm is a good reference
- **Gesture: swipe-up-on-home to open drawer** (Lawnchair, Posidon) â€” ensure preserved; one-finger reachability
- **Hide app from drawer / private space** (Lawnchair, Neo) â€” hidden-apps list with PIN reveal

### Patterns & Architectures Worth Studying
- Lawnchair's **Launcher3 fork-rebase cadence** â€” they rebase onto each new Android's Launcher3 rather than diverging; makes AOSP upgrades tractable. "Lite" must preserve this discipline
- Kvaesitso's **search provider plugin model** â€” each search source (apps/files/wiki/cloud) is an independent provider implementing a common interface; users toggle providers individually
- Smartspacer's **virtual widget host** â€” shows how to inject a "pluggable" widget slot without being tied to one content source
- Posidon's **single-page canvas** vs. multi-page pager â€” architectural choice; "Lite" should document which model it commits to
- **Strip-for-size tactics** â€” to earn the "Lite" name, benchmark baseline Lawnchair APK (~30MB) and prune: drop Hermes/flutter deps if present, disable unused icon packs at build, aggressively R8-shrink; KISS is the north star at <2MB

## Implementation Deep Dive (Round 3)

### Reference Implementations to Study
- **amirzaidi/Launcher3/src/com/android/launcher3/widget/WidgetHostViewLoader.java** â€” https://github.com/amirzaidi/Launcher3 â€” canonical `AppWidgetHostView` bind + `bindAppWidgetIdIfAllowed` + fallback to widget-picker activity pattern. Template for Lite's `AppWidgetHost` integration.
- **LawnchairLauncher/lawnchair/lawnchair/src/app/lawnchair/LawnchairLauncher.kt** â€” https://github.com/LawnchairLauncher/lawnchair â€” upstream Lite derives from; rebase-onto-Launcher3 cadence is in `lawnchair/versions/` directory. Check before attempting AOSP upgrades.
- **MM2-0/Kvaesitso/app/ui/src/main/java/de/mm20/launcher2/ui/launcher/search/SearchVM.kt** â€” https://github.com/MM2-0/Kvaesitso â€” provider-plugin search architecture. Each provider (`apps`, `contacts`, `files`, `wikipedia`) implements `SearchableRepository<T>` interface. Direct blueprint for Lite's "Search suggestions 2.0".
- **KieronQuinn/Smartspacer/app-legacy/src/main/java/com/kieronquinn/app/smartspacer/ui/activities/configuration/ConfigurationActivity.kt** â€” https://github.com/KieronQuinn/Smartspacer â€” SystemUI Smartspace bridge + Target plugin protocol. Required path for Lite's At-a-Glance card.
- **tanujnotes/Olauncher/app/src/main/java/app/olauncher/helper/AppCompare.kt** â€” https://github.com/tanujnotes/Olauncher â€” lightweight fuzzy-search compare. Compare against Lite's current `ranking 100/90/80/70/60/50` and steal anything simpler.
- **Neamar/KISS/app/src/main/java/fr/neamar/kiss/utils/fuzzy/FuzzyScore.java** â€” https://github.com/Neamar/KISS/blob/master/app/src/main/java/fr/neamar/kiss/utils/fuzzy/FuzzyScore.java â€” diacritic-insensitive matching and bigram scoring. Template for Lite's fuzzy-search v2.
- **NeoApplications/Neo-Launcher/src/com/saggitt/omega/iconpack/IconPackManager.kt** â€” https://github.com/NeoApplications/Neo-Launcher â€” icon-pack XML fallback chain with per-pack priority. Direct match for Lite's "Icon pack mixer" roadmap item.
- **android.googlesource.com/platform/packages/apps/Launcher3/.../model/data/WorkspaceItemInfo.java** â€” https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/model/data/WorkspaceItemInfo.java â€” upstream AOSP model for workspace items. Lite's `AppModel.kt` can mirror the field names for backup compatibility with AOSP launchers.

### Known Pitfalls from Similar Projects
- **Widget picker must handle `canBindAppWidget` failure** â€” Developer docs â€” without `BIND_APPWIDGET` (only grantable to system), must launch `ACTION_APPWIDGET_CONFIGURE` via `AppWidgetHost.startAppWidgetConfigureActivityForResult`. Lite v2.19.0 already surfaces toast on failure but verify the fallback config-activity path.
- **Glance composables do not inter-operate with Compose UI** â€” cannot reuse Lite's existing Compose components in a Glance widget. If shipping widgets (not just hosting), duplicate UI code in `GlanceModifier`. https://medium.com/androiddevelopers/demystifying-jetpack-glance-for-app-widgets-8fbc7041955c
- **Glance `setWidgetPreview` rate-limited to ~2 calls/hour** â€” Android 15+ â€” cannot re-render widget previews on every change. Cache aggressively and batch updates.
- **Android 16 `system_app_widget_background_radius` = 24dp + content padding for 28dp clipping** â€” widgets missing sufficient inner padding get content clipped. If Lite ships its own widgets, bake in 28dp padding margin. https://developer.android.com/develop/ui/views/appwidgets
- **`AppWidgetHost.startListening()` must be called in `onStart`, stopped in `onStop`** â€” leaking across activity recreation causes `SecurityException: Widget n not bound to host`. Lite's `MainActivity.kt` handles this but verify after Compose BOM bumps.
- **Launcher3 base classes rename between AOSP versions** â€” `WorkspaceItemInfo` was `ShortcutInfo` pre-Q. Lite's internal model wrapper must absorb these renames; Lawnchair upstream tracks via `lawnchair/versions/`.
- **Contacts search requires `READ_CONTACTS` runtime permission** â€” Lite already shows permission chip but must re-check after system revokes permission for unused apps (Android 11+ auto-reset feature). https://developer.android.com/reference/android/Manifest.permission
- **Themed icons monochrome drawable resource only on Android 13+** â€” older Android shows transparent icon. Lite guards on `Build.VERSION_CODES.TIRAMISU` but verify via Android 12 emulator.
- **Private Space integration requires `PROFILE_OWNER` or privileged system access** â€” Lite cannot natively integrate with Android 15 Private Space. Only hidden-apps + PIN-reveal is feasible.
- **DeviceAdmin `lockNow()` permission revoked on uninstall leaves orphaned admin records** â€” Lite's `AdminReceiver` needs a clean deactivation in `onDisableRequested`. Users who uninstall without deactivating hit a stuck "device admin" entry.

### Library Integration Checklist
- **Smartspacer (At-a-Glance)** â€” `com.kieronquinn.smartspacer:plugin-sdk:1.0.0` â€” entry: `class LawnchairSmartspaceClient : SmartspacerClient(context) { override fun onSmartspaceUpdate(targets: List<SmartspaceTarget>) { ... } }`. Gotcha: requires Smartspacer companion app installed; graceful-degrade to "install Smartspacer to enable" prompt if not present.
- **AppWidgetHost (framework)** â€” no dep; `AppWidgetManager.getInstance(context)` + `AppWidgetHost(context, APPWIDGET_HOST_ID=1024)`. Entry: `host.startListening(); val id = host.allocateAppWidgetId(); manager.bindAppWidgetIdIfAllowed(id, provider)`. Gotcha: `APPWIDGET_HOST_ID` must be stable across app versions or you orphan every bound widget ID â€” Lite uses 1024 (locked-in).
- **Accompanist DrawablePainter** â€” `com.google.accompanist:accompanist-drawablepainter:0.36.0` (pin) â€” entry: `Image(painter = rememberDrawablePainter(drawable), contentDescription = null)`. Gotcha: Accompanist is end-of-life; Google recommends replacing with `Painter.fromDrawable()` from Compose 1.7+. Plan the migration before next Compose BOM bump.

## Research-Driven Additions
```

</details>
