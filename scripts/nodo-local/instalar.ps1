# ─────────────────────────────────────────────────────────────────────────────
# Instala (o actualiza) AURA en el SERVIDOR LOCAL de una tienda (modo híbrido,
# docs/MODO_HIBRIDO.md). Se corre en el equipo de la tienda, como administrador,
# desde la carpeta que armó empaquetar.ps1.
#
# Queda instalado en -Carpeta (C:\AuraTienda), todo como servicios de Windows:
#   AuraPostgres  PostgreSQL 17, solo escucha en este equipo (puerto -PuertoPg)
#   AuraBackend   backend en modo LOCAL, solo escucha en este equipo (9001)
#   AuraWeb       nginx: sirve el front y pasa /api al backend (puerto -PuertoWeb)
# más una tarea programada de respaldo diario de la base.
#
# Las cajas solo abren el navegador en http://<IP de este equipo>.
#
# Primera vez (con internet: baja el catálogo de la nube):
#   .\instalar.ps1 -NubeUrl https://back-v0.cloudtecnology.cloud -Token <token del nodo>
# Actualizar a una versión nueva (mismo comando, sin parámetros):
#   .\instalar.ps1
# Volver a correrlo conserva la base y las llaves; solo cambia backend, front y
# configuración.
# ─────────────────────────────────────────────────────────────────────────────
param(
    # URL de la nube, sin barra final. Obligatoria la primera vez.
    [string]$NubeUrl,
    # Token que mostró la nube al registrar el nodo (POST /api/sync/nodos).
    [string]$Token,
    [string]$Carpeta = 'C:\AuraTienda',
    [int]$PuertoWeb = 80,
    # 5433 para no chocar con un PostgreSQL que ya tenga el equipo.
    [int]$PuertoPg = 5433,
    # Solo lo usa nginx (no queda expuesto). 9091 para no chocar con otro
    # programa que ya use el 9001.
    [int]$PuertoBackend = 9091,
    [string]$MemoriaBackend = '1g',
    [string]$HoraRespaldo = '22:00',
    [int]$DiasRespaldo = 14,
    # Guarda todo lo que se ve en pantalla en este archivo (lo usa el instalador .exe).
    [string]$Log
)

$ErrorActionPreference = 'Stop'
$paquete = $PSScriptRoot
if ($Log) {
    New-Item -ItemType Directory -Force (Split-Path $Log -Parent) | Out-Null
    Start-Transcript -Path $Log -Force | Out-Null
}
# Si algo falla, que el error quede en pantalla y en el log, y salga con código 1.
trap {
    Write-Host "`nERROR: $($_.Exception.Message)" -ForegroundColor Red
    if ($Log) { Stop-Transcript | Out-Null }
    exit 1
}
$base = 'aura_tienda'

# SID en vez de nombres: en un Windows en español el grupo se llama "Administradores".
$SID_ADMIN = '*S-1-5-32-544'
$SID_SYSTEM = '*S-1-5-18'
$SID_NETWORK = '*S-1-5-20'

function Paso($texto) { Write-Host "`n== $texto" -ForegroundColor Cyan }
function Aviso($texto) { Write-Host "  ! $texto" -ForegroundColor Yellow }

function Aleatorio([int]$bytes) {
    $b = New-Object byte[] $bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    return $b
}

function ClaveAlfanumerica([int]$largo) {
    $abc = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    return -join ((Aleatorio $largo) | ForEach-Object { $abc[$_ % $abc.Length] })
}

function Ejecutar($exe, [string[]]$argumentos) {
    & $exe @argumentos
    if ($LASTEXITCODE -ne 0) { throw "$(Split-Path $exe -Leaf) terminó con código $LASTEXITCODE" }
}

function DetenerServicio($nombre) {
    $s = Get-Service $nombre -ErrorAction SilentlyContinue
    if ($s -and $s.Status -ne 'Stopped') {
        Write-Host "  deteniendo $nombre"
        Stop-Service $nombre -Force
        $s.WaitForStatus('Stopped', '00:01:00')
    }
}

