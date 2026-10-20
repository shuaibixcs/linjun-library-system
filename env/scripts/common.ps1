$ErrorActionPreference = 'Stop'
$script:ProjectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))

function Get-LibraryMaven {
    $installed = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($installed) { return $installed.Source }
    $mavenHome = Join-Path $script:ProjectRoot 'env/tools/apache-maven-3.9.9'
    $mavenCommand = Join-Path $mavenHome 'bin/mvn.cmd'
    if (-not (Test-Path -LiteralPath $mavenCommand)) {
        $tools = Join-Path $script:ProjectRoot 'env/tools'
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $archive = Join-Path $tools 'maven.zip'
        Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile $archive
        Expand-Archive -LiteralPath $archive -DestinationPath $tools -Force
    }
    return $mavenCommand
}

function Set-LibraryJava {
    $javaCommand = Get-Command java.exe -ErrorAction Stop
    $script:JavaPath = $javaCommand.Source
    $env:JAVA_HOME = Split-Path (Split-Path $script:JavaPath -Parent) -Parent
    $version = (& $script:JavaPath -version 2>&1 | Out-String)
    if ($version -notmatch 'version "(\d+)') { throw 'Java version detection failed.' }
    if ([int]$Matches[1] -ne 21) { throw 'This coursework project is built and tested on JDK 21. Please select JDK 21 in PATH.' }
}
