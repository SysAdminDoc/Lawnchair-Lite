# Lawnchair Lite Roadmap

Minimal, fast Android launcher built on Jetpack Compose with professional-grade stability (v2.25.0). Stability architecture already landed; roadmap pushes performance, visual polish, and feature-parity with premium launchers.

## Planned Features

### Home & Drawer
- **Blur behind drawer** — RenderEffect blur on Android 12+ (feature-detect, no crash on older OS)
- **Per-page wallpaper dim** — different dim per home page

### Gestures & Actions
- **Per-app gesture assignment** — long-press an app icon to bind custom gesture set (quick actions, shortcuts)
- **Gesture recorder** — trace a shape on the home screen as a custom gesture, bind to action
- **Global shortcuts shelf** — dock-style row of arbitrary app shortcuts (not apps) accessible via swipe
- **Assistant replacement** — swipe-up-from-corner launches chosen assistant (ChatGPT, Gemini, Perplexity)

### Performance
- **Baseline profile** — ship `baseline-prof.txt` to cut first-launch startup
- **R8 full-mode** — enable full optimization, verify no reflection breaks
- **Icon bitmap cache tuning** — bounded-memory LRU with explicit bitmap recycling
- **Drawer lazy-column pre-warm** — pre-measure on first open to eliminate first-scroll jank

### Themeing
- **Material You dynamic color** — opt-in per theme, pulls monet palette from wallpaper
- **Theme import/export** — share full theme + icon-pack + accent as a `.lawnchair-theme` JSON
- **Icon pack mixer** — pick icons from multiple packs at once with fallback chain
- **Custom font** — user-selectable font via font file import

### Widgets & Info
- **Widget stack** — stack multiple widgets in one slot, swipe between (iOS smart stack parity)
- **At-a-glance card** — weather + next calendar + next alarm + unread-count aggregation
- **Search suggestions 2.0** — web suggestions from chosen engine via suggestion API with caching

### Settings & Backup
- **Selective backup** — export/import specific sections (gestures only, theme only, layout only)
- **Cloud backup** — optional gdrive/onedrive/webdav backup of the JSON layout
- **Backup schedule** — auto-backup weekly to `/storage/emulated/0/Android/data/.../backups/`

### Stability
- **Crash analytics (local)** — persist uncaught exceptions with stack + device state to a local log viewer in Settings
- **OEM skin compatibility matrix** — automated install test on Pixel, OneUI, MIUI, ColorOS, OxygenOS, HyperOS via cloud device farm in CI
- **Work profile parity** — dual-profile app drawer with badge, respect org policies

## Competitive Research
- **Lawnchair (upstream)** — Lite is derived from; pull performance/stability fixes from main branch, keep feature set intentionally smaller.
- **Nova Launcher** — gold-standard paid launcher; reference for gesture flexibility and backup granularity.
- **Smart Launcher 6** — excellent auto-categorization; borrow UX not implementation.
- **Niagara Launcher** — list-first minimalist; proves there's appetite for minimal. Lawnchair Lite should keep that ethos without becoming Niagara-shaped.
- **Kvaesitso** — open-source, search-first; reference for fuzzy-search ranking.

## Nice-to-Haves
- Wear OS companion for quick-launch from wrist
- Tablet/foldable layout (large-screen dual-pane drawer, taskbar)
- Android Auto companion launcher (distinct APK, shared theme engine)
- Experimental: LLM-powered search that understands "my gym app" → returns Strong
- Theming API so icon pack authors can define accent + wallpaper suggestions per pack
- Private space integration (Android 15 private space) with biometric unlock

## Open-Source Research (Round 2)

