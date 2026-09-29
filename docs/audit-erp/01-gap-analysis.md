# Auditoría ERP AURA · 01 — Análisis de brechas (consolidado)

> Fecha: 2026-09-29 · Modo: AUDIT (sin cambios de código) · Agente: `aura-erp-architect`
> Referencia funcional: `.claude/knowledge/aura-worldoffice-baseline.md` + transcripciones de los 4 videos.
> Detalle con evidencia archivo:línea, matrices AS-IS completas y tareas en plantilla:
> - [Bloque A — Seguridad, maestros e inventario](parts/A-seguridad-maestros-inventario.md)
> - [Bloque B — Compras, ventas y caja/POS](parts/B-compras-ventas-caja.md)
> - [Bloque C — Contabilidad, bancos, activos y reportes](parts/C-contabilidad-bancos-reportes.md)

---

## 1. Resumen ejecutivo

AURA ya no es un POS: tiene cobertura funcional de ERP comparable (y en varias áreas superior) a la referencia —
lotes FEFO, seriales, recetas con merma, precios dinámicos, control de crédito, cierre anual colombiano, EEFF NIIF,
devengos, modo revisión del contador, reporte gerencial por cruces, nómina/PILA completas.

**El problema no es de cobertura sino de integridad.** Las brechas críticas se concentran en tres ejes:

1. **Seguridad multi-tenant** — escalada de rol a `PLATFORM_ADMIN`, sucursales cruzadas entre empresas y una API
   (767 endpoints) sin autorización por rol.
2. **Integridad transaccional** — consecutivos `MAX()+1`, stock sin bloqueo, posting contable que puede perderse o
   caer en períodos cerrados, anulaciones que dejan CxP/banco/FE vivos.
3. **Costo y cadena documental** — costo = último costo de compra (no promedio), devoluciones que reescriben la venta,
   NC/ND electrónicas desconectadas de la venta, la CxC y el inventario.

El motor contable (dominio puro, cuadre garantizado, sin PUC en generadores, AFTER_COMMIT + PostingLog) es una base
sólida: la mayoría de fallas contables son **de conexión documento → mayor**, no del motor.

### Conteo (después de deduplicar)

| Bloque | P0 | P1 | P2 | P3 | Total bruto |
|---|---:|---:|---:|---:|---:|
| A — Seguridad, maestros, inventario | 8 | 9 | 17 | 8 | 42 |
| B — Compras, ventas, caja/POS | 12 | 17 | 10 | 7 | 46 |
| C — Contabilidad, bancos, activos, reportes | 5 | 10 | 17 | 5 | 37 |
| **Total bruto** | **25** | **36** | **44** | **20** | **125** |

Tras unificar los gaps que describen el mismo problema desde distintos bloques (sección 4) quedan **~112 brechas únicas**,
de las cuales **18 son P0**.

---

## 2. Hallazgos críticos (P0 consolidados)

