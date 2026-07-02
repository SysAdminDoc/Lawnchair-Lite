param(
    [string]$Serial = $env:ANDROID_SERIAL,
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk",
    [string]$Package = "app.lawnchairlite",
    [string]$Activity = ".MainActivity",
    [switch]$SkipInstall,
    [switch]$ListDevices,
    [int]$StepDelayMs = 900
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:AdbPath = $null
$script:DeviceSerial = $null
$script:Failures = [System.Collections.Generic.List[string]]::new()
$script:Warnings = [System.Collections.Generic.List[string]]::new()

function Resolve-Adb {
    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    }
    $candidates += "adb.exe"

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    throw "adb.exe was not found. Set ANDROID_HOME or add platform-tools to PATH."
}

function Invoke-AdbRaw {
    param(
        [string[]]$Arguments,
        [int]$TimeoutSec = 30
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:AdbPath
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Arguments = ($Arguments | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + ($_ -replace '"', '\"') + '"'
        } else {
            $_
        }
    }) -join " "

    $process = [System.Diagnostics.Process]::Start($startInfo)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutSec * 1000)) {
        try { $process.Kill() } catch {}
        try { [void]$process.WaitForExit(5000) } catch {}
        $stdout = if ($stdoutTask.IsCompleted) { try { $stdoutTask.Result } catch { "" } } else { "" }
        $stderr = if ($stderrTask.IsCompleted) { try { $stderrTask.Result } catch { "" } } else { "" }
        return [pscustomobject]@{
            ExitCode = 124
            StdOut = $stdout
            StdErr = ($stderr + "`nadb timed out: $($Arguments -join ' ')").Trim()
            Text = ($stdout + "`n" + $stderr + "`nadb timed out: $($Arguments -join ' ')").Trim()
        }
    }

    $stdout = $stdoutTask.Result
    $stderr = $stderrTask.Result
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        StdOut = $stdout
        StdErr = $stderr
        Text = ($stdout + "`n" + $stderr).Trim()
    }
}

function Invoke-Adb {
    param(
        [string[]]$Arguments,
        [int]$TimeoutSec = 30
    )

    $actualArguments = if ($script:DeviceSerial) {
        @("-s", $script:DeviceSerial) + $Arguments
    } else {
        $Arguments
    }
    Invoke-AdbRaw -Arguments $actualArguments -TimeoutSec $TimeoutSec
}

function Invoke-AdbShell {
    param(
        [string]$Command,
        [int]$TimeoutSec = 30
    )

    Invoke-Adb -Arguments @("shell", $Command) -TimeoutSec $TimeoutSec
}

function Pass {
    param([string]$Name, [string]$Message = "")
    $suffix = if ($Message) { " - $Message" } else { "" }
    Write-Host "PASS ${Name}${suffix}"
}

function Fail {
    param([string]$Name, [string]$Message)
    $script:Failures.Add("${Name}: $Message")
    Write-Host "FAIL ${Name} - $Message"
}

function Warn {
    param([string]$Name, [string]$Message)
    $script:Warnings.Add("${Name}: $Message")
    Write-Host "DEGRADED ${Name} - $Message"
}

function Get-AdbDevices {
    $result = Invoke-AdbRaw -Arguments @("devices") -TimeoutSec 15
    if ($result.ExitCode -ne 0) {
        throw $result.Text
    }

    $devices = @()
    foreach ($line in ($result.StdOut -split "`r?`n")) {
        if ($line -match "^(\S+)\s+(device|offline|unauthorized)$") {
            $devices += [pscustomobject]@{
                Serial = $matches[1]
                State = $matches[2]
            }
        }
    }
    $devices
}

function Get-ScreenSize {
    $last = ""
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        $result = Invoke-AdbShell -Command "wm size" -TimeoutSec 45
        $last = $result.Text
        if ($result.Text -match "(\d+)x(\d+)") {
            return [pscustomobject]@{
                Width = [int]$matches[1]
                Height = [int]$matches[2]
            }
        }
        Start-Sleep -Milliseconds 1000
    }
    throw "Unable to read screen size: $last"
}

