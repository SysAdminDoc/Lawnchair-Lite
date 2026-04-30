# Lawnchair Lite v2.20.0

Minimal, fast Android launcher with professional-grade stability and smooth Compose-powered animations.

## Stability Architecture

Built on crash patterns identified across Lawnchair v14-v15 beta releases:

- **Global crash handler** with notification-based bug reporting (modeled after LawnchairApp's UncaughtExceptionHandler)
- **DataStore corruption recovery** via `ReplaceFileCorruptionHandler` - corrupted preferences reset to defaults instead of crash-looping
- **Defensive PackageManager calls** - all PM/LauncherApps interactions wrapped for `DeadSystemException`, `SecurityException`, `NameNotFoundException`
- **Package existence validation** before operations - prevents the race condition where customizing an app being uninstalled causes a crash (Lawnchair 15 Beta 2 fix)
- **Debounced package events** (300ms) - bulk install/uninstall doesn't trigger N consecutive reloads
- **LruCache for icon packs** (500 entry limit) - prevents OOM on large icon packs
- **Safe grid deserialization** - malformed workspace data returns empty cells, never crashes
- **Atomic DataStore writes** - process death during save never corrupts settings
- **Reflection-based API calls** for status bar expansion - OEM ROMs that block it fail gracefully
- **Per-app error isolation** during app list loading - one bad package entry doesn't prevent loading the rest

## Features

- Multiple home pages with swipe navigation, add/remove pages from long-press menu
- App drawer with alphabetical fast scroller (haptic feedback on letter changes)
- Folder creation via drag-and-drop with 3x3 preview for large folders
- Icon pack support (ADW/Nova format) with 4-icon preview per pack
- Custom icon labels, hide apps from drawer with batch unhide
- 6 theme modes (Midnight, Glass, OLED, Mocha, Aurora, Neon) with per-theme error colors
- Custom accent color with 12 presets + hex input + theme-default reset chip
- Configurable grid (3-8 cols, 3-10 rows), dock (3-7 icons), icon sizes (S/M/L/XL)
- 9 gesture actions: double-tap, triple-tap, swipe-down, swipe-up, pinch, dock-tap, dock-swipe
- Gesture app binding: assign any gesture to launch a specific app (with icon preview in settings)
- Smart fuzzy search with relevance scoring (exact > starts > word > contains > pkg > subsequence)
- Inline calculator and unit converter in drawer search
- Time-aware app suggestions (morning/afternoon/evening/night usage patterns)
- Search history chips with fade gradient, recent apps row with clear button
- Home screen widgets via AppWidgetHost with grid-cell-based sizing
- Contact search with permission chip, web search fallback
- Notification badges (count/dot/hidden), app shortcuts via LauncherApps API
- 5 page transitions (Slide, Cube, Stack, Fade, Depth, Carousel)
- Wallpaper dimming (0-80%) with parallax effect
- Backup/restore layout as JSON with import error feedback
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
IconPackManager        - LruCache, defensive XML parsing, preview icons
ShortcutRepository     - LauncherApps shortcut queries + launching
NotificationListener   - NotificationListenerService for badge counts
AppCategorizer         - Word-boundary tokenized auto-categorization
AppModel               - Safe deserialization, data types, enums
UI (Compose)           - HomeScreen, AppDrawer, Components, Settings, Theme
```

## Build

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ANDROID_HOME="$HOME/AppData/Local/Android/Sdk" ./gradlew assembleRelease
```

Debug build: `./gradlew assembleDebug`

Requires Android SDK 28+ (Android 9), targets SDK 34 (Android 14).
