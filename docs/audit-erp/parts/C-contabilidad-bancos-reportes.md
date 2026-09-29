# Bloque C — Contabilidad, Bancos, Activos fijos y Reportes

Auditoría AS-IS vs. referencia funcional · MODO AUDIT (sin cambios de código)
Fecha: 2026-09-29 · Rama inspeccionada: `fix/camilo-caja-pagos` · Baseline: secciones 1.8–1.15, 2.1–2.4, 2.16–2.18, 3.21, 4.1–4.9, 4.17–4.19

Convenciones de evidencia:
- `BE:` = backend `src/main/java/com/cloud_technological/aura_pos/…`
- `MIG:` = `src/main/resources/db/migration/…`
- `FE:` = frontend `D:\Proyectos Camilo\aura-post\aura-frontend\src\app\…`
- Nómina está fuera del baseline: solo se revisó su asiento.
- Lo que ya documentan los planes (`docs/ARQUITECTURA_CONTABILIDAD.md`, `docs/DISENO_CONTABILIDAD_AVANZADA.md`, `docs/PLAN_CIERRE_CONTABLE.md`, `docs/PLAN_PRESUPUESTO.md`, `docs/PLAN_REPORTE_GERENCIAL.md`) se enlaza; no se vuelve a diseñar.

---

## 1. Resumen del bloque

AURA tiene un módulo contable **mucho más avanzado que un POS típico** y, en varias piezas, mejor que la referencia: contabilización perpetua en línea (el costo de ventas se registra por venta, no en un proceso mensual), un motor por eventos con `AFTER_COMMIT` + `PostingLog`, un dominio puro (`AsientoBuilder`/`ReglasAsiento`) que no permite construir asientos descuadrados, modo revisión (borrador → contabilizado), períodos por fecha con apertura automática y reapertura con traza (V171), cierre anual fiscal colombiano (provisión de renta, traslado, distribución y dividendos), devengos (causaciones, diferidos, deterioro y anticipos), conciliación bancaria contra extracto, EEFF NIIF (incluye flujo de efectivo indirecto y cambios en el patrimonio), exógena con lotes y aprobación, notas de diario con reversión, soportes y plantillas, libros auxiliar y diario, borradores de IVA 300 y retención 350, e importador Excel.

El riesgo no está en lo que falta, sino en **la integridad del puente operación → mayor**:

1. **Los documentos operativos no validan el período contable.** Se confirman en meses cerrados y el asiento revienta después del commit: el documento existe y la contabilidad no. No hay detector de "documentos sin asiento" ni reproceso de fallos.
2. **El consecutivo de comprobante es `MAX()+1`** con índice único. Dos ventas simultáneas chocan y una se queda sin asiento. La idempotencia es de tipo "consulta y luego inserta", sin restricción en la base de datos.
3. **El costo que va al mayor no es costo promedio.** `producto.costo` se sobrescribe con el último costo de compra y el costo de ventas sale de ahí; los fletes entran a 1435 pero no al costo unitario. La cuenta 1435 y el kardex se separan por construcción.
4. **La devolución de venta se contabiliza con la fecha de la venta original**, al costo actual del producto y a cuentas genéricas distintas de las que usó la venta. La nota crédito electrónica acredita 1305 **sin tercero** y sin cruzar la CxC.
5. **Motor dual:** conviven 1.584 líneas del motor viejo (`ContabilidadAutoServiceImpl`: compra, devolución, gasto, merma, nómina, caja, tesorería, obligaciones, reversa y cierre) con el registro nuevo (18 generadores). La venta tiene **dos generadores que dan asientos distintos**.
6. La parametrización existe en el backend (concepto → cuenta, formas de pago, impuestos, categorías, modo revisión, posting log) pero **el frontend solo expone las categorías de producto**. Para el contador, los defaults del enum `ConceptoContable` funcionan en la práctica como cuentas fijas.

Conteo de gaps del bloque: **P0 = 5 · P1 = 10 · P2 = 17 · P3 = 5 (37 en total)**.

---

## 2. Hallazgos críticos

| # | Hallazgo | Evidencia | Impacto |
|---|---|---|---|
| H1 | Ventas, compras, gastos, devoluciones, mermas y abonos **no validan el período contable** antes de confirmarse. Si el mes está cerrado, `PeriodoContableResolver` lanza la excepción **dentro del listener AFTER_COMMIT**, y el documento queda vivo sin asiento. | `grep periodo` vacío en `BE: services/implementations/{Venta,Compra,Gasto,Devolucion,Merma}ServiceImpl.java`; `BE: event/CompraContabilizacionListener.java` reversa (REQUIRES_NEW, commit) y **luego** genera: si la generación falla, la compra queda con el asiento reversado y sin reemplazo | El mayor queda incompleto en silencio; saldos de 1105/1435/2205 incorrectos |
| H2 | El consecutivo del comprobante se calcula con `COALESCE(MAX(n),0)+1` y hay índice único `ux_asiento_empresa_comprobante`. Con concurrencia, una inserción falla → `PostingLog` ERROR, **sin reintento**. La idempotencia es `existsBy…` seguido de `save`, sin índice único en `(empresa_id, tipo_origen, origen_id)`. | `BE: repositories/contabilidad/AsientoContableQueryRepository.java:122-141`; `MIG: V63` (índice de comprobante); `MIG: V44` (solo `idx_asiento_origen`, no único); `BE: contabilidad/application/ContabilizarDocumentoUseCase.java` | Ventas sin asiento en horas pico del POS; asientos duplicados si el evento se entrega dos veces |
| H3 | El costo de ventas usa `producto.costo`, que la compra **sobrescribe** con el último costo unitario (no hay promedio ponderado). La compra capitaliza fletes en 1435, pero no en `producto.costo`. La NC de compra no restituye el costo. | `BE: CompraServiceImpl.java:1545-1546` (`producto.setCosto(item.getCostoUnitario())`); `BE: VentaServiceImpl.java:839-853` (`calcularCostoBase`); `BE: ContabilidadAutoServiceImpl.java:~245` (`costoInventario = subtotal - descuento + fletes`) | 1435 del mayor ≠ valorización del kardex; utilidad bruta errada |
| H4 | Asiento de devolución: la fecha es la de la **venta original**, no `fechaDevolucion`; el costo reingresado es `producto.getCosto()` actual y no el `costo_linea` de la venta; inventario, costo e IVA van a cuentas genéricas y no a las de la categoría o el impuesto; el reembolso va siempre a `CAJA` e ignora `metodoDevolucion` y `tesoreriaMovimientoId`. | `BE: ContabilidadAutoServiceImpl.java:470-615`; `BE: entity/DevolucionEntity.java` (`fechaDevolucion`, `metodoDevolucion`, `tesoreriaMovimientoId`) | Una devolución de un mes cerrado no se contabiliza; 1435, 6135 y 2408 desalineados con la venta; caja inflada/banco desinflado |
| H5 | La NC electrónica debita `INGRESOS_VENTAS` y acredita `CLIENTES` **con `clienteId = null`**. No se relaciona con la CxC operativa ni con la devolución, así que la misma devolución puede reversar el ingreso dos veces. | `BE: contabilidad/infrastructure/persistence/LectorNotaJpa.java` (último argumento `null`); `BE: contabilidad/application/generador/NotaCreditoGenerador.java`; `BE: services/implementations/FactusNotaService.java:384-395` | Auxiliar de cartera por tercero descuadrado; exógena 1008 errada; posible doble reversa |
| H6 | El cierre mensual **cancela las cuentas de resultados en cada mes** (asiento `CIERRE`). Los reportes que no excluyen `CIERRE` muestran cero en las clases 4-6 de los meses cerrados: los ingresos del borrador del IVA 300 y el balance de comprobación por período. | `BE: ContabilidadAutoServiceImpl.java:1017-1075`; `BE: repositories/contabilidad/DeclaracionesQueryRepository.java:52-65` (sin filtro de `CIERRE`); `…AsientoContableQueryRepository.java:556-604` | Declaración de IVA con ingresos en cero en meses cerrados; balance de prueba inútil para resultados |
| H7 | La venta tiene dos generadores: `VentaGenerador`, con categorías, CC e impuesto por producto (evento nuevo), y `ContabilidadAutoServiceImpl.generarDesdeVenta`, sin nada de eso (endpoint `POST /api/contabilidad/asientos/generar-desde-venta/{id}` + listener `@Deprecated` aún registrado). | `BE: controllers/ContabilidadController.java:297-306`; `BE: event/VentaContabilizacionListener.java` | Regenerar a mano da un asiento distinto al automático |

---

## 3. Matriz AS-IS

