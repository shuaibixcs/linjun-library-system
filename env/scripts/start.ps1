. (Join-Path $PSScriptRoot 'common.ps1')

function Start-Library {
    Set-LibraryJava
    $jar = Join-Path $script:ProjectRoot 'back/target/library-system-1.0.0.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Please run env/scripts/build.ps1 first.' }
    if (-not (Test-Path -LiteralPath (Join-Path $script:ProjectRoot 'config.yaml'))) { throw 'Create config.yaml from env/config.example.yaml first.' }
    $portInUse = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
    if ($portInUse) { throw 'Port 8080 is in use. Stop the existing application before starting.' }
    $work = Join-Path $script:ProjectRoot 'work'
    New-Item -ItemType Directory -Force -Path $work | Out-Null
    $process = Start-Process -FilePath $script:JavaPath -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', ('"' + $jar + '"')) -WorkingDirectory $script:ProjectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $work 'server.log') -RedirectStandardError (Join-Path $work 'server-error.log') -PassThru
    $process.Id | Set-Content -LiteralPath (Join-Path $work 'backend.pid')
    for ($i=0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 1
        if ($process.HasExited) { throw 'Application exited. Check work/server.log and work/server-error.log.' }
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 2
            if ($health.success) { Write-Host 'Library ready: http://localhost:8080'; return }
        } catch { }
    }
    throw 'Startup is taking longer than expected. Check work/server.log.'
}

Start-Library
