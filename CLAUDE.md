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