| Dominio | Funcionalidad | Implementación encontrada | Estado | Evidencia |
|---|---|---|---|---|
| PUC | Plan de cuentas jerárquico por empresa | `plan_cuenta` (código, tipo, naturaleza, nivel, padre, auxiliar, es_medio_pago, codigo_dian); siembra del PUC básico | COMPLETE | `MIG: V44`; `BE: PlanCuentasServiceImpl.java:98-150`; `FE: features/contabilidad/plan-cuentas` |
| PUC | Auxiliares/subauxiliares y cuentas de tesorería | `auxiliar`, `es_medio_pago`, `/plan-cuentas/medios-pago` | COMPLETE | `BE: ContabilidadController.java:54-97` |
| PUC | Control de cambios de cuentas con movimiento | Se puede cambiar tipo, naturaleza, `auxiliar` y `padre_id` aunque la cuenta tenga movimientos; `padre_id` no se valida por empresa; borrar = inactivar | PARTIAL | `BE: PlanCuentasServiceImpl.java:75-100` |
| PUC | Tercero obligatorio por cuenta | Solo existe una heurística en el importador (13/22/23 piden tercero); no hay atributo `exige_tercero` | PARTIAL | `BE: ImportacionServiceImpl.java:437` |
| Motor | Evento → generador → asiento | `DocumentoContabilizableEvent` → `ContabilizacionListener` (AFTER_COMMIT) → `ContabilizarDocumentoUseCase` → `GeneradorRegistry` (18 generadores) | PARTIAL (dual) | `BE: contabilidad/**` |
| Motor | Motor legacy | `ContabilidadAutoServiceImpl` (1.584 líneas, 17 métodos `generarDesde*`, `reversar`, `generarCierre`); 6 pares de evento/listener legacy activos | DIFFERENT (deuda) | `BE: services/implementations/ContabilidadAutoServiceImpl.java`; `BE: event/*` |
| Motor | Cuentas parametrizadas (sin hardcode) | `ConceptoContable` (código por defecto) → override `cuenta_config` → fallback al código por defecto; guardarraíles por clase. Los literales PUC están en el enum y en los seeds; también en reportes (`DeclaracionesServiceImpl`, `EstadosFinancierosService`, `ExogenaService` seeds, `CierreAnualService` "54") | PARTIAL | `BE: entity/ConceptoContable.java`; `BE: ConfiguracionContableServiceImpl.java:42-67`; `BE: DeclaracionesServiceImpl.java:74-160` |
| Motor | Plantillas por tarifa / actividad (1.8) | Categoría contable de producto (ingreso, devolución, inventario, costo, servicio) + cuentas por impuesto (generado/descontable). No hay asistente por actividad económica | PARTIAL / DIFFERENT | `MIG: V89, V90`; `BE: CategoriaContableProductoServiceImpl.java`; `BE: ImpuestoServiceImpl.java` |
| Motor | Reglas por tipo documento / tipo tercero / sucursal / forma de pago (1.9) | Solo empresa + concepto + forma de pago + categoría + impuesto. No hay dimensión de sucursal, tipo de documento ni tipo de contribuyente | PARTIAL | `BE: contabilidad/infrastructure/resolucion/*` |
| Motor | Contabilización avanzada / editar partida antes de guardar | Modo revisión (BORRADOR) + aprobar; no se editan las partidas de un automático; no hay vista previa | PARTIAL | `MIG: V88`; `BE: contabilidad/web/AsientoRevisionController.java` |
| Motor | Vista previa de contabilización (2.17) | No existe para venta, compra ni gasto (sí para deterioro y documento soporte) | MISSING | `BE: DevengoController.java:81-91` (solo deterioro) |
| Motor | Replicación de reglas (1.10) | No existe | MISSING | — |
| Motor | Idempotencia | Use case: `existsByTipoOrigenAndOrigenIdAndEmpresaId`; legacy: `asientoVigente` por conteo de reversas. Sin índice único | PARTIAL | `BE: AsientoRepositorioJpa.java:31-35`; `BE: ContabilidadAutoServiceImpl.java:1367-1395` |
| Motor | Cuadre garantizado | `AsientoBuilder.build()` + `AsientoBalanceValidator` en el legacy y en lo manual. Sin CHECK/trigger en la BD | COMPLETE (app) / PARTIAL (BD) | `BE: contabilidad/domain/ReglasAsiento.java`; `BE: utils/AsientoBalanceValidator.java` |
| Motor | Reproceso de fallos | `contabilidad_posting_log` (consulta de 200 filas); reproceso manual solo para venta, compra y nómina (legacy). No hay job ni reintento por tipo | PARTIAL | `BE: PostingLogAdapter.java`; `BE: ContabilidadController.java:297-332` |
| Motor | Reversa vs. borrado | Contraasiento con fecha de hoy (el original sigue CONTABILIZADO, V176). La reversa copia CC pero **no proyecto ni frente**. Borrado físico solo de la apertura sin otros asientos y de las notas en borrador | COMPLETE (con defecto) | `BE: ContabilidadAutoServiceImpl.java:401-468`; `BE: AperturaContableServiceImpl.java:170-179` |
| Formas de pago | Forma de pago → cuenta | `forma_pago_contable` (código, nombre, cuenta, requiere cuenta bancaria); CRUD + seed. Sin grupo (contado/crédito/billetera/financiera), sin documentos aplicables, sin tercero de cartera ni mapeo electrónico | PARTIAL | `MIG: V86`; `BE: FormaPagoContableController.java`; FE: no consume `/formas-pago` |
| Categorías | Categoría contable de producto | CRUD + seed + selector en la ficha de producto | COMPLETE | `BE: CategoriaContableProductoController.java`; `FE: core/services/contabilidad.service.ts:40` |
| Impuestos | Impuesto parametrizable (IVA/INC, cuentas) | `impuesto` con cuentas generado/descontable; resolución por producto | PARTIAL (sin FE) | `MIG: V90`; `BE: ResolucionImpuestoJpa.java` |
| Retenciones | Tarifas de retención | `tarifa_retencion` (natural/jurídica, base mínima, **cuenta_contable_id**) + FE | PARTIAL | `MIG: V57`; `FE: contabilidad/tarifas-retencion` |
| Retenciones | Cuenta por tarifa/concepto en el asiento | Compra y gasto usan siempre `RETEFUENTE/RETEIVA/RETEICA_PRACTICADA`; `tarifa_retencion.cuenta_contable_id` no se usa | PARTIAL | `BE: ContabilidadAutoServiceImpl.java:318-333, 710-717` |
| Retenciones | Retenciones al recaudo (que nos practican) | Abonos vinculados + conceptos 1355xx | COMPLETE | `MIG: V181`; `BE: RetencionRecaudoService.java`; `AbonoCobroGenerador.java` |
| Retenciones | ReteICA por municipio | Una sola cuenta 2368; sin municipio ni tarifa por actividad | PARTIAL | `BE: ConceptoContable.RETEICA_PRACTICADA` |
| Notas | Nota de diario / comprobante manual | `NotaDiario*` (borrador → contabilizar, reversar, anular, soportes, PDF, importar líneas, plantillas); además el legacy `POST /asientos` y `POST /comprobantes` (CE/RC/CD) | COMPLETE / duplicado | `BE: NotaDiarioServiceImpl.java`; `BE: AsientoContableServiceImpl.java:100-160` |
| Notas | Validaciones de la nota manual legacy | `POST /asientos` no valida cuenta auxiliar/activa/de la empresa ni líneas negativas o de doble sentido | PARTIAL | `BE: AsientoContableServiceImpl.java:100-160` |
| Saldos iniciales | Apertura contable | Un solo asiento `APERTURA`; el descuadre se lleva solo a 3705 (o a una cuenta elegida); sugerencia desde bancos; se elimina si no hay otros asientos | PARTIAL | `BE: AperturaContableServiceImpl.java` |
| Saldos iniciales | Importador Excel (PUC, terceros, saldos, cartera abierta) | Validar/confirmar | COMPLETE | `BE: ImportacionController.java`; `FE: contabilidad/importar` |
| Saldos iniciales | Inventario / activos / diferidos iniciales (2.2) | Sin pestaña contable integrada; no se concilian contra el kardex ni contra la ficha de activos | MISSING | — |
| Terceros | Traslado/fusión de terceros (contable) | No existe | MISSING | — |
| PUC | Traslado de cuentas (movimiento/parametrización) (1.14) | No existe | MISSING | — |
| Períodos | Bloqueo y cierre mensual | Período por fecha (V171), varios abiertos, cierre en orden, reapertura con motivo y evento, bloqueo de asientos en período cerrado dentro del resolver | PARTIAL | `BE: PeriodoContableResolver.java`; `BE: PeriodoContableServiceImpl.java`; `MIG: V171` |
| Períodos | Bloqueo de **documentos operativos** en período cerrado (2.1) | No existe (solo la ventana de retroactividad de caja, V149) | MISSING | `MIG: V149` (solo caja) |
| Cierre | Checklist de cierre (fases 2-5) | Pendiente según el plan | MISSING (planeado) | `docs/PLAN_CIERRE_CONTABLE.md` §2 |
| Cierre | Cancelación de resultados | Mensual (`CIERRE` → 3605) + cierre anual (provisión de renta, traslado a 3705, distribución, dividendos) | DIFFERENT / BETTER | `BE: ContabilidadAutoServiceImpl.java:1017`; `BE: contabilidad/infrastructure/cierre/CierreAnualService.java` |
| Costo de ventas | Costo por venta (perpetuo) | `venta_detalle.costo_linea` = cantidad × `producto.costo` (último costo), o composición | PARTIAL (base de costo errada) | `BE: VentaServiceImpl.java:421, 839-853` |
| Costo de ventas | Informe de rentabilidad | `reportes/avanzado/margenes`, top productos | COMPLETE | `BE: ReporteController.java:354` |
| Depreciación | Cálculo y asiento | Endpoint por período; cuota = (costo − residual − acumulada) / vida **total** (decreciente, no línea recta); ignora fecha de adquisición; si faltan cuentas registra la depreciación sin asiento | PARTIAL (defectuoso) | `BE: ActivoFijoServiceImpl.java:116-237` |
| Diferidos | Amortización | `DiferidoService` + generador | COMPLETE | `BE: contabilidad/infrastructure/devengo/DiferidoService.java` |
| Devengo | Causaciones, deterioro de cartera, anticipos | Servicios + generadores + endpoints | COMPLETE (sin FE para causaciones y deterioro) | `BE: DevengoController.java`, `AnticipoController.java` |
| Diferencia en cambio | Moneda extranjera | Fuera de alcance declarado | NOT_APPLICABLE (hoy) | `docs/DISENO_CONTABILIDAD_AVANZADA.md` §14 |
| Presupuesto | Presupuesto por cuenta PUC | Solo diseño | MISSING (planeado) | `docs/PLAN_PRESUPUESTO.md` |
| Dimensiones | Centro de costo, proyecto y frente en la partida | `asiento_detalle.centro_costo_id/proyecto_id/frente_id`; ER y mayor filtran por CC/proyecto/frente | PARTIAL | `MIG: V49, V51, V92`; `BE: ContabilidadController.java:244-280` |
| Dimensiones | Sucursal en el asiento | No existe la columna; la venta usa el CC de la sucursal como sustituto | MISSING | `BE: entity/AsientoContableEntity.java` |
| Bancos | Cuentas bancarias | `cuenta_bancaria` (banco, tipo, número, titular, tercero, cuenta contable, saldo inicial/actual, sobregiro, cupo); validación de prefijo 11 | COMPLETE | `MIG: V36, V101, V175`; `FE: tesoreria/cuentas-bancarias` |
| Bancos | Movimientos de tesorería | Egresos y recaudos con contrapartida, anulación con reversa | COMPLETE | `BE: TesoreriaServiceImpl.java` |
| Bancos | Traslados de fondos | Con anulación y reembolso de caja menor | COMPLETE | `MIG: V177, V178`; `TrasladoFondosGenerador` |
| Bancos | Saldo del banco | Doble fuente: `cuenta_bancaria.saldo_actual` (mutable) vs. mayor; hay un comparador `conciliacion-mayor` | DIFFERENT | `BE: CuentaBancariaServiceImpl.java:138` |
| Bancos | Conciliación manual | **Dos mecanismos**: marcar `tesoreria_movimiento.conciliado` (V37) y extracto + líneas (V94) | PARTIAL / duplicado | `BE: TesoreriaController.java:84-113`; `BE: ConciliacionBancariaService.java` |
| Bancos | Conciliación automática (4.7) | Sugerencias por valor exacto y fecha ±3 días; CSV genérico; ajustes GMF/comisión/interés con asiento; cierre exige cuadre | PARTIAL | `BE: ConciliacionBancariaService.java:135-190, 237` |
| Bancos | Partidas conciliatorias arrastradas | Las partidas en tránsito son informativas del mes; no se arrastran; el saldo inicial del extracto no se valida contra el anterior | MISSING | `BE: ConciliacionBancariaService.java:280-300` |
| Bancos | Obligaciones financieras | Desembolso, tabla de cuotas, pago de cuota (capital + interés), anulación sin cuotas pagadas | PARTIAL | `BE: ObligacionFinancieraServiceImpl.java`; `ContabilidadAutoServiceImpl.java:1133-1198` |
| Bancos | Reclasificación de sobregiros | Al cierre (2105) y reversa al abrir el siguiente mes | COMPLETE | `BE: ContabilidadAutoServiceImpl.java:1086` |
| Activos | Ficha | Código, descripción, categoría, fecha, valor, vida útil, método, residual, ubicación, responsable (texto), cuentas, CC, tercero. Sin placa, serial, póliza, mantenimientos, activo padre ni adiciones | PARTIAL | `MIG: V55`; `BE: entity/ActivoFijoEntity.java` |
| Activos | Baja / venta de activo (3.23) | La baja cambia el estado **sin asiento**; la venta de activo no existe | MISSING | `BE: ActivoFijoServiceImpl.java:93-103` |
| Activos | Adquisición ligada a compra | La compra puede debitar una cuenta 15xx (destino) sin crear ni vincular la ficha | MISSING | `BE: ContabilidadAutoServiceImpl.java:258-262` |
| Activos | Informes (4.19) | Lista + historial de depreciación; no hay informes por CC, responsable, póliza ni mantenimiento | PARTIAL | `BE: ActivoFijoController.java` |
| Reportes contables | Balance de prueba | Solo por `periodo_contable_id`; sin saldo anterior, rango, tercero, CC ni nivel; incluye CIERRE | PARTIAL | `BE: AsientoContableQueryRepository.java:556-604`; `BE: ReporteContableController.java` |
| Reportes contables | Balance general / situación financiera | Por clase y detallado; resultado del ejercicio presentado en patrimonio | COMPLETE | `BE: AsientoContableServiceImpl.java:490-650`; `FE: contabilidad/balance-general` |
| Reportes contables | Estado de resultados (con CC/proyecto/frente) | Sí, excluye CIERRE | COMPLETE | `…QueryRepository.java:255-300` |
| Reportes contables | Comparativos meses/años | No existen | MISSING | — |
| Reportes contables | EEFF NIIF (EFE indirecto, cambios en el patrimonio) | Sí | BETTER_THAN_REFERENCE | `BE: contabilidad/infrastructure/reportes/EstadosFinancierosService.java`; `FE: contabilidad/eeff` |
| Reportes contables | Libro mayor, auxiliar por tercero, diario | Sí (+ Excel) | COMPLETE | `BE: LibrosContablesController.java`; `ContabilidadController.java:262` |
| Fiscal | Exógena | Formatos 1001/1005/1006/1007/1008/1009/2276, mapeos, validación, lotes, aprobación, Excel | PARTIAL (faltan 1003/1010/1012) | `MIG: V95`; `BE: contabilidad/infrastructure/exogena/*` |
| Fiscal | Borradores de declaraciones | IVA 300 y retención 350 con advertencias; sin ICA | PARTIAL | `BE: DeclaracionesServiceImpl.java` |
| Fiscal | Certificados de retención a terceros | Solo el de ingresos y retenciones de empleados | MISSING | `BE: CertificadoController.java` |
| Reportes operativos | Ventas | Excel/PDF por rango; avanzados por categoría, vendedor, márgenes y rotación; FE `reporte-ventas` | PARTIAL (filtros limitados) | `BE: ReporteController.java:87-115, 309-372` |
| Reportes operativos | Compras (2.18) | No hay endpoint; la carpeta del FE `reportes/compras` está vacía | MISSING | `FE: features/reportes/compras` (vacía) |
| Reportes operativos | Cartera / CxP / estados de cuenta | Resumen, documentos, Excel, estado de cuenta en PDF | COMPLETE | `BE: ReporteController.java:255-285`; `EstadoCuentaPdfService.java` |
| Reportes operativos | Inventario / kardex / mermas / gastos | Excel/PDF | COMPLETE | `BE: ReporteController.java:152-215, 294-301` |
| Reportes | Reporte gerencial (cruces) | PDF de auditoría | BETTER_THAN_REFERENCE | `docs/PLAN_REPORTE_GERENCIAL.md` |
| Reportes | Dashboard | KPIs de ventas; sin KPIs financieros | PARTIAL | `BE: DashboardController.java` |
| Reportes | Procesamiento asíncrono + correo (2.18/3.21) | No existe para reportes (solo hay ejecutores async para nómina y logs) | MISSING | `BE: config/AsyncConfig.java` |
| Config UI | Concepto → cuenta, formas de pago, impuestos, modo revisión, posting log | Backend completo; **sin pantalla** | PARTIAL | FE: ninguna referencia a `configuracion-cuentas`, `formas-pago`, `/impuestos`, `posting-log` |
| Seguridad | Permisos por acción contable | Sin `@PreAuthorize` efectivo en los controladores contables (ver Bloque A / `docs/PLAN_SEGURIDAD.md`) | MISSING | `BE: controllers/*Contab*`, `contabilidad/web/*` |
| Auditoría | Trazas contables | `usuario_id`, `contabilizado_por/at`, `anulado_por/at`, `motivo_anulacion`, `periodo_contable_evento`, `contabilidad_config_log`, `posting_log` | COMPLETE | `MIG: V85, V88, V171, V173` |
| Tests | Pruebas del motor | Unitarias y golden de los generadores nuevos; **ninguna** para compra, devolución, gasto, merma ni reversa (legacy) | PARTIAL | `src/test/java/**` (VentaGeneradorTest, GoldenAsientos, …) |

---

## 4. Mapa de eventos operativos → asiento

Leyenda de la resolución: **P** = parametrizado (concepto → override → default), **C** = categoría/impuesto de producto, **FP** = forma de pago / cuenta bancaria, **D** = cuenta directa del documento, **F** = fija en el código o sin tercero/dimensión.

