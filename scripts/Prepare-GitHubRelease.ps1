param([switch]$Build)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if ($Build) { & (Join-Path $PSScriptRoot 'Build-Release.ps1') }
$gradle = Get-Content -LiteralPath (Join-Path $root 'app/build.gradle.kts') -Raw
if ($gradle -notmatch 'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"') { throw 'Version name not found.' }
$version = $Matches[1]
if ($gradle -notmatch 'versionCode\s*=\s*(\d+)') { throw 'Version code not found.' }
$code = [int]$Matches[1]
if ($gradle -notmatch 'minSdk\s*=\s*(\d+)') { throw 'Minimum SDK not found.' }
$minSdk = [int]$Matches[1]
$name = "OneTools-$version.apk"
$source = Join-Path $root "app/build/outputs/distribution/release/$name"
if (!(Test-Path -LiteralPath $source -PathType Leaf)) { throw 'Build the release APK first.' }
$built = Get-Content -LiteralPath (Join-Path $root 'app/build/outputs/apk/release/output-metadata.json') -Raw | ConvertFrom-Json
if ($built.applicationId -ne 'dev.onetools' -or $built.elements.Count -ne 1 -or $built.elements[0].versionCode -ne $code -or $built.elements[0].versionName -ne $version) {
    throw 'The built APK metadata does not match the current version. Rebuild the release.'
}
$directory = Join-Path $root "artifacts/github-release/$version"
New-Item -ItemType Directory -Path $directory -Force | Out-Null
Copy-Item -LiteralPath $source -Destination (Join-Path $directory $name)
$hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
$metadata = @{schemaVersion=1;applicationId='dev.onetools';versionCode=$code;versionName=$version;minSdk=$minSdk;apkName=$name;sha256=$hash} | ConvertTo-Json
[IO.File]::WriteAllText((Join-Path $directory 'update.json'), $metadata, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $directory 'SHA256SUMS.txt'), "$hash  $name`n", [Text.UTF8Encoding]::new($false))
Write-Output "Release assets prepared: $directory"
Write-Output 'Verify the APK signature and version, upload all three assets to a draft release with the matching v<version> tag, then publish.'
