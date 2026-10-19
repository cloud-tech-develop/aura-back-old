# Plan — Firma contable (un contador, varias empresas)

> Estado: **F0–F7 implementadas 2026-10-08 (local, sin commit). V199 sin correr.**
> Reemplaza la decisión del 2026-10-06 ("un login por empresa").
> Prerrequisito: Lote 4 de permisos (V190–V193) estable en producción — esto toca ese código.

## 1. Qué se decidió

| Tema | Decisión |
|---|---|
| Quién es el contador | Una **firma contable**: una empresa más de Aura, marcada como firma. Su usuario principal es el contador; puede tener auxiliares. |
| Quién la crea | Camilo, desde el panel de plataforma. |
| Cobro | La firma es **gratis**. Se cobra a cada empresa cliente. Los usuarios de la firma no cuentan como usuarios del cliente. |
| Cómo se vincula un cliente | Al crear (o editar) la empresa cliente en la plataforma se elige su firma contable. Un cliente tiene como máximo **una** firma activa. |
| Qué usuarios de la firma ven qué clientes | Los **administradores de la firma** (ADMIN/SUPER_ADMIN) atienden **todos** sus clientes sin asignación (ajuste 2026-10-08 tras la primera prueba). A los demás usuarios el administrador les elige qué clientes atienden. |
| Inicio de sesión | Si el usuario tiene más de una empresa disponible (su firma + clientes asignados), sale el modal "¿Dónde vas a trabajar?". Con una sola, entra directo. Arriba queda "Cambiar empresa". |
| Qué hace en el cliente | Perfil **"Contador externo"** creado en el cliente: contabilidad completa, facturas de compra, gastos, documento soporte, cartera/CxP, terceros y reportes. **Sin** facturas de venta, POS ni caja. |
| Facturas de venta | Solo si el administrador del cliente las activa en ese perfil (Perfiles, como cualquier otro). |
| Quién quita el acceso | El **administrador del cliente** y **Camilo** (plataforma). |

## 2. Modelo de datos (V199 + espejo Laravel `000199`, idempotente)

```sql
-- La empresa es una firma contable (gratis, puede atender clientes).
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS es_firma_contable BOOLEAN NOT NULL DEFAULT FALSE;

-- Firma → cliente. Un cliente tiene como máximo una firma activa.
CREATE TABLE IF NOT EXISTS empresa_firma_cliente (
    id                  BIGSERIAL PRIMARY KEY,
    firma_empresa_id    INT       NOT NULL REFERENCES empresa(id),
    cliente_empresa_id  INT       NOT NULL REFERENCES empresa(id),
    perfil_id           BIGINT    REFERENCES perfil(id),     -- "Contador externo" en el cliente
    activo              BOOLEAN   NOT NULL DEFAULT TRUE,
    creado_por          INT,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    revocado_por        INT,                                  -- usuario del cliente o PLATFORM_ADMIN
    revocado_at         TIMESTAMP,
    CHECK (firma_empresa_id <> cliente_empresa_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_firma_cliente_activo
    ON empresa_firma_cliente (cliente_empresa_id) WHERE activo;

-- Qué usuario de la firma atiende qué cliente (lo decide el admin de la firma).
CREATE TABLE IF NOT EXISTS usuario_firma_cliente (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  INT       NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    vinculo_id  BIGINT    NOT NULL REFERENCES empresa_firma_cliente(id) ON DELETE CASCADE,
    activo      BOOLEAN   NOT NULL DEFAULT TRUE,
    created_by  INT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_usuario_firma_cliente
    ON usuario_firma_cliente (usuario_id, vinculo_id);
```

### 2.1 F1 — hecho 2026-10-08 (local, sin commit, V199 sin correr)
- `V199__firma_contable.sql` + Laravel `2026_07_03_000199_firma_contable.php` (idempotente). Además de lo de arriba: índice por firma, índice por usuario, `updated_at` en la asignación.
- Entidades `EmpresaFirmaClienteEntity`, `UsuarioFirmaClienteEntity`; campo `EmpresaEntity.esFirmaContable`.
- JPA (solo findById/save/delete): `EmpresaFirmaClienteJPARepository`, `UsuarioFirmaClienteJPARepository`.
- `FirmaContableQueryRepository`: `clientesDelUsuario`, `acceso(usuario, cliente)`, `accesoVigente(usuario, vínculo)`, `firmas(texto)`, `vinculoActivoDelCliente`, `clientesDeLaFirma`, `vinculosDelUsuario`, `asignacionId`, `usuariosConAcceso`. "Acceso vigente" = vínculo activo + asignación activa + cliente activo + usuario activo **y de la firma**.
- ⚠ Con `ddl-auto=validate` el backend no arranca hasta correr V199.

