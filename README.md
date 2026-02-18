# Lawnchair Lite

![Version](https://img.shields.io/badge/version-0.1.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)
![Platform](https://img.shields.io/badge/platform-Android%2028+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.01-4285F4)

> Lightweight Android launcher with 5 built-in dark themes, customizable layouts, and a clean app drawer. Inspired by Lawnchair, built from scratch with Jetpack Compose.

![Screenshot](screenshot.png)

## Quick Start

```bash
git clone https://github.com/SysAdminDoc/lawnchair-lite.git
cd lawnchair-lite
```

1. Open in Android Studio (Hedgehog 2023.1.1+)
2. Sync Gradle
3. Build & run on device/emulator (API 28+)
4. Set as default home app when prompted

## Features

| Feature | Description | Default |
|---------|-------------|---------|
| 5 Dark Themes | Midnight, Glass, OLED, Mocha, Aurora | Glass |
| Grid Layout | 4x5, 5x6, 6x7 configurable columns | 4x5 |
| Icon Shapes | Squircle, Circle, Square, Teardrop, Cylinder | Squircle |
| App Drawer | Grid, List, A-Z alphabetical views | Grid |
| Dock | Configurable 4-6 pinned apps | 5 apps |
| Dense Mode | Compact layout with smaller icons/labels | Off |
| Clock Widget | Live clock with date on home screen | On |
| Dock Search | Search bar above dock opens drawer | On |
| Context Menu | Long-press for info, dock, hide, uninstall | - |
| Hidden Apps | Hide apps from drawer and home grid | - |
| Page Indicator | Animated dot indicators for home pages | - |
| Package Listener | Auto-refresh on app install/uninstall | - |
| Persistent Settings | DataStore-backed preferences survive restarts | - |
| Edge-to-Edge | Transparent status/nav bars, wallpaper visible | - |

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌──────────────────┐
│  MainActivity    │────>│ LauncherViewModel│────>│  AppRepository   │
│                  │     │                  │     │                  │
│  Entry point     │     │  State mgmt     │     │  PackageManager  │
│  Edge-to-edge    │     │  Settings flow  │     │  App loading     │
│  Package events  │     │  Search/filter  │     │  Launch/uninstall│
└─────────────────┘     └─────────────────┘     └──────────────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│  HomeScreen      │     │  LauncherPrefs   │
│                  │     │                  │
│  Pager + Grid   │     │  DataStore       │
│  Clock + Dock   │     │  Theme/Layout    │
│  Drawer/Settings│     │  Dock/Hidden     │
└─────────────────┘     └─────────────────┘
```

## Themes

| Theme | Accent | Style |
|-------|--------|-------|
| Midnight Dark | `#7C6AFF` Purple | Deep navy surfaces, purple glow |
| Frosted Glass | `#00D4FF` Cyan | Translucent panels, glass blur |
| OLED Black | `#00E676` Green | Pure black, minimal borders |
| Catppuccin Mocha | `#CBA6F7` Lavender | Warm dark, community palette |
| Northern Aurora | `#64FFDA` Teal | Deep blue-black, aurora glow |

## Project Structure

```
app/src/main/java/app/lawnchairlite/
├── LauncherApplication.kt      # Application class
├── MainActivity.kt             # Entry point, system bars, package listener
├── LauncherViewModel.kt        # Central state management
├── data/
│   ├── AppModel.kt             # Data classes, enums
│   ├── AppRepository.kt        # PackageManager queries, app launching
│   └── LauncherPrefs.kt        # DataStore persistent settings
└── ui/
    ├── Theme.kt                # 5 color schemes + CompositionLocal
    ├── Components.kt           # AppIcon, SearchBar, Clock, Dock, ContextMenu
    ├── AppDrawer.kt            # Grid/List/Alpha drawer views
    ├── SettingsScreen.kt       # Theme picker, toggles, layout options
    └── HomeScreen.kt           # Main screen composable
```

## Permissions

| Permission | Reason |
|-----------|--------|
| `QUERY_ALL_PACKAGES` | List installed apps in drawer |
| `SET_WALLPAPER` | Wallpaper management |
| `VIBRATE` | Haptic feedback on long-press |
| `BIND_APPWIDGET` | Future widget hosting |

## What This Doesn't Do

- No recents/recent apps integration (requires Launcher3/QuickSwitch)
- No gesture navigation hooks (system-level API)
- No Google Feed/Discover page
- No Smartspacer integration
- No icon pack support (planned)

## FAQ

**Q: How do I set this as my default launcher?**
After installing, press the home button and select "Lawnchair Lite" then "Always".

**Q: Why can't I see some apps?**
Check if they're hidden. Long-press any app > "Hide app" toggles visibility.

**Q: How do I add apps to the dock?**
Long-press an app > "Add to dock". Remove the same way.

## Roadmap

- [ ] Icon pack support
- [ ] Swipe gestures (up/down custom actions)
- [ ] Widget hosting
- [ ] Folder support
- [ ] App usage sorting
- [ ] Backup/restore settings

## License

MIT - see `LICENSE` for details.

Issues and PRs welcome.