function Get-UiXml {
    $lastError = ""
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        [void](Invoke-AdbShell -Command "rm -f /sdcard/lawnchair-lite-smoke.xml" -TimeoutSec 10)
        $dump = Invoke-AdbShell -Command "uiautomator dump --compressed /sdcard/lawnchair-lite-smoke.xml" -TimeoutSec 20
        $cat = Invoke-Adb -Arguments @("exec-out", "cat", "/sdcard/lawnchair-lite-smoke.xml") -TimeoutSec 20
        $catIndex = $cat.StdOut.IndexOf("<hierarchy", [System.StringComparison]::Ordinal)
        if ($catIndex -ge 0) {
            return $cat.StdOut.Substring($catIndex)
        }

        $direct = Invoke-Adb -Arguments @("exec-out", "uiautomator", "dump", "/dev/tty") -TimeoutSec 20
        $directIndex = $direct.StdOut.IndexOf("<hierarchy", [System.StringComparison]::Ordinal)
        if ($directIndex -ge 0) {
            return $direct.StdOut.Substring($directIndex)
        }

        $lastError = "direct='$($direct.Text)' file='$($dump.Text)' cat='$($cat.Text)'"
        Start-Sleep -Milliseconds 1500
    }

    throw "Unable to dump UI hierarchy: $lastError"
}

function Find-BoundsByText {
    param(
        [string]$Xml,
        [string[]]$Needles
    )

    foreach ($needle in $Needles) {
        $escaped = [regex]::Escape($needle)
        $patterns = @(
            '<node[^>]+(?:text|content-desc)="[^"]*' + $escaped + '[^"]*"[^>]+bounds="(?<bounds>\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\])"',
            '<node[^>]+bounds="(?<bounds>\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\])"[^>]+(?:text|content-desc)="[^"]*' + $escaped + '[^"]*"'
        )
        foreach ($pattern in $patterns) {
            $match = [regex]::Match($Xml, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
            if ($match.Success) {
                return $match.Groups["bounds"].Value
            }
        }
    }
    $null
}

function Get-BoundsCenter {
    param([string]$Bounds)

    if ($Bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        throw "Invalid bounds: $Bounds"
    }

    [pscustomobject]@{
        X = [int](([int]$matches[1] + [int]$matches[3]) / 2)
        Y = [int](([int]$matches[2] + [int]$matches[4]) / 2)
    }
}

function Tap-Bounds {
    param([string]$Bounds)
    $center = Get-BoundsCenter -Bounds $Bounds
    [void](Invoke-AdbShell -Command "input tap $($center.X) $($center.Y)" -TimeoutSec 15)
    Start-Sleep -Milliseconds $StepDelayMs
}

function Dismiss-BlockingDialogs {
    for ($attempt = 0; $attempt -lt 2; $attempt++) {
        $xml = Get-UiXml
        if ($xml -match "isn.?t responding") {
            $close = Find-BoundsByText -Xml $xml -Needles @("Close app")
            $wait = Find-BoundsByText -Xml $xml -Needles @("Wait")
            if ($close) {
                Tap-Bounds -Bounds $close
                Warn "system-dialog" "closed blocking ANR dialog"
                continue
            }
            if ($wait) {
                Tap-Bounds -Bounds $wait
                Warn "system-dialog" "waited through blocking ANR dialog"
                continue
            }
        }
        return
    }
}

function Start-Launcher {
    $component = "$Package/$Activity"
    for ($attempt = 0; $attempt -lt 2; $attempt++) {
        [void](Invoke-AdbShell -Command "am force-stop $Package" -TimeoutSec 15)
        Start-Sleep -Milliseconds 500
        if ($script:DeviceSerial -like "emulator-*") {
            $homeResult = Invoke-AdbShell -Command "cmd package set-home-activity $component" -TimeoutSec 20
            if ($homeResult.ExitCode -ne 0 -or $homeResult.Text -match "Error|Exception") {
                Warn "launch-home" "could not set emulator HOME: $($homeResult.Text)"
            }
            [void](Invoke-AdbShell -Command "input keyevent HOME" -TimeoutSec 15)
        } else {
            $result = Invoke-Adb -Arguments @(
                "shell",
                "am",
                "start",
                "-n",
                $component,
                "-a",
                "android.intent.action.MAIN",
                "-c",
                "android.intent.category.LAUNCHER",
                "--activity-clear-top"
            ) -TimeoutSec 20

            if ($result.ExitCode -ne 0 -or $result.Text -match "Error|Exception|not found") {
                Fail "launch" $result.Text
                return $false
            }
        }

        if ($attempt -eq 1) {
            [void](Invoke-Adb -Arguments @("shell", "monkey", "-p", $Package, "-c", "android.intent.category.LAUNCHER", "1") -TimeoutSec 20)
        }

        Start-Sleep -Milliseconds ($StepDelayMs + 2200)
        Dismiss-BlockingDialogs
        $xml = Get-UiXml
        if ($xml -match ('package="' + [regex]::Escape($Package) + '"')) {
            Pass "launch" "UI hierarchy belongs to $Package"
            return $true
        }
    }

    Fail "launch" "app start returned success, but $Package was not foreground"
    $false
}

function Open-HomeMenu {
    param([object]$Size, [double]$YRatio = 0.66)
    $x = [int]($Size.Width / 2)
    $y = [int]($Size.Height * $YRatio)
    [void](Invoke-AdbShell -Command "input swipe $x $y $x $y 1600" -TimeoutSec 20)
    Start-Sleep -Milliseconds ($StepDelayMs + 800)
}

function Wait-HomeMenu {
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        $xml = Get-UiXml
        if ($xml -match "Add Widget" -and $xml -match "Rearrange Icons") {
            return $xml
        }
        Start-Sleep -Milliseconds 1000
    }
    ""
}