- `usuario.empresa_id` **no cambia**: el usuario sigue siendo de la firma. Por eso el conteo de usuarios del cliente (`EmpresaPlataformaQueryRepository`) ya no los incluye, sin tocar nada.
- Si se quita el vínculo y luego se vuelve a dar, se crea una fila nueva (queda la historia).
- El perfil "Contador externo" es una fila normal de `perfil` en el cliente, sembrada con la plantilla de §1. El admin del cliente lo edita en Perfiles.

## 3. Autenticación y permisos

### 3.1 Login
`AuthServiceImpl.login` (hoy toma la empresa de `usuario.getEmpresa()`, l.187/211):
1. Valida usuario y clave como hoy.
2. Calcula las empresas disponibles: su empresa (la firma) + los clientes con `usuario_firma_cliente.activo` y `empresa_firma_cliente.activo` y el cliente activo.
3. Una sola → login de hoy, sin cambios.
4. Varias → responde `requiereEmpresa = true`, la lista (`id`, razón social, NIT, logo, si es su firma) y un **token de paso** (claim `scope = SELECCION_EMPRESA`, 2 minutos, sin `empresaId`). `JwtAuthenticationFilter` rechaza ese token en cualquier ruta que no sea `/api/auth/seleccionar-empresa`.

### 3.2 `POST /api/auth/seleccionar-empresa` `{ empresaId }`
Mismo patrón que `cambiar-sede` (`AuthServiceImpl.cambiarSede`, l.265). Lo usan el modal y "Cambiar empresa".
- Si es su propia empresa → token normal.
- Si es un cliente → revisa el vínculo y emite el JWT con:
  - `empresaId` = cliente
  - `rol` = `CONTADOR_EXTERNO` (**nunca** ADMIN: `exigirAdministrador()` de Usuarios/Empresa no debe dejarlo pasar)
  - `firmaId` = firma, `vinculoId` = vínculo
  - `sucursalId` = sede principal del cliente; alcance "todas las sedes" (el perfil decide)
- Anota en la bitácora del cliente: "Ingresó Juan Pérez (Firma XYZ)".

### 3.3 En cada petición
- `JwtAuthenticationFilter`: si el token trae `vinculoId`, comprueba que el vínculo y la asignación del usuario sigan activos (caché de 30 s, como `token_version`). Si no → 401 **solo en ese cliente**; sus otros clientes siguen funcionando.
- `PermisoUsuarioService`: la caché pasa de `usuarioId` a `(usuarioId, empresaId)`. Cuando la empresa del token no es la del usuario, el perfil sale de `empresa_firma_cliente.perfil_id`, no de `usuario.perfil_id`.
- `CustomUserDetailsService`: el rol de Spring sale del token, no de `usuario.rol`, cuando hay `vinculoId`.