| ID global | Hallazgo | Gaps origen | Impacto |
|---|---|---|---|
| **X-01** | Escalada a `PLATFORM_ADMIN`: `CreateUsuarioDto.rol` / `UpdateUsuarioDto.rol` aceptan cualquier texto; `CustomUserDetailsService:42` lo convierte en authority; `SecurityConfig:55` abre `/api/platform/**` a esa authority | A-001 | Un ADMIN de cualquier cliente administra toda la plataforma. **Verificado en código.** |
| **X-02** | Sucursal de otra empresa asignable a un usuario y embebida en el JWT (`UsuarioServiceImpl:185`); IDOR en caja/devoluciones/ventas | A-002, B-017 | Acceso cruzado entre tenants |
| **X-03** | API sin autorización por rol (767 endpoints); `PermisoInterceptor`/`@RequerirPermiso` sin registrar; `@PreAuthorize` inerte; rotación JWT pendiente | A-003 (`PLAN_SEGURIDAD.md`) | Cualquier usuario autenticado opera cualquier módulo |
| **X-04** | Catálogo global `unidad_medida` editable por cualquier tenant | A-004 | Un cliente altera datos de todos |
| **X-05** | Consecutivos `MAX()+1` sin bloqueo en ventas, documentos y comprobantes contables (series OB y CE compartidas) | A-008, B-009, C-002 | Números duplicados; asientos perdidos en POS concurrente |
| **X-06** | Stock leer→sumar→save sin `@Lock`/`@Version` en venta, compra, devolución, reconteo, traslado, merma | A-006, B-010 | Salidas perdidas; validación de negativos saltada |
| **X-07** | Stock editable a mano sin kardex ni asiento (`InventarioServiceImpl:112-138`) | A-005 | Kardex ≠ stock; 1435 ≠ inventario |
| **X-08** | Costo = último costo de compra (`CompraServiceImpl:1545`); fletes a 1435 sin sumarse al costo unitario | A-007, C-003 | Utilidad, costo de ventas e inventario valorizado incorrectos |
| **X-09** | Documentos en períodos cerrados quedan sin asiento (falla post-commit silenciosa); compra editada puede quedar sin asiento válido | C-001 | Mayor incompleto sin alerta |
| **X-10** | Posting no idempotente en BD, sin reproceso ni detector de documentos sin asiento | C-002 | Duplicados u omisiones contables |
| **X-11** | Anular venta con CUFE es posible (`VentaServiceImpl:728-831`) | B-001 | Factura válida en DIAN, anulada en AURA |
| **X-12** | Anular venta no revierte banco, devoluciones vigentes ni comisiones | B-008 | Doble reversa; saldos bancarios falsos |
| **X-13** | Anular compra no anula la CxP ni revierte pagos; no valida abonos/NC/DS | B-007 | CxP fantasma; caja/banco descuadrados |
| **X-14** | Devolución reescribe `venta_detalle` y totales, agrega líneas de "cambio" a la venta original; reembolso ignora descuentos | B-002, B-003 | Documento fiscal alterado; se devuelve más dinero del cobrado |
| **X-15** | Asiento de devolución con fecha de la venta, costo actual, cuentas genéricas y reembolso siempre a CAJA | C-004, A-015 | Mayor ≠ operación |
| **X-16** | NC/ND electrónicas sin `venta_id`/`devolucion_id`, sin inventario ni CxC; asiento acredita 1305 sin tercero | B-004, C-005, B-022 | Doble reversa de ingreso; cartera ≠ 1305 |
| **X-17** | Payload FE siempre descuento 0 / contado / persona natural; `@Retry` en timeout; sin bloqueo "ya emitida"; `reference_code` choca entre sucursales | B-005, B-006 | Total DIAN ≠ AURA; facturas huérfanas en DIAN |
| **X-18** | Recepción de OC es un contador sin documento; gasto editado sin recontabilizar y eliminado con CxP viva | B-011, B-012 | Inventario/CxP inconsistentes |

---

## 3. Matriz de brechas consolidada (checklist de la baseline §8)

Estados: COMPLETE · PARTIAL · MISSING · DIFFERENT · BETTER_THAN_REFERENCE (BTR) · NOT_APPLICABLE (N/A).

