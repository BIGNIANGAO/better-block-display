param([string]$JdkHome)
. "$PSScriptRoot\scripts\Environment.ps1"
$toolchain = Get-Toolchain $JdkHome
$output = Reset-BuildDirectory 'build\core-tests'
$sources = @(
    Get-ChildItem -LiteralPath "$PSScriptRoot\src\main\java\blockreminder\core" -Filter '*.java' | ForEach-Object FullName
    Get-ChildItem -LiteralPath "$PSScriptRoot\src\main\java\blockreminder\api" -Filter '*.java' | ForEach-Object FullName
    Get-ChildItem -LiteralPath "$PSScriptRoot\src\test\java\blockreminder\core" -Filter '*.java' | ForEach-Object FullName
)
Invoke-JavaCompilation $toolchain $sources $output ''
& $toolchain.Java -ea -classpath $output blockreminder.core.BlockCalculatorTest
if ($LASTEXITCODE -ne 0) { throw 'Core regression tests failed' }
