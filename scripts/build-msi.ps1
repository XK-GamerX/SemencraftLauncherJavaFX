param(
    [string]$Version = "1.0.0"
)

$ErrorActionPreference = "Stop"

function Normalize-Version {
    param(
        [string]$Raw
    )

    $safeRaw = if ($null -eq $Raw) { "" } else { $Raw }
    $parts = [regex]::Matches($safeRaw, "\d+") | ForEach-Object { $_.Value }
    if ($parts.Count -eq 0) {
        return "1.0.0"
    }

    $major = $parts[0]
    $minor = if ($parts.Count -ge 2) { $parts[1] } else { "0" }
    $patch = if ($parts.Count -ge 3) { $parts[2] } else { "0" }
    return "$major.$minor.$patch"
}

function Assert-Command {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Hint
    )

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "No se encontro '$Name'. $Hint"
    }
}

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)

Assert-Command -Name "jpackage.exe" -Hint "Instala JDK 21+ y agrega su carpeta bin al PATH."
Assert-Command -Name "candle.exe" -Hint "Instala WiX Toolset v3 y agrega su carpeta bin al PATH."
Assert-Command -Name "light.exe" -Hint "Instala WiX Toolset v3 y agrega su carpeta bin al PATH."

$normalizedVersion = Normalize-Version -Raw $Version
if ($normalizedVersion -ne $Version) {
    Write-Host "Version normalizada: '$Version' -> '$normalizedVersion'"
}
$Version = $normalizedVersion

Push-Location $projectRoot
try {
    & ".\gradlew.bat" --no-daemon packageLauncherInstaller -PinstallerVersion=$Version
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle termino con codigo $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$msi = Get-ChildItem (Join-Path $projectRoot "build\installer\output\*.msi") -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $msi) {
    throw "No se encontro MSI en build\\installer\\output."
}

Write-Host ""
Write-Host "MSI generado:"
Write-Host $msi.FullName
Write-Host ""
Write-Host "Nota: Los modpacks se actualizan desde GitHub en runtime (no hace falta rebuild por cada cambio de mod)."
Write-Host ""
Write-Host "Credito del instalador: Khel Palacios"
