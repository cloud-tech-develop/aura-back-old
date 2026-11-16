# Plan — Nube de documentos por empresa

Fecha: 2026-11-14 · Estado: **F0–F7 hechas en local, sin commit** (V236, V237 y `docs/sql/menu_nube_documentos.sql` corridos solo en local; falta prueba de punta a punta con usuarios reales). F8 queda para después. Submódulo: `principal.documentos`. Cuota y términos en `nube_espacio`.

**V237 (cambio de diseño):** `SOLO_FIRMA` ya no es "cualquier firma": la carpeta guarda `privada_firma_id` y solo esa firma la ve. Si el cliente cambia de contador, la firma nueva no ve los papeles de la anterior; la tarea horaria (`NubeScheduler`, minuto 20) los vuelve COMPARTIDA al terminar el vínculo. La purga de la papelera corre a las 03:30.

**Front:** `/documentos` (Principal → Documentos), `/platform/solicitudes-espacio`, campana (espacio 95 %/lleno y respuesta de la solicitud), tema "Documentos" en /ayuda.

## 1. Qué se construye

Cada empresa tiene su propio espacio de archivos ("Documentos"). Ahí suben archivos:

- los usuarios de la propia empresa, y
- la firma contable que la atiende o la creó (gestionados), mientras el vínculo esté vigente.

Ambos pueden crear carpetas. Cada archivo pesa **máximo 1 MB**.

Los archivos son **del cliente**, no de la firma. Si la firma pierde el vínculo, deja de verlos ese mismo día y los archivos se quedan con la empresa.

## 2. Lo que encontré en el código (y es un riesgo hoy)

| Hallazgo | Dónde | Riesgo |
|---|---|---|
| El bucket R2 actual es **público** (`r2.public-url = pub-….r2.dev`). Cualquiera con la URL descarga el archivo, sin iniciar sesión. | `application.properties`, `R2StorageService` | Hoy ya se guardan ahí soportes contables (`subirSoporte`), soportes de vinculación y PDFs de asistencia. Si una URL se filtra (correo reenviado, captura, historial del navegador), el archivo queda expuesto para siempre. |
| Las URLs públicas se guardan en la base. | Notas contables, vinculación, asistencia | Revocar el acceso no sirve: la URL sigue funcionando. |
| El tipo de archivo se valida con el `Content-Type` que manda el navegador. | `R2StorageService.subirSoporte` | Se puede falsificar: un `.html` o un ejecutable puede entrar como "application/pdf". |
| `r2.dev` es el dominio de pruebas de Cloudflare. | config | Tiene límite de tráfico y no es para producción. |

**Conclusión:** la nube **no** puede usar el bucket ni el servicio actuales. Va en un bucket privado nuevo. Recomiendo además migrar después los soportes sensibles (vinculación, notas contables) a ese bucket privado (F8).

## 3. Principios de seguridad (no negociables)

1. **Bucket privado.** Sin URL pública. Ningún archivo se puede abrir sin pasar por el backend.
2. **La empresa sale del token, siempre.** Nunca de un parámetro. Toda consulta de carpeta o archivo lleva `AND empresa_id = :empresaDelToken` (regla F0 de la firma; `AislamientoEmpresa`). Un ID de otra empresa responde **404**, no 403, para no confirmar que existe.
3. **Acceso de la firma = vínculo vigente.** Se apoya en el filtro existente (`FirmaContableQueryRepository.accesoVigente`). Quitar el vínculo corta el acceso a la nube en la siguiente petición.
4. **Permisos por perfil.** Ver, subir, crear carpetas y eliminar son permisos distintos.
5. **Descarga por el backend.** El backend revisa permisos, registra el evento y entrega el archivo (o una URL firmada de 60 segundos). En la base se guarda la *clave del objeto*, nunca una URL.
6. **Cifrado propio además del de Cloudflare.** Cada archivo se cifra con AES-256-GCM antes de subirlo, con una llave por empresa derivada de una llave maestra (`NUBE_MASTER_KEY`, solo en Dokploy). Si se filtran las credenciales de R2, lo que se descarga es ilegible. Con archivos de 1 MB el costo es despreciable.
7. **El tipo se detecta por el contenido**, no por la extensión ni el `Content-Type` (Apache Tika, firma de bytes).
8. **Todo queda registrado.** Subir, descargar, ver, renombrar, mover, eliminar y restaurar: quién, de qué empresa, si entró por la firma, IP, fecha. El registro no se edita ni se borra desde la app.
9. **Nada se borra al primer clic.** Eliminar manda a la papelera 30 días; luego se purga del bucket.