### Related OSS Projects
- LawnchairLauncher/lawnchair — https://github.com/LawnchairLauncher/lawnchair — upstream; Lawnchair 16 on Launcher3 from Android 16; this project's direct parent
- MM2-0/Kvaesitso — https://github.com/MM2-0/Kvaesitso — search-first launcher, universal search across apps/files/cloud/contacts/wikipedia; v1.40.1-fdroid (Apr 2026)
- Posidon — https://github.com/Posidon-Software/posidon_launcher — one-page launcher with RSS feed, One UI-inspired
- Olauncher — https://github.com/tanujnotes/Olauncher — minimal text-only launcher (no icons)
- NeoApplications/Neo-Launcher — https://github.com/NeoApplications/Neo-Launcher — highly customizable FOSS launcher, another Launcher3 derivative
- KISS Launcher — https://github.com/Neamar/KISS — <2MB, Android 5+, zero-background
- Rootless Pixel Launcher (amirzaidi/Launcher3) — closest-to-AOSP reference; minimal patch over Launcher3
- T-UI Launcher — https://github.com/ossrs/t-ui-launcher — terminal-shell style, type-to-launch
- Smartspacer — https://github.com/KieronQuinn/Smartspacer — system-level At-a-Glance replacement; Lawnchair integrates with it
- QuickSwitch — Lawnchair's recents bridge; reference for Android 10-15 integration (root)

### Features to Borrow
- **Universal search bar from home screen** (Kvaesitso) — apps + contacts + files + calendar + web results in one query field; top-tier QoL addition
- **Scrollable widget canvas as optional home page** (Kvaesitso) — decouples widgets from grid cells
- **Local calculator + unit converter in search** (Kvaesitso) — no network, instant answers
- **At-a-Glance via Smartspacer integration** (Lawnchair upstream) — weather / calendar / media card at top of home; "Lite" can still include it
- **Per-app icon override** (Neo Launcher, Lawnchair) — with icon-pack support; already a Lawnchair feature, make sure Lite preserves
- **Material You wallpaper-color theming** (Lawnchair upstream) — inherit free from base
- **App drawer search with fuzzy match** (KISS) — typo-tolerant; KISS's CompareString algorithm is a good reference
- **Gesture: swipe-up-on-home to open drawer** (Lawnchair, Posidon) — ensure preserved; one-finger reachability
- **Hide app from drawer / private space** (Lawnchair, Neo) — hidden-apps list with PIN reveal

### Patterns & Architectures Worth Studying
- Lawnchair's **Launcher3 fork-rebase cadence** — they rebase onto each new Android's Launcher3 rather than diverging; makes AOSP upgrades tractable. "Lite" must preserve this discipline
- Kvaesitso's **search provider plugin model** — each search source (apps/files/wiki/cloud) is an independent provider implementing a common interface; users toggle providers individually
- Smartspacer's **virtual widget host** — shows how to inject a "pluggable" widget slot without being tied to one content source
- Posidon's **single-page canvas** vs. multi-page pager — architectural choice; "Lite" should document which model it commits to
- **Strip-for-size tactics** — to earn the "Lite" name, benchmark baseline Lawnchair APK (~30MB) and prune: drop Hermes/flutter deps if present, disable unused icon packs at build, aggressively R8-shrink; KISS is the north star at <2MB

## Implementation Deep Dive (Round 3)

### Reference Implementations to Study
- **amirzaidi/Launcher3/src/com/android/launcher3/widget/WidgetHostViewLoader.java** — https://github.com/amirzaidi/Launcher3 — canonical `AppWidgetHostView` bind + `bindAppWidgetIdIfAllowed` + fallback to widget-picker activity pattern. Template for Lite's `AppWidgetHost` integration.
- **LawnchairLauncher/lawnchair/lawnchair/src/app/lawnchair/LawnchairLauncher.kt** — https://github.com/LawnchairLauncher/lawnchair — upstream Lite derives from; rebase-onto-Launcher3 cadence is in `lawnchair/versions/` directory. Check before attempting AOSP upgrades.
- **MM2-0/Kvaesitso/app/ui/src/main/java/de/mm20/launcher2/ui/launcher/search/SearchVM.kt** — https://github.com/MM2-0/Kvaesitso — provider-plugin search architecture. Each provider (`apps`, `contacts`, `files`, `wikipedia`) implements `SearchableRepository<T>` interface. Direct blueprint for Lite's "Search suggestions 2.0".
- **KieronQuinn/Smartspacer/app-legacy/src/main/java/com/kieronquinn/app/smartspacer/ui/activities/configuration/ConfigurationActivity.kt** — https://github.com/KieronQuinn/Smartspacer — SystemUI Smartspace bridge + Target plugin protocol. Required path for Lite's At-a-Glance card.
- **tanujnotes/Olauncher/app/src/main/java/app/olauncher/helper/AppCompare.kt** — https://github.com/tanujnotes/Olauncher — lightweight fuzzy-search compare. Compare against Lite's current `ranking 100/90/80/70/60/50` and steal anything simpler.
- **Neamar/KISS/app/src/main/java/fr/neamar/kiss/utils/fuzzy/FuzzyScore.java** — https://github.com/Neamar/KISS/blob/master/app/src/main/java/fr/neamar/kiss/utils/fuzzy/FuzzyScore.java — diacritic-insensitive matching and bigram scoring. Template for Lite's fuzzy-search v2.
- **NeoApplications/Neo-Launcher/src/com/saggitt/omega/iconpack/IconPackManager.kt** — https://github.com/NeoApplications/Neo-Launcher — icon-pack XML fallback chain with per-pack priority. Direct match for Lite's "Icon pack mixer" roadmap item.
- **android.googlesource.com/platform/packages/apps/Launcher3/.../model/data/WorkspaceItemInfo.java** — https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/model/data/WorkspaceItemInfo.java — upstream AOSP model for workspace items. Lite's `AppModel.kt` can mirror the field names for backup compatibility with AOSP launchers.