| Documento / evento | Tipo origen · prefijo | Publicador | Generador (motor) | Débitos / créditos | Resolución | Fecha contable | Reverso | Observación |
|---|---|---|---|---|---|---|---|---|
| Venta confirmada | VENTA · VT | `VentaServiceImpl:676` (`DocumentoContabilizableEvent`) | `VentaGenerador` (nuevo) | Db Caja/Banco por pago, 1305 saldo; Cr ingreso por categoría, impuesto por impuesto; Db costo / Cr inventario por categoría | C + FP + P; CC de sucursal | `fecha_emision` | `ContabilidadReversaEvent("VENTA")` → legacy `reversar` | Costo = `costo_linea` (último costo) |
| Venta (reproceso manual) | VENTA · VT | `POST /asientos/generar-desde-venta` | **legacy** `generarDesdeVenta` | Igual, pero con cuentas únicas | P (sin C, sin CC) | `fecha_emision` | idem | Diverge del automático (H7) |
| Anulación de venta | ANULACION_VENTA · RV | `VentaServiceImpl:828` | legacy `reversar` | Espejo | copia cuenta/tercero/CC | **hoy** | — | Pierde proyecto/frente |
| Compra / NC de compra | COMPRA · CO | `CompraServiceImpl:667, 1421` (`CompraContabilizableEvent`) | **legacy** `generarDesdeCompra` (reversa + genera) | Db inventario por categoría o destino D; Db IVA desc. por impuesto; Cr retenciones; Cr pagos FP; Cr 2205 saldo | C + D + FP + P; CC/proyecto | `compra.fecha` | reversa legacy | Retenciones sin cuenta por tarifa; reversa y generación no son atómicas (H1) |
| Anulación de compra | ANULACION_COMPRA · RV | `CompraServiceImpl:1215` | legacy `reversar` | Espejo | — | hoy | — | — |
| Devolución de venta / cambio | DEVOLUCION · DV | `DevolucionServiceImpl:423` | **legacy** `generarDesdeDevolucion` | Db devolución por categoría; Db IVA (genérico); Cr 1305/Caja; reingreso inventario/costo (genérico) | C parcial + P + **F (CAJA)** | **fecha de la venta** | reversa legacy | H4 |
| NC electrónica aceptada | NOTA_CREDITO · NC | `FactusNotaService:390` | `NotaCreditoGenerador` | Db 4135 base; Db 2408; Cr 1305 | P, **sin tercero** | `created_at` (UTC) | — | H5 |
| ND electrónica | NOTA_DEBITO · ND | idem | `NotaDebitoGenerador` | Espejo | P | `created_at` | — | Mismo problema de tercero (misma lectura) |
| Recaudo de cartera (abono / recibo de caja / pedido) | ABONO_COBRAR · RC | `CuentaCobrarServiceImpl:251`, `ReciboCajaServiceImpl`, `TurnoCajaServiceImpl:257`, `PedidoVendedorServiceImpl:306` | `AbonoCobroGenerador` | Db FP; Db retenciones que nos practican; Cr 1305 tercero | FP + P | **hoy** | `ContabilidadReversaEvent("ABONO_COBRAR")` | Ignora `fecha_pago` |
| Pago a proveedor | ABONO_PAGAR · EG | `CuentaPagarServiceImpl:255`, `TurnoCajaServiceImpl:300` | `AbonoPagoGenerador` | Db 2205 tercero; Cr FP | FP + P | hoy | — | — |
| Anticipo / cruce | ANTICIPO · AN / ANTICIPO_CRUCE · AC | `AnticipoService` | generadores nuevos | 2805/1330 vs FP | P + FP | fecha del documento | — | — |
| Gasto | GASTO · GT | `GastoServiceImpl:136` (`OperacionContabilizableEvent`) | **legacy** `generarDesdeGasto` | Db cuenta D / diferido 1705 / 5195; Db IVA; Cr retenciones; Cr 2205 o FP | D + P + FP | `gasto.fecha` | reversa legacy | Retenciones genéricas |
| Merma | MERMA · MM | `MermaServiceImpl:242` | **legacy** `generarDesdeMerma` | Db 5195; Cr inventario por categoría (componentes) | P + C | `merma.fecha` | reversa legacy | — |
| Obsequio | OBSEQUIO · **OB** | `ObsequioServiceImpl:308` | `ObsequioGenerador` | Db 523550 / IVA asumido; Cr inventario | P + C | fecha | reversa | Comparte la serie "OB" con obligaciones |
| Consumo interno | CONSUMO_INTERNO · CI | `ConsumoInternoServiceImpl` | `ConsumoInternoGenerador` | Db gasto/CC; Cr inventario | D + C | fecha | reversa | — |
| Diferencia de cierre de caja | DIFERENCIA_CAJA · DC | `TurnoCajaServiceImpl:194` | `DiferenciaCajaGenerador` | 5195 / 4295 vs caja | P | fecha del turno | — | — |
| Movimiento de caja con concepto | MOVIMIENTO_CAJA · RC/CE | `TurnoCajaServiceImpl:333, 449` | **legacy** `generarDesdeMovimientoCaja` | Caja vs. cuenta del concepto | D + FP | `created_at` (UTC) | — | — |
| Traslado de fondos | TRASLADO_FONDOS · TF | `TrasladoFondosServiceImpl` | `TrasladoFondosGenerador` | Cuenta destino / origen | D | fecha | reversa | — |
| Tesorería egreso/recaudo | TESORERIA · TS | `TesoreriaServiceImpl:73, 131` | **legacy** `generarDesdeTesoreria` | Banco vs. contrapartida D | D + FP | fecha del movimiento | reversa | — |
| Ajuste de conciliación | AJUSTE_BANCARIO · AB | `ConciliacionBancariaService` | `AjusteBancarioGenerador` | 530515/530595/421005 vs. banco | P | fecha de la línea | — | — |
| Desembolso de obligación | OBLIGACION · **OB** | `ObligacionFinancieraServiceImpl` | **legacy** | Db banco; Cr 2105 tercero | FP + P | `fecha_desembolso` | reversa | Comparte la serie "OB" con obsequio |
| Pago de cuota | CUOTA_OBLIGACION · CU | idem | **legacy** | Db 2105 capital, Db 5305 interés; Cr banco | P + FP | `fecha_pago` | **no hay** | Interés por caja, sin causación mensual |
| Nómina (causación) | NOMINA · NO | `NominaServiceImpl:455` | **legacy** | Db 5105xx por concepto (reparto por frente); Cr 2505/2370/2380/25xx | P | **`created_at`** (UTC) | reversa legacy | Retefuente y FSP de nómina sin mapear |
| Pago de nómina / prestación | NOMINA_PAGO · PN / PRESTACION_PAGO · PP | `NominaServiceImpl:593`, `PrestacionServiceImpl` | **legacy** | Db pasivo; Cr FP | FP + P | fecha del pago | — | — |
| Causación / diferido / deterioro | CAUSACION · CS / DIFERIDO · DF / DETERIORO · DT | `devengo/*Service` | generadores nuevos | Según el concepto | P | fecha del proceso | — | Sin FE para causaciones ni deterioro |
| Depreciación | DEPRECIACION · DP | `ActivoFijoServiceImpl` (**escritura directa**, sin motor) | — | Db gasto del activo; Cr 1592 del activo | D | último día del período | **no hay** | Fórmula incorrecta; sin asiento si faltan cuentas |
| Baja / venta de activo | — | — | — | **Nada** | — | — | — | MISSING |
| Cierre mensual | CIERRE · **CE** | `PeriodoContableServiceImpl` | legacy `generarCierre` | Cancela 4/5/6 → 3605 | P | último día del mes | ANULADO al reabrir | La serie "CE" choca con los comprobantes de egreso |
| Reclasificación de sobregiro | SOBREGIRO · SB | idem | legacy | Banco vs. 2105 | P | último día | ANULADO al reabrir | — |
| Cierre anual / distribución / dividendos | CIERRE_ANUAL · CA / DISTRIBUCION_UTILIDAD · DU / DIVIDENDO_PAGO · PD | `CierreAnualService` | generadores nuevos | 5405/2404, 3605→3705, 2360 | P | 31-dic / fecha | — | — |
| Apertura | APERTURA · (AP) | `AperturaContableServiceImpl` (**directa**) | — | Líneas del usuario + ajuste 3705 | D + P | fecha de apertura | borrado físico si es el único asiento | El descuadre se absorbe (GAP-C-012) |
| Nota de diario / comprobante manual | MANUAL · CD/CE/RC | `NotaDiarioServiceImpl`, `AsientoContableServiceImpl` (**directa**) | — | Usuario | D | fecha del DTO | reversión (nota) / ANULADO (legacy) | Legacy sin validación de auxiliar |

Conclusión del mapa: **no hay cuentas PUC literales en los generadores** (todo pasa por `ConceptoContable` + overrides, lo que respeta ADR-006). La deuda está en (a) las cuentas **genéricas** donde ya existe una regla más fina (devolución, NC, retenciones), (b) las **fechas contables** tomadas de `now()`/`created_at` en UTC, (c) tres escritores directos fuera del motor (depreciación, apertura y manual legacy) y (d) 13 flujos que siguen en el legacy sin pruebas.

---

## 5. Gaps del bloque

### P0 — CRITICAL

#### GAP-C-001 — Documentos operativos confirmados en períodos cerrados quedan sin asiento
**Estado:** MISSING (control) · **Prioridad:** P0
**AURA actual:** `PeriodoContableResolver` bloquea el asiento de un mes cerrado, pero solo se invoca desde el motor (AFTER_COMMIT). `VentaServiceImpl`, `CompraServiceImpl`, `GastoServiceImpl`, `DevolucionServiceImpl`, `MermaServiceImpl`, `CuentaCobrar/PagarServiceImpl` y `TurnoCajaServiceImpl` no consultan el período. `CompraContabilizacionListener` ejecuta `reversar()` (REQUIRES_NEW, se confirma) y después `generarDesdeCompra()`: si la segunda falla, la compra queda **sin asiento vigente**. El fallo va a `PostingLog`/`ErrorLog` y nadie lo recoge.
**Referencia funcional:** 2.1 "la fecha de bloqueo impide documentos iguales o anteriores a un corte".
**Problema:** el mayor pierde documentos en silencio y los saldos de caja, inventario, cartera y proveedores divergen de la operación. Es la causa probable de parte de lo descrito en el diagnóstico de 1105 negativa (compra editada con asiento viejo).
**Decisión:** implementar.
**Diseño propuesto:** puerto `PeriodoContablePort.exigirAbierto(empresaId, fecha)` invocado **dentro de la transacción del documento** (guard de aplicación) en todos los servicios que publican un evento contable, incluidos la edición y la anulación. Si el mes está cerrado: error 422 con la acción sugerida (cambiar la fecha o reabrir). Para la edición de compras: reversa y regeneración en **una sola transacción** REQUIRES_NEW (nuevo `ReprocesarDocumentoUseCase`) que haga todo o nada.
**Impacto técnico:** BE (≈10 servicios + un listener), sin cambios de BD; FE: mostrar el mensaje.
**Riesgos:** el POS de fin de mes con un período cerrado por error bloquea ventas. Mitigación: el período del mes en curso nunca puede estar cerrado (ya se valida `suyo.isAfter(now)`); el período se cierra en orden.
**Dependencias:** ninguna.
**Pruebas:** unitarias del guard; integración "compra con fecha de mes cerrado → 422, sin filas"; "edición de compra cuya regeneración falla → el asiento original sigue vigente".
**Criterios de aceptación:** ningún documento con efecto contable se confirma con fecha en un período CERRADO; editar una compra nunca deja cero asientos vigentes.

#### GAP-C-002 — Posting no garantizado: consecutivo `MAX+1`, idempotencia sin restricción y sin reproceso
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `AsientoContableQueryRepository.siguienteNumeroComprobante` hace `MAX(n)+1` sobre `asiento_contable ∪ comprobante_caja`; el índice único `ux_asiento_empresa_comprobante` (V63) convierte la carrera en excepción. `ContabilizarDocumentoUseCase` comprueba existencia y luego guarda; `asiento_contable` no tiene índice único por origen (V44 solo `idx_asiento_origen`). No hay reintento, job ni endpoint para reprocesar los ERROR del `contabilidad_posting_log`, ni un detector de documentos sin asiento. Colisiones de serie: `OB` (obsequio y obligación), `CE` (cierre mensual y comprobante de egreso).
**Referencia funcional:** implícita (todo documento contabiliza exactamente una vez).
**Problema:** con varios cajeros, las ventas simultáneas pierden su asiento; un evento duplicado puede generar dos asientos.
**Decisión:** implementar.
**Diseño propuesto:** (1) tabla `consecutivo_comprobante(empresa_id, prefijo, ultimo)` con `UPDATE … RETURNING` (bloqueo de fila) o secuencia por empresa y prefijo; (2) índice único parcial `(empresa_id, tipo_origen, origen_id) WHERE estado <> 'ANULADO' AND tipo_origen NOT LIKE 'ANULACION_%' AND tipo_origen NOT IN ('MANUAL', …)`, previa limpieza; (3) `ReprocesoContableJob` (scheduler) que reintenta los ERROR con backoff, más un endpoint `POST /api/contabilidad/asientos/reprocesar` por tipo e id; (4) consulta "documentos sin asiento" (venta, compra, gasto, devolución, merma, abono, NC) en la pantalla de revisión y en la lista de cierre; (5) separar las series `OB`→`OF` para obligaciones y `CE`→`CM` para el cierre (las nuevas numeraciones no reescriben las existentes).
**Impacto técnico:** BE + migración (Flyway + espejo Laravel idempotente) + FE (pestaña "Sin asiento / fallidos").
**Riesgos:** si el índice único encuentra duplicados reales en producción, falla la migración. Mitigación: script de diagnóstico previo y remediación documentada.
**Dependencias:** GAP-C-007 (una sola ruta de posting) para el reproceso uniforme; puede empezar sin él.
**Pruebas:** test de concurrencia (2 hilos × 50 ventas → 100 asientos con consecutivos únicos); evento duplicado → 1 asiento; job reprocesa el ERROR tras corregir la cuenta.
**Criterios de aceptación:** 0 documentos sin asiento después del job; 0 duplicados por origen; consecutivos sin colisión bajo carga.

#### GAP-C-003 — El costo que va al mayor no es costo promedio y 1435 diverge del kardex
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `CompraServiceImpl.actualizarPreciosProducto` sobrescribe `producto.costo` con el costo unitario de la última compra; `VentaServiceImpl.calcularCostoBase` usa ese valor para `costo_linea`; el asiento de compra capitaliza `subtotal − descuento + fletes` en 1435, pero los fletes no entran a `producto.costo`; la NC de compra no restituye el costo; solo los lotes tienen promedio (`LoteStockService:134`). No hay un cruce periódico 1435 ↔ valorización del kardex por bodega.
**Referencia funcional:** 4.3 (costo promedio; Db 6 / Cr 14), 4.25 (kardex).
**Problema:** la utilidad bruta, el inventario del balance y la exógena quedan errados; el error se acumula con cada compra a un precio distinto.
**Decisión:** implementar (con el Bloque B, dueño del kardex).
**Diseño propuesto:** costo promedio ponderado móvil **por empresa + bodega + producto** (o por empresa, según la decisión del Bloque B) recalculado en cada entrada `(saldo_valor + entrada_valor) / (saldo_cant + entrada_cant)`, con prorrateo de fletes y descuentos de cabecera por línea; la salida (venta, merma, obsequio, consumo, traslado) toma el promedio vigente y lo guarda en el movimiento; la NC o devolución de compra sale al costo de la entrada original. En contabilidad: el generador lee el costo **del movimiento de kardex**, no del producto. Agregar a la lista de cierre el control "1435 vs. kardex valorizado" con una tolerancia.
**Impacto técnico:** BE (Compra, Venta, Devolución, Merma, Obsequio, Consumo y kardex), BD (columnas de valor en el kardex/saldo), reporte de conciliación.
**Riesgos:** recosteo histórico; se necesita un ajuste de inventario inicial (asiento de ajuste 1435 vs. 6135/4295) al activar.
**Dependencias:** Bloque B (saldo por bodega/kardex valorizado).
**Pruebas:** golden de compra a 10 y a 12 → venta al promedio 11; flete prorrateado; NC de compra; reconciliación 1435 = kardex.
**Criterios de aceptación:** para toda empresa, `saldo 1435x` = Σ kardex valorizado (±0,5 %) al cierre; la utilidad por producto usa el promedio.