## 4. Reglas de negocio

| Regla | Valor propuesto |
|---|---|
| Tamaño máximo por archivo | 1 MB (1 048 576 bytes), validado en el backend |
| Cuota por empresa | 500 MB y 5 000 archivos (configurable desde plataforma) |
| Tipos permitidos | PDF, JPG, PNG, WebP, XLSX, XLS, CSV, DOCX, XML (facturas electrónicas), TXT |
| Tipos prohibidos | Ejecutables, scripts, HTML, SVG, archivos con macros (XLSM, DOCM), ZIP/RAR (no se pueden inspeccionar) |
| Profundidad de carpetas | Máximo 6 niveles |
| Nombre | Se limpia: sin `/ \ : * ? " < > |`, sin `..`, máximo 150 caracteres, sin doble extensión engañosa (`factura.pdf.exe`) |
| Nombres repetidos | No se permiten dos iguales en la misma carpeta (sin distinguir mayúsculas); el front propone "nombre (2).pdf" |
| Visibilidad de carpeta | `COMPARTIDA` (cliente y firma) o `SOLO_FIRMA` (papeles de trabajo del contador; el cliente no la ve). Solo la firma puede crear `SOLO_FIRMA` |
| Papelera | 30 días; restaurar o eliminar definitivo (solo admin) |
| Límite de subidas | 60 archivos por minuto por usuario |
| Vínculo terminado | La firma pierde acceso inmediato. Las carpetas `SOLO_FIRMA` pasan a `COMPARTIDA` para que el cliente las conserve (son información del cliente) |

## 5. Datos (V236 + Laravel 000236)

```sql
nube_carpeta (
  id bigserial PK, empresa_id int NOT NULL, padre_id bigint NULL REFERENCES nube_carpeta,
  nombre varchar(150) NOT NULL, visibilidad varchar(15) NOT NULL DEFAULT 'COMPARTIDA',
  creado_por int NOT NULL, creado_por_firma_id int NULL,
  created_at, updated_at, deleted_at, purgar_en
)
-- único: (empresa_id, coalesce(padre_id,0), lower(nombre)) WHERE deleted_at IS NULL

nube_archivo (
  id bigserial PK, empresa_id int NOT NULL, carpeta_id bigint NULL REFERENCES nube_carpeta,
  nombre varchar(150) NOT NULL, extension varchar(10) NOT NULL, mime varchar(100) NOT NULL,
  tamano_bytes int NOT NULL CHECK (tamano_bytes <= 1048576),
  sha256 char(64) NOT NULL, object_key varchar(120) NOT NULL UNIQUE,
  iv bytea NOT NULL, version_llave smallint NOT NULL,
  subido_por int NOT NULL, subido_por_firma_id int NULL,
  created_at, updated_at, deleted_at, purgar_en
)

nube_evento (            -- solo INSERT
  id bigserial PK, empresa_id int NOT NULL, usuario_id int NOT NULL, firma_empresa_id int NULL,
  accion varchar(20) NOT NULL, archivo_id bigint NULL, carpeta_id bigint NULL,
  detalle jsonb NULL, ip varchar(45), user_agent varchar(255), created_at
)
```

