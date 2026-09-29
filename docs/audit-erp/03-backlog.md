# Auditoría ERP AURA · 03 — Backlog ejecutable (índice consolidado)

> Cada tarea P0/P1 tiene su ficha completa (problema, evidencia AS-IS, diseño, backend, frontend, BD, API, permisos,
> contabilidad/inventario, auditoría, migración, pruebas, dependencias, riesgos, criterios de aceptación y DoD)
> en el archivo de bloque indicado. Las P2/P3 están en tabla resumida al final de cada bloque.
> - `A` → [parts/A-seguridad-maestros-inventario.md](parts/A-seguridad-maestros-inventario.md)
> - `B` → [parts/B-compras-ventas-caja.md](parts/B-compras-ventas-caja.md)
> - `C` → [parts/C-contabilidad-bancos-reportes.md](parts/C-contabilidad-bancos-reportes.md)
>
> Regla de acceso a datos: JPA solo `findById`/`save`/`delete`; toda consulta en el `QueryRepository` (ver `.claude/agents/aura-erp-architect.md` §7.6).
>
> Estado inicial de todas las tareas: **Proposed**. Pasan a **Ready** cuando el usuario aprueba la fase.

Leyenda de dependencias: `→` depende de.

---

## F0 — Estabilización

| ID | Tarea | Prio | Tipo | Depende |
|---|---|---|---|---|
| F0-01 | Verificar migraciones V156–V183 en local y definir cuáles suben a prod; correr SQL de menú pendientes | P0 | DB/DevOps | — |
| F0-02 | Commit de la rama `fix/camilo-caja-pagos` (V183) | P1 | DevOps | — |
| F0-03 | Inventario de índices UNIQUE reales en prod (tablas pre-V14: `venta`, consecutivos) | P0 | DB | — |
| F0-04 | Diagnósticos SQL solo lectura: roles existentes, `usuario_sucursal` cruzados, `unidad_medida` alteradas, ventas con CUFE anuladas, devoluciones que reescribieron ventas, documentos sin asiento, consecutivos duplicados, compras anuladas con CxP viva | P0 | DB/QA | — |

## F1 — Contención P0

| ID | Tarea | Prio | Tipo | Depende |
|---|---|---|---|---|
| TASK-A-001 | ✅ 2026-09-29 Bloqueo de roles privilegiados (`utils/PoliticaRoles` + tests): PLATFORM_ADMIN nunca desde la empresa, SUPER_ADMIN solo por SUPER_ADMIN; también en sincronizar empleado→usuario | P0 | Backend | F0-04 |
| TASK-A-002 | ✅ 2026-09-29 Sucursal asignada debe ser de la empresa del usuario (el token solo se emite en login desde esas asignaciones) | P0 | Backend | F0-04 |
| TASK-A-003 | Red de autorización por URL, seguridad de método activa, rotación JWT | P0 | Backend | — |
| TASK-A-004 | Proteger el catálogo global de unidades de medida | P0 | Backend | — |
| ~~TASK-B-001~~ | ❌ Descartada por decisión del usuario (2026-09-29): no se bloquea anular ventas con CUFE | — | — | — |
| TASK-B-003 | Anulación de venta completa (banco, devoluciones, comisiones) | P0 | Backend | → B-001 |
| TASK-B-002 | Anulación de compra completa (CxP, pagos, validaciones) | P0 | Backend | — |
| TASK-B-005 | Valor de la devolución con descuentos de línea y general | P0 | Backend | — |
| TASK-B-012 | Gasto: edición y anulación coherentes | P0 | Backend | — |
| TASK-B-017 | Cierre de accesos cruzados entre empresas (caja, devoluciones, ventas) | P1 | Backend | → A-002 |
| TASK-B-007 | Payload de factura electrónica fiel a la venta | P0 | Backend | — |
| TASK-B-008 (paso 1) | Quitar `@Retry` en FE, reservar estado antes de enviar, `reference_code` único por sucursal | P0 | Backend | — |
| TASK-C-001 | Guard de período contable en documentos operativos | P0 | Backend | — |
| TASK-C-002 | Reproceso atómico de documentos editados (compra) | P0 | Backend | — |

