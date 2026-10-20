. (Join-Path $PSScriptRoot 'common.ps1')

function Initialize-LibraryDatabase {
    $localConfig = Join-Path $script:ProjectRoot 'config.yaml'
    if (Test-Path -LiteralPath $localConfig) { throw 'config.yaml already exists. Existing local credentials are preserved.' }
    $mysql = (Get-Command mysql.exe -ErrorAction Stop).Source
    $rootPassword = Read-Host 'MySQL root password' -AsSecureString
    $temporaryPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootPassword)
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($temporaryPointer)
        $appPassword = 'Lj' + [guid]::NewGuid().ToString('N') + '!'
        $sql = "CREATE DATABASE IF NOT EXISTS linjun_library CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'linjun_app'@'localhost' IDENTIFIED BY '$appPassword'; GRANT ALL PRIVILEGES ON linjun_library.* TO 'linjun_app'@'localhost';"
        $sql | & $mysql -u root --default-character-set=utf8mb4
        if ($LASTEXITCODE -ne 0) { throw 'Database or user initialization failed.' }
        $tableCount = & $mysql -u root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='linjun_library'"
        if ($LASTEXITCODE -ne 0) { throw 'Database inspection failed.' }
        if ([int]$tableCount -eq 0) {
            Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $script:ProjectRoot 'env/sql/schema.sql') | & $mysql -u root --default-character-set=utf8mb4 linjun_library
            if ($LASTEXITCODE -ne 0) { throw 'Schema initialization failed.' }
        }
        $yaml = "spring:`n  datasource:`n    username: linjun_app`n    password: '$appPassword'`n"
        $env:MYSQL_PWD = $appPassword
        & $mysql -u linjun_app -N -e 'SELECT 1' linjun_library | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Existing linjun_app credentials differ. Fill config.yaml with its password or choose a fresh database/user manually.' }
        [System.IO.File]::WriteAllText($localConfig, $yaml, [System.Text.UTF8Encoding]::new($false))
        Write-Host 'Dedicated database, schema and ignored local config.yaml are ready.'
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($temporaryPointer)
        if ($null -eq $previousPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $previousPassword }
    }
}

Initialize-LibraryDatabase
