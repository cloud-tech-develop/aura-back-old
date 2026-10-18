# ─────────────────────────────────────────────────────────────────────────────
# Arma el paquete de instalación del SERVIDOR LOCAL de una tienda (modo híbrido,
# docs/MODO_HIBRIDO.md). Se corre en el equipo de desarrollo, con internet.
#
# El paquete trae todo lo que la tienda necesita, así la instalación no depende
# de internet (salvo la primera sincronización):
#   app\aura.jar         backend (mvn package)
#   front\               frontend compilado con la configuración "tienda" (apiUrl /api/)
#   esquema\esquema.sql  esquema de la base, sacado de una base recién migrada
#   esquema\preparar_nodo.sql
#   descargas\           PostgreSQL, Java 17 (JRE), nginx y WinSW, en zip
#   instalar.ps1, respaldar.ps1
#
# Uso (PowerShell):
#   .\scripts\nodo-local\empaquetar.ps1
#   .\scripts\nodo-local\empaquetar.ps1 -BaseReferencia aura_sync_nodo -SinFront
#
# -BaseReferencia es una base local con TODAS las migraciones de Laravel corridas
# (php artisan migrate sobre una base vacía). De ella sale el esquema: antes de
# empaquetar una versión nueva, migrarla.
# ─────────────────────────────────────────────────────────────────────────────
param(
    [string]$Front = 'D:\Proyectos Camilo\aura-post\aura-frontend',
    [string]$BaseReferencia = 'aura_sync_nodo',
    [string]$PgHost = 'localhost',
    [int]$PgPuerto = 5432,
    [string]$PgUsuario = 'postgres',
    [string]$Salida = (Join-Path $PSScriptRoot '..\..\dist\aura-tienda'),
    # Versiones de lo que se descarga. PostgreSQL debe ser 17 (igual que la nube).
    [string]$PgVersion = '17.6-1',
    [string]$NginxVersion = '1.26.3',
    [string]$WinSwVersion = '2.12.0',
    [switch]$SinBackend,
    [switch]$SinFront,
    # Además del paquete, arma el instalador .exe (requiere Inno Setup 6).
    [switch]$Instalador
)

$ErrorActionPreference = 'Stop'
# La barra de progreso de Invoke-WebRequest en PowerShell 5.1 hace la descarga
# varias veces más lenta.
$ProgressPreference = 'SilentlyContinue'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..\..')

function Paso($texto) { Write-Host "`n== $texto" -ForegroundColor Cyan }

function Descargar($url, $destino) {
    if (Test-Path $destino) { Write-Host "  ya está: $(Split-Path $destino -Leaf)"; return }
    Write-Host "  bajando $url"
    $tmp = "$destino.part"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing
    Move-Item $tmp $destino -Force
}

New-Item -ItemType Directory -Force $Salida | Out-Null
$Salida = (Resolve-Path $Salida).Path
foreach ($d in 'app', 'front', 'esquema', 'descargas') { New-Item -ItemType Directory -Force (Join-Path $Salida $d) | Out-Null }

# ── 1. Descargas (se reutilizan entre empaquetados) ──────────────────────────
Paso 'Descargas'
$desc = Join-Path $Salida 'descargas'
# Fuera del paquete: el zip completo de PostgreSQL y su copia descomprimida.
$cache = Join-Path (Split-Path $Salida -Parent) 'cache-empaquetar'
New-Item -ItemType Directory -Force $cache | Out-Null
$pgZipCompleto = Join-Path $cache 'postgresql.zip'
if ((Test-Path (Join-Path $desc 'postgresql.zip')) -and -not (Test-Path $pgZipCompleto)) {
    Move-Item (Join-Path $desc 'postgresql.zip') $pgZipCompleto   # paquetes armados antes de este cambio
}
Descargar "https://get.enterprisedb.com/postgresql/postgresql-$PgVersion-windows-x64-binaries.zip" $pgZipCompleto
Descargar 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse' (Join-Path $desc 'jre.zip')
Descargar "https://nginx.org/download/nginx-$NginxVersion.zip" (Join-Path $desc 'nginx.zip')
Descargar "https://github.com/winsw/winsw/releases/download/v$WinSwVersion/WinSW-x64.exe" (Join-Path $desc 'WinSW-x64.exe')

# tar.exe (Windows 10/11) descomprime mucho más rápido que Expand-Archive. Con ruta
# completa: si Git está instalado, su tar (GNU) toma 'D:' como un servidor remoto.
$tar = Join-Path $env:SystemRoot 'System32\tar.exe'
$pgLocal = Join-Path $cache 'pgsql'
if (-not (Test-Path (Join-Path $pgLocal 'bin\pg_dump.exe'))) {
    Write-Host '  descomprimiendo PostgreSQL'
    & $tar -xf $pgZipCompleto -C $cache
    if ($LASTEXITCODE -ne 0) { throw "tar falló al descomprimir PostgreSQL ($LASTEXITCODE)" }
}