| Dominio | Capacidad | Estado AURA | Prioridad | Gaps |
|---|---|---|---|---|
| Seguridad | Roles | PARTIAL (texto libre, sin lista blanca) | P0 | A-001, A-010 |
| Seguridad | Permisos por módulo | PARTIAL (solo menú/front) | P0 | A-003, A-031 |
| Seguridad | Permisos por acción / campo | MISSING | P1 | A-010, C-015 |
| Seguridad | Scope empresa/sucursal | PARTIAL (tenant del token ✔; sucursal ✘) | P0/P1 | A-002, A-011, B-017 |
| Seguridad | Override por usuario / límites | MISSING | P1 | A-017, B-024 |
| Seguridad | Auditoría transversal | MISSING (solo detectiva) | P1 | A-012 |
| Empresa | Multiempresa / sucursales | COMPLETE | — | — |
| Empresa | Prefijos / resoluciones por sucursal y documento | PARTIAL (una resolución por empresa) | P1 | A-014 |
| Empresa | Consecutivos seguros | PARTIAL (`MAX+1`) | P0 | X-05 |
| Empresa | Centros de costo / dimensiones | COMPLETE (CC/proyecto/frente en partida); sin sucursal en asiento | P2 | C-017 |
| Terceros | Cliente/proveedor/empleado coexistente | PARTIAL (doble fuente de rol) | P2 | A-019 |
| Terceros | Direcciones / contactos múltiples | MISSING | P2 | A-020 |
| Terceros | Condiciones comerciales | PARTIAL | P2 | A-022 |
| Terceros | Crédito / cupo | BTR | — | — |
| Terceros | Historial 360 | PARTIAL | P2 | A-023 |
| Terceros | Fusión de duplicados | MISSING | P2 | A-021, C-021 |
| Catálogo | Producto / servicio / insumo | COMPLETE | — | — |
| Catálogo | Conversiones / presentaciones | BTR (plan presentaciones) | — | — |
| Catálogo | Listas de precios / precios dinámicos | BTR | — | — |
| Catálogo | Impuestos múltiples por producto | MISSING (IVA fijo 0/5/19) | P2 | A-024, B-037 |
| Catálogo | Descuentos con límites | PARTIAL (sin límite server-side) | P1 | B-024, A-017 |
| Catálogo | Serial / lote | BTR | — | — |
| Catálogo | Talla/color | MISSING | P3 | A-036 |
| Catálogo | Kit / receta | BTR | — | — |
| Catálogo | AIU | MISSING | P3 | A-037, B-046 |
| Inventario | Saldo por bodega | COMPLETE (V172) | — | — |
| Inventario | Costo promedio | MISSING (último costo) | P0 | X-08 |
| Inventario | Min/max/reorden | PARTIAL (solo mínimo) | P2 | A-025 |
| Inventario | Política de negativos | PARTIAL | P2 | A-026 |
| Inventario | Traslados | COMPLETE (sin tránsito) | P3 | A-041 |
| Inventario | Mermas / obsequios / consumo interno | BTR | — | — |
| Inventario | Entrada por motivo | MISSING | P2 | A-027 |
| Inventario | Ajuste físico / reconteo | PARTIAL (doble descuento, costo 0, sin asiento) | P1 | A-013 |
| Inventario | Kardex | PARTIAL (sin enlace estructurado ni signo único) | P1 | A-016 |
| Inventario | Concurrencia | MISSING | P0 | X-06 |
| Producción | Simplificada por lote | PARTIAL (receta sí, orden de producción no) | P2 | A-028 |
| Compras | Orden de compra | PARTIAL (recepción sin documento) | P0 | B-011 |
| Compras | Remisión / cruce parcial sin doble ingreso | MISSING | P1 | B-011, B-021 |
| Compras | Factura de compra | COMPLETE (edición destructiva) | P1 | B-014, B-015 |
| Compras | NC devolución | BTR | — | — |
| Compras | NC descuento / ND | PARTIAL | P1 | B-022 |
| Compras | Anticipos | PARTIAL | P1 | B-023 |
| Compras | Egresos / CxP | PARTIAL (anulación incompleta) | P0 | B-007 |
| Compras | Documento soporte | PARTIAL (código listo; DS no habilitado; sin nota de ajuste) | P1 | B-013 |
| Compras | XML / eventos RADIAN | MISSING | P2 | B-030 |
| Ventas | Cotización | PARTIAL (conversión sin vínculo) | P1 | B-020 |
| Ventas | Pedido / plan separe | PARTIAL | P1 | B-019 |
| Ventas | Remisión / devolución de remisión | DIFFERENT (POS factura al despachar) | P2 | B-032 |
| Ventas | Factura / POS | COMPLETE (inmutable ✔) | — | — |
| Ventas | Anulación | PARTIAL (CUFE, banco, comisiones) | P0 | B-001, B-008 |
| Ventas | NC devolución / descuento / ND | PARTIAL / MISSING (desconectadas) | P0 | X-14, X-16 |
| Ventas | Recibos de caja | BTR (multi-factura con locks) | — | — |
| Ventas | CxC / cartera | COMPLETE (plan cartera) | — | — |
| Ventas | Retenciones al recaudo | BTR | — | — |
| Ventas | Facturación electrónica | PARTIAL (payload infiel, no idempotente, sin cola) | P0/P1 | B-005, B-006, B-028 |
| Ventas | Comisiones | PARTIAL (no se revierten; sin esquemas por recaudo) | P1/P3 | B-025, B-041 |
| Ventas | Moneda extranjera / exportación / ingresos para terceros | N/A hoy | P3 | B-042..044 |
| Caja | Turnos, arqueo, caja menor, traslados, carritos abandonados | BTR (arqueo mejorable) | P2 | B-031 |
| Contabilidad | PUC y auxiliares | COMPLETE (guardas débiles en asiento manual legacy) | P1 | C-014 |
| Contabilidad | Motor de reglas | COMPLETE en dominio, **dual** (legacy 13 flujos) | P1 | C-007 |
| Contabilidad | Parametrización visible | PARTIAL (sin pantalla) | P1 | C-009 |
| Contabilidad | Vista previa de contabilización | MISSING | P2 | B-039, C-019 |
| Contabilidad | Saldos iniciales | PARTIAL (descuadre a 3705 silencioso) | P1 | C-012 |
| Contabilidad | Reclasificación / traslado de cuentas | MISSING | P2 | C-020 |
| Contabilidad | Períodos y cierres | BTR (cierre anual, reapertura) con fugas | P0/P1 | C-001, C-006 |
| Contabilidad | Costo de ventas | BTR (perpetuo) con base incorrecta | P0 | X-08 |
| Contabilidad | Depreciación | PARTIAL (fórmula errada, baja sin asiento) | P1 | C-011 |
| Contabilidad | Diferidos / devengos | BTR | — | — |
| Contabilidad | Retenciones por tarifa / ICA / certificados | PARTIAL | P1/P2 | C-010, C-031 |
| Contabilidad | Fechas contables | PARTIAL (`now()` en UTC) | P1 | C-008 |
| Contabilidad | Balance de prueba / EEFF / libros | PARTIAL (BP sin saldo anterior) / BTR (EEFF, libros) | P1 | C-013 |
| Contabilidad | Exógena / declaraciones 300/350 | PARTIAL | P2 | C-032, C-006 |
| Contabilidad | Presupuesto | MISSING (diseñado) | P2 | C-024 |
| Bancos | Cuentas / movimientos | COMPLETE (saldo con doble fuente) | P2 | C-026 |
| Bancos | Conciliación manual | PARTIAL (doble mecanismo, sin partidas arrastradas) | P2 | C-016 |
| Bancos | Conciliación automática | MISSING | P3 | C-034 |
| Bancos | Obligaciones financieras | PARTIAL | P2 | C-025 |
| Activos | Ficha / responsable / CC | PARTIAL | P3 | C-035 |
| Activos | Depreciación / baja / venta | PARTIAL / MISSING | P1 | C-011 |
| Reportes | Ventas / cartera / inventario / contables | COMPLETE–PARTIAL | P2 | A-034, C-023 |
| Reportes | Compras | MISSING | P2 | B-033, C-022 |
| Reportes | Procesamiento asíncrono | MISSING | P2 | C-022 |
| Integraciones | Importación Excel | PARTIAL (contable ✔; productos/documentos ✘) | P2 | A-029, B-034 |
| Integraciones | Email / WhatsApp | PARTIAL | P2 | B-036 |
| Calidad | Pruebas de stock y autorización | MISSING | P1 | A-032 |

