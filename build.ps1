$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$buildDir = Join-Path $projectDir 'build'
$classesDir = Join-Path $buildDir 'classes'
$testClassesDir = Join-Path $buildDir 'test-classes'

$resolvedProjectDir = [System.IO.Path]::GetFullPath($projectDir).TrimEnd('\')
$resolvedBuildDir = [System.IO.Path]::GetFullPath($buildDir)
if (-not $resolvedBuildDir.StartsWith($resolvedProjectDir + '\', [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe build directory: $resolvedBuildDir"
}
if ([System.IO.Directory]::Exists($resolvedBuildDir)) {
    [System.IO.Directory]::Delete($resolvedBuildDir, $true)
}
New-Item -ItemType Directory -Force -Path $classesDir, $testClassesDir | Out-Null

$mainSources = Get-ChildItem (Join-Path $projectDir 'src/main/java') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d $classesDir $mainSources
if ($LASTEXITCODE -ne 0) { throw 'Main source compilation failed' }

$jarStagingDir = Join-Path $buildDir 'jar-staging'
$metaInfDir = Join-Path $jarStagingDir 'META-INF'
New-Item -ItemType Directory -Force -Path $jarStagingDir, $metaInfDir | Out-Null
Copy-Item -Path (Join-Path $classesDir '*') -Destination $jarStagingDir -Recurse -Force
Set-Content -Path (Join-Path $metaInfDir 'MANIFEST.MF') -Encoding ascii -NoNewline `
    -Value "Manifest-Version: 1.0`r`nMain-Class: com.example.donation.DonationServer`r`n`r`n"

$jarPath = Join-Path $projectDir 'donation-service.jar'
if ([System.IO.File]::Exists($jarPath)) {
    [System.IO.File]::Delete($jarPath)
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($jarStagingDir, $jarPath)

$testSources = Get-ChildItem (Join-Path $projectDir 'src/test/java') -Recurse -Filter '*.java' |
    Where-Object { $_.Name -ne 'DonationServerJUnitTest.java' } |
    ForEach-Object { $_.FullName }
javac -encoding UTF-8 -cp $classesDir -d $testClassesDir $testSources
if ($LASTEXITCODE -ne 0) { throw 'Test source compilation failed' }

java -ea -cp "$classesDir;$testClassesDir" com.example.donation.DonationServerTest
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }

Write-Host "Built: $(Join-Path $projectDir 'donation-service.jar')"
