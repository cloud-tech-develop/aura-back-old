# Plan: permisos por perfil (qué ve y qué puede hacer cada usuario)

> Estado: **P0–P4 IMPLEMENTADOS full-stack 2026-10-01; P5–P10 IMPLEMENTADOS full-stack 2026-10-02**
> (local, sin commit; V190+V191+V192 sin aplicar; V192 no se pudo correr en seco: no hay psql en el equipo).
> Va en un solo despliegue (decisión del usuario). Decisiones del usuario tomadas (abajo). Cada fase se sube en su propio domingo (ver `PLAN_DESPLIEGUE.md`).

## Objetivo

Al crear un usuario, decidir **qué módulos/submódulos ve** y **qué puede hacer en
cada uno** (ver, crear, editar, anular). Ejemplo: un administrador que no ve
Contabilidad, o un auxiliar que ve Empleados pero no puede guardar.

## Decisiones tomadas (2026-10-01)

1. **Perfil + excepciones.** El usuario recibe un perfil; si hace falta, se le
   quita o agrega un permiso puntual sin crear otro perfil.
2. **Acciones: VER, CREAR, EDITAR, ANULAR.** (ANULAR cubre eliminar.) Exportar y
   aprobar se pueden sumar después sin rediseñar.
3. **El rol fijo queda como "tipo de usuario".** `usuario.rol` sigue mandando en
   la lógica de negocio (el cajero necesita turno, el vendedor vende sin caja:
   23 usos en el back y 8 en el front). El perfil decide qué ve y qué puede hacer.

## Estado actual (verificado en el código el 2026-10-01)

| Punto | Hoy |
|---|---|
| Empresa | `modulos → submodulos` (2 niveles) + `empresa_modulo` / `empresa_submodulo` para lo que la empresa tiene activo. |
| Tercer nivel (RRHH: Gestión / Asistencia / Parámetros) | Solo existe en `sidebar.config.ts` (`subgroups`). No está en la base: la empresa no lo puede apagar; solo se filtra por rol. |
| Usuario | Un campo `usuario.rol` (6 valores en `PoliticaRoles`) + 30 listas `roles: [...]` en el menú (111 ítems). |
| Acciones | No existen. |
| Menú | Se filtra comparando `normalize(label)` con el código del submódulo (gotcha label↔submódulo: cambiar un texto rompe el permiso; labels repetidos comparten visibilidad). |
| Back | `@RequerirPermiso` + `PermisoInterceptor` existen pero **0 de 113 controllers** los usan, y solo validan la empresa. Ocultar el menú no protege: la API responde igual. |
| Endpoints | 333 GET, 275 POST, 96 PUT, 31 PATCH, 59 DELETE. Muchos POST son lecturas (`/page`, `/buscar`, reportes). |
| Alta de usuario | `POST /usuarios/create` y `POST /usuarios/create-from-empleado`; front `features/usuarios/form` y `nomina/empleados/crear-usuario`. |

## Modelo

```
Empresa (lo contratado)  ⊇  Perfil (lo del tipo de usuario)  ⊇  Usuario (perfil ± excepciones)
```

Permiso efectivo de un usuario sobre un submódulo y una acción =
`empresa lo tiene activo` **y** (`excepción del usuario` si existe, si no `lo da su perfil`).

Tablas nuevas:
- `submodulos.padre_id` (árbol de 3 niveles real) — RRHH → Gestión → Empleados.
- `perfil(id, empresa_id, nombre, descripcion, es_sistema, activo)`.
- `perfil_permiso(perfil_id, submodulo_id, ver, crear, editar, anular)`.
- `usuario.perfil_id`.
- `usuario_permiso(usuario_id, submodulo_id, ver, crear, editar, anular)` — solo las
  excepciones; cada columna admite NULL = "lo que diga el perfil".
- `permiso_ruta(prefijo, submodulo_id, accion_por_defecto?)` — qué URL del API
  pertenece a qué submódulo (para el interceptor).
- `permiso_cambio_log` — quién cambió qué permiso y cuándo.

## Fases

Migraciones desde la siguiente libre (hoy V190), cada una con su espejo Laravel.

