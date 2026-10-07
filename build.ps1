param(
    [string]$GameDir,
    [string]$BaseModJar,
    [string]$ModTheSpireJar,
    [string]$JdkHome,
    [switch]$SkipTests
)
. "$PSScriptRoot\scripts\Environment.ps1"
if (!$SkipTests) { & "$PSScriptRoot\test.ps1" -JdkHome $JdkHome }
$toolchain = Get-Toolchain $JdkHome
$dependencies = Get-GameDependencies $GameDir $BaseModJar $ModTheSpireJar
$output = Reset-BuildDirectory 'build\classes'
$sources = @(Get-ChildItem -LiteralPath "$PSScriptRoot\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-JavaCompilation $toolchain $sources $output $dependencies.Classpath
Copy-Item -Path "$PSScriptRoot\src\main\resources\*" -Destination $output -Recurse -Force
New-Item -ItemType Directory -Force -Path "$output\META-INF" | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\LICENSE" -Destination "$output\META-INF\LICENSE" -Force
$dist = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$artifact = Join-Path $dist '更好的格挡显示.jar'
& $toolchain.Jar cf $artifact -C $output .
if ($LASTEXITCODE -ne 0) { throw 'Jar packaging failed' }
$digest = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$artifact.sha256", "$digest  更好的格挡显示.jar" + [Environment]::NewLine,
    [Text.UTF8Encoding]::new($false))
$report = [ordered]@{ compiler = $toolchain.Version; target = 'Java 8'; game = $dependencies.GameJar
    baseMod = $dependencies.BaseModJar; modTheSpire = $dependencies.ModTheSpireJar
    artifact = $artifact; sha256 = $digest }
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'build\build-report.json'), ($report | ConvertTo-Json),
    [Text.UTF8Encoding]::new($false))
Write-Host "Built $artifact"
Write-Host "SHA-256 $digest"
