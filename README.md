# Lawnchair Lite v2.3.0

Minimal, fast Android launcher with professional-grade stability.

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

- Multiple home pages with swipe navigation
- App drawer with alphabetical fast scroller
- Folder creation via drag-and-drop
- Icon pack support (ADW/Nova format)
- Custom icon labels
- Hide apps from drawer
- 5 theme modes (Midnight, Glass, OLED, Mocha, Aurora)
- Configurable grid (3-8 cols, 3-10 rows), dock (3-7 icons), icon sizes
- Double-tap and swipe-down gestures (lock, notifications, drawer, settings, kill apps)
- Auto-place newly installed apps on home screen
- Backup/restore layout as JSON
- Device admin for screen lock gesture
- Context menu on home/dock icons (rename, rearrange, remove, uninstall, app info)
- Edit mode with wiggle animation for rearranging
- Notification badge dots with count
- App shortcuts in long-press menus (LauncherApps API)
- Wallpaper dimming (0-80%)
- Recent apps row in drawer
- Search by app name or package name

### v2.3.0

- **Themed icons** - Android 13+ Material You monochrome icon support
- **Drawer sort options** - Sort by name (A-Z), most used, or recently installed
- **Icon label styles** - Shown, Hidden, Home Only, or Drawer Only
- **At-a-Glance battery + alarm** - Battery percentage and next alarm in clock widget
- **Search web fallback** - "Search Google" button when no apps match in drawer
- **Install date tracking** - Apps track first install time for sort-by-recent

### v2.2.0

- Notification badge dots, app shortcuts, wallpaper dimming
- Recent apps row, search by package name, app usage tracking

## Architecture

```
LauncherApplication    - Global crash handler + notification reporter
MainActivity           - Lifecycle, debounced package receiver
LauncherViewModel      - State management, debounced operations, package validation
LauncherPrefs          - DataStore with corruption handler, atomic writes
AppRepository          - Hardened PM calls, package existence checks, themed icons
IconPackManager        - LruCache, defensive XML parsing
ShortcutRepository     - LauncherApps shortcut queries + launching
NotificationListener   - NotificationListenerService for badge counts
AppModel               - Safe deserialization, data types, enums
UI (Compose)           - HomeScreen, AppDrawer, Components, Settings, Theme
```

## Build

```bash
./gradlew assembleDebug
```

Requires Android SDK 28+ (Android 9), targets SDK 34 (Android 14).