### P0 · Base de datos y perfiles iniciales
- Tablas de arriba + `submodulos.padre_id`; cargar el tercer nivel de RRHH como submódulos hijos.
- **Perfiles de sistema calcados de los roles actuales**, por empresa:
  SUPER_ADMIN y ADMIN = todo lo que la empresa tiene; CAJERO y VENDEDOR = lo que hoy
  les deja ver el menú. Cada usuario existente queda con el perfil de su rol →
  **el día 1 nadie gana ni pierde acceso.**
- Servicio `PermisoUsuarioService.efectivos(usuarioId)` con caché por usuario
  (se invalida al cambiar perfil, excepciones o módulos de la empresa).
- Endpoint `GET /api/permisos/mios` → `{ "nomina.empleados": ["VER","CREAR"], ... }`.

### P1 · Pantalla de perfiles y alta de usuario
- Configuración › Perfiles: lista + formulario con el árbol de módulos (solo los
  que la empresa tiene) y columnas Ver/Crear/Editar/Anular; marcar un padre marca
  sus hijos; "Duplicar perfil".
- Form de usuario (los dos caminos de alta): elegir perfil; pestaña
  "Excepciones" con el mismo árbol mostrando lo heredado y permitiendo quitar/dar.
- Reglas: nadie se da más de lo que tiene; el SUPER_ADMIN de la empresa no puede
  perder "Usuarios y permisos" (evita quedar todos por fuera); los perfiles de
  sistema se pueden duplicar pero no borrar.

### P2 · Front obedece los permisos
- Cada ítem de `sidebar.config.ts` lleva `codigo` explícito; el menú filtra por
  `VER` con ese código (se acaba la comparación por label).
- Guard de ruta por código (si entra por URL sin permiso → mensaje y vuelta al inicio).
- Directiva `*puede="'nomina.empleados:EDITAR'"` para ocultar/desactivar botones
  Guardar, Editar, Anular. Se aplica pantalla por pantalla, empezando por las
  críticas (contabilidad, nómina, usuarios, compras).

### P3 · El back bloquea (la seguridad real)
- `PermisoInterceptor` ampliado: busca el submódulo por **prefijo de URL**
  (`permiso_ruta`) y la acción por método:
  `GET → VER`, `PUT/PATCH → EDITAR`, `DELETE → ANULAR`, `POST → CREAR`
  **salvo** rutas de lectura (`/page`, `/buscar`, `/search`, `/reporte*`, `/export*`,
  `/preview*` → VER) y `/anular*` → ANULAR.
- `@RequerirPermiso(submodulo, accion)` solo para excepciones que la regla no acierte.
- **Primero en modo observar** (un domingo): no bloquea, solo registra lo que
  habría bloqueado. Se revisa el registro una semana, se ajustan perfiles y rutas,
  y el domingo siguiente se activa el bloqueo.
- Fuera del interceptor: `/api/public/**`, login, y lo que el POS necesita para vender
  según el tipo de usuario.

### P4 · Cierre
- Auditoría de cambios de permisos (pantalla sobre `permiso_cambio_log`).
- Revisar `docs/sql/menu_submodulo*.sql`: los submódulos nuevos ya nacen con código y padre.
- Manual `/ayuda`: tema "Usuarios, perfiles y permisos".

## Segunda etapa: P5–P10 (decidida 2026-10-02)

Lo que una empresa mediana pide además de "qué ve y qué puede hacer". Todo en **V192**
(espejo Laravel `000192`). Regla del día 1 igual que en P0: **nadie gana ni pierde nada al
subir** (límites vacíos = sin límite, todas las sedes = sí, acciones especiales heredadas).

Decisiones del usuario (2026-10-02):
1. Descuento o rebaja de precio por encima del límite → **pide autorización en el momento**
   (un supervisor pone su usuario y PIN/clave); la venta sigue y queda en la bitácora.
2. Bitácora = **acciones sensibles** con antes/después, no todo lo que escribe.
3. Alcance por sede = **solo sus sedes**; el perfil tiene "Todas las sedes".
4. Sesión = **revocar + bloquear** (sin refresh token).

### P5 · Pendientes chicos
- Submódulo `tesoreria.obligaciones` (Obligaciones financieras) con código en el menú.
- `*appPuede` en los botones que no siguen el patrón (Aprobar, Contabilizar, Reabrir…).

### P6 · Acciones especiales
- Catálogo `permiso_accion_especial (submodulo, codigo, nombre, hereda_de)`; la clave es
  `modulo.submodulo:CODIGO` (p. ej. `contabilidad.periodos-contables:REABRIR`).