# Descomprime un zip que trae una carpeta raíz (jdk-17..., pgsql, nginx-1.x) y la
# deja como $destino.
function DescomprimirEn($zip, $destino) {
    $tmp = Join-Path $env:TEMP ("aura-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Force $tmp | Out-Null
    # tar.exe (Windows 10/11) es mucho más rápido que Expand-Archive. Con ruta completa:
    # si Git está instalado, su tar (GNU) toma 'C:' como un servidor remoto.
    $tar = Join-Path $env:SystemRoot 'System32\tar.exe'
    if (Test-Path $tar) {
        & $tar -xf $zip -C $tmp
        if ($LASTEXITCODE -ne 0) { throw "No se pudo descomprimir $(Split-Path $zip -Leaf) (tar $LASTEXITCODE)" }
    } else {
        Expand-Archive $zip $tmp -Force
    }
    $raiz = Get-ChildItem $tmp | Select-Object -First 1
    if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }
    Move-Item $raiz.FullName $destino
    Remove-Item $tmp -Recurse -Force
}

function EscaparXml([string]$s) { return [Security.SecurityElement]::Escape($s) }

# ── 0. Verificaciones ────────────────────────────────────────────────────────
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Corra este script como administrador (clic derecho > Ejecutar como administrador).' }

foreach ($f in 'app\aura.jar', 'front\index.html', 'esquema\esquema.sql', 'esquema\preparar_nodo.sql',
               'descargas\postgresql-tienda.zip', 'descargas\jre.zip', 'descargas\nginx.zip', 'descargas\WinSW-x64.exe') {
    if (-not (Test-Path (Join-Path $paquete $f))) { throw "Falta $f en el paquete: vuelva a correr empaquetar.ps1" }
}

$configDir = Join-Path $Carpeta 'config'
$configArchivo = Join-Path $configDir 'tienda.json'
$cfg = $null
if (Test-Path $configArchivo) { $cfg = Get-Content $configArchivo -Raw | ConvertFrom-Json }
$primeraVez = $null -eq $cfg

if ($primeraVez -and (-not $NubeUrl -or -not $Token)) {
    throw 'Primera instalación: indique -NubeUrl y -Token (el token que mostró la nube al registrar el nodo).'
}