### Known Pitfalls from Similar Projects
- **Widget picker must handle `canBindAppWidget` failure** — Developer docs — without `BIND_APPWIDGET` (only grantable to system), must launch `ACTION_APPWIDGET_CONFIGURE` via `AppWidgetHost.startAppWidgetConfigureActivityForResult`. Lite v2.19.0 already surfaces toast on failure but verify the fallback config-activity path.
- **Glance composables do not inter-operate with Compose UI** — cannot reuse Lite's existing Compose components in a Glance widget. If shipping widgets (not just hosting), duplicate UI code in `GlanceModifier`. https://medium.com/androiddevelopers/demystifying-jetpack-glance-for-app-widgets-8fbc7041955c
- **Glance `setWidgetPreview` rate-limited to ~2 calls/hour** — Android 15+ — cannot re-render widget previews on every change. Cache aggressively and batch updates.
- **Android 16 `system_app_widget_background_radius` = 24dp + content padding for 28dp clipping** — widgets missing sufficient inner padding get content clipped. If Lite ships its own widgets, bake in 28dp padding margin. https://developer.android.com/develop/ui/views/appwidgets
- **`AppWidgetHost.startListening()` must be called in `onStart`, stopped in `onStop`** — leaking across activity recreation causes `SecurityException: Widget n not bound to host`. Lite's `MainActivity.kt` handles this but verify after Compose BOM bumps.
- **Launcher3 base classes rename between AOSP versions** — `WorkspaceItemInfo` was `ShortcutInfo` pre-Q. Lite's internal model wrapper must absorb these renames; Lawnchair upstream tracks via `lawnchair/versions/`.
- **Contacts search requires `READ_CONTACTS` runtime permission** — Lite already shows permission chip but must re-check after system revokes permission for unused apps (Android 11+ auto-reset feature). https://developer.android.com/reference/android/Manifest.permission
- **Themed icons monochrome drawable resource only on Android 13+** — older Android shows transparent icon. Lite guards on `Build.VERSION_CODES.TIRAMISU` but verify via Android 12 emulator.
- **Private Space integration requires `PROFILE_OWNER` or privileged system access** — Lite cannot natively integrate with Android 15 Private Space. Only hidden-apps + PIN-reveal is feasible.
- **DeviceAdmin `lockNow()` permission revoked on uninstall leaves orphaned admin records** — Lite's `AdminReceiver` needs a clean deactivation in `onDisableRequested`. Users who uninstall without deactivating hit a stuck "device admin" entry.