#### GAP-C-004 — Asiento de devolución de venta con fecha, costo y cuentas incorrectos
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `ContabilidadAutoServiceImpl.generarDesdeDevolucion` (470-615): fecha = `venta.fechaEmision`; costo devuelto = `producto.getCosto()` actual; IVA → `IVA_GENERADO`; inventario/costo → `INVENTARIO`/`COSTO_VENTAS` genéricos; reembolso → `CAJA` siempre; el ingreso por cambio va a `INGRESOS_VENTAS` genérico. `DevolucionEntity` tiene `fechaDevolucion`, `metodoDevolucion` y `tesoreriaMovimientoId`, que no se usan.
**Referencia funcional:** 3.9 (NC por devolución: reingreso al costo, reversa de ingreso e IVA, cartera o caja).
**Problema:** las devoluciones de meses cerrados no se contabilizan (fallo silencioso, ver GAP-C-001); 1435, 6135 y 2408 no espejan la venta; la caja del mayor sube aunque el dinero salga del banco.
**Decisión:** implementar (migrar a `DevolucionGenerador`).
**Diseño propuesto:** `DevolucionGenerador` en el registro: fecha = `fechaDevolucion`; el costo y las cuentas se leen de las **líneas de la venta original** (`costo_linea` proporcional, cuentas por categoría, impuesto por impuesto); reembolso por `ResolucionCuentaPago(metodoDevolucion, cuenta bancaria del movimiento de tesorería)`; tercero en todas las líneas de 13/28.
**Impacto técnico:** BE (generador + lector + tests), sin BD.
**Riesgos:** las devoluciones históricas mantienen su asiento; solo se afectan las nuevas (se documenta).
**Dependencias:** GAP-C-003 (costo), GAP-C-007.
**Pruebas:** golden de devolución total, parcial, con cambio, a cartera y con reembolso bancario; mes de la venta cerrado → se contabiliza en el mes de la devolución.
**Criterios de aceptación:** una devolución total de una venta produce exactamente el espejo de sus líneas de ingreso, IVA y costo.

#### GAP-C-005 — La NC/ND electrónica acredita cartera sin tercero y sin relación con la CxC ni con la devolución
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `LectorNotaJpa.cargar` devuelve `clienteId = null`; `NotaCreditoGenerador` debita `INGRESOS_VENTAS` (no la devolución 4175 ni la categoría) y acredita `CLIENTES` sin tercero, aunque la venta haya sido de contado. `FactusNotaService` no cruza la `cuenta_cobrar` ni el registro de devolución; el inventario no se mueve. La fecha es `created_at` (UTC).
**Referencia funcional:** 3.9/3.10/3.11 (NC por devolución y por descuento, ND), 5 (matriz de efectos).
**Problema:** el auxiliar de 1305 por tercero y la exógena 1008 quedan errados; si el usuario además registra la devolución, el ingreso se reversa dos veces; la CxC operativa sigue debiendo.
**Decisión:** implementar (coordinado con el Bloque de ventas).
**Diseño propuesto:** la NC nace de un documento de AURA (devolución o descuento) y **referencia la venta**: el tercero = cliente de la venta; la contrapartida depende del estado de la venta (con saldo → 1305 del cliente y abono a la CxC; pagada → saldo a favor 2805 o reembolso por FP); tipo DESCUENTO → cuenta de descuento/devolución de la categoría sin movimiento de inventario; tipo DEVOLUCION → **un solo asiento** (el de la devolución) y la NC solo guarda su CUFE en la devolución (sin segundo asiento).
**Impacto técnico:** BE (FactusNotaService, lector, generador, CxC), BD (FK `nota_electronica.devolucion_id`/`venta_id`).
**Riesgos:** notas históricas sin tercero: script de corrección que asigne el cliente de la venta referenciada.
**Dependencias:** GAP-C-004; Bloque ventas (flujo NC).
**Pruebas:** NC descuento a crédito, NC de venta de contado, NC ligada a devolución (un solo asiento), ND.
**Criterios de aceptación:** ninguna línea sobre cuentas 13/28 sin tercero; Σ CxC operativa por cliente = saldo 1305 por tercero.

### P1 — HIGH

#### GAP-C-006 — Los asientos de CIERRE mensual contaminan la declaración de IVA y el balance de comprobación
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `generarCierre` cancela 4/5/6 → 3605 en cada mes. `DeclaracionesQueryRepository.movimientos` no excluye `CIERRE` (la sección "Ingresos del período" del 300 da ~0 en meses cerrados); `balanceComprobacion` (por período) tampoco. El ER, la exógena y el EFE sí lo excluyen.
**Referencia funcional:** 4.4 (cancelación, normalmente anual; su periodicidad es una decisión contable).
**Problema:** los reportes fiscales y el balance de prueba son inconsistentes según el mes esté abierto o cerrado.
**Decisión:** mejorar (se mantiene la cancelación mensual, que funciona como un cierre "duro" legítimo, pero se vuelve configurable).
**Diseño propuesto:** (1) parámetro `empresa.cancelacion_resultados = MENSUAL|ANUAL` (default ANUAL, que es lo usual en Colombia; con ANUAL el cierre de mes no genera `CIERRE` y `CierreAnualService` cancela 4/5/6 al 31-dic); (2) una vista o función SQL única `v_movimiento_contable(excluye_cierre)` que usen **todos** los reportes; (3) test de regresión por reporte.
**Impacto técnico:** BE (reportes y cierre), BD (columna en empresa).
**Riesgos:** cambiar la modalidad a mitad de año; se permite solo con el año sin cierres.
**Dependencias:** GAP-C-013.
**Pruebas:** 300 de un mes cerrado = 300 del mismo mes abierto.
**Criterios de aceptación:** ningún reporte de resultados depende de si el mes está cerrado.

#### GAP-C-007 — Motor dual: 13 flujos en el legacy y dos generadores de venta distintos
**Estado:** DIFFERENT (deuda) · **Prioridad:** P1
**AURA actual:** `ContabilidadAutoServiceImpl` (1.584 líneas, `@Autowired` en campos, `ResponseStatusException` genérica) contabiliza compra, devolución, gasto, merma, nómina, pago de nómina, prestación, obligación, cuota, movimiento de caja, tesorería, cierre y sobregiro, y sigue haciendo la reversa. Siguen activos 6 pares de evento/listener legacy. El endpoint manual de venta usa el legacy (H7). Sin tests para esos flujos. El propio `docs/ARQUITECTURA_CONTABILIDAD.md` §11 declara este estrangulamiento como pendiente (E11).
**Referencia funcional:** 7.1 del agente (evento → motor → regla → asiento).
**Problema:** cada corrección se hace dos veces o en un solo lado; las reglas de idempotencia difieren (`asientoVigente` vs. `existePorOrigen`).
**Decisión:** refactorizar por estrangulamiento (sin big bang).
**Diseño propuesto:** migrar al registro en este orden: Compra → Devolución (GAP-C-004) → Gasto → Merma → Tesorería/MovimientoCaja → Obligación/Cuota → Nómina (3) → Cierre/Sobregiro; `ReversarDocumentoUseCase` en `application/` con copia de **todas** las dimensiones (hoy pierde proyecto y frente); el endpoint manual delega en el use case; borrar los listeners legacy al terminar. Cada migración trae su golden file.
**Impacto técnico:** BE; sin BD salvo el índice de GAP-C-002.
**Riesgos:** regresiones de asiento. Mitigación: golden de "asiento legacy = asiento nuevo" antes de cambiar el publicador.
**Dependencias:** GAP-C-002 (idempotencia unificada).
**Pruebas:** por flujo, contado/crédito/mixto/borde + reversa; paridad legacy↔nuevo.
**Criterios de aceptación:** `ContabilidadAutoServiceImpl` eliminado; un solo listener; `grep ContabilidadReversaEvent` = 0.

#### GAP-C-008 — Fechas contables tomadas de `now()`/`created_at` con la JVM en UTC
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** abonos de cobro y pago (`LectorAbonosJpa`: "la fecha contable es hoy"), reversas (`LocalDate.now()`), nómina (`n.getCreatedAt()`), movimiento de caja (`mov.getCreatedAt()`), NC/ND (`n.getCreatedAt()`). La JVM de producción corre en UTC (memoria "zona horaria producción"); después de las 19:00 COT la fecha es la del día siguiente, y el 31 cae en el mes siguiente.
**Referencia funcional:** el documento determina la fecha contable.
**Problema:** asientos en el mes equivocado; arqueos y cierres no amarran; cartera con fechas desplazadas.
**Decisión:** implementar.
**Diseño propuesto:** cada lector toma la **fecha del documento** (`fecha_pago`, `fecha_liquidacion`/fin del período de nómina, fecha del movimiento, fecha de emisión de la nota); `Clock` inyectado con `ZoneId` de la empresa (`America/Bogota` por defecto) para `hoy()`; prohibir `LocalDate.now()` en `contabilidad/**` (regla ArchUnit).
**Impacto técnico:** BE.
**Dependencias:** el arreglo de 3 capas de la zona horaria (memoria, sin desplegar).
**Pruebas:** abono a las 23:30 del 31 → mes corriente; ArchUnit.
**Criterios de aceptación:** 0 usos de `now()` para la fecha contable; todas las fechas contables en la zona de la empresa.

#### GAP-C-009 — La parametrización contable no tiene pantalla
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** existen `/api/contabilidad/configuracion-cuentas` (+`/modo`, `/log`), `/formas-pago`, `/impuestos` y `/asientos/posting-log`, pero el FE no los consume (solo `/categorias-producto`). Tampoco hay FE para causaciones, deterioro ni anticipos en contabilidad.
**Referencia funcional:** 1.8–1.13 (asistente de plantillas, formas de pago, detalle de contabilización).
**Problema:** el contador no puede cambiar las cuentas sin SQL: los defaults del enum operan como hardcode y los errores `CuentaNoParametrizada` no se pueden resolver desde la app.
**Decisión:** implementar (ya priorizado en la memoria "Estrategia contable: validar → config UI → saldos iniciales").
**Diseño propuesto:** "Contabilidad → Parametrización" con pestañas: Conceptos (concepto, cuenta actual, default, guardarraíl y log de cambios), Formas de pago, Impuestos, Categorías, Modo de contabilización, Posting log con **Reprocesar** (GAP-C-002). Selector de cuentas filtrado a auxiliares activas de la clase permitida.
**Impacto técnico:** FE, más ajustes menores de BE (DTO).
**Dependencias:** GAP-C-002 (reproceso), GAP-C-015 (permiso `contabilidad.parametrizar`).
**Pruebas:** E2E (cambiar la cuenta de IVA generado → la venta siguiente usa la nueva).
**Criterios de aceptación:** todo concepto del enum es editable desde la UI con traza en `contabilidad_config_log`.

#### GAP-C-010 — Retenciones sin cuenta por tarifa/concepto; 350 sin desglose; ReteICA sin municipio
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** compra y gasto acreditan `RETEFUENTE_PRACTICADA` (2365), `RETEIVA_PRACTICADA` (2367) y `RETEICA_PRACTICADA` (2368) únicos; `tarifa_retencion.cuenta_contable_id` existe y no se usa; la compra guarda `retefuente_valor` sin la tarifa aplicada; `DeclaracionesServiceImpl` agrupa por prefijos literales.
**Referencia funcional:** 1.12 (tarifa asociada al tercero, desglose especial por tarifa).
**Problema:** el formulario 350 y los certificados exigen base y valor por concepto (compras, servicios, honorarios, arrendamientos…); hoy no se pueden obtener del mayor.
**Decisión:** implementar.
**Diseño propuesto:** tabla `documento_retencion(documento_tipo, documento_id, tarifa_retencion_id, base, porcentaje, valor)` para compras, gastos y documento soporte; el generador acredita la cuenta de la tarifa (fallback al concepto); `asiento_detalle.base_gravable` (GAP-C-027) en esas líneas; 350 por concepto; ReteICA con municipio y actividad en la tarifa.
**Impacto técnico:** BE, BD, FE (selector de tarifa en compra/gasto).
**Dependencias:** GAP-C-007 (Compra/Gasto al registro), GAP-C-027.
**Pruebas:** compra con 2 tarifas → 2 créditos; 350 por concepto.
**Criterios de aceptación:** Σ 2365xx por concepto = renglones del 350.

#### GAP-C-011 — Activos fijos: depreciación mal calculada, baja sin asiento, sin venta de activo
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `ActivoFijoServiceImpl.calcularYGuardar`: `cuota = (valor − residual − acumulada) / vidaUtilMeses` (divide lo remanente por la vida **total**: la depreciación decrece y nunca termina); ignora `fecha_adquisicion` (deprecia activos antes de comprarlos y sin prorrateo); ignora `metodo_depreciacion`; si faltan cuentas **suma la acumulada sin asiento**; escribe el asiento directamente (fuera del motor, sin idempotencia fuerte ni reversa); `darDeBaja` solo cambia el estado; `eliminar` es un soft delete aunque tenga depreciaciones; no hay adquisición ligada a la compra.
**Referencia funcional:** 1.27, 1.28, 4.2, 3.23.
**Problema:** el gasto por depreciación y el valor en libros son incorrectos; las bajas dejan costo y acumulada vivos en el balance.
**Decisión:** implementar.
**Diseño propuesto:** `DepreciacionGenerador` (tipo `DEPRECIACION`, un asiento por período y lote con líneas por activo y CC) con cuota por método (línea recta = (costo − residual)/vida; suma de dígitos; reducción de saldos), inicio en el mes siguiente a la fecha de uso, tope al valor residual; cuentas obligatorias (validación al crear); `BajaActivoGenerador` (Db 1592 acumulada, Db pérdida 5310 / Cr 15xx costo); `VentaActivo` = factura de venta con línea "activo" que dispara la baja + utilidad/pérdida (4245/5310); vínculo `compra_detalle → activo_fijo`; no se elimina con movimientos.
**Impacto técnico:** BE, BD (columnas de método/uso, tabla de baja, vínculo), FE.
**Dependencias:** GAP-C-007; ventas (línea de activo).
**Pruebas:** golden de línea recta de 60 meses = 60 cuotas iguales; alta a mitad de mes; baja con pérdida; venta con utilidad.
**Criterios de aceptación:** Σ depreciación = costo − residual al terminar la vida útil; saldo 1592 = Σ acumulada de las fichas.

#### GAP-C-012 — Saldos iniciales: el descuadre se absorbe sin aviso y no se concilia con los auxiliares
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `AperturaContableServiceImpl.guardar` lleva cualquier diferencia a `RESULTADOS_ACUMULADOS` (3705) o a una cuenta elegida; permite un único asiento de apertura; no valida que las cuentas sean auxiliares; los saldos de 1305/2205/1435/15xx/1592 no se contrastan con la cartera importada, el inventario inicial ni las fichas de activos.
**Referencia funcional:** 2.2/2.3 ("diferencia débito/crédito debe ser cero"; pestañas de inventario, activos, diferidos y contabilidad).
**Problema:** un error de digitación de miles de millones queda escondido en patrimonio; la cartera operativa y 1305 nacen distintas.
**Decisión:** mejorar.
**Diseño propuesto:** apertura por **lotes** (varios documentos APERTURA por módulo) en estado borrador; el descuadre se permite solo con confirmación explícita y queda como advertencia en la lista de cierre; validación de auxiliar/tercero obligatorio; reporte "Apertura vs. auxiliares" (1305 vs. CxC importada, 2205 vs. CxP, 1435 vs. inventario inicial valorizado, 15xx/1592 vs. fichas).
**Impacto técnico:** BE, FE (`contabilidad/saldos-iniciales`).
**Dependencias:** GAP-C-003 (inventario valorizado), GAP-C-011.
**Pruebas:** apertura descuadrada sin confirmar → 422; reporte de cruces.
**Criterios de aceptación:** ninguna apertura descuadrada sin confirmación explícita registrada; cruces al 100 % o con diferencia visible.