## F2 — Integridad transaccional

| ID | Tarea | Prio | Tipo | Depende |
|---|---|---|---|---|
| TASK-A-007 (= B-009) | Numerador transaccional de consecutivos, único por documento | P0 | Backend/DB | F0-03 |
| TASK-A-005 (= B-010) | Servicio único de movimiento de stock con bloqueo | P0 | Backend | — |
| TASK-A-006 | El stock solo cambia por documentos (quitar edición directa) | P0 | Backend/Frontend | → A-005 |
| TASK-C-003 | Consecutivo de comprobante seguro, idempotencia en BD, reproceso y detector de documentos sin asiento | P0 | Backend/DB | → A-007 |
| TASK-B-015 | Idempotencia de creación y control de factura de proveedor duplicada | P1 | Backend/DB | — |
| TASK-B-016 | Bloqueo en abonos CxC/CxP y movimientos de turno | P1 | Backend | — |
| TASK-B-024 | Estados tipados en documentos | P1 | Backend/DB | — |
| TASK-B-025 | Anulación lógica en lugar de borrado físico | P1 | Backend | — |
| TASK-A-012 | Base de pruebas de inventario y autorización | P1 | QA | → A-005 |

## F3 — Costeo y kardex valorizado

| ID | Tarea | Prio | Tipo | Depende |
|---|---|---|---|---|
| TASK-A-011 | Kardex estructurado (documento, usuario, costo, valor, signo) | P1 | Backend/DB | → A-005 |
| TASK-A-008 | Costo promedio ponderado por empresa y producto (flag) | P0 | Backend/DB | → A-011 |
| TASK-C-004 | Costo promedio como base del asiento + control 1435 vs kardex | P0 | Backend | → A-008, C-003 |
| TASK-A-017 | Reversos al costo del movimiento original | P1 | Backend | → A-011 |
| TASK-C-005 | `DevolucionGenerador` espejo de la venta original | P0 | Backend | → C-004 |
| TASK-A-009 | Reconteo correcto: saldo al contar, costo real y asiento | P1 | Backend | → A-008 |

## F4 — Cadena documental y notas

| ID | Tarea | Prio | Tipo | Depende |
|---|---|---|---|---|
| TASK-B-020 | Grafo documental, conversión de cotización y control de cruces | P1 | Backend/DB/Frontend | F2 |
| TASK-B-011 | Recepción real de la orden de compra y legalización sin doble ingreso | P0 | Backend/DB/Frontend | → B-020 |
| TASK-B-004 | Devolución inmutable: no reescribir la venta; el cambio es venta nueva | P0 | Backend | → B-020 |
| TASK-B-006 | Nota de venta unificada ligada a venta, CxC, inventario y NC/ND electrónica | P0 | Backend/DB/Frontend | → B-004 |
| TASK-C-006 | NC/ND electrónica con tercero y cruce con CxC/devolución | P0 | Backend | → B-006 |
| TASK-B-021 | NC de compra por valor y ND de compra | P1 | Backend | → B-020 |
| TASK-B-022 | Anticipos completos | P1 | Backend | → B-020 |
| TASK-B-019 | Pedido con abonos, plan separe y cobro por recibo | P1 | Backend/Frontend | → B-022 |
| TASK-B-014 | Edición de compra segura | P1 | Backend | → C-002 |
| TASK-B-018 | Origen de fondos declarado en reembolsos de devolución | P1 | Backend/Frontend | → B-004 |

## F5 — Autorización fina y auditoría

