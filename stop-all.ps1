$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$stopBackendScript = Join-Path $repoRoot 'stop-backend.ps1'
$runFile = Join-Path $repoRoot '.learnflow-dev-processes.json'

$frontendPort = 5173
$agentPort = 8000

function Write-Step {
  param([string]$Message)
  Write-Host "[LearnFlow] $Message" -ForegroundColor Cyan
}

function Get-PortListeners {
  param([int]$Port)
  $lines = @(netstat -ano -p tcp | Select-String "LISTENING" | Select-String ":$Port\s")
  $listeners = @()
  foreach ($line in $lines) {
    $text = ($line.ToString() -replace '\s+', ' ').Trim()
    $parts = $text.Split(' ')
    if ($parts.Length -ge 5) {
      $listeners += [pscustomobject]@{
        Pid = [int]$parts[4]
      }
    }
  }
  return $listeners
}

function Stop-PortOwnerIfSafe {
  param(
    [int]$Port,
    [string[]]$AllowedProcessNames
  )

  $listeners = @(Get-PortListeners -Port $Port)
  if (-not $listeners.Count) {
    Write-Step "Port $Port is already free."
    return
  }

  foreach ($listener in $listeners) {
    try {
      $proc = Get-Process -Id $listener.Pid -ErrorAction Stop
    } catch {
      continue
    }

    if ($AllowedProcessNames -contains $proc.ProcessName) {
      Write-Step "Stopping port owner on $Port (PID=$($proc.Id), Name=$($proc.ProcessName))"
      Stop-Process -Id $proc.Id -Force
      continue
    }

    throw "Port $Port is owned by PID=$($proc.Id) Name=$($proc.ProcessName). Please stop it manually."
  }
}

function Stop-RecordedWindow {
  param(
    [object]$RunInfo,
    [string]$PropertyName,
    [string]$Label
  )

  if (-not $RunInfo) {
    return
  }

  $pidValue = $RunInfo.$PropertyName
  if (-not $pidValue) {
    return
  }

  try {
    $proc = Get-Process -Id $pidValue -ErrorAction Stop
    if ($proc.ProcessName -in @('powershell', 'pwsh')) {
      Write-Step "Closing $Label window (PID=$pidValue)"
      Stop-Process -Id $pidValue -Force
    }
  } catch {
    return
  }
}

$runInfo = $null
if (Test-Path $runFile) {
  try {
    $runInfo = Get-Content $runFile -Raw | ConvertFrom-Json
  } catch {
    $runInfo = $null
  }
}

if (Test-Path $stopBackendScript) {
  & $stopBackendScript
}

Stop-PortOwnerIfSafe -Port $frontendPort -AllowedProcessNames @('node', 'powershell', 'pwsh')
Stop-PortOwnerIfSafe -Port $agentPort -AllowedProcessNames @('python', 'powershell', 'pwsh')

Stop-RecordedWindow -RunInfo $runInfo -PropertyName 'frontendWindowPid' -Label 'frontend'
Stop-RecordedWindow -RunInfo $runInfo -PropertyName 'agentWindowPid' -Label 'agent'
Stop-RecordedWindow -RunInfo $runInfo -PropertyName 'backendWindowPid' -Label 'backend'

if (Test-Path $runFile) {
  Remove-Item -Force $runFile
}

Write-Host ""
Write-Host "LearnFlow dev stack stopped." -ForegroundColor Green