### Library Integration Checklist
- **Smartspacer (At-a-Glance)** — `com.kieronquinn.smartspacer:plugin-sdk:1.0.0` — entry: `class LawnchairSmartspaceClient : SmartspacerClient(context) { override fun onSmartspaceUpdate(targets: List<SmartspaceTarget>) { ... } }`. Gotcha: requires Smartspacer companion app installed; graceful-degrade to "install Smartspacer to enable" prompt if not present.
- **AppWidgetHost (framework)** — no dep; `AppWidgetManager.getInstance(context)` + `AppWidgetHost(context, APPWIDGET_HOST_ID=1024)`. Entry: `host.startListening(); val id = host.allocateAppWidgetId(); manager.bindAppWidgetIdIfAllowed(id, provider)`. Gotcha: `APPWIDGET_HOST_ID` must be stable across app versions or you orphan every bound widget ID — Lite uses 1024 (locked-in).
- **Accompanist DrawablePainter** — `com.google.accompanist:accompanist-drawablepainter:0.36.0` (pin) — entry: `Image(painter = rememberDrawablePainter(drawable), contentDescription = null)`. Gotcha: Accompanist is end-of-life; Google recommends replacing with `Painter.fromDrawable()` from Compose 1.7+. Plan the migration before next Compose BOM bump.

## Research-Driven Additions

- [ ] P0 - Complete widget bind and configuration recovery
  Why: Widgets currently fail with a toast when host binding is not already allowed, which blocks many third-party widgets.
  Evidence: `app/src/main/java/app/lawnchairlite/ui/HomeScreen.kt`, Android AppWidgetHost docs
  Touches: `HomeScreen.kt`, `LauncherViewModel.kt`, `MainActivity.kt`, `Components.kt`, widget tests
  Acceptance: Selecting a widget that needs permission launches the system bind flow, runs provider configuration when required, deletes abandoned widget IDs on cancel, and restores the picker state without losing the current page.
  Complexity: M

- [ ] P0 - Add backup privacy rules and backup content controls
  Why: Android Auto Backup is enabled while launcher preferences include search history, app usage, hidden apps, widgets, and layout data.
  Evidence: `app/src/main/AndroidManifest.xml`, `app/src/main/java/app/lawnchairlite/data/LauncherPrefs.kt`, Android Auto Backup and backup-risk docs
  Touches: `AndroidManifest.xml`, `res/xml/`, `LauncherPrefs.kt`, `SettingsScreen.kt`, backup tests
  Acceptance: Manifest data extraction rules explicitly include/exclude launcher state, manual export offers history/usage toggles, and restore/import behavior is documented in Settings.
  Complexity: M

- [ ] P0 - Audit platform permissions and graceful degradation
  Why: Play/F-Droid readiness depends on explaining or removing broad permissions such as `QUERY_ALL_PACKAGES`, `BIND_APPWIDGET`, `POST_NOTIFICATIONS`, and `KILL_BACKGROUND_PROCESSES`.
  Evidence: `app/src/main/AndroidManifest.xml`, Google Play package-visibility policy, Android exported/permission docs
  Touches: `AndroidManifest.xml`, `LauncherViewModel.kt`, `SettingsScreen.kt`, README permission section
  Acceptance: Every dangerous/broad permission has a visible feature owner, runtime status, denial recovery, and documented distribution rationale; unused or system-only permissions are removed.
  Complexity: M

- [ ] P1 - Add Compose accessibility semantics and a11y tests for custom controls
  Why: Many chips, rows, drag/drop targets, icon buttons, and image-only controls are custom `clickable` elements without roles, state descriptions, or test coverage.
  Evidence: `SettingsScreen.kt`, `AppDrawer.kt`, `Components.kt`, Compose semantics and accessibility-test docs
  Touches: `SettingsScreen.kt`, `AppDrawer.kt`, `Components.kt`, `HomeScreen.kt`, Compose UI tests
  Acceptance: Switch-like rows expose switch semantics, chips expose selection state, icon/folder/widget actions have labels, TalkBack traversal is predictable, and automated accessibility checks cover Settings plus drawer search.
  Complexity: L

- [ ] P1 - Localize user-facing strings and prepare translation workflow
  Why: Settings, drawer, Smartspace, toasts, dialogs, and backup messages are hardcoded, blocking i18n and accessibility polish.
  Evidence: `SettingsScreen.kt`, `AppDrawer.kt`, `Components.kt`, `LauncherViewModel.kt`, Kvaesitso/F-Droid translation practices
  Touches: `res/values/strings.xml`, UI files, ViewModel toast callers, tests
  Acceptance: User-visible text is resource-backed, formatted strings use locale-safe placeholders, and at least one non-English resource file builds without string-format crashes.
  Complexity: L