#### GAP-C-013 — Balance de prueba sin saldo anterior, rangos ni filtros
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `balanceComprobacion(periodoId)` suma solo los movimientos del `periodo_contable_id` (las reversas quedan en el período de "hoy", no en el de su fecha), sin saldo inicial ni final, sin rango de fechas ni de cuentas, tercero, CC ni nivel, e incluye CIERRE. Solo lo consume la pantalla de períodos.
**Referencia funcional:** 2.4, 4.17 (fechas, rango de cuentas, tercero, moneda, cancelación de cuentas; PDF/Excel).
**Problema:** es el reporte de trabajo n.º 1 del contador; sin él no se valida la apertura ni el cierre.
**Decisión:** implementar.
**Diseño propuesto:** `GET /api/contabilidad/balance-prueba?desde&hasta&cuentaDesde&cuentaHasta&nivel&terceroId&centroCostoId&incluirCierre` con saldo anterior, débito, crédito y saldo final, subtotales por nivel jerárquico y agrupación por tercero; por fecha (no por `periodo_contable_id`); Excel/PDF; pantalla propia.
**Impacto técnico:** BE (query con CTE jerárquico), FE.
**Dependencias:** GAP-C-006 (vista común).
**Pruebas:** Σ débitos = Σ créditos por rango; saldo final(mes n) = saldo anterior(mes n+1).
**Criterios de aceptación:** el balance de prueba cuadra en todos los niveles y exporta a Excel/PDF.

#### GAP-C-014 — Asiento manual legacy y plan de cuentas sin guardas de integridad
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `AsientoContableServiceImpl.crear` (usado por `FE: contabilidad/asientos`) no valida que `cuentaId` sea de la empresa, auxiliar y activa, ni que cada línea sea no negativa y de un solo lado; `crearComprobante` duplica la lógica de `NotaDiario`. `PlanCuentasServiceImpl.actualizar` permite cambiar tipo, naturaleza, `auxiliar` y `padre_id` con movimientos (y `padre_id` de otra empresa).
**Referencia funcional:** 1.11.
**Problema:** asientos contra cuentas de agrupación o de otro tenant (fuga entre empresas); reportes jerárquicos rotos al cambiar la naturaleza de una cuenta con saldo.
**Decisión:** implementar.
**Diseño propuesto:** validador único `CuentaMovimientoValidator` (misma empresa, auxiliar, activa, tercero/CC si la cuenta lo exige) usado por todo escritor directo; `POST /asientos` delega en `NotaDiarioService`; en el PUC, bloquear el cambio de tipo, naturaleza y auxiliar→agrupación cuando hay movimientos, y validar `padre_id` en la empresa con nivel = padre + 1.
**Impacto técnico:** BE.
**Dependencias:** ninguna.
**Pruebas:** cuenta de otra empresa → 404; agrupación → 422; cambio de naturaleza con movimientos → 409.
**Criterios de aceptación:** ningún `asiento_detalle` referencia una cuenta de agrupación o de otra empresa (consulta de verificación = 0).

#### GAP-C-015 — Sin permisos por acción en operaciones contables sensibles
**Estado:** MISSING · **Prioridad:** P1
**AURA actual:** los controladores de `/api/contabilidad/**`, `/api/periodos-contables/**`, `/api/activos-fijos/**` y `/api/tesoreria/**` no tienen control de rol efectivo (ver `docs/PLAN_SEGURIDAD.md`: 642 endpoints sin control y `@PreAuthorize` inerte). El menú filtra por submódulo, pero el backend no.
**Referencia funcional:** 1.3–1.5 (permisos detallados: contabilizaciones, formas de pago, replicar).
**Problema:** cualquier usuario autenticado puede reabrir períodos, anular comprobantes, ejecutar el cierre anual o cambiar las cuentas.
**Decisión:** implementar (el mecanismo lo construye el Bloque A; aquí se define el catálogo contable).
**Diseño propuesto:** catálogo de acciones: `contabilidad.asiento.crear|anular|reversar`, `contabilidad.periodo.cerrar|reabrir`, `contabilidad.cierre_anual.ejecutar`, `contabilidad.parametrizar`, `contabilidad.saldos_iniciales`, `contabilidad.importar`, `tesoreria.conciliar|cerrar_extracto`, `activos.depreciar|baja`, `reportes.contables.ver|exportar`.
**Dependencias:** Bloque A (motor de permisos).
**Pruebas:** matriz rol × endpoint (403 esperado).
**Criterios de aceptación:** 100 % de los endpoints del bloque con acción declarada y probada.

### P2 — MEDIUM (formato compacto)

#### GAP-C-016 — Conciliación bancaria: doble mecanismo, sin partidas conciliatorias arrastradas ni reporte
**Estado:** PARTIAL · **AURA:** `TesoreriaController` `/conciliar`, `/conciliar-lote` (bandera por movimiento) + `ConciliacionBancariaService` (extracto); las partidas en tránsito solo se muestran; match 1:1; solo CSV genérico; el saldo inicial del extracto no se valida contra el final anterior. **Ref.:** 4.5/4.7. **Decisión:** unificar en extractos (deprecar la bandera), tabla `partida_conciliatoria` (libro no en banco y banco no en libro) que pasa al siguiente extracto, matching N:1 y 1:N, validación de continuidad de saldos, PDF "Conciliación bancaria" firmado. **Dep.:** GAP-C-008. **Aceptación:** saldo libro + partidas = saldo extracto, con reporte exportable.

#### GAP-C-017 — Asiento sin sucursal y reglas solo por empresa
**Estado:** MISSING · **AURA:** `asiento_contable` sin `sucursal_id`; resolución sin sucursal, tipo de documento ni tipo de tercero. **Ref.:** 1.9, 4.18. **Decisión:** `asiento_detalle.sucursal_id` (backfill desde el documento), `cuenta_config` con `sucursal_id` opcional (precedencia sucursal > empresa); ER/BP por sucursal. **Dep.:** Bloque A (sucursal scope).

#### GAP-C-018 — Formas de pago sin grupo ni tercero de cartera (ADDI/Sistecrédito)
**Estado:** PARTIAL · **AURA:** `forma_pago_contable` sin `grupo` ni `tercero_id`; la venta solo va a 1305 si el método es `CREDITO`; el recaudo con financiera queda sin tercero. **Ref.:** 1.10/1.13. **Decisión:** `grupo` (CONTADO/CREDITO/FINANCIERA/BILLETERA/BONO/CHEQUE), `tercero_id` y `documentos_aplica`; FINANCIERA → Db 13xx con tercero = financiera; acción "Duplicar forma de pago".

#### GAP-C-019 — Sin vista previa de contabilización
**Estado:** MISSING · **Ref.:** 2.17. **Decisión:** `POST /api/contabilidad/vista-previa/{tipo}` que ejecuta el generador sobre un DTO sin persistir (los generadores ya son puros: el lector acepta un documento en memoria). Botón "Ver contabilización" en compra, gasto y venta de oficina. **Dep.:** GAP-C-007.

#### GAP-C-020 — Traslado de cuentas contables
**Estado:** MISSING · **Ref.:** 1.14. **Decisión:** herramienta "Reclasificar": por rango de fechas/documentos genera un **asiento de reclasificación** (no reescribe líneas) de la cuenta A a la B; opción de mover también la parametrización (`cuenta_config`, categorías, formas de pago); excluye los períodos cerrados. **Dep.:** GAP-C-015.

#### GAP-C-021 — Traslado/fusión de terceros en lo contable
**Estado:** MISSING · **Ref.:** 1.15. **Decisión:** fusión que reasigna `tercero_id` en `asiento_detalle` y en los auxiliares **solo** de períodos abiertos y de documentos no reportados a la DIAN; en cerrados, asiento de reclasificación entre terceros; bitácora. **Dep.:** Bloque A (maestro de terceros).

#### GAP-C-022 — Reportes de compras inexistentes; filtros de ventas limitados; sin procesamiento asíncrono
**Estado:** MISSING/PARTIAL · **AURA:** sin endpoint de compras (`FE: reportes/compras` vacío); ventas Excel/PDF solo por fechas. **Ref.:** 2.18, 3.21. **Decisión:** motor de reportes con filtros comunes (empresa, sucursal, fechas, tercero, vendedor/comprador, grupo, CC, forma de pago, producto); `reporte_solicitud` asíncrono (`@Async("reportesExecutor")`), almacenamiento R2 y aviso por campana/correo.

#### GAP-C-023 — Sin comparativos ni ER por centro de costo en columnas
**Estado:** MISSING · **Ref.:** 4.17. **Decisión:** balance/ER comparativo mes vs. mes y año vs. año (fase 5 del plan de cierre) y ER con columnas por CC/sucursal; notas a los EEFF (plantilla).

#### GAP-C-024 — Presupuesto
**Estado:** MISSING (diseñado) · **Decisión:** ejecutar `docs/PLAN_PRESUPUESTO.md` tal cual (ejecución leída del mayor). No se rediseña aquí.

#### GAP-C-025 — Obligaciones financieras incompletas
**Estado:** PARTIAL · **AURA:** interés por caja al pagar; sin causación mensual, sin porción corriente/no corriente, sin anulación del pago de cuota, sin GMF ni seguros en la cuota. **Decisión:** causación de interés (evento `CAUSACION`), reclasificación de la porción corriente al cierre anual, reversa del pago de cuota y conceptos adicionales.

#### GAP-C-026 — Saldo bancario con doble fuente de verdad
**Estado:** DIFFERENT · **AURA:** `cuenta_bancaria.saldo_actual` se muta en paralelo al mayor. **Decisión:** que el saldo mostrado salga del mayor (o de una vista); `saldo_actual` queda en solo lectura y se deprecia; se conserva el comparador como control.

#### GAP-C-027 — La partida no guarda base gravable, tarifa ni documento de cruce
**Estado:** MISSING · **Decisión:** `asiento_detalle.base_gravable`, `porcentaje`, `documento_cruce_tipo/id` (para cartera por documento, certificados y 350/exógena sin heurísticas).

#### GAP-C-028 — Informes de activos fijos
**Estado:** PARTIAL · **Ref.:** 4.19. **Decisión:** informes por CC, ubicación, responsable (tercero), categoría y depreciación mensual/anual con Excel/PDF. **Dep.:** GAP-C-011.

#### GAP-C-029 — IVA de compra siempre descontable
**Estado:** PARTIAL · **AURA:** la compra va siempre a 2408 descontable. **Decisión:** flag `iva_mayor_valor_costo` por empresa, impuesto o documento (no responsables de IVA, IVA no descontable) → se capitaliza en inventario o gasto.

#### GAP-C-030 — Asiento de nómina: fecha y conceptos sin mapear
**Estado:** PARTIAL · **AURA:** fecha = `created_at`; retefuente de nómina, FSP y embargos no tienen concepto propio (ya anotado en la memoria de nómina electrónica). **Decisión:** fecha = fin del período de nómina; conceptos `RETEFUENTE_NOMINA` (2365xx) y `FSP_POR_PAGAR`; se migra con GAP-C-007.

#### GAP-C-031 — Declaración de ICA y certificados de retención a terceros
**Estado:** MISSING · **Decisión:** borrador de ICA por municipio y certificados de retención en la fuente/IVA/ICA por tercero y año en PDF. **Dep.:** GAP-C-010, GAP-C-027. **Nota:** validar la normativa vigente (DIAN/municipios) antes de implementar.

#### GAP-C-032 — Exógena: formatos faltantes
**Estado:** PARTIAL · **Decisión:** 1003 (retenciones que nos practicaron), 1010 (socios), 1012 (inversiones/cuentas) y verificación contra la resolución vigente del año. **Dep.:** GAP-C-027.

### P3 — LOW

#### GAP-C-033 — Replicación de reglas a otra empresa o forma de pago
**Estado:** MISSING · **Decisión:** "Copiar parametrización" de la empresa A a la B (mismos códigos PUC) y duplicar una forma de pago.

#### GAP-C-034 — Conciliación automática con parsers por banco
**Estado:** PARTIAL · **Decisión:** perfiles de importación por banco (Excel/OFX/CSV con mapeo de columnas) y reglas automáticas GMF/comisión → ajuste sugerido. El PDF se deja como mejora opcional.

#### GAP-C-035 — Ficha de activo extendida (placa, serial, póliza, mantenimiento, activo padre, adiciones)
**Estado:** MISSING · **Decisión:** tablas `activo_poliza` y `activo_mantenimiento`, campos de placa/serial/padre y adición que incrementa el costo y recalcula la cuota.

#### GAP-C-036 — Dashboard financiero
**Estado:** PARTIAL · **Decisión:** tarjetas de disponible (11xx), CxC/CxP vencidas, utilidad del mes (ER) y documentos sin asiento.

#### GAP-C-037 — Defensa en profundidad en la BD
**Estado:** PARTIAL · **AURA:** el cuadre y el período cerrado solo se validan en la app; `asiento_detalle ON DELETE CASCADE`. **Decisión:** trigger `DEFERRABLE` de cuadre al commit y trigger que rechaza INSERT/UPDATE/DELETE en asientos de un período CERRADO (con bypass por GUC de sesión para la reapertura).

---

## 6. Fortalezas (conservar)

1. **Contabilización perpetua en línea**: el costo de ventas y el inventario se registran por documento. Es mejor que el proceso mensual de la referencia (4.3), porque no hay un "costo de ventas pendiente". Lo que hay que corregir es la base del costo (GAP-C-003), no el modelo.
2. **Dominio puro con cuadre imposible de violar** (`AsientoBuilder`/`ReglasAsiento`) + generadores pequeños con tests y golden files (`VentaGeneradorTest`, `GoldenAsientos`, `AbonoGeneradoresTest`, …).
3. **AFTER_COMMIT + PostingLog**: la venta nunca cae por un fallo contable (le falta el reproceso, GAP-C-002).
4. **Resolución en capas** (cuenta bancaria → forma de pago → concepto; producto → categoría → concepto; impuesto por producto) y guardarraíles por clase PUC con log de cambios. No hay códigos PUC literales en los generadores.
5. **Reversión por contraasiento** con el original conservado (V176), notas con reversión automática, soportes y plantillas.
6. **Períodos por fecha** con apertura automática, cierre en orden, reapertura con motivo y evento (V171), y la validación de borradores, extractos y descuadres antes del cierre.
7. **Cierre anual colombiano** (provisión de renta, traslado a 3705, distribución y dividendos), **devengos** (causaciones, diferidos, deterioro, anticipos) y **EEFF NIIF completos** (EFE indirecto, cambios en el patrimonio): superan la referencia.
8. **Modo revisión** para el contador (borradores y aprobación masiva) y detección de descuadres.
9. **Dimensiones** CC/proyecto/frente en la partida (reparto de nómina por frente).
10. **Reporte gerencial por cruces** y **libros** (auxiliar por tercero, diario, mayor) con Excel.
11. **Importador Excel** con validar/confirmar (PUC, terceros, saldos, cartera abierta).

