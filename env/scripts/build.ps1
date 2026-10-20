. (Join-Path $PSScriptRoot 'common.ps1')

function Build-Library {
    Set-LibraryJava
    $maven = Get-LibraryMaven
    $round = Join-Path $script:ProjectRoot ('test/round_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_build')
    New-Item -ItemType Directory -Force -Path $round | Out-Null
    Push-Location (Join-Path $script:ProjectRoot 'front')
    try {
        & npm.cmd ci --cache (Join-Path $script:ProjectRoot 'work/npm-cache') --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
    $static = Join-Path $script:ProjectRoot 'back/src/main/resources/static'
    # Remove only the verified generated static directory to avoid stale hashed bundles.
    $resolvedStatic = [System.IO.Path]::GetFullPath($static)
    $expectedStatic = [System.IO.Path]::Combine($script:ProjectRoot, 'back', 'src', 'main', 'resources', 'static')
    if ($resolvedStatic -ne $expectedStatic) { throw 'Unexpected static output path.' }
    if (Test-Path -LiteralPath $static) { Remove-Item -LiteralPath $static -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $static | Out-Null
    Copy-Item -Path (Join-Path $script:ProjectRoot 'front/dist/*') -Destination $static -Recurse -Force
    Push-Location $script:ProjectRoot
    try {
        & $maven -B -ntp -f back/pom.xml clean package "-Dtest.report.dir=$round/results"
        if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed.' }
        'Frontend production build and backend tests passed.' | Set-Content -LiteralPath (Join-Path $round 'summary.txt') -Encoding UTF8
    } finally { Pop-Location }
    Write-Host 'Build complete. Run env/scripts/start.ps1 to open the application.'
}

Build-Library
