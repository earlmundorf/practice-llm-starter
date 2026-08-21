<#
QRSPI kit installer — Windows PowerShell. Identical behaviour to install.sh.

    .\qrspi-kit\install.ps1 list                      show available profiles
    .\qrspi-kit\install.ps1 <profile>                 install into the kit's parent directory
    .\qrspi-kit\install.ps1 <profile> -Target <dir>   install into <dir> instead

If the execution policy blocks it:
    powershell -ExecutionPolicy Bypass -File .\qrspi-kit\install.ps1 <profile>

Nothing is ever deleted outside .claude/skills/qrspi and .claude/commands/cq.
An existing working-docs/config.json is never overwritten.
#>

[CmdletBinding()]
param(
  [Parameter(Position = 0)] [string] $ProfileName,
  [string] $Target
)

$ErrorActionPreference = 'Stop'

$KitDir      = $PSScriptRoot
$ProfilesDir = Join-Path $KitDir 'profiles'
$VersionFile = Join-Path $KitDir 'VERSION'
$KitVersion  = if (Test-Path $VersionFile) { (Get-Content $VersionFile -Raw).Trim() } else { 'unknown' }

function Die([string]$msg) {
  Write-Host "error: $msg" -ForegroundColor Red
  exit 1
}

function Show-Profiles {
  Write-Host ""
  Write-Host "QRSPI kit $KitVersion - available profiles:"
  Write-Host ""
  Get-ChildItem -Path $ProfilesDir -Filter *.json | Sort-Object Name | ForEach-Object {
    $d = Get-Content $_.FullName -Raw | ConvertFrom-Json
    Write-Host ("  {0,-24} {1}" -f $_.BaseName, $d.stack)
    Write-Host ("  {0,-24} {1}" -f "", $d._notes[0])
    Write-Host ""
  }
  Write-Host "Install with:  .\qrspi-kit\install.ps1 <profile>"
  Write-Host ""
}

if (-not $ProfileName -or $ProfileName -in @('list', '--list', '-l', 'help', '--help', '-h')) {
  Show-Profiles
  exit 0
}

# ---- validate everything BEFORE touching the target ---------------------------------
$src = Join-Path $ProfilesDir "$ProfileName.json"
if (-not (Test-Path $src)) {
  Write-Host "error: no such profile: $ProfileName" -ForegroundColor Red
  Show-Profiles
  exit 1
}
if (-not $Target) { $Target = Split-Path -Parent $KitDir }
if (-not (Test-Path $Target -PathType Container)) { Die "target directory does not exist: $Target" }
if (-not (Test-Path (Join-Path $KitDir 'skills/qrspi/SKILL.md'))) {
  Die "kit looks incomplete: skills/qrspi/SKILL.md not found"
}

$profileData = Get-Content $src -Raw | ConvertFrom-Json
$tv = $profileData.triggerVocabulary
if ([string]::IsNullOrWhiteSpace($tv)) { Die "$ProfileName.json has no triggerVocabulary field" }
if ($tv -match '[\r\n]')               { Die "triggerVocabulary in $ProfileName.json must be a single line" }

$TargetFull = (Resolve-Path $Target).Path
Write-Host ""
Write-Host "Installing profile $ProfileName (kit $KitVersion)"
Write-Host "  into $TargetFull"
Write-Host ""

# ---- 1. the skill (generated: replaced wholesale) -----------------------------------
$skillDir = Join-Path $TargetFull '.claude/skills/qrspi'
if (Test-Path $skillDir) { Remove-Item $skillDir -Recurse -Force }
New-Item -ItemType Directory -Path $skillDir -Force | Out-Null
Copy-Item -Path (Join-Path $KitDir 'skills/qrspi/*') -Destination $skillDir -Recurse -Force
Write-Host "  .claude/skills/qrspi/            installed"

# ---- 2. render the one placeholder in the installed copy ----------------------------
$skillFile = Join-Path $skillDir 'SKILL.md'
$content = (Get-Content $skillFile -Raw).Replace('{{TRIGGER_VOCABULARY}}', $tv)
Set-Content -Path $skillFile -Value $content -NoNewline
if ((Get-Content $skillFile -Raw) -match '\{\{TRIGGER_VOCABULARY\}\}') {
  Die "placeholder substitution failed in SKILL.md"
}
Write-Host "  SKILL.md frontmatter             rendered from triggerVocabulary"

# ---- 3. publish the /cq: commands ---------------------------------------------------
$cqDir = Join-Path $TargetFull '.claude/commands/cq'
New-Item -ItemType Directory -Path $cqDir -Force | Out-Null
Copy-Item -Path (Join-Path $KitDir 'skills/qrspi/commands/*.md') -Destination $cqDir -Force
Write-Host "  .claude/commands/cq/             published (/cq:go ... /cq:7_validate)"