## 7. No aplicables (hoy)

| Capacidad de referencia | Razón |
|---|---|
| Diferencia en cambio / moneda extranjera (4.9, 3.16) | Fuera de alcance declarado hasta que exista un cliente importador (`DISENO_CONTABILIDAD_AVANZADA.md` §14). Reevaluar con el primer cliente multimoneda. |
| Consolidación contable multiempresa | Declarado fuera de alcance; la multiempresa operativa existe. |
| Costos de producción clase 7 / CIF | Producción fuera del mercado actual (se reevalúa en la Fase 9 global). |
| Conciliación desde PDF del banco (4.7 tal como se observó) | Se reemplaza por perfiles de importación estructurada (GAP-C-034); el PDF es frágil. |
| Asistente de plantillas por actividad económica (1.8) literal | AURA lo resuelve mejor con categorías contables + impuestos; basta con un "asistente de categorías" (dentro de GAP-C-009). |

---

## 8. Fases del bloque (por dependencias)

```
C0 Integridad del posting ──► C1 Costo e inventario contable ──► C2 Motor único
      (001, 002)                  (003, 004, 005)                  (007, 008)
         │                              │                              │
         └──────────────► C3 Parametrización visible ◄─────────────────┘
                           (009, 010, 015*, 018, 019)
                                        │
             ┌──────────────────────────┼─────────────────────────┐
             ▼                          ▼                         ▼
   C4 Cierres y reportes contables   C5 Activos fijos       C6 Bancos
   (006, 012, 013, 023, 027, 031,    (011, 028, 035)        (016, 025, 026, 034)
    032, 037)
             │
             ▼
   C7 Herramientas del contador (014, 020, 021, 033)
             │
             ▼
   C8 Reportes operativos y BI (022, 036) · C9 Presupuesto y dimensiones (024, 017, 029, 030)
```
`*` GAP-C-015 depende del motor de permisos del Bloque A.

| Fase | Objetivo | Gaps | Depende de | Salida verificable |
|---|---|---|---|---|
| C0 | Todo documento contabiliza exactamente una vez y nunca en un mes cerrado | 001, 002 | — | 0 documentos sin asiento, 0 duplicados, test de concurrencia |
| C1 | El costo del mayor = el costo del kardex | 003, 004, 005 | C0; Bloque B (kardex valorizado por bodega) | 1435 = kardex; devolución/NC espejo de la venta |
| C2 | Un solo motor, fechas correctas | 007, 008 | C0 | Legacy eliminado; ArchUnit sin `now()` |
| C3 | El contador parametriza sin SQL | 009, 010, 015, 018, 019 | C2 (parcial), Bloque A | Pantalla de parametrización + reproceso |
| C4 | Reportes contables confiables y cierre asistido | 006, 012, 013, 023, 027, 031, 032, 037 | C2 | Balance de prueba, 300/350 coherentes, fases 2–5 del plan de cierre |
| C5 | Activos con ciclo de vida completo | 011, 028, 035 | C2 | Depreciación correcta, baja/venta con asiento |
| C6 | Bancos conciliados con arrastre | 016, 025, 026, 034 | C2, 008 | Reporte de conciliación, partidas conciliatorias |
| C7 | Herramientas de corrección | 014, 020, 021, 033 | C3, Bloque A (terceros) | Reclasificación y fusión trazables |
| C8/C9 | Reportes async, BI, presupuesto, sucursal | 022, 036, 024, 017, 029, 030 | C4 | Reportes de compras, presupuesto en ejecución |

Estrategia de despliegue: cada fase con migración Flyway + espejo Laravel **idempotente** (memoria: el esquema lo aplica Laravel; `ddl-auto=validate` no tolera tipos distintos, ver el gotcha de `ADD COLUMN IF NOT EXISTS`); primero local (`aura-pos`) y **nunca** directo a la BD de producción remota; los flags por empresa (`cancelacion_resultados`, costo promedio) permiten activar gradualmente.

---

## 9. Tareas

### P0 y P1 (plantilla completa)

## TASK-C-001 — Guard de período contable en documentos operativos

**Épica:** C0 Integridad del posting
**Módulo:** Contabilidad / Ventas / Compras / Caja
**Tipo:** Backend
**Prioridad:** P0
**Estado:** Proposed

### Problema
Los documentos se confirman en meses cerrados y su asiento falla después del commit.

### Evidencia AS-IS
- Archivo: `BE: services/implementations/{Venta,Compra,Gasto,Devolucion,Merma,CuentaCobrar,CuentaPagar,TurnoCaja,ReciboCaja,ObligacionFinanciera,Tesoreria}ServiceImpl.java`
- Clase/componente: `PeriodoContableResolver` (solo lo usa el motor)
- Tabla: `periodo_contable`
- Endpoint: `POST /api/ventas`, `POST/PUT /api/compras`, `POST /api/gastos`, …
- Comportamiento: 201 al crear; el asiento falla en AFTER_COMMIT (`PeriodoCerradoException` → `PostingLog`).

### Referencia funcional
Baseline 2.1 (fecha de bloqueo).

### Decisión
Implementar.

### Diseño propuesto
`PeriodoContablePort.exigirAbierto(empresaId, fecha)` (lectura, sin abrir meses) invocado al inicio de crear/editar/anular en cada servicio publicador. Mensaje: "El período MM/AAAA está cerrado: cambie la fecha o reabra el período en Contabilidad → Períodos".

### Backend
Nuevo método en el puerto y en el adapter; llamadas en ≈11 servicios; `GlobalException` 422 con el código `PERIODO_CERRADO`.

### Frontend
Mostrar el mensaje en POS, compras, gastos, devoluciones, mermas y abonos (interceptor genérico ya existente).

### Base de datos
Ninguna.

### API
Respuesta 422 `{codigo:"PERIODO_CERRADO", periodo:"2026-08"}`.

### Seguridad y permisos
Ninguno (la reapertura sigue en GAP-C-015).

### Inventario/contabilidad
Impide movimientos de kardex con fecha en un mes cerrado (coordinar con el Bloque B).

### Auditoría
Registrar el intento bloqueado en `error_log` con usuario y documento.

### Migración de datos
Script de diagnóstico: documentos con fecha en un período CERRADO sin asiento vigente → lista para el contador.

### Pruebas unitarias
Guard: abierto, inexistente (no bloquea), cerrado (bloquea).

### Pruebas integración
Venta, compra, gasto y abono con fecha cerrada → 422 y 0 filas nuevas.

### Pruebas E2E
POS con el mes anterior cerrado y fecha manual retroactiva → mensaje.

### Dependencias
Ninguna.

### Riesgos
Bloquear la operación si alguien cierra el mes en curso (ya está prohibido por el servicio).

### Criterios de aceptación
- [ ] Ningún servicio publicador confirma un documento con fecha en un período CERRADO.
- [ ] El script de diagnóstico lista los documentos huérfanos existentes.

### Definition of Done
- [ ] Código integrado
- [ ] Migraciones verificadas
- [ ] Pruebas verdes
- [ ] Permisos probados
- [ ] Auditoría validada
- [ ] Documentación actualizada

## TASK-C-002 — Reproceso atómico de documentos editados (compra)

**Épica:** C0 · **Módulo:** Contabilidad/Compras · **Tipo:** Backend · **Prioridad:** P0 · **Estado:** Proposed

### Problema
La edición de una compra reversa y regenera en dos transacciones: si falla la segunda, la compra queda sin asiento.

### Evidencia AS-IS
- Archivo: `BE: event/CompraContabilizacionListener.java`
- Clase: `ContabilidadAutoServiceImpl.reversar` y `generarDesdeCompra` (ambos REQUIRES_NEW)
- Tabla: `asiento_contable`
- Endpoint: `PUT /api/compras/{id}`
- Comportamiento: la reversa se confirma aunque la regeneración falle.

### Referencia funcional
7.4 del agente (reversión antes que borrado; trazabilidad).

### Decisión
Implementar.

### Diseño propuesto
`ReprocesarDocumentoUseCase.ejecutar(tipo, id)` en una sola transacción REQUIRES_NEW: genera el nuevo asiento **antes**; si tiene éxito, crea la reversa del vigente; si falla, rollback total y `PostingLog` ERROR con `accion=REPROCESO`.

### Backend
Use case nuevo en `contabilidad/application`; el listener de compra delega en él.

### Frontend
Ninguno.

### Base de datos
Ninguna.

### API
Ninguna nueva.

### Seguridad y permisos
N/A.

### Inventario/contabilidad
Asiento vigente siempre = el último válido.

### Auditoría
`PostingLog` con estado REPROCESO_OK/ERROR.

### Migración de datos
Consulta de compras con `originales ≤ reversas` y estado vigente → regenerar con TASK-C-003.

### Pruebas unitarias
Use case con un generador que lanza excepción → sin reversa.

### Pruebas integración
Edición con cuenta inexistente → el asiento original sigue vigente.

### Pruebas E2E
N/A.

### Dependencias
TASK-C-003 (reproceso manual para las huérfanas).

### Riesgos
Bajo.

### Criterios de aceptación
- [ ] Nunca hay una compra con 0 asientos vigentes después de editarla.

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] Documentación actualizada

## TASK-C-003 — Consecutivo seguro, idempotencia en BD, reproceso y detector de documentos sin asiento

**Épica:** C0 · **Módulo:** Contabilidad · **Tipo:** Backend/DB/Frontend · **Prioridad:** P0 · **Estado:** Proposed

### Problema
`MAX+1` choca con concurrencia; sin índice único por origen; los ERROR no se reprocesan; nadie detecta los documentos sin asiento; las series OB y CE se comparten entre tipos distintos.

### Evidencia AS-IS
- Archivo: `BE: repositories/contabilidad/AsientoContableQueryRepository.java:122-141`; `BE: contabilidad/infrastructure/persistence/AsientoRepositorioJpa.java`; `BE: ContabilidadAutoServiceImpl.java:118-128` (prefijos)
- Tabla: `asiento_contable` (V44, V63)
- Endpoint: `GET /api/contabilidad/asientos/posting-log` (solo consulta)
- Comportamiento: `DataIntegrityViolation` → PostingLog ERROR, sin reintento.

### Referencia funcional
Implícita.

### Decisión
Implementar.

### Diseño propuesto
1) Tabla `consecutivo_comprobante(empresa_id, prefijo, ultimo, PRIMARY KEY(empresa_id, prefijo))` inicializada con el MAX actual (de `asiento_contable ∪ comprobante_caja`); `UPDATE … SET ultimo = ultimo+1 RETURNING ultimo`.
2) Índice único parcial `ux_asiento_origen_vigente(empresa_id, tipo_origen, origen_id) WHERE estado IN ('BORRADOR','CONTABILIZADO') AND tipo_origen NOT IN ('MANUAL','APERTURA','DEPRECIACION') AND tipo_origen NOT LIKE 'ANULACION_%'`. Nota: la compra editada tiene N originales; por eso primero hay que marcar el original reversado con `revertido_por_id` y excluirlo (`AND revertido_por_id IS NULL`).
3) `ReprocesoContableJob` (@Scheduled cada 15 min, `@Async` propio) sobre `posting_log` ERROR sin éxito posterior, con máximo de intentos.
4) `POST /api/contabilidad/asientos/reprocesar {tipoOrigen, origenId}` y `/reprocesar-fallidos`.
5) `GET /api/contabilidad/asientos/sin-asiento?desde&hasta` (ventas, compras, gastos, devoluciones, mermas, abonos, NC/ND, obsequios y consumos sin asiento vigente).
6) Nuevas series: obligación `OF`, cierre mensual `CM` (sin renumerar lo histórico).

### Backend
Adapter de consecutivo, job, endpoints y query.

### Frontend
Pestaña "Fallidos / Sin asiento" en `contabilidad/revision` con el botón Reprocesar.

### Base de datos
Flyway V184+ (y espejo Laravel idempotente): tabla de consecutivos y seed; columna `revertido_por_id` para los automáticos si no aplica ya (existe desde V174 para las notas: reutilizar); índice único con precheck.

### API
Ver el diseño.

### Seguridad y permisos
`contabilidad.reprocesar`.

### Inventario/contabilidad
N/A.

### Auditoría
PostingLog con `intento` y `origen = JOB|MANUAL`.

### Migración de datos
Script de diagnóstico de duplicados por origen antes de crear el índice; plan de remediación (anular el duplicado más reciente).

### Pruebas unitarias
Consecutivo por prefijo; job con backoff.

### Pruebas integración
2 hilos × 50 ventas; evento duplicado; reproceso tras corregir la cuenta.

### Pruebas E2E
Revisión → Sin asiento → Reprocesar.

### Dependencias
TASK-C-001.

### Riesgos
Falla del índice por datos históricos (mitigado con el precheck).

### Criterios de aceptación
- [ ] 100 ventas concurrentes = 100 asientos con consecutivos únicos.
- [ ] Un evento duplicado no crea un segundo asiento.
- [ ] El job deja en 0 los fallidos recuperables.
- [ ] El detector lista 0 documentos sin asiento en el ambiente de prueba.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas (Flyway + Laravel) · [ ] Pruebas verdes · [ ] Permisos probados · [ ] Documentación actualizada

## TASK-C-004 — Costo promedio ponderado como base del asiento y control 1435 vs. kardex

**Épica:** C1 Costo e inventario contable · **Módulo:** Inventario/Contabilidad · **Tipo:** Backend/DB · **Prioridad:** P0 · **Estado:** Proposed

### Problema
El costo de ventas usa el último costo de compra; los fletes no entran al costo; la NC de compra no restituye.

### Evidencia AS-IS
- Archivo: `BE: CompraServiceImpl.java:1545`, `VentaServiceImpl.java:839`, `ContabilidadAutoServiceImpl.java:~245`
- Tabla: `producto.costo`, `venta_detalle.costo_linea`, kardex
- Comportamiento: `producto.setCosto(item.getCostoUnitario())`.

### Referencia funcional
4.3, 4.25.

### Decisión
Implementar junto con el Bloque B.

### Diseño propuesto
Promedio móvil en el saldo de inventario (dimensión del Bloque B: empresa + bodega + producto); cada movimiento guarda `costo_unitario` y `costo_total`; prorrateo de fletes/descuentos por línea al valor; las salidas usan el promedio vigente; los generadores de venta, merma, obsequio, consumo, devolución y traslado leen el costo del movimiento.

### Backend
`CostoPromedioService` (llamado por el kardex); cambiar `calcularCostoBase`; lectores de los generadores.

### Frontend
Kardex valorizado (Bloque B); indicador "1435 vs. kardex" en la lista de cierre.

### Base de datos
Columnas de valor en el kardex/saldo (definidas por el Bloque B); flag por empresa `costeo = PROMEDIO`.

