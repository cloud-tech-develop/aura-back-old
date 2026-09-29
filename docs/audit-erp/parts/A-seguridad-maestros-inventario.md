# Auditoría ERP · Bloque A: seguridad, maestros e inventario

> **Modo:** AUDIT. No se modificó código, migraciones ni el front.
> **Fecha:** 2026-09-29 · **Rama:** `fix/camilo-caja-pagos` (con 5 archivos sin commit + `V183` sin commit).
> **Alcance:** usuarios, roles y permisos (módulo, acción, campo, overrides, scope), auditoría, multiempresa, sucursales, prefijos, consecutivos, centros de costo, terceros, catálogo, inventario y producción simplificada.
> **Referencia funcional:** baseline §1.1–1.31, §4.10–4.16, §4.20–4.28 y checklists SEGURIDAD/EMPRESA/TERCEROS/CATÁLOGO/INVENTARIO; transcripciones video-01 (permisos por campo, prefijo por usuario, min. 18–25) y video-04 (costo promedio, producción, ajustes).
>
> **Abreviaturas de rutas**
> - `$B` = `src/main/java/com/cloud_technological/aura_pos`
> - `$M` = `src/main/resources/db/migration`
> - `$F` = `D:\Proyectos Camilo\aura-post\aura-frontend\src\app`

---

## 1. Resumen del bloque

### 1.1 Inventario técnico (lo que aplica a este bloque)

| Aspecto | Detectado | Evidencia |
|---|---|---|
| Backend | Spring Boot **3.5.10**, Java **17**, Spring Security (JWT HS256, stateless), JPA + `NamedParameterJdbcTemplate` (QueryRepositories con RowMapper manual) | `pom.xml:8,30`; `$B/config/SecurityConfig.java` |
| BD | PostgreSQL. `ddl-auto=validate`. **Flyway apagado**: el esquema lo aplica el proyecto Laravel `aura-pos-migracion-old`, que espeja cada `V*.sql` | memoria del proyecto; `docs/adr/ADR-005` (switch pendiente) |
| Migraciones | 170 archivos V14…V183. Tablas núcleo (`usuario`, `empresa`, `sucursal`, `tercero`, `producto`, `inventario`, `venta`, `compra`) son **pre-V14**: ningún `.sql` las crea (sin `V13__baseline`) | `docs/ESTADO.md` §3.1 |
| Frontend | Angular + PrimeNG 18, rutas protegidas con `rolGuard([...])` (117 usos) y menú filtrado por `normalize(label)` | `$F/core/guards/role.guard.ts`; `$F/shared/utils/modules-fiilter.ts` |
| API | **767** mappings en **118** `@RestController` (la cifra de 642 de `PLAN_SEGURIDAD.md` creció) | `grep @*Mapping` |
| Tenant | `empresaId` sale siempre del JWT (`SecurityUtils.getEmpresaId()`), nunca del cliente | `$B/utils/SecurityUtils.java:21` |
| Pruebas | 40 clases de test; **ninguna** de stock, costo, bodegas, traslados, reconteo ni autorización (salvo `RateLimitFilterTest`, `KardexCatalogoTest`) | `src/test` |

### 1.2 Diagnóstico en una línea por dominio

- **Seguridad:** la autenticación es sólida; la **autorización no existe en el backend** y hay dos agujeros nuevos de escalada/cruce de tenant que no estaban en `PLAN_SEGURIDAD.md` (rol libre al crear usuarios → `PLATFORM_ADMIN`; sucursal de otra empresa asignable a un usuario).
- **Permisos:** solo existe "módulo/submódulo activo por empresa" (EP-006). No hay permiso por rol, por acción, por campo ni overrides por usuario. El control por rol vive **solo en el front** con 4 roles hardcodeados.
- **Auditoría:** no hay bitácora transversal. Hay auditoría detectiva (reporte gerencial, `AuditoriaService`) y bitácoras puntuales (config contable, nómina/asistencia).
- **Empresa/sucursal:** multiempresa por tenant correcto; sucursales, bodegas (V172) y centros de costo jerárquicos existen. Resolución DIAN **una por empresa**; prefijo por sucursal; consecutivos por `MAX()+1` sin bloqueo.
- **Terceros:** ficha fiscal rica (natural/jurídica, responsabilidades, CIIU, exógena) y crédito avanzado. Falta multi-dirección, contactos, fusión y condiciones comerciales en la ficha.
- **Catálogo:** presentaciones "contiene N", recetas con merma y rendimiento, lotes/seriales de punta a punta, precios dinámicos (cliente, volumen, franja horaria). Faltan impuestos múltiples, variantes talla/color, AIU; doble fuente de precio e IVA.
- **Inventario:** saldo por bodega bien modelado y kardex con catálogo único. Pero **cuatro fallas de integridad**: costo = último costo de compra (no promedio), stock editable a mano sin kardex ni asiento, actualización de saldo sin bloqueo (pérdida de actualizaciones en concurrencia) y reconteo que aplica diferencias contra un saldo congelado al crear.

### 1.3 Conteo de brechas del bloque

| Prioridad | Cantidad |
|---|---|
| P0 | 8 |
| P1 | 9 |
| P2 | 17 |
| P3 | 8 |
| **Total** | **42** |

---

## 2. Hallazgos críticos (P0)

| # | Hallazgo | Evidencia | Por qué es P0 |
|---|---|---|---|
| 1 | **Escalada a `PLATFORM_ADMIN` desde cualquier ADMIN de empresa.** El rol del usuario se copia del DTO sin lista blanca | `$B/dto/usuarios/CreateUsuarioDto.java:30` (`@NotBlank String rol`), `$B/mappers/UsuarioMapper.java:16-24` (no ignora `rol`), `UpdateUsuarioDto.java:22`; `SecurityConfig.java:55` concede `/api/platform/**` a quien tenga esa authority | Un admin de un cliente crea un usuario `PLATFORM_ADMIN` y administra **todas** las empresas de la plataforma. Compromiso total multi-tenant |
| 2 | **Asignar a un usuario una sucursal de otra empresa** | `$B/services/implementations/UsuarioServiceImpl.java:185` usa `sucursalRepo.findById(...)` sin `empresaId`; el login mete esa sucursal en el JWT (`AuthServiceImpl.java:168-176`) | IDOR entre tenants. Contradice la fortaleza "sin IDOR" de `PLAN_SEGURIDAD.md` §3 |
| 3 | **Catálogo global de unidades de medida editable y desactivable por cualquier usuario autenticado de cualquier empresa** | `$B/entity/UnidadMedidaEntity.java` (sin `empresa_id`); `UnidadMedidaController.java:57,65` (PUT/DELETE); `UnidadMedidaServiceImpl.java:64-84` | Un cajero de la empresa X renombra "Kilogramo" o lo desactiva para todos los clientes |
| 4 | **La API no autoriza por rol** (sigue abierto C-02/C-03 de `PLAN_SEGURIDAD.md`). Además existe `PermisoInterceptor` + `@RequerirPermiso` (HU-031) **sin registrar** y con **0 usos** | `SecurityConfig.java:50-62` (`anyRequest().authenticated()`); `WebConfig.java` sin `addInterceptors`; `grep @RequerirPermiso` = 0; 6 `@PreAuthorize` inertes | Cualquier token (cajero) puede anular ventas, ajustar inventario, cambiar costos. La rotación del `JWT_SECRET` expuesto (Fase 0.2) sigue pendiente |
| 5 | **Stock editable a mano sin kardex ni asiento** | `$B/services/implementations/InventarioServiceImpl.java:112-138` (`actualizar` aplica `stockActual` del DTO; solo mueve lotes); `:83-110` (`crear` con stock inicial, sin kardex); front `$F/features/inventario/inventario/form/form-inventario.component.html:108-116` deja el campo editable en modo edición | El kardex deja de explicar el saldo (el propio `AuditoriaQueryRepository.kardexVsStock` lo detecta después). El 1435 contable y el físico divergen sin rastro de quién |
| 6 | **Pérdida de actualizaciones de stock en concurrencia** | Todas las rutas hacen *leer → sumar en Java → save* sin `@Lock` ni `@Version`: `VentaServiceImpl.java:384-484`, `CompraServiceImpl.java:571-585`, `ReconteoServiceImpl.java:194-205`, `TrasladoServiceImpl`, `MermaServiceImpl`. `InventarioJPARepository` no tiene método con bloqueo; en todo el código solo `CuentaCobrarJPARepository` usa `PESSIMISTIC_WRITE` | Dos cajas vendiendo el mismo producto a la vez: una de las dos salidas se pierde; además la validación de stock negativo se evade |
| 7 | **El costo del producto es el último costo de compra, no costo promedio** | `CompraServiceImpl.java:1545-1551` (`producto.setCosto(item.getCostoUnitario())`); el costo de venta sale de `producto.getCosto()` (`VentaServiceImpl.java:504,851`; `ContabilidadAutoServiceImpl.java:511`); búsqueda de "promedio/ponderado" en inventario: 0 resultados | Costo de ventas, utilidad bruta e inventario valorizado incorrectos en cada compra con precio distinto. La referencia (video-04 min. 37, 1:35) mueve todo al promedio ponderado |
| 8 | **Consecutivos por `MAX()+1` sin bloqueo ni secuencia** | `$B/repositories/ventas/VentaQueryRepository.java:124-130`; `VentaServiceImpl.java:284-289` (el TODO lo reconoce); igual en `FacturaQueryRepository:41`, `CotizacionQueryRepository:89`. La unicidad de `venta(sucursal, consecutivo)` no la crea ninguna migración (tabla pre-V14): **verificar en BD** | Dos ventas simultáneas pueden salir con el mismo número (documento fiscal) o fallar si hay índice único |

---

## 3. Mapa de procesos del bloque (AS-IS)

