$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$targetPort = 18081
$lockFile = Join-Path $repoRoot '.learnflow-backend.lock'

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
  param([int]$Port)
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

    if ($proc.ProcessName -in @('java', 'javaw')) {
      Write-Step "Stopping backend process on $Port (PID=$($proc.Id), Name=$($proc.ProcessName))"
      Stop-Process -Id $proc.Id -Force
      continue
    }

    throw "Port $Port is owned by PID=$($proc.Id) Name=$($proc.ProcessName). Please stop it manually."
  }
}

if (Test-Path $lockFile) {
  Remove-Item -Force $lockFile
}

Stop-PortOwnerIfSafe -Port $targetPort
