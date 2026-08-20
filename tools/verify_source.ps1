param(
    [switch]$Build,
    [switch]$ArchiveReady
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$required = @(
    'gradlew.bat',
    'gradle/wrapper/gradle-wrapper.jar',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/assets/xposed_init',
    'app/src/main/res/values/arrays.xml'
)

foreach ($relative in $required) {
    $path = Join-Path $project $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing required source file: $relative"
    }
}

if (Test-Path -LiteralPath (Join-Path $project 'local.properties')) {
    throw 'local.properties must not be included in the source archive'
}

if ($ArchiveReady) {
    $forbiddenDirectories = @('.gradle', 'build', 'app/build')
    foreach ($relative in $forbiddenDirectories) {
        if (Test-Path -LiteralPath (Join-Path $project $relative)) {
            throw "Generated directory must not be included in the source archive: $relative"
        }
    }
    $forbiddenExtensions = @('.apk', '.aab', '.jks', '.keystore', '.zip')
    $forbiddenFiles = @(Get-ChildItem -LiteralPath $project -Recurse -File -Force |
        Where-Object { $forbiddenExtensions -contains $_.Extension.ToLowerInvariant() })
    if ($forbiddenFiles.Count -ne 0) {
        $relative = $forbiddenFiles[0].FullName.Substring($project.Length + 1)
        throw "Generated or sensitive file must not be included in the source archive: $relative"
    }
    Write-Host '[OK] archive excludes generated APKs, caches, keys and nested ZIP files'
}

$scope = Get-Content -LiteralPath (Join-Path $project 'app/src/main/res/values/arrays.xml') -Raw
$scopePackages = @([regex]::Matches($scope, '<item>([^<]+)</item>') |
    ForEach-Object { $_.Groups[1].Value.Trim() })
if ($scopePackages.Count -ne 1 -or $scopePackages[0] -ne 'com.miui.home') {
    throw "Unexpected Xposed scope: $($scopePackages -join ', ')"
}

$manifest = Get-Content -LiteralPath (Join-Path $project 'app/src/main/AndroidManifest.xml') -Raw
if ($manifest -match 'android:debuggable\s*=') {
    throw 'Manifest must not hardcode android:debuggable'
}

$java = Get-ChildItem -LiteralPath (Join-Path $project 'app/src/main/java') -Recurse -Filter *.java |
    Get-Content -Raw
$forbidden = @('WindowManagerService', 'system_server', 'this.f$0', 'Code decompiled incorrectly')
foreach ($pattern in $forbidden) {
    if ($java -match [regex]::Escape($pattern)) {
        throw "Forbidden source marker found: $pattern"
    }
}

Write-Host '[OK] source structure, scope and forbidden-marker checks passed'

if ($Build) {
    Push-Location $project
    try {
        & .\gradlew.bat :app:assembleDebug :app:assembleRelease --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle exited with $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}
