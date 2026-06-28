# Research - Lawnchair Lite

## Executive Summary
Lawnchair Lite is a compact Android launcher built with Kotlin, Jetpack Compose, Material 3, DataStore, AppWidgetHost, LauncherApps, and a deliberately local-first feature set. Verified: the strongest current shape is stability plus fast customization: defensive PackageManager loading, DataStore corruption recovery, local Smartspace, drawer tabs, folder covers, category rules, dock labels, widgets, icon packs, backup/restore, and gesture binding are already present. Highest-value direction: keep the "Lite" promise by hardening platform edges before adding more surface area. Priority opportunities are widget bind/config recovery, backup privacy controls, manifest permission policy, custom Compose accessibility semantics, localization, backup schema migration, local diagnostics, dependency/toolchain modernization, diacritic-aware search, drawer folder parity, and migration import from incumbent launchers.

## Product Map
- Core workflows: set as HOME launcher; arrange home pages, dock, folders, and widgets; open/search apps, contacts, shortcuts, calculator, units, and web; configure themes, grids, icon packs, gestures, drawer tabs/categories, Smartspace, badges, hidden apps, and backup/restore.
- User personas: power users leaving Nova/Lawnchair upstream; privacy-first FOSS users; users wanting a stable minimal launcher; work-profile users; customization-heavy icon/theme users.
- Platforms and distribution: Android minSdk 28, compile/target SDK 34, signed APK from Gradle; GitHub-hosted public repo; no Play-specific build flavor currently.
- Key integrations and data flows: LauncherApps/PackageManager for apps and profiles; AppWidgetHost for hosted widgets; NotificationListenerService for badges; CalendarProvider, ContactsProvider, LocationManager, and Open-Meteo for Smartspace/contact search; DataStore plus JSON export/import for layout and settings.

## Competitive Landscape
- Lawnchair upstream: strong Launcher3 rebase discipline, Android 16 base, Smartspacer, global search, dynamic theming, QuickSwitch, and font/icon customization. Learn the platform-upgrade cadence and permission/search polish; avoid root-only recents and upstream feature breadth that would weaken Lite's stability goal.
- Kvaesitso: search-first FOSS launcher with app/contact/calendar/web/cloud providers, F-Droid distribution, crash reporter, debug export, translation infrastructure, and plugin-style modules. Learn modular search providers and diagnostics; avoid turning the home screen into a feed/search product that changes Lawnchair Lite's grid-first identity.
- KISS Launcher: tiny, fast, search-centric launcher with learned ranking and low memory expectations. Learn diacritic/transliteration-aware matching and fast recency ranking; avoid text-only minimalism because this project already centers icons, folders, widgets, and themes.
- Nova Launcher: still the benchmark for gestures, backups, drawer groups, per-icon/folder gestures, and migration expectations. Learn backup import/export polish and app-drawer group parity; avoid opaque commercial dependencies, ad/tracker concerns, and paywall-shaped features.
- Smart Launcher: strong automatic categorization, smart folders, theme adaptation, and backup/migration flows. Learn synced drawer/home folder concepts and Nova import expectations; avoid over-automating layout changes without user review.
- Niagara Launcher: excellent one-handed app list, app pop-ups, pop-up widgets, and notification-in-context flows. Learn lightweight contextual surfaces; avoid replacing the grid workspace with list-first navigation.
- Microsoft Launcher: mainstream cloud/local backup, feed integration, and calendar/to-do productivity surface. Learn transfer and recovery flows; avoid account lock-in and feed bloat.
- Neo/Fossify/Stario/Olauncher: FOSS launchers show demand for privacy-first distribution, minimal UI, icon packs, categories, backup/restore, and simple migration. Learn F-Droid/libre positioning and issue triage patterns; avoid duplicate forks of Launcher3 complexity without the upstream rebase capacity.

## Security, Privacy, and Reliability
- Verified: `app/src/main/AndroidManifest.xml` requests `QUERY_ALL_PACKAGES`, `BIND_APPWIDGET`, `READ_CONTACTS`, `READ_CALENDAR`, `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS`, and `KILL_BACKGROUND_PROCESSES`; each needs a Settings-facing purpose, degrade path, and Play/F-Droid policy note before wider distribution.
- Verified: `app/src/main/AndroidManifest.xml` has `android:allowBackup="true"` while `app/src/main/java/app/lawnchairlite/data/LauncherPrefs.kt` exports search history, app usage, widgets, home grid, dock grid, hidden apps, favorites, and gesture bindings. Add data extraction rules or require encrypted device backup so launcher habits do not leak through cloud backup unintentionally.
- Verified: `app/src/main/java/app/lawnchairlite/ui/HomeScreen.kt` deletes widget IDs and shows a toast when `bindAppWidgetIdIfAllowed()` returns false, but Android's host guidance says the host should launch the bind permission flow and handle widget configuration. This is the top recovery gap for widgets.
- Verified: `app/src/main/java/app/lawnchairlite/LauncherApplication.kt` posts crash notifications and `CrashCopyReceiver.kt` copies reports, but there is no Settings-visible crash history/debug bundle and no fallback when notification permission is absent.
- Verified: `app/src/main/java/app/lawnchairlite/ui/SettingsScreen.kt`, `AppDrawer.kt`, and `Components.kt` use many custom `clickable` rows, text buttons, chips, and icon images without explicit roles, state descriptions, or traversal semantics; automated Compose accessibility tests are absent.

