param(
    [string]$Version = "1.4.4"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root "dist"
$workRoot = Join-Path $dist "work"
$work = Join-Path $workRoot "pasta-$Version"
$mavenPom = Join-Path $root "folia-phantom\pom.xml"

if (Test-Path $work) {
    Remove-Item -LiteralPath $work -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $dist, $workRoot, $work | Out-Null

mvn -f $mavenPom clean package

$guiTarget = Join-Path $root "folia-phantom\folia-phantom-gui\target"
$cliTarget = Join-Path $root "folia-phantom\folia-phantom-cli\target"
$guiJar = Join-Path $guiTarget "pasta-gui-$Version.jar"
$cliJar = Join-Path $cliTarget "pasta-cli-$Version.jar"
$guiLib = Join-Path $guiTarget "lib"
$serverTargets = @(
    @{ Id = "folia"; Name = "Folia"; Url = "https://github.com/PaperMC/Folia" },
    @{ Id = "shreddedpaper"; Name = "ShreddedPaper"; Url = "https://github.com/MultiPaper/ShreddedPaper" },
    @{ Id = "canvas"; Name = "Canvas"; Url = "https://github.com/CraftCanvasMC/Canvas" },
    @{ Id = "horizon"; Name = "Horizon"; Url = "https://github.com/CraftCanvasMC/Horizon" }
)

if (!(Test-Path $guiJar)) {
    throw "Missing GUI JAR: $guiJar"
}
if (!(Test-Path $cliJar)) {
    throw "Missing CLI JAR: $cliJar"
}

function New-CleanDir([string]$Path) {
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

function Copy-GuiPayload([string]$Path) {
    New-CleanDir $Path
    Copy-Item -LiteralPath $guiJar -Destination (Join-Path $Path "pasta-gui-$Version.jar")
    if (Test-Path $guiLib) {
        Copy-Item -LiteralPath $guiLib -Destination (Join-Path $Path "lib") -Recurse
    }
    Copy-Item -LiteralPath (Join-Path $root "README.md") -Destination $Path
    Copy-Item -LiteralPath (Join-Path $root "LICENSE") -Destination $Path
}

function Add-JavaFxNativeJars([string]$Path, [string]$Classifier) {
    $lib = Join-Path $Path "lib"
    New-Item -ItemType Directory -Force -Path $lib | Out-Null
    foreach ($module in @("javafx-base", "javafx-graphics", "javafx-controls", "javafx-fxml")) {
        mvn org.apache.maven.plugins:maven-dependency-plugin:3.7.1:copy "-Dartifact=org.openjfx:$module`:21.0.11:jar:$Classifier" "-DoutputDirectory=$lib" "-Dmdep.stripVersion=false" | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to copy JavaFX $Classifier artifact for $module"
        }
    }
}

function Copy-CliPayload([string]$Path) {
    New-CleanDir $Path
    Copy-Item -LiteralPath $cliJar -Destination (Join-Path $Path "pasta-cli-$Version.jar")
    Copy-Item -LiteralPath (Join-Path $root "README.md") -Destination $Path
    Copy-Item -LiteralPath (Join-Path $root "LICENSE") -Destination $Path
}

function Write-TextFile([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Write-ServerCompatibility([string]$Path, [hashtable]$Server) {
    $content = @"
pasta server compatibility build

Target server family: $($Server.Name)
Project URL: $($Server.Url)

This package uses the same pasta patching engine as the generic build. It is labeled for operators who are validating patched Bukkit/Paper plugins against $($Server.Name)-compatible server environments.

Recommended flow:
1. Patch only plugins you own, administer, or are licensed to modify.
2. Test patched output on a staging $($Server.Name) server before production.
3. Keep the generated audit metadata inside patched JARs for traceability.
"@
    Write-TextFile (Join-Path $Path "SERVER-COMPATIBILITY.txt") $content
}

function New-Zip([string]$Name) {
    $zip = Join-Path $dist "$Name.zip"
    if (Test-Path $zip) {
        Remove-Item -LiteralPath $zip -Force
    }
    Compress-Archive -Path (Join-Path $work $Name) -DestinationPath $zip
    Write-Host "Created $zip"
}

foreach ($server in $serverTargets) {
    $prefix = "pasta-$($server.Id)"
    $windowsGui = Join-Path $work "$prefix-windows-gui-$Version"
    $linuxGui = Join-Path $work "$prefix-linux-gui-$Version"
    $cli = Join-Path $work "$prefix-cli-$Version"

    Copy-GuiPayload $windowsGui
    Add-JavaFxNativeJars $windowsGui "win"
    Write-ServerCompatibility $windowsGui $server
    Write-TextFile (Join-Path $windowsGui "pasta-gui.bat") "@echo off`r`nset DIR=%~dp0`r`njava -jar ""%DIR%pasta-gui-$Version.jar"" %*`r`n"

    Copy-GuiPayload $linuxGui
    Get-ChildItem (Join-Path $linuxGui "lib") -Filter "*-win.jar" | Remove-Item -Force
    Add-JavaFxNativeJars $linuxGui "linux"
    Write-ServerCompatibility $linuxGui $server
    Write-TextFile (Join-Path $linuxGui "pasta-gui.sh") "#!/usr/bin/env sh`nDIR=`"`$(CDPATH= cd -- `"`$(dirname -- `"`$0`")`" && pwd)`"`njava -jar `"`$DIR/pasta-gui-$Version.jar`" `"`$@`"`n"

    Copy-CliPayload $cli
    Write-ServerCompatibility $cli $server
    Write-TextFile (Join-Path $cli "pasta-cli.bat") "@echo off`r`nset DIR=%~dp0`r`njava -jar ""%DIR%pasta-cli-$Version.jar"" %*`r`n"
    Write-TextFile (Join-Path $cli "pasta-cli.sh") "#!/usr/bin/env sh`nDIR=`"`$(CDPATH= cd -- `"`$(dirname -- `"`$0`")`" && pwd)`"`njava -jar `"`$DIR/pasta-cli-$Version.jar`" `"`$@`"`n"

    New-Zip "$prefix-windows-gui-$Version"
    New-Zip "$prefix-linux-gui-$Version"
    New-Zip "$prefix-cli-$Version"
}