### 3.4 F3 — hecho 2026-10-08 (backend, local, sin commit)
- `JwtTokenProvider`: `generateTokenCliente` (claims `vinculoId`, `firmaId`), `generateTokenSeleccion` (claim `scope=SELECCION_EMPRESA`, 2 min, sin empresa). `SecurityUtils.getVinculoId()/getFirmaId()`.
- `JwtAuthenticationFilter`: el token de paso solo entra a `/api/auth/seleccionar-empresa` (si no, 401 `ELEGIR_EMPRESA`); con `vinculoId` revisa `AccesoFirmaService.vigente` (caché 30 s; se limpia al quitar un acceso) → 401 `ACCESO_FIRMA_REVOCADO`; las autoridades de Spring salen del rol del token.
- `AuthServiceImpl`: login de un usuario de firma con clientes → `requiereEmpresa=true`, `empresas` (su firma primero) y token de paso. `POST /api/auth/seleccionar-empresa {empresaId}` (también para "Cambiar empresa"), `GET /api/auth/empresas`. En el cliente: rol `CONTADOR_EXTERNO`, sede principal del cliente, `firmaNombre`, bitácora "Ingresó X (Firma Y)". `cambiar-sede` dentro de un cliente: cualquier sede del cliente con el acceso vigente.
- `LoginResponseDto`: `requiereEmpresa`, `empresas`, `empresaId/Nombre/Nit`, `firmaNombre`, `puedeCambiarEmpresa`.
- `PermisoUsuarioService`: en el cliente, el perfil del vínculo (debe ser del cliente y activo), **sin** excepciones, especiales ni límites del usuario; todas las sedes.
- Pruebas: +3 en `PermisoUsuarioServiceTest` (perfil del vínculo y rol, excepciones de la firma no cuentan, perfil ajeno no da nada). 61/61 en permisos + firma.
- **El front (modal, cambiar empresa) es F6.** Hasta entonces, un usuario de firma con clientes asignados recibe `requiereEmpresa` y el front actual no lo sabe manejar; los demás usuarios no cambian.

## 4. Fuga de datos entre empresas (lo más delicado)

Todo lo que lea la empresa del **usuario en la base** en vez del **token** le mostraría al contador datos de su firma estando en un cliente. Hay que pasarlos a `SecurityUtils.getEmpresaId()`:

- `ErrorLogServiceImpl:63`, `UsuarioServiceImpl:320` (`usuario.getEmpresa()`)
- `PermisoUsuarioQueryRepository:26` (lee `empresa_id` de `usuario`)
- `UsuarioQueryRepository:44`, `ComisionQueryRepository:33` (`u.empresa_id`)
- Búsqueda completa: `getEmpresa()` sobre usuario, `u.empresa_id`, `findSuperAdminByEmpresaId`, procesos asíncronos y jobs (nómina async, alertas, notificaciones) que no tienen token.

**El tercero del usuario es de la firma.** Donde el sistema toma `usuario.tercero` para ponerlo en un documento (vendedor de la venta, responsable, comisiones), con el contador quedaría un tercero de otra empresa. Regla: si el token trae `vinculoId`, esos campos quedan vacíos o se piden en el formulario. Se revisa sobre todo en ventas (solo aplica si el admin le activa facturas de venta), compras, gastos y notas.

Prueba obligatoria: un usuario de la firma, estando en el cliente A, no ve nada del cliente B ni de su propia firma (listados, reportes, PDF, Excel, buscadores).

### 4.1 F0 — hecho 2026-10-08 (local, sin commit)

**Empresa del token, no del usuario**
- `PermisoUsuarioService`: caché por `usuarioId:empresaId` de la sesión; sesión en una empresa que no es la del usuario → **sin permisos** hasta F3. Fuera de una petición (jobs) usa la del usuario. `invalidarUsuario` borra todas sus empresas.
- `AuthServiceImpl.cambiarSede`: empresa del token; la sede tiene que ser de esa empresa aunque esté asignada al usuario.
- `ErrorLogService.registrarAsync(..., empresaId)`: la empresa la pasa `GlobalExceptionHandler` (leída en el hilo de la petición) y `PostingLogAdapter` (la del documento).
- Revisado y correcto: `UsuarioQueryRepository:44`, `ComisionQueryRepository:33`, `EmpresaPlataformaQueryRepository` (filtran por la empresa que reciben del token; los usuarios de la firma no aparecen en el cliente, que es lo esperado). `UsuarioServiceImpl.sucursalDeLaEmpresa` valida contra la empresa del usuario destino, que es de la sesión. Los jobs (`@Scheduled`, nómina async) reciben la empresa del documento, no del usuario.
- `usuario.tercero` solo se usa para mostrar el nombre (listados, PDF); no se copia a documentos del cliente salvo vendedor de pedidos/visitas/rutas, que el contador no tiene.