---

## 4. Deduplicación entre bloques

| Tema único | Gaps que lo describen | Dueño en backlog |
|---|---|---|
| Numerador transaccional | A-008, B-009, C-002 (consecutivo de comprobante) | TASK-A-007 (+ C-003 para comprobantes) |
| Servicio de stock con bloqueo | A-006, B-010 | TASK-A-005 (B-010 lo consume) |
| Costo promedio ponderado | A-007, C-003 | TASK-A-008 + TASK-C-004 (control 1435) |
| Reversos al costo original / devolución espejo | A-015, C-004, B-002 | TASK-A-017 + TASK-C-005 + TASK-B-004 |
| NC/ND ligadas a venta, CxC e inventario | B-004, B-022, C-005 | TASK-B-006 + TASK-C-006 |
| RBAC por acción | A-010, C-015 | TASK-A-010 (C-016 aporta el catálogo contable) |
| IDOR sucursal/empresa | A-002, B-017 | TASK-A-002 + TASK-B-017 |
| Vista previa de contabilización | B-039, C-019 | TASK-C-020 |
| Reportes de compras + asíncrono | B-033, C-022 | TASK-C-023 |
| Fusión de terceros | A-021, C-021 | TASK-A-020 + TASK-C-022 |
| Límites de descuento/precio | A-017, B-024 | TASK-A-016 + TASK-B-023 |

