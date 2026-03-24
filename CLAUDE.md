# Lawnchair Lite - Working Notes

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- Android SDK 28+ (min), SDK 34 (target)
- AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.01.00
- DataStore for preferences, Accompanist DrawablePainter for icons

## Build
```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ./gradlew assembleRelease
```
Sign: `jarsigner` with `lawnchair-lite.jks` (alias: lawnchair-lite), then `zipalign -v 4`.

## Key Files (5,375 lines total, 17 files)
- `LauncherViewModel.kt` (1,223) - Central state, gestures, search, calculator, unit converter, drag-drop, widgets
- `HomeScreen.kt` (766) - Home surface, Launcher3 drawer transition, pager, widget overlays, overlays
- `Components.kt` (900) - Icons, badges, menus, folders, clock, search, fast scroller, widget picker
- `AppDrawer.kt` (413) - Grid with nested scroll dismiss, recent apps, categories, web search
- `SettingsScreen.kt` (487) - All settings UI, icon pack picker, backup/restore, hidden apps
- `data/LauncherPrefs.kt` (539) - DataStore, settings flow, serialization, backup export/import, atomic reset
- `data/IconPackManager.kt` (200) - Icon pack XML parsing, LRU cache, Mutex thread safety
- `data/AppModel.kt` (169) - Data types, GridCell sealed class, serialization, escaping
- `data/AppRepository.kt` (139) - PackageManager wrapper, defensive loading
- `data/AppCategorizer.kt` (74) - Word-boundary tokenized auto-categorization
- `data/ShortcutRepository.kt` (78) - LauncherApps shortcut queries
- `LauncherApplication.kt` (111) - Crash handler with notification reporting
- `MainActivity.kt` (125) - Single activity, package receiver, widget host lifecycle
- `NotificationListener.kt` (76) - Badge counts via debounced companion StateFlow
- `Theme.kt` (39) - 6 color schemes (Midnight, Glass, OLED, Mocha, Aurora, Neon)
- `AdminReceiver.kt` (11), `CrashCopyReceiver.kt` (25) - Device admin, crash clipboard

## Architecture
- Single Activity, Compose-only UI
- Drawer transition: Animatable float 0-1, Launcher3 settle thresholds (0.4/0.6)
- NestedScrollConnection intercepts grid scroll for drawer dismiss
- DataStore with ReplaceFileCorruptionHandler + atomic resetToDefaults()
- Defensive deserialization (never crashes on bad data)
- Debounced package events (300ms)
- NotificationListener communicates via companion StateFlow (debounced 150ms)
- filteredApps uses combine() with 5 flows (Array<Any?> vararg overload)
- CameraManager.TorchCallback syncs flashlight state with external toggles

## Version History
- v2.15.7: suggestFolderName tokenized, unit converter regex hoisted, flashlight TorchCallback sync, drawer columns range 0-6
- v2.15.6: atomic resetAllSettings (30+ writes -> 1), widget picker toast feedback, all drawer close paths reset category, hasOpenOverlay covers widgetPicker+editMode
- v2.15.5: zero compiler warnings, web search at bottom of results, drawer search auto-focus
- v2.15.4: AppCategorizer word-boundary tokenization, ProGuard tightened (APK 18.6->3.3MB), widget data in backup, FolderOverlay icon styling, category reset on all close paths
- v2.15.3: RECENT_APP skips current app, resetAllSettings covers all prefs, backup includes suggestion/app usage, compact clock battery alert
- v2.15.2: folder columns setting, category reset, RECENT_APP hidden filter, drawer icon styling parity, GrayscaleColorFilter constant, ImeAction.Search, BatteryAlert, DrawerContextMenu parity
- v2.15.1: widget BoxWithConstraints, clock pending-job pattern, fast scroller from displayApps
- v2.15.0: hide dock, Recent App gesture, grayscale icons, page line indicator, label weight
- v2.14.0: Z-A sort, Depth transition, dock pulse, scroll-to-top, XL icons
- v2.13.0: accent presets, search clear, Carousel transition, app size, reset all
- v2.12.0: Neon theme, copy results, folder badges, drawer scale, clock cycling
- v2.11.0: clock styles, unit converter, Edit Mode gesture, launch counts
- v2.10.0: fuzzy search, inline calculator, hexagon/diamond shapes, fast scroller
- v2.9.0: suggestions row, search history, themed badges
- v2.8.0: widgets (AppWidgetHost), contact search, staggered animation
- v2.7.0: flashlight/triple-tap/pinch gestures, dock handle tap, parallax
- v2.6.0: section headers, dock/search/haptic styles, drawer opacity, folder columns
- v2.5.0: categories, home lock, drawer columns, accent color, icon shadow
- v2.4.0: page transitions, dock swipe, badge styles, grid padding, clock tap
- v2.3.0: themed icons, drawer sort, label styles, At-a-Glance, web search
- v2.2.0: notification badges, shortcuts, wallpaper dim, recent apps
- v2.1.0: icon packs, folders, custom labels, hidden apps, gestures, backup
- v2.0.0: stability architecture, crash handler, DataStore corruption recovery

