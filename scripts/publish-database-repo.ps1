param(
    [string]$RepositoryUrl = "https://github.com/XK-GamerX/SemencraftDatabase.git",
    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"

function Copy-DirectoryContents {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    Get-ChildItem -Path $Source -Force | ForEach-Object {
        $target = Join-Path $Destination $_.Name
        if ($_.PSIsContainer) {
            New-Item -ItemType Directory -Force -Path $target | Out-Null
            Copy-DirectoryContents -Source $_.FullName -Destination $target
            return
        }
        Copy-Item -Path $_.FullName -Destination $target -Force
    }
}

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$templateDir = Join-Path $projectRoot "database-repo"
if (-not (Test-Path $templateDir)) {
    throw "No existe la carpeta template database-repo."
}

$githubToken = Read-Host "GitHub token para publicar SemencraftDatabase"
$tempRoot = Join-Path $env:TEMP "SemencraftDatabase-publish"
if (Test-Path $tempRoot) {
    Remove-Item $tempRoot -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

try {
    git -c "http.extraHeader=Authorization: Bearer $githubToken" clone --branch $Branch $RepositoryUrl $tempRoot
}
catch {
    throw "No se pudo clonar el repo. Verifica URL, branch y token."
}

Get-ChildItem -Path $tempRoot -Force | Where-Object { $_.Name -ne ".git" } | Remove-Item -Recurse -Force
Copy-DirectoryContents -Source $templateDir -Destination $tempRoot

Push-Location $tempRoot
try {
    git add .
    git status --short
    $hasChanges = (git status --short)
    if ([string]::IsNullOrWhiteSpace($hasChanges)) {
        Write-Host "No hay cambios nuevos para publicar."
        return
    }

    git commit -m "Bootstrap Semencraft database and Discord bot"
    git -c "http.extraHeader=Authorization: Bearer $githubToken" push origin $Branch
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Base de datos publicada correctamente en:"
Write-Host $RepositoryUrl