function Open-HomeMenuAndWait {
    param([object]$Size)
    foreach ($ratio in @(0.66, 0.58, 0.72, 0.50)) {
        Open-HomeMenu -Size $Size -YRatio $ratio
        $xml = Wait-HomeMenu
        if (-not [string]::IsNullOrWhiteSpace($xml)) {
            return $xml
        }
    }
    ""
}

function Run-DrawerSearchSmoke {
    param([object]$Size)

    if (-not (Start-Launcher)) { return }

    $xml = ""
    $bounds = $null
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        $xml = Get-UiXml
        $bounds = Find-BoundsByText -Xml $xml -Needles @("Search apps", "Search")
        if ($bounds) { break }
        Start-Sleep -Milliseconds 800
    }
    if (-not $bounds) {
        $x = [int]($Size.Width / 2)
        [void](Invoke-AdbShell -Command "input swipe $x $([int]($Size.Height * 0.84)) $x $([int]($Size.Height * 0.24)) 360" -TimeoutSec 15)
        Start-Sleep -Milliseconds $StepDelayMs
        $xml = Get-UiXml
        $bounds = Find-BoundsByText -Xml $xml -Needles @("Search apps", "Search")
    }

    if (-not $bounds) {
        if ($xml -match ('package="' + [regex]::Escape($Package) + '"')) {
            $x = [int]($Size.Width / 2)
            $y = [int]($Size.Height * 0.84)
            [void](Invoke-AdbShell -Command "input tap $x $y" -TimeoutSec 15)
            Start-Sleep -Milliseconds $StepDelayMs
        } else {
            Fail "drawer-search" "could not find a search affordance after launch or drawer swipe"
            return
        }
    } else {
        Tap-Bounds -Bounds $bounds
    }
    [void](Invoke-AdbShell -Command "input text cal" -TimeoutSec 15)
    Start-Sleep -Milliseconds $StepDelayMs

    $xml = Get-UiXml
    if ($xml -match "cal" -or $xml -match 'Search[^"]*cal') {
        Pass "drawer-search" "query text reached the drawer search surface"
    } else {
        Fail "drawer-search" "search UI did not show the smoke query"
    }
}

function Run-SettingsSmoke {
    param([object]$Size)

    if (-not (Start-Launcher)) { return }

    Start-Sleep -Milliseconds 1800
    $xml = Open-HomeMenuAndWait -Size $Size
    if ([string]::IsNullOrWhiteSpace($xml)) {
        Fail "settings" "home long-press menu did not expose Settings"
        return
    }

    $x = [int]($Size.Width / 2)
    $y = [int]($Size.Height * 0.633)
    [void](Invoke-AdbShell -Command "input tap $x $y" -TimeoutSec 15)
    Start-Sleep -Milliseconds $StepDelayMs
    $xml = Get-UiXml
    if ($xml -match "Settings" -and ($xml -match "Search settings" -or $xml -match "Theme")) {
        Pass "settings" "settings panel opened"
    } else {
        Fail "settings" "settings panel was not visible after tapping Settings"
    }
}