**Registros por ID de otra empresa (B-017)** — escaneo de `findById` en servicios y de SQL por `:id` sin empresa. Corregidos con `AislamientoEmpresa.es(...)` (responden "no encontrado"):
| Archivo | Qué permitía |
|---|---|
| `TurnoCajaServiceImpl` (2) | Abonar desde la caja a una CxC o CxP de otra empresa |
| `CarteraServiceImpl.actualizarCredito` | Cambiar el cupo de crédito de un cliente de otra empresa |
| `CuentaCobrarServiceImpl.crear` / `CuentaPagarServiceImpl.crear` | Crear una CxC/CxP contra un tercero de otra empresa |
| `RutaService` (2), `VisitaService` (3) | Usar locales o vendedores de otra empresa |
| `ComisionServiceImpl` (3) | Asignar como técnico a un usuario de otra empresa |

Revisados sin problema: borrados (`deleteById` precedidos de `findByIdAndEmpresaId`), `AcuerdoPagoQueryRepository.anular` (bloquea antes por empresa), soporte de asistencia, perfiles (filas internas), jobs y catálogos globales (municipios, módulos).

**Pruebas:** `PermisoUsuarioServiceTest` + 5 de aislamiento (otra empresa sin permisos, caché sin mezclar, invalidar todas, fuera de petición). 49/49 en `services/permisos`.

**Queda para F3:** el perfil del vínculo cuando la empresa del token no es la del usuario; rol `CONTADOR_EXTERNO`; filtro del vínculo por petición.

## 5. Pantallas

### Plataforma (Camilo)
- Crear/editar empresa: casilla **"Es firma contable"** y buscador **"Firma contable que la atiende"** (solo empresas marcadas como firma). Cambiar o quitar la firma desde editar.
- Ficha de la firma: lista de sus clientes, con fecha de alta y estado.

**F2 — hecho 2026-10-08 (local, sin commit):**
- Back: `FirmaContableService` (asignar/cambiar/quitar firma con bitácora en el cliente, marcar/desmarcar firma, perfil "Contador externo" con `PerfilesSistema.CONTADOR`: `principal.dashboard`, `contabilidad.*`, `compras.*`, `cuentas.*`, `cartera.*`, `tesoreria.*`, `reportes.*`, `terceros-y-sucursales.terceros`; ver/crear/editar/anular). `GET /api/platform/firmas-contables?texto=`, `PUT /api/platform/empresas/{id}/firma-contable {firmaEmpresaId|null}`; `esFirmaContable`/`firmaContableId` al crear; `esFirmaContable` al editar. Listado y detalle traen `esFirmaContable`, `firmaContableId/Nombre`, `totalClientesFirma`.
- Reglas: la firma debe estar marcada y activa; una firma no tiene firma; no se desmarca una firma con clientes; misma firma = nada; cambiar = desactiva el vínculo viejo (historia) y crea uno nuevo.
- Front (`features/super_admin`): sección "Firma contable" en crear/editar (tarjetas Empresa / Firma contable + buscador de firma), sub-renglón en el listado.
- Pruebas: `FirmaContableServiceTest` 9/9.

### Firma (su administrador)
- Usuarios → crear/editar usuario: selector múltiple **"Clientes que atiende"** (solo aparece si la empresa es firma). `GET /api/firma/clientes`, `PUT /api/usuarios/{id}/clientes`.
- Lista de clientes de la firma (nombre, NIT, usuarios asignados).

### Cliente (su administrador)
- Usuarios → sección **"Firma contable con acceso"**: la firma, los usuarios de la firma que entran y el botón **"Quitar acceso"** (con confirmación). Al quitarlo se cierran las sesiones de la firma **en ese cliente**.
- Volver a dar acceso: desde la plataforma (es un tema comercial).
- Perfiles: "Contador externo" aparece como un perfil más; ahí activa facturas de venta si quiere.

### Todos (front)
- Modal "¿Dónde vas a trabajar?" después del login: tarjetas con logo, nombre y NIT; la firma primero; buscador si son más de 8. **Responsivo** (una columna en teléfono).
- Barra superior: "Trabajando en: Cliente X" + "Cambiar empresa". Al cambiar se limpia todo lo guardado en el front (IndexDB, cachés, sede).
- Bitácora: el usuario se muestra como "Juan Pérez (Firma XYZ)".

## 6. Fases

