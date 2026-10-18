# ─────────────────────────────────────────────────────────────────────────────
# Respaldo de la base del servidor local de una tienda (modo híbrido). Lo corre
# la tarea programada "AURA Tienda - respaldo" que crea instalar.ps1; también se
# puede correr a mano como administrador.
#
# Deja un .backup (pg_dump formato custom) por corrida y borra los de más de
# -Dias días. Restaurar:
#   pgsql\bin\pg_restore.exe -h localhost -p 5433 -U postgres -d aura_tienda --clean <archivo>
# ─────────────────────────────────────────────────────────────────────────────
param(
    [string]$Carpeta = 'C:\AuraTienda',
    [int]$Dias = 14
)

$ErrorActionPreference = 'Stop'
$cfg = Get-Content (Join-Path $Carpeta 'config\tienda.json') -Raw | ConvertFrom-Json
$destino = Join-Path $Carpeta 'respaldos'
New-Item -ItemType Directory -Force $destino | Out-Null

$archivo = Join-Path $destino ("aura_tienda_" + (Get-Date -Format 'yyyyMMdd_HHmm') + ".backup")
$env:PGPASSWORD = $cfg.pgClave
& (Join-Path $Carpeta 'pgsql\bin\pg_dump.exe') -h localhost -p $cfg.pgPuerto -U postgres -d aura_tienda -Fc -f $archivo
if ($LASTEXITCODE -ne 0) {
    Remove-Item $archivo -Force -ErrorAction SilentlyContinue
    throw "pg_dump falló ($LASTEXITCODE)"
}

Get-ChildItem $destino -Filter 'aura_tienda_*.backup' |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$Dias) } |
    Remove-Item -Force
Write-Host "Respaldo: $archivo"
