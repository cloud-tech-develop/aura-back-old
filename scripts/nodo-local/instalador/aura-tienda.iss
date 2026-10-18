; ─────────────────────────────────────────────────────────────────────────────
; Instalador del SERVIDOR LOCAL de una tienda (modo híbrido, docs/MODO_HIBRIDO.md).
;
; Envuelve el paquete que arma empaquetar.ps1: el asistente pide la URL de la
; nube y el token del nodo, y corre instalar.ps1 (servicios, base, firewall,
; respaldo). Volver a correrlo actualiza backend y front y conserva la base.
;
; Lo compila empaquetar.ps1 -Instalador. A mano:
;   ISCC.exe /DPaquete=<carpeta del paquete> /DAppVersion=2026.10.17 aura-tienda.iss
;
; Instalación desatendida (técnico con muchas tiendas):
;   AuraTienda-Setup.exe /VERYSILENT /NUBE=https://back-v0.cloudtecnology.cloud /TOKEN=<token>
; ─────────────────────────────────────────────────────────────────────────────

#ifndef Paquete
  #define Paquete "..\..\..\dist\aura-tienda"
#endif
#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#define Carpeta "C:\AuraTienda"
#define NubePorDefecto "https://back-v0.cloudtecnology.cloud"

[Setup]
AppId={{5E2B7C1A-3F4D-4E8B-9A6C-2D1E0F9B7A43}
AppName=AURA Tienda
AppVersion={#AppVersion}
AppVerName=AURA Tienda {#AppVersion}
AppPublisher=Cloud Technology
UninstallDisplayName=AURA Tienda (servidor local)
; La carpeta es fija: los scripts, los respaldos y el soporte la esperan ahí.
DefaultDirName={#Carpeta}
DisableDirPage=yes
DisableProgramGroupPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#Paquete}\..
OutputBaseFilename=AuraTienda-Setup-{#AppVersion}
; Casi todo ya viene comprimido (zip, jar): comprimir más solo demora.
Compression=lzma2/fast
SolidCompression=no
WizardStyle=modern
SetupLogging=yes
MinVersion=10.0

[Languages]
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[Files]
; El paquete va a una carpeta temporal: instalar.ps1 copia de ahí lo que queda.
Source: "{#Paquete}\*"; DestDir: "{tmp}\paquete"; Flags: recursesubdirs createallsubdirs ignoreversion
; Lo que se queda en la tienda para soporte y para desinstalar.
Source: "{#Paquete}\desinstalar.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#Paquete}\LEEME-TIENDA.txt"; DestDir: "{app}"; Flags: ignoreversion isreadme

[Run]
Filename: "{code:UrlLocal}"; Description: "Abrir AURA en este equipo"; Flags: postinstall shellexec nowait skipifsilent

[UninstallRun]
; Quita servicios, firewall y respaldo. La base y los respaldos se conservan.
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\desinstalar.ps1"" -Carpeta ""{app}"""; Flags: runhidden waituntilterminated; RunOnceId: "QuitarServicios"

[Code]
var
  PaginaNube: TInputQueryWizardPage;
  Resumen: TArrayOfString;

function ConfigArchivo: String;
begin
  Result := '{#Carpeta}\config\tienda.json';
end;

function EsActualizacion: Boolean;
begin
  Result := FileExists(ConfigArchivo);
end;

function LogInstalacion: String;
begin
  Result := '{#Carpeta}\logs\instalacion.log';
end;

{ Línea "CLAVE=valor" del resumen que deja instalar.ps1. }
function ValorResumen(Clave: String): String;
var
  I: Integer;
begin
  Result := '';
  for I := 0 to GetArrayLength(Resumen) - 1 do
    if Pos(Clave + '=', Resumen[I]) = 1 then
      Result := Copy(Resumen[I], Length(Clave) + 2, Length(Resumen[I]));
end;

function UrlLocal(Param: String): String;
begin
  Result := ValorResumen('URL_LOCAL');
  if Result = '' then Result := 'http://localhost';
end;

procedure InitializeWizard;
begin
  PaginaNube := CreateInputQueryPage(wpWelcome,
    'Conexión con la nube',
    'La central de AURA a la que esta tienda envía sus ventas.',
    'El token lo entrega el administrador al registrar la tienda en la nube; se muestra una sola vez.');
  PaginaNube.Add('URL de la nube:', False);
  PaginaNube.Add('Token de la tienda:', False);
  PaginaNube.Values[0] := ExpandConstant('{param:NUBE|{#NubePorDefecto}}');
  PaginaNube.Values[1] := ExpandConstant('{param:TOKEN|}');
  if EsActualizacion then
    PaginaNube.Description := 'AURA ya está instalado en este equipo: se actualiza y se conservan la base de datos y las ventas. ' +
      'Deje el token vacío para mantener el actual.';
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Url: String;
begin
  Result := True;
  if CurPageID = PaginaNube.ID then
  begin
    Url := Trim(PaginaNube.Values[0]);
    if (Pos('http://', Lowercase(Url)) <> 1) and (Pos('https://', Lowercase(Url)) <> 1) then
    begin
      MsgBox('La URL de la nube debe empezar por https:// (o http://).', mbError, MB_OK);
      Result := False;
    end
    else if (not EsActualizacion) and (Trim(PaginaNube.Values[1]) = '') then
    begin
      MsgBox('Falta el token de la tienda. Pídalo al administrador de AURA (se genera al registrar la tienda en la nube).',
        mbError, MB_OK);
      Result := False;
    end;
  end;
end;

{ Corre instalar.ps1 con lo que se escribió en el asistente. }
procedure Configurar;
var
  Parametros, Token: String;
  Codigo: Integer;
begin
  WizardForm.StatusLabel.Caption := 'Configurando base de datos y servicios (vea el avance en la ventana de consola)...';
  WizardForm.ProgressGauge.Style := npbstMarquee;
  ForceDirectories('{#Carpeta}\logs');

  Parametros := '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{tmp}\paquete\instalar.ps1') + '"' +
    ' -Carpeta "{#Carpeta}" -NubeUrl "' + Trim(PaginaNube.Values[0]) + '" -Log "' + LogInstalacion + '"';
  Token := Trim(PaginaNube.Values[1]);
  if Token <> '' then Parametros := Parametros + ' -Token "' + Token + '"';

  { Consola visible: el técnico ve el avance; instalar.ps1 guarda lo mismo en el log. }
  if not Exec('powershell.exe', Parametros, '', SW_SHOWNORMAL, ewWaitUntilTerminated, Codigo) then
    Codigo := -1;

  WizardForm.ProgressGauge.Style := npbstNormal;
  LoadStringsFromFile('{#Carpeta}\logs\resumen.txt', Resumen);

  if Codigo <> 0 then
  begin
    MsgBox('La configuración no terminó (código ' + IntToStr(Codigo) + ').' + #13#10#13#10 +
      'El detalle está en:' + #13#10 + LogInstalacion + #13#10#13#10 +
      'Corrija el problema y vuelva a correr este instalador: conserva lo que ya quedó.', mbError, MB_OK);
    ShellExec('open', 'notepad.exe', '"' + LogInstalacion + '"', '', SW_SHOW, ewNoWait, Codigo);
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then Configurar;
end;

procedure CurPageChanged(CurPageID: Integer);
var
  Texto, Estado: String;
begin
  if CurPageID = wpFinished then
  begin
    Estado := ValorResumen('CATALOGO');
    Texto := 'AURA quedó instalado como servicio de Windows y arranca solo con el equipo.' + #13#10#13#10 +
      'En cada caja, abra el navegador en:' + #13#10 + ValorResumen('URL_CAJAS') + #13#10#13#10;
    if Estado = 'OK' then
      Texto := Texto + 'Catálogo sincronizado con la nube: ya se puede iniciar sesión.'
    else
      Texto := Texto + 'ATENCIÓN: todavía no bajó el catálogo de la nube y sin él no se puede iniciar sesión. ' +
        'Revise internet, la URL y el token. ' + ValorResumen('ERROR');
    if ValorResumen('RED_PUBLICA') <> '' then
      Texto := Texto + #13#10#13#10 + 'La red de Windows está como Pública: las cajas no podrán entrar hasta cambiarla a Privada.';
    WizardForm.FinishedLabel.Caption := Texto;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    MsgBox('Se quitaron los servicios de AURA.' + #13#10#13#10 +
      'La base de datos y los respaldos se conservan en {#Carpeta} por si quedaron ventas sin subir a la nube. ' +
      'Bórrela a mano cuando esté seguro.', mbInformation, MB_OK);
end;