| ID | Tarea | Prio | Tipo | Depende |
|---|---|---|---|---|
| TASK-A-010 | RBAC por acción con overrides por usuario | P1 | Backend/DB/Frontend | → A-003 |
| TASK-C-016 | Catálogo de acciones contables y su aplicación | P1 | Backend | → A-010 |
| TASK-A-015 | Scope de sucursal/bodega y cambio de sede | P1 | Backend/Frontend | → A-002 |
| TASK-A-016 | Límites de descuento y cambio de precio con autorización | P1 | Backend/Frontend | → A-010 |
| TASK-B-023 | Precio/impuesto validados en backend y límites de descuento | P1 | Backend | → A-016 |
| TASK-A-013 | Bitácora de auditoría de negocio | P1 | Backend/DB | — |
| TASK-A-030 | Menú por código de permiso; submódulos por migración | P2 | Frontend/DB | → A-010 |
| TASK-A-034 | Bloqueo de cuenta y refresh token | P2 | Backend | — |

## F6 — Motor contable único y parametrización

| ID | Tarea | Prio | Tipo | Depende |
|---|---|---|---|---|
| TASK-C-008 | Estrangular `ContabilidadAutoServiceImpl` y unificar el generador de ventas | P1 | Backend | → C-003 |
| TASK-C-010 | Fechas contables del documento y reloj de la empresa | P1 | Backend | — |
| TASK-C-009 | Pantalla de parametrización contable | P1 | Frontend/Backend | → C-008 (parcial), A-010 |
| TASK-C-011 | Retenciones por tarifa y 350 por concepto | P1 | Backend | → C-009 |
| TASK-B-027 | Retenciones en compras parametrizadas | P1 | Backend/Frontend | → C-011 |
| TASK-C-015 | Validador único de cuentas y guardas del PUC | P1 | Backend | — |
| TASK-C-019 | Grupo y tercero en la forma de pago; duplicar forma de pago | P2 | Backend/DB/Frontend | → C-009 |
| TASK-C-020 (= B-037) | Vista previa de contabilización | P2 | Backend/Frontend | → C-008 |

## F7 — Fiscal electrónico (verificar normativa vigente antes de iniciar)

| ID | Tarea | Prio | Tipo | Depende |
|---|---|---|---|---|
| TASK-B-008 (completo) | Outbox FE/NC/DS, estados y reconciliación | P0 | Backend/DB | → B-006 |
| TASK-B-026 | Cola de ventas pendientes de FE y política de emisión | P1 | Backend/Frontend | → B-008 |
| TASK-B-013 | Documento soporte: bloqueo del origen y nota de ajuste | P1 | Backend | → B-008 |
| TASK-A-014 | Resoluciones y prefijos por sucursal, documento y usuario | P1 | Backend/DB/Frontend | → A-007 |
| TASK-A-023 | `producto_impuesto` N:M | P2 | DB/Backend | normativa |
| TASK-B-035 | `venta_impuesto` por tarifa | P2 | Backend/DB | → B-023 |
| TASK-B-028 | Recepción XML de proveedores y eventos RADIAN | P2 | Backend/Frontend | → B-008, B-011 |

## F8 — Cierres, reportes contables, bancos y activos

| ID | Tarea | Prio | Depende |
|---|---|---|---|
| TASK-C-007 | Vista común de movimientos y modalidad de cancelación de resultados | P1 | → C-008 |
| TASK-C-013 | Saldos iniciales por lotes con cruces contra los auxiliares | P1 | → C-008 |
| TASK-C-014 | Balance de prueba completo | P1 | → C-007 |
| TASK-C-012 | Activos fijos: depreciación correcta, baja y venta con asiento | P1 | → C-008 |
| TASK-C-024 | Comparativos y ER por CC en columnas | P2 | → C-014 |
| TASK-C-017 | Conciliación unificada con partidas arrastradas | P2 | → C-010 |
| TASK-C-026 | Obligaciones: intereses, porción corriente, anulación de cuota | P2 | → C-008 |
| TASK-C-027 | Saldo bancario desde el mayor | P2 | → C-008 |
| TASK-C-028 | Base gravable, tarifa y documento de cruce en la partida | P2 | → C-008 |
| TASK-C-029 | Informes de activos fijos | P2 | → C-012 |
| TASK-C-032 | Borrador de ICA y certificados de retención | P2 | → C-011, C-028 |
| TASK-C-033 | Exógena 1003/1010/1012 | P2 | → C-028 |

