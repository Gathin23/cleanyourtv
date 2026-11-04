<#
.SYNOPSIS
  Sets up the AI for tv_ai_agent.py from PowerShell, with no web page.

.DESCRIPTION
  Writes %APPDATA%\tv-monitor\config.json, the same file the --setup page writes. The key is asked for here,
  hidden, and saved encrypted for your Windows user (DPAPI); it is never printed, never put on the command line and
  never sent to the TV. Values you do not pass keep what is already saved.

.PARAMETER TvAddress      IP address of the TV, for example 192.168.1.50.
.PARAMETER Url            API address. Default https://api.anthropic.com/v1/messages.
.PARAMETER Model          Model name. Default claude-sonnet-5-5.
.PARAMETER RevertSeconds  Seconds to confirm a change before it is undone, 20 to 600. Default 60.
.PARAMETER KeepKey        Do not ask for a key; keep the saved one.
.PARAMETER Test           After saving, send one tiny request (8 tokens) to prove the key and address work.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\set-ai.ps1 -TvAddress 192.168.1.50 -Test

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\set-ai.ps1 -Model claude-sonnet-5-5 -RevertSeconds 90 -KeepKey
#>
[CmdletBinding()]
param(
    [string]$TvAddress,
    [string]$Url,
    [string]$Model,
    [ValidateRange(20, 600)][int]$RevertSeconds,
    [switch]$KeepKey,
    [switch]$Test
)

$ErrorActionPreference = 'Stop'
try { Add-Type -AssemblyName System.Security } catch { }
try { Add-Type -AssemblyName System.Security.Cryptography.ProtectedData } catch { }

$dir  = Join-Path $env:APPDATA 'tv-monitor'
$path = Join-Path $dir 'config.json'

# What is saved already, as an ordered table, so unknown fields survive.
$cfg = [ordered]@{}
if (Test-Path $path) {
    try {
        (Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $cfg[$_.Name] = $_.Value }
    } catch {
        Write-Warning "The saved config could not be read and will be replaced: $($_.Exception.Message)"
    }
}

function Protect-Secret([string]$secret) {
    $raw = [Text.Encoding]::UTF8.GetBytes($secret)
    $enc = [Security.Cryptography.ProtectedData]::Protect($raw, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    'dpapi:' + [Convert]::ToBase64String($enc)
}

function Unprotect-Secret([string]$stored) {
    $kind, $body = $stored -split ':', 2
    $data = [Convert]::FromBase64String($body)
    if ($kind -eq 'dpapi') {
        $data = [Security.Cryptography.ProtectedData]::Unprotect($data, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    }
    [Text.Encoding]::UTF8.GetString($data)
}

if ($TvAddress)                       { $cfg['host'] = $TvAddress.Trim() }
if ($Url)                             { $cfg['url'] = $Url.Trim() }
if ($Model)                           { $cfg['model'] = $Model.Trim() }
if ($PSBoundParameters.ContainsKey('RevertSeconds')) { $cfg['revert_seconds'] = $RevertSeconds }

# The key: hidden prompt; Enter keeps the saved one.
$newKey = $null
if (-not $KeepKey) {
    $prompt = if ($cfg['key']) { 'API key (Enter keeps the saved one)' } else { 'API key' }
    $secure = Read-Host -Prompt $prompt -AsSecureString
    if ($secure.Length -gt 0) {
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $newKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
        $cfg['key'] = Protect-Secret $newKey
    }
}
if (-not $cfg['key'] -and -not $env:ANTHROPIC_API_KEY) {
    Write-Warning 'No key saved and ANTHROPIC_API_KEY is not set: the agent will not be able to call the AI.'
}

New-Item -ItemType Directory -Force $dir | Out-Null
# UTF-8 without a byte-order mark: the Python agent reads this file.
[IO.File]::WriteAllText($path, ($cfg | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
try { icacls $path /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null } catch { }

Write-Host "Saved: $path"
foreach ($k in 'host', 'url', 'model', 'revert_seconds') {
    if ($cfg.Contains($k)) { Write-Host ("  {0,-15} {1}" -f $k, $cfg[$k]) }
}
Write-Host ("  {0,-15} {1}" -f 'key', $(if ($cfg['key']) { 'saved, encrypted for this Windows user' } elseif ($env:ANTHROPIC_API_KEY) { 'from ANTHROPIC_API_KEY' } else { 'none' }))

if ($Test) {
    $key = if ($env:ANTHROPIC_API_KEY) { $env:ANTHROPIC_API_KEY } elseif ($cfg['key']) { Unprotect-Secret $cfg['key'] } else { $null }
    if (-not $key) { throw 'Nothing to test: there is no key.' }
    $uri  = if ($cfg['url'])   { $cfg['url'] }   else { 'https://api.anthropic.com/v1/messages' }
    $mod  = if ($cfg['model']) { $cfg['model'] } else { 'claude-sonnet-5-5' }
    $body = @{ model = $mod; max_tokens = 8; messages = @(@{ role = 'user'; content = 'Reply with the word ok.' }) } | ConvertTo-Json -Depth 5
    try {
        $reply = Invoke-RestMethod -Method Post -Uri $uri -TimeoutSec 60 -ContentType 'application/json' -Body $body `
            -Headers @{ 'x-api-key' = $key; 'anthropic-version' = '2023-06-01' }
        Write-Host ("Test call: ok ({0})" -f ($reply.content[0].text -replace '\s+', ' ').Trim())
    } catch {
        Write-Host "Test call failed: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
}