---

## 5. Fortalezas a conservar (BETTER_THAN_REFERENCE)

- **Tenant siempre desde el token** (`SecurityUtils.getEmpresaId()`), usuario separado de tercero/empleado.
- **Saldo por bodega** (V172), bodegas que no venden, principal única por índice parcial.
- **Lotes FEFO, seriales de punta a punta, consumo interno y obsequio con cuenta e IVA, recetas con merma y costeo.**
- **Presentaciones "contiene N"** sin que el usuario entienda factores.
- **Precios dinámicos** (cliente con vigencia, volumen, día/hora) y **control de crédito** con score y autorización.
- **Venta inmutable**; **NC de compra** validada contra origen con tres destinos del dinero; **recibo de caja multi-factura** con locks.
- **Origen de fondos declarado**, freno de fechas retroactivas, caja menor, traslados de fondos, carritos abandonados.
- **Retenciones al recaudo** como abonos vinculados.
- **Motor contable**: dominio puro con cuadre imposible de violar, golden tests, resolución en capas sin PUC literal, reversión por contraasiento, AFTER_COMMIT + PostingLog.
- **Cierre anual colombiano, devengos, EEFF NIIF completos, modo revisión del contador, libros, reporte gerencial por cruces, importador Excel contable.**
- **Nómina, asistencia por obra/frente, PILA y nómina electrónica** (fuera del baseline).
- **Auditoría detectiva** (`kardexVsStock`, descuadres).

## 6. No aplicables hoy

| Capacidad | Motivo | Reevaluar cuando |
|---|---|---|
| Moneda extranjera, diferencia en cambio, exportación | Sin clientes multimoneda | Primer cliente importador/exportador |
| Ingresos para terceros, AIU | Sin demanda (AIU puede cruzar con Proyectos/Frentes) | Cliente de construcción/inmobiliaria |
| Remisión de venta obligatoria | El POS factura al despachar | Cliente distribuidor |
| Consolidación contable multiempresa, clase 7 / CIF | Fuera de alcance | Producción industrial |
| Conciliación desde PDF del banco | Frágil; se reemplaza por perfiles de importación | — |
| "Todo usuario es empleado", rol "API" | Decisión de AURA superior | Integraciones entrantes |
| WhatsApp por QR de una línea | No copiar; usar API/links | — |

## 7. Dependencias clave entre bloques

```
Seguridad A0 ─────────────► RBAC A3 ─────────► Permisos contables C-015/C-016
Numerador + stock con lock (A1/B1) ─► Costo promedio (A2) ─► Asiento con costo real (C1)
Posting idempotente (C0) ─────────► Reproceso de todo lo que se corrija después
Grafo documental (B2) ─► Notas/devoluciones (B3) ─► NC contable (C-006)
Motor único (C2) ─► Parametrización visible (C3) ─► Cierres/reportes (C4)
Verificación normativa DIAN vigente ─► Fiscal electrónico (B4, A-014, A-024, C-031/032)
```

## 8. Riesgos transversales

- **Datos ya corruptos en prod**: cada fase de contención necesita un **diagnóstico SQL de solo lectura** previo
  (roles existentes, `usuario_sucursal` cruzadas, unidades alteradas, ventas con CUFE anuladas, devoluciones que
  reescribieron ventas, documentos sin asiento, consecutivos duplicados) y un script de corrección aprobado.
- **Esquema aplicado por Laravel** (Flyway apagado, `ddl-auto=validate`): toda migración nueva requiere espejo
  idempotente en `aura-pos-migracion-old`; nunca aplicar directo sobre `aura-db` de prod. Cuidado con
  `ADD COLUMN IF NOT EXISTS` y tipos distintos.
- **Tablas anteriores a V14**: la unicidad real (p. ej. `venta`) es UNKNOWN; verificar índices en prod antes de B1.
- **Muchas migraciones locales sin correr** (V156–V183): hay que estabilizarlas antes de sumar nuevas (Fase 0).
- **Normativa**: FE/POS electrónico, DS y nota de ajuste, RADIAN, UVT, exógena e ICA deben validarse contra la DIAN
  vigente; los videos reflejan la norma de su momento.
- **Zona horaria**: JVM de prod en UTC afecta fechas contables (C-008) y reportes.
