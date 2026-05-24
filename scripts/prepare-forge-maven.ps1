param(
    [Parameter(Mandatory = $true)]
    [string]$InstallToolsJar,

    [Parameter(Mandatory = $true)]
    [string]$MergeToolJar,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,

    [Parameter(Mandatory = $true)]
    [string]$DependencyClasspathFile,

    [Parameter(Mandatory = $true)]
    [string]$CompiledClassesDirectory
)

$ErrorActionPreference = "Stop"

$minecraftVersion = "1.20.1"
$versionManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
$javaExe = "java"
if ($env:JAVA_HOME -and $env:JAVA_HOME.Trim().Length -gt 0) {
    $candidateJavaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $candidateJavaExe) {
        $javaExe = $candidateJavaExe
    }
}

function Resolve-FullPath {
    param([string]$Path)

    $executionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Download-FileIfMissing {
    param(
        [string]$Url,
        [string]$Destination
    )

    if ((Test-Path $Destination) -and ((Get-Item $Destination).Length -gt 0)) {
        return
    }

    Write-Host "Downloading $Url"
    Write-Host " -> $Destination"

    try {
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
    } catch {
        throw "Download failed. Please download '$Url' and place it at '$Destination'. Original error: $($_.Exception.Message)"
    }
}

function Invoke-JavaTool {
    param([string[]]$Arguments)

    & $javaExe @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Java tool failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Convert-Tsrg2ForSpecialSource {
    param(
        [string]$InputPath,
        [string]$OutputPath
    )

    $converted = New-Object System.Collections.Generic.List[string]
    foreach ($line in Get-Content -Encoding UTF8 $InputPath) {
        if ($line -eq "tsrg2 left right") {
            continue
        }

        if ($line.StartsWith("`t`t")) {
            continue
        }

        if ($line.StartsWith("`t")) {
            $trimmed = $line.Trim()
            if ($trimmed -eq "static") {
                continue
            }

            $parts = $trimmed -split "\s+"
            if ($parts.Count -lt 3) {
                continue
            }

            if ($parts[1].StartsWith("(")) {
                $converted.Add("`t$($parts[0]) $($parts[1]) $($parts[2])")
            } else {
                $converted.Add("`t$($parts[0]) $($parts[2])")
            }
        } else {
            $converted.Add($line)
        }
    }

    Set-Content -Encoding ASCII -Path $OutputPath -Value $converted
}

$InstallToolsJar = Resolve-FullPath $InstallToolsJar
$MergeToolJar = Resolve-FullPath $MergeToolJar
$OutputDirectory = Resolve-FullPath $OutputDirectory
$DependencyClasspathFile = Resolve-FullPath $DependencyClasspathFile
$CompiledClassesDirectory = Resolve-FullPath $CompiledClassesDirectory

New-Item -ItemType Directory -Force $OutputDirectory | Out-Null

$downloadDirectory = $OutputDirectory
if ($env:USERPROFILE -and $env:USERPROFILE.Trim().Length -gt 0) {
    $downloadDirectory = Join-Path $env:USERPROFILE ".m2\repository\com\steve\ai\mine-mind-maven-cache\$minecraftVersion"
    New-Item -ItemType Directory -Force $downloadDirectory | Out-Null
}

$clientJar = Join-Path $downloadDirectory "client.jar"
$serverJar = Join-Path $downloadDirectory "server.jar"
$serverExtractedJar = Join-Path $OutputDirectory "server-extracted.jar"
$clientMappings = Join-Path $downloadDirectory "client.txt"
$serverMappings = Join-Path $downloadDirectory "server.txt"
$mergedObfuscatedJar = Join-Path $OutputDirectory "merged-obf.jar"
$mergedMappings = Join-Path $OutputDirectory "official.tsrg"
$specialSourceMappings = Join-Path $OutputDirectory "official-specialsource.tsrg"
$minecraftOfficialJar = Join-Path $OutputDirectory "minecraft-official.jar"
$javacArgs = Join-Path $OutputDirectory "javac.args"

if ((Test-Path $minecraftOfficialJar) -and ((Get-Item $minecraftOfficialJar).Length -gt 0)) {
    Write-Host "Minecraft official compile jar already exists: $minecraftOfficialJar"
    $dependencyClasspath = ""
    if (Test-Path $DependencyClasspathFile) {
        $dependencyClasspath = (Get-Content -Raw -Encoding UTF8 $DependencyClasspathFile).Trim()
    }
    $javacClasspath = @($CompiledClassesDirectory, $minecraftOfficialJar, $dependencyClasspath) |
        Where-Object { $_ -ne $null -and $_.Trim().Length -gt 0 } |
        ForEach-Object { $_.Trim().Replace('\', '/') }
    Set-Content -Encoding ASCII -Path $javacArgs -Value @("-classpath", "`"$($javacClasspath -join [IO.Path]::PathSeparator)`"")
    exit 0
}

try {
    $manifest = Invoke-RestMethod -Uri $versionManifestUrl
} catch {
    throw "Unable to download Minecraft version manifest from '$versionManifestUrl'. Original error: $($_.Exception.Message)"
}

$version = $manifest.versions | Where-Object { $_.id -eq $minecraftVersion } | Select-Object -First 1
if ($null -eq $version) {
    throw "Minecraft version '$minecraftVersion' was not found in $versionManifestUrl"
}

try {
    $versionInfo = Invoke-RestMethod -Uri $version.url
} catch {
    throw "Unable to download Minecraft $minecraftVersion metadata from '$($version.url)'. Original error: $($_.Exception.Message)"
}

Download-FileIfMissing $versionInfo.downloads.client.url $clientJar
Download-FileIfMissing $versionInfo.downloads.server.url $serverJar
Download-FileIfMissing $versionInfo.downloads.client_mappings.url $clientMappings
Download-FileIfMissing $versionInfo.downloads.server_mappings.url $serverMappings

if (-not ((Test-Path $serverExtractedJar) -and ((Get-Item $serverExtractedJar).Length -gt 0))) {
    Invoke-JavaTool @(
        "-jar", $InstallToolsJar,
        "--task", "BUNDLER_EXTRACT",
        "--input", $serverJar,
        "--output", $serverExtractedJar,
        "--jar-only"
    )
}

if (-not ((Test-Path $mergedObfuscatedJar) -and ((Get-Item $mergedObfuscatedJar).Length -gt 0))) {
    Invoke-JavaTool @(
        "-jar", $MergeToolJar,
        "--merge",
        "--client", $clientJar,
        "--server", $serverExtractedJar,
        "--output", $mergedObfuscatedJar
    )
}

if (-not ((Test-Path $mergedMappings) -and ((Get-Item $mergedMappings).Length -gt 0))) {
    Invoke-JavaTool @(
        "-jar", $InstallToolsJar,
        "--task", "MERGE_MAPPING",
        "--left", $clientMappings,
        "--right", $clientMappings,
        "--output", $mergedMappings,
        "--classes",
        "--fields",
        "--methods",
        "--params",
        "--reverse-left",
        "--reverse-right"
    )
}

Convert-Tsrg2ForSpecialSource $mergedMappings $specialSourceMappings

Invoke-JavaTool @(
    "-cp", $InstallToolsJar,
    "net.md_5.specialsource.SpecialSource",
    "-i", $mergedObfuscatedJar,
    "-o", $minecraftOfficialJar,
    "-m", $specialSourceMappings,
    "--kill-lvt",
    "--kill-generics",
    "--stable",
    "-q"
)

$dependencyClasspath = ""
if (Test-Path $DependencyClasspathFile) {
    $dependencyClasspath = (Get-Content -Raw -Encoding UTF8 $DependencyClasspathFile).Trim()
}
$javacClasspath = @($CompiledClassesDirectory, $minecraftOfficialJar, $dependencyClasspath) |
    Where-Object { $_ -ne $null -and $_.Trim().Length -gt 0 } |
    ForEach-Object { $_.Trim().Replace('\', '/') }
Set-Content -Encoding ASCII -Path $javacArgs -Value @("-classpath", "`"$($javacClasspath -join [IO.Path]::PathSeparator)`"")

Write-Host "Prepared Minecraft official compile jar: $minecraftOfficialJar"
Write-Host "Prepared javac argfile: $javacArgs"
