. (Join-Path $PSScriptRoot 'common.ps1')

function Stop-Library {
    $pidFile = Join-Path $script:ProjectRoot 'work/backend.pid'
    if (-not (Test-Path -LiteralPath $pidFile)) { Write-Host 'No project process recorded.'; return }
    $libraryProcessId = [int](Get-Content -LiteralPath $pidFile)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$libraryProcessId"
    $expectedJar = Join-Path $script:ProjectRoot 'back/target/library-system-1.0.0.jar'
    if ($process -and $process.CommandLine.Contains($expectedJar)) {
        Stop-Process -Id $libraryProcessId
        Write-Host 'Library application stopped.'
    } elseif ($process) { throw 'Recorded process belongs to another application; left running.' }
    Remove-Item -LiteralPath $pidFile
}

Stop-Library