- `perfil_accion_especial` y `usuario_accion_especial` (excepción, NULL = lo del perfil).
- Sin fila explícita, la acción **hereda** de una acción base (`hereda_de` = EDITAR, CREAR…):
  el día 1 quien podía, sigue pudiendo. `hereda_de` NULL = apagada salvo acceso total.
- Back: `@RequerirPermiso(accion = "REABRIR")` en el endpoint; el interceptor la revisa
  como especial. En servicios: `PermisoUsuarioService.puedeEspecial(usuario, clave)`.
- Front: `*appPuede="'contabilidad.periodos-contables:REABRIR'"` y columna "Especiales" en la matriz.

### P7 · Bitácora de auditoría
- Tabla `auditoria_evento` (quién, cuándo, qué, sobre qué documento, antes/después, quién autorizó, IP).
- **Automática** desde el interceptor: toda petición EDITAR / ANULAR / especial que termina bien
  (ruta, documento, usuario). **Explícita** con antes/después donde importa: autorizaciones,
  precio y costo de producto, usuarios (activar, clave, perfil, sedes), reapertura de período.
- Pantalla Caja › Bitácora (`caja.bitacora`), filtros por fecha, usuario, módulo y documento.

### P8 · Límites de descuento y precio con autorización
- `perfil.descuento_max_pct` y `perfil.rebaja_precio_max_pct` (NULL = sin límite); el usuario
  puede tener los suyos (excepción).
- Rebaja de precio = precio de la línea contra el **menor precio legítimo** del producto
  (precio 1–3, presentación, listas, precio cliente/volumen), sin IVA. Las líneas con regla de
  descuento automática no cuentan contra el límite.
- Por encima del límite: el front pide autorización → `POST /api/autorizaciones` (usuario +
  PIN o clave de quien tenga `ventas.ventas:AUTORIZAR_DESCUENTO` y límite suficiente) →
  `autorizacionId` de un solo uso, 10 minutos, en la venta. El back lo vuelve a validar.
- Vender a crédito pasa a ser acción especial `ventas.ventas:VENDER_A_CREDITO` (hereda de CREAR).

### P9 · Alcance por sede
- `perfil.todas_sedes` (por defecto sí). Sin ella, el usuario solo opera y ve sus sedes
  (`usuario_sucursal`).
- Genérico: un `sucursalId` en la URL o en el cuerpo que no sea suyo → 403; vacío → su sede.
- `POST /api/auth/cambiar-sede`: el selector de sede de la barra superior hoy no hace nada
  (el token se queda con la sede del login); con esto emite un token nuevo con la sede elegida.

### P10 · Sesión
- `usuario.token_version` en el token: desactivar, cambiar la clave, cambiar las sedes o
  "Cerrar sesiones" lo sube y las sesiones abiertas caen con 401.
- 5 intentos fallidos → bloqueo 15 minutos (`intentos_fallidos`, `bloqueado_hasta`).
- Token de 12 h por defecto (`JWT_EXPIRATION_MS`).

## Pruebas clave
- Usuario "Admin sin contabilidad": no ve el grupo, no entra por URL, y
  `GET /api/contabilidad/asientos` responde 403 (con P3 activo).
- Auxiliar RRHH con Empleados en solo VER: ve la lista, no ve "Guardar", y un
  `PUT /api/empleados/{id}` responde 403.
- Excepción: perfil sin Reportes + excepción "dar VER en reportes de ventas" → solo ese aparece.
- La empresa apaga un módulo → desaparece para todos aunque el perfil lo tenga.
- Día 1 tras P0: cada usuario existente ve exactamente lo mismo que antes.

## Chequeo antes de subir P0 a producción (solo lectura)
Pantallas de RRHH apagadas a propósito en empresas con RRHH activo. Hoy se ven igual
(el menú de RRHH no consulta la base); con P2 dejarían de verse. En local: 0 filas.
```sql
SELECT es.empresa_id, s.codigo FROM empresa_submodulo es
JOIN submodulos s ON s.id = es.submodulo_id
JOIN modulos m ON m.id = s.modulo_id AND m.codigo = 'recursos-humanos'
JOIN empresa_modulo em ON em.empresa_id = es.empresa_id AND em.modulo_id = m.id AND em.activo
WHERE es.activo = FALSE;
```
Si sale algo, decidir con la empresa antes de P2 (activarlas o confirmar que no las quiere).

