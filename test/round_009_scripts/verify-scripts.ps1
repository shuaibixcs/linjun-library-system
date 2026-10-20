$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$files = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'env/scripts') -Filter '*.ps1'
foreach ($file in $files) {
    $parseTokens = $null
    $parseErrors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$parseTokens, [ref]$parseErrors) | Out-Null
    if ($parseErrors.Count -gt 0) { throw ($file.Name + ': ' + ($parseErrors.Message -join '; ')) }
    Write-Output ($file.Name + ': syntax passed')
}
