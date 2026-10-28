param(
    [Parameter(Mandatory = $true)][string]$MsiPath
)

# Retoques al MSI ya generado por jpackage que WiX no deja hacer desde main.wxs.
#
# Las pantallas finales de WixUI (terminado, cancelado, error) dejan "Atras" y "Cancelar"
# visibles pero siempre desactivados: dos botones grises que no hacen nada junto a
# "Finalizar". Aqui se ocultan (Attributes = 0: ni visibles ni activos).

$ErrorActionPreference = "Stop"

$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.GetType().InvokeMember("OpenDatabase", "InvokeMethod", $null, $installer, @($MsiPath, 1))

function Invoke-Sql([string]$Sql) {
    $view = $database.GetType().InvokeMember("OpenView", "InvokeMethod", $null, $database, @($Sql))
    [void]$view.GetType().InvokeMember("Execute", "InvokeMethod", $null, $view, $null)
    [void]$view.GetType().InvokeMember("Close", "InvokeMethod", $null, $view, $null)
}

foreach ($dialog in @("ExitDialog", "UserExit", "FatalError")) {
    Invoke-Sql "UPDATE ``Control`` SET ``Attributes`` = 0 WHERE ``Dialog_`` = '$dialog' AND (``Control`` = 'Back' OR ``Control`` = 'Cancel')"
}

[void]$database.GetType().InvokeMember("Commit", "InvokeMethod", $null, $database, $null)
[System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) | Out-Null
Write-Host "MSI retocado: $MsiPath"