- `object_key = nube/{empresaId}/{uuid}` — no lleva el nombre del archivo.
- La cuota va en `empresa.configuracion` (ojo: se asigna como **String** JSON, ver gotcha).
- Idempotente (`IF NOT EXISTS`), y la migración Laravel espejo.

## 6. API (`/api/nube`)

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/carpetas?padreId=` | NUBE.VER |
| POST | `/carpetas` | NUBE.CARPETAS |
| PUT | `/carpetas/{id}` (renombrar/mover) | NUBE.CARPETAS |
| DELETE | `/carpetas/{id}` (a la papelera, con su contenido) | NUBE.ELIMINAR |
| GET | `/archivos?carpetaId=&texto=` | NUBE.VER |
| POST | `/archivos` (multipart, uno o varios) | NUBE.SUBIR |
| GET | `/archivos/{id}/descargar` | NUBE.VER |
| PUT | `/archivos/{id}` (renombrar/mover) | NUBE.SUBIR |
| DELETE | `/archivos/{id}` | NUBE.ELIMINAR |
| GET | `/papelera` · POST `/papelera/{tipo}/{id}/restaurar` | NUBE.ELIMINAR |
| GET | `/uso` (cuota usada) | NUBE.VER |
| GET | `/eventos?archivoId=&desde=&hasta=` | admin del cliente o de la firma |

Respuesta de descarga: `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox`, `Cache-Control: no-store`. La vista previa (PDF/imagen) se pinta en el front desde un `blob`, nunca abriendo una URL del bucket.

Estructura (reglas de código del equipo): `NubeController` → `NubeService` (interfaz) → `NubeServiceImpl`; `NubeQueryRepository` para listados; JPA solo `findById/save`; `NubeMapper`; `ArchivoPrivadoStorage` (put/get/delete cifrado) aparte de `R2StorageService`.

## 7. Fases

| Fase | Contenido | Esfuerzo |
|---|---|---|
| **F0** | Bucket privado `aura-nube` en R2 con token **solo de ese bucket** (no reutilizar las credenciales actuales). `ArchivoPrivadoStorage` con cifrado AES-GCM + llave por empresa (HKDF). Variables `R2_NUBE_*`, `NUBE_MASTER_KEY` en Dokploy. | 1 d |
| **F1** | V236 + Laravel 000236, entidades, JPA, `NubeQueryRepository`. | 0,5 d |
| **F2** | Servicio y API: carpetas, subida con validación (tamaño, Tika, nombre, cuota), descarga, renombrar/mover, papelera. Visibilidad `SOLO_FIRMA`. | 2 d |
| **F3** | Bitácora `nube_evento` en cada acción + endpoint de consulta. Límite de subidas por minuto. | 0,5 d |
| **F4** | Permisos: módulo `NUBE` y especiales; perfil Contador externo y admin de la firma con acceso; SQL del menú (`docs/sql/menu_nube.sql`). | 0,5 d |
| **F5** | Front (aura-frontend): pantalla Documentos — árbol de carpetas, lista, arrastrar y soltar, barra de cuota, vista previa, papelera, historial del archivo. Estándar `.page > .card`, responsive. | 2 d |
| **F6** | Tarea programada: purga de papelera vencida (borra del bucket y luego la fila); al terminar un vínculo, `SOLO_FIRMA` → `COMPARTIDA`. | 0,5 d |
| **F7** | Pruebas de seguridad (sección 8) + manual en /ayuda + texto de aceptación de uso. | 1 d |
| **F8** (después) | Migrar soportes sensibles actuales al bucket privado; antivirus ClamAV en un contenedor del VPS. | 1,5 d |

Total F0–F7: ~8 días.

## 8. Pruebas de aceptación de seguridad

1. Usuario de la empresa A pide un archivo de la empresa B por ID → 404, y queda el intento en el log.
2. Usuario de la firma con vínculo revocado → 403 en la siguiente petición, aunque tenga el token vigente.
3. Usuario del cliente no ve carpetas `SOLO_FIRMA` en listados, búsqueda, papelera ni uso.
4. Archivo de 1 048 577 bytes → rechazado. De 1 048 576 → aceptado.
5. `.exe` renombrado a `.pdf`, HTML con `Content-Type: application/pdf`, SVG, XLSM → rechazados.
6. Nombre `../../otra_empresa/x.pdf` → se limpia; la clave del objeto nunca depende del nombre.
7. La clave del objeto descargada directo del bucket sin credenciales → acceso denegado. Con credenciales → archivo cifrado ilegible.
8. Descarga con perfil sin `NUBE.VER` → 403.
9. Superar la cuota → mensaje claro, nada se sube a medias.
10. Cada descarga aparece en el historial con usuario, firma e IP.

## 9. Lo legal (para hablar con un abogado, no lo reemplaza)

- Con la Ley 1581 de 2012, la empresa cliente es el **responsable** de los datos que sube; Aura actúa como **encargado**. Eso debe quedar escrito en los términos del servicio o en un contrato de transmisión de datos, con lo que Aura se compromete a hacer (y a no hacer) con los archivos.
- La primera vez que alguien entra a Documentos, aceptar un texto corto: qué se puede subir, quién lo ve (la firma incluida) y por cuánto tiempo se guarda. Se guarda la aceptación con fecha y usuario.
- Política escrita de qué pasa con los archivos cuando la empresa deja de pagar o se da de baja (ejemplo: 90 días para descargarlos, luego se borran).
- Procedimiento ante incidentes: la bitácora (F3) es la evidencia de quién accedió a qué.
- Cloudflare R2 guarda en servidores fuera de Colombia: es transferencia internacional de datos y también debe estar en los términos.

## 10. Decisiones (2026-11-14, confirmadas por el usuario)

1. **Cuota: 500 MB por empresa.** Al llenarse **no se borra nada solo**: la subida se rechaza con el mensaje "Tu espacio está lleno. Elimina archivos o solicita más espacio (tiene costo)". Botón **Solicitar más espacio** → crea una solicitud que le llega a la plataforma (campana); la plataforma amplía la cuota y la empresa recibe el aviso. Al 80 % y al 95 % se muestra una advertencia.
2. **Carpetas `SOLO_FIRMA` desde la primera versión.**
3. **Aislamiento total entre clientes de una firma.** Cada empresa tiene su propio espacio:
   - La firma con 3 clientes ve el espacio de **un cliente a la vez**: el de la empresa seleccionada en el token (el selector de empresa que ya existe). No hay vista que mezcle clientes, ni búsqueda entre clientes, ni mover/copiar archivos de un cliente a otro.
   - Un cliente **no ve, ni le aparece**, nada de otro cliente de la misma firma: ni carpetas, ni nombres, ni conteos, ni eventos. Un ID de otro cliente responde 404.
   - La firma tiene además su propio espacio como empresa (sus documentos internos), igual de separado.
   - Prueba adicional de aceptación (11): usuario de la firma trabajando en el cliente A pide un archivo del cliente B (también suyo) por ID → 404; tiene que cambiar de empresa para verlo.
4. **ZIP/RAR no se permiten.**

### Datos adicionales por la decisión 1

```sql
nube_solicitud_espacio (
  id bigserial PK, empresa_id int NOT NULL, solicitado_por int NOT NULL,
  cuota_actual_mb int NOT NULL, cuota_solicitada_mb int NOT NULL,
  estado varchar(15) NOT NULL DEFAULT 'PENDIENTE',  -- PENDIENTE | APROBADA | RECHAZADA
  atendido_por int NULL, atendido_at timestamp NULL, observacion varchar(300) NULL,
  created_at
)
```

API: `POST /api/nube/solicitudes-espacio` (admin del cliente o de la firma) y en plataforma `GET/PUT /api/platform/nube/solicitudes-espacio`. Se suma a F4 (~0,5 d).
