param([string]$GameDir, [string]$BaseModJar, [string]$ModTheSpireJar, [string]$JdkHome,
    [switch]$WithStSLib,
    [ValidateSet('ENG','DUT','EPO','PTB','ZHS','ZHT','FIN','FRA','DEU','GRE','IND','ITA',
        'JPN','KOR','NOR','POL','RUS','SPA','SRP','SRB','THA','TUR','UKR','VIE')][string]$Language = 'ENG',
    [ValidateRange(800,7680)][int]$Width = 1280, [ValidateRange(600,4320)][int]$Height = 720)
. "$PSScriptRoot\scripts\Environment.ps1"
$toolchain = Get-Toolchain $JdkHome
$dependencies = Get-GameDependencies $GameDir $BaseModJar $ModTheSpireJar
if (!(Test-Path -LiteralPath "$PSScriptRoot\dist\更好的格挡显示.jar")) { throw 'Run build.ps1 first.' }
$output = Reset-BuildDirectory 'build\smoke-classes'
$sources = @(Get-ChildItem -LiteralPath "$PSScriptRoot\src\smoke\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-JavaCompilation $toolchain $sources $output ($dependencies.Classpath + ";$PSScriptRoot\dist\更好的格挡显示.jar")
Copy-Item -Path "$PSScriptRoot\src\smoke\resources\*" -Destination $output -Recurse -Force
$runtime = Join-Path $PSScriptRoot 'build\runtime'
New-Item -ItemType Directory -Force -Path "$runtime\mods","$runtime\user\AppData\Roaming","$runtime\user\AppData\Local" | Out-Null
New-Item -ItemType Directory -Force -Path "$runtime\betaPreferences" | Out-Null
[IO.File]::WriteAllText("$runtime\betaPreferences\STSGameplaySettings",
    (@{ LANGUAGE = $Language } | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
foreach ($name in @('smoke-success.txt','smoke-failure.txt','smoke-assertions.txt','smoke-delta.png','smoke-total.png',
    'smoke-partial.png','smoke-loss.png','smoke-cap.png','smoke-edge.png','smoke-left-edge.png',
    'smoke-zero-current.png','smoke-enemy-turn.png','smoke-zero-forecast.png','smoke-zero-hidden.png',
    'smoke-turn-end-before.png','smoke-turn-end-hold.png','smoke-turn-end-gain.png','smoke-turn-end-damage.png')) {
    $resultFile = Join-Path $runtime $name
    if (Test-Path -LiteralPath $resultFile) { Remove-Item -LiteralPath $resultFile -Force }
}
Copy-Item -LiteralPath $dependencies.GameJar -Destination "$runtime\desktop-1.0.jar" -Force
Copy-Item -LiteralPath $dependencies.BaseModJar -Destination "$runtime\mods\BaseMod.jar" -Force
Copy-Item -LiteralPath "$PSScriptRoot\dist\更好的格挡显示.jar" -Destination "$runtime\mods\更好的格挡显示.jar" -Force
if (Test-Path -LiteralPath "$runtime\mods\BlockReminderReborn.jar") {
    Remove-Item -LiteralPath "$runtime\mods\BlockReminderReborn.jar" -Force
}
& $toolchain.Jar cf "$runtime\mods\SmokeHarness.jar" -C $output .
if ($LASTEXITCODE -ne 0) { throw 'Smoke harness packaging failed' }
# Separate local saves and APPDATA/user.home isolate user data and MTS settings.
[IO.File]::WriteAllLines("$runtime\info.displayconfig", @([string]$Width,[string]$Height,'60','false','false','false'), [Text.UTF8Encoding]::new($false))
$java8 = Join-Path $dependencies.GameDir 'jre\bin\java.exe'
if (!(Test-Path -LiteralPath $java8)) { throw 'The game bundled Java 8 runtime is required for this MTS smoke test.' }
$oldAppData = $env:APPDATA
$oldLocalAppData = $env:LOCALAPPDATA
$modIds = 'basemod,block-reminder-reborn,block-reminder-smoke'
if ($WithStSLib) {
    $workshop = Split-Path (Split-Path $dependencies.BaseModJar -Parent) -Parent
    $stslib = Join-Path $workshop '1609158507\StSLib.jar'
    if (!(Test-Path -LiteralPath $stslib)) { throw 'StSLib workshop jar not found.' }
    Copy-Item -LiteralPath $stslib -Destination "$runtime\mods\StSLib.jar" -Force
    $modIds = 'basemod,stslib,block-reminder-reborn,block-reminder-smoke'
}
try {
    $env:APPDATA = "$runtime\user\AppData\Roaming"
    $env:LOCALAPPDATA = "$runtime\user\AppData\Local"
    Push-Location $runtime
    # Windows PowerShell treats native stderr as errors, including MTS's
    # normal offline Steam notice. Judge the process by its exit code below.
    $previousErrorAction = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $java8 "-Duser.home=$runtime\user" '-Duser.language=en' '-Dfile.encoding=UTF-8' '-Xmx1024m' -jar $dependencies.ModTheSpireJar --skip-launcher --skip-intro --mods $modIds 2>&1 | Tee-Object -FilePath "$runtime\smoke.log"
    } finally { $ErrorActionPreference = $previousErrorAction }
    if ($LASTEXITCODE -ne 0) { throw "Runtime smoke test failed ($LASTEXITCODE). See build\runtime\smoke.log." }
    if (!(Test-Path -LiteralPath "$runtime\smoke-success.txt")) { throw 'Smoke test did not complete.' }
    Get-Content -LiteralPath "$runtime\smoke-assertions.txt","$runtime\smoke-success.txt"
} finally {
    Pop-Location
    $env:APPDATA = $oldAppData
    $env:LOCALAPPDATA = $oldLocalAppData
}
