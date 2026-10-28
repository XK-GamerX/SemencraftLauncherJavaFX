param()

$ErrorActionPreference = "Stop"

$botDir = Split-Path -Parent $PSCommandPath
$configFile = Join-Path $botDir "bot-config.json"

$supabaseUrl = Read-Host "Supabase URL"
$supabaseAnonKey = Read-Host "Supabase anon key"
$supabaseServiceRoleKey = Read-Host "Supabase service_role key"
$discordBotToken = Read-Host "Token del bot de Discord"
if ([string]::IsNullOrWhiteSpace($discordBotToken)) {
    throw "No se proporciono token del bot."
}

$config = @{
    supabaseUrl = $supabaseUrl
    supabaseAnonKey = $supabaseAnonKey
    supabaseServiceRoleKey = $supabaseServiceRoleKey
    guildId = "1333950171154878464"
    adminChannelId = "1333951378632216708"
    discordBotToken = $discordBotToken
    roleName = "Registrado"
    actionSyncIntervalSeconds = 5
    syncIntervalSeconds = 120
} | ConvertTo-Json

Set-Content -Path $configFile -Value $config -Encoding UTF8

Write-Host ""
Write-Host "Configuracion del bot generada en:"
Write-Host $configFile
