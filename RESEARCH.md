# Research — Lawnchair Lite

## Executive Summary
Verified: Lawnchair Lite is a Kotlin/Jetpack Compose Android launcher with a deliberately local-first, grid-and-drawer identity: home pages, dock, folders, widgets, app drawer tabs, fuzzy search, gestures, icon packs, local Smartspace, notification badges, hidden apps, and JSON backup/restore are already implemented. Its strongest current shape is stability-first launcher behavior, including defensive package loading, DataStore corruption recovery, widget bind/config recovery, debounced package events, and release R8 shrinking. Highest-value direction: harden trust and platform edges before broadening features. Priority opportunities are: backup privacy/data-extraction rules, platform-permission audit, release metadata drift cleanup, Compose accessibility semantics, versioned backup restore preview, local diagnostics export, local Android 15/16 smoke testing, i18n extraction, ViewModel boundary splits, dependency/target SDK modernization, drawer group parity, and launcher backup migration.

## Product Map
- Core workflows: set Lawnchair Lite as HOME; arrange home pages/dock/folders/widgets; search apps/contacts/calculator/units/web; configure themes, grids, icon packs, gestures, drawer categories/tabs, Smartspace, badges, hidden apps, and backup/restore.
- User personas: Nova/Lawnchair power users who want a lighter launcher; privacy-first FOSS users; users with large app libraries; work-profile users; widget/theme customization users.
- Platforms and distribution: Android minSdk 28, target/compile SDK 34, AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.01.00, signed APK release build from Gradle; GitHub-hosted repo, with F-Droid/libre readiness still planned.
- Key integrations and data flows: LauncherApps/PackageManager for app/profile inventory; AppWidgetHost/AppWidgetManager for widgets; NotificationListenerService for badges; ContactsProvider, CalendarProvider, LocationManager, and Open-Meteo for optional local Smartspace/search; DataStore plus JSON export/import for settings/layout.

## Competitive Landscape
- Lawnchair upstream: Launcher3-based, Android 16 development branch, rich Pixel-style customization, Smartspacer/QuickSwitch ecosystem. Learn platform-rebase discipline and Launcher3 compatibility boundaries; avoid root/QuickSwitch recents complexity because Lite is already stable without it.
- Kvaesitso: active FOSS search-first launcher with app/contact/calendar/web/cloud search, F-Droid split, plugin/provider concepts, and strong issue signal around providers/widgets/icons. Learn modular provider architecture and diagnostics; avoid replacing Lite's grid workspace with a feed/search-only shell.
- KISS Launcher: small active FOSS launcher focused on fast search, history, favorites, and low resource use. Learn diacritic/transliteration-aware matching and dense history UX; avoid text-only minimalism that would discard Lite's icon/widget/folder strengths.
- Nova Launcher: benchmark for drawer folders/groups, gestures, backups, and user migration expectations. Learn backup/import polish and drawer organization; avoid opaque commercial/account assumptions.
- Smart Launcher: strong automatic app categorization, Nova backup import, modular feature toggles, migration messaging, and explicit privacy answers. Learn migration flow and category UX; avoid cloud/analytics assumptions in the default Lite path.
- Niagara Launcher: premium pop-up folders/widgets, widget stack, one-handed list UX, and notification-in-context surfaces. Learn compact contextual actions and widget stacking; avoid changing Lite into a list-first launcher.
- Action Launcher: covers, shutters, widget stacks, all-apps folders, Quickdrawer, and Quickedit icon suggestions. Learn gesture-revealed widget/folder affordances; avoid feature density that harms discoverability.
- Fossify/Neo/Olauncher/Stario: FOSS/minimal launchers show demand for no-ads/no-bloat positioning, icon packs, backup/restore, categories, hidden apps, and simple release channels. Learn libre metadata and permission transparency; avoid cloning Launcher3 breadth without corresponding maintenance capacity.

## Security, Privacy, and Reliability
- Verified: `app/src/main/AndroidManifest.xml` sets `android:allowBackup="true"` while `LauncherPrefs.exportBackup()` includes search history, suggestion usage, app usage, widgets, home/dock layout, hidden apps, favorites, custom labels, and gesture/app bindings. Android Auto Backup and backup-risk guidance make this the top privacy gap until data extraction rules and manual export toggles land.
- Verified: `app/src/main/AndroidManifest.xml` requests broad or sensitive permissions: `QUERY_ALL_PACKAGES`, `BIND_APPWIDGET`, `POST_NOTIFICATIONS`, `KILL_BACKGROUND_PROCESSES`, `EXPAND_STATUS_BAR`, `READ_CONTACTS`, `READ_CALENDAR`, and `ACCESS_COARSE_LOCATION`. Play/F-Droid readiness requires feature ownership, denial recovery, and removal of unused/system-only permissions.
- Verified: local `CHANGELOG.md` advertises v2.27.0 backup privacy controls, but tracked `README.md` and `app/build.gradle.kts` are v2.26.0 and current tracked code has no `res/xml/backup_rules.xml` or `data_extraction_rules.xml`. Treat release-note/version drift as a blocking release hygiene issue before shipping.
- Verified: `LauncherApplication.kt` only exposes crash reports through notifications and clipboard copy. If notification permission is denied or the notification fails, users have no Settings-visible crash history, support bundle, or local diagnostic export.
- Verified: `SettingsScreen.kt`, `Components.kt`, `AppDrawer.kt`, and `HomeScreen.kt` contain many custom `clickable` rows/chips/icons and drag/drop controls without explicit roles, state descriptions, traversal grouping, or Compose accessibility tests.
- Verified: `SettingsScreen.kt` imports backup JSON by reading the selected file into memory and `LauncherPrefs.importBackup()` applies fields directly without dry-run preview, schema compatibility report, max-size guard, or section-level confirmation.