- [ ] P1 - Version backup schemas with dry-run restore preview
  Why: Import is defensive but silently skips fields and has no schema compatibility report for old, partial, future, or cross-launcher files.
  Evidence: `LauncherPrefs.kt`, Nova/Smart Launcher backup migration expectations
  Touches: `LauncherPrefs.kt`, `AppModel.kt`, `SettingsScreen.kt`, backup tests
  Acceptance: Import validates schema version, previews sections to be restored, reports skipped/unknown fields, preserves existing state until user confirms, and tests cover legacy and future backups.
  Complexity: M

- [ ] P1 - Add local diagnostics export beyond crash notifications
  Why: Crash reporting depends on notifications and clipboard copy, leaving no Settings-visible history or support bundle when notifications are denied.
  Evidence: `LauncherApplication.kt`, `CrashCopyReceiver.kt`, Kvaesitso debug export pattern
  Touches: `LauncherApplication.kt`, `SettingsScreen.kt`, local files directory, diagnostics tests
  Acceptance: Settings shows recent crash entries, package/build/device/app-state summary, copy/share/delete actions, and a no-notification fallback after uncaught crashes.
  Complexity: M

- [ ] P2 - Modernize Android and Compose dependency lane
  Why: The app is on AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.01.00, DataStore 1.0.0, Activity 1.8.2, and Accompanist DrawablePainter 0.34.0.
  Evidence: `app/build.gradle.kts`, AGP release notes, Compose updates, existing Accompanist note in this roadmap
  Touches: Gradle files, `Theme.kt`, icon rendering call sites, tests, release build
  Acceptance: Dependencies are bumped in a controlled branchless commit, deprecated APIs are removed where practical, release/debug builds pass, and icon rendering no longer depends on stale Accompanist APIs if a stable Compose replacement is available.
  Complexity: L

- [ ] P2 - Improve fuzzy search for diacritics, transliteration, and aliases
  Why: Current ranking is fast but basic; KISS/Kvaesitso-style search handles user typos, accents, initials, and learned ranking better.
  Evidence: `LauncherViewModel.kt`, `AppCategorizer.kt`, KISS fuzzy search, Kvaesitso search providers
  Touches: `LauncherViewModel.kt`, new search scorer class, `AppCategorizer.kt`, tests
  Acceptance: Search normalizes diacritics, supports initials and common app aliases, preserves current exact/start/word ranking priority, and adds deterministic tests for accented and typo-heavy app names.
  Complexity: M

- [ ] P2 - Add drawer folder/group parity without changing the home grid model
  Why: Nova and Smart Launcher users expect drawer folders or groups, while Lite currently has home/dock folders plus drawer tabs/categories/favorites.
  Evidence: `AppDrawer.kt`, `LauncherPrefs.kt`, Nova folders/groups, Smart Launcher automatic categories
  Touches: `AppDrawer.kt`, `LauncherPrefs.kt`, `AppModel.kt`, `SettingsScreen.kt`, backup tests
  Acceptance: Users can create named drawer groups/folders from existing apps, assign apps manually or by rule, persist them through backup, and still use All/Recent/Favorites/Work tabs.
  Complexity: L

- [ ] P3 - Add migration import for popular launcher backups
  Why: Switchers from Nova, Smart Launcher, and upstream Lawnchair expect layout transfer before they commit to a new launcher.
  Evidence: Nova FAQ/backups, Smart Launcher docs, Lawnchair upstream backups, `LauncherPrefs.kt`
  Touches: new import adapters, `LauncherPrefs.kt`, `SettingsScreen.kt`, import tests
  Acceptance: Import accepts at least one documented third-party backup format, maps compatible apps/folders/dock items, reports unsupported widgets/settings, and never overwrites the current layout without preview confirmation.
  Complexity: XL

- [ ] P3 - Prepare F-Droid/libre distribution metadata and flavor audit
  Why: FOSS launcher users discover alternatives through F-Droid, and this repo needs a clear no-tracker, no-cloud, reproducible local-build story.
  Evidence: F-Droid launcher category, Kvaesitso F-Droid listing, manifest permissions
  Touches: Gradle flavors if needed, README, metadata files, release process notes
  Acceptance: A libre-compatible build path is documented, metadata avoids proprietary service assumptions, requested permissions are justified, and the APK can be built locally with the same signing/release checklist.
  Complexity: M