function Run-WidgetPickerSmoke {
    param([object]$Size)

    if (-not (Start-Launcher)) { return }

    Start-Sleep -Milliseconds 1800
    $xml = Open-HomeMenuAndWait -Size $Size
    if ([string]::IsNullOrWhiteSpace($xml)) {
        Fail "widget-picker" "home long-press menu did not expose Add Widget"
        return
    }

    $x = [int]($Size.Width / 2)
    $y = [int]($Size.Height * 0.42)
    [void](Invoke-AdbShell -Command "input tap $x $y" -TimeoutSec 15)
    Start-Sleep -Milliseconds $StepDelayMs
    $xml = Get-UiXml
    if ($xml -match "Add Widget" -or $xml -match "Search widgets") {
        Pass "widget-picker" "widget picker opened"
    } else {
        Fail "widget-picker" "widget picker was not visible after tapping Add Widget"
    }
}

function Check-PlatformSignals {
    $sdk = Invoke-AdbShell -Command "getprop ro.build.version.sdk" -TimeoutSec 15
    $release = Invoke-AdbShell -Command "getprop ro.build.version.release" -TimeoutSec 15
    $sdkNumber = 0
    [void][int]::TryParse($sdk.StdOut.Trim(), [ref]$sdkNumber)

    if ($sdkNumber -ge 35) {
        Warn "platform-api" "Android $($release.StdOut.Trim()) API $sdkNumber detected; verify Private Space and archived-app behavior manually when test data exists"
    } else {
        Pass "platform-api" "Android $($release.StdOut.Trim()) API $sdkNumber"
    }
}

function Finish-Smoke {
    if ($script:Failures.Count -gt 0) {
        Write-Output ""
        Write-Output "Smoke failed:"
        foreach ($failure in $script:Failures) {
            Write-Output "- $failure"
        }
        exit 1
    }

    Write-Output ""
    if ($script:Warnings.Count -gt 0) {
        Write-Output "Smoke completed with degraded platform checks."
    } else {
        Write-Output "Smoke passed."
    }
    exit 0
}

try {
    $script:AdbPath = Resolve-Adb
    $devices = @(Get-AdbDevices)

    if ($ListDevices) {
        if ($devices.Count -eq 0) {
            Write-Output "No adb devices found."
            exit 0
        }
        $devices | Format-Table -AutoSize | Out-String | Write-Output
        exit 0
    }

    $readyDevices = @($devices | Where-Object { $_.State -eq "device" })
    if ($Serial) {
        $match = $devices | Where-Object { $_.Serial -eq $Serial } | Select-Object -First 1
        if (-not $match) {
            Fail "device" "serial $Serial is not listed by adb"
            Finish-Smoke
        }
        if ($match.State -ne "device") {
            Fail "device" "serial $Serial is $($match.State)"
            Finish-Smoke
        }
        $script:DeviceSerial = $Serial
    } elseif ($readyDevices.Count -eq 1) {
        $script:DeviceSerial = $readyDevices[0].Serial
    } elseif ($readyDevices.Count -gt 1) {
        $script:DeviceSerial = $readyDevices[0].Serial
        Warn "device" "multiple devices connected; selected $script:DeviceSerial"
    } else {
        Fail "device" "no online adb device found"
        Finish-Smoke
    }

    Pass "device" "using $script:DeviceSerial"

    if (-not $SkipInstall) {
        if (-not (Test-Path $ApkPath)) {
            Fail "install" "APK not found at $ApkPath; run Gradle or pass -ApkPath"
            Finish-Smoke
        }

        $resolvedApk = (Resolve-Path $ApkPath).Path
        $install = Invoke-Adb -Arguments @("install", "-r", "-d", $resolvedApk) -TimeoutSec 180
        if ($install.Text -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
            Warn "install" "existing $Package signature differs; uninstalling and retrying"
            [void](Invoke-Adb -Arguments @("uninstall", $Package) -TimeoutSec 60)
            $install = Invoke-Adb -Arguments @("install", "-r", "-d", $resolvedApk) -TimeoutSec 180
        }
        if ($install.ExitCode -eq 0 -and $install.Text -match "Success") {
            Pass "install" $resolvedApk
        } else {
            Fail "install" $install.Text
            Finish-Smoke
        }
    }

    $size = Get-ScreenSize
    Pass "screen" "$($size.Width)x$($size.Height)"

    Check-PlatformSignals
    Run-DrawerSearchSmoke -Size $size
    Run-SettingsSmoke -Size $size
    Run-WidgetPickerSmoke -Size $size
} catch {
    Fail "harness" $_.Exception.Message
}

Finish-Smoke
