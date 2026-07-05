param(
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$repo = Resolve-Path (Join-Path $PSScriptRoot "..")

function Read-RepoFile([string]$RelativePath) {
    $path = Join-Path $repo $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required file: $RelativePath"
    }
    [System.IO.File]::ReadAllText($path)
}

function Pass([string]$Message) {
    Write-Host "[PASS] $Message"
}

function Fail([string]$Message) {
    Write-Error "[FAIL] $Message"
    exit 1
}

$androidNs = "http://schemas.android.com/apk/res/android"
$manifest = [xml](Read-RepoFile "app/src/main/AndroidManifest.xml")
$readme = Read-RepoFile "README.md"
$gradleFiles = @(
    "settings.gradle.kts",
    "build.gradle.kts",
    "app/build.gradle.kts",
    "gradle/libs.versions.toml"
) | ForEach-Object {
    $path = Join-Path $repo $_
    if (Test-Path -LiteralPath $path) { $path }
}
$buildText = ($gradleFiles | ForEach-Object { [System.IO.File]::ReadAllText($_) }) -join "`n"

$permissions = @(
    $manifest.manifest.'uses-permission' |
        ForEach-Object { $_.GetAttribute("name", $androidNs) } |
        Where-Object { $_ }
)

foreach ($permission in $permissions) {
    $shortName = $permission.Split(".")[-1]
    if ($readme -notmatch [regex]::Escape($shortName)) {
        Fail "README.md does not justify $shortName"
    }
}
Pass "README.md documents all uses-permission entries"

if ($permissions -contains "android.permission.BIND_APPWIDGET") {
    Fail "BIND_APPWIDGET must not be declared in a libre user app"
}
Pass "Manifest avoids system-only BIND_APPWIDGET"

$application = $manifest.manifest.application
if ($application.GetAttribute("dataExtractionRules", $androidNs) -ne "@xml/data_extraction_rules") {
    Fail "Manifest must reference @xml/data_extraction_rules"
}
if ($application.GetAttribute("fullBackupContent", $androidNs) -ne "@xml/backup_rules") {
    Fail "Manifest must reference @xml/backup_rules"
}

$dataRules = Read-RepoFile "app/src/main/res/xml/data_extraction_rules.xml"
$backupRules = Read-RepoFile "app/src/main/res/xml/backup_rules.xml"
if ($dataRules -notmatch "ll_prefs\.preferences_pb" -or $backupRules -notmatch "ll_prefs\.preferences_pb") {
    Fail "Backup rules must explicitly handle launcher DataStore state"
}
if ($dataRules -notmatch "<cloud-backup" -or $dataRules -notmatch "<device-transfer") {
    Fail "Data extraction rules must separate cloud backup and device transfer"
}
Pass "Backup and data-extraction rules are present"

$forbiddenDependencyPatterns = @(
    "com\.google\.firebase",
    "com\.google\.android\.gms",
    "play-services",
    "crashlytics",
    "admob",
    "appcenter",
    "sentry-android"
)
foreach ($pattern in $forbiddenDependencyPatterns) {
    if ($buildText -match $pattern) {
        Fail "Forbidden proprietary service dependency pattern found: $pattern"
    }
}
Pass "Gradle files contain no Firebase, Play services, ads, or crash-report SDKs"

$settings = Read-RepoFile "settings.gradle.kts"
foreach ($requiredRepository in @("google()", "mavenCentral()", "gradlePluginPortal()")) {
    if ($settings -notmatch [regex]::Escape($requiredRepository)) {
        Fail "settings.gradle.kts is missing $requiredRepository"
    }
}
if ($settings -match "jitpack|maven\s*\(") {
    Fail "settings.gradle.kts contains non-standard external Maven repositories"
}
Pass "Repository list is limited to standard Android/Gradle sources"

$metadataRoot = Join-Path $repo "fastlane/metadata/android/en-US"
foreach ($relative in @(
    "title.txt",
    "short_description.txt",
    "full_description.txt"
)) {
    $path = Join-Path $metadataRoot $relative
    if (-not (Test-Path -LiteralPath $path)) {
        Fail "Missing Fastlane metadata file: fastlane/metadata/android/en-US/$relative"
    }
}

$versionCodeMatch = [regex]::Match((Read-RepoFile "app/build.gradle.kts"), "versionCode\s*=\s*(\d+)")
if (-not $versionCodeMatch.Success) {
    Fail "Unable to read versionCode from app/build.gradle.kts"
}
$changelogPath = Join-Path $metadataRoot ("changelogs/{0}.txt" -f $versionCodeMatch.Groups[1].Value)
if (-not (Test-Path -LiteralPath $changelogPath)) {
    Fail "Missing Fastlane changelog for versionCode $($versionCodeMatch.Groups[1].Value)"
}

$fullDescription = [System.IO.File]::ReadAllText((Join-Path $metadataRoot "full_description.txt"))
foreach ($requiredPhrase in @("No ads", "No analytics", "No cloud account")) {
    if ($fullDescription -notmatch [regex]::Escape($requiredPhrase)) {
        Fail "full_description.txt must state: $requiredPhrase"
    }
}
Pass "Fastlane metadata is present and libre-positioned"

if ($Build) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    $env:ANDROID_HOME = Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk"
    Push-Location $repo
    try {
        & ".\gradlew.bat" --console plain ":app:assembleRelease"
        if ($LASTEXITCODE -ne 0) {
            Fail "assembleRelease failed"
        }
    } finally {
        Pop-Location
    }
    Pass "assembleRelease completed"
}

Write-Host "Libre audit passed."