## Orden y lotes
P0 → P1 → P2 → P3 (observar) → P3 (bloquear) → P4. Un lote por domingo; P3 en dos domingos.

## Estado por fase
| Fase | Estado | Migración | Notas |
|---|---|---|---|
| P0 | **Hecho** | V190 | Tablas + árbol RRHH (3 grupos, 23 hojas) + 5 perfiles de sistema por empresa (ADMINISTRADOR acceso total, CAJERO, VENDEDOR, SUPERVISOR, **BASICO** para roles que son cargos) + usuarios asignados. `PermisoUsuarioService` (caché 5 min, `puede`, `puedeAlguna` con `modulo.*`), `PerfilesSistema(Service)`, `GET /api/permisos/mios`. |
| P1 | **Hecho** | V191 (`caja.perfiles`) | Back: `PerfilService` + `/api/perfiles` (lista, árbol, detalle, crear, editar, duplicar, eliminar, excepciones por usuario, historial, bloqueos, modo). Reglas: nadie da más de lo que tiene; Administrador no se edita (se duplica); sistema no se borra ni desactiva; solo SUPER_ADMIN toca a otro SUPER_ADMIN; SUPER_ADMIN nunca pierde `caja.usuarios` ni `caja.perfiles`. Alta/edición de usuario (los dos caminos) asigna perfil. Front: Caja › Perfiles y Permisos (lista + Historial + Registro del control), form plano con matriz, `/admin/usuarios/:id/permisos` (excepciones), campo Perfil en el form de usuario y en crear usuario desde empleado, columna Perfil y botón Permisos en la lista. |
| P2 | **Hecho** | — | Menú por `codigo` (112 ítems; se quitaron todas las listas `roles`; mi perfil/escanear QR quedan con `tipos: ['VENDEDOR']`), `permisoGuard` en el layout (122 `rolGuard` reemplazados; queda solo en `configuracion/tipos-empleado`), directiva `*appPuede` aplicada a 183 botones Nuevo/Editar/Anular en 71 pantallas, 403 = aviso "Sin permiso". |
| P3 | **Hecho** | V191 (`permiso_bloqueo_log`) | `PermisoInterceptor` sobre `/api/**` con `app.permisos.modo` = APAGADO / **OBSERVAR (por defecto)** / BLOQUEAR (variable `PERMISOS_MODO`, se cambia reiniciando, sin desplegar). **Cambio de diseño:** el mapa ruta→submódulo vive en `PermisoRutas.java` (no en una tabla `permiso_ruta`): se versiona con el código y `PermisoRutasTest` falla si un controller nuevo queda sin submódulo. Rutas sin submódulo no se bloquean (se anotan). Maestros de lectura libre para el POS; ventas/cotizaciones/turnos/terceros también aceptan `principal.punto-de-venta`. |
| P4 | **Hecho** | — | Historial de cambios (`permiso_cambio_log`) y Registro del control en la pantalla de perfiles; manual /ayuda (Usuarios + tema nuevo Perfiles y Permisos). |

### Ajustes del 2026-10-02 (tarde) — V193
- **Autorización sin clave en el equipo del cajero.** V192 la pedía con usuario + PIN/clave en el POS
  y se podía leer en las herramientas del navegador (y aceptaba la clave de login). Ahora: el supervisor
  genera en SU sesión un **código de 6 dígitos** (2 min, un uso; la base guarda solo su SHA-256) y lo
  dicta, o el cajero **pide aprobación remota** y el supervisor la aprueba desde su sesión (campana +
  pantalla `/autorizaciones`, escudo en la barra superior). V193: `solicitante_id`/`autorizador_id`
  admiten null, `detalle`, `codigo_hash`. Endpoints `POST /api/autorizaciones/{codigo|usar-codigo|solicitudes}`,
  `GET /pendientes`, `POST /solicitudes/{id}/{aprobar|rechazar}`.
- **Usuario ligado a un tercero (obligatorio).** `CreateUsuarioDto.terceroId`; ya no se crea un tercero
  por usuario. Un tercero = un usuario. El usuario de acceso por defecto es el correo del tercero.
  La edición ya no usa el mapper (copiaba el PIN sin cifrar o lo borraba).
- **Página de usuario con pestañas** (`/admin/usuarios/nuevo` y `/:id`): Datos y acceso · Sedes ·
  Permisos · Descuentos y acciones especiales, un solo Guardar. Se borraron el diálogo viejo y la
  pantalla aparte de permisos (`/admin/usuarios/:id/permisos` redirige).

