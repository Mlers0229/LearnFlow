param(
  [switch]$CleanBackend
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontendDir = Join-Path $repoRoot 'frontend'
$agentDir = Join-Path $repoRoot 'agent-platform'
$backendScript = Join-Path $repoRoot 'start-backend.ps1'
$stopBackendScript = Join-Path $repoRoot 'stop-backend.ps1'
$agentPython = Join-Path $agentDir 'venv\Scripts\python.exe'
$runFile = Join-Path $repoRoot '.learnflow-dev-processes.json'

$frontendPort = 5173
$agentPort = 8000
$backendPort = 18081

function Write-Step {
  param([string]$Message)
  Write-Host "[LearnFlow] $Message" -ForegroundColor Cyan
}

function Assert-Path {
  param(
    [string]$Path,
    [string]$Label
  )
  if (-not (Test-Path $Path)) {
    throw "$Label not found: $Path"
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

    throw "Port $Port is owned by PID=$($proc.Id) Name=$($proc.ProcessName). Please stop it manually before starting all services."
  }
}

function Assert-PortFree {
  param([int]$Port)
  $listeners = @(Get-PortListeners -Port $Port)
  if ($listeners.Count) {
    throw "Port $Port is still in use. Please stop the process first."
  }
}

function Start-PowerShellTask {
  param(
    [string]$Label,
    [string]$WorkingDirectory,
    [string]$Command
  )

  $process = Start-Process `
    -FilePath "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" `
    -WorkingDirectory $WorkingDirectory `
    -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $Command) `
    -PassThru

  Write-Step "$Label started in a new PowerShell window (PID=$($process.Id))."
  return $process
}

Assert-Path -Path $frontendDir -Label 'Frontend directory'
Assert-Path -Path $agentDir -Label 'Agent directory'
Assert-Path -Path $backendScript -Label 'Backend start script'
Assert-Path -Path $stopBackendScript -Label 'Backend stop script'
Assert-Path -Path $agentPython -Label 'Agent Python executable'

Write-Step "Preparing ports..."
& $stopBackendScript
Stop-PortOwnerIfSafe -Port $frontendPort -AllowedProcessNames @('node', 'powershell', 'pwsh')
Stop-PortOwnerIfSafe -Port $agentPort -AllowedProcessNames @('python', 'powershell', 'pwsh')

Start-Sleep -Seconds 1

Assert-PortFree -Port $frontendPort
Assert-PortFree -Port $agentPort
Assert-PortFree -Port $backendPort

$backendCommand = if ($CleanBackend) {
  "& '$backendScript' -Clean"
} else {
  "& '$backendScript'"
}

$frontendCommand = "Set-Location '$frontendDir'; npm run dev -- --host 0.0.0.0 --strictPort"
$agentCommand = "Set-Location '$agentDir'; & '$agentPython' -m uvicorn app.main:app --host 0.0.0.0 --port $agentPort --reload"

Write-Step "Starting backend..."
$backendProcess = Start-PowerShellTask -Label 'Backend' -WorkingDirectory $repoRoot -Command $backendCommand

Start-Sleep -Seconds 1

Write-Step "Starting frontend..."
$frontendProcess = Start-PowerShellTask -Label 'Frontend' -WorkingDirectory $frontendDir -Command $frontendCommand

Write-Step "Starting agent-platform..."
$agentProcess = Start-PowerShellTask -Label 'Agent platform' -WorkingDirectory $agentDir -Command $agentCommand

$payload = [pscustomobject]@{
  startedAt = (Get-Date).ToString('s')
  backendWindowPid = $backendProcess.Id
  frontendWindowPid = $frontendProcess.Id
  agentWindowPid = $agentProcess.Id
  frontendPort = $frontendPort
  backendPort = $backendPort
  agentPort = $agentPort
} | ConvertTo-Json -Compress

[System.IO.File]::WriteAllText($runFile, $payload, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "LearnFlow dev stack started:" -ForegroundColor Green
Write-Host "  Frontend: http://localhost:$frontendPort" -ForegroundColor Green
Write-Host "  Backend : http://localhost:$backendPort" -ForegroundColor Green
Write-Host "  Agent   : http://localhost:$agentPort" -ForegroundColor Green
Write-Host ""
Write-Host "Use .\stop-all.ps1 to stop all three services." -ForegroundColor Yellow
