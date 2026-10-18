param(
    [ValidateSet('apk', 'bundle')][string]$Format = 'apk',
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.android\signing\OneTools'),
    [switch]$VerifyUpdates
)

$ErrorActionPreference = 'Stop'
$configPath = Join-Path $SigningDirectory 'signing.local.json'
if (!(Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw 'Signing configuration is missing. Restore the existing signing folder from backup; do not replace the release key.'
}
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$keystorePath = Join-Path $SigningDirectory $config.keystoreFile
if (!(Test-Path -LiteralPath $keystorePath -PathType Leaf)) {
    throw 'Release keystore is missing. Restore it from backup.'
}
if ([string]::IsNullOrWhiteSpace($config.password) -or [string]::IsNullOrWhiteSpace($config.alias)) {
    throw 'Signing configuration is incomplete.'
}

$values = @{
    ONETOOLS_KEYSTORE = $keystorePath
    ONETOOLS_STORE_PASSWORD = $config.password
    ONETOOLS_KEY_ALIAS = $config.alias
    ONETOOLS_KEY_PASSWORD = $config.password
}
$previous = @{}
$projectRoot = Split-Path -Parent $PSScriptRoot
try {
    foreach ($name in $values.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }
    Push-Location -LiteralPath $projectRoot
    try {
        $task = if ($Format -eq 'bundle') { ':app:bundleRelease' } else { ':app:assembleRelease' }
        $tasks = @($task)
        if ($VerifyUpdates) { $tasks += @(':app:testReleaseUnitTest', ':app:lintRelease', ':app:assembleReleaseAndroidTest', '-PtestBuildType=release') }
        & (Join-Path $projectRoot 'gradlew.bat') @tasks --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Release build failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    $config = $null
    $values = $null
}