| Proceso | Disparo → estados | Inventario | Contabilidad | Reverso | Auditoría | Observación |
|---|---|---|---|---|---|---|
| Alta de usuario | Admin → `UsuarioController.crear` (exige ADMIN/SUPER_ADMIN, `UsuarioController.java:107-114`) → crea tercero + usuario + `usuario_sucursal` | — | — | Desactivar (`activo=false`) | No | Rol libre (P0 #1); sucursal sin validar empresa (P0 #2). Alternativa `create-from-empleado`: rol = nombre del cargo (`UsuarioServiceImpl.java:252`), string libre |
| Login | `AuthServiceImpl.login` → JWT con `empresaId`, `sucursalId` (la *default*), `rol`, `usuarioId` | — | — | — | No hay registro de login | No hay endpoint para cambiar de sede sin volver a iniciar sesión; el rol del token no se refresca si se cambia (dura 72 h) |
| Alta de sucursal | `SucursalServiceImpl.crear` → (sin commit) crea "Bodega Principal" | — | — | Desactivar | No | V183 repara las creadas entre V172 y este cambio |
| Alta de producto | `ProductoServiceImpl.crear` → producto + (opcional) presentaciones, precios, receta | Ninguno (la fila de `inventario` se crea aparte) | Cuentas por categoría contable o override (V89) | Soft delete (`deleted_at`), **aunque tenga saldo** | No | SKU no se valida único (`ProductoServiceImpl.java:98,146` solo valida código de barras) |
| Saldo inicial / edición de stock | `InventarioController POST /create`, `PUT /{id}` | Cambia `stock_actual`; **sin kardex** | **Sin asiento** | No hay | No | P0 #5 |
| Compra | `CompraServiceImpl.crear` | `+` por bodega, lotes, seriales, kardex `COMPRA` | Por evento (motor contable, otro bloque) | Anulación / NC / edición con reversión (`EDICION_COMPRA_REVERSION`) | Parcial (usuario, autorizado retroactivo) | Sobrescribe costo del producto (P0 #7) |
| Venta POS | `VentaServiceImpl.crear` | `−` por bodega; explota receta (`ConsumoComposicionService`); FEFO de lotes; seriales | Por evento; costo = `producto.costo` | Anulación (`ANULACION_VENTA`) | Parcial | Sin bloqueo (P0 #6); consecutivo `MAX+1` (P0 #8) |
| Traslado | `TrasladoServiceImpl.crear` (atómico, `TRASLADO_SALIDA` + `TRASLADO_ENTRADA`) | Bodega→bodega (misma o distinta sucursal) | Ninguna (neutra; correcto dentro de la misma empresa) | `anular` (`ANULACION_TRASLADO`) | Usuario | No hay tránsito ni recepción; costo = `producto.costo` |
| Merma | `MermaServiceImpl.crear` con `motivo_merma` | `−` | Si `motivo.afecta_contabilidad` (cuenta no parametrizable por motivo) | Anulación | Usuario | Valida stock insuficiente (`MermaServiceImpl.java:194`) |
| Obsequio | `ObsequioServiceImpl` | `−` (explota receta) | `ObsequioGenerador` con IVA sobre valor comercial | Anulación | Usuario | Bien resuelto |
| Consumo interno | `ConsumoInternoServiceImpl` con concepto (cuenta + ¿genera IVA?) | `−` | Sí, por concepto | Anulación | Usuario + responsable | Mejor que referencia |
| Reconteo | `ReconteoServiceImpl` BORRADOR → EN_CONTEO → APROBADO / ANULADO | Aplica `contado − stock_sistema` (snapshot de la creación) al saldo **actual** | **Ninguna** | No se anula aprobado | creado_por, aprobado_por | Ver GAP-A-013 |
| Devolución de cliente / cambio | `DevolucionServiceImpl` (`DEVOLUCION`, `DEVOLUCION_CAMBIO`) | `+` / `−` enlazados | Costo devuelto a costo **actual** (`ContabilidadAutoServiceImpl.java:511`) | `ANULACION_DEVOLUCION` | Usuario | Cambio de mercancía resuelto dentro de la devolución |
| Producción | No hay documento. La receta se "produce" al vender (backflush) | Descarga componentes en la venta | Costo del padre = costeo de receta (`aplicar-costo`) | — | — | No se puede fabricar por lote y almacenar terminado |

---

## 4. Matriz AS-IS

Estados: COMPLETE · PARTIAL · MISSING · DIFFERENT · BETTER_THAN_REFERENCE · NOT_APPLICABLE · UNKNOWN.

### 4.1 Seguridad y usuarios

| Dominio | Funcionalidad | Implementación encontrada | Estado | Evidencia |
|---|---|---|---|---|
| Seguridad | Autenticación usuario/clave | BCrypt, JWT HS256, usuario inactivo rechazado en cada petición | COMPLETE | `SecurityConfig.java:98-107`; `CustomUserDetailsService.java:33-36` |
| Seguridad | Rate limit de login | Ventana fija por IP en `/api/auth/**` | COMPLETE | `$B/security/RateLimitFilter.java`; `PLAN_SEGURIDAD.md` 0.5 |
| Seguridad | Bloqueo por intentos fallidos | No existe | MISSING | `PLAN_SEGURIDAD.md` 1.4 (planeado) |
| Seguridad | Refresh token / revocación | JWT de 72 h sin revocación | MISSING | `PLAN_SEGURIDAD.md` 2.1 (planeado) |
| Seguridad | Rotación de llaves | Llave fuera del repo pero **la misma que estuvo versionada** | PARTIAL | `PLAN_SEGURIDAD.md` 5.2 |
| Usuarios | Usuario separado de tercero/empleado | Usuario → tercero (datos personales), empleado opcional | BETTER_THAN_REFERENCE | `$B/entity/UsuarioEntity.java` (tercero y empleado opcionales) |
| Usuarios | Invitación por correo / primer acceso | Hay recuperación de clave (token UUID, un uso); el alta fija la clave el admin | PARTIAL | `AuthController.java:56`; `PasswordResetTokenEntity` |
| Roles | Catálogo de roles | `usuario.rol` es **string libre**; front ofrece ADMIN/CAJERO/SUPERVISOR; menú conoce SUPER_ADMIN/ADMIN/CAJERO/VENDEDOR; `create-from-empleado` usa el nombre del cargo | PARTIAL (mal modelado) | `UsuarioEntity.java` (`private String rol`); `$F/core/models/usuario.model.ts:81-85`; `UsuarioServiceImpl.java:252` |
| Roles | Validación de rol asignable | Ninguna | MISSING (P0) | `CreateUsuarioDto.java:30`; `UsuarioMapper.java:16-24` |
| Permisos | Por módulo/submódulo | Por **empresa** (`empresa_modulo`, `empresa_submodulo`), gestionado por plataforma | PARTIAL | `$B/entity/EmpresaSubmoduloEntity.java`; `PermisoServiceImpl.java:130-150`; EP-006/HU-027..032 |
| Permisos | Por rol | Solo en el front (`roles:[...]` en `sidebar.config.ts`, `rolGuard`) | DIFFERENT (inseguro) | `$F/layout/sidebar/sidebar.config.ts`; `$F/app.routes.ts` (117 `rolGuard`) |
| Permisos | Por acción (ver/crear/editar/anular/aprobar) | No existe. Controles *ad hoc* por rol en 5 puntos (usuarios, notificaciones, cierre de turno, empresa, retroactivos) | MISSING | `UsuarioController.java:107`; `TurnoCajaController.java:48,107`; `ControlFechaRetroactivaServiceImpl.java:58-91` |
| Permisos | Enforcement en backend | `anyRequest().authenticated()`; `PermisoInterceptor` sin registrar; `@RequerirPermiso` sin usos; `@PreAuthorize` inerte (sin `@EnableMethodSecurity` y con `hasRole` sin prefijo) | MISSING (P0) | `SecurityConfig.java:50-62`; `WebConfig.java`; `config/PermisoInterceptor.java` |
| Permisos | Por campo (ver/ocultar/obligatorio/default) | No existe | MISSING | — |
| Permisos | Overrides por usuario (prefijo, bodega, sucursal, defaults) | Solo sucursales asignadas (`usuario_sucursal.es_default`) | PARTIAL | `UsuarioSucursalEntity.java` |
| Permisos | Límites (descuento, precio mínimo, caja, bodega) | No existen límites de descuento ni de precio. Sí: rol que cierra caja y rol que autoriza retroactivos | PARTIAL | `grep descuentoMax|precioMinimo` = 0; `EmpresaEntity.rolAutorizaRetroactivo` |
| Scope | Empresa | Siempre del token | COMPLETE | `SecurityUtils.java:21`; `PLAN_SEGURIDAD.md` §3 |
| Scope | Sucursal | El documento trae `sucursalId` en el DTO y solo se valida contra la empresa, no contra las sucursales del usuario; no hay cambio de sede | PARTIAL | `VentaServiceImpl.java:221-224`; `CompraServiceImpl.java:443`; `InventarioServiceImpl.java:87` |
| Scope | Bodega | `bodega.responsable_usuario_id` informativo; no restringe quién mueve la bodega | PARTIAL | `$M/V172__bodegas.sql:30-33` |
| Auditoría | Bitácora transversal (quién/qué/cuándo/antes/después) | No existe | MISSING | `PLAN_SEGURIDAD.md` M-05 / 2.3 |
| Auditoría | Bitácoras puntuales | `contabilidad_config_log` (V85), `contabilidad_posting_log`, `auditoria_nomina_asistencia` (V73), `error_log` (V15); `centros_costos.usuario_creacion/modificacion` | PARTIAL | migraciones citadas |
| Auditoría | Auditoría detectiva por cruces | Reporte gerencial: kardex vs stock, signo de kardex, asientos descuadrados, caja, cartera | BETTER_THAN_REFERENCE | `$B/services/AuditoriaService.java`; `AuditoriaQueryRepository.java:171-260` |
| UX | Menú por permisos | Filtro por `normalize(label)` contra un Set global de submódulos (labels repetidos comparten visibilidad) y submódulos que se crean con SQL manual (`docs/sql/menu_submodulo_*.sql`, varios sin correr) | PARTIAL | `modules-fiilter.ts:21-60`; `docs/sql/` |
| Dashboard | Dashboard protegido | Dashboard existe; su protección es por menú del front | PARTIAL | `DashboardController.java`; `rolGuard` |

### 4.2 Empresa, sucursales, prefijos, consecutivos, centros de costo

| Dominio | Funcionalidad | Implementación | Estado | Evidencia |
|---|---|---|---|---|
| Empresa | Multiempresa (tenant) | `empresa_id` en cada tabla, del token; alta de empresas desde plataforma con suscripción (V84) | COMPLETE | `EmpresaPlataformaServiceImpl`; `$M/V84__empresa_suscripcion.sql` |
| Empresa | Un usuario en varias empresas (contador, grupo) | `usuario.empresa_id` único; `username` único global | MISSING | `UsuarioEntity.java` |
| Sucursal | Sucursales | `sucursal` con código, prefijo, consecutivo, centro de costo | COMPLETE | `$B/entity/SucursalEntity.java` |
| Sucursal | Bodega principal automática | V172 + (sin commit) `BodegaService.crearPrincipal` + V183 | COMPLETE (pendiente commit/prod) | `SucursalServiceImpl`, `AuthServiceImpl`, `$M/V183__bodega_principal_faltante.sql` |
| Prefijos | Prefijo por sucursal | `sucursal.prefijo_facturacion` | PARTIAL | `SucursalEntity.java:42` |
| Prefijos | Resoluciones DIAN (rango, vigencia) por prefijo/sucursal | **Una** resolución en `empresa` (`resolucion_*`, `factus_numbering_range_id`) | PARTIAL | `EmpresaEntity.java:111-137`; `$M/V25__resolucion_facturacion.sql` |
| Prefijos | Prefijo por tipo de documento y por usuario | No existe | MISSING | video-01 min. 21-25 |
| Consecutivos | Numeración segura en concurrencia | `MAX()+1` en venta, factura, cotización, recibo, acuerdo; recibo y acuerdo tienen `UNIQUE` (V167, V170) | PARTIAL (P0 en venta) | `VentaQueryRepository.java:124`; `$M/V167:32`, `$M/V170:32` |
| Centros de costo | Jerárquicos, por sucursal, presupuesto, responsable | `centros_costos` con padre, nivel, `permite_movimientos`, sucursal | COMPLETE | `$B/entity/CentroCostoEntity.java`; `$M/V49__centros_costos.sql` |
| Centros de costo | En documentos y asientos | compra/gasto/sucursal/asiento_detalle (V51, V92) | COMPLETE (profundidad: otro bloque) | `CompraServiceImpl.java:486`; `$M/V51`, `$M/V92` |
| Empresa | Secretos de integración | Credenciales Factus en texto plano en `empresa` (no se exponen en DTO de salida) | PARTIAL | `EmpresaEntity` (`factusClientSecret`, `factusPassword`, `factusAccessToken`) |

### 4.3 Terceros

| Dominio | Funcionalidad | Implementación | Estado | Evidencia |
|---|---|---|---|---|
| Terceros | Roles coexistentes cliente/proveedor/empleado/banco | Booleanos `es_*` **y** tabla `tercero_rol` (V98) con doble escritura | PARTIAL (dos fuentes) | `TerceroEntity` (`esCliente`…); `TerceroRolEntity`; `docs/ESTADO.md` Fase 1 |
| Terceros | Natural/jurídica, nombres separados, DV | `nombre1/2`, `apellido1/2`, `razonSocial`, `dv`, `tipoPersona` (V97) | COMPLETE | `$M/V97__tercero_campos_natural_juridica.sql` |
| Terceros | Datos fiscales | régimen, responsabilidad fiscal, gran contribuyente, autorretenedor fuente/ICA, declarante, CIIU, e-mail FE | COMPLETE | `$M/V52__tercero_campos_fiscales.sql`; `TerceroEntity` |
| Terceros | Múltiples direcciones (envío, sucursales del cliente) | Una sola `direccion`/`municipio` | MISSING | `TerceroEntity` |
| Terceros | Contactos (persona, cargo, teléfono, e-mail) | Solo un teléfono/e-mail en la ficha | MISSING | `grep tercero_contacto` = 0; `form-tercero.component.html:140` es solo la sección "Contacto" de la ficha |
| Terceros | Condiciones comerciales (lista de precios, vendedor, forma de pago, descuento) | Precio especial por cliente+presentación, descuento por cliente+categoría, plazo en `tercero_credito`; **sin lista de precios ni vendedor por defecto** | PARTIAL | `PrecioClienteEntity`, `DescuentoClienteEntity`, `TerceroCreditoEntity` |
| Terceros | Crédito (cupo, estado, riesgo, autorización) | `tercero_credito`, `regla_credito`, solicitudes de autorización, control de crédito (V169), historial | BETTER_THAN_REFERENCE | `$B/entity/TerceroCreditoEntity.java`; `$M/V169__control_credito.sql` |
| Terceros | Impuestos/retenciones por tercero | Flags fiscales; tarifas de retención (V57, V181) | PARTIAL (detalle: bloque fiscal) | `TarifaRetencionEntity` |
| Terceros | Historial 360 | Estado de cuenta (CxC/CxP) + PDF; ficha de cliente en cartera | PARTIAL | `TerceroController.java:138,150`; `$F/features/cartera/ficha-cliente` |
| Terceros | Fusión/traslado de duplicados | No existe | MISSING | `grep fusion|merge` sin resultados en terceros |
| Terceros | Unicidad de documento | Validación en servicio (`existeDocumento`); índice en BD: tabla pre-V14 | PARTIAL / UNKNOWN en BD | `TerceroServiceImpl.java:138,185` |
| Terceros | Creación rápida DIAN/RUES | No existe | MISSING | sin resultados en back ni front |
| Terceros | Borrado | Soft delete (`deleted_at`, `activo=false`) | COMPLETE | `TerceroServiceImpl.java:206-207` |
| Terceros | Importación Excel | Validar/confirmar terceros | COMPLETE | `ImportacionController.java:53-58` |

### 4.4 Catálogo

| Dominio | Funcionalidad | Implementación | Estado | Evidencia |
|---|---|---|---|---|
| Catálogo | Producto / servicio | `tipo_producto` ESTANDAR/KIT/PESABLE/SERVICIO (sin CHECK) + `maneja_inventario` | PARTIAL | `ProductoEntity.java:54`; `PLAN_INSUMOS_MERMAS_COMPOSICION.md` §0 |
| Catálogo | Uso venta/insumo | `uso_producto` VENTA/INSUMO/AMBOS (V158) | COMPLETE | `$M/V158__producto_uso.sql` |
| Catálogo | Gasto / activo como ítem | Módulos separados (`gasto`, `activo_fijo`), no en el maestro de productos | DIFFERENT (aceptable) | `GastoEntity`, `ActivoFijoEntity` |
| Catálogo | Categoría jerárquica + categoría contable | `categoria.padre`, `categoria_contable_producto` y overrides de cuenta en producto (V89) | COMPLETE | `CategoriaEntity`; `$M/V89` |
| Catálogo | Código de barras / SKU | Código de barras único por empresa (servicio); **SKU sin validar**; código de barras de presentación con índice no único (V159) | PARTIAL | `ProductoServiceImpl.java:98,146`; `$M/V159:85-104` |
| Catálogo | Conversiones / presentaciones | Presentación "contiene N" (V159), cambio de unidad (V160), `se_vende`, en documentos (V162) | COMPLETE (F5–F7 pendientes) | `PLAN_PRESENTACIONES_SIN_FRICCION.md` F1–F4 hechos |
| Catálogo | Unidades de medida | Tabla **global** sin empresa; editable por cualquiera | PARTIAL (P0 seguridad) | `UnidadMedidaEntity`; `UnidadMedidaServiceImpl.java:64-84` |
| Catálogo | Listas de precios | `lista_precios` + `producto_precio` (por presentación, utilidad esperada) **y** columnas `precio`, `precio_2`, `precio_3` en producto | PARTIAL (dos fuentes) | `ListaPreciosEntity`, `ProductoPrecioEntity`, `ProductoEntity.java:48-53` |
| Catálogo | Descuentos por cantidad | `precio_volumen` por presentación (rango) | COMPLETE | `PrecioVolumenEntity` |
| Catálogo | Reglas de descuento por día/hora/categoría/producto | `regla_descuento` | BETTER_THAN_REFERENCE | `ReglaDescuentoEntity` |
| Catálogo | Precio por cliente con vigencia | `precio_cliente` | BETTER_THAN_REFERENCE | `PrecioClienteEntity` |
| Catálogo | Impuestos parametrizados | `impuesto` (IVA/INC/EXCLUIDO/EXENTO, cuentas, vigencia) — **uno** por producto + `iva_porcentaje` + `impoconsumo` sueltos | PARTIAL | `$M/V90__impuestos.sql`; `ProductoEntity.java:54-71` |
| Catálogo | Impuestos múltiples (bolsa, ultraprocesados, bebidas azucaradas) | No existe relación producto–impuesto N:M | MISSING | — |
| Catálogo | Serial | Seriales de punta a punta (V166), garantía | COMPLETE | `SerialProductoEntity`; `PLAN_LOTES_SERIALES_CONSUMO_INTERNO.md` F4 |
| Catálogo | Lote / vencimiento | Lotes FEFO que cuadran con inventario (V164/V165), alertas | BETTER_THAN_REFERENCE | `LoteEntity`; `LoteStockService`; plan F1–F3, F6 |
| Catálogo | Talla/color (variantes) | No existe | MISSING | `grep talla|variante` sin resultados de catálogo |
| Catálogo | Kits / receta | `producto_composicion` (KIT/receta, unidad, presentación, % merma, orden, nota, rendimiento, costeo, duplicar) | BETTER_THAN_REFERENCE | `ProductoComposicionEntity`; `ProductoComposicionController.java:97-148`; `$M/V138` |
| Catálogo | Facturar sin existencia | Booleano `permitir_stock_negativo` por producto (BLOQUEAR/PERMITIR) | PARTIAL | `$M/V18`; `VentaServiceImpl.java:390` |
| Catálogo | AIU | No existe | MISSING | — |
| Catálogo | Favorito POS | `visible_en_pos` (no favoritos) | PARTIAL | `ProductoEntity.java:125` |
| Catálogo | Etiquetas de código de barras | Existe feature | COMPLETE | `$F/features/catalogo/etiquetas` |
| Catálogo | Importación de productos | No existe (solo SQL manual por cliente) | MISSING | `ImportacionController` (sin productos); `docs/sql/migracion_productos_el_pisingo.sql`; plan presentaciones F6 |
| Catálogo | Historial de cambios de precio/costo | No existe | MISSING | — |

### 4.5 Inventario y producción

| Dominio | Funcionalidad | Implementación | Estado | Evidencia |
|---|---|---|---|---|
| Inventario | Saldo por bodega | `inventario(bodega_id, producto_id)` único; `sucursal_id` derivado | COMPLETE | `$M/V172__bodegas.sql:155-195` |
| Inventario | Bodega: principal, permite venta, responsable, ubicación | Sí | COMPLETE | `BodegaEntity`; `BodegaServiceImpl.java:155-170` (borrado protegido) |
| Inventario | Mínimo | `inventario.stock_minimo` + alerta stock bajo | PARTIAL | `InventarioEntity`; `InventarioController.java:58` |
| Inventario | Máximo / punto de reorden / sugerido de compra | No existe | MISSING | `grep reorden|stock_maximo` = 0 |
| Inventario | Costo promedio ponderado | No existe; último costo de compra | MISSING (P0) | `CompraServiceImpl.java:1545-1551` |
| Inventario | Costo en kardex | `costo_historico` = `producto.costo` del momento; reconteo escribe **0** | PARTIAL | `ReconteoServiceImpl.java:233`; `KardexQueryRepository.java:182-183` |
| Inventario | Negativos | Por producto, sin "advertir", sin política por empresa/bodega | PARTIAL | `$M/V18` |
| Inventario | Traslado entre bodegas | Documento único, dos movimientos atómicos, anulación | COMPLETE | `TrasladoServiceImpl.java:115,233`; `TipoMovimientoInventario` |
| Inventario | Traslado en tránsito / recepción | No existe | MISSING (P3) | `TrasladoEntity` (estado sin tránsito) |
| Inventario | Cambio de mercancía | `DEVOLUCION_CAMBIO` enlazado a la devolución | COMPLETE | `TipoMovimientoInventario.java:45` |
| Inventario | Salida por pérdida/deterioro/muestra | Merma con motivo parametrizable (solo `afecta_contabilidad`) | PARTIAL | `MotivoMermaEntity` |
| Inventario | Salida por consumo | Consumo interno con concepto → cuenta, IVA, responsable | BETTER_THAN_REFERENCE | `$M/V163`; `ConceptoConsumoInternoEntity` |
| Inventario | Obsequio | Documento propio con IVA | BETTER_THAN_REFERENCE | `ObsequioGenerador` |
| Inventario | Entrada de almacén por motivo (sobrante, obsequio recibido, saldo inicial) | No existe (solo reconteo positivo o edición directa) | MISSING | `TipoMovimientoInventario` sin tipo de entrada manual |
| Inventario | Ajuste físico (conteo) | Reconteo total/parcial con estados y aprobación | PARTIAL (defectos) | `ReconteoServiceImpl.java:172-246` |
| Inventario | Edición directa de stock | Permitida, sin kardex | DIFFERENT (P0) | `InventarioServiceImpl.java:112-138` |
| Inventario | Kardex | Catálogo único de tipos, reporte resumido/detalle, Excel/PDF, valorizado | PARTIAL | `$B/utils/TipoMovimientoInventario.java`; `KardexController.java:40-85`; `ReporteController.java:152-173` |
| Inventario | Trazabilidad kardex→documento | `referencia_origen` texto; sin `documento_tipo/id`, sin usuario; signo no uniforme (reconteo en `abs`) | PARTIAL | `MovimientoInventarioEntity.java:25-34`; `TipoMovimientoInventario.java:16-21` |
| Inventario | Concurrencia | Sin bloqueo | MISSING (P0) | ver P0 #6 |
| Inventario | Consolidado de existencias / valorizado | Listado de inventario, Excel/PDF, rotación | PARTIAL | `ReporteController.java:294,301,365` |
| Inventario | Kardex por lote / serial | Lotes y seriales con documentos (`documento_lote`, `documento_serial`) | COMPLETE | `DocumentoLoteEntity`, `DocumentoSerialEntity` |
| Inventario | Informe de precios (listas vs existencias) | No existe como informe | MISSING | — |
| Producción | Receta / ficha técnica | Completa, con costeo | COMPLETE | `ProductoComposicionController.java:108-148` |
| Producción | Producción por venta (backflush) | Venta descarga componentes | DIFFERENT (válido para restaurante) | `VentaServiceImpl.java:430-481` |
| Producción | Orden de producción por lote (entrada de terminado) | No existe | MISSING | `grep produccion` sin resultados de inventario |
| Producción | Informe de costos de producto terminado | Solo costeo teórico de la receta | PARTIAL | `/receta/{id}/costeo` |

---

## 5. Brechas (GAP-A-XXX)

### P0 — Crítico

#### GAP-A-001 — Rol de usuario sin lista blanca (escalada a PLATFORM_ADMIN)
**Estado:** MISSING (control) · **Prioridad:** P0
**AURA actual:** `CreateUsuarioDto.rol` solo es `@NotBlank` (`$B/dto/usuarios/CreateUsuarioDto.java:30`); `UsuarioMapper.toEntity` copia `rol` (`$B/mappers/UsuarioMapper.java:16-24`); `UpdateUsuarioDto.java:22` también. `SecurityConfig.java:55` da `/api/platform/**` a la authority `PLATFORM_ADMIN`. `UsuarioController` solo exige que quien llama sea ADMIN o SUPER_ADMIN (`:107-114`).
**Referencia funcional:** §1.2 — los roles son un catálogo administrado; ningún rol de empresa puede otorgar privilegios de plataforma.
**Problema:** un ADMIN de cualquier cliente crea (o edita) un usuario con rol `PLATFORM_ADMIN` o `SUPER_ADMIN` y obtiene control de la plataforma entera o de su empresa por encima del dueño.
**Decisión:** implementar ya (contención).
**Diseño propuesto:** lista blanca de roles asignables por empresa (`ADMIN`, `SUPERVISOR`, `CAJERO`, `VENDEDOR` + roles personalizados cuando exista GAP-A-010). `PLATFORM_ADMIN` solo se crea por SQL o desde `/api/platform`. Nadie asigna un rol superior al propio (`SUPER_ADMIN` solo lo asigna `SUPER_ADMIN`). Ignorar `rol` en `updateEntityFromDto` salvo por el método explícito `cambiarRol`.
**Impacto técnico:** backend (`UsuarioServiceImpl`, mapper, DTO con `@Pattern`/enum), sin BD.
**Riesgos:** usuarios existentes con roles fuera de la lista (p. ej. nombres de cargo por `create-from-empleado`): diagnosticar antes (`SELECT rol, count(*) FROM usuario GROUP BY rol`).
**Dependencias:** ninguna.
**Pruebas:** unitarias del validador; integración: ADMIN intenta crear `PLATFORM_ADMIN` → 403; SUPER_ADMIN crea CAJERO → 201.
**Criterios de aceptación:** ningún endpoint de empresa persiste `PLATFORM_ADMIN`; consulta en prod de usuarios `PLATFORM_ADMIN` revisada y justificada.

#### GAP-A-002 — Sucursal de otra empresa asignable a un usuario
**Estado:** MISSING (control) · **Prioridad:** P0
**AURA actual:** `UsuarioServiceImpl.java:185` `sucursalRepo.findById(asig.getSucursalId())`; también `asignarSucursalesDesdeCreateEmpleado` (`:277`). El login pone esa sucursal en el token (`AuthServiceImpl.java:168-176`) y servicios como `ProductoController`, `KardexQueryRepository`, `BodegaServiceImpl` filtran por `securityUtils.getSucursalId()`.
**Referencia funcional:** §1.5 — el override de sucursal es dentro de la empresa.
**Problema:** IDOR entre tenants; lectura de datos de otra empresa por los caminos que filtran solo por sucursal.
**Decisión:** implementar.
**Diseño propuesto:** `findByIdAndEmpresaId`; además, en `JwtAuthenticationFilter` (o en `SecurityUtils`) validar que `sucursalId` del token pertenece a `empresaId`.
**Impacto técnico:** backend, 2 métodos + verificación; diagnóstico SQL de `usuario_sucursal` cruzados.
**Riesgos:** bajo.
**Dependencias:** ninguna.
**Pruebas:** integración: asignar sucursal de empresa B desde admin de A → 404.
**Criterios de aceptación:** consulta `usuario_sucursal us JOIN usuario u JOIN sucursal s WHERE u.empresa_id <> s.empresa_id` devuelve 0 filas en prod.

#### GAP-A-003 — Autorización del backend inexistente
**Estado:** MISSING · **Prioridad:** P0 · **Ya planeado:** `docs/PLAN_SEGURIDAD.md` C-02, C-03, Fase 1 (1.1–1.3) y 0.2 (rotación de `JWT_SECRET`), todo pendiente.
**AURA actual:** `SecurityConfig.java:50-62`; `PermisoInterceptor` (`$B/config/PermisoInterceptor.java`) existe pero `WebConfig` no lo registra y `@RequerirPermiso` tiene 0 usos; `@PreAuthorize("hasRole(...)")` en 6 clases sin `@EnableMethodSecurity`. 767 endpoints.
**Referencia funcional:** §1.3–1.4 — un rol puede crear documentos sin consultar informes; permisos por acción.
**Problema:** un cajero con su token puede ajustar inventario, cambiar costos, aprobar reconteos, anular traslados y todo lo demás por API.
**Decisión:** implementar en dos pasos: red de contención por URL (PLAN_SEGURIDAD 1.3) y luego el modelo de GAP-A-010.
**Diseño propuesto:** paso 1 (días): `@EnableMethodSecurity`, cambiar `hasRole`→`hasAuthority`, reglas por URL para `/api/inventario/**` (escritura), `/api/reconteos/*/aprobar`, `/api/productos/**` (escritura), `/api/usuarios/**`, `/api/sucursales/**`, `/api/bodegas/**`, `DELETE /api/**` → ADMIN/SUPER_ADMIN. Registrar `PermisoInterceptor` para que el módulo/submódulo desactivado por empresa también se aplique en backend. Paso 2: GAP-A-010.
**Impacto técnico:** `SecurityConfig`, `WebConfig`, 6 anotaciones; mapa endpoint→rol mínimo.
**Riesgos:** romper pantallas de CAJERO/VENDEDOR que hoy llaman endpoints "de admin" para leer (p. ej. búsqueda de productos, terceros). Mitigar: solo escritura en el paso 1 y prueba por rol con las rutas del front.
**Dependencias:** ninguna.
**Pruebas:** matriz curl por rol (PLAN_SEGURIDAD §6); test de integración `@WithMockUser` por grupo de URL.
**Criterios de aceptación:** `curl` con token CAJERO a `PUT /api/inventario/{id}`, `POST /api/reconteos/{id}/aprobar`, `PUT /api/productos/{id}` → 403; el POS sigue vendiendo.

#### GAP-A-004 — Unidades de medida globales modificables por cualquier tenant
**Estado:** MISSING (control) · **Prioridad:** P0
**AURA actual:** `unidad_medida` sin `empresa_id`; `UnidadMedidaController.java:57,65` PUT/DELETE; `UnidadMedidaServiceImpl.java:64-84` sin filtro de empresa.
**Referencia funcional:** §1.22 — unidades como catálogo; no dice nada de compartir entre empresas.
**Problema:** integridad cross-tenant del catálogo (renombrar "UND" a otra cosa cambia facturas, etiquetas y el mapeo DIAN de todos).
**Decisión:** implementar.
**Diseño propuesto:** corto plazo: escritura solo `PLATFORM_ADMIN` (regla de URL). Mediano plazo (GAP-A-038): unidades de sistema (sólo lectura, con código DIAN) + unidades propias por empresa.
**Impacto técnico:** `SecurityConfig` (1 regla); luego migración `unidad_medida.empresa_id NULL = sistema`.
**Riesgos:** ninguno en el corto plazo.
**Dependencias:** ninguna.
**Pruebas:** `PUT /api/unidades-medida/1` con token ADMIN de empresa → 403.
**Criterios de aceptación:** solo plataforma modifica unidades de sistema.

#### GAP-A-005 — Stock editable a mano sin kardex ni asiento
**Estado:** DIFFERENT (anti-patrón) · **Prioridad:** P0
**AURA actual:** `InventarioServiceImpl.actualizar` (`:112-138`) aplica `UpdateInventarioDto.stockActual` directo; `crear` (`:83-110`) acepta stock inicial; ninguno escribe `movimiento_inventario` ni publica evento contable (solo mueve lotes). El formulario lo permite en edición (`form-inventario.component.html:108-116`, `form-inventario.component.ts:107,216-223`).
**Referencia funcional:** §4.16 — todo ajuste genera documento con cantidad sistema, física, diferencia, costo, motivo y contabilización.
**Problema:** el saldo no se explica por el kardex (el reporte gerencial lo detecta *a posteriori* con `kardexVsStock`), el valor contable del inventario no cambia y no queda quién lo hizo.
**Decisión:** refactorizar: el stock solo cambia por documentos.
**Diseño propuesto:** (1) `stockActual` se ignora en `actualizar` (mínimo, ubicación sí editables); (2) en `crear`, un stock inicial > 0 genera movimiento `SALDO_INICIAL` (nuevo tipo) con costo, y evento `INVENTORY_OPENING` para el motor contable (contrapartida parametrizable, p. ej. saldos iniciales/3xxx), o se exige usar "Entrada de almacén" (GAP-A-027); (3) front: campo de stock solo lectura en edición con botón "Ajustar" que lleva a reconteo/ajuste.
**Impacto técnico:** backend (`InventarioServiceImpl`, `TipoMovimientoInventario`), front (form), evento contable (coordinar con bloque contable).
**Riesgos:** usuarios acostumbrados a "corregir" el stock ahí → ofrecer ajuste rápido de una línea (reconteo parcial de un producto) en el mismo flujo.
**Dependencias:** GAP-A-006 (bloqueo), GAP-A-016 (kardex estructurado) recomendable.
**Pruebas:** integración: `PUT` con `stockActual` no cambia el saldo; `crear` con stock 10 escribe kardex `SALDO_INICIAL` con `saldo_nuevo − saldo_anterior = 10`.
**Criterios de aceptación:** `kardexVsStock` = 0 hallazgos nuevos después del despliegue.

#### GAP-A-006 — Actualización de stock sin control de concurrencia
**Estado:** MISSING · **Prioridad:** P0
**AURA actual:** patrón leer-sumar-guardar sin bloqueo en venta (`VentaServiceImpl.java:384-484,776-808`), compra (`CompraServiceImpl.java:571-585,1364-1369`), reconteo (`ReconteoServiceImpl.java:194-205`), traslado, merma, obsequio, consumo, devolución; lotes igual (`LoteStockService`). `InventarioJPARepository` sin `@Lock`; entidades sin `@Version`.
**Referencia funcional:** checklist INVENTARIO "concurrencia"; baseline §7 "SaldoInventario".
**Problema:** actualizaciones perdidas con dos cajas o una venta y una compra simultáneas; la validación "stock insuficiente" se evade (ambas leen el mismo saldo).
**Decisión:** implementar un **servicio único de movimiento de stock**.
**Diseño propuesto:** `InventarioMovimientoService.aplicar(bodega, producto, delta, tipo, documento, costo, lote?)` que: (a) bloquea la fila con `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)` + `findForUpdate`) en orden de `producto_id` para evitar interbloqueos; (b) valida la política de negativos; (c) escribe el kardex con convención de signo única; (d) devuelve saldo anterior/nuevo. Migrar los servicios uno a uno (venta y compra primero). Alternativa mínima inmediata: `UPDATE inventario SET stock_actual = stock_actual + :delta WHERE id = :id [AND stock_actual + :delta >= 0] RETURNING stock_actual`.
**Impacto técnico:** backend transversal (10+ servicios); sin cambio de esquema (o `version` si se elige optimista).
**Riesgos:** deadlocks si no se ordena; latencia mínima. Regresiones en recetas/lotes: cubrir con pruebas.
**Dependencias:** GAP-A-032 (pruebas).
**Pruebas:** integración con Testcontainers: 20 ventas concurrentes de 1 und sobre stock 10 → 10 exitosas, 10 rechazadas, saldo 0, 10 filas de kardex.
**Criterios de aceptación:** prueba de concurrencia verde; `kardexVsStock` estable.

#### GAP-A-007 — Costo del producto = último costo de compra
**Estado:** MISSING (costo promedio) · **Prioridad:** P0
**AURA actual:** `CompraServiceImpl.actualizarPreciosProducto` (`:1545-1551`) sobrescribe `producto.costo`; la NC no lo toca (`:563-568`, bien). El costo de venta, merma, obsequio, consumo, traslado, reconteo y devolución lee `producto.getCosto()` (ver `grep getCosto()`: `VentaServiceImpl.java:504,851`, `ContabilidadAutoServiceImpl.java:511`, `ConsumoInternoServiceImpl.java:219`, `ObsequioServiceImpl.java:231`, `ReconteoServiceImpl.java:222`, `DevolucionServiceImpl.java:298,407,450`). Los lotes sí promedian su propio costo (`LoteStockService.java:134`).
**Referencia funcional:** video-04 (min. 17, 37:46, 1:35:36) — entradas y salidas al costo promedio ponderado; costo de producto terminado = suma de promedios de componentes.
**Problema:** una compra cara o barata reescribe el costo de **todo** el stock existente; el costo de ventas y el inventario valorizado quedan mal y la utilidad es incorrecta. Es un saldo contable incorrecto (P0 por la regla de priorización).
**Decisión:** implementar costo promedio ponderado móvil por **empresa + producto** (unidad base).
**Diseño propuesto:** columna `producto.costo_promedio` (o tabla `producto_costo(empresa_id, producto_id, costo_promedio, updated_at)`); en cada entrada valorizada (compra, entrada de almacén, saldo inicial, producción): `nuevo = (saldo_total × prom + cant × costo) / (saldo_total + cant)` calculado con el saldo **de toda la empresa** bajo bloqueo; salidas y traslados usan el promedio vigente; devoluciones y anulaciones reingresan al **costo del movimiento original** (GAP-A-015). `producto.costo` queda como "último costo" informativo. Presentaciones: costo por unidad base = costo_presentación ÷ factor. Kardex guarda `costo_unitario` y `valor` (GAP-A-016). El motor contable (otro bloque) toma el costo del kardex, no del producto.
**Impacto técnico:** BD (`V184+`, espejo Laravel), servicio de stock (GAP-A-006), generadores contables de costo de venta, reportes de inventario/utilidad.
**Riesgos:** saldos negativos (promedio indefinido) → usar último promedio conocido y marcar; decisión de corte: inicializar `costo_promedio = producto.costo` al migrar y documentar que la historia no se recalcula (o recalculo opcional por producto).
**Dependencias:** GAP-A-006, GAP-A-016; coordinación con el bloque contable (evento `COST_OF_SALES`).
**Pruebas:** unitarias de la fórmula (compra 10×100, compra 10×200 → 150; venta 5 → costo 750); golden test del asiento de costo de venta.
**Criterios de aceptación:** valor del inventario (Σ saldo × promedio) = saldo contable de la 1435 por categoría contable en un ciclo compra-venta-devolución de prueba.

#### GAP-A-008 — Consecutivos `MAX()+1` sin bloqueo
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `VentaQueryRepository.java:124-130`, `VentaServiceImpl.java:284-289` (TODO), `FacturaQueryRepository.java:41-47`, `CotizacionQueryRepository.java:89-95` (además parsea `SUBSTRING(numero,5)`), `ReciboCajaQueryRepository:33`, `AcuerdoPagoQueryRepository:36` (estos dos con `UNIQUE`).
**Referencia funcional:** §1.5, video-01 min. 21-25 (prefijos por documento y usuario, consecutivo por prefijo).
**Problema:** dos documentos con el mismo número (fiscal) o error 500 al chocar con un índice único, según exista o no en la BD de producción.
**Decisión:** implementar un **numerador** central.
**Diseño propuesto:** tabla `numerador(empresa_id, sucursal_id NULL, tipo_documento, prefijo, siguiente, desde, hasta, vigente_hasta, resolucion_id NULL)` con `UPDATE … SET siguiente = siguiente + 1 … RETURNING siguiente - 1`; índice único `(sucursal_id, prefijo, consecutivo)` en `venta` tras diagnóstico de duplicados (solo lectura en prod). Se integra con GAP-A-014 (resoluciones).
**Impacto técnico:** BD (tabla + backfill desde `MAX`), `VentaServiceImpl`, `CotizacionService`, `FacturaService`, recibos y acuerdos (migran al numerador).
**Riesgos:** los huecos por rollback son aceptables salvo en FE (Factus asigna su propio número; confirmar con el bloque fiscal qué número es el legal).
**Dependencias:** ninguna para el paso 1 (bloqueo); GAP-A-014 para la parte de resolución.
**Pruebas:** 50 ventas concurrentes en la misma sucursal → 50 consecutivos distintos y contiguos.
**Criterios de aceptación:** diagnóstico `SELECT sucursal_id, consecutivo, count(*) … HAVING count(*)>1` = 0 y el índice único creado.

### P1 — Alto

#### GAP-A-010 — Modelo de roles y permisos por acción inexistente
**Estado:** MISSING · **Prioridad:** P1 · **Ya planeado parcialmente:** EP-006 (módulos por empresa, HU-027..032), `PLAN_SEGURIDAD.md` Fase 1.2.
**AURA actual:** rol string libre; permisos solo por empresa; roles hardcodeados en `sidebar.config.ts` y 117 `rolGuard`; controles *ad hoc* por rol en 5 sitios (tabla §4.1).
**Referencia funcional:** §1.2–1.5 y §1.7 (permisos gruesos por módulo/documento/informe/configuración, finos por acción/botón/campo, overrides por usuario).
**Problema:** una empresa no puede crear un rol "Bodeguero" que haga traslados y reconteos sin ver costos, ni un "Auxiliar contable" que consulte sin anular. Cada regla nueva es código.
**Decisión:** implementar un RBAC con acciones, sin copiar la UI de WO.
**Diseño propuesto:**
- `rol(id, empresa_id NULL=sistema, codigo, nombre, es_sistema)`; `usuario.rol_id` (mantener `usuario.rol` como código mientras migra).
- `permiso(codigo)` jerárquico `modulo.recurso.accion` (p. ej. `inventario.ajuste.aprobar`, `inventario.costo.ver`, `productos.precio.editar`, `ventas.venta.anular`). Catálogo en código (enum) sembrado por migración.
- `rol_permiso(rol_id, permiso)`; `usuario_permiso(usuario_id, permiso, efecto ALLOW/DENY)` para overrides.
- Regla: no se otorga una acción si el módulo no está activo para la empresa (`empresa_submodulo`) — el permiso fino nunca supera al grueso (§1.4).
- Enforcement: `@PreAuthorize("@perm.tiene('inventario.ajuste.aprobar')")` con un bean que cachea permisos por usuario (Caffeine, invalidación al cambiar rol/permisos). Las authorities **no** van en el JWT (se leen del caché) para que un cambio aplique sin esperar 72 h.
- Front: `GET /api/me/permisos` → directiva `*appPermiso="'…'"` y guard por permiso; el menú deja de filtrar por label (GAP-A-031).
- Roles de sistema sembrados equivalentes a los actuales (SUPER_ADMIN, ADMIN, SUPERVISOR, CAJERO, VENDEDOR) para no cambiar comportamiento el día 1.
**Impacto técnico:** BD (4 tablas), backend (bean de permisos, anotaciones en 118 controladores por lotes), front (servicio, directiva, guard, pantalla de roles).
**Riesgos:** volumen; hacerlo por módulos empezando por los de impacto económico. No duplicar reglas en front y back: el front solo oculta, la verdad es el back.
**Dependencias:** GAP-A-003 paso 1.
**Pruebas:** unitarias del evaluador (ALLOW/DENY, módulo inactivo); integración por endpoint crítico; E2E: rol personalizado sin `inventario.costo.ver` no ve costos.
**Criterios de aceptación:** una empresa crea un rol, le asigna acciones y el backend las respeta; los 5 roles actuales siguen funcionando igual.

#### GAP-A-011 — Scope de sucursal/bodega no aplicado; sede fija por login
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `sucursalId` llega en el DTO y solo se valida con la empresa (`VentaServiceImpl.java:221-224`, `CompraServiceImpl.java:443`, `InventarioServiceImpl.java:87`); `usuario_sucursal` solo se consulta al login (`AuthQueryRepository`); no hay endpoint para cambiar de sede (el token lleva la *default*); el rol/sucursal del token no se refrescan (`SecurityUtils.java:33` lee claims).
**Referencia funcional:** §1.5 (usuario ligado a sucursal/bodega/prefijo), checklist "scope empresa/sucursal".
**Problema:** un cajero asignado a la sede A puede registrar ventas, compras, mermas y traslados en la sede B por API; los reportes por sucursal no están restringidos.
**Decisión:** implementar.
**Diseño propuesto:** `ScopeService.exigirSucursal(sucursalId)` (el usuario la tiene asignada o tiene permiso `scope.todas_sucursales`); `POST /api/auth/cambiar-sucursal` que re-emite el token; `usuario_bodega` opcional (bodegas permitidas y por defecto) y validación en movimientos de stock; consultas de listados filtran por sucursales permitidas.
**Impacto técnico:** backend (servicio + llamadas en servicios de documentos), front (selector de sede en topbar que llama al endpoint).
**Riesgos:** usuarios hoy sin sucursal asignada (SUPER_ADMIN) → permiso de alcance total.
**Dependencias:** GAP-A-002, GAP-A-010.
**Pruebas:** integración: CAJERO de A crea venta en B → 403.
**Criterios de aceptación:** todos los servicios que reciben `sucursalId`/`bodegaId` pasan por `ScopeService`.

#### GAP-A-012 — Bitácora de auditoría transversal
**Estado:** MISSING · **Prioridad:** P1 · **Ya planeado:** `PLAN_SEGURIDAD.md` M-05 y 2.3 (`auditoria_acceso`).
**AURA actual:** solo bitácoras puntuales (V85, posting log, V73, error_log) y auditoría detectiva (`AuditoriaService`).
**Referencia funcional:** §7 `AuditoriaEvento`; checklist SEGURIDAD "auditoría".
**Problema:** no se sabe quién cambió un precio, un costo, un rol, un stock mínimo, quién aprobó un reconteo con faltante o desactivó un usuario.
**Decisión:** implementar bitácora de negocio (no solo de acceso).
**Diseño propuesto:** tabla `auditoria_evento(id, empresa_id, usuario_id, sucursal_id, entidad, entidad_id, accion, antes jsonb, despues jsonb, ip, correlation_id, created_at)` particionable por mes; `AuditoriaEventoService.registrar(...)` llamado explícitamente en los puntos sensibles (catálogo de acciones auditables) + listener JPA opcional para entidades maestras (producto, precio, usuario, rol, bodega, tercero). Escritura en la misma transacción. Pantalla de consulta con filtros y exportación.
**Impacto técnico:** BD, backend (servicio + ~30 puntos), front (consulta).
**Riesgos:** volumen → no auditar lecturas; retención configurable.
**Dependencias:** ninguna (mejor con GAP-A-010 para el permiso de consulta).
**Pruebas:** integración: cambiar precio → 1 evento con antes/después.
**Criterios de aceptación:** las acciones de la lista (≥ 20) generan evento; consulta por entidad y usuario.

#### GAP-A-013 — Reconteo: diferencia contra saldo congelado, costo cero, sin asiento
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `stock_sistema` se toma al **crear** el reconteo; al aprobar se aplica `contado − stock_sistema` al saldo **actual** (`ReconteoServiceImpl.java:190-205`): si hubo ventas entre crear y contar, se descuentan dos veces. Kardex con `costo_historico = 0` (`:233`). Si no existe fila de inventario, la línea se salta en silencio (`:198`). No publica evento contable (`grep` = 0). El creador puede aprobar su propio conteo.
**Referencia funcional:** §4.16 (sistema, físico, diferencia, costo, contabilización por motivo).
**Problema:** faltantes/sobrantes irreales y valor del ajuste en cero; el inventario contable no se entera.
**Decisión:** mejorar.
**Diseño propuesto:** capturar `stock_sistema` al **registrar el conteo de cada línea** (`actualizarDetalle`) y, al aprobar, aplicar `delta = contado − stock_sistema_al_contar` (o bien bloquear la bodega durante el conteo total); crear la fila de inventario si no existe (sobrante de producto nuevo en la bodega); costo = costo promedio (GAP-A-007); publicar `INVENTORY_ADJUSTMENT` con motivo (sobrante→ingreso/recuperación, faltante→gasto/pérdida, parametrizable); permiso `inventario.ajuste.aprobar` distinto de quien cuenta (GAP-A-010); mostrar valor total del ajuste antes de aprobar.
**Impacto técnico:** backend (`ReconteoServiceImpl`, evento, generador contable en el bloque contable), front (valor del ajuste, columna "stock al contar").
**Riesgos:** reconteos en curso durante el despliegue: aprobar o anular antes.
**Dependencias:** GAP-A-006, GAP-A-007; motor contable (otro bloque).
**Pruebas:** integración: crear reconteo con stock 10, vender 3, contar 7, aprobar → ajuste 0.
**Criterios de aceptación:** kardex del reconteo con costo > 0 y asiento cuadrado por el valor del ajuste.

#### GAP-A-014 — Prefijos y resoluciones por sucursal/tipo de documento/usuario
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** una resolución por empresa (`EmpresaEntity.java:111-137`), prefijo en sucursal (`SucursalEntity.java:42`), sin prefijos por tipo de documento ni por usuario.
**Referencia funcional:** video-01 min. 21-25: prefijos por sucursal y documento, prefijo por defecto y no editable por usuario.
**Problema:** una empresa con dos sedes que facturan con resoluciones distintas no puede operar; no hay control de vigencia ni de rango agotado a nivel de numerador.
**Decisión:** implementar con GAP-A-008.
**Diseño propuesto:** `resolucion(empresa_id, tipo_documento, prefijo, numero, desde, hasta, fecha_desde, fecha_hasta, proveedor_ref (Factus range id), activa)` + `numerador` apuntando a la resolución + `sucursal_documento_prefijo(sucursal_id, tipo_documento, numerador_id)` + override `usuario_prefijo`. Alertas de rango/vigencia por agotarse.
**Impacto técnico:** BD, backend (venta, FE, DS, nómina electrónica usan la resolución del numerador), front (pantalla de resoluciones).
**Riesgos:** alto acoplamiento con facturación electrónica → coordinar con el bloque fiscal.
**Dependencias:** GAP-A-008; bloque fiscal.
**Pruebas:** dos sucursales con prefijos distintos generan numeraciones independientes.
**Criterios de aceptación:** cada venta guarda `resolucion_id` y su número cae en el rango vigente.

#### GAP-A-015 — Reversos y devoluciones reingresan a costo actual
**Estado:** DIFFERENT · **Prioridad:** P1
**AURA actual:** `ContabilidadAutoServiceImpl.java:511` y `DevolucionServiceImpl.java:298,407,450`, `VentaServiceImpl.java:787,816` usan `producto.getCosto()` al revertir.
**Referencia funcional:** §5 matriz de efectos (NC devolución = reverso).
**Problema:** el reverso no es simétrico al movimiento original: el inventario y el costo de ventas quedan con diferencias cada vez que el costo cambió entre la venta y la devolución.
**Decisión:** mejorar.
**Diseño propuesto:** los reversos leen el `costo_unitario` del movimiento de kardex original (requiere GAP-A-016).
**Dependencias:** GAP-A-016, GAP-A-007.
**Pruebas:** vender a costo 100, comprar a 200, devolver → reingresa a 100.
**Criterios de aceptación:** asiento de devolución = negativo exacto del costo de la venta devuelta.

#### GAP-A-016 — Kardex sin enlace estructurado al documento ni convención de signo única
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `movimiento_inventario` guarda `tipo_movimiento`, `cantidad`, `saldo_anterior/nuevo`, `costo_historico`, `referencia_origen` (texto) (`MovimientoInventarioEntity.java:24-34`); sin `documento_tipo`, `documento_id`, `documento_linea_id`, `usuario_id`, `valor`; reconteo guarda `abs()` (`TipoMovimientoInventario.java:16-21`); `historialProducto` usa el id del movimiento como id del documento (`InventarioServiceImpl.java:160`).
**Referencia funcional:** §4.21, §4.25, §7.3 (trazabilidad de cruces).
**Problema:** no se puede navegar del kardex al documento ni hacer reversos simétricos (GAP-A-015) ni reportes de costo por documento; el signo depende de quién escribió.
**Decisión:** mejorar (aditivo).
**Diseño propuesto:** columnas `documento_tipo`, `documento_id`, `documento_detalle_id`, `usuario_id`, `costo_unitario`, `valor` (con signo), `cantidad_con_signo` generada = `saldo_nuevo − saldo_anterior`; backfill best-effort parseando `referencia_origen`; el servicio único de stock (GAP-A-006) llena todo.
**Dependencias:** GAP-A-006.
**Pruebas:** toda fila nueva tiene documento y signo coherente (`cantidad_con_signo = saldo_nuevo − saldo_anterior`).
**Criterios de aceptación:** desde el kardex se abre el documento origen.

#### GAP-A-017 — Límites operativos por usuario/rol (descuento, precio bajo costo, caja, bodega)
**Estado:** MISSING · **Prioridad:** P1
**AURA actual:** no hay topes de descuento ni alerta de venta bajo costo (`grep descuentoMax|precioMinimo` = 0). El precio que envía el front se acepta. Existen: rol que cierra caja (`TurnoCajaController.java:48`) y rol que autoriza retroactivos (`EmpresaEntity.rolAutorizaRetroactivo`).
**Referencia funcional:** §1.5, §3.3 (políticas de precio/descuento), baseline §7.5 "límites de descuento/caja/bodega/prefijo".
**Problema:** un cajero puede vender a cualquier precio o con cualquier descuento sin autorización.
**Decisión:** implementar respetando la decisión de producto: **un cambio de precio en el POS no es un descuento** (no convertirlo); se controla como cambio de precio con su propio límite.
**Diseño propuesto:** parámetros por rol/usuario: `descuento_max_pct`, `permite_cambio_precio`, `permite_bajo_costo`, `variacion_precio_max_pct`; validación en backend en `VentaServiceImpl` contra el precio calculado por `PrecioDinamicoService`; si excede → requiere autorización (PIN de supervisor, reutilizando el patrón de autorización retroactiva) y queda en auditoría.
**Dependencias:** GAP-A-010, GAP-A-012.
**Pruebas:** CAJERO con tope 10 % envía 15 % → 403 con código `REQUIERE_AUTORIZACION`.
**Criterios de aceptación:** toda venta con precio fuera de política tiene autorizador registrado.

#### GAP-A-032 — Pruebas automáticas del núcleo de inventario y autorización
**Estado:** MISSING · **Prioridad:** P1 (habilitante)
**AURA actual:** 40 clases de prueba, ninguna de stock/costo/traslado/reconteo/autorización; no hay `V13__baseline` y por eso no hay BD desde cero para Testcontainers (`docs/ESTADO.md` §3.1). ADR-004 define la pirámide.
**Problema:** los arreglos P0 (bloqueo, costo, reconteo, permisos) no se pueden verificar ni proteger de regresiones.
**Decisión:** implementar antes o junto con A1.
**Diseño propuesto:** pruebas unitarias del servicio de stock y la fórmula de costo (sin BD); integración con Testcontainers sobre un `schema.sql` generado con `pg_dump --schema-only` (solo esquema) de local `aura-pos`; matriz de autorización por rol.
**Dependencias:** esquema base (bloque transversal, ver `docs/ESTADO.md`).
**Criterios de aceptación:** pipeline ejecuta pruebas de concurrencia de stock y matriz de roles.

### P2 — Medio

#### GAP-A-019 — Doble fuente de verdad en precio, IVA y rol de tercero
**Estado:** PARTIAL · **Prioridad:** P2
**AURA actual:** precio en `producto.precio/precio_2/precio_3` **y** en `lista_precios/producto_precio`; IVA en `producto.iva_porcentaje` **y** `producto.impuesto_id` **y** `producto.impoconsumo` **y** `categoria.impuesto_defecto`; rol de tercero en `es_*` **y** `tercero_rol` (doble escritura).
**Problema:** divergencias silenciosas (la compra actualiza `precio_1..3`, la lista no).
**Decisión:** mejorar: declarar la fuente canónica y derivar la otra (vista o sincronización única) — `impuesto_id` canónico; `lista_precios` canónica con "lista base" = `precio`; `tercero_rol` canónico.
**Dependencias:** GAP-A-024.
**Criterios de aceptación:** una sola escritura por concepto; reporte de divergencias = 0.

#### GAP-A-020 — Terceros: múltiples direcciones y contactos
**Estado:** MISSING · **Prioridad:** P2 · `tercero_direccion(tipo, direccion, municipio_id, barrio, zona, principal)`, `tercero_contacto(nombre, cargo, telefono, email, recibe_fe, recibe_cobranza)`; documentos de venta/compra eligen dirección de entrega; cobranza usa el contacto de cobranza.

#### GAP-A-021 — Fusión de terceros duplicados
**Estado:** MISSING · **Prioridad:** P2 · Proceso "fusionar A en B": reasigna FK de documentos no electrónicos y asientos en períodos abiertos, deja A inactivo con `fusionado_en`, bloquea si A tiene documentos electrónicos emitidos o períodos cerrados (§1.15), audita todo. Requiere inventario de FKs a `tercero` y GAP-A-012.

#### GAP-A-022 — Condiciones comerciales en la ficha del tercero
**Estado:** PARTIAL · **Prioridad:** P2 · Agregar `lista_precios_id`, `vendedor_id` (usuario/empleado), `forma_pago_id` por defecto y `descuento_pct` general; `PrecioDinamicoService.calcularPrecio` usa la lista del cliente antes de la base. Definir y documentar el orden de combinación (precio cliente > lista cliente > volumen > regla > base; descuentos no acumulables salvo configuración) (§1.20).

#### GAP-A-023 — Historial 360 del tercero
**Estado:** PARTIAL · **Prioridad:** P2 · Vista única por tercero con pestañas: ventas, cotizaciones, pedidos, devoluciones, compras, NC/ND, recibos, egresos, cartera, obsequios, consumo, asientos; reutilizar las consultas existentes (estado de cuenta, ficha de cartera).

#### GAP-A-024 — Impuestos múltiples por producto
**Estado:** MISSING · **Prioridad:** P2 · `producto_impuesto(producto_id, impuesto_id, orden, base)` con tipos IVA, INC, bolsa (valor fijo por unidad), ultraprocesados/bebidas azucaradas (tarifa o valor por volumen); el cálculo de la línea itera impuestos. **Verificar normativa vigente antes de implementar tarifas** (fuente C). Coordinar con los bloques de ventas/fiscal.

#### GAP-A-025 — Máximo, punto de reorden y sugerido de compra
**Estado:** MISSING · **Prioridad:** P2 · `inventario.stock_maximo`, `punto_reorden`; reporte "bajo mínimo / sobre máximo / a reordenar" por bodega y **sugerido de orden de compra** (máximo − saldo − pendientes de OC) que crea una orden de compra borrador.

#### GAP-A-026 — Política de negativos tri-estado
**Estado:** PARTIAL · **Prioridad:** P2 · `politica_negativos` BLOQUEAR/ADVERTIR/PERMITIR en empresa (default), bodega y producto (override); reporte de saldos negativos con antigüedad; en ADVERTIR la venta pasa y queda alerta. Integrar en el servicio de stock (GAP-A-006).

#### GAP-A-027 — Entrada de almacén por motivo y motivos con cuenta
**Estado:** MISSING · **Prioridad:** P2 · Documento "Entrada de inventario" (saldo inicial, sobrante, obsequio recibido, producción manual) con motivo parametrizable (`motivo_inventario(tipo ENTRADA/SALIDA, cuenta_contable_id, afecta_costo, requiere_tercero)`); unificar `motivo_merma` hacia este catálogo (hoy solo `afecta_contabilidad`, sin cuenta). §4.10–4.11.

#### GAP-A-028 — Producción simplificada por lote
**Estado:** MISSING · **Prioridad:** P2 · Documento "Producción": elige receta y cantidad (usa `rendimiento_receta`), descarga componentes al costo promedio, carga el terminado al costo sumado (+ servicios externos opcionales), kardex `PRODUCCION_CONSUMO` / `PRODUCCION_ENTRADA`, evento contable materia prima → terminado. Mantener el *backflush* en venta como opción por producto (`modo_produccion` A_DEMANDA / POR_LOTE). Informe de costos de producto terminado (§4.28). "Producción por negativos" (§4.15) solo como asistente que propone, nunca automático.

#### GAP-A-029 — Importación masiva de productos y saldos iniciales de inventario
**Estado:** MISSING · **Prioridad:** P2 · **Ya planeado:** `PLAN_PRESENTACIONES_SIN_FRICCION.md` F6. Extender el patrón validar/confirmar de `ImportacionController` a productos (con presentaciones, categoría, impuesto, precios por lista) y a saldos iniciales por bodega (genera `SALDO_INICIAL` con costo, GAP-A-005).

#### GAP-A-030 — Credenciales de integración en texto plano
**Estado:** PARTIAL · **Prioridad:** P2 · Cifrar `factus_client_secret`, `factus_password`, tokens con `AESencryptUtil` (hoy sin usos) y `AES_KEY`; rotación documentada. Relacionado con `PLAN_SEGURIDAD.md` Fase 3.

#### GAP-A-031 — Menú por label normalizado y SQL de menú manual
**Estado:** PARTIAL · **Prioridad:** P2 · Filtrar por `codigo` de submódulo estable (no por label) y por permiso (GAP-A-010); sembrar submódulos por migración idempotente en vez de `docs/sql/menu_submodulo_*.sql` sueltos (varios pendientes de correr según la memoria del proyecto).

#### GAP-A-033 — Unicidad y borrado seguro en catálogo
**Estado:** PARTIAL · **Prioridad:** P2 · Validar SKU único por empresa (`ProductoServiceImpl` solo valida código de barras); impedir el soft-delete de un producto con saldo ≠ 0 o documentos abiertos (hoy `ProductoServiceImpl.java:201-208` lo borra igual); verificar índices únicos en BD de `tercero(empresa_id, numero_documento)` y `producto(empresa_id, sku)` (tablas pre-V14: UNKNOWN).

#### GAP-A-034 — Informes de inventario faltantes
**Estado:** PARTIAL · **Prioridad:** P2 · Consolidado de existencias por bodega/producto/lote/serial con filtros positivo/negativo/cero y **valorizado al promedio**; informe de precios (producto × listas × existencias, §4.26); informe de fichas técnicas (§4.23).

#### GAP-A-035 — Historial de precio y costo del producto
**Estado:** MISSING · **Prioridad:** P2 · Se obtiene como consulta de `auditoria_evento` (GAP-A-012) para cambios manuales + kardex para costo; pantalla en la ficha del producto.

#### GAP-A-018 — Bloqueo de cuenta y sesiones revocables
**Estado:** MISSING · **Prioridad:** P2 · **Ya planeado:** `PLAN_SEGURIDAD.md` 1.4 y 2.1. Se referencia; no se duplica diseño.

### P3 — Bajo

| GAP | Título | Estado | Resumen de diseño |
|---|---|---|---|
| GAP-A-036 | Variantes talla/color | MISSING | `producto_variante(atributos jsonb, sku, codigo_barras)`; el saldo pasa a (bodega, producto, variante). Solo si hay clientes de moda/calzado: impacta el modelo de saldo, evaluar antes. |
| GAP-A-037 | AIU en servicios | MISSING | Parámetros A/I/U por producto-servicio con cuenta por componente e IVA solo sobre U. Coordinar con facturación electrónica. |
| GAP-A-038 | Unidades por empresa y conversiones genéricas | PARTIAL | Unidades de sistema (con código DIAN) + propias; conversiones entre unidades (rollo→metros) más allá de presentaciones, solo si se necesita. |
| GAP-A-039 | Usuario en varias empresas | MISSING | `usuario_empresa` + selector de empresa que re-emite el token; útil para contadores externos. |
| GAP-A-040 | Creación rápida de tercero desde NIT (DIAN/RUES) | MISSING | Consulta a proveedor externo (Factus u otro) para precargar razón social/DV; no sustituye la ficha. |
| GAP-A-041 | Traslado en tránsito con recepción | MISSING | Estados DESPACHADO → RECIBIDO (con diferencias) para traslados entre sucursales lejanas; bodega virtual "en tránsito". |
| GAP-A-042 | Favoritos del POS y campos personalizados | PARTIAL | Favoritos por usuario/sucursal; campos personalizados en tercero y producto (`jsonb` + definición). |
| GAP-A-043 | Documentación de bodegas | PARTIAL | `V172` cita `docs/PLAN_BODEGAS.md`, que no existe en `docs/`. Escribirlo (modelo, `BodegaResolver`, reglas de principal). |

> Nota de numeración: GAP-A-009 quedó integrado en GAP-A-003 (rotación de `JWT_SECRET`, `PLAN_SEGURIDAD.md` 5.2) para no duplicar el plan existente.

---

## 6. Fortalezas (conservar)

| Fortaleza | Por qué es mejor que la referencia | Evidencia |
|---|---|---|
| Tenant siempre desde el token | Elimina la clase entera de IDOR por parámetro (salvo los dos casos hallados, GAP-A-002/004) | `SecurityUtils.getEmpresaId()` |
| Usuario separado de tercero y empleado | WO obliga a que el usuario sea empleado; AURA no | `UsuarioEntity` |
| Saldo por bodega con `sucursal_id` derivado | Migración no destructiva; reportes por sucursal intactos | `$M/V172__bodegas.sql` |
| Bodegas que no venden (averías, cuarentena), responsable, principal única por índice parcial | Más expresivo que "bodega principal" de WO | `V172:30-60` |
| Borrado protegido de bodegas y seriales con movimientos | Reversión antes que borrado | `BodegaServiceImpl.java:155-170`; `SerialProductoServiceImpl.java:146-156` |
| Lotes FEFO que cuadran con el inventario; bloqueo de vencidos configurable; alertas | La referencia solo muestra lote como atributo | `LoteStockService`; `EmpresaEntity.lotesBloquearVencidos` |
| Seriales de punta a punta con garantía y documento de salida | Trazabilidad por serial | `SerialProductoEntity` |
| Consumo interno con concepto → cuenta + IVA + responsable; obsequio con IVA | Separa pérdida, consumo y regalo con su efecto fiscal | `$M/V163`; `ObsequioGenerador` |
| Recetas con % de merma, rendimiento, unidad/presentación, costeo y duplicado | Más completa que la ficha técnica observada | `ProductoComposicionEntity`; `$M/V138` |
| Presentaciones "contiene N" + herramienta "pasar a unidad" con vista previa | Resuelve conversiones sin que el usuario sepa qué es un factor | `PLAN_PRESENTACIONES_SIN_FRICCION.md` F1–F4 |
| Precios dinámicos: por cliente con vigencia, por volumen, reglas por día/hora | Supera listas + descuento por cantidad | `PrecioClienteEntity`, `PrecioVolumenEntity`, `ReglaDescuentoEntity` |
| Control de crédito con estados, riesgo, score, reglas y autorización | Más completo que "cupo" | `TerceroCreditoEntity`; `$M/V169` |
| Catálogo único de tipos de kardex expuesto por API | Front y back ya no divergen | `$B/utils/TipoMovimientoInventario.java` |
| Auditoría detectiva (kardex vs stock, signo, descuadres) | Control que WO no muestra | `AuditoriaService`, `AuditoriaQueryRepository` |
| Control de documentos retroactivos con autorización y motivo | Protege arqueos cerrados | `ControlFechaRetroactivaServiceImpl` |
| Rate limit, Swagger apagado, secretos en variables | Fase 0 de seguridad hecha | `PLAN_SEGURIDAD.md` §4 |

---

## 7. No aplicables / fuera de este bloque

| Capacidad | Estado | Motivo |
|---|---|---|
| Nómina, asistencia, turnos, PILA, prestaciones, nómina electrónica | NOT_APPLICABLE / BETTER_THAN_REFERENCE | Fuera del baseline; AURA tiene un módulo completo (V67–V135). No auditado a fondo |
| Proyectos, frentes, asistencia por obra | BETTER_THAN_REFERENCE | Fuera del baseline |
| Rol "API" de WO | NOT_APPLICABLE (hoy) | No hay integraciones entrantes de terceros; si aparecen, usar credenciales de servicio con permisos (GAP-A-010), no un rol genérico |
| Clasificaciones "diferido", "dotación", "intangible" en el maestro de productos | DIFFERENT | AURA las maneja en módulos contables/gastos (bloque contable) |
| Ficha y depreciación de activos fijos (§1.27–1.28) | Otro bloque | Solo se registró que el activo vive fuera del catálogo de productos |
| Motor de contabilización, plan de cuentas, formas de pago (§1.8–1.14) | Otro bloque | Este bloque solo emite los eventos de inventario que ese motor consume |
| Obligación "todo usuario es empleado" | NOT_APPLICABLE | Decisión de AURA superior (§1.1 lo permite) |

---

## 8. Propuesta de fases del bloque

Las fases se ordenan por dependencia, no por tamaño. Numeración sugerida de migraciones desde **V184**, cada una con espejo idempotente en Laravel (`aura-pos-migracion-old`) y **sin tocar prod** sin respaldo.

```
A0 Contención de seguridad ──┬──> A3 Autorización fina y auditoría ──> A4 Maestros
                             │                                          (prefijos, terceros, impuestos, import)
A1 Integridad de stock ──────┴──> A2 Costeo y trazabilidad ──> A5 Inventario avanzado (reorden, negativos,
 (bloqueo, stock por documento,                                   entradas por motivo, producción, informes)
  reconteo, consecutivos, pruebas)                                                    │
                                                                                      v
                                                                         A6 Especializaciones (P3)
```

| Fase | Objetivo | Gaps | Depende de | Entregable verificable |
|---|---|---|---|---|
| **A0** Contención de seguridad | Cerrar escaladas e IDOR y poner la red de autorización por URL | 001, 002, 003 (paso 1 + rotación JWT), 004 | — | Matriz curl por rol en verde; diagnósticos SQL de roles y sucursales cruzadas = 0 |
| **A1** Integridad de stock | Que el saldo solo cambie por documentos, sin carreras | 006, 005, 008 (paso 1), 013 (parte stock), 032 | A0 (recomendado, no bloqueante) | Prueba de concurrencia verde; `kardexVsStock` sin hallazgos nuevos; consecutivos únicos |
| **A2** Costeo y trazabilidad | Costo promedio ponderado y kardex enlazado | 016, 007, 015, 013 (costo + evento contable) | A1; bloque contable (eventos `COST_OF_SALES`, `INVENTORY_ADJUSTMENT`) | Inventario valorizado = saldo 1435 en ciclo de prueba |
| **A3** Autorización fina y auditoría | RBAC por acción, scope sucursal/bodega, límites, bitácora | 010, 011, 012, 017, 031, 018 | A0 | Rol personalizado funcional; toda acción sensible auditada |
| **A4** Maestros | Numeración/resoluciones, terceros completos, impuestos, importación | 014, 008 (paso 2), 019, 020, 021, 022, 023, 024, 029, 030, 033, 035 | A1 (numerador), A3 (auditoría para fusión); bloque fiscal para 014/024 | Dos sedes con resoluciones propias; fusión auditada; productos importados |
| **A5** Inventario avanzado | Reorden, negativos, entradas por motivo, producción por lote, informes | 025, 026, 027, 028, 034 | A2 | Sugerido de compra; producción con costo real |
| **A6** Especializaciones | Variantes, AIU, multiempresa por usuario, tránsito, favoritos | 036–043 | según caso | Bajo demanda de clientes |

---

## 9. Tareas

### 9.1 Tareas P0 y P1 (formato completo)

## TASK-A-001 — Lista blanca de roles asignables y bloqueo de escalada

**Épica:** A0 Contención de seguridad
**Módulo:** Seguridad / Usuarios
**Tipo:** Backend / QA
**Prioridad:** P0
**Estado:** Ready

### Problema
Cualquier ADMIN crea o edita usuarios con rol `PLATFORM_ADMIN` o `SUPER_ADMIN`.

### Evidencia AS-IS
- Archivo: `$B/dto/usuarios/CreateUsuarioDto.java:30`, `UpdateUsuarioDto.java:22`, `$B/mappers/UsuarioMapper.java:16-36`
- Clase/componente: `UsuarioServiceImpl.crear/actualizar`, `UsuarioController`
- Tabla: `usuario.rol`
- Endpoint: `POST /api/usuarios/create`, `PUT /api/usuarios/{id}`, `POST /api/usuarios/create-from-empleado`
- Comportamiento: el rol llega del cliente y se persiste tal cual.

### Referencia funcional
§1.2 roles como catálogo administrado.

### Decisión
Implementar.

### Diseño propuesto
`RolesAsignables` (enum/constante) con `ADMIN, SUPERVISOR, CAJERO, VENDEDOR`; `SUPER_ADMIN` solo asignable por `SUPER_ADMIN`; `PLATFORM_ADMIN` nunca desde `/api/usuarios`. Ignorar `rol` en `updateEntityFromDto`; método explícito para cambiar rol con la misma validación. En `create-from-empleado`, mapear cargo → rol de la lista (o CAJERO por defecto), no el nombre del cargo.

### Backend
Validador en `UsuarioServiceImpl`; `@Mapping(target="rol", ignore=true)` en update; excepción 403 con mensaje claro.

### Frontend
Sin cambio obligatorio (el dropdown ya ofrece ADMIN/CAJERO/SUPERVISOR); agregar VENDEDOR si corresponde.

### Base de datos
Ninguna. Diagnóstico previo: `SELECT empresa_id, rol, count(*) FROM usuario GROUP BY 1,2;`

### API
Mismos endpoints; nuevo código de error `ROL_NO_ASIGNABLE`.

### Seguridad y permisos
Es el control en sí.

### Inventario/contabilidad
N/A.

### Auditoría
Registrar cambio de rol cuando exista GAP-A-012.

### Migración de datos
Corregir a mano roles no estándar encontrados en el diagnóstico (decisión del negocio).

### Pruebas unitarias
Validador: matriz (rol que asigna × rol asignado).

### Pruebas integración
ADMIN → crea `PLATFORM_ADMIN` = 403; ADMIN → crea `SUPER_ADMIN` = 403; SUPER_ADMIN → crea ADMIN = 201.

### Pruebas E2E
Crear cajero desde la pantalla de usuarios sigue funcionando.

### Dependencias
Ninguna.

### Riesgos
Usuarios existentes con rol = nombre de cargo pierden menús si se normalizan sin revisar.

### Criterios de aceptación
- [ ] No es posible persistir `PLATFORM_ADMIN` desde endpoints de empresa.
- [ ] Solo `SUPER_ADMIN` asigna `SUPER_ADMIN`.
- [ ] Diagnóstico de roles en prod revisado.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas (N/A)
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada (`PLAN_SEGURIDAD.md`: nuevo hallazgo C-04)

---

## TASK-A-002 — Validar empresa en asignación de sucursales y en el token

**Épica:** A0 Contención de seguridad
**Módulo:** Seguridad / Sucursales
**Tipo:** Backend / QA
**Prioridad:** P0
**Estado:** Ready

### Problema
Un usuario puede quedar asignado a una sucursal de otra empresa.

### Evidencia AS-IS
- Archivo: `$B/services/implementations/UsuarioServiceImpl.java:185,277`
- Clase/componente: `asignarSucursales`, `asignarSucursalesDesdeCreateEmpleado`
- Tabla: `usuario_sucursal`
- Endpoint: `POST /api/usuarios/create`, `PUT /api/usuarios/{id}`
- Comportamiento: `findById` sin empresa; el login pone la sucursal en el JWT.

### Referencia funcional
§1.5.

### Decisión
Implementar.

### Diseño propuesto
`sucursalRepo.findByIdAndEmpresaId`; en `JwtAuthenticationFilter`, si `sucursalId` del token no pertenece a `empresaId`, 401.

### Backend
2 métodos + verificación en filtro (cacheable).

### Frontend
N/A.

### Base de datos
Diagnóstico: `SELECT us.* FROM usuario_sucursal us JOIN usuario u ON u.id=us.usuario_id JOIN sucursal s ON s.id=us.sucursal_id WHERE u.empresa_id<>s.empresa_id;`

### API
N/A.

### Seguridad y permisos
Cierra IDOR entre tenants.

### Inventario/contabilidad
N/A.

### Auditoría
N/A (evento cuando exista GAP-A-012).

### Migración de datos
Desactivar filas cruzadas si el diagnóstico encuentra alguna.

### Pruebas unitarias
N/A.

### Pruebas integración
Asignar sucursal de otra empresa → 404.

### Pruebas E2E
Alta de usuario con sucursales propias sigue funcionando.

### Dependencias
Ninguna.

### Riesgos
Bajo.

### Criterios de aceptación
- [ ] Diagnóstico = 0 filas.
- [ ] Prueba de integración verde.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas (N/A)
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-003 — Red de autorización por URL y seguridad de método activa

**Épica:** A0 Contención de seguridad
**Módulo:** Seguridad
**Tipo:** Backend / QA / DevOps
**Prioridad:** P0
**Estado:** Ready (ya diseñado en `PLAN_SEGURIDAD.md` 1.1 y 1.3)

### Problema
767 endpoints sin control de rol; `PermisoInterceptor` sin registrar; `@PreAuthorize` inerte; `JWT_SECRET` sin rotar.

### Evidencia AS-IS
- Archivo: `$B/config/SecurityConfig.java:50-62`, `$B/config/WebConfig.java`, `$B/config/PermisoInterceptor.java`
- Clase/componente: 6 `@PreAuthorize("hasRole(...)")`
- Tabla: `empresa_submodulo`
- Endpoint: todos
- Comportamiento: `anyRequest().authenticated()`.

### Referencia funcional
§1.3–1.4.

### Decisión
Implementar paso 1 (contención); el modelo fino es TASK-A-010.

### Diseño propuesto
1. `@EnableMethodSecurity` + `hasRole`→`hasAuthority` en el mismo commit.
2. Reglas por URL para escritura en inventario, productos, precios, bodegas, sucursales, usuarios, reconteo (aprobar), traslado (anular), unidades de medida (solo plataforma), y `DELETE /api/**` → ADMIN/SUPER_ADMIN.
3. Registrar `PermisoInterceptor` en `WebConfig.addInterceptors` y anotar controladores con `@RequerirPermiso(modulo, submodulo)` para que un módulo desactivado para la empresa también se niegue en backend.
4. Rotar `JWT_SECRET` (PLAN_SEGURIDAD 5.2) en ventana de bajo tráfico.

### Backend
`SecurityConfig`, `WebConfig`, anotaciones.

### Frontend
Manejo de 403 con mensaje (toast) en el interceptor HTTP.

### Base de datos
N/A.

### API
Respuestas 403 nuevas en escritura para roles no admin.

### Seguridad y permisos
Mapa endpoint→rol mínimo documentado en `PLAN_SEGURIDAD.md`.

### Inventario/contabilidad
N/A.

### Auditoría
Log de 403 (con usuario y ruta).

### Migración de datos
N/A.

### Pruebas unitarias
N/A.

### Pruebas integración
`@SpringBootTest` + `@WithMockUser(authorities="CAJERO")` sobre cada grupo de URL.

### Pruebas E2E
Recorrido del POS, caja y ventas con usuario CAJERO y VENDEDOR sin 403 inesperados.

### Dependencias
Ninguna.

### Riesgos
Pantallas de cajero que escriben en rutas "de admin" (p. ej. crear tercero desde POS): relevar con el front antes de activar.

### Criterios de aceptación
- [ ] Checklist curl de `PLAN_SEGURIDAD.md` §6 en verde.
- [ ] POS, caja y cierre de turno operan con CAJERO.
- [ ] `JWT_SECRET` rotado.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas (N/A)
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-004 — Proteger el catálogo global de unidades de medida

**Épica:** A0 Contención de seguridad
**Módulo:** Catálogo
**Tipo:** Backend
**Prioridad:** P0
**Estado:** Ready

### Problema
Cualquier usuario de cualquier empresa modifica o desactiva unidades compartidas.

### Evidencia AS-IS
- Archivo: `$B/controllers/UnidadMedidaController.java:57,65`; `$B/services/implementations/UnidadMedidaServiceImpl.java:64-84`
- Tabla: `unidad_medida` (sin `empresa_id`)
- Endpoint: `PUT/DELETE /api/unidades-medida/{id}` (y creación)

### Referencia funcional
§1.22.

### Decisión
Implementar.

### Diseño propuesto
Regla de URL: escritura en `/api/unidades-medida/**` solo `PLATFORM_ADMIN`. Ocultar botones de edición en el front para el resto. Diseño de unidades propias por empresa queda en GAP-A-038.

### Backend
`SecurityConfig` (1 regla).

### Frontend
Ocultar crear/editar/eliminar salvo plataforma.

### Base de datos
N/A.

### API
403 para no plataforma.

### Seguridad y permisos
Cierra escritura cross-tenant.

### Inventario/contabilidad
N/A.

### Auditoría
N/A.

### Migración de datos
Revisar si alguna empresa ya alteró unidades (comparar con el catálogo semilla).

### Pruebas unitarias
N/A.

### Pruebas integración
ADMIN de empresa → `PUT` = 403.

### Pruebas E2E
Listado de unidades sigue visible en producto.

### Dependencias
TASK-A-003 (mismo archivo; puede ir en el mismo commit).

### Riesgos
Ninguno.

### Criterios de aceptación
- [ ] Solo plataforma modifica unidades.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas (N/A)
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-005 — Servicio único de movimiento de stock con bloqueo

**Épica:** A1 Integridad de stock
**Módulo:** Inventario
**Tipo:** Backend / QA
**Prioridad:** P0
**Estado:** Proposed

### Problema
Actualizaciones de stock perdidas en concurrencia; kardex escrito por cada servicio con convenciones distintas.

### Evidencia AS-IS
- Archivo: `VentaServiceImpl.java:384-484,776-808`; `CompraServiceImpl.java:571-585,1364-1369`; `ReconteoServiceImpl.java:194-205`; `TrasladoServiceImpl`, `MermaServiceImpl`, `ObsequioServiceImpl`, `ConsumoInternoServiceImpl`, `DevolucionServiceImpl`; `LoteStockService`
- Clase/componente: `InventarioJPARepository` (sin `@Lock`)
- Tabla: `inventario`, `movimiento_inventario`, `lote`
- Comportamiento: leer → sumar en Java → `save`.

### Referencia funcional
Checklist INVENTARIO "concurrencia"; §7 SaldoInventario.

### Decisión
Refactorizar por estrangulamiento (ADR-001): un servicio nuevo que los documentos adoptan uno a uno.

### Diseño propuesto
`InventarioMovimientoService.aplicar(List<Linea>)`: ordena por `(bodega_id, producto_id)`, bloquea con `findForUpdate` (`@Lock(PESSIMISTIC_WRITE)`), crea la fila de inventario si no existe (con `ON CONFLICT` para la carrera de creación), valida política de negativos, actualiza saldo, delega lotes/seriales, escribe kardex (tipo, documento, signo, costo). Orden de adopción: venta → compra → devolución → traslado → merma/obsequio/consumo → reconteo.

### Backend
Nuevo servicio + método de repositorio con bloqueo; cambios en 9 servicios.

### Frontend
N/A.

### Base de datos
Ninguna obligatoria (índice único `(bodega_id, producto_id)` ya existe por V172).

### API
N/A.

### Seguridad y permisos
N/A.

### Inventario/contabilidad
Base para costo promedio (TASK-A-008) y kardex estructurado (TASK-A-011).

### Auditoría
`usuario_id` en kardex cuando exista la columna (TASK-A-011).

### Migración de datos
N/A.

### Pruebas unitarias
Validación de negativos y orden de bloqueo.

### Pruebas integración
20 ventas concurrentes de 1 und sobre stock 10 → 10 OK, 10 rechazadas, saldo 0, 10 kardex. Venta + compra concurrentes → saldo exacto.

### Pruebas E2E
Dos navegadores vendiendo el mismo producto.

### Dependencias
TASK-A-012 (pruebas/esquema para Testcontainers).

### Riesgos
Interbloqueos (mitigado por orden); regresión en recetas y lotes.

### Criterios de aceptación
- [ ] Ningún servicio actualiza `inventario.stock_actual` fuera del servicio único (`grep setStockActual` solo en él y en `CambioUnidadProductoRepository`).
- [ ] Prueba de concurrencia verde.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-006 — El stock solo cambia por documentos (quitar edición directa)

**Épica:** A1 Integridad de stock
**Módulo:** Inventario
**Tipo:** Backend / Frontend / QA
**Prioridad:** P0
**Estado:** Ready

### Problema
El stock se edita a mano sin kardex ni asiento.

### Evidencia AS-IS
- Archivo: `$B/services/implementations/InventarioServiceImpl.java:83-138`; `$B/dto/inventario/UpdateInventarioDto.java:12`; `$F/features/inventario/inventario/form/form-inventario.component.html:108-116`
- Endpoint: `POST /api/inventario/create`, `PUT /api/inventario/{id}`

### Referencia funcional
§4.16.

### Decisión
Refactorizar.

### Diseño propuesto
`actualizar` ignora `stockActual` (solo mínimo/ubicación/máximo). `crear` con stock inicial > 0 escribe kardex `SALDO_INICIAL` (nuevo tipo en `TipoMovimientoInventario`) al costo del producto y publica `INVENTORY_OPENING` (contrapartida parametrizable en el motor contable; si el bloque contable no está listo, solo kardex y queda registrado como pendiente de contabilizar). En el front, stock de solo lectura en edición + botón "Ajustar existencia" que abre un reconteo parcial de ese producto.

### Backend
`InventarioServiceImpl`, `TipoMovimientoInventario`, evento.

### Frontend
`form-inventario` (campo deshabilitado en edición; texto de ayuda corregido: el hint actual del stock repite el del mínimo).

### Base de datos
Ninguna.

### API
`UpdateInventarioDto.stockActual` deprecado (ignorado con advertencia en log).

### Seguridad y permisos
`inventario.ajuste.crear` cuando exista TASK-A-010.

### Inventario/contabilidad
Kardex `SALDO_INICIAL`; evento contable.

### Auditoría
Kardex con usuario.

### Migración de datos
Ninguna; `kardexVsStock` del reporte gerencial sirve para listar las divergencias históricas y resolverlas con un reconteo.

### Pruebas unitarias
N/A.

### Pruebas integración
`PUT` con `stockActual` no cambia saldo; `crear` con 10 → kardex de +10.

### Pruebas E2E
Crear inventario de producto nuevo con stock inicial; intentar editar stock (deshabilitado).

### Dependencias
TASK-A-005 (usar el servicio único).

### Riesgos
Cambio de hábito del usuario → ayuda en `/ayuda` (manual de usuario) actualizada.

### Criterios de aceptación
- [ ] No hay forma de cambiar `stock_actual` sin fila de kardex.
- [ ] Manual de ayuda actualizado.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-007 — Numerador transaccional de consecutivos

**Épica:** A1 Integridad de stock (paso 1) / A4 Maestros (paso 2)
**Módulo:** Empresa / Documentos
**Tipo:** DB / Backend / QA
**Prioridad:** P0
**Estado:** Proposed

### Problema
Consecutivos `MAX()+1` sin bloqueo.

### Evidencia AS-IS
- Archivo: `VentaQueryRepository.java:124-130`, `VentaServiceImpl.java:284-289`, `FacturaQueryRepository.java:41`, `CotizacionQueryRepository.java:89`, `ReciboCajaQueryRepository.java:33`, `AcuerdoPagoQueryRepository.java:36`
- Tabla: `venta`, `factura`, `cotizacion`, `recibo_caja`, `acuerdo_pago`

### Referencia funcional
§1.5; video-01 min. 21-25.

### Decisión
Implementar.

### Diseño propuesto
Paso 1 (P0): tabla `numerador(id, empresa_id, sucursal_id, tipo_documento, prefijo, siguiente, …)` + `NumeradorService.siguiente(...)` con `UPDATE … RETURNING` dentro de la transacción del documento; backfill `siguiente = MAX+1` por (sucursal, tipo). Índice único `(sucursal_id, prefijo, consecutivo)` en `venta` tras diagnóstico. Paso 2 (P1, TASK-A-014): vínculo a resolución y rangos.

### Backend
`NumeradorService`; reemplazar 6 llamadas.

### Frontend
N/A.

### Base de datos
`V18x__numerador.sql` + espejo Laravel idempotente; índice único condicionado a diagnóstico sin duplicados.

### API
N/A.

### Seguridad y permisos
N/A.

### Inventario/contabilidad
N/A.

### Auditoría
N/A.

### Migración de datos
Diagnóstico de duplicados (solo lectura en prod) y plan de corrección si los hay.

### Pruebas unitarias
N/A.

### Pruebas integración
50 ventas concurrentes → 50 números distintos.

### Pruebas E2E
Venta POS muestra el número correcto.

### Dependencias
Ninguna (paso 1).

### Riesgos
Si hay duplicados históricos, el índice único no se puede crear sin corregirlos.

### Criterios de aceptación
- [ ] Prueba concurrente verde.
- [ ] Índice único creado en local y plan para prod.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-008 — Costo promedio ponderado por empresa y producto

**Épica:** A2 Costeo y trazabilidad
**Módulo:** Inventario / Contabilidad
**Tipo:** DB / Backend / QA
**Prioridad:** P0
**Estado:** Proposed

### Problema
Costo = último costo de compra; costo de ventas e inventario valorizado incorrectos.

### Evidencia AS-IS
- Archivo: `CompraServiceImpl.java:1545-1551`; lectores de `producto.getCosto()` (ver GAP-A-007)
- Tabla: `producto.costo`, `movimiento_inventario.costo_historico`

### Referencia funcional
video-04 (costo promedio en entradas/salidas y en producción).

### Decisión
Implementar.

### Diseño propuesto
`producto.costo_promedio NUMERIC(18,6)` (y `producto.costo` = último costo). Fórmula móvil sobre el saldo de **todas las bodegas** de la empresa, calculada en `InventarioMovimientoService` bajo bloqueo del producto. Entradas valorizadas: compra (neto de descuento, en unidad base), saldo inicial, entrada por motivo, producción. Salidas/traslados: al promedio vigente. Reversos: al costo del movimiento original (TASK-A-011). Saldo ≤ 0: conservar último promedio y marcar el producto para revisión. El motor contable lee el `valor` del kardex para `COST_OF_SALES`.

### Backend
Servicio de stock, compra, venta, devolución, reportes de utilidad.

### Frontend
Ficha de producto muestra "Costo promedio" y "Último costo".

### Base de datos
Migración con `costo_promedio = costo` inicial + espejo Laravel.

### API
DTOs de producto y kardex exponen ambos costos.

### Seguridad y permisos
`inventario.costo.ver` (TASK-A-010).

### Inventario/contabilidad
Evento `COST_OF_SALES` con valor del kardex (coordinar con bloque contable).

### Auditoría
Kardex con costo unitario y valor.

### Migración de datos
Sin recálculo histórico por defecto; herramienta opcional de recálculo por producto desde una fecha.

### Pruebas unitarias
Fórmula (casos: compra-compra-venta, saldo cero, presentaciones, NC de compra).

### Pruebas integración
Ciclo compra 10×100, compra 10×200, venta 5, devolución 1 → promedio 150; costo venta 750; devolución 150.

### Pruebas E2E
Utilidad del reporte de ventas coincide con el cálculo manual.

### Dependencias
TASK-A-005, TASK-A-011; bloque contable.

### Riesgos
Cambio de cifras de utilidad visibles para el cliente → comunicar la fecha de corte.

### Criterios de aceptación
- [ ] Σ(saldo × promedio) por categoría contable = saldo 1435 en el ciclo de prueba.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-009 — Reconteo correcto: saldo al contar, costo real y asiento

**Épica:** A1/A2
**Módulo:** Inventario
**Tipo:** Backend / Frontend / QA
**Prioridad:** P1
**Estado:** Proposed

### Problema
Diferencias aplicadas contra saldo congelado al crear, costo cero, sin asiento, líneas sin inventario ignoradas, sin segregación.

### Evidencia AS-IS
- Archivo: `$B/services/implementations/ReconteoServiceImpl.java:92-246`
- Tabla: `reconteos`, `reconteo_detalle`
- Endpoint: `/api/reconteos/*`

### Referencia funcional
§4.16.

### Decisión
Mejorar.

### Diseño propuesto
`reconteo_detalle.stock_al_contar` fijado en `actualizarDetalle`; al aprobar `delta = contado − stock_al_contar`; crear fila de inventario si falta; costo promedio; evento `INVENTORY_ADJUSTMENT` con motivo (sobrante/faltante) → cuentas parametrizadas; aprobador ≠ creador salvo permiso explícito; vista previa con valor total.

### Backend
`ReconteoServiceImpl`, entidad, evento.

### Frontend
Columnas "Stock al contar", "Diferencia", "Valor"; total a aprobar.

### Base de datos
`ALTER TABLE reconteo_detalle ADD COLUMN IF NOT EXISTS stock_al_contar NUMERIC(18,6)` + espejo.

### API
Respuesta con valores.

### Seguridad y permisos
`inventario.ajuste.aprobar`.

### Inventario/contabilidad
Kardex con costo; asiento por motivo.

### Auditoría
Aprobador y valor en bitácora.

### Migración de datos
Reconteos abiertos: aprobar o anular antes del despliegue.

### Pruebas unitarias
Cálculo del delta.

### Pruebas integración
Crear (10) → vender 3 → contar 7 → aprobar → ajuste 0.

### Pruebas E2E
Reconteo parcial de un producto desde "Ajustar existencia".

### Dependencias
TASK-A-005, TASK-A-008; bloque contable.

### Riesgos
Diferencias de redondeo en productos pesables (6 decimales, V156).

### Criterios de aceptación
- [ ] Caso de ventas durante el conteo sin doble descuento.
- [ ] Asiento cuadrado por el valor del ajuste.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-010 — RBAC por acción con overrides por usuario

**Épica:** A3 Autorización fina y auditoría
**Módulo:** Seguridad
**Tipo:** DB / Backend / Frontend / QA
**Prioridad:** P1
**Estado:** Proposed

### Problema
Sin roles configurables ni permisos por acción; control solo en el front.

### Evidencia AS-IS
- Archivo: `UsuarioEntity.rol` (string); `PermisoServiceImpl.java:130`; `$F/layout/sidebar/sidebar.config.ts`; `$F/app.routes.ts`
- Tabla: `usuario`, `modulos`, `submodulos`, `empresa_modulo`, `empresa_submodulo`

### Referencia funcional
§1.2–1.5, §1.7.

### Decisión
Implementar.

### Diseño propuesto
Ver GAP-A-010 (tablas `rol`, `permiso`, `rol_permiso`, `usuario_permiso`; evaluador cacheado; permisos no van en el JWT; roles de sistema equivalentes a los actuales). Permisos por campo (§1.4) se modelan como acciones de visibilidad (`productos.costo.ver`, `ventas.precio.editar`) y no como un motor genérico de formularios.

### Backend
Entidades, `PermisoEvaluator` (`@perm.tiene`), `GET /api/me/permisos`, CRUD de roles, anotaciones por controlador (por lotes: inventario, productos, precios, terceros, usuarios, sucursales/bodegas).

### Frontend
`PermisoService`, directiva `*appPermiso`, `permisoGuard` (reemplaza `rolGuard` gradualmente), pantalla "Roles y permisos" (una card, estándar UI de AURA).

### Base de datos
4 tablas + semilla de permisos y roles de sistema + `usuario.rol_id` (backfill desde `usuario.rol`).

### API
Nuevos endpoints `/api/roles`, `/api/me/permisos`.

### Seguridad y permisos
Regla: permiso fino requiere módulo activo en la empresa.

### Inventario/contabilidad
Permisos `inventario.costo.ver`, `inventario.ajuste.aprobar`, `inventario.traslado.anular`, `productos.precio.editar`.

### Auditoría
Cambios de rol/permiso auditados (TASK-A-013).

### Migración de datos
Backfill `rol_id`; roles no estándar → rol personalizado con los permisos del rol más cercano.

### Pruebas unitarias
Evaluador (ALLOW/DENY/herencia/módulo inactivo).

### Pruebas integración
Por endpoint crítico × rol.

### Pruebas E2E
Crear rol "Bodeguero" con traslados y reconteo sin costos; verificar menú y API.

### Dependencias
TASK-A-003.

### Riesgos
Alcance grande: entregar por módulos; mantener `rolGuard` mientras convive.

### Criterios de aceptación
- [ ] Rol personalizado funcional en API y menú.
- [ ] Roles de sistema sin cambios de comportamiento.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-011 — Kardex estructurado (documento, usuario, costo, valor, signo)

**Épica:** A2 Costeo y trazabilidad
**Módulo:** Inventario
**Tipo:** DB / Backend / QA
**Prioridad:** P1
**Estado:** Proposed

### Problema
Kardex enlazado por texto y con signo no uniforme.

### Evidencia AS-IS
- Archivo: `$B/entity/MovimientoInventarioEntity.java:24-34`; `TipoMovimientoInventario.java:16-21`; `InventarioServiceImpl.java:160`
- Tabla: `movimiento_inventario`

### Referencia funcional
§4.21, §4.25, §7.3.

### Decisión
Mejorar (aditivo).

### Diseño propuesto
Columnas `documento_tipo`, `documento_id`, `documento_detalle_id`, `usuario_id`, `costo_unitario`, `valor`, `delta` (= `saldo_nuevo − saldo_anterior`, persistida); índices `(documento_tipo, documento_id)`. Backfill de `delta` exacto; `documento_*` best-effort desde `referencia_origen`.

### Backend
Servicio de stock las llena; reportes de kardex usan `delta` y `valor`.

### Frontend
Kardex con enlace al documento.

### Base de datos
Migración + espejo Laravel (cuidar el gotcha de `ADD COLUMN IF NOT EXISTS` con tipos).

### API
DTO de kardex con documento.

### Seguridad y permisos
Valor visible solo con `inventario.costo.ver`.

### Inventario/contabilidad
Fuente para costo de ventas y reversos simétricos (GAP-A-015).

### Auditoría
`usuario_id`.

### Migración de datos
Backfill de `delta` para todas las filas.

### Pruebas unitarias
N/A.

### Pruebas integración
Cada tipo de documento escribe documento y `delta` coherente.

### Pruebas E2E
Clic del kardex al documento.

### Dependencias
TASK-A-005.

### Riesgos
Tamaño de la tabla en el backfill (hacer por lotes).

### Criterios de aceptación
- [ ] 100 % de filas nuevas con documento y usuario.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-012 — Base de pruebas de inventario y autorización

**Épica:** A1 Integridad de stock
**Módulo:** QA transversal
**Tipo:** QA / DevOps
**Prioridad:** P1
**Estado:** Proposed

### Problema
Sin pruebas del núcleo de inventario ni de autorización; sin esquema base para Testcontainers.

### Evidencia AS-IS
- Archivo: `src/test` (40 clases, ninguna de stock); `docs/ESTADO.md` §3.1 (sin `V13__baseline`); ADR-004

### Referencia funcional
N/A (calidad).

### Decisión
Implementar.

### Diseño propuesto
Esquema de pruebas desde `pg_dump --schema-only` de la BD **local** `aura-pos`; Testcontainers PostgreSQL; fixtures mínimas (empresa, sucursal, bodega, producto, usuario por rol); suites: concurrencia de stock, costo promedio, reconteo, traslado, matriz de autorización.

### Backend
Configuración de test.

### Frontend
N/A.

### Base de datos
Script de esquema versionado para tests.

### API
N/A.

### Seguridad y permisos
Matriz rol × endpoint.

### Inventario/contabilidad
Golden files de asientos de inventario (ADR-004).

### Auditoría
N/A.

### Migración de datos
N/A.

### Pruebas unitarias
Incluidas.

### Pruebas integración
Incluidas.

### Pruebas E2E
N/A.

### Dependencias
Ninguna.

### Riesgos
Deriva entre el esquema de pruebas y el real → regenerarlo en cada migración.

### Criterios de aceptación
- [ ] `mvn verify` ejecuta las suites de stock y autorización.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-013 — Bitácora de auditoría de negocio

**Épica:** A3 Autorización fina y auditoría
**Módulo:** Seguridad / Auditoría
**Tipo:** DB / Backend / Frontend
**Prioridad:** P1
**Estado:** Proposed (alinea `PLAN_SEGURIDAD.md` 2.3)

### Problema
No se sabe quién cambió precios, costos, roles, stock ni quién aprobó ajustes.

### Evidencia AS-IS
- Archivo: no existe servicio; precedentes `ContabilidadConfigLogEntity`, `AuditoriaNominaAsistenciaEntity`
- Tabla: —

### Referencia funcional
§7 AuditoriaEvento.

### Decisión
Implementar.

### Diseño propuesto
`auditoria_evento` (ver GAP-A-012) + `AuditoriaEventoService.registrar`; catálogo de acciones auditables: usuario (crear, rol, desactivar, sucursales), rol/permisos, producto (precio, costo, impuesto, cuentas, activar), lista de precios, bodega, sucursal, tercero (datos fiscales, crédito, fusión), inventario (ajuste, traslado anulado, reconteo aprobado), numerador/resolución. Pantalla de consulta y exportación Excel.

### Backend
Servicio + llamadas en ~30 puntos.

### Frontend
Pantalla "Auditoría" (filtros por usuario, entidad, fecha) y pestaña "Historial" en ficha de producto/tercero/usuario.

### Base de datos
Tabla + índices `(empresa_id, entidad, entidad_id)`, `(empresa_id, created_at)`.

### API
`POST /api/auditoria/page`, `GET /api/auditoria/{entidad}/{id}`.

### Seguridad y permisos
`seguridad.auditoria.ver`.

### Inventario/contabilidad
N/A.

### Auditoría
Es la tarea.

### Migración de datos
N/A.

### Pruebas unitarias
Serialización antes/después.

### Pruebas integración
Cambio de precio → 1 evento.

### Pruebas E2E
Consulta del historial de un producto.

### Dependencias
Ninguna (mejor tras TASK-A-010).

### Riesgos
Volumen: retención configurable.

### Criterios de aceptación
- [ ] Las acciones del catálogo generan evento con antes/después.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-014 — Resoluciones y prefijos por sucursal, documento y usuario

**Épica:** A4 Maestros
**Módulo:** Empresa / Documentos
**Tipo:** DB / Backend / Frontend
**Prioridad:** P1
**Estado:** Proposed

### Problema
Una resolución por empresa; prefijo solo por sucursal.

### Evidencia AS-IS
- Archivo: `EmpresaEntity.java:111-137`; `SucursalEntity.java:42-45`; `$M/V25__resolucion_facturacion.sql`

### Referencia funcional
video-01 min. 21-25.

### Decisión
Implementar con el bloque fiscal.

### Diseño propuesto
`resolucion` + `numerador.resolucion_id` + `sucursal_documento_prefijo` + `usuario_prefijo` (override); alertas de rango y vigencia; migración que crea una resolución por empresa desde las columnas actuales.

### Backend
`NumeradorService` usa la resolución; FE/DS toman el `numbering_range_id` de la resolución.

### Frontend
Pantalla de resoluciones y asignación por sucursal/usuario.

### Base de datos
Tablas + backfill + espejo Laravel.

### API
CRUD de resoluciones.

### Seguridad y permisos
`empresa.resolucion.gestionar`.

### Inventario/contabilidad
N/A.

### Auditoría
Cambios de resolución auditados.

### Migración de datos
Backfill desde `empresa.resolucion_*`.

### Pruebas unitarias
Rango agotado / vencido.

### Pruebas integración
Dos sedes, dos prefijos.

### Pruebas E2E
Venta en cada sede con su prefijo.

### Dependencias
TASK-A-007; bloque fiscal.

### Riesgos
Afecta la emisión electrónica: pruebas en ambiente de habilitación.

### Criterios de aceptación
- [ ] Cada venta guarda `resolucion_id` válida.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-015 — Scope de sucursal/bodega y cambio de sede

**Épica:** A3 Autorización fina y auditoría
**Módulo:** Seguridad
**Tipo:** Backend / Frontend
**Prioridad:** P1
**Estado:** Proposed

### Problema
El documento puede apuntar a cualquier sucursal de la empresa; la sede queda fija al login.

### Evidencia AS-IS
- Archivo: `VentaServiceImpl.java:221-224`; `CompraServiceImpl.java:443`; `InventarioServiceImpl.java:87`; `AuthServiceImpl.java:166-176`

### Referencia funcional
§1.5.

### Decisión
Implementar.

### Diseño propuesto
`ScopeService.exigirSucursal/exigirBodega`; `POST /api/auth/cambiar-sucursal`; `usuario_bodega` opcional; permiso `scope.todas_sucursales`.

### Backend
Servicio + llamadas en documentos y listados.

### Frontend
Selector de sede en topbar que re-emite el token.

### Base de datos
`usuario_bodega` (opcional).

### API
Nuevo endpoint de cambio de sede.

### Seguridad y permisos
Es la tarea.

### Inventario/contabilidad
Movimientos solo en bodegas permitidas.

### Auditoría
Cambio de sede registrado.

### Migración de datos
SUPER_ADMIN/ADMIN reciben `scope.todas_sucursales`.

### Pruebas unitarias
Evaluador de alcance.

### Pruebas integración
CAJERO de A → venta en B = 403.

### Pruebas E2E
Cambio de sede y venta.

### Dependencias
TASK-A-002, TASK-A-010.

### Riesgos
Pantallas que asumen todas las sucursales.

### Criterios de aceptación
- [ ] Todo documento con `sucursalId`/`bodegaId` pasa por `ScopeService`.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-016 — Límites de descuento y de cambio de precio con autorización

**Épica:** A3 Autorización fina y auditoría
**Módulo:** Ventas / Seguridad
**Tipo:** Backend / Frontend
**Prioridad:** P1
**Estado:** Proposed

### Problema
Sin topes de descuento ni control de venta bajo costo.

### Evidencia AS-IS
- Archivo: `VentaServiceImpl` (acepta el precio del front); `PrecioDinamicoController.java:196` (cálculo solo consultivo)

### Referencia funcional
§1.5, §3.3.

### Decisión
Implementar sin convertir el cambio de precio en descuento (decisión de producto).

### Diseño propuesto
Parámetros por rol/usuario (`descuento_max_pct`, `permite_cambio_precio`, `variacion_precio_max_pct`, `permite_bajo_costo`); validación en backend contra el precio calculado; fuera de política → autorización con PIN de supervisor (`pin_acceso_rapido` ya existe) registrada en la línea.

### Backend
Validador en venta; campos `autorizado_por` en la línea o cabecera.

### Frontend
Modal de autorización en el POS.

### Base de datos
Parámetros por rol/usuario; columnas de autorización.

### API
Código de error `REQUIERE_AUTORIZACION`.

### Seguridad y permisos
`ventas.precio.autorizar`.

### Inventario/contabilidad
N/A.

### Auditoría
Autorizaciones en bitácora.

### Migración de datos
Defaults permisivos para no frenar la operación el día 1.

### Pruebas unitarias
Validador.

### Pruebas integración
Descuento sobre el tope → 403 con código.

### Pruebas E2E
Autorización con PIN en el POS.

### Dependencias
TASK-A-010, TASK-A-013; bloque ventas.

### Riesgos
Fricción en caja: defaults configurables por empresa.

### Criterios de aceptación
- [ ] Toda venta fuera de política tiene autorizador.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

---

## TASK-A-017 — Reversos al costo del movimiento original

**Épica:** A2 Costeo y trazabilidad
**Módulo:** Inventario / Contabilidad
**Tipo:** Backend / QA
**Prioridad:** P1
**Estado:** Proposed

### Problema
Devoluciones y anulaciones reingresan al costo actual.

### Evidencia AS-IS
- Archivo: `ContabilidadAutoServiceImpl.java:511`; `DevolucionServiceImpl.java:298,407,450`; `VentaServiceImpl.java:787,816`

### Referencia funcional
§5 matriz de efectos.

### Decisión
Mejorar.

### Diseño propuesto
Leer `costo_unitario` del kardex de la línea original (`documento_detalle_id`).

### Backend
Devolución, anulación de venta/compra/merma/obsequio/consumo.

### Frontend
N/A.

### Base de datos
N/A (usa TASK-A-011).

### API
N/A.

### Seguridad y permisos
N/A.

### Inventario/contabilidad
Asientos simétricos.

### Auditoría
N/A.

### Migración de datos
Documentos previos sin enlace: fallback al costo actual con marca.

### Pruebas unitarias
N/A.

### Pruebas integración
Vender a 100, comprar a 200, devolver → 100.

### Pruebas E2E
N/A.

### Dependencias
TASK-A-011, TASK-A-008.

### Riesgos
Documentos históricos sin costo de línea.

### Criterios de aceptación
- [ ] Asiento de devolución = negativo exacto del costo de la venta devuelta.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

### 9.2 Tareas P2/P3 (resumen)

| Tarea | Gap | Fase | Tipo | Prioridad | Objetivo | Dependencias | Criterio de aceptación |
|---|---|---|---|---|---|---|---|
| TASK-A-018 | 019 | A4 | Backend/DB | P2 | Fuente canónica de precio (listas), impuesto (`impuesto_id`) y rol de tercero (`tercero_rol`) | 024 | Una sola escritura por concepto; reporte de divergencias = 0 |
| TASK-A-019 | 020 | A4 | DB/Back/Front | P2 | Direcciones y contactos de tercero | — | Venta elige dirección de entrega; cobranza usa contacto |
| TASK-A-020 | 021 | A4 | Back/Front | P2 | Fusión de terceros con bloqueos fiscales | TASK-A-013 | Fusión auditada; bloqueada con documentos electrónicos |
| TASK-A-021 | 022 | A4 | Back/Front | P2 | Lista de precios, vendedor, forma de pago y descuento por tercero + orden de combinación documentado | — | `calcularPrecio` respeta la lista del cliente |
| TASK-A-022 | 023 | A4 | Back/Front | P2 | Vista 360 del tercero | — | Pestañas con todos los documentos del tercero |
| TASK-A-023 | 024 | A4 | DB/Back | P2 | `producto_impuesto` N:M (verificar normativa vigente) | bloque fiscal | Línea con IVA + bolsa + ultraprocesado calcula y contabiliza |
| TASK-A-024 | 025 | A5 | DB/Back/Front | P2 | Máximo, reorden y sugerido de OC | TASK-A-005 | OC borrador desde el sugerido |
| TASK-A-025 | 026 | A5 | Back/Front | P2 | Política de negativos tri-estado + reporte | TASK-A-005 | ADVERTIR deja vender y alerta |
| TASK-A-026 | 027 | A5 | DB/Back/Front | P2 | Documento de entrada por motivo + `motivo_inventario` con cuenta | TASK-A-008 | Sobrante/obsequio recibido con kardex y asiento |
| TASK-A-027 | 028 | A5 | DB/Back/Front | P2 | Producción por lote + informe de costos de terminado | TASK-A-008 | Terminado entra al costo sumado de componentes |
| TASK-A-028 | 029 | A4 | Back/Front | P2 | Importación Excel de productos y saldos iniciales (plan presentaciones F6) | TASK-A-006 | Validar/confirmar con errores por fila |
| TASK-A-029 | 030 | A0/A4 | Back | P2 | Cifrar credenciales Factus | — | Sin secretos legibles en `empresa` |
| TASK-A-030 | 031 | A3 | Front/DB | P2 | Menú por código y permiso; submódulos por migración | TASK-A-010 | Sin dependencia de labels; SQL sueltos retirados |
| TASK-A-031 | 033 | A4 | Back/DB | P2 | SKU único; no borrar producto con saldo; verificar índices únicos en tablas pre-V14 | — | Diagnóstico y restricciones aplicadas |
| TASK-A-032 | 034 | A5 | Back/Front | P2 | Consolidado valorizado, informe de precios, fichas técnicas | TASK-A-008 | Informes con Excel |
| TASK-A-033 | 035 | A4 | Front | P2 | Historial de precio/costo en la ficha | TASK-A-013 | Pestaña Historial |
| TASK-A-034 | 018 | A3 | Back | P2 | Bloqueo de cuenta y refresh token (PLAN_SEGURIDAD 1.4 y 2.1) | — | Según `PLAN_SEGURIDAD.md` |
| TASK-A-035 | 036 | A6 | DB/Back/Front | P3 | Variantes talla/color | TASK-A-005 | Saldo por variante |
| TASK-A-036 | 037 | A6 | Back/Front | P3 | AIU | bloque fiscal | Factura AIU correcta |
| TASK-A-037 | 038 | A6 | DB/Back | P3 | Unidades por empresa | TASK-A-004 | Unidades de sistema de solo lectura |
| TASK-A-038 | 039 | A6 | DB/Back/Front | P3 | Usuario multiempresa | TASK-A-010 | Selector de empresa |
| TASK-A-039 | 040 | A6 | Back/Front | P3 | Creación rápida por NIT | — | Precarga razón social/DV |
| TASK-A-040 | 041 | A6 | Back/Front | P3 | Traslado en tránsito | TASK-A-005 | Recepción con diferencias |
| TASK-A-041 | 042 | A6 | Front/DB | P3 | Favoritos POS y campos personalizados | — | — |
| TASK-A-042 | 043 | A6 | Docs | P3 | Escribir `docs/PLAN_BODEGAS.md` | — | Documento existe y V172 lo referencia correctamente |

---

## 10. Riesgos transversales del bloque

1. **Esquema sin baseline:** las tablas núcleo no tienen migración de creación; los índices únicos de `venta`, `tercero`, `producto` son UNKNOWN hasta verificarlos en la BD real. Toda restricción nueva debe ir precedida de un diagnóstico de solo lectura.
2. **Doble motor de migraciones:** cada `V18x` necesita su espejo idempotente en Laravel; recordar el gotcha de `ADD COLUMN IF NOT EXISTS` con tipos distintos y `ddl-auto=validate`.
3. **Trabajo local sin commit:** lotes/seriales (V163–V166), presentaciones (V159–V162), bodegas (V172, V183) y otros están solo en local. Las fases A1/A2 tocan esos mismos servicios: consolidar y hacer commit antes de empezar para no mezclar cambios.
4. **Coordinación con el bloque contable:** costo promedio, ajustes, saldo inicial y producción emiten eventos que ese bloque debe consumir; acordar los contratos (`INVENTORY_OPENING`, `INVENTORY_ADJUSTMENT`, `COST_OF_SALES`, `PRODUCTION`) antes de A2.
5. **Coordinación con el bloque fiscal:** resoluciones/prefijos (TASK-A-014) e impuestos múltiples (TASK-A-023) dependen de la normativa vigente y de Factus.

## 11. Siguiente bloque ejecutable

**A0 completo (TASK-A-001, 002, 003 paso 1, 004)**: ningún cambio de esquema, alto impacto, 2–3 días. Primero correr en prod, en solo lectura, los tres diagnósticos: roles existentes, `usuario_sucursal` cruzados entre empresas, unidades de medida alteradas. Después, **TASK-A-012 + TASK-A-005** (pruebas + servicio único de stock) como puerta de entrada a A1/A2.
