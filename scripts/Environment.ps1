Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-WorkspaceRoot { Split-Path $PSScriptRoot -Parent }

function Get-Toolchain([string]$JdkHome) {
    $candidates = @()
    if ($JdkHome) { $candidates += $JdkHome }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $command = Get-Command javac -ErrorAction SilentlyContinue
    if ($command) {
        $item = Get-Item -LiteralPath $command.Source
        if ($item.LinkType) {
            foreach ($target in $item.Target) { $candidates += Split-Path (Split-Path $target -Parent) -Parent }
        }
        $candidates += Split-Path (Split-Path $command.Source -Parent) -Parent
    }
    foreach ($folder in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium")) {
        if (Test-Path -LiteralPath $folder) {
            $candidates += Get-ChildItem -LiteralPath $folder -Directory | ForEach-Object { $_.FullName }
        }
    }
    foreach ($candidate in $candidates) {
        $compiler = Join-Path $candidate 'bin\javac.exe'
        $archive = Join-Path $candidate 'bin\jar.exe'
        if ((Test-Path -LiteralPath $compiler) -and (Test-Path -LiteralPath $archive)) {
            $version = (& $compiler -version 2>&1 | Out-String).Trim()
            $flags = if ($version -match '^javac 1\.8') { @('-source', '8', '-target', '8') } else { @('--release', '8') }
            return @{ Javac = $compiler; Java = (Join-Path $candidate 'bin\java.exe'); Jar = $archive; Flags = $flags; Version = $version }
        }
    }
    throw 'JDK 8 or newer is required. Supply -JdkHome or set JAVA_HOME.'
}

function Get-GameDependencies([string]$GameDir, [string]$BaseModJar, [string]$ModTheSpireJar) {
    $libraries = @()
    $steam = Get-ItemProperty 'HKCU:\Software\Valve\Steam' -ErrorAction SilentlyContinue
    if ($steam) {
        $libraries += $steam.SteamPath
        $vdf = Join-Path $steam.SteamPath 'steamapps\libraryfolders.vdf'
        if (Test-Path -LiteralPath $vdf) {
            $content = Get-Content -LiteralPath $vdf -Raw
            foreach ($match in [regex]::Matches($content, '"path"\s+"([^"]+)"')) {
                $libraries += $match.Groups[1].Value.Replace('\\', '\')
            }
        }
    }
    if (!$GameDir) {
        foreach ($library in $libraries) {
            $candidate = Join-Path $library 'steamapps\common\SlayTheSpire'
            if (Test-Path -LiteralPath (Join-Path $candidate 'desktop-1.0.jar')) { $GameDir = $candidate; break }
        }
    }
    if (!$GameDir) { throw 'Game not found. Supply -GameDir with your SlayTheSpire installation.' }
    $gameJar = Join-Path $GameDir 'desktop-1.0.jar'
    foreach ($library in $libraries) {
        if (!$BaseModJar) {
            $candidate = Join-Path $library 'steamapps\workshop\content\646570\1605833019\BaseMod.jar'
            if (Test-Path -LiteralPath $candidate) { $BaseModJar = $candidate }
        }
        if (!$ModTheSpireJar) {
            $candidate = Join-Path $library 'steamapps\workshop\content\646570\1605060445\ModTheSpire.jar'
            if (Test-Path -LiteralPath $candidate) { $ModTheSpireJar = $candidate }
        }
    }
    foreach ($dependency in @($gameJar, $BaseModJar, $ModTheSpireJar)) {
        if (!$dependency -or !(Test-Path -LiteralPath $dependency -PathType Leaf)) {
            throw 'Missing dependency. Supply -GameDir, -BaseModJar and -ModTheSpireJar or subscribe to BaseMod / ModTheSpire in Steam.'
        }
    }
    return @{ GameDir = $GameDir; GameJar = $gameJar; BaseModJar = $BaseModJar; ModTheSpireJar = $ModTheSpireJar
              Classpath = (@($gameJar, $BaseModJar, $ModTheSpireJar) -join ';') }
}

function Reset-BuildDirectory([string]$RelativePath) {
    $root = [IO.Path]::GetFullPath((Get-WorkspaceRoot))
    $build = [IO.Path]::GetFullPath((Join-Path $root 'build')) + [IO.Path]::DirectorySeparatorChar
    $target = [IO.Path]::GetFullPath((Join-Path $root $RelativePath))
    if (!$target.StartsWith($build, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Build cleanup target is outside the workspace build directory: $target"
    }
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    return $target
}

function Invoke-JavaCompilation($Toolchain, [string[]]$Sources, [string]$Output, [string]$Classpath) {
    $arguments = @($Toolchain.Flags) + @('-Xlint:-options', '-J-Duser.language=en', '-encoding', 'UTF-8', '-d', $Output)
    if ($Classpath) { $arguments += @('-classpath', $Classpath) }
    $arguments += $Sources
    & $Toolchain.Javac @arguments
    if ($LASTEXITCODE -ne 0) { throw "Java compilation failed ($LASTEXITCODE)" }
}
