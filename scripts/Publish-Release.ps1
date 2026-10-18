param(
    [switch]$PrepareOnly,
    [switch]$PublishPrepared,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.android\signing\OneTools')
)
$ErrorActionPreference = 'Stop'
if ($PrepareOnly -and $PublishPrepared) { throw 'Choose one mode.' }
$root = Split-Path -Parent $PSScriptRoot
$repository = 'fun7772ei/OneTools'
$remote = "https://github.com/$repository.git"
$api = "https://api.github.com/repos/$repository"
$gradle = Get-Content -LiteralPath (Join-Path $root 'app/build.gradle.kts') -Raw
if ($gradle -notmatch 'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"') { throw 'Missing semantic version.' }
$version = $Matches[1]
if ($gradle -notmatch 'versionCode\s*=\s*(\d+)') { throw 'Missing version code.' }
$code = [int]$Matches[1]
$stage = Join-Path $root "artifacts/source-publication/$version"
$assets = Join-Path $root "artifacts/github-release/$version"
$preparedPath = Join-Path $assets 'prepared-source.json'
$scanner = Join-Path $root 'scripts/public_source.py'
$apkName = "OneTools-$version.apk"
$apkPath = Join-Path $assets $apkName
$notesPath = Join-Path $root 'docs/RELEASE_NOTES.md'

function Invoke-Git([string[]]$Arguments) {
    & git @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Git operation failed. No force push or history rewrite was attempted.' }
}
function Read-Audit([string]$Directory, [switch]$Tracked) {
    $arguments = @($scanner, 'audit', '--root', $Directory)
    if ($Tracked) { $arguments += '--tracked' }
    $result = & python @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Public source did not pass the safety check.' }
    return ($result | ConvertFrom-Json)
}
function Get-Sdk {
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    $properties = Join-Path $root 'local.properties'
    if (Test-Path -LiteralPath $properties) {
        $line = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
        if ($line) { return $line.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
    throw 'Set ANDROID_HOME to the Android SDK directory.'
}
function Assert-Apk {
    $tools = Join-Path (Get-Sdk) 'build-tools/37.0.0'
    $signature = & (Join-Path $tools 'apksigner.bat') verify --verbose --print-certs $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $certificate = '32eaacc7bafdac2673fa36c9102115ab0251030de5ca421dd6a6baa33e81b68d'
    if (($signature | Select-String -Pattern 'certificate SHA-256 digest:') -notmatch $certificate) { throw 'The APK must use the existing release certificate.' }
    $badging = & (Join-Path $tools 'aapt.exe') dump badging $apkPath
    if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "name='dev.onetools' versionCode='$code' versionName='$version'") { throw 'APK identity or version mismatch.' }
    & (Join-Path $tools 'zipalign.exe') -c -P 16 4 $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment check failed.' }
    $metadata = Get-Content -LiteralPath (Join-Path $assets 'update.json') -Raw | ConvertFrom-Json
    $hash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($metadata.applicationId -ne 'dev.onetools' -or $metadata.versionName -ne $version -or $metadata.versionCode -ne $code -or $metadata.apkName -ne $apkName -or $metadata.sha256 -ne $hash) { throw 'Update metadata differs from the signed APK.' }
    if ((Get-Content -LiteralPath (Join-Path $assets 'SHA256SUMS.txt') -Raw).Trim() -ne "$hash  $apkName") { throw 'Checksum file mismatch.' }
    return $hash
}

$currentSource = Read-Audit $root
if (!$PublishPrepared) {
    & python $scanner self-test
    if ($LASTEXITCODE -ne 0) { throw 'Safety guard self-check failed.' }
    & (Join-Path $PSScriptRoot 'Build-Release.ps1') -SigningDirectory $SigningDirectory -VerifyUpdates
    & (Join-Path $PSScriptRoot 'Prepare-GitHubRelease.ps1')
    if (!(Test-Path -LiteralPath $stage)) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $stage) -Force | Out-Null
        Invoke-Git @('clone', '--branch', 'main', '--single-branch', $remote, $stage)
    }
    if ((git -C $stage remote get-url origin) -ne $remote) { throw 'Unexpected publication remote.' }
    if (git -C $stage status --porcelain --untracked-files=no) { throw 'Publication checkout contains tracked changes. Inspect it before retrying.' }
    Invoke-Git @('-C', $stage, 'pull', '--ff-only', 'origin', 'main')
    & python $scanner export --destination $stage
    if ($LASTEXITCODE -ne 0) { throw 'Export failed.' }
    $previousSdk = $env:ANDROID_HOME
    try {
        $env:ANDROID_HOME = Get-Sdk
        Push-Location -LiteralPath $stage
        try {
            & (Join-Path $stage 'gradlew.bat') :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'The exported public source does not build or pass its checks.' }
        } finally { Pop-Location }
    } finally { $env:ANDROID_HOME = $previousSdk }
    Invoke-Git @('-C', $stage, 'add', '--all')
    Invoke-Git @('-C', $stage, 'update-index', '--chmod=+x', 'gradlew')
    $indexedSource = Read-Audit $stage -Tracked
    if ($indexedSource.sourceSha256 -ne $currentSource.sourceSha256) { throw 'Staged source differs from reviewed source.' }
    & git -C $stage diff --cached --quiet
    if ($LASTEXITCODE -eq 1) {
        Invoke-Git @('-C', $stage, '-c', 'user.name=OneTools', '-c', 'user.email=fun7772ei@users.noreply.github.com', 'commit', '-m', "Publish OneTools $version source and verified release pipeline")
    } elseif ($LASTEXITCODE -ne 0) { throw 'Cannot inspect staged changes.' }
    $head = (git -C $stage rev-parse HEAD).Trim()
    $hash = Assert-Apk
    Copy-Item -LiteralPath $notesPath -Destination (Join-Path $assets 'RELEASE_NOTES.md')
    @{version=$version;versionCode=$code;commit=$head;apkSha256=$hash;sourceSha256=$currentSource.sourceSha256} | ConvertTo-Json | Set-Content -LiteralPath $preparedPath -Encoding utf8
    if ($PrepareOnly) { Write-Output "Verified publication candidate prepared for $version ($head). Nothing pushed yet."; return }
}