| Fase | Qué | Días |
|---|---|---|
| F0 | Auditoría de §4: pasar todo a la empresa del token + prueba de aislamiento | 1 |
| F1 | V199 + Laravel 000199, entidades y QueryRepository (regla: JPA solo findById/save/delete) | 0,5 |
| F2 | Plataforma: marcar firma, asignar firma al crear/editar, sembrar perfil "Contador externo" | 1 |
| F3 | Auth: login con selección, `seleccionar-empresa`, claims, filtro del vínculo, permisos por (usuario, empresa) | 1,5 |
| F4 | Firma: asignar clientes a usuarios | 0,5 |
| F5 | Cliente: sección firma + quitar acceso | 0,5 |
| F6 | Front: modal, cambiar empresa, limpieza de estado, bitácora | 1 |
| F7 | Pruebas (§7) y manual /ayuda | 0,5 |
| | **Total** | **~6,5** |

## 6.1 F4–F7 — hecho 2026-10-08 (local, sin commit)
- **F4 (firma):** `GET /api/firma/clientes`, `GET|PUT /api/firma/usuarios/{id}/clientes {vinculoIds}` (solo ADMIN/SUPER_ADMIN de la firma, ruta mapeada a `caja.usuarios`). Quitar un cliente desactiva la asignación (la sesión en ese cliente se corta), volver a darlo reactiva la fila. Bitácora en la firma. Front: pestaña "Clientes que atiende" en la ficha del usuario (solo firma y solo admin).
- **F5 (cliente):** `GET /api/firma/acceso` (firma + usuarios que entran), `DELETE /api/firma/acceso` (quitar). Front: aviso "Firma contable con acceso" arriba de Caja → Usuarios con "Quitar acceso" (confirmación PrimeNG). Volver a dar acceso: plataforma.
- **F6 (front):** `shared/components/selector-empresa` ("¿Dónde vas a trabajar?", buscador con más de 8, responsivo); login con token de paso (2 min, mensaje si vence); barra superior con la empresa actual y "Cambiar empresa" (también en el menú del usuario); `auth.interceptor` respeta un `Authorization` ya puesto.
- **F7:** `JwtFirmaContableFilterTest` (4), `FirmaContableServiceTest` (12), `PermisoUsuarioServiceTest` (19). 68/68 en lo nuevo. Suite completa: los únicos fallos son preexistentes (comparado contra HEAD en un worktree: mismas 18 fallas en CxC/CxP/Devengo/FacturaLog; además `AuraPosApplicationTests`, `VentaGeneradorTest`, `FacturaServiceTest` que no tocan este código). Se ajustaron los mocks de CxC/CxP (tercero con empresa) por el filtro de F0. Manual /ayuda: tema "Firma contable" + Usuarios.

## 7. Pruebas de aceptación

1. Usuario normal de una empresa: el login no cambia, no ve modal.
2. Contador con su firma + 3 clientes: ve el modal con 4 opciones; entra al cliente A y solo ve datos de A.
3. Auxiliar con 1 cliente asignado y sin acceso a nada más de la firma: ve firma + 1 cliente (o entra directo si se decide ocultar la firma cuando no tiene permisos en ella).
4. En el cliente, el contador registra una factura de compra y una nota contable; no ve POS, caja ni facturas de venta.
5. El admin del cliente activa facturas de venta en "Contador externo" → el contador ya puede facturar (sin cerrar sesión, tras la caché de 5 min o invalidándola).
6. El admin del cliente quita el acceso → la sesión del contador en ese cliente recibe 401 en menos de 30 s; en los otros clientes sigue trabajando.
7. El contador no puede entrar a Usuarios del cliente ni cambiar claves (rol `CONTADOR_EXTERNO`).
8. La firma y sus usuarios no suman en el conteo de usuarios del cliente.
9. Bitácora del cliente muestra "(Firma XYZ)" en lo que hizo el contador.
10. Todo el flujo se ve bien en teléfono, tablet y PC.

## 8. Pendiente de definir (no bloquea empezar)
- Si un auxiliar no tiene ningún permiso en la firma, ¿se le muestra igual la firma en el modal? Propuesta: no.
- Tablero "Mis empresas" para la firma (pendientes de cada cliente: período abierto, IVA por declarar, conciliaciones, facturas rechazadas). Segunda etapa.

