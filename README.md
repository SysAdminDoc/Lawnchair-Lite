<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-2.27.0-58A6FF?style=for-the-badge">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-4ade80?style=for-the-badge">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-58A6FF?style=for-the-badge">
</p>

# Lawnchair Lite v2.27.0

Minimal, fast Android launcher with professional-grade stability and smooth Compose-powered animations.

## Stability Architecture

Built on crash patterns identified across Lawnchair v14-v15 beta releases:

- **Global crash handler** with notification-based bug reporting (modeled after LawnchairApp's UncaughtExceptionHandler)
- **DataStore corruption recovery** via `ReplaceFileCorruptionHandler` - corrupted preferences reset to defaults instead of crash-looping
- **Defensive PackageManager calls** - all PM/LauncherApps interactions wrapped for `DeadSystemException`, `SecurityException`, `NameNotFoundException`
- **Package existence validation** before operations - prevents the race condition where customizing an app being uninstalled causes a crash (Lawnchair 15 Beta 2 fix)
- **Debounced package events** (300ms) - bulk install/uninstall doesn't trigger N consecutive reloads
- **Byte-bounded icon-pack bitmap cache** with recycled evictions - prevents OOM on large icon packs
- **Safe grid deserialization** - malformed workspace data returns empty cells, never crashes
- **Atomic DataStore writes** - process death during save never corrupts settings
- **Reflection-based API calls** for status bar expansion - OEM ROMs that block it fail gracefully
- **Per-app error isolation** during app list loading - one bad package entry doesn't prevent loading the rest

## Features

- Multiple home pages with swipe navigation, add/remove pages from long-press menu
- App drawer with alphabetical fast scroller (haptic feedback on letter changes)
- Material 3 drawer tabs for All, Recent, Favorites, and Work profile apps
- Drawer groups/folders that filter any drawer tab by selected apps or package-prefix rules
- Folder creation via drag-and-drop with 3x3 preview, app-icon covers, and emoji covers
- Icon pack support (ADW/Nova format) with 4-icon preview per pack and ordered multi-pack fallback mixing
- Custom icon labels, hide apps from drawer with batch unhide
- 6 theme modes (Midnight, Glass, OLED, Mocha, Aurora, Neon) with per-theme error colors
- Custom accent color with 12 presets + hex input + theme-default reset chip
- Material You dynamic color can pull the Android 12+ wallpaper palette into any theme
- Custom font import applies a persisted local `.ttf` or `.otf` file across launcher text
- Theme import/export shares the active theme, icon pack chain, custom font reference, icon shape, and accent as a `.lawnchair-theme` JSON file
- First-party Smartspace with local weather, next calendar event, next alarm, unread notifications, and permission prompts
- Drawer category rules by app-name regex, package prefix, or install source with backup/restore support
- Configurable grid (3-8 cols, 3-10 rows), dock (3-7 icons), icon sizes (S/M/L/XL)
- Optional dock labels with opacity control
- 9 gesture actions: double-tap, triple-tap, swipe-down, swipe-up, pinch, dock-tap, dock-swipe, custom draw
- Gesture app binding: assign any gesture to launch a specific app (with icon preview in settings)
- Custom draw gesture recorder: trace a saved shape on the home screen to run a bound action
- Assistant replacement: swipe up from either bottom corner to launch a selected assistant app or the system assistant
- Smart fuzzy search with relevance scoring (exact > starts > word > contains > pkg > subsequence)
- Inline calculator and unit converter in drawer search
- Time-aware app suggestions (morning/afternoon/evening/night usage patterns)
- Search history chips with fade gradient, recent apps row with clear button
- Home screen widgets via AppWidgetHost with provider previews, grid-cell sizing, bind recovery, configuration, stack paging, and removal confirmation
- Contact search with permission chip, cached web suggestions, and web search fallback
- Notification badges (count/dot/hidden), app shortcuts via LauncherApps API, shortcut/PWA icon overrides, per-app swipe-up shortcut bindings, and a swipe-open global shortcut shelf
- 5 page transitions (Slide, Cube, Stack, Fade, Depth, Carousel)
- Wallpaper dimming (0-80%) with parallax effect and per-page overrides
- Backup/restore layout as JSON with privacy toggles, restore preview, and Nova backup migration import
- Uninstall confirmation dialog (all paths: D&D, home menu, drawer menu)
- Reset all settings with confirmation dialog
- Settings search filter across 8 collapsible sections
- Device admin for screen lock gesture, flashlight toggle gesture

## Smoothness

- **Compose VelocityTracker** for accurate drawer fling velocity (no manual nanoTime)
- **Always-composed drawer** preserves scroll position, eliminates recomposition on open/close
- **Material 3 icon press** - crisp no-bounce spring animation (dampingRatio 1.0, stiffness 800)
- **Memoized theme computation** - `remember`-cached, no recompute per recomposition
- **Conditional auto-focus** - keyboard only opens when drawer opened via search bar, not swipe
- **Pager scroll guard** - horizontal paging disabled during drawer transition
- **Drawer background blur** - Android 12+ devices blur the home layer behind the drawer while older OS versions keep the existing fade/scale path
- **Baseline profile** - startup, home, drawer/search, settings, shortcut, widget, and backup classes ship pre-profiled for faster first-run compilation
- **R8 full mode** - release builds explicitly use full optimization while retaining metadata required by preference/model serialization paths
- **Bounded icon bitmap cache** - icon-pack drawables are rendered into a byte-capped LRU cache with explicit bitmap recycling
- **Icon pack mixer** - Settings can combine multiple installed icon packs in priority order, using later packs as fallbacks for missing appfilter entries
- **Custom font loading** - persisted SAF font files load off the UI thread and fall back to the system font if access is revoked
- **Drawer grid pre-warm** - the app drawer pre-measures the first offscreen rows while hidden to avoid first-scroll jank

## Permissions

- `QUERY_ALL_PACKAGES` powers the launcher app list, drawer search, categories, hidden apps, and gesture app binding.
- `INTERNET` is used for optional Open-Meteo weather lookups and user-triggered web search handoff. The app does not include tracker, ad, analytics, Firebase, or Play services SDKs.
- `VIBRATE` powers local haptic feedback for drawer scrolling, page limits, gestures, and long-press actions.
- `POST_NOTIFICATIONS` is used only for local crash-copy notifications on Android 13+; denied access does not block launcher use.
- `READ_CONTACTS`, `READ_CALENDAR`, and `ACCESS_COARSE_LOCATION` are optional search/Smartspace features with Settings recovery actions.
- `KILL_BACKGROUND_PROCESSES` and `EXPAND_STATUS_BAR` back user-triggered quick actions and degrade gracefully when Android or OEM policy blocks them.
- Widget placement uses Android's per-widget bind prompt; the protected `BIND_APPWIDGET` permission is not declared.

## Libre / F-Droid Readiness

The standard release variant is libre-compatible: it uses AndroidX/Compose framework dependencies only, has no ad/tracker/cloud-account SDKs, and ships Fastlane metadata under `fastlane/metadata/android/en-US/`.

Local audit:

```powershell
.\tools\libre-audit.ps1
```

Build and audit:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$HOME\AppData\Local\Android\Sdk"
.\tools\libre-audit.ps1 -Build
```

### v2.27.0

- **Cloud backup privacy** - Android Auto Backup excludes launcher DataStore state from silent cloud backup
- **Local transfer rule** - Android 12+ device transfer can keep the launcher DataStore state for on-device migration
- **Manual export controls** - Search history, usage/recents, and hidden apps are excluded unless explicitly enabled
- **Private restore preservation** - Omitted private sections no longer clear existing local search, usage, or hidden-app data
- **Permission audit panel** - Advanced Settings explains each broad/runtime permission, current status, recovery action, and degraded behavior
- **Restore preview** - Backup import now validates schema compatibility and shows sections, skipped fields, and private-data handling before applying
- **Local diagnostics** - Crashes are saved to private app storage with Settings copy/share/delete support even when notifications are unavailable
- **Drawer groups** - Drawer folders can be created in Settings, populated manually or by package-prefix rule, filtered across All/Recent/Favorites/Work, and backed up with launcher JSON
- **Widget picker previews** - The widget picker shows provider preview images when available, icon/placeholder fallbacks when unavailable, and confirms widget removal before deleting host IDs
- **Widget stacks** - Edit-mode widgets can add compatible widgets into the same grid slot, swipe between stack pages, and remove the active stack item without clearing the rest
- **Shortcut icon overrides** - App shortcuts and pinned web/PWA-style entries can use manual source-app icon overrides, persist through backup/restore, and fall back safely when shortcut icon resources are missing
- **Per-app swipe shortcuts** - App long-press shortcut rows can bind a shortcut to that app icon's swipe-up gesture, with backup persistence
- **Custom draw gesture** - Settings can record a freeform home-screen shape and bind it to any existing gesture action, with backup persistence
- **Global shortcuts shelf** - Long-press shortcut rows can pin arbitrary app shortcuts to a swipe-open shelf above the dock, with backup persistence and long-press removal
- **Assistant replacement** - Bottom-corner swipe-up can launch a selected assistant app or the system assistant, with backup persistence
- **Baseline profile** - Release builds ship `baseline-prof.txt` plus ProfileInstaller so startup and primary launcher journeys are precompiled after install
- **R8 full mode** - Release shrinking now pins full-mode optimization and keeps required Kotlin/Java metadata attributes for retained launcher models
- **Icon bitmap cache tuning** - Icon-pack resources now use a bounded bitmap LRU that recycles evicted cache entries and avoids permanently caching misses
- **Icon pack mixer** - Settings can combine multiple installed icon packs with an ordered fallback chain that persists through backups and theme exports
- **Custom font import** - Theme Settings can import a local font file, validate it before applying, and clear back to the system font
- **Drawer lazy grid pre-warm** - The hidden drawer now pre-measures the first offscreen rows before the first open so the initial scroll is already composed
- **Material You dynamic color** - Android 12+ devices can opt into wallpaper-derived accents for any selected theme, while custom hex accents still take precedence
- **Theme import/export** - Settings can export or import `.lawnchair-theme` JSON files containing theme mode, dynamic color, accent, icon pack chain, custom font reference, and icon appearance options
- **Search suggestions 2.0** - Drawer search fetches cached web suggestions from the selected engine when that engine exposes a JSON suggestion API
- **Nova backup migration** - Restore accepts Nova Launcher ZIP backups, converts compatible apps/folders/dock items, and previews unsupported items before import
- **Drawer background blur** - Android 12+ RenderEffect blur runs behind the app drawer with safe fallback on older Android versions
- **Per-page wallpaper dim** - Home pages can override the default wallpaper dim and interpolate between page-specific values while swiping

### v2.26.0

- **Widget bind recovery** - Widgets that need host permission now launch Android's system bind flow instead of failing with a toast
- **Widget configuration** - Providers with configuration activities run setup before the widget is placed
- **Abandoned ID cleanup** - Canceled bind/config flows delete the allocated widget ID to avoid orphaned host entries
- **Placement guard** - The launcher checks for an empty span before requesting widget permission

### v2.25.0

- **Dock labels** - Optional labels under dock apps and folders
- **Opacity control** - Dock label opacity slider in Dock settings
- **Scoped rendering** - Dock labels reuse existing label size/weight while leaving home and drawer labels unchanged
- **Backup support** - Dock label settings export/import with launcher backups

### v2.24.0

- **Folder covers** - Folder long-press menu can choose an emoji or one of the folder app icons as the cover
- **Cover rendering** - Custom covers replace the 2x2/3x3 preview while preserving notification badges and labels
- **Safe persistence** - New covered-folder serialization round-trips while old folder layouts keep the legacy format
- **Stale cleanup** - Removing an app also clears it as a folder cover when needed

### v2.23.0

- **Material 3 drawer tabs** - All, Recent, Favorites, and Work tabs switch the drawer grid without leaving search
- **Favorites tab** - Drawer context menu can add/remove favorite apps, persisted in DataStore and backups
- **Work profile tab** - LauncherApps profile scan loads managed-profile apps when Android exposes them
- **Profile-aware launch** - Work-profile apps launch through LauncherApps while personal apps keep the existing launch path

### v2.22.0

- **Drawer category rules** - User-defined rules override automatic app categorization
- **Rule matchers** - Match by app-name regex, package prefix, or install source
- **Settings editor** - Add, disable, and remove category rules from the Drawer settings section
- **Backup support** - Category rules export/import with the launcher backup JSON

### v2.21.0

- **First-party Smartspace** - Weather, next calendar event, next alarm, and unread notification aggregation now render in the home At-a-Glance area without Google Smartspace
- **Weather chip** - Uses last-known coarse location and a no-key Open-Meteo forecast request with short timeouts
- **Calendar chip** - Reads the next 7-day calendar event through Android CalendarProvider when permission is granted
- **Permission prompts** - Home Smartspace rows request location/calendar access directly and refresh after grants

### v2.19.0

- **VelocityTracker drawer fling** - Replaced manual nanoTime velocity with Compose VelocityTracker for consistent, accurate flings
- **Always-composed drawer** - Drawer stays in composition tree, hidden via graphicsLayer alpha; preserves scroll position
- **Remove Page** - "Remove Page" in home long-press menu (checks empty, shifts widgets, greyed out on last page)
- **Clear Recents** - "Clear" button on the RECENT row header in the app drawer
- **Reset confirmation** - "Reset All Settings" guarded by confirmation dialog
- **Fast scroller haptics** - Light vibration on each letter change while dragging
- **3x3 folder preview** - Folders with 5+ apps show 9 icons in a 3x3 grid
- **Error color slot** - `LauncherColors.error` with per-theme tuning (rose for Mocha, coral for Neon/Aurora)
- **Accent reset chip** - "Default" chip at start of accent palette to revert to theme native accent
- **Theme preview 4-swatch** - Adds background color dot for better theme differentiation
- **Gesture picker app icon** - Shows small app icon inline when gesture is LAUNCH_APP
- **Search history fade gradient** - Right-edge gradient hint for scrollable chip row
- **Conditional auto-focus** - Keyboard only auto-focuses when opened via search bar tap
- **Pager scroll guard** - HorizontalPager disabled while drawer is partially open
- **Hidden indicator zero height** - HIDDEN page indicator no longer wastes 6dp
- **Adaptive icon widths** - Drawer icon items scale with icon size setting
- **App size split APKs** - Sums sourceDir + splitSourceDirs for accurate size
- **Widget picker cell sizes** - Shows "2x1 cells" instead of raw dp dimensions
- **Widget span actual cell width** - Uses real grid cell width instead of hardcoded 73dp
- **Import failure toast** - Error message instead of silent failure
- **No-bounce icon press** - Material 3 spring animation (dampingRatio 1.0, stiffness 800)
- **Memoized theme** - `themeColorsWithAccent` cached via `remember`
- **Theme.kt reformatted** - Named-parameter multi-line constructors for all 6 themes
- **SearchOff empty state** - Icon above "No apps found" in drawer

### v2.18.0

- **Settings search filter** - Keyword search across all 8 sections, auto-expands matching sections
- **Uninstall confirmation dialog** - All uninstall paths (D&D, home menu, drawer menu) go through confirmation
- **Swipe-up home gesture** - Configurable, separate from drawer open (default: APP_DRAWER)
- **Haptic feedback on page limits** - Vibration when swiping past first/last page
- **Hidden apps "Unhide All"** - Batch unhide button when >1 hidden app
- **Settings sections auto-expand** during search

### v2.17.0

- **LAUNCH_APP gesture action** - Bind any gesture to open a specific app
- **"Add Page" in home space menu** - Pixel Launcher-style long-press menu
- **Settings section summaries** - Show current values when collapsed
- **Drawer shows "X results"** during search
- **Gesture app picker UI** in settings

### v2.16.1

- Audit fixes: SearchPill shows engine initial+name, haptic on home space menu, dock swipe indicator dot, categorizedApps perf skip during search, contact search permission chip, icon pack preview async loading

### v2.16.0

- Collapsible settings sections (8 groups), search engine picker (Google/DDG/Bing/Brave/Startpage), home space long-press menu, dock swipe-up gesture, icon pack preview (4 sample icons)

### v2.15.7

- suggestFolderName tokenized, unit converter regex hoisted, flashlight TorchCallback sync, drawer columns range 0-6

### v2.15.6

- Atomic resetAllSettings, widget picker toast feedback, all drawer close paths reset category

### v2.15.5

- Zero compiler warnings, web search at bottom of results, drawer search auto-focus

### v2.15.4

- AppCategorizer word-boundary tokenization, ProGuard tightened (APK 18.6->3.3MB), widget data in backup

### v2.15.3

- RECENT_APP skips current app, resetAllSettings covers all prefs, backup includes suggestion/app usage

### v2.15.2

- Folder columns setting, category reset, RECENT_APP hidden filter, drawer icon styling parity

### v2.15.1

- Widget BoxWithConstraints, clock pending-job pattern, fast scroller from displayApps

### v2.15.0

- Hide dock, Recent App gesture, grayscale icons, page line indicator, label weight

### v2.14.0

- Z-A sort, Depth transition, dock pulse, scroll-to-top, XL icons

### v2.13.0

- Accent presets, search clear, Carousel transition, app size, reset all

### v2.12.0

- Neon theme, copy results, folder badges, drawer scale, clock cycling

### v2.11.0

- Clock styles, unit converter, Edit Mode gesture, launch counts

### v2.10.0

- Fuzzy search, inline calculator, hexagon/diamond shapes, fast scroller

### v2.9.0

- Suggestions row, search history, themed badges

### v2.8.0

- Widgets (AppWidgetHost), contact search, staggered animation

### v2.7.0

- Flashlight/triple-tap/pinch gestures, dock handle tap, parallax

### v2.6.0

- Section headers, dock/search/haptic styles, drawer opacity, folder columns

### v2.5.0

- Categories, home lock, drawer columns, accent color, icon shadow

### v2.4.0

- Page transitions, dock swipe, badge styles, grid padding, clock tap

### v2.3.0

- Themed icons, drawer sort, label styles, At-a-Glance, web search

### v2.2.0

- Notification badges, shortcuts, wallpaper dim, recent apps

### v2.1.0

- Icon packs, folders, custom labels, hidden apps, gestures, backup

### v2.0.0

- Stability architecture, crash handler, DataStore corruption recovery

## Architecture

```
LauncherApplication    - Global crash handler + notification reporter
MainActivity           - Lifecycle, debounced package receiver, widget host
LauncherViewModel      - State management, debounced operations, package validation
LauncherPrefs          - DataStore with corruption handler, atomic writes
AppRepository          - Hardened PM calls, package existence checks, themed icons
IconPackManager        - Byte-bounded bitmap LRU, multi-pack fallback chain, defensive XML parsing, preview icons
ShortcutRepository     - LauncherApps shortcut queries + launching
NotificationListener   - NotificationListenerService for badge counts
WebSuggestionService   - Engine suggestion APIs with bounded in-memory caching
AppCategorizer         - Word-boundary tokenized categorization with user rules
AppModel               - Safe deserialization, data types, enums
UI (Compose)           - HomeScreen, AppDrawer, Components, Settings, Theme
```

## Build

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ./gradlew assembleRelease
```

Debug build: `./gradlew assembleDebug`

Requires Android SDK 28+ (Android 9), compiles against SDK 37, and targets SDK 36.

Local launcher smoke test after a debug or release build:

```powershell
tools/android-smoke.ps1 -Serial emulator-5554
```

The smoke harness installs the selected APK, launches Lawnchair Lite, checks drawer search, Settings, and the widget picker, and reports `PASS`, `FAIL`, or `DEGRADED` platform checks. On emulator serials it sets Lawnchair Lite as the HOME activity so launcher flows foreground deterministically.
