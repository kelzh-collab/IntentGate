param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$ExpectedSignerSha256,
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$apk = Get-Item -LiteralPath $ApkPath
if ($apk.Extension -ne '.apk') { throw 'Expected an APK file.' }
if (-not $SdkPath) { throw 'Set ANDROID_HOME or supply SdkPath.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkPath 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android SDK Build Tools are unavailable.' }

# Windows PowerShell can represent native stderr warnings as non-terminating errors.
function Invoke-ApkVerificationTool([string]$Executable, [string[]]$ToolArgs, [string]$FailureMessage) {
    $ErrorActionPreference = 'Continue'
    $result = & $Executable @ToolArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw $FailureMessage }
    $result | Out-String
}

$signature = Invoke-ApkVerificationTool (Join-Path $buildTools.FullName 'apksigner.bat') `
    @('verify', '--verbose', '--print-certs', $apk.FullName) 'APK signature verification failed.'
$signers = [regex]::Matches($signature, 'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})')
if ($signers.Count -ne 1 -or $signers[0].Groups[1].Value -ine $ExpectedSignerSha256) {
    throw 'APK signer does not match the explicitly approved signing certificate.'
}
if ($signature -match 'CN=Android Debug') { throw 'Debug signing certificates cannot be used for this release.' }

$badging = Invoke-ApkVerificationTool (Join-Path $buildTools.FullName 'aapt2.exe') `
    @('dump', 'badging', $apk.FullName) 'APK package metadata could not be read.'
if ($badging -notmatch "package: name='com\.intentgate\.app' versionCode='3' versionName='1\.0\.0-beta\.2'") {
    throw 'APK applicationId or version does not match v1.0.0-beta.2.'
}
if ($badging -notmatch "(?m)^(?:minSdkVersion|sdkVersion):'26'" -or $badging -notmatch "(?m)^targetSdkVersion:'37'") {
    throw 'APK SDK metadata does not match the approved build configuration.'
}
if ($badging -match '(?m)^application-debuggable') { throw 'A debuggable APK is not a release artifact.' }

$hash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
if ($OutputDirectory) {
    $output = New-Item -ItemType Directory -Path $OutputDirectory -Force
    $checksum = Join-Path $output.FullName 'SHA256SUMS'
    [System.IO.File]::WriteAllText($checksum, "$hash  $($apk.Name)`n", [System.Text.Encoding]::ASCII)
}
[pscustomobject]@{
    File = $apk.Name
    ApplicationId = 'com.intentgate.app'
    VersionName = '1.0.0-beta.2'
    VersionCode = 3
    MinSdk = 26
    SignerSha256 = $ExpectedSignerSha256.ToLowerInvariant()
    FileSha256 = $hash
    Verified = $true
}