### API
`GET /api/contabilidad/controles/inventario-vs-mayor?hasta`.

### Seguridad y permisos
Lectura contable.

### Inventario/contabilidad
Ajuste de arranque: asiento 1435 vs. 6135/4295 por la diferencia inicial.

### Auditoría
Traza del recosteo.

### Migración de datos
Recalcular el promedio desde el último conteo físico o la apertura (script idempotente) y generar el ajuste contable de arranque.

### Pruebas unitarias
Promedio con 3 entradas; flete prorrateado; salida parcial.

### Pruebas integración
Compra→venta→NC compra→devolución: 1435 = kardex.

### Pruebas E2E
Reporte de control en verde.

### Dependencias
Bloque B (saldo/kardex por bodega); TASK-C-001.

### Riesgos
Recosteo histórico; rendimiento del kardex (bloqueo por fila del saldo).

### Criterios de aceptación
- [ ] |1435 − kardex valorizado| ≤ 0,5 % por empresa al cierre.
- [ ] El costo de la venta = promedio vigente en el momento de la venta.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Documentación actualizada

## TASK-C-005 — `DevolucionGenerador` espejo de la venta original

**Épica:** C1 · **Módulo:** Ventas/Contabilidad · **Tipo:** Backend · **Prioridad:** P0 · **Estado:** Proposed

### Problema
Fecha, costo, cuentas y medio de reembolso incorrectos en el asiento de devolución.

### Evidencia AS-IS
- Archivo: `BE: ContabilidadAutoServiceImpl.java:470-615`; `BE: entity/DevolucionEntity.java`
- Endpoint: `POST /api/devoluciones`
- Comportamiento: `fecha = venta.fechaEmision`; `costo = producto.getCosto()`; reembolso a `CAJA`.

### Referencia funcional
3.9, matriz de efectos (§5).

### Decisión
Implementar (migración al registro).

### Diseño propuesto
Lector que une `devolucion_detalle` con `venta_detalle` (costo y cuentas por proporción); fecha = `fechaDevolucion`; reembolso por `ResolucionCuentaPago(metodoDevolucion, cuenta del movimiento de tesorería)`; tercero en 1305/2805; el ingreso del cambio va a la cuenta de la categoría.

### Backend
`DevolucionGenerador`, `LectorDevolucion(Jpa)`; `DevolucionServiceImpl` publica `DocumentoContabilizableEvent("DEVOLUCION")`.

### Frontend
Ninguno.

### Base de datos
Ninguna.

### API
Ninguna.

### Seguridad y permisos
N/A.

### Inventario/contabilidad
Reingreso al costo original (o al promedio, si la política del Bloque B lo define así: documentarlo).

### Auditoría
PostingLog.

### Migración de datos
No se reescriben las devoluciones históricas; el informe de diferencias queda para el contador.

### Pruebas unitarias
Golden: total, parcial, con cambio, a cartera, reembolso por transferencia.

### Pruebas integración
Venta en un mes cerrado + devolución en el mes abierto → asiento en el mes abierto.

### Pruebas E2E
N/A.

### Dependencias
TASK-C-001, TASK-C-004 (base del costo), TASK-C-012.

### Riesgos
Diferencias con los asientos históricos (se documentan).

### Criterios de aceptación
- [ ] Devolución total = espejo exacto del asiento de la venta (ingreso, IVA, costo).
- [ ] El reembolso bancario acredita la cuenta del banco.

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] Documentación actualizada

## TASK-C-006 — NC/ND electrónica con tercero y cruce con CxC/devolución

**Épica:** C1 · **Módulo:** Facturación electrónica/Contabilidad · **Tipo:** Backend/DB · **Prioridad:** P0 · **Estado:** Proposed

### Problema
Crédito a 1305 sin tercero, sin cruce operativo y con posible doble reversa.

### Evidencia AS-IS
- Archivo: `BE: contabilidad/infrastructure/persistence/LectorNotaJpa.java`; `NotaCreditoGenerador.java`; `FactusNotaService.java:384-395`
- Tabla: `nota_electronica`
- Endpoint: `POST /api/nota-electronica/…`

### Referencia funcional
3.9–3.11.

### Decisión
Implementar.

### Diseño propuesto
`nota_electronica.venta_id` (derivable de `facturaLocal`) y `devolucion_id` opcional; tercero = cliente de la venta; si hay `devolucion_id` → no genera asiento (lo hace la devolución); si no → NC de descuento: Db cuenta de devolución/descuento de la categoría + Db IVA del impuesto / Cr 1305 del cliente (si la venta tiene saldo, abona la CxC) o 2805 (saldo a favor). ND espejo.

### Backend
Lector, generadores, abono en `cuenta_cobrar`.

### Frontend
En el formulario de NC: elegir "Devolución de mercancía (ir a Devoluciones)" o "Descuento/ajuste de valor".

### Base de datos
Columnas `venta_id` y `devolucion_id` + backfill por `factus_numero`.

### API
Sin cambios de contrato, más los campos nuevos.

### Seguridad y permisos
`facturacion.nota_credito.emitir`.

### Inventario/contabilidad
La NC de descuento no mueve inventario.

### Auditoría
Relación documental venta → NC.

### Migración de datos
Script que asigna `tercero_id` a las líneas 1305 de los asientos NC/ND históricos usando la venta referenciada (en períodos abiertos; en cerrados, asiento de reclasificación).

### Pruebas unitarias
Venta a crédito, venta de contado, NC ligada a devolución.

### Pruebas integración
Σ CxC por cliente = 1305 por tercero después de la NC.

### Pruebas E2E
Emitir NC → estado de cuenta del cliente actualizado.

### Dependencias
TASK-C-005; Bloque ventas.

### Riesgos
Notas históricas sin la venta ubicable (se listan para gestión manual).

### Criterios de aceptación
- [ ] 0 líneas en 13xx/28xx sin tercero.
- [ ] Una devolución con NC genera exactamente un asiento.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Documentación actualizada

## TASK-C-007 — Vista común de movimientos y modalidad de cancelación de resultados

**Épica:** C4 · **Módulo:** Contabilidad/Reportes · **Tipo:** Backend/DB · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Los asientos CIERRE mensuales anulan los resultados en los reportes que no los excluyen.

### Evidencia AS-IS
- Archivo: `BE: DeclaracionesQueryRepository.java:52-65`; `AsientoContableQueryRepository.java:556-604`; `ContabilidadAutoServiceImpl.java:1017`
- Comportamiento: 300 con ingresos ≈ 0 en meses cerrados.

### Referencia funcional
4.4.

### Decisión
Mejorar.

### Diseño propuesto
`empresa.cancelacion_resultados` (`ANUAL` por defecto para empresas nuevas; las existentes conservan `MENSUAL`); con ANUAL, el cierre de mes no genera CIERRE y `CierreAnualService` agrega la cancelación 4/5/6 → 3605 al 31-dic antes del traslado. Función SQL `fn_movimientos(empresa, desde, hasta, incluir_cierre)` que usan todos los repositorios de reportes.

### Backend
Refactor de los repositorios de reportes; `PeriodoContableServiceImpl` condicional.

### Frontend
Opción en la parametrización (TASK-C-009).

### Base de datos
Columna + función.

### API
Sin cambios.

### Seguridad y permisos
`contabilidad.parametrizar`.

### Inventario/contabilidad
N/A.

### Auditoría
Cambio de modalidad en `contabilidad_config_log`; solo permitido si el año no tiene cierres.

### Migración de datos
Ninguna (las existentes quedan en MENSUAL).

### Pruebas unitarias
N/A.

### Pruebas integración
300 y balance de prueba iguales con el mes abierto y con el mes cerrado.

### Pruebas E2E
Declaraciones → mes cerrado muestra los ingresos.

### Dependencias
TASK-C-010.

### Riesgos
Cambio de modalidad a mitad de año (bloqueado).

### Criterios de aceptación
- [ ] Ningún reporte de resultados cambia al cerrar el mes.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Documentación actualizada

## TASK-C-008 — Estrangular `ContabilidadAutoServiceImpl`

**Épica:** C2 Motor único · **Módulo:** Contabilidad · **Tipo:** Backend/QA · **Prioridad:** P1 · **Estado:** Proposed

### Problema
13 flujos en el motor legacy sin tests; dos generadores de venta.

### Evidencia AS-IS
- Archivo: `BE: ContabilidadAutoServiceImpl.java` (1.584 líneas); `BE: event/*ContabilizacionListener.java`; `ContabilidadController.java:297-332`

### Referencia funcional
7.1 del agente; `docs/ARQUITECTURA_CONTABILIDAD.md` §11 (E11).

### Decisión
Refactorizar por estrangulamiento.

### Diseño propuesto
Sub-tareas (una PR por flujo, cada una con golden de paridad): C-008a Compra/NC compra; C-008b Gasto; C-008c Merma; C-008d Tesorería + MovimientoCaja; C-008e Obligación + Cuota (series OF/CU); C-008f Nómina, pago de nómina y prestación; C-008g Cierre + Sobregiro; C-008h `ReversarDocumentoUseCase` (copia CC, proyecto, frente y tercero); C-008i endpoints manuales → use case; C-008j borrar listeners/eventos legacy y la clase.

### Backend
Generadores, lectores, use case de reversa.

### Frontend
Ninguno.

### Base de datos
Ninguna.

### API
Los endpoints `generar-desde-*` pasan a `POST /asientos/reprocesar`.

### Seguridad y permisos
`contabilidad.reprocesar`.

### Inventario/contabilidad
Asientos idénticos (paridad).

### Auditoría
PostingLog uniforme.

### Migración de datos
Ninguna.

### Pruebas unitarias
Por generador: contado, crédito, mixto, NC y borde.

### Pruebas integración
Flujo → evento → asiento (Testcontainers cuando exista el baseline V13).

### Pruebas E2E
N/A.

### Dependencias
TASK-C-003.

### Riesgos
Regresiones (mitigadas con la paridad).

### Criterios de aceptación
- [ ] Clase legacy eliminada; un solo listener; 100 % de los tipos con golden.

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] ADR actualizado

## TASK-C-009 — Pantalla de parametrización contable

**Épica:** C3 · **Módulo:** Contabilidad (FE) · **Tipo:** Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
La parametrización existe en el backend y no en la UI.

### Evidencia AS-IS
- FE: ninguna referencia a `configuracion-cuentas`, `formas-pago`, `/impuestos` ni `posting-log`
- BE: `ConfiguracionContableController`, `FormaPagoContableController`, `ImpuestoController`, `AsientoRevisionController:37`

### Referencia funcional
1.8–1.13.

### Decisión
Implementar.

### Diseño propuesto
Ruta `contabilidad/parametrizacion` con `.page > .card > .card-head` y `p-tabView`: Conceptos · Formas de pago · Impuestos · Categorías · Modo y cancelación · Posting log (con Reprocesar). Selector de cuenta que filtra auxiliares activas de la clase permitida (guardarraíl). Seguir el estándar UI (memoria: pestañas sí, switches no; botones de opción para sí/no; `p-calendar`).

### Backend
DTOs con `claseEsperada` y `codigoDefault`.

### Frontend
Componente, servicio y SQL de menú (`docs/sql/menu_submodulo_parametrizacion_contable.sql`).

### Base de datos
Registro del submódulo.

### API
Existente.

### Seguridad y permisos
`contabilidad.parametrizar`.

### Inventario/contabilidad
N/A.

### Auditoría
`contabilidad_config_log` visible en la pestaña.

### Migración de datos
N/A.

### Pruebas unitarias
Componentes.

### Pruebas integración
N/A.

### Pruebas E2E
Cambiar la cuenta de IVA generado → la venta siguiente usa la nueva.

### Dependencias
TASK-C-003 (reproceso), TASK-C-015.

### Riesgos
Bajo.

### Criterios de aceptación
- [ ] Todo concepto del enum es editable con traza.
- [ ] Posting log con reproceso.

### Definition of Done
- [ ] Código integrado · [ ] Permisos probados · [ ] Manual `/ayuda` actualizado

## TASK-C-010 — Fechas contables del documento y reloj de la empresa

**Épica:** C2 · **Módulo:** Contabilidad · **Tipo:** Backend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
`now()` y `created_at` en UTC como fecha contable.

### Evidencia AS-IS
- Archivo: `BE: LectorAbonosJpa.java:24,35`; `LectorNotaJpa.java`; `ContabilidadAutoServiceImpl.java` (nómina `createdAt`, movimiento de caja `createdAt`, reversa `LocalDate.now()`)

### Referencia funcional
Implícita.

### Decisión
Implementar.

### Diseño propuesto
`RelojEmpresa.hoy(empresaId)` (ZoneId de la empresa, default America/Bogota); lectores con la fecha del documento; regla ArchUnit que prohíbe `LocalDate.now()`/`LocalDateTime.now()` en `contabilidad/**`.

### Backend
Lectores y use case de reversa.

### Frontend
N/A.

### Base de datos
`empresa.zona_horaria` (si no existe).

### API
N/A.

### Seguridad y permisos
N/A.

### Inventario/contabilidad
N/A.

### Auditoría
N/A.

### Migración de datos
Diagnóstico de asientos del día siguiente (`docs/sql/diagnostico_desfase_horario.sql`, ya existe).

### Pruebas unitarias
Reloj fijo a las 23:30 COT del 31.

### Pruebas integración
Abono con `fecha_pago` pasada → asiento en esa fecha.

### Pruebas E2E
N/A.

### Dependencias
El arreglo de zona horaria en 3 capas (memoria).

### Riesgos
Bajo.

### Criterios de aceptación
- [ ] ArchUnit verde; 0 asientos con fecha ≠ fecha del documento en las pruebas.

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes

## TASK-C-011 — Retenciones por tarifa y 350 por concepto

**Épica:** C3 · **Módulo:** Compras/Gastos/Contabilidad · **Tipo:** Backend/DB/Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Todas las retenciones van a una cuenta por tipo; la tarifa no queda registrada.

### Evidencia AS-IS
- Archivo: `BE: ContabilidadAutoServiceImpl.java:318-333, 710-717`; `entity/TarifaRetencionEntity.java` (`cuentaContableId` sin uso); `DeclaracionesServiceImpl.java:152-160`

### Referencia funcional
1.12.

### Decisión
Implementar.

### Diseño propuesto
`documento_retencion` (tipo, id, tarifa, base, %, valor); el generador agrupa por la cuenta de la tarifa; el 350 agrupa por `tarifa.codigo_concepto`; ReteICA con `municipio_id` y actividad.

### Backend
Compra, gasto, documento soporte, generadores (tras C-008a/b), declaraciones.

### Frontend
Selector de tarifa por línea o encabezado en compra y gasto.

### Base de datos
Tabla nueva + columnas de ICA en `tarifa_retencion`.

### API
Campos `retenciones[]` en los DTO de compra y gasto.

### Seguridad y permisos
N/A.

### Inventario/contabilidad
N/A.

### Auditoría
N/A.

