param(
    [switch]$AlsoWriteLocalRuntimeConfig
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$resourceDir = Join-Path $projectRoot "src\main\resources\com\semencraft\semencraftlauncherjavafx\private"
$resourceFile = Join-Path $resourceDir "database-private.json"

$githubToken = Read-Host "GitHub token para SemencraftDatabase"
$supabaseUrl = Read-Host "Supabase URL"
$supabaseAnonKey = Read-Host "Supabase anon key"
$supabaseServiceRoleKey = Read-Host "Supabase service_role key"

$json = @{
    githubToken = $githubToken
    supabaseUrl = $supabaseUrl
    supabaseAnonKey = $supabaseAnonKey
    supabaseServiceRoleKey = $supabaseServiceRoleKey
} | ConvertTo-Json

New-Item -ItemType Directory -Force -Path $resourceDir | Out-Null
Set-Content -Path $resourceFile -Value $json -Encoding UTF8

Write-Host ""
Write-Host "Archivo privado del launcher generado en:"
Write-Host $resourceFile

if ($AlsoWriteLocalRuntimeConfig) {
    $appData = [Environment]::GetFolderPath("ApplicationData")
    $localDir = Join-Path $appData ".semencraft\launcher"
    $localFile = Join-Path $localDir "database-private.json"
    New-Item -ItemType Directory -Force -Path $localDir | Out-Null
    Set-Content -Path $localFile -Value $json -Encoding UTF8
    Write-Host ""
    Write-Host "Tambien se escribio la configuracion runtime en:"
    Write-Host $localFile
}

Write-Host ""
Write-Host "Importante: este archivo queda fuera de git por .gitignore."