## Architecture Assessment
- `LauncherViewModel.kt` is the central module for app loading, gestures, search, Smartspace, widgets, backup, contacts, and actions. Split search providers, Smartspace loaders, widget binding, and backup migration into focused classes before more features land.
- `LauncherPrefs.kt` already validates many import fields, but the backup format is implicit JSON with no schema version migrator, dry-run preview, or compatibility report. Add versioned import/export and tests for stale, future, and partial backups.
- `SettingsScreen.kt` uses hardcoded display strings throughout; only `res/values/strings.xml` is minimal. Move user-facing text to resources before adding translations or F-Droid metadata.
- `app/build.gradle.kts` is on AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.01.00, Activity 1.8.2, DataStore 1.0.0, and Accompanist DrawablePainter 0.34.0. This is stable but now behind platform, Compose, accessibility-test, generated-widget-preview, and Android 15/16 readiness work.
- Tests currently cover categorization and AppInfo key stability only. Add tests around backup migration, search scoring, widget bind decisions, permission degradation, and Compose semantics.

## Rejected Ideas
- Root recents/QuickSwitch parity from Lawnchair upstream: rejected because it adds root-only crash and maintenance risk that conflicts with Lite's stability target.
- Full feed-first redesign from Kvaesitso/Microsoft/Posidon: rejected because Lawnchair Lite is a grid launcher with optional Smartspace, not a feed shell.
- Native Android 15 Private Space integration: rejected because third-party launchers cannot rely on privileged/profile-owner APIs; strengthen hidden apps and biometric reveal instead.
- Cloud account backup as the first backup improvement: rejected for now because local JSON, privacy rules, and schema migration must be correct before adding sync.
- Android Auto, Wear OS, and TV companion launchers: rejected because phone launcher reliability, tablets/foldables, and backup migration have higher user impact.
- Remote semantic app search: rejected because local search, contacts/calendar providers, and privacy controls should land first.

## Sources
Direct OSS competitors:
- https://github.com/LawnchairLauncher/lawnchair
- https://github.com/MM2-0/Kvaesitso
- https://f-droid.org/en/packages/de.mm20.launcher2.release/
- https://github.com/Neamar/KISS
- https://github.com/tanujnotes/Olauncher
- https://github.com/NeoApplications/Neo-Launcher
- https://github.com/FossifyOrg/Launcher
- https://github.com/albu-razvan/Stario

Commercial and community:
- https://novalauncher.com/
- https://novalauncher.com/faq
- https://play.google.com/store/apps/details?id=ginlemon.flowerfree
- https://docs.smartlauncher.net/faq
- https://niagaralauncher.com/
- https://help.niagaralauncher.app/article/40-niagara-pro-features
- https://play.google.com/store/apps/details?id=com.microsoft.launcher
- https://support.microsoft.com/en-us/office/using-microsoft-launcher-on-android
- https://github.com/diluteoxygen/Android-Launcher-Comparison-Table
- https://f-droid.org/en/categories/launcher/

Android platform and dependencies:
- https://developer.android.com/develop/ui/views/appwidgets/host
- https://developer.android.com/reference/kotlin/android/appwidget/AppWidgetManager
- https://developer.android.com/develop/ui/views/appwidgets/previews
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/privacy-and-security/risks/backup-best-practices
- https://developer.android.com/privacy-and-security/risks/android-exported
- https://developer.android.com/develop/ui/compose/accessibility/semantics
- https://developer.android.com/develop/ui/compose/accessibility/testing
- https://developer.android.com/topic/performance/baselineprofiles/overview
- https://developer.android.com/about/versions/15/behavior-changes-15
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html

## Open Questions
- Which distribution channel is the primary target for the next public build: GitHub-only, Play, F-Droid-compatible, or separate Play/FOSS flavors?
- Should explicit JSON backups include search history and app usage by default, or should those be opt-in sections?