## 9. Segunda etapa — clientes gestionados y solicitudes (F8–F12 implementadas 2026-10-08, local, sin commit)

### 9.1 Decisiones
| Tema | Decisión |
|---|---|
| Cliente que no quiere Aura | **Cliente gestionado**: lo crea la **firma** desde su panel; la firma le paga a Aura por contrato aparte. |
| Cupo | **10** clientes gestionados por firma al inicio; la plataforma lo sube. |
| Módulos del gestionado | Los elige la firma, de una lista permitida: contabilidad, facturación de venta (facturas, notas, cotizaciones), compras (facturas, gastos, documento soporte), CxC/CxP, cartera, tesorería, terceros, catálogo de productos/servicios, reportes y **nómina**. **Nunca** POS, caja ni inventario. |
| Permisos en el gestionado | Los decide la firma. |
| Empresa que pide una firma | **Solicitud**: el administrador del cliente busca la firma por **NIT**, la firma recibe la notificación y acepta o rechaza. |
| Cliente que ya tiene firma | **Primero quita la actual** y luego solicita otra. |

### 9.2 Supuestos (confirmados 2026-10-08)
- **A. En un gestionado, los administradores de la firma entran como ADMIN del cliente** (nadie más lo administra): eligen módulos de la lista permitida, editan perfiles y el "Contador externo" de sus auxiliares. Los auxiliares entran con "Contador externo".
- **B. Facturación electrónica del gestionado**: la firma pone resolución, rangos y prefijo desde Mi perfil (V198); usuario, clave y credenciales de Factus las pone la plataforma por seguridad.
- **C. Usuario de consulta**: al crear el gestionado, la firma puede crear un usuario del cliente con el perfil "Consulta" (solo VER) para mirar el estado de su empresa.

### 9.3 Datos (V200 + Laravel 000200)
- `empresa_firma_cliente.modalidad` VARCHAR(20) NOT NULL DEFAULT 'CLIENTE_PAGA' (`CLIENTE_PAGA` | `GESTIONADO`).
- `empresa.cupo_gestionados` INT NOT NULL DEFAULT 10 (solo aplica a firmas).
- `solicitud_firma_cliente(id, cliente_empresa_id, firma_empresa_id, estado PENDIENTE|ACEPTADA|RECHAZADA|CANCELADA, solicitado_por, solicitado_at, respondido_por, respondido_at, motivo)` + índice único parcial: una PENDIENTE por cliente.

### 9.4 Fases
| Fase | Qué |
|---|---|
| F8 | V200; modalidad y cupo; reglas (gestionado = ADMIN para admins de la firma; lista permitida de módulos). |
| F9 | Firma: "Nuevo cliente gestionado" (datos, NIT, módulos permitidos), contador de cupo, lista de clientes con modalidad; editar módulos del gestionado. Sin usuario principal del cliente. |
| F10 | Solicitudes: cliente busca por NIT y solicita (exige no tener firma); firma ve la campana + lista y acepta/rechaza; aceptar crea el vínculo `CLIENTE_PAGA`; el cliente recibe la respuesta en su campana. |
| F11 | Plataforma: cupo por firma, columna de modalidad, reporte de gestionados por firma (para cobrar). |
| F12 | Pruebas, manual /ayuda. |