### Estado de la segunda etapa (2026-10-02)
| Fase | Estado | Qué quedó |
|---|---|---|
| P5 | **Hecho** | V192 crea `tesoreria.obligaciones` (copia lo que cada perfil tenía en tesorería) y `caja.bitacora`; menú con código. Botones especiales (reabrir período, aprobar reconteo, aprobar/rechazar crédito, aprobar nómina) se ocultan sin la acción especial. |
| P6 | **Hecho** | `permiso_accion_especial` (8 acciones, `hereda_de`), `perfil_accion_especial`, `usuario_accion_especial`; `PermisoUsuarioService.puedeEspecial`; `@RequerirPermiso(accion = "REABRIR")` lo entiende el interceptor; `ControlPermisoService.exigirEspecial` para las que se deciden en el servicio (vender a crédito, con el modo del control). Front: `<app-acciones-especiales>` en perfil y usuario; `*appPuede="'clave:CODIGO'"`. |
| P7 | **Hecho** | `auditoria_evento` + `BitacoraService` (servicio con antes/después; automática desde el interceptor para EDITAR/ANULAR/especiales en cualquier modo). Explícitas: precio/costo de producto, usuario (desactivar, clave, sedes, cerrar sesiones), autorizaciones. Pantalla Caja › Bitácora. |
| P8 | **Hecho** | Límites en perfil y usuario; `AutorizacionService` (exceso, autorizar con usuario + PIN/clave, 10 min, un uso, techo del supervisor, freno de 5 intentos); validación en `VentaServiceImpl.crear`; POS abre `<app-autorizacion-supervisor>` antes de guardar. |
| P9 | **Hecho** | `perfil.todas_sedes`; `AlcanceSedeFilter` (parámetro `sucursalId`), `AlcanceSedeBodyAdvice` (cuerpo y `params` de listados), `{sucursalId}` de la ruta en el interceptor; con el modo del control. `POST /api/auth/cambiar-sede` + selector de la barra superior (antes no hacía nada). |
| P10 | **Hecho** | `usuario.token_version` en el claim `tv` (revoca al desactivar, cambiar clave o sedes, "Cerrar sesiones", restablecer clave); bloqueo 5 intentos / 15 min; token de 12 h por defecto. |

Tests: `PermisoEspecialesTest` (9) y `AutorizacionServiceTest` (9), más los 16 de antes: 34 en verde.

## Pendientes conocidos
- Pasar de OBSERVAR a BLOQUEAR solo después de revisar una semana el Registro del control. El alcance por sede y "vender a crédito" también siguen el modo; **los límites de descuento/precio se aplican siempre** que estén puestos.
- Alcance por sede: los listados de ventas, compras, gastos y turnos recortan en su SQL con `AlcanceSede.filtro(...)` (2026-10-02). Desde 2026-10-03 también mermas, obsequios, consumo interno, reconteos, cajas, bodegas (la tabla, no el selector: los traslados eligen bodegas de otras sedes), lotes, seriales, kardex (listado, reporte, detalle y por familia), carritos abandonados, dashboard, reporte de gastos y reportes avanzados. Para SQL de una pieza se marca `/*SEDE:columna*/` después del WHERE y se ejecuta con `alcanceSede.aplicar(sql, params)` (una marca sin aplicar es solo un comentario). Fuera a propósito: cartera y cuentas por cobrar/pagar (son por tercero), contabilidad (una sola por NIT) y la supervisión retroactiva (los abonos no tienen sede).
- La autorización del supervisor está en el POS y en el pedido del vendedor (se revisa al **tomar** el pedido; el despacho no la repite ni exige "vender a crédito", porque el pedido nace a crédito por diseño). La futura Facturación (FV) debe usar el mismo `<app-autorizacion-supervisor>`.
- La rebaja de precio se mide contra el menor precio legítimo; un precio especial de cliente baja la referencia para todos los clientes (permisivo a propósito para no frenar ventas legítimas).
- Token de 12 h: un turno más largo obliga a volver a entrar. Si molesta, subir `JWT_EXPIRATION_MS` (ya no hace falta un token largo para revocar).
- Sesiones abiertas antes de V192 traen `tv` = 0 y siguen valiendo hasta que se revoquen o venzan.