### Migración de datos
Los históricos quedan en la cuenta genérica (se documenta).

### Pruebas unitarias
2 tarifas → 2 créditos.

### Pruebas integración
350 por concepto = mayor.

### Pruebas E2E
Compra con retención de servicios → 350.

### Dependencias
TASK-C-008 (a, b), GAP-C-027.

### Riesgos
Validar la normativa vigente (UVT, bases) antes de sembrar tarifas.

### Criterios de aceptación
- [ ] Σ 2365xx por concepto = renglones del 350.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes

## TASK-C-012 — Activos fijos: depreciación correcta, baja y venta con asiento

**Épica:** C5 · **Módulo:** Activos fijos · **Tipo:** Backend/DB/Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Fórmula incorrecta, sin prorrateo, depreciación sin asiento posible, baja sin asiento, sin venta de activo.

### Evidencia AS-IS
- Archivo: `BE: ActivoFijoServiceImpl.java:93-237`
- Tabla: `activo_fijo`, `depreciacion_periodo` (V55)
- Endpoint: `POST /api/activos-fijos/depreciar/{periodoId}`, `PUT /{id}/dar-de-baja`, `DELETE /{id}`

### Referencia funcional
1.27, 1.28, 4.2, 3.23.

### Decisión
Implementar.

### Diseño propuesto
`DepreciacionGenerador` (un asiento por período, líneas por activo con CC); cálculo por método (LR, SDA, saldos decrecientes) desde `fecha_inicio_uso`; cuentas obligatorias; `BajaActivoGenerador` y `VentaActivoGenerador`; bloquear `DELETE` con depreciaciones (solo anular); vínculo con `compra_detalle`.

### Backend
Generadores, servicio y validaciones.

### Frontend
Ficha con fecha de uso y método; acciones Baja/Vender; vista previa de la cuota.

### Base de datos
`fecha_inicio_uso`, `activo_baja(activo_id, fecha, tipo, valor_venta, asiento_id)`, `compra_detalle.activo_fijo_id`.

### API
`POST /activos-fijos/{id}/baja`, `POST /activos-fijos/{id}/venta`, `GET /activos-fijos/depreciacion/previa?periodo`.

### Seguridad y permisos
`activos.depreciar`, `activos.baja`.

### Inventario/contabilidad
Baja: Db 1592 + Db 5310 / Cr 15xx; venta: + 1305/4245.

### Auditoría
Historial por activo.

### Migración de datos
Recalcular la acumulada correcta y generar el ajuste en un período abierto (reporte para el contador).

### Pruebas unitarias
LR 60 meses; alta el día 15; tope residual.

### Pruebas integración
Baja con pérdida; venta con utilidad.

### Pruebas E2E
Depreciar el mes → asiento DP con líneas por activo.

### Dependencias
TASK-C-008.

### Riesgos
Diferencias históricas.

### Criterios de aceptación
- [ ] Σ cuotas = costo − residual.
- [ ] Saldo 1592 = Σ acumulada de las fichas.
- [ ] Baja y venta con asiento.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Permisos probados

## TASK-C-013 — Saldos iniciales por lotes con cruces contra los auxiliares

**Épica:** C4 · **Módulo:** Contabilidad · **Tipo:** Backend/Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
El descuadre se absorbe en 3705; hay un único asiento de apertura; no se concilia con los auxiliares.

### Evidencia AS-IS
- Archivo: `BE: AperturaContableServiceImpl.java:60-160`
- Endpoint: `POST/DELETE /api/contabilidad/saldos-iniciales`

### Referencia funcional
2.2, 2.3.

### Decisión
Mejorar.

### Diseño propuesto
Apertura como nota tipo `AP` con estados (borrador → contabilizada), varias por empresa (por módulo); descuadre bloqueado salvo `confirmarAjuste=true` con motivo; validación de auxiliar y tercero; reporte "Apertura vs. auxiliares".

### Backend
Reutilizar `NotaDiarioService` con el tipo AP; controles de cruce.

### Frontend
Pantalla de saldos iniciales con pestañas (Contable · Cartera · Proveedores · Inventario · Activos) y panel de cruces.

### Base de datos
Ninguna nueva (se usa `asiento_contable.tipo_comprobante='AP'`).

### API
`GET /api/contabilidad/saldos-iniciales/cruces`.

### Seguridad y permisos
`contabilidad.saldos_iniciales`.

### Inventario/contabilidad
Cruce con el inventario inicial valorizado (Bloque B).

### Auditoría
Motivo del ajuste en el asiento.

### Migración de datos
Las aperturas existentes siguen igual.

### Pruebas unitarias
Descuadre sin confirmar → 422.

### Pruebas integración
Cartera importada = 1305 de la apertura.

### Pruebas E2E
Flujo de apertura completo.

### Dependencias
TASK-C-004, TASK-C-012.

### Riesgos
Bajo.

### Criterios de aceptación
- [ ] No hay descuadre silencioso; los cruces se muestran con su diferencia.

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] Manual actualizado

## TASK-C-014 — Balance de prueba completo

**Épica:** C4 · **Módulo:** Reportes contables · **Tipo:** Backend/Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Solo existe el movimiento por `periodo_contable_id`.

### Evidencia AS-IS
- Archivo: `BE: AsientoContableQueryRepository.java:556-604`; `ReporteContableController.java`

### Referencia funcional
2.4, 4.17.

### Decisión
Implementar.

### Diseño propuesto
Endpoint con saldo anterior, débitos, créditos y saldo final; filtros por fechas, rango de cuentas, nivel, tercero, CC, sucursal (cuando exista) e incluirCierre; jerarquía con subtotales; Excel/PDF; pantalla `contabilidad/balance-prueba`.

### Backend
Query con CTE recursivo sobre `plan_cuenta.padre_id`; exportadores (`ExcelEstilos`, iText).

### Frontend
Pantalla estándar con filtros y exportación.

### Base de datos
Índice `(empresa_id, fecha)` ya existe; evaluar `asiento_detalle(cuenta_id, asiento_id)`.

### API
`GET /api/contabilidad/balance-prueba`, `/excel`, `/pdf`.

### Seguridad y permisos
`reportes.contables.ver|exportar`.

### Inventario/contabilidad
N/A.

### Auditoría
N/A.

### Migración de datos
N/A.

### Pruebas unitarias
Armado jerárquico.

### Pruebas integración
Continuidad de saldos entre meses; cuadre por nivel.

### Pruebas E2E
Exportar a Excel.

### Dependencias
TASK-C-007.

### Riesgos
Rendimiento en empresas grandes (paginación por nivel).

### Criterios de aceptación
- [ ] Cuadra en todos los niveles; saldo final(n) = saldo anterior(n+1).

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] SQL de menú

## TASK-C-015 — Validador único de cuentas y guardas del PUC

**Épica:** C7 · **Módulo:** Contabilidad · **Tipo:** Backend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Asiento manual legacy sin validación; PUC editable con movimientos.

### Evidencia AS-IS
- Archivo: `BE: AsientoContableServiceImpl.java:100-160, 163-300`; `PlanCuentasServiceImpl.java:75-100`

### Referencia funcional
1.11.

### Decisión
Implementar.

### Diseño propuesto
`CuentaMovimientoValidator` (empresa, activa, auxiliar, tercero/CC obligatorios según la cuenta, línea de un solo lado y no negativa) usado por el manual, los comprobantes, la apertura, la depreciación y las notas; `POST /asientos` delega en `NotaDiarioService`; PUC: `existeMovimiento(cuentaId)` bloquea el cambio de tipo, naturaleza y auxiliar→agrupación; valida `padre_id` en la empresa y el nivel; atributo `exige_tercero` y `exige_cc` en `plan_cuenta`.

### Backend
Validador + refactor.

### Frontend
Mensajes; marcas de "exige tercero/CC" en el PUC.

### Base de datos
Columnas `exige_tercero` y `exige_centro_costo` (seed: 13, 22, 23, 28 → tercero).

### API
Sin cambios de contrato.

### Seguridad y permisos
Cierra la fuga entre empresas por `cuentaId`.

### Inventario/contabilidad
N/A.

### Auditoría
Cambios del PUC al `contabilidad_config_log`.

### Migración de datos
Consulta de verificación de líneas sobre cuentas de agrupación o de otra empresa (debe dar 0; si no, reporte).

### Pruebas unitarias
Validador.

### Pruebas integración
Cuenta de otra empresa → 404; agrupación → 422; naturaleza con movimientos → 409.

### Pruebas E2E
N/A.

### Dependencias
Ninguna.

### Riesgos
Bajo.

### Criterios de aceptación
- [ ] La consulta de verificación da 0; todos los escritores directos usan el validador.

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes

## TASK-C-016 — Catálogo de acciones contables y su aplicación

**Épica:** C3 · **Módulo:** Seguridad/Contabilidad · **Tipo:** Backend/QA · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Las operaciones sensibles no tienen control por acción.

### Evidencia AS-IS
- Archivo: `BE: controllers/{Contabilidad,PeriodoContable,ActivoFijo,Tesoreria,ConfiguracionContable,…}Controller.java`, `contabilidad/web/*`; `docs/PLAN_SEGURIDAD.md`

### Referencia funcional
1.3–1.5.

### Decisión
Implementar sobre el mecanismo del Bloque A.

### Diseño propuesto
Declarar las acciones de GAP-C-015 y anotar cada endpoint del bloque; perfil "Contador" y "Auxiliar contable" por defecto.

### Backend
Anotaciones o aspecto del Bloque A.

### Frontend
Ocultar las acciones sin permiso.

### Base de datos
Seed del catálogo de acciones.

### API
403 uniforme.

### Seguridad y permisos
Objeto de la tarea.

### Inventario/contabilidad
N/A.

### Auditoría
Intentos denegados en el log.

### Migración de datos
Asignar las acciones a los roles existentes (ADMIN = todas).

### Pruebas unitarias
N/A.

### Pruebas integración
Matriz rol × endpoint.

### Pruebas E2E
Usuario sin `periodo.reabrir` no ve el botón y recibe 403.

### Dependencias
Bloque A.

### Riesgos
Bloquear a usuarios actuales (se mitiga con el seed por rol).

### Criterios de aceptación
- [ ] 100 % de los endpoints del bloque con acción declarada y test.

### Definition of Done
- [ ] Código integrado · [ ] Permisos probados

### P2 / P3 (resumen)

| ID | Gap | Título | Tipo | Prioridad | Depende de | Criterio de aceptación clave |
|---|---|---|---|---|---|---|
| TASK-C-017 | GAP-C-016 | Unificar la conciliación en extractos + partidas conciliatorias arrastradas + PDF | BE/DB/FE | P2 | C-010 | Saldo libro + partidas = extracto; continuidad de saldos |
| TASK-C-018 | GAP-C-017 | `sucursal_id` en la partida + regla por sucursal | BE/DB | P2 | Bloque A | ER/BP filtrables por sucursal |
| TASK-C-019 | GAP-C-018 | Grupo y tercero en la forma de pago; duplicar forma de pago | BE/DB/FE | P2 | C-009 | Venta con ADDI → 13xx con tercero de la financiera |
| TASK-C-020 | GAP-C-019 | Vista previa de contabilización | BE/FE | P2 | C-008 | La vista previa = el asiento generado |
| TASK-C-021 | GAP-C-020 | Reclasificación / traslado de cuentas | BE/FE | P2 | C-016 | Asiento de reclasificación trazable; períodos cerrados excluidos |
| TASK-C-022 | GAP-C-021 | Fusión de terceros (contable) | BE/FE | P2 | Bloque A | Terceros duplicados con saldo 0 tras fusionar; los documentos DIAN no se tocan |
| TASK-C-023 | GAP-C-022 | Reporte de compras + filtros de ventas + cola asíncrona con correo/campana | BE/FE | P2 | — | Reporte grande entregado por la campana en < 5 min |
| TASK-C-024 | GAP-C-023 | Comparativos y ER por CC en columnas | BE/FE | P2 | C-014 | Mes vs. mes y año vs. año cuadran con el ER |
| TASK-C-025 | GAP-C-024 | Presupuesto (ejecutar `PLAN_PRESUPUESTO.md`) | BE/DB/FE | P2 | C-007 | Ejecución = mayor |
| TASK-C-026 | GAP-C-025 | Causación de intereses, porción corriente, anulación del pago de cuota | BE | P2 | C-008e | Interés causado mensualmente |
| TASK-C-027 | GAP-C-026 | Saldo bancario desde el mayor; deprecar `saldo_actual` | BE/FE | P2 | C-008d | Saldo mostrado = saldo 1110xx |
| TASK-C-028 | GAP-C-027 | Base gravable, tarifa y documento de cruce en la partida | BE/DB | P2 | C-008 | Certificados y 350 sin heurísticas |
| TASK-C-029 | GAP-C-028 | Informes de activos fijos | BE/FE | P2 | C-012 | Excel/PDF por CC/responsable/categoría |
| TASK-C-030 | GAP-C-029 | IVA mayor valor del costo | BE | P2 | C-008a | Empresa no responsable → IVA capitalizado |
| TASK-C-031 | GAP-C-030 | Asiento de nómina: fecha del período + retefuente/FSP | BE | P2 | C-008f | Asiento en el mes de la nómina; 2365 de nómina |
| TASK-C-032 | GAP-C-031 | Borrador de ICA y certificados de retención | BE/FE/Compliance | P2 | C-011, C-028 | Certificado por tercero/año = mayor (validar normativa vigente) |
| TASK-C-033 | GAP-C-032 | Exógena 1003/1010/1012 | BE/Compliance | P2 | C-028 | Lote validado sin errores (resolución vigente) |
| TASK-C-034 | GAP-C-033 | Copiar parametrización entre empresas | BE/FE | P3 | C-009 | Empresa B con las mismas reglas |
| TASK-C-035 | GAP-C-034 | Perfiles de importación de extractos por banco + reglas automáticas | BE/FE | P3 | C-017 | 80 % de las líneas conciliadas sin intervención |
| TASK-C-036 | GAP-C-035 | Pólizas, mantenimientos, placa/serial, adiciones | BE/DB/FE | P3 | C-012 | La adición recalcula la cuota |
| TASK-C-037 | GAP-C-036 | Dashboard financiero | BE/FE | P3 | C-003 | Tarjetas con cifras = reportes |
| TASK-C-038 | GAP-C-037 | Triggers de cuadre y de período cerrado en la BD | DB | P3 | C-008 | INSERT descuadrado o en mes cerrado rechazado por la BD |

---

### Siguiente bloque ejecutable

**C0 (TASK-C-001 → TASK-C-002 → TASK-C-003)**: no tiene dependencias externas, cierra las dos fuentes de "documentos sin asiento" y deja el reproceso que todas las fases siguientes necesitan para remediar datos históricos. En paralelo, y sin tocar lógica, conviene correr el diagnóstico de documentos huérfanos y duplicados por origen en la copia local para dimensionar la remediación.