### 9.5 Hecho (F8–F12)
- **F8:** V200 + Laravel 000200 (corrida en local). `EmpresaFirmaClienteEntity.modalidad`, `EmpresaEntity.cupoGestionados`, `SolicitudFirmaClienteEntity`. `AccesoCliente.administraElCliente()` (gestionado + ADMIN/SUPER_ADMIN de la firma) → rol `ADMIN` en el token y perfil Administrador del cliente (`PermisoUsuarioService`, `AuthServiceImpl`).
- **F9:** `PaqueteGestionado` (CONTABILIDAD siempre, FACTURACION, COMPRAS, NOMINA + base; nunca POS/caja/inventario), `GestionadoService` (crear con cupo, NIT único, tercero de la empresa, módulos, arranque PUC, vínculo GESTIONADO, usuario de consulta con perfil CONSULTA; cambiar paquetes). `/api/firma/gestionados*`. Front: `/firma/clientes` (cupo, solicitudes, clientes, diálogo de módulos) y `/firma/clientes/nuevo`; "Clientes de la firma" en el menú de usuario (`esFirmaContable` en el login).
- **F10:** `/api/firma/buscar?nit=`, `/api/firma/solicitud` (GET/POST/DELETE), `/api/firma/solicitudes` + aceptar/rechazar. Campana: `SOLICITUDES_FIRMA` (firma) y `RESPUESTA_FIRMA` (cliente, 7 días). Front: bloque "Solicitar firma" en Caja → Usuarios.
- **F11:** plataforma: cupo editable, sub-renglón con gestionados/cupo y "Gestionada (la paga)". **Arreglado:** Create/Update de empresa ahora guardan facturación electrónica y credenciales de Factus (antes se perdían); el detalle las devuelve; un campo vacío no borra lo guardado; credenciales nuevas invalidan el token de Factus.
- **F12:** `GestionadoServiceTest` (7), `FirmaContableServiceTest` (18), `PermisoUsuarioServiceTest` (21), `JwtFirmaContableFilterTest` (4). Manual /ayuda: gestionados, solicitudes y errores.

## 10. Identidad y documentos electrónicos del gestionado (2026-10-19, local, sin commit)

Decisiones del usuario (2026-10-19):
- La firma maneja la identidad de sus gestionados: persona **natural o jurídica**, nombre, tipo de documento y DV. El NIT no cambia.
- Las credenciales de Factus (client id, secret, usuario y clave) y el `factus_prefijo` **solo los pone la plataforma**: Factus se los entrega a Aura.
- La firma **solicita** la activación electrónica (factura de venta, nómina electrónica, documento soporte); la solicitud llega a la **campana del panel de plataforma**; Aura pone las credenciales y la firma recibe el aviso.
- Resolución DIAN, rangos de factura y nómina, logo y contacto: el admin de la firma (ADMIN en el gestionado, perfil Administrador con `EDITAR_EMPRESA`) los edita en "Mi perfil" del cliente. Ya funcionaba.

Hecho:
- **V228** (+ Laravel 000228): `solicitud_activacion_electronica` (PENDIENTE/ATENDIDA/RECHAZADA/CANCELADA, una pendiente por cliente). **Sin correr** → con `ddl-auto=validate` el back no arranca hasta correrla.
- `IdentidadTributaria`: valida y escribe la identidad en la empresa (`tipo_persona`, que lee la exógena) y en su tercero (natural → nombres/apellidos y nombre1..apellido2; jurídica → razón social). DV vacío se calcula (`DocumentoSoporteArmador.digitoVerificacion`). La cédula sin RUT no lleva DV.
- `GestionadoService.crear` acepta tipo de persona; `identidad` / `cambiarIdentidad` (bitácora del cliente).
- `ActivacionElectronicaService`: estado, solicitar, cancelar; plataforma: pendientes, contar, atender, rechazar (motivo obligatorio). `atenderSiQuedoActiva` se llama al guardar la empresa en la plataforma: FE activa + las 4 credenciales → la pendiente queda ATENDIDA sola.
- API firma: `GET/PUT /api/firma/gestionados/{id}/identidad`, `GET /api/firma/gestionados/{id}/electronica`, `POST/DELETE .../electronica/solicitud`. API plataforma: `GET /api/platform/solicitudes-electronica`, `GET .../pendientes`, `POST .../{id}/atender`, `POST .../{id}/rechazar`.
- Campana de la firma: `ACTIVACION_ELECTRONICA` (atendida o rechazada, 7 días).
- Front: tipo de persona en "Nuevo cliente gestionado"; botones "Datos" y "Electrónica" por gestionado en `/firma/clientes`; plataforma: campana con contador (cada 60 s) y página `/platform/solicitudes-electronica` (Configurar credenciales → editar empresa, Marcar atendida, Rechazar). Manual /ayuda actualizado.
- Tests: `IdentidadTributariaTest` (3).

Pruebas pendientes (navegador): crear gestionado natural con CC y con NIT; pasar uno de jurídica a natural y revisar el tercero; pedir activación → campana de plataforma → guardar credenciales con FE activa → aviso en la firma; rechazar con motivo; generar exógena de un gestionado natural (no debe pedir 1010).