## Critical Gotchas
### Gesture System
- drawerVisible must NOT be a pointerInput key - cancels drag mid-flight
- GridCellView: do NOT use `cell` as pointerInput key - restarts gesture on recomposition
- NEVER set LazyVerticalGrid userScrollEnabled=false - kills NestedScrollConnection
- Clock double-tap: pending-job pattern (scope.launch + delay(360ms)), cancel on second tap
- Triple-tap: 400ms window after last double-tap
- Pinch threshold: zoom < 0.7 (pinch-in only)
- Lambda param `app` inside onAppClick shadows package name - use `clickedApp`

### Drawer & Category State
- selectedCategory MUST reset to DrawerCategory.ALL on every drawer close path:
  - settleDrawer(), external close (vmDrawerOpen), closeDrawer(), closeAllOverlays()
  - AND: app click, contact tap/call, shortcut click, pin home/dock lambdas in HomeScreen
- hasOpenOverlay() must include _widgetPickerOpen and _editMode
- closeAllOverlays() must close _widgetPickerOpen

### Icons & Styling
- GrayscaleColorFilter: top-level private val - do NOT recreate per recomposition
- FolderOverlay: must pass iconShadow/grayscale/labelWeight to AppIconContent and TappableAppIcon
- TappableAppIcon: has iconShadow/grayscale/labelWeight params; all call sites must pass them
- HexagonShape/DiamondShape: top-level GenericShape constants

### Data & Backup
- Backup must include: suggestion_usage, app_usage, widgets_v1
- resetAllSettings: use prefs.resetToDefaults() single atomic transaction, NOT individual set() calls
- Search history terms escaped for pipe delimiter (escapeSearchTerm/unescapeSearchTerm)
- Launch tracking: batched DataStore write (saveLaunchTracking) - 3 writes -> 1
- Suggestion usage: "bucket:appKey=count" format, 4 time buckets, capped at 200 entries

### Matching & Categorization
- AppCategorizer: tokenize() with word-boundary splitting (split on '.', ' ', '_', '-') - NEVER substring `in`
- suggestFolderName: same tokenization pattern as AppCategorizer
- Fuzzy search: 100=exact, 90=starts, 80=word, 70=contains, 60=pkg, 50=subsequence

### Widgets
- AppWidgetHost ID is 1024 - fixed constant
- Widget grid cells invisible (alpha=0) - actual widget rendered by overlay BoxWithConstraints
- Widget overlay: BoxWithConstraints for actual container-based offset, NOT magic 1000dp
- Widget picker: must show toast on canBindWidget=false and findFirstEmptySpan=null

### Performance & Build
- ProGuard: do NOT blanket-keep Compose classes - AGP handles it; only keep manifest-referenced + data layer
- Unit converter regex: class-level val, NOT inline Regex() (recompiles on every keystroke)
- Flashlight: TorchCallback in init, unregister in onCleared - keeps state accurate
- Drawer columns: range 0-6 everywhere (setter, prefs, settings UI, backup import)

### Platform
- Themed icons: API 33+ only (Build.VERSION_CODES.TIRAMISU)
- VibratorManager: API 31+ with VIBRATOR_SERVICE fallback
- Wallpaper dim max 80%
- BatteryManager.BATTERY_PROPERTY_CAPACITY can return -1
- AlarmManager.nextAlarmClock can return null
- Fast scroller: letters/letterIndex from displayApps (not apps), headerAdjustedLetterIndex for section headers
