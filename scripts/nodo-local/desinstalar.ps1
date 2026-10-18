# ─────────────────────────────────────────────────────────────────────────────
# Quita AURA del servidor local de una tienda (lo que dejó instalar.ps1): los
# servicios, la regla del firewall y la tarea de respaldo. Como administrador.
#
# Por defecto CONSERVA la carpeta (base de datos, respaldos y llaves): si en la
# tienda quedaron ventas sin subir a la nube, no se pierden.
#   .\desinstalar.ps1               servicios fuera, datos intactos
#   .\desinstalar.ps1 -BorrarTodo   también borra C:\AuraTienda (pide confirmar)
# ─────────────────────────────────────────────────────────────────────────────
param(
    [string]$Carpeta = 'C:\AuraTienda',
    [switch]$BorrarTodo
)

$ErrorActionPreference = 'Stop'
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Corra este script como administrador.' }

function QuitarServicio($nombre, $exeWinSw) {
    $s = Get-Service $nombre -ErrorAction SilentlyContinue
    if (-not $s) { return }
    if ($s.Status -ne 'Stopped') { Stop-Service $nombre -Force; $s.WaitForStatus('Stopped', '00:01:00') }
    if ($exeWinSw -and (Test-Path $exeWinSw)) { & $exeWinSw uninstall | Out-Null } else { sc.exe delete $nombre | Out-Null }
    Write-Host "  servicio $nombre quitado"
}

QuitarServicio 'AuraWeb' (Join-Path $Carpeta 'nginx\AuraWeb.exe')
QuitarServicio 'AuraBackend' (Join-Path $Carpeta 'app\AuraBackend.exe')
$s = Get-Service 'AuraPostgres' -ErrorAction SilentlyContinue
if ($s) {
    if ($s.Status -ne 'Stopped') { Stop-Service 'AuraPostgres' -Force; $s.WaitForStatus('Stopped', '00:01:00') }
    & (Join-Path $Carpeta 'pgsql\bin\pg_ctl.exe') unregister -N AuraPostgres
    Write-Host '  servicio AuraPostgres quitado'
}

Get-NetFirewallRule -DisplayName 'AURA Tienda (web)' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
Unregister-ScheduledTask -TaskName 'AURA Tienda - respaldo' -Confirm:$false -ErrorAction SilentlyContinue
Write-Host '  firewall y tarea de respaldo quitados'

if ($BorrarTodo -and (Test-Path $Carpeta)) {
    $ok = Read-Host "Se borrará $Carpeta con la base de datos y los respaldos. Escriba BORRAR para confirmar"
    if ($ok -eq 'BORRAR') { Remove-Item $Carpeta -Recurse -Force; Write-Host "  $Carpeta borrada" }
    else { Write-Host '  no se borró nada' }
} else {
    Write-Host "Datos conservados en $Carpeta"
}