## F9 — Maestros y políticas comerciales

| ID | Tarea | Prio | Depende |
|---|---|---|---|
| TASK-A-018 | Fuente canónica de precio, impuesto y rol de tercero | P2 | → A-023 |
| TASK-A-019 | Direcciones y contactos de tercero | P2 | — |
| TASK-A-020 + TASK-C-022 | Fusión de terceros (operativa + contable) | P2 | → A-013 |
| TASK-A-021 | Condiciones comerciales por tercero | P2 | — |
| TASK-A-022 | Vista 360 del tercero | P2 | — |
| TASK-A-024 | Máximo, reorden y sugerido de OC | P2 | → A-005 |
| TASK-A-025 | Política de negativos tri-estado | P2 | → A-005 |
| TASK-A-026 | Entrada por motivo + `motivo_inventario` con cuenta | P2 | → A-008 |
| TASK-A-027 | Producción por lote + informe de costos | P2 | → A-008 |
| TASK-A-029 | Cifrar credenciales Factus | P2 | — |
| TASK-A-031 | SKU único, no borrar producto con saldo | P2 | F0-03 |
| TASK-A-033 | Historial de precio/costo | P2 | → A-013 |
| TASK-B-029 | Turno de caja: controles de arqueo | P2 | → B-017 |

## F10 — Productividad, reportes operativos y BI

| ID | Tarea | Prio | Depende |
|---|---|---|---|
| TASK-C-023 (= B-031) | Reportes de compras + filtros de ventas + cola asíncrona | P2 | → B-020 |
| TASK-A-032 | Consolidado valorizado, informe de precios, fichas técnicas | P2 | → A-008 |
| TASK-A-028 | Importación Excel de productos y saldos iniciales | P2 | → A-006 |
| TASK-B-032 | Importar/exportar documentos | P2 | → B-015 |
| TASK-B-033 | Smart create en el autocomplete estándar | P2 | — |
| TASK-B-034 | Envío de PDF por email y WhatsApp | P2 | — |
| TASK-B-036 | Retirar flujo `factura` legacy y `FacturaRetryScheduler` | P2 | — |
| TASK-B-030 | Remisión de venta (solo si hay demanda de distribución) | P2 | → B-020 |
| TASK-C-021 | Reclasificación / traslado de cuentas | P2 | → C-016 |
| TASK-C-025 | Presupuesto (`PLAN_PRESUPUESTO.md`) | P2 | → C-007 |
| TASK-C-018 | `sucursal_id` en la partida | P2 | → A-015 |
| TASK-C-030 | IVA mayor valor del costo | P2 | → C-008 |
| TASK-C-031 | Asiento de nómina: fecha del período + retefuente/FSP | P2 | → C-008 |

## F11 — Especializaciones (P3, bajo demanda)

A-035 variantes · A-036 AIU · A-037 unidades por empresa · A-038 usuario multiempresa · A-039 creación por NIT ·
A-040 traslado en tránsito · A-041 favoritos/campos personalizados · A-042 `docs/PLAN_BODEGAS.md` ·
B-038 replicar documento · B-039 comisiones por recaudo · B-040 moneda extranjera · B-041 exportación ·
B-042 ingresos para terceros · B-043 campos XML · B-044 factura sectorial/AIU ·
C-034 copiar parametrización · C-035 conciliación automática · C-036 ficha de activo extendida ·
C-037 dashboard financiero · C-038 triggers de cuadre/período en BD.

---

## Definition of Done común

- [ ] Código integrado con pruebas verdes (incluida la regresión del P0 que corrige).
- [ ] Migración con espejo idempotente en Laravel, probada en local antes de prod.
- [ ] Diagnóstico previo y script de corrección de datos (si aplica) aprobados.
- [ ] Permisos probados por rol.
- [ ] Auditoría/trazabilidad validada.
- [ ] Manual `/ayuda` y SQL de menú actualizados si cambió una pantalla o un mensaje.