## Architecture Assessment
- `LauncherViewModel.kt` is about 80 KB and owns app loading, package events, gestures, search, Smartspace, widgets, backup, contacts, launch actions, and settings state. Split search scoring/providers, Smartspace loaders, widget placement, and backup import/export into testable collaborators before adding more feature surface.
- `LauncherPrefs.kt` has strong defensive defaults and corruption recovery, but backup schema is implicit and import mutates state directly. A versioned schema plus dry-run preview is required before third-party migration import.
- UI text is mostly hardcoded in Compose files; `res/values/strings.xml` is minimal. Localization and TalkBack quality both depend on moving user-visible strings and formatted messages into resources.
- Test coverage is narrow: current unit tests cover app categorization, model key/cover serialization, and widget grid span planning. There are no backup import tests, search ranking tests, permission-degradation tests, diagnostics tests, Compose semantics tests, or local instrumentation smoke tests.
- Dependency lane is behind current Android guidance: AGP 8.2.2, Kotlin 1.9.22, Compose BOM 2024.01.00, Activity 1.8.2, DataStore 1.0.0, and Accompanist DrawablePainter 0.34.0. Modernization should be staged with release/debug build proof and icon-rendering regression checks.
- The existing roadmap's hosted CI/device-farm idea conflicts with repo rules that builds and tests happen locally. Replace that direction with a local emulator/device smoke harness for launcher-specific flows.

## Rejected Ideas
- Native QuickSwitch/Recents integration from Lawnchair upstream: rejected because root/system-recents coupling is high-risk and contradicts Lite's stability-first shape.
- Feed-first or search-only redesign from Kvaesitso/Microsoft/Posidon: rejected because Lite's product identity is grid-first with optional search and Smartspace.
- Native Android Private Space control with biometric unlock: rejected because Private Space is platform-owned; implement graceful visibility handling and optional hidden-app protection instead.
- Cloud backup as the first backup improvement: rejected until local Auto Backup rules, explicit export privacy, restore preview, and schema migration are correct.
- Public plugin SDK now: rejected until internal search/Smartspace/widget boundaries exist; Kvaesitso issues show plugin support creates API-stability and support burden.
- Hosted CI/cloud device farm matrix: rejected for this repo because local-build rules forbid GitHub Actions/build CI; use local adb/emulator smoke coverage instead.
- LLM/semantic remote app search: rejected because local search quality, privacy controls, and provider modularity have higher fit and lower risk.
- Wear OS, TV, Android Auto companion launchers: rejected until phone launcher reliability, backup, accessibility, and distribution readiness are stronger.

## Sources
Direct OSS competitors:
- https://github.com/LawnchairLauncher/lawnchair
- https://github.com/LawnchairLauncher/lawnchair/releases
- https://github.com/MM2-0/Kvaesitso
- https://f-droid.org/en/packages/de.mm20.launcher2.release/
- https://github.com/Neamar/KISS
- https://github.com/tanujnotes/Olauncher
- https://github.com/NeoApplications/Neo-Launcher
- https://github.com/FossifyOrg/Launcher
- https://github.com/albu-razvan/Stario
- https://github.com/diluteoxygen/Android-Launcher-Comparison-Table

Commercial, adjacent, and community:
- https://novalauncher.com/faq
- https://docs.smartlauncher.net/faq/start-here/how-to-migrate-from-other-launchers
- https://docs.smartlauncher.net/faq/changelog/6.6
- https://www.smartlauncher.net/blog/a-message-for-nova-users
- https://help.niagaralauncher.app/article/115-pop-ups
- https://play.google.com/store/apps/details?id=bitpit.launcher
- https://play.google.com/store/apps/details?id=com.actionlauncher.playstore
- https://play.google.com/store/apps/details?id=com.microsoft.launcher
- https://support.microsoft.com/en-us/office/using-microsoft-launcher-on-android

Android platform, dependencies, and security:
- https://developer.android.com/develop/ui/views/appwidgets/host
- https://developer.android.com/develop/ui/views/appwidgets/previews
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/privacy-and-security/risks/backup-best-practices
- https://developer.android.com/training/package-visibility
- https://support.google.com/googleplay/android-developer/answer/10158779
- https://developer.android.com/privacy-and-security/risks/android-exported
- https://developer.android.com/develop/ui/compose/accessibility/semantics
- https://developer.android.com/topic/performance/baselineprofiles/overview
- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html

## Open Questions
- Which distribution target should define the next release gate: GitHub-only signed APK, Play-compatible build, F-Droid-compatible flavor, or separate Play/FOSS variants?
- Should explicit launcher JSON exports include search history, app usage/recents, hidden apps, and widget bindings by default, or should those be opt-in sections?
