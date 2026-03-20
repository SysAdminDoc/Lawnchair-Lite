# Lawnchair Lite v2.14.0

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

### v2.14.0

- **Reverse Sort (Z-A)** - Descending alphabetical ordering in drawer sort options
- **Depth Page Transition** - 6th transition: behind-page shrinks to 0.75x with fade while front page slides over (parallax depth)
- **Dock Handle Pulse** - Breathing accent-colored glow animation (2s cycle) on the dock handle bar
- **Scroll-to-Top** - Tap the drawer drag handle to instantly scroll the app list to the top
- **XL Icon Size** - Extra Large (66dp) icons for better visibility and larger touch targets

### v2.13.0

- **Accent Color Presets** - 12 popular colors as tappable circles for quick accent selection (Red, Pink, Purple, Indigo, Blue, Cyan, Green, Orange, and more)
- **Search Clear Button** - X icon in drawer search field to instantly clear text (appears when search is non-empty)
- **Carousel Page Transition** - New 3D rotating carousel effect for home screen pages (5th transition option)
- **App Size in Context Menu** - Shows installed APK size alongside version and launch count (e.g., "Chrome v125 · 45.2 MB · 142 launches")
- **Reset All Settings** - One-tap button in Quick Actions to restore all preferences to factory defaults

### v2.12.0

- **Neon Theme** - Hot pink/magenta cyberpunk dark theme with pure white text and deep black background
- **Tap-to-Copy Results** - Tap calculator or unit converter results to copy to clipboard with toast confirmation
- **Folder Notification Badges** - Folders show total notification count from all apps inside (uses accent color)
- **Drawer Entrance Scale** - Subtle 0.95x to 1.0x scale-up effect as the drawer opens for a premium feel
- **Double-Tap Clock Cycling** - Double-tap the clock time to cycle through Large/Compact/Minimal styles with haptic feedback

### v2.11.0

- **Clock Styles** - Large (full At-a-Glance with date, time, battery, alarm), Compact (single-line date/time/battery), Minimal (oversized time only)
- **Unit Converter** - Type unit conversions in drawer search: "10km" → 6.21 mi, "100F" → 37.78 C, "5lb" → 2.27 kg. Supports km/mi, m/ft, cm/in, lb/kg, oz/g, gal/L, F/C
- **Edit Mode Gesture** - "Edit Mode" added to gesture actions, assignable to double-tap, triple-tap, pinch, or dock handle
- **Long-Press Empty Cell** - Long-pressing an empty home grid cell enters edit mode for rearranging
- **App Launch Count** - Context menu shows total launch count per app (e.g., "142 launches")

### v2.10.0

- **Smart Fuzzy Search** - Relevance-scored search: exact match > starts with > word match > contains > package name > subsequence (fuzzy). Typing "chr" finds Chrome, "sett" finds Settings
- **Inline Calculator** - Type math expressions in drawer search (e.g., "2+3", "15*4.5", "(100-20)/3") and see the result instantly. Supports +, -, *, /, %, parentheses
- **New Icon Shapes** - Hexagon and Diamond shapes added to the existing 4 (Squircle, Circle, Square, Teardrop)
- **Draggable Fast-Scroller** - Drag along the letter rail in the drawer to rapidly scroll through the alphabet (previously tap-only)

### v2.9.0

- **App Suggestions** - Smart time-aware suggestion row on home screen, predicts apps based on morning/afternoon/evening/night usage patterns
- **Search History** - Recent search terms shown as chips in drawer, tap to re-search, individual remove, clear all
- **Themed Notification Badges** - Badge colors now use the theme accent color instead of hardcoded red

### v2.8.0

- **Home Screen Widgets** - Add widgets to home screen via AppWidgetHost, picker dialog, render + remove in edit mode
- **Contact Search** - Search drawer finds matching contacts with call buttons
- **Staggered Drawer Animation** - Improved drawer open/close animation

### v2.7.0

- **Flashlight Gesture** - Toggle flashlight via gesture action
- **Triple-Tap Gesture** - Configurable triple-tap action (400ms window)
- **Pinch-In Gesture** - Pinch to trigger action (zoom < 0.7 threshold)
- **Dock Handle Tap** - Configurable action on dock handle tap
- **Wallpaper Parallax** - Subtle parallax scrolling on multi-page
- **Drawer Animation Toggle** - Enable/disable drawer transition animation

### v2.6.0

- **Alphabetic section headers** - Letter dividers in drawer grid (A, B, C...)
- **Dock styles** - Solid, Pill, Floating, Transparent dock backgrounds
- **Search bar styles** - Pill, Bar, Minimal (icon only), Hidden
- **Haptic feedback intensity** - Off, Light, Medium, Strong
- **Drawer background opacity** - Adjustable 50-100% opacity
- **Icon label font size** - Small (9sp), Medium (11sp), Large (13sp)
- **Folder grid columns** - Configurable 3-5 columns per folder
- **App version in context menu** - Shows version info on long-press

### v2.5.0

- **Drawer categories** - Smart auto-categorized tabs (All, Games, Social, Media, Tools, Work, Other)
- **Home screen lock** - Prevent accidental drag/remove/edit with toast feedback
- **Drawer column count** - Separate column count from home grid (Auto or 1-6)
- **Custom accent color** - Hex color picker overrides theme accent globally
- **Icon shadow** - Toggleable drop shadow/elevation on app icons
- **App categorizer** - Heuristic-based categorization by package name and label

### v2.4.0

- **Page transitions** - Slide, Cube, Stack, and Fade effects when swiping between home pages
- **Dock swipe-up actions** - Assign a secondary app to each dock slot, launch on swipe up
- **Badge style options** - Count (default), Dot Only, or Hidden
- **Grid padding controls** - Adjustable horizontal and vertical icon spacing (0-24dp)
- **Clock tap actions** - Tap date to open calendar, tap time to open clock app
- **Status bar hide** - Option to hide status bar on home screen for more space

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
