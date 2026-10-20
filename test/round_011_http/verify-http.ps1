$ErrorActionPreference = 'Stop'
$baseUrl = 'http://127.0.0.1:8080'
$runtime = Join-Path $PSScriptRoot 'runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null

function Get-DemoSession {
    param([string]$Username)
    $body = @{username=$Username; password='Library123!'} | ConvertTo-Json
    $response = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -Body $body -ContentType 'application/json'
    if (-not $response.success) { throw 'Login failed.' }
    return @{Authorization=('Bearer ' + $response.data.token)}
}

function Test-LibraryHttp {
    $adminHeaders = Get-DemoSession 'superadmin'
    $studentHeaders = Get-DemoSession '2026001'
    $homeResponse = Invoke-WebRequest -Uri $baseUrl
    if ($homeResponse.StatusCode -ne 200 -or $homeResponse.Content -notmatch 'id="app"') { throw 'Production frontend is missing.' }
    foreach ($module in @('students','administrators','categories','books','copies','acquisitions','loans','fines','blacklist','resources','reservations','activities','registrations','announcements','visits')) {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/data/${module}?size=2" -Headers $adminHeaders
        if (-not $response.success -or $response.data.records.Count -gt 2) { throw ('Module failed: ' + $module) }
        Write-Output ($module + ': HTTP pagination passed')
    }
    foreach ($headers in @($adminHeaders,$studentHeaders)) {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/dashboard" -Headers $headers
        if (-not $response.success) { throw 'Dashboard failed.' }
    }
    $template = Join-Path $runtime 'student-import.xlsx'
    Invoke-WebRequest -Uri "$baseUrl/api/students/template" -Headers $adminHeaders -OutFile $template
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($template)
    try { if (-not $zip.GetEntry('xl/worksheets/sheet1.xml')) { throw 'Invalid Excel template.' } }
    finally { $zip.Dispose() }
    Write-Output 'Production HTML, two role dashboards and Excel template: passed'
}

Test-LibraryHttp