# El puerto web no puede estar tomado por otro programa (IIS, Skype, otro nginx...).
$ocupado = Get-NetTCPConnection -LocalPort $PuertoWeb -State Listen -ErrorAction SilentlyContinue |
    Where-Object { (Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName -ne 'nginx' }
if ($ocupado) {
    $proc = (Get-Process -Id $ocupado[0].OwningProcess -ErrorAction SilentlyContinue).ProcessName
    throw "El puerto $PuertoWeb ya lo usa '$proc'. Ciérrelo o instale con -PuertoWeb 8080."
}

Paso "Instalando en $Carpeta ($(if ($primeraVez) { 'primera vez' } else { 'actualización' }))"
New-Item -ItemType Directory -Force $Carpeta, $configDir, (Join-Path $Carpeta 'logs'),
    (Join-Path $Carpeta 'respaldos'), (Join-Path $Carpeta 'app') | Out-Null

# ── 1. Configuración y llaves (se generan una sola vez) ──────────────────────
if ($primeraVez) {
    $cfg = [pscustomobject]@{
        nubeUrl       = $NubeUrl.TrimEnd('/')
        token         = $Token
        pgPuerto      = $PuertoPg
        backendPuerto = $PuertoBackend
        pgClave       = (ClaveAlfanumerica 24)
        # Llaves propias de la tienda: no hace falta que sean las de la nube.
        jwtSecret     = [Convert]::ToBase64String((Aleatorio 64))
        aesKey        = [Convert]::ToBase64String((Aleatorio 32))
        instaladoEl   = (Get-Date -Format 's')
    }
} else {
    if ($NubeUrl) { $cfg.nubeUrl = $NubeUrl.TrimEnd('/') }
    if ($Token) { $cfg.token = $Token }
    $PuertoPg = [int]$cfg.pgPuerto
    # El puerto del backend se puede cambiar al actualizar (-PuertoBackend).
    if ($PSBoundParameters.ContainsKey('PuertoBackend') -or -not $cfg.backendPuerto) {
        $cfg | Add-Member -NotePropertyName backendPuerto -NotePropertyValue $PuertoBackend -Force
    }
    $PuertoBackend = [int]$cfg.backendPuerto
}
$cfg | ConvertTo-Json | Set-Content $configArchivo -Encoding UTF8
# Solo administradores y el sistema leen las llaves.
icacls $configDir /inheritance:r /grant:r "${SID_ADMIN}:(OI)(CI)F" "${SID_SYSTEM}:(OI)(CI)F" | Out-Null

# ── 2. Detener servicios para reemplazar archivos ────────────────────────────
DetenerServicio 'AuraWeb'
DetenerServicio 'AuraBackend'

# ── 3. Programas: Java, PostgreSQL, nginx ────────────────────────────────────
Paso 'Programas'
$jre = Join-Path $Carpeta 'jre'
if (-not (Test-Path (Join-Path $jre 'bin\java.exe'))) {
    Write-Host '  Java 17'
    DescomprimirEn (Join-Path $paquete 'descargas\jre.zip') $jre
}
$pg = Join-Path $Carpeta 'pgsql'
if (-not (Test-Path (Join-Path $pg 'bin\postgres.exe'))) {
    Write-Host '  PostgreSQL'
    # Sin pgAdmin ni StackBuilder: los quita empaquetar.ps1.
    DescomprimirEn (Join-Path $paquete 'descargas\postgresql-tienda.zip') $pg
}
$nginx = Join-Path $Carpeta 'nginx'
if (-not (Test-Path (Join-Path $nginx 'nginx.exe'))) {
    Write-Host '  nginx'
    DescomprimirEn (Join-Path $paquete 'descargas\nginx.zip') $nginx
}

# ── 4. Base de datos ─────────────────────────────────────────────────────────
Paso 'Base de datos'
$datos = Join-Path $Carpeta 'datos'
$psql = Join-Path $pg 'bin\psql.exe'
$env:PGPASSWORD = $cfg.pgClave
# Sin los NOTICE de cada secuencia movida.
$env:PGOPTIONS = '-c client_min_messages=warning'

if (-not (Test-Path (Join-Path $datos 'PG_VERSION'))) {
    Write-Host '  creando el clúster'
    $pwfile = Join-Path $env:TEMP ("aura-pw-" + [guid]::NewGuid())
    Set-Content $pwfile $cfg.pgClave -NoNewline -Encoding ASCII
    try {
        Ejecutar (Join-Path $pg 'bin\initdb.exe') @('-D', $datos, '-U', 'postgres', '-E', 'UTF8', '--locale=C',
            '-A', 'scram-sha-256', "--pwfile=$pwfile")
    } finally { Remove-Item $pwfile -Force }
    Add-Content (Join-Path $datos 'postgresql.conf') "`nport = $PuertoPg`nlisten_addresses = 'localhost'`n"
    # El servicio de PostgreSQL corre como NETWORK SERVICE.
    icacls $datos /grant "${SID_NETWORK}:(OI)(CI)F" | Out-Null
}
if (-not (Get-Service 'AuraPostgres' -ErrorAction SilentlyContinue)) {
    Ejecutar (Join-Path $pg 'bin\pg_ctl.exe') @('register', '-N', 'AuraPostgres', '-D', $datos, '-S', 'auto')
}
Start-Service 'AuraPostgres'

$listo = $false
for ($i = 0; $i -lt 30 -and -not $listo; $i++) {
    & (Join-Path $pg 'bin\pg_isready.exe') -h localhost -p $PuertoPg -q
    $listo = $LASTEXITCODE -eq 0
    if (-not $listo) { Start-Sleep -Seconds 2 }
}
if (-not $listo) { throw 'PostgreSQL no arrancó: revise el visor de eventos (servicio AuraPostgres).' }

$existe = & $psql -h localhost -p $PuertoPg -U postgres -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$base'"
if ($existe -ne '1') {
    Write-Host "  creando la base $base"
    Ejecutar $psql @('-h', 'localhost', '-p', "$PuertoPg", '-U', 'postgres', '-d', 'postgres', '-qc', "CREATE DATABASE $base")
    # -o NUL: el esquema imprime filas de set_config que no dicen nada.
    Ejecutar $psql @('-h', 'localhost', '-p', "$PuertoPg", '-U', 'postgres', '-d', $base, '-q', '-v', 'ON_ERROR_STOP=1',
        '-o', 'NUL', '-f', (Join-Path $paquete 'esquema\esquema.sql'))
    # Ids de la tienda desde 1.500.000.000: nunca chocan con los de la nube.
    Ejecutar $psql @('-h', 'localhost', '-p', "$PuertoPg", '-U', 'postgres', '-d', $base, '-q', '-v', 'ON_ERROR_STOP=1',
        '-f', (Join-Path $paquete 'esquema\preparar_nodo.sql'))
    Write-Host '  esquema creado; ids de la tienda desde 1.500.000.000'
} else {
    Write-Host "  la base $base ya existe: se conserva"
    Aviso 'Si esta versión trae migraciones nuevas, aplíquelas a la base antes de usarla.'
}

# ── 5. Backend y front ───────────────────────────────────────────────────────
Paso 'Backend y front'
Copy-Item (Join-Path $paquete 'app\aura.jar') (Join-Path $Carpeta 'app\aura.jar') -Force
$front = Join-Path $Carpeta 'front'
if (Test-Path $front) { Remove-Item $front -Recurse -Force }
Copy-Item (Join-Path $paquete 'front') $front -Recurse
Copy-Item (Join-Path $paquete 'respaldar.ps1') $Carpeta -Force
if (Test-Path (Join-Path $paquete 'VERSION.txt')) { Copy-Item (Join-Path $paquete 'VERSION.txt') $Carpeta -Force }

$winsw = Join-Path $paquete 'descargas\WinSW-x64.exe'

# Servicio del backend. El backend lee su configuración de variables de entorno.
$envs = [ordered]@{
    DB_URL                    = "jdbc:postgresql://localhost:$PuertoPg/$base"
    DB_USER                   = 'postgres'
    DB_PASSWORD               = $cfg.pgClave
    SERVER_PORT               = "$PuertoBackend"
    # Solo nginx habla con el backend: no queda expuesto en la red.
    SERVER_ADDRESS            = '127.0.0.1'
    FRONTEND_URL              = 'http://localhost'
    JWT_SECRET                = $cfg.jwtSecret
    AES_KEY                   = $cfg.aesKey
    FACTUS_API_BASE_URL       = 'https://api.factus.com.co'
    APP_SYNC_MODO             = 'LOCAL'
    APP_SYNC_NUBE_URL         = $cfg.nubeUrl
    APP_SYNC_NODO_TOKEN       = $cfg.token
    APP_SYNC_INTERVALO_MS     = '15000'
    APP_SYNC_CATALOGO_CADA_MS = '300000'
}
$envXml = ($envs.GetEnumerator() | ForEach-Object {
    "  <env name=""$($_.Key)"" value=""$(EscaparXml $_.Value)""/>" }) -join "`r`n"
$appDir = Join-Path $Carpeta 'app'
$backendXml = @"
<service>
  <id>AuraBackend</id>
  <name>AURA Backend (tienda)</name>
  <description>Backend de AURA en modo local: vende sin internet y sincroniza con la nube.</description>
  <executable>$(EscaparXml (Join-Path $jre 'bin\java.exe'))</executable>
  <arguments>-Xmx$MemoriaBackend -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Duser.timezone=America/Bogota -Dfile.encoding=UTF-8 -jar "$(EscaparXml (Join-Path $appDir 'aura.jar'))"</arguments>
  <workingdirectory>$(EscaparXml $appDir)</workingdirectory>
  <depend>AuraPostgres</depend>
  <startmode>Automatic</startmode>
  <onfailure action="restart" delay="10 sec"/>
  <logpath>$(EscaparXml (Join-Path $Carpeta 'logs'))</logpath>
  <log mode="roll-by-size"><sizeThreshold>10240</sizeThreshold><keepFiles>8</keepFiles></log>
$envXml
</service>
"@
Copy-Item $winsw (Join-Path $appDir 'AuraBackend.exe') -Force
Set-Content (Join-Path $appDir 'AuraBackend.xml') $backendXml -Encoding UTF8
# El XML lleva las llaves.
icacls (Join-Path $appDir 'AuraBackend.xml') /inheritance:r /grant:r "${SID_ADMIN}:F" "${SID_SYSTEM}:F" | Out-Null
if (-not (Get-Service 'AuraBackend' -ErrorAction SilentlyContinue)) {
    Ejecutar (Join-Path $appDir 'AuraBackend.exe') @('install')
}

# nginx: el front con rutas de Angular y /api hacia el backend.
$frontNginx = $front -replace '\\', '/'
$nginxConf = @"
worker_processes  1;
events { worker_connections  1024; }

http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile      on;
    keepalive_timeout  65;
    client_max_body_size 20m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    server {
        listen       $PuertoWeb;
        server_name  _;
        root         $frontNginx;

        # Rutas de Angular: cualquier ruta sin archivo devuelve index.html.
        location / {
            try_files `$uri `$uri/ /index.html;
        }
        # index.html sin caché, para que tome la versión nueva al actualizar.
        location = /index.html {
            add_header Cache-Control "no-cache";
        }

        location /api/ {
            proxy_pass         http://127.0.0.1:$PuertoBackend;
            proxy_http_version 1.1;
            proxy_set_header   Host `$host;
            # IP real de cada caja: el límite de intentos de login es por IP.
            proxy_set_header   X-Forwarded-For `$remote_addr;
            proxy_set_header   X-Forwarded-Proto `$scheme;
            proxy_read_timeout 300s;
        }
    }
}
"@
Set-Content (Join-Path $nginx 'conf\nginx.conf') $nginxConf -Encoding ASCII
$webXml = @"
<service>
  <id>AuraWeb</id>
  <name>AURA Web (tienda)</name>
  <description>nginx: sirve AURA a las cajas de la tienda.</description>
  <executable>$(EscaparXml (Join-Path $nginx 'nginx.exe'))</executable>
  <arguments>-p "$(EscaparXml $nginx)"</arguments>
  <stopexecutable>$(EscaparXml (Join-Path $nginx 'nginx.exe'))</stopexecutable>
  <stoparguments>-p "$(EscaparXml $nginx)" -s quit</stoparguments>
  <workingdirectory>$(EscaparXml $nginx)</workingdirectory>
  <depend>AuraBackend</depend>
  <startmode>Automatic</startmode>
  <onfailure action="restart" delay="10 sec"/>
  <logpath>$(EscaparXml (Join-Path $Carpeta 'logs'))</logpath>
</service>
"@
Copy-Item $winsw (Join-Path $nginx 'AuraWeb.exe') -Force
Set-Content (Join-Path $nginx 'AuraWeb.xml') $webXml -Encoding UTF8
if (-not (Get-Service 'AuraWeb' -ErrorAction SilentlyContinue)) {
    Ejecutar (Join-Path $nginx 'AuraWeb.exe') @('install')
}

# ── 6. Firewall ──────────────────────────────────────────────────────────────
Paso 'Firewall'
$regla = 'AURA Tienda (web)'
Get-NetFirewallRule -DisplayName $regla -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName $regla -Direction Inbound -Protocol TCP -LocalPort $PuertoWeb `
    -Action Allow -Profile Private, Domain | Out-Null
Write-Host "  puerto $PuertoWeb abierto en redes privadas y de dominio"
$publicas = Get-NetConnectionProfile | Where-Object NetworkCategory -eq 'Public'
foreach ($p in $publicas) {
    Aviso "La red '$($p.Name)' está como Pública: las cajas no podrán entrar. Cámbiela a Privada:"
    Aviso "  Set-NetConnectionProfile -InterfaceIndex $($p.InterfaceIndex) -NetworkCategory Private"
}

# ── 7. Respaldo diario ───────────────────────────────────────────────────────
Paso 'Respaldo diario'
$tarea = 'AURA Tienda - respaldo'
$accion = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $Carpeta 'respaldar.ps1')`" -Carpeta `"$Carpeta`" -Dias $DiasRespaldo"
$disparador = New-ScheduledTaskTrigger -Daily -At $HoraRespaldo
$ajustes = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopIfGoingOnBatteries -AllowStartIfOnBatteries
Register-ScheduledTask -TaskName $tarea -Action $accion -Trigger $disparador -Settings $ajustes `
    -User 'SYSTEM' -RunLevel Highest -Force | Out-Null
Write-Host "  todos los días a las $HoraRespaldo en $(Join-Path $Carpeta 'respaldos') (se guardan $DiasRespaldo días)"

# ── 8. Arrancar y esperar la primera sincronización ──────────────────────────
Paso 'Arrancando'
Start-Service 'AuraBackend'
Start-Service 'AuraWeb'

Write-Host '  esperando al backend (puede tardar un minuto)...'
$arriba = $false
for ($i = 0; $i -lt 60 -and -not $arriba; $i++) {
    try {
        Invoke-WebRequest "http://127.0.0.1:$PuertoWeb/api/sync/local/estado" -UseBasicParsing -TimeoutSec 5 | Out-Null
        $arriba = $true
    } catch {
        # 401/403 = el backend ya responde (el estado pide sesión).
        $codigo = $_.Exception.Response.StatusCode.value__
        if ($codigo -eq 401 -or $codigo -eq 403) { $arriba = $true } else { Start-Sleep -Seconds 3 }
    }
}
$catalogoOk = $false
$errorSync = ''
if (-not $arriba) {
    $errorSync = "El backend no respondió. Revise $(Join-Path $Carpeta 'logs\AuraBackend.out.log')"
    Aviso $errorSync
} else {
    Write-Host '  backend arriba; esperando la primera bajada del catálogo...'
    $bajada = $null
    for ($i = 0; $i -lt 40 -and -not $bajada; $i++) {
        $bajada = & $psql -h localhost -p $PuertoPg -U postgres -d $base -Atc "SELECT valor FROM sync_estado WHERE clave = 'ultima_bajada'"
        if (-not $bajada) { Start-Sleep -Seconds 3 }
    }
    if ($bajada) {
        $catalogoOk = $true
        Write-Host "  catálogo sincronizado ($bajada)" -ForegroundColor Green
    } else {
        $err = & $psql -h localhost -p $PuertoPg -U postgres -d $base -Atc "SELECT valor FROM sync_estado WHERE clave = 'ultimo_error'"
        Aviso 'Todavía no bajó el catálogo: sin él no se puede iniciar sesión. Revise internet, la URL y el token.'
        if ($err) { Aviso "Último error: $err"; $errorSync = "Último error: $err" }
    }
}

# ── 9. Resumen ───────────────────────────────────────────────────────────────
Paso 'Listo'
$sufijo = if ($PuertoWeb -eq 80) { '' } else { ":$PuertoWeb" }
# Solo adaptadores físicos conectados: fuera WSL, Hyper-V, VPN y similares.
$fisicos = Get-NetAdapter -Physical -ErrorAction SilentlyContinue | Where-Object Status -eq 'Up' |
    Select-Object -ExpandProperty ifIndex
$ips = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'WellKnown' -and
                   (-not $fisicos -or $fisicos -contains $_.InterfaceIndex) }
$urls = @($ips | ForEach-Object { "http://$($_.IPAddress)$sufijo" })
Write-Host 'Abra AURA en las cajas con:'
foreach ($ip in $ips) { Write-Host "    http://$($ip.IPAddress)$sufijo   ($($ip.InterfaceAlias))" -ForegroundColor Green }
Write-Host "En este equipo: http://localhost$sufijo"
Write-Host 'Deje la IP de este equipo fija (en el router o en Windows) para que no cambie.'

# Resumen para el instalador .exe (pantalla final).
@(
    "CATALOGO=$(if ($catalogoOk) { 'OK' } else { 'NO' })",
    "ERROR=$errorSync",
    "URL_LOCAL=http://localhost$sufijo",
    "URL_CAJAS=$($urls -join '  ')",
    "RED_PUBLICA=$(@($publicas | ForEach-Object Name) -join ', ')"
) | Set-Content (Join-Path $Carpeta 'logs\resumen.txt') -Encoding Default

if ($Log) { Stop-Transcript | Out-Null }
