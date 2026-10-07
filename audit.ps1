param([string]$Artifact)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (!$Artifact) { $Artifact = Join-Path $PSScriptRoot 'dist\更好的格挡显示.jar' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($Artifact)
try {
    $classes = 0
    $majorVersions = [Collections.Generic.HashSet[int]]::new()
    $forbidden = @()
    $localizations = @{}
    foreach ($entry in $archive.Entries) {
        $name = $entry.FullName
        if ($name -match '^(com/|basemod/|energizedSpire/)' -or $name -match '(?i)smoke') { $forbidden += $name }
        if ($name.EndsWith('.class')) {
            $stream = $entry.Open()
            try {
                $header = [byte[]]::new(8)
                if ($stream.Read($header,0,8) -ne 8) { throw "Truncated class: $name" }
                if ($header[0] -ne 0xca -or $header[1] -ne 0xfe -or $header[2] -ne 0xba -or $header[3] -ne 0xbe) {
                    throw "Invalid class: $name"
                }
                $majorVersions.Add(($header[6] -shl 8) + $header[7]) | Out-Null
                $classes++
            } finally { $stream.Dispose() }
        }
        if ($name.EndsWith('.json')) {
            $reader = [IO.StreamReader]::new($entry.Open(),[Text.Encoding]::UTF8)
            try {
                $jsonData = $reader.ReadToEnd() | ConvertFrom-Json
                if ($name -match '^blockreminder/localization/([a-z]{3})/UIStrings\.json$') {
                    $localizations[$Matches[1]] = $jsonData.'block-reminder-reborn:UI'.TEXT
                }
            }
            finally { $reader.Dispose() }
        }
    }
    if ($classes -eq 0 -or $majorVersions.Count -ne 1 -or !$majorVersions.Contains(52)) { throw 'Classes are not all Java 8 compatible.' }
    if ($forbidden.Count) { throw ('Forbidden dependency/test entries: ' + ($forbidden -join ', ')) }
    foreach ($required in @('ModTheSpire.json','META-INF/LICENSE','blockreminder/images/badge.png',
        'blockreminder/images/block-preview.png',
        'blockreminder/localization/eng/UIStrings.json','blockreminder/localization/zhs/UIStrings.json',
        'blockreminder/localization/zht/UIStrings.json')) {
        if (!$archive.GetEntry($required)) { throw "Missing release resource: $required" }
    }
    $supportedLanguages = @('eng','dut','epo','ptb','zhs','zht','fin','fra','deu','gre','ind','ita',
        'jpn','kor','nor','pol','rus','spa','srp','srb','tha','tur','ukr','vie')
    foreach ($language in $supportedLanguages) {
        if (!$localizations.ContainsKey($language)) { throw "Missing localization: $language" }
        $localizedText = $localizations[$language]
        if ($localizedText.Count -ne 24) { throw "Incomplete localization: $language" }
        for ($index = 0; $index -lt 24; $index++) {
            if ([string]::IsNullOrWhiteSpace($localizedText[$index]) -or $localizedText[$index].Contains([char]0xfffd)) {
                throw "Invalid localization text: $language at $index"
            }
            $expectedTokens = @([regex]::Matches($localizations['eng'][$index], '%[a-z]') | ForEach-Object Value)
            $actualTokens = @([regex]::Matches($localizedText[$index], '%[a-z]') | ForEach-Object Value)
            if (($expectedTokens -join '|') -ne ($actualTokens -join '|')) {
                throw "Localization placeholder mismatch: $language at $index"
            }
        }
    }
} finally { $archive.Dispose() }
$hash = (Get-FileHash -LiteralPath $Artifact -Algorithm SHA256).Hash.ToLowerInvariant()
$declared = (Get-Content -LiteralPath "$Artifact.sha256" -Raw).Split(' ',[StringSplitOptions]::RemoveEmptyEntries)[0]
if ($hash -ne $declared) { throw 'Artifact hash does not match checksum file.' }
$report = [ordered]@{ result = 'PASS'; classes = $classes; javaClassMajor = 52;
    bytes = (Get-Item -LiteralPath $Artifact).Length; forbiddenEntries = $forbidden; sha256 = $hash;
    languages = $supportedLanguages; textRowsPerLanguage = 24 }
New-Item -ItemType Directory -Force -Path "$PSScriptRoot\build" | Out-Null
[IO.File]::WriteAllText("$PSScriptRoot\build\package-audit.json", ($report | ConvertTo-Json -Depth 3),
    [Text.UTF8Encoding]::new($false))
Write-Host "PASS release archive audit ($classes classes, Java 8, 24 languages, SHA-256 verified)"
