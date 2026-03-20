# Lawnchair Lite - Working Notes

## Tech Stack
- Kotlin, Jetpack Compose, Material 3
- Android SDK 28+ (min), SDK 34 (target)
- AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.01.00
- DataStore for preferences, Accompanist DrawablePainter for icons

## Build
```bash
./gradlew assembleDebug
```

## Key Files
- `LauncherViewModel.kt` - Central state management (~450 lines)
- `HomeScreen.kt` - Main launcher surface with Launcher3-style drawer transition (~500 lines)
- `AppDrawer.kt` - Scrollable grid with nested scroll dismiss, recent apps, web search (~200 lines)
- `Components.kt` - All UI components (icons, menus, folders, dialogs, At-a-Glance ~470 lines)
- `SettingsScreen.kt` - Full settings panel (~350 lines)
- `Theme.kt` - 5 color schemes
- `data/LauncherPrefs.kt` - DataStore with corruption recovery
- `data/AppRepository.kt` - Hardened PackageManager wrapper, themed icons
- `data/IconPackManager.kt` - Icon pack with LRU cache
- `data/ShortcutRepository.kt` - LauncherApps shortcut queries
- `data/AppModel.kt` - Data types, serialization, DrawerSort, LabelStyle enums
- `NotificationListener.kt` - NotificationListenerService for badge counts

## Architecture
- Single Activity, Compose-only UI
- Drawer transition: Animatable float 0-1, Launcher3 settle thresholds
- NestedScrollConnection intercepts grid scroll for drawer dismiss
- DataStore with ReplaceFileCorruptionHandler
- Defensive deserialization (never crashes on bad data)
- Debounced package events (300ms)
- NotificationListener communicates via companion StateFlow
- ShortcutRepository queries LauncherApps API
- filteredApps uses combine() with 5 flows for sort/filter

## Version History
- v2.12.0: Neon theme (hot pink cyberpunk), tap-to-copy calculator/converter results, folder notification badges, drawer entrance scale, double-tap clock cycles style
- v2.11.0: Clock styles (Large/Compact/Minimal), unit converter in search (km/mi/F/C/lb/kg/etc.), Edit Mode gesture action, long-press empty cell enters edit mode, app launch count in context menu
- v2.10.0: Smart fuzzy search (relevance-scored: exact>starts-with>word>contains>package>subsequence), inline calculator (math expression eval in drawer search), hexagon + diamond icon shapes, draggable fast-scroller rail
- v2.9.0: App suggestions row (time-bucketed usage prediction), search history with chips, themed notification badges (accent-colored)
- v2.8.0: Home screen widgets (AppWidgetHost, picker, render, remove), contact search in drawer, staggered drawer animation
- v2.7.0: Flashlight gesture, triple-tap gesture, pinch-in gesture, dock handle tap action, wallpaper parallax, drawer animation toggle
- v2.6.0: Section headers, dock styles (solid/pill/floating/transparent), search bar styles, haptic levels, drawer opacity, label font size, folder columns, app version info
- v2.5.0: Drawer categories (auto-categorized tabs), home screen lock, drawer column count, custom accent color, icon shadow, app categorizer
- v2.4.0: Page transitions (cube/stack/fade), dock swipe-up actions, badge styles (count/dot/hidden), grid padding, clock tap actions, status bar hide
- v2.3.0: Themed icons, drawer sort, label styles, At-a-Glance battery/alarm, web search fallback, install date tracking
- v2.2.0: Notification badges, app shortcuts, wallpaper dimming, recent apps, search by package, expanded grid options
- v2.1.0: Icon packs, folders, custom labels, hidden apps, gestures, backup/restore, edit mode
- v2.0.0: Initial stability architecture, crash handler, DataStore corruption recovery

## Gotchas
- GridCellView: do NOT use `cell` as pointerInput key - restarts gesture on recomposition
- drawerVisible must NOT be a pointerInput key - cancels drag mid-flight
- NEVER set LazyVerticalGrid userScrollEnabled=false - kills NestedScrollConnection
- Package name search: match on `packageName` in addition to `label`
- Notification badges require user granting notification access in system settings
- App shortcuts require launcher being default (hasShortcutHostPermission)
- Wallpaper dim max is 80% (not 100%) to keep some visibility
- Themed icons only available on API 33+ (Android 13)
- filteredApps combine() uses Array<Any?> overload for 5+ flows
- AlarmManager.nextAlarmClock can return null (no alarm set)
- BatteryManager.BATTERY_PROPERTY_CAPACITY can return -1 on some devices
- Triple-tap detection: uses double-tap timing (400ms window after last double-tap = triple)
- CameraManager.setTorchMode requires API 23+ (SDK 28 min covers this)
- Pinch gesture threshold: zoom < 0.7 triggers action (pinch-in only, not pinch-out)
- GridCell.Widget added to sealed class — all exhaustive when() expressions must include it
- AppWidgetHost ID is 1024 — fixed constant, must match across startListening/stopListening
- BIND_APPWIDGET is a protected permission — most widgets need user approval via bind intent
- Widget grid cells are invisible (alpha=0) — actual widget rendered by overlay AndroidView
- Contact search debounced at 200ms, max 5 results, requires READ_CONTACTS permission
- Suggestion usage stores "bucket:appKey=count" format — time buckets: morning/afternoon/evening/night
- Search history max 10 items, suggestion usage capped at 200 entries to prevent unbounded growth
- Badge colors now use theme accent — no longer hardcoded red
- Launch tracking uses batched DataStore write (saveLaunchTracking) — 3 writes → 1
- Search history terms are escaped for pipe delimiter (escapeSearchTerm/unescapeSearchTerm)
- NotificationListener debounces rebuildCounts at 150ms
- IconPackManager shared fields are @Volatile for thread safety
- dropOnGrid caps target index to prevent unbounded list growth
- Contact search checks READ_CONTACTS permission before querying
- Widget host auto-cleans stale entries when provider returns null info
- Fuzzy search scoring: 100=exact, 90=starts-with, 80=word-starts, 70=contains, 60=pkg, 50=subsequence
- Calculator uses recursive descent parser (evalExpression/evalTerm/evalFactor), supports +-*/% and parentheses
- HexagonShape and DiamondShape are top-level GenericShape constants (not recreated per call)
- FastScrollerRail uses awaitEachGesture for combined tap+drag (single gesture handler)