# ---- 4. the config: never overwrite -------------------------------------------------
$wd = Join-Path $TargetFull 'working-docs'
New-Item -ItemType Directory -Path $wd -Force | Out-Null
$cfg = Join-Path $wd 'config.json'
if (Test-Path $cfg) {
  Copy-Item $src "$cfg.new" -Force
  Write-Host "  working-docs/config.json         KEPT (yours) - profile written to config.json.new"
  $configNote = "existing config kept; review config.json.new and merge, then delete it"
} else {
  Copy-Item $src $cfg -Force
  Write-Host "  working-docs/config.json         written from $ProfileName"
  $configNote = "config written; fill in any <placeholders> it names"
}

# ---- 5. seed the findings log -------------------------------------------------------
$fd = Join-Path $wd 'findings'
New-Item -ItemType Directory -Path $fd -Force | Out-Null
foreach ($f in @('README.md', 'TEMPLATE.md')) {
  $dest = Join-Path $fd $f
  if (-not (Test-Path $dest)) {
    Copy-Item (Join-Path $KitDir "skills/qrspi/findings-seed/$f") $dest -Force
  }
}
Write-Host "  working-docs/findings/           seeded (existing findings untouched)"

# ---- 6. stamp what produced this install --------------------------------------------
@"
profile: $ProfileName
profileVersion: $($profileData.profileVersion)
kitVersion: $KitVersion
installedAt: $(Get-Date -Format 'yyyy-MM-dd')
source: qrspi-kit/skills/qrspi
note: generated directory - edit the kit and re-install, never edit here
"@ | Set-Content -Path (Join-Path $skillDir '.installed-from')
Write-Host "  .installed-from                  stamped"

# ---- 7. ignore the kit, when it lives inside the target -----------------------------
$kitInsideTarget = ((Split-Path -Parent $KitDir) -eq $TargetFull)
if ($kitInsideTarget) {
  $gi = Join-Path $TargetFull '.gitignore'
  $has = (Test-Path $gi) -and ((Get-Content $gi) -contains '/qrspi-kit/')
  if (-not $has) {
    if (Test-Path $gi) {
      $raw = Get-Content $gi -Raw
      if ($raw.Length -gt 0 -and -not $raw.EndsWith("`n")) { Add-Content -Path $gi -Value "" }
    }
    Add-Content -Path $gi -Value "# QRSPI kit - local tool copy, not part of the project"
    Add-Content -Path $gi -Value "/qrspi-kit/"
    Write-Host "  .gitignore                       added /qrspi-kit/"
  } else {
    Write-Host "  .gitignore                       already ignores /qrspi-kit/"
  }
}

# ---- schema check -------------------------------------------------------------------
try {
  $c = Get-Content $cfg -Raw | ConvertFrom-Json
  $known = @('profile','profileVersion','stack','workingDir','buildTool','packageManager','appModule',
             'framework','protectedPaths','apiBoundary','build','changeTypeVerbs','jira','researchLayers',
             'questionCategories','manualVerificationSurfaces','sliceExample','verbNamespaces',
             'triggerVocabulary','_notes','project')
  $verbs = if ($c.build) { $c.build.PSObject.Properties.Name } else { @() }
  $bad = @()
  if ($c.changeTypeVerbs) {
    foreach ($p in $c.changeTypeVerbs.PSObject.Properties) {
      foreach ($v in $p.Value) { if ($verbs -notcontains $v) { $bad += $v } }
    }
  }
  $unknown = @($c.PSObject.Properties.Name | Where-Object { $known -notcontains $_ })
  if ($bad.Count) {
    Write-Host ("  config check                     ERROR: changeTypeVerbs names verbs missing from build: {0}" -f (($bad | Select-Object -Unique) -join ', '))
  }
  if ($unknown.Count) {
    Write-Host ("  config check                     warning: unknown keys (typo?): {0}" -f ($unknown -join ', '))
  }
  if (-not $bad.Count -and -not $unknown.Count) { Write-Host "  config check                     ok" }
} catch {
  Write-Host "  config check                     WARNING: config.json is not readable JSON"
}

Write-Host ""
Write-Host "Done. $configNote"
Write-Host "Next:  /cq:go <TICKET-KEY>          (tiers: trivial | simple | full | comprehensive)"
if ($kitInsideTarget) {
  Write-Host "The kit was left in place; it is gitignored and safe to delete by hand."
} else {
  Write-Host "Installed from $KitDir (left untouched)."
}
Write-Host ""
