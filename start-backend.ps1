param(
  [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $repoRoot 'backend'
$targetPort = 18081
$lockFile = Join-Path $repoRoot '.learnflow-backend.lock'

function Write-Step {
  param([string]$Message)
  Write-Host "[LearnFlow] $Message" -ForegroundColor Cyan
}

function Set-RunLock {
  $payload = [pscustomobject]@{
    pid = $PID
    createdAt = (Get-Date).ToString('s')
  } | ConvertTo-Json -Compress
  [System.IO.File]::WriteAllText($lockFile, $payload, (New-Object System.Text.UTF8Encoding($false)))
}

function Clear-RunLock {
  if (Test-Path $lockFile) {
    Remove-Item -Force $lockFile
  }
}

function Assert-NoActiveLock {
  if (-not (Test-Path $lockFile)) {
    return
  }

  try {
    $raw = Get-Content $lockFile -Raw | ConvertFrom-Json
    $existingPid = [int]$raw.pid
    $existingProc = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
    if ($existingProc) {
      throw "A backend start task is already running (PID=$existingPid). Please close the existing backend window or run stop-backend.bat first."
    }
  } catch {
    Write-Step "Found a stale lock file. Removing it."
  }

  Clear-RunLock
}

function Get-JavaProcessCommandLine {
  param([int]$ProcessId)
  try {
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId"
    return $proc.CommandLine
  } catch {
    return ''
  }
}

function Stop-LearnFlowBackendProcesses {
  $javaProcesses = @(Get-Process java, javaw -ErrorAction SilentlyContinue)
  if (-not $javaProcesses.Count) {
    Write-Step "No existing Java process was found."
    return
  }

  $stopped = @()
  foreach ($proc in $javaProcesses) {
    $commandLine = Get-JavaProcessCommandLine -ProcessId $proc.Id
    $looksLikeBackend =
      $commandLine -match 'learnflow-backend' -or
      $commandLine -match [regex]::Escape($backendDir) -or
      $commandLine -match 'spring-boot:run'

    if ($looksLikeBackend) {
      Write-Step "Stopping old backend process PID=$($proc.Id)"
      Stop-Process -Id $proc.Id -Force
      $stopped += $proc.Id
    }
  }

  if (-not $stopped.Count) {
    Write-Step "Java processes exist, but none looked like LearnFlow backend."
  }
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
        Proto = $parts[0]
        LocalAddress = $parts[1]
        ForeignAddress = $parts[2]
        State = $parts[3]
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
    return
  }

  foreach ($listener in $listeners) {
    try {
      $proc = Get-Process -Id $listener.Pid -ErrorAction Stop
    } catch {
      continue
    }

    if ($proc.ProcessName -in @('java', 'javaw')) {
      Write-Step "Stopping port owner on $Port (PID=$($proc.Id), Name=$($proc.ProcessName))"
      Stop-Process -Id $proc.Id -Force
      continue
    }

    throw "Port $Port is owned by PID=$($proc.Id) Name=$($proc.ProcessName). The script will not stop a non-Java process automatically."
  }
}

function Assert-PortFree {
  param([int]$Port)
  $listeners = @(Get-PortListeners -Port $Port)
  if (-not $listeners.Count) {
    Write-Step "Port $Port is free."
    return
  }

  Write-Host ""
  Write-Host "Port $Port is still in use:" -ForegroundColor Yellow
  $listeners | Format-Table -AutoSize
  Write-Host ""
  throw "Port $Port is occupied by another process. Stop it first, then run this script again."
}

if (-not (Test-Path $backendDir)) {
  throw "Backend directory not found: $backendDir"
}

Assert-NoActiveLock
Set-RunLock

Write-Step "Cleaning old LearnFlow backend processes..."
Stop-PortOwnerIfSafe -Port $targetPort
Stop-LearnFlowBackendProcesses

Start-Sleep -Seconds 1

Write-Step "Checking port $targetPort..."
Assert-PortFree -Port $targetPort

Push-Location $backendDir
try {
  if ($Clean) {
    Write-Step "Running mvn clean..."
    & mvn clean | Out-Host
  }

  Write-Step "Starting backend with mvn spring-boot:run..."
  & mvn spring-boot:run
} finally {
  Clear-RunLock
  Pop-Location
}