# A la tienda va PostgreSQL sin pgAdmin, StackBuilder, documentación ni símbolos:
# ~20.000 archivos menos (de 315 MB a ~60 MB).
$pgTienda = Join-Path $desc 'postgresql-tienda.zip'
if (-not (Test-Path $pgTienda)) {
    Write-Host '  armando PostgreSQL para la tienda (sin pgAdmin)'
    & $tar -a -c -f $pgTienda -C $cache --exclude 'pgsql/pgAdmin 4' --exclude 'pgsql/StackBuilder' `
        --exclude 'pgsql/doc' --exclude 'pgsql/symbols' --exclude 'pgsql/include' pgsql
    if ($LASTEXITCODE -ne 0) { throw "tar falló al armar postgresql-tienda.zip ($LASTEXITCODE)" }
}

# ── 2. Esquema de la base ────────────────────────────────────────────────────
Paso "Esquema desde la base '$BaseReferencia'"
if (-not $env:PGPASSWORD) {
    $seguro = Read-Host "Clave de $PgUsuario en $PgHost`:$PgPuerto" -AsSecureString
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
}
$esquema = Join-Path $Salida 'esquema\esquema.sql'
& (Join-Path $pgLocal 'bin\pg_dump.exe') -h $PgHost -p $PgPuerto -U $PgUsuario -d $BaseReferencia `
    --schema-only --no-owner --no-privileges -f $esquema
if ($LASTEXITCODE -ne 0) { throw "pg_dump falló ($LASTEXITCODE)" }
Copy-Item (Join-Path $PSScriptRoot 'preparar_nodo.sql') (Join-Path $Salida 'esquema') -Force
Write-Host "  $esquema ($([math]::Round((Get-Item $esquema).Length / 1KB)) KB)"

# ── 3. Backend ───────────────────────────────────────────────────────────────
if (-not $SinBackend) {
    Paso 'Backend (mvn package)'
    Push-Location $repo
    try {
        # clean: el IDE puede dejar en target/classes clases compiladas con
        # errores ("Unresolved compilation problems") que package reutiliza.
        & mvn -q -B -DskipTests clean package
        if ($LASTEXITCODE -ne 0) { throw "mvn package falló ($LASTEXITCODE)" }
    } finally { Pop-Location }
    $jar = Get-ChildItem (Join-Path $repo 'target') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*.original' -and $_.Name -notlike '*-plain.jar' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    Copy-Item $jar.FullName (Join-Path $Salida 'app\aura.jar') -Force
    Write-Host "  $($jar.Name) -> app\aura.jar"
}

# ── 4. Frontend ──────────────────────────────────────────────────────────────
if (-not $SinFront) {
    Paso 'Frontend (ng build --configuration tienda)'
    Push-Location $Front
    try {
        & npx ng build --configuration tienda
        if ($LASTEXITCODE -ne 0) { throw "ng build falló ($LASTEXITCODE)" }
    } finally { Pop-Location }
    $dist = Join-Path $Front 'dist\aura-frontend\browser'
    Remove-Item (Join-Path $Salida 'front\*') -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item "$dist\*" (Join-Path $Salida 'front') -Recurse -Force
}

# ── 5. Scripts de la tienda ──────────────────────────────────────────────────
Paso 'Scripts'
foreach ($s in 'instalar.ps1', 'respaldar.ps1', 'desinstalar.ps1', 'LEEME-TIENDA.txt') {
    Copy-Item (Join-Path $PSScriptRoot $s) $Salida -Force
}
# Versión empaquetada, para saber qué hay instalado en cada tienda.
$commit = (& git -C $repo rev-parse --short HEAD) 2>$null
"$(Get-Date -Format 'yyyy-MM-dd HH:mm') backend $commit" | Set-Content (Join-Path $Salida 'VERSION.txt') -Encoding UTF8

# ── 6. Instalador .exe ───────────────────────────────────────────────────────
if ($Instalador) {
    Paso 'Instalador (Inno Setup)'
    $iscc = @("$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe", "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
              "$env:ProgramFiles\Inno Setup 6\ISCC.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $iscc) { throw 'No está Inno Setup 6: winget install --id JRSoftware.InnoSetup -e --scope user' }
    $version = Get-Date -Format 'yyyy.MM.dd.HHmm'
    & $iscc /Q "/DPaquete=$Salida" "/DAppVersion=$version" (Join-Path $PSScriptRoot 'instalador\aura-tienda.iss')
    if ($LASTEXITCODE -ne 0) { throw "ISCC falló ($LASTEXITCODE)" }
    Write-Host "  $(Join-Path (Split-Path $Salida -Parent) "AuraTienda-Setup-$version.exe")"
}

Paso 'Listo'
Write-Host "Paquete en: $Salida"
Write-Host 'Copie esa carpeta al servidor de la tienda y corra instalar.ps1 como administrador.'