if (!(Test-Path -LiteralPath $preparedPath)) { throw 'Prepare the release first.' }
$prepared = Get-Content -LiteralPath $preparedPath -Raw | ConvertFrom-Json
if ((git -C $stage remote get-url origin) -ne $remote -or (git -C $stage status --porcelain)) { throw 'Publication checkout is not clean or has a different remote.' }
$head = (git -C $stage rev-parse HEAD).Trim()
$indexedSource = Read-Audit $stage -Tracked
$hash = Assert-Apk
if ($head -ne $prepared.commit -or $prepared.sourceSha256 -ne $currentSource.sourceSha256 -or $indexedSource.sourceSha256 -ne $currentSource.sourceSha256 -or $hash -ne $prepared.apkSha256 -or $prepared.version -ne $version -or $prepared.versionCode -ne $code) { throw 'Source or APK changed since preparation. Prepare a new candidate.' }

# Retrieve the existing Git Credential Manager credential only after local build processes exit.
# Tokens stay in memory and are never written to the source tree, command line, or Git remote URL.
$credentialText = "protocol=https`nhost=github.com`npath=$repository.git`n`n" | git -c credential.interactive=never credential fill 2>$null
if ($LASTEXITCODE -ne 0) { throw 'GitHub credential is unavailable. Sign in with Git Credential Manager.' }
$credential = @{}
foreach ($line in $credentialText) { if ($line -match '^([^=]+)=(.*)$') { $credential[$Matches[1]]=$Matches[2] } }
$headers = @{Authorization="Bearer $($credential.password)";Accept='application/vnd.github+json';'User-Agent'='OneTools-release';'X-GitHub-Api-Version'='2022-11-28'}
try {
    if ((Invoke-RestMethod 'https://api.github.com/user' -Headers $headers).login -ne 'fun7772ei') { throw 'Unexpected GitHub account.' }
    $repo = Invoke-RestMethod $api -Headers $headers
    if ($repo.full_name -ne $repository -or $repo.private -or $repo.default_branch -ne 'main') { throw 'Unexpected repository, visibility or default branch.' }
    $existing = Invoke-RestMethod "$api/releases?per_page=100" -Headers $headers
    if (@($existing | Where-Object tag_name -eq "v$version").Count) { throw 'This release already exists. Existing published assets are never overwritten.' }
    $existingTag = git -C $stage ls-remote --tags origin "refs/tags/v$version"
    if ($LASTEXITCODE -ne 0 -or $existingTag) { throw 'Cannot create a new release tag. Inspect existing tags before continuing.' }
    $latest = Invoke-RestMethod "$api/releases/latest" -Headers $headers
    $latestMetadataUrl = ($latest.assets | Where-Object name -eq 'update.json').browser_download_url
    if (!$latestMetadataUrl -or !( $latestMetadataUrl.StartsWith("https://github.com/$repository/releases/download/"))) { throw 'Cannot verify the previous release metadata.' }
    $previousMetadata = Invoke-RestMethod $latestMetadataUrl
    if ($previousMetadata -is [string]) { $previousMetadata = $previousMetadata | ConvertFrom-Json }
    if ($previousMetadata.schemaVersion -ne 1 -or $previousMetadata.applicationId -ne 'dev.onetools' -or "$($previousMetadata.versionCode)" -notmatch '^[1-9][0-9]*$') { throw 'Previous release metadata is invalid.' }
    if ($code -le $previousMetadata.versionCode) { throw 'Increase versionCode before publishing.' }
    Invoke-Git @('-c', 'credential.interactive=never', '-C', $stage, 'push', 'origin', 'HEAD:main')
    $deadline = [DateTime]::UtcNow.AddMinutes(25)
    $run = $null
    $lastState = ''
    do {
        $runs = $null
        try {
            $runs = Invoke-RestMethod "$api/actions/workflows/verify.yml/runs?head_sha=$head&event=push&per_page=10" -Headers $headers
        } catch {
            if ([int]$_.Exception.Response.StatusCode -notin @(404, 502, 503, 504)) { throw }
        }
        $run = $runs.workflow_runs | Where-Object head_sha -eq $head | Sort-Object id -Descending | Select-Object -First 1
        if ($run) {
            $state = "$($run.status):$($run.conclusion)"
            if ($state -ne $lastState) { Write-Output "GitHub verification: $state ($($run.html_url))"; $lastState=$state }
            if ($run.status -eq 'completed') { break }
        }
        Start-Sleep -Seconds 15
    } while ([DateTime]::UtcNow -lt $deadline)
    if (!$run -or $run.conclusion -ne 'success') { throw 'GitHub verification did not succeed. Release publication stopped.' }
    Invoke-Git @('-C', $stage, 'tag', "v$version", $head)
    Invoke-Git @('-c', 'credential.interactive=never', '-C', $stage, 'push', 'origin', "refs/tags/v$version")
    $body = @{tag_name="v$version";target_commitish=$head;name="OneTools $version";body=(Get-Content -LiteralPath $notesPath -Raw);draft=$true;prerelease=$false} | ConvertTo-Json
    $release = Invoke-RestMethod -Method Post -Uri "$api/releases" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    $upload = $release.upload_url -replace '\{.*\}$',''
    foreach ($name in @($apkName,'update.json','SHA256SUMS.txt')) {
        $path = Join-Path $assets $name
        $type = if ($name.EndsWith('.apk')) {'application/vnd.android.package-archive'} elseif ($name.EndsWith('.json')) {'application/json'} else {'text/plain'}
        $asset = Invoke-RestMethod -Method Post -Uri "${upload}?name=$name" -Headers $headers -ContentType $type -InFile $path
        $digest = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($asset.state -ne 'uploaded' -or $asset.size -ne (Get-Item -LiteralPath $path).Length -or $asset.digest -ne "sha256:$digest") { throw 'Uploaded asset verification failed. Release remains a draft.' }
    }
    $null = Invoke-RestMethod -Method Patch -Uri "$api/releases/$($release.id)" -Headers $headers -ContentType 'application/json' -Body '{"draft":false,"make_latest":"true"}'
    $published = Invoke-RestMethod "$api/releases/latest" -Headers $headers
    if ($published.tag_name -ne "v$version" -or $published.draft -or $published.prerelease -or $published.assets.Count -ne 3) { throw 'Published release verification failed.' }
    $published | Select-Object tag_name,html_url,@{Name='commit';Expression={$head}},@{Name='assets';Expression={$_.assets | Select-Object name,size,digest}} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $assets 'published.json') -Encoding utf8
    Write-Output "Source and signed APK published: $($published.html_url)"
} finally { $credentialText=$null; $credential=$null; $headers=$null }
