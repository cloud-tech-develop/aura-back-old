# Auditoría ERP · Bloque B — Compras, Ventas, Caja/POS

**Modo:** AUDIT (sin cambios de código) · **Fecha:** 2026-09-29 · **Rama inspeccionada:** `fix/camilo-caja-pagos`
**Alcance:** ciclo de compras (OC, recepción, factura, NC/ND, anticipos, egresos, CxP, gastos, documento soporte, recepción FE/RADIAN), ciclo de ventas (cotización, pedido, remisión, factura/POS, devolución, NC/ND, recibos, CxC, retenciones al recaudo, FE Factus, ingresos para terceros, comisiones, moneda extranjera, exportación, WhatsApp/email), caja/POS (turnos, arqueo, traslados, caja menor, carritos abandonados), políticas de precio/descuento, cruces y trazabilidad, importación/exportación, smart create.
**Fuera de alcance (otros bloques):** kardex/costeo/bodegas en sí (bloque de inventario), motor contable/cierres/bancos (bloque contable), seguridad transversal (ver `docs/PLAN_SEGURIDAD.md`). Aquí sólo se señalan como *cross-ref* cuando la brecha nace en un flujo de este bloque.

Convención de rutas: `svc/` = `src/main/java/com/cloud_technological/aura_pos/services/implementations/`; `repo/` = `.../aura_pos/repositories/`; `mig/` = `src/main/resources/db/migration/`; `front/` = `D:\Proyectos Camilo\aura-post\aura-frontend\src\app\`.

---

## 1. Resumen del bloque

AURA tiene un ciclo **POS-céntrico** sólido para la venta de mostrador de contado y crédito (inventario por bodega, lotes, seriales, presentaciones, crédito con cupo, recibo de caja multi-factura, retenciones al recaudo, origen de fondos declarado, caja menor, traslados de fondos, carritos abandonados). Las compras tienen una nota crédito bien pensada (validación contra la factura origen, tres destinos del dinero, reversión al anular).

El problema no es de funcionalidad faltante sino de **integridad entre subsistemas**: cada documento mueve inventario, subledger (CxC/CxP), tesorería/caja, asiento contable y (a veces) DIAN, pero **las anulaciones, ediciones y notas no revierten los mismos cinco ejes que el documento original movió**. Los hallazgos más graves:

1. **Se puede anular una venta ya emitida a la DIAN** y el backend no lo impide (sólo el front oculta el botón al rol CAJERO).
2. **La devolución de venta reescribe la venta original** (cantidades, totales, bases IVA) e incluso le **agrega líneas** en los "cambios": el documento interno deja de coincidir con la factura electrónica.
3. **La devolución calcula el reembolso sin descuentos** → se devuelve más dinero del que el cliente pagó.
4. **La NC/ND electrónica está desconectada**: no mueve inventario, no toca la CxC del subledger (aunque el asiento sí acredita Clientes) y no está vinculada a la venta ni a la devolución → doble reversa del ingreso o cartera ≠ contabilidad.
5. **El payload FE no representa la venta**: descuento siempre 0, forma de pago siempre contado, método "10", persona natural fija, IVA leído del producto actual.
6. **La emisión FE no es idempotente** (`@Retry` sobre POST con reintento en timeout, sin estado PENDIENTE ni reconciliación).
7. **Anular una compra no anula su CxP ni revierte pagos** (banco/caja): el pasivo sigue vivo y el dinero sigue "fuera".
8. **Numeración `MAX+1` / `count+1`** sin bloqueo, con `catch → 1` en ventas.
9. **La "recepción" de la orden de compra es un contador**: marca recibido sin documento de inventario ni vínculo a la compra.
10. Stock actualizado por **read-modify-write sin bloqueo** en venta, compra y devolución.

**Conteo de brechas:** 46 → P0: 12 · P1: 17 · P2: 10 · P3: 7.

---

## 2. Hallazgos críticos (P0)

| # | Hallazgo | Evidencia principal | Efecto |
|---|---|---|---|
| 1 | Anular venta con CUFE | `svc/VentaServiceImpl.java:728-831` (sin chequeo de `cufe`/`estadoDian`); `front/features/ventas/detalle/detalle-venta.component.ts:61` (solo oculta a CAJERO) | Factura válida en DIAN sin soporte en AURA; inventario, CxC y asiento revertidos sin NC |
| 2 | Devolución muta la venta | `svc/DevolucionServiceImpl.java:251-275` (reduce `venta_detalle`), `:301-311` (agrega líneas a la venta original), `:323-325` + `:596-622` (recalcula totales/IVA) | Historia destruida; venta ≠ FE; reportes y comisiones sobre datos alterados |
| 3 | Reembolso sin descuento | `svc/DevolucionServiceImpl.java:218-221` (`precioUnitario × cantidad + impuesto`), frente a `svc/VentaServiceImpl.java:402-405` (base = precio×cant − descuento) | Fuga de dinero en cada devolución de producto con descuento |
| 4 | NC/ND electrónica aislada | `svc/FactusNotaService.java:346-395`; `entity/NotaElectronicaEntity.java:33-86` (sin `venta_id`/`devolucion_id`); `contabilidad/application/generador/NotaCreditoGenerador.java` (CR Clientes, "el costo/inventario no se reversa aquí") | Subledger CxC ≠ cuenta Clientes; doble reversa si se hace devolución + NC |
| 5 | Payload FE infiel | `svc/VentaFacturaService.java:162-182` (sin descuento, `metodoPago "10"`, vencimiento = hoy); `svc/FactusService.java:129,148-149,159` | Total DIAN ≠ total AURA; crédito reportado como contado; NIT reportado como persona natural |
| 6 | FE no idempotente | `svc/FactusService.java:55-56` (`@Retry`), `application.properties:128-132` (reintenta en `SocketTimeoutException`); `svc/VentaFacturaService.java:72` (check sin lock); `:179` + `FactusService.java:119` (reference `PREF-PREF-consecutivo`, consecutivo por sucursal) | Factura huérfana en DIAN sin CUFE local; colisión de reference entre sucursales |
| 7 | Anular compra incompleto | `svc/CompraServiceImpl.java:1137-1218` (no toca `cuenta_pagar`, no llama `revertirPagosAnteriores`, solo anula el comprobante `:1151`) | CxP viva de una compra anulada; banco/caja siguen descontados mientras el asiento se reversa |
| 8 | Anular venta incompleto | `svc/VentaServiceImpl.java:728-831` (no revierte `tesoreriaService` de `:565-573`, no valida devoluciones activas, no toca `comision_venta`) | Saldo bancario inflado; asiento de devolución vivo + reversa total de la venta = doble reversa |
| 9 | Numeración duplicable | `repo/ventas/VentaQueryRepository.java:124-136` (`MAX+1`, `catch → 1`); TODO en `svc/VentaServiceImpl.java:284-289`; `svc/DevolucionServiceImpl.java:187`; `svc/OrdenCompraServiceImpl.java:70-74`; `svc/CotizacionServiceImpl.java:95` | Números repetidos; `venta` no tiene migración baseline que pruebe un UNIQUE |
| 10 | Stock sin bloqueo (cross-ref inventario) | `svc/VentaServiceImpl.java:384-399` valida y `:478-486` escribe; `svc/CompraServiceImpl.java:571-585`; `svc/DevolucionServiceImpl.java:551-559`; sin `@Lock`/`@Version` en inventario (grep) | Lost update y stock negativo no permitido bajo concurrencia |
| 11 | Recepción de OC fantasma | `svc/OrdenCompraServiceImpl.java:202-241` (solo `cantidadRecibida`); `compraId` nunca se asigna; `front/features/compras/ordenes/index-ordenes.component.ts:335-373` (recibe y luego navega a un form que puede abandonarse) | OC "CERRADA" sin mercancía; doble ingreso posible si se registra otra compra |
| 12 | Gasto inconsistente | `svc/GastoServiceImpl.java:231-248` (cambia `monto` sin re-postear ni ajustar CxP/caja); `:252-266` (eliminar no anula CxP ni revierte pagos) | Documento ≠ asiento ≠ CxP ≠ caja |

---

## 3. Matriz AS-IS

| Dominio | Funcionalidad | Implementación encontrada | Estado | Evidencia |
|---|---|---|---|---|
| Compras | Orden de compra | CRUD + estados BORRADOR→ENVIADA→CONFIRMADA→RECIBIDA_PARCIAL/CERRADA/ANULADA; no mueve inventario ni contabiliza | PARTIAL | `svc/OrdenCompraServiceImpl.java:114-254`; `mig/V38__ordenes_compra.sql` |
| Compras | Recepción / remisión de compra | No existe documento; "recibir" solo suma contador en OC y el front abre el form de compra | MISSING | `svc/OrdenCompraServiceImpl.java:202-241`; grep `remision` sin resultados en back/front/migraciones |
| Compras | Factura de compra | Crea detalle, inventario por bodega, lotes, seriales, presentaciones, retenciones, pagos, CxP (crédito), egreso de caja, comprobante CE, evento contable | COMPLETE (con riesgos) | `svc/CompraServiceImpl.java:437-672` |
| Compras | Cruce OC → factura (parcial, sin doble ingreso) | Sin FK `compra.orden_compra_id`; OC.`compraId` nunca se setea | MISSING | `entity/CompraEntity.java` (sin campo); grep `setCompraId` sin resultados |
| Compras | NC compra devolución | Documento negativo con validación contra origen, 3 destinos (CRUCE_CXP, DEVOLUCION_DINERO, SALDO_A_FAVOR), inventario/lotes/seriales, reversión al anular | BETTER_THAN_REFERENCE | `svc/CompraServiceImpl.java:114-234`, `:860-1063` |
| Compras | NC compra descuento (solo valor) | NC exige productos y cantidades de la factura origen; una NC sin cantidad vale 0 | MISSING | `svc/CompraServiceImpl.java:191-234`, `:527-535` |
| Compras | ND compra | No existe | MISSING | grep `NOTA_DEBITO` solo en notas electrónicas de venta |
| Compras | Anticipos a proveedor y cruce | `AnticipoService` crea/cruza contra CxP con asiento; sin anulación, sin caja/tesorería | PARTIAL | `contabilidad/infrastructure/devengo/AnticipoService.java:47-163` |
| Compras | Comprobante de egreso | Uno por compra de contado, sincronizado y anulable; abono CxP con origen declarado | COMPLETE | `svc/CompraServiceImpl.java:819-851`; `svc/CuentaPagarServiceImpl.java:159-260` |
| Compras | CxP | Crear, abonar (origen declarado, saldo disponible), cruces, eliminar abono (físico), sin lock | PARTIAL | `svc/CuentaPagarServiceImpl.java:159-260, 343, 418` |
| Compras | Gastos / servicios | Gasto con forma de pago, CxP a crédito, tributarios; edición y eliminación inconsistentes; sin replicar | PARTIAL | `svc/GastoServiceImpl.java:62-266` |
| Compras | Documento soporte electrónico | Emisión Factus v1 desde compra/gasto, previa, PDF, descartar; DS no habilitado ante DIAN; sin nota de ajuste; origen editable tras aceptado | PARTIAL | `svc/DocumentoSoporteServiceImpl.java:75-235`; `mig/V180__documento_soporte.sql`; memoria "documento-soporte-electronico" |
| Compras | Recepción FE proveedor (XML) / eventos RADIAN | No existe | MISSING | grep `radian|acuse|AttachedDocument` sin resultados relevantes |
| Compras | Retenciones en compra | % manual por documento (retefuente/reteIVA/reteICA) | PARTIAL | `svc/CompraServiceImpl.java:616-631`; existe `TarifaRetencionServiceImpl` no usado aquí |
| Compras | Duplicado factura proveedor | Sin control proveedor+número | MISSING | grep `NumeroCompra` en repos y `numero_compra` en migraciones sin resultados |
| Compras | Reportes de compras | No hay reporte de compras (sí de gastos) | MISSING | `controllers/ReporteController.java:87-372` |
| Ventas | Cotización | CRUD, vigencia, vencimiento automático, reactivación 1 vez, PDF front | PARTIAL | `svc/CotizacionServiceImpl.java:85-309` |
| Ventas | Cotización → venta | `convertirAVenta` solo devuelve el DTO; nunca pasa a CONVERTIDA; sin vínculo | PARTIAL | `svc/CotizacionServiceImpl.java:252-265`; grep `CONVERTIDA` solo en `:244` |
| Ventas | Pedido (vendedor) | CREADA→DESPACHADA (genera venta crédito)→COBRADA/ANULADA; pedido "espejo" por cada venta POS de usuario con empleado | DIFFERENT | `svc/PedidoVendedorServiceImpl.java:113-355`; `svc/VentaServiceImpl.java:663-726` |
| Ventas | Plan separe / pedido con abonos | No existe; cobro de pedido es total y sin caja | MISSING | `svc/PedidoVendedorServiceImpl.java:257-330` |
| Ventas | Remisión de venta / devolución de remisión | No existe; el despacho del pedido es directamente la venta | DIFFERENT | `svc/PedidoVendedorServiceImpl.java:186-253` |
| Ventas | Factura / POS | Venta con presentaciones, compuestos, lotes, seriales, crédito con cupo, pagos mixtos, CxC, comisión, evento contable | COMPLETE (con riesgos) | `svc/VentaServiceImpl.java:195-681` |
| Ventas | Edición de venta | No existe (inmutable) | BETTER_THAN_REFERENCE | `controllers/VentaController.java` (solo create/anular) |
| Ventas | Anulación de venta | Revierte inventario, CxC (si sin abonos), asiento; no FE, no banco, no comisión, no devoluciones | PARTIAL | `svc/VentaServiceImpl.java:728-831` |
| Ventas | Devolución de venta (interna) | Parcial/total, reintegro opcional, seriales, lotes, cartera, reembolso, cambio con productos agregados, asiento | PARTIAL (mal modelada) | `svc/DevolucionServiceImpl.java:146-531` |
| Ventas | NC electrónica venta | Factus v1, prefill desde Factus o venta local, PDF, reenvío correo, asiento de reversa de ingreso | PARTIAL | `svc/FactusNotaService.java:75-395` |
| Ventas | NC descuento venta | Solo vía NC electrónica por valor; sin efecto en CxC | PARTIAL | idem |
| Ventas | ND venta | Factus v1 + asiento; sin efecto en CxC | PARTIAL | `svc/FactusNotaService.java:250-253` |
| Ventas | Recibo de caja | Multi-factura, lock advisory + pessimistic, sobrante, retenciones, anulación con validación de turno cerrado | COMPLETE | `svc/ReciboCajaServiceImpl.java:77-330`; `repo/cartera/ReciboCajaQueryRepository.java:35`; `mig/V167__recibo_caja_cartera.sql` |
| Ventas | Retenciones al recaudo | Abonos vinculados + borradores 300/350 | COMPLETE (local, V181 sin correr) | `svc/RetencionRecaudoService.java`; `mig/V181__retenciones_en_recaudo.sql` |
| Ventas | CxC / cartera | Abono directo (sin lock), recibo (con lock), acuerdos, agenda, control de crédito, tablero | PARTIAL | `svc/CuentaCobrarServiceImpl.java:171-462`; `docs/PLAN_CARTERA.md` |
| Ventas | FE (Factus) | Bajo demanda por venta; persiste CUFE/QR/número/URL | PARTIAL | `svc/VentaFacturaService.java:54-111`; `svc/FactusService.java:55-185` |
| Ventas | Cola de pendientes FE / envío masivo | No; reporte Excel de FE sí | PARTIAL | `controllers/ReporteController.java:117-132` |
| Ventas | Tabla `factura` (flujo viejo) + retry scheduler | Vivo cada 5 min sobre flujo muerto | DIFFERENT (deuda) | `scheduler/FacturaRetryScheduler.java:30`; `svc/FacturaRetryService.java:45-87`; memoria "gotcha-factura-tabla-vieja" |
| Ventas | Ingresos para terceros | No existe | MISSING | grep sin resultados |
| Ventas | Comisiones | Por producto/categoría, modalidad SERVICIO/VENTA, liquidación, pago; sin recaudo/rangos/metas; no se revierten | PARTIAL | `entity/ComisionConfigEntity.java:40-51`; `svc/ComisionServiceImpl.java:343`; `repo/turno_caja/TurnoCajaQueryRepository.java:278-288` |
| Ventas | Moneda extranjera / TRM | No existe | MISSING | grep `trm|moneda` solo en suscripciones/PDF |
| Ventas | Exportación | No existe | MISSING | grep |
| Ventas | WhatsApp / email de documentos | Solo link `wa.me` en agenda de cobro; correo lo envía Factus (FE/NC) | PARTIAL | `front/features/cartera/agenda/agenda-cobro.component.ts:91-95`; `svc/FactusNotaService.java:319-339` |
| Ventas | Reportes de ventas | Excel/PDF, avanzados (categoría, top, vendedor, márgenes), gerencial | PARTIAL | `controllers/ReporteController.java:87-372` |
| Precios | Listas de precios | Existen, se usan en el POS (front) | COMPLETE | `front/features/pos/pos.component.ts:226-277` |
| Precios | Precio/descuento por cliente, volumen, reglas | Entidades y CRUD; resolución solo en front; backend confía en precio/descuento/impuesto recibidos | PARTIAL | `svc/PrecioDinamicoServiceImpl.java`, `svc/ReglaDescuentoServiceImpl.java`; `svc/VentaServiceImpl.java:402-407` |
| Precios | Límite de descuento por usuario/rol | No existe; descuento libre en POS | MISSING | `front/features/pos/pos.component.ts:1339-1340` |
| Caja | Turno de caja (abrir/cerrar/diferencia) | Uno por caja y por usuario, diferencia contabilizada, ajuste retroactivo | PARTIAL | `svc/TurnoCajaServiceImpl.java:128-200, 380-470` |
| Caja | Arqueo por denominaciones / ciego | No | MISSING | idem |
| Caja | Movimiento manual en turno (abono CxC/CxP) | Existe; `findById` sin empresa; sin tope | PARTIAL (riesgo) | `svc/TurnoCajaServiceImpl.java:211-305` |
| Caja | Traslado de fondos / caja menor | Implementado con anulación (V177/V178) | COMPLETE (local) | `svc/TrasladoFondosServiceImpl.java`; `docs/PLAN_CAJA_FONDOS.md` |
| Caja | Origen de fondos declarado | Compra, gasto, CxC, CxP, recibo | COMPLETE | `docs/PAGO_OBLIGACIONES_ORIGEN.md`; `svc/CompraServiceImpl.java:759-806` |
| Caja | Carritos abandonados | Registro + reporte | COMPLETE (local, V182) | `controllers/CarritoAbandonadoController.java:23-40` |
| Transversal | Trazabilidad documento→documento / control de cruces | Solo `compra_origen_id` (NC compra), `devolucion.venta_id`, `pedido.venta_id`; sin grafo ni cantidades aplicadas | MISSING | entidades citadas |
| Transversal | Idempotencia en creación | No existe (ni en venta, compra, recibo, abono) | MISSING | grep `idempot|X-Request` solo en contabilidad |
| Transversal | Importación de documentos | Solo PUC, terceros, saldos y cartera abierta | PARTIAL | `controllers/ImportacionController.java:43-79` |
| Transversal | Smart create en documentos | `app-tercero-autocomplete` sin alta rápida; sin producto rápido | MISSING | `front/shared/components/tercero-autocomplete/tercero-autocomplete.component.ts` |
| Transversal | Vista previa contable | No en compra/venta | MISSING | — |
| Transversal | Estados tipados | Strings libres y mezcla de mayúsculas | DIFFERENT (deuda) | ver GAP-B-026 |

---

## 4. Matriz de efectos documentales: AURA real vs baseline §5

Leyenda: ✅ coincide · ⚠ diverge con riesgo · ❌ no existe.

### 4.1 Compras

| Documento | Inventario (baseline → AURA) | Contabilidad | CxP | Dinero (caja/banco) | Veredicto |
|---|---|---|---|---|---|
| Orden compra | No → No; **pero "recibir" marca `cantidad_recibida` sin mover stock** (`OrdenCompraServiceImpl:202-241`) | No → No | No → No | — | ⚠ contador fantasma |
| Remisión compra | Sí+ → **no existe** | — | — | — | ❌ |
| Factura compra directa | Sí+ → Sí+ (`CompraServiceImpl:570-585`) | Sí → Sí (`:667`) | Sí → solo si CREDITO (`:648`) | contado: `compra_pago` + tesorería + egreso caja + CE (`:645-662`) | ✅ |
| Factura legalizando OC/remisión | Solo pendiente → **sin cruce**: la compra entra completa; la OC se marca aunque la compra no se guarde; nada impide registrar otra compra por lo mismo | Sí | Sí | — | ⚠ riesgo de doble ingreso |
| NC devolución compra | Sí− → Sí− con tope por lo acreditable (`:191-234`, `:574-582`) | Sí reverso → Sí (documento negativo) | Reduce → CRUCE_CXP / devolución de dinero / saldo a favor | devolución: ingreso caja/banco (`:901-985`) | ✅ (mejor) |
| NC descuento compra | No cantidad → **no existe** | — | — | — | ❌ |
| ND compra | No cantidad → **no existe** | — | — | — | ❌ |
| Egreso (abono CxP) | No → No | Sí → Sí (`ABONO_PAGAR`) | Reduce → Reduce (sin lock) | caja/banco declarado | ✅ |
| **Anular factura compra** | reversa → Sí− (`:1154-1202`) | reversa → Sí (`:1215`) | debería anular → **NO se anula** | debería revertir → **NO** (solo anula CE `:1151`) | ⚠ **P0** |
| **Editar factura compra** | reversa+reingreso (borra detalles `:1302`) | re-post (`:1421`) | sincroniza, bloquea si hay abonos (`:1501-1541`) | revierte y rehace pagos (`:1411-1415`) | ⚠ no valida NC hijas, DS, período |
| Gasto | No → No | Sí | CxP si crédito (`GastoServiceImpl:226`) | tesorería (`:180`) | ✅ al crear; ⚠ editar/eliminar |

### 4.2 Ventas

| Documento | Inventario (baseline → AURA) | Contabilidad | CxC | Dinero / DIAN | Veredicto |
|---|---|---|---|---|---|
| Cotización | No → No | No → No | No → No | — | ✅ (pero conversión sin traza) |
| Pedido (vendedor) | No → No (sin reserva) | No → No | No → No | cobro antes de despacho: **no registra dinero** | ⚠ |
| Remisión venta / devolución remisión | Sí− / Sí+ → **no existen** (despacho = venta) | — | — | — | ❌ (DIFFERENT) |
| Factura/POS directa | Sí− → Sí− (`VentaServiceImpl:437-505`) | Sí → Sí (`:676`) | Sí → si crédito/parcial (`:630-652`) | pagos + tesorería (`:549-574`) | ✅ |
| FE de la venta | — | — | — | CUFE bajo demanda; payload infiel | ⚠ P0 |
| Devolución interna | Sí+ → Sí+ si reintegra (`DevolucionServiceImpl:404-409`) | Sí reverso → Sí (`:422-425`) | Reduce → **modifica `total_deuda`/`saldo` directo, sin abono** (`:352-374`) | reembolso con origen **deducido** (`:652-719`) | ⚠ además **muta la venta** |
| NC electrónica (devolución) | Sí+ → **No** | Sí → Sí (DB ingreso/IVA, CR Clientes) | Reduce → **No toca `cuenta_cobrar`** | DIAN sí | ⚠ **P0** |
| NC descuento venta | No cantidad → No | Sí → Sí | Reduce → **No** | DIAN sí | ⚠ |
| ND venta | No → No | Sí → Sí | Aumenta → **No** | DIAN sí | ⚠ |
| Recibo de caja | No → No | Sí → Sí | Reduce → Reduce (con lock) | caja/banco declarado | ✅ |
| **Anular venta** | reversa → Sí+ | reversa → Sí | anula si sin abonos | **no revierte banco; no mira CUFE; no mira devoluciones; comisión viva** | ⚠ **P0** |

### 4.3 Dobles afectaciones detectadas

1. **Devolución + NC electrónica** del mismo ítem: el ingreso/IVA se reversa dos veces (asiento `DEVOLUCION` + asiento `NOTA_CREDITO`), y la CxC del subledger baja una vez mientras la cuenta Clientes baja dos. Nada vincula los dos documentos (`NotaElectronicaEntity` sin `venta_id`/`devolucion_id`).
2. **Venta con devolución parcial y luego anulada:** `anular` reversa el asiento original completo (el que se generó con los totales previos a la devolución) y el asiento de la devolución sigue vivo → el ingreso de lo devuelto queda reversado dos veces. El inventario no se duplica solo porque la devolución ya había reducido `venta_detalle.cantidad` (efecto colateral de la mutación, no un diseño).
3. **OC recibida + compra registrada a mano** (o compra abandonada y registrada otra vez): no hay aplicación de cantidades, así que el sistema no puede impedir el doble ingreso ni detectar la OC "cerrada" sin mercancía.
4. **NC compra SALDO_A_FAVOR** queda solo como débito en Proveedores (`CompraServiceImpl:872-873`); no se materializa como anticipo cruzable → el crédito es invisible para el subledger y se puede "cobrar" dos veces por fuera.

---

## 5. Brechas (GAP-B-XXX)

### P0

### GAP-B-001 — Se puede anular una venta ya emitida electrónicamente
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `svc/VentaServiceImpl.java:728-831` no consulta `venta.cufe`/`estado_dian`; `VentaController.java:72-75` sin restricción; front `detalle-venta.component.ts:61` solo oculta a CAJERO y el HTML `:124` no mira `cufe`.
**Referencia funcional:** baseline §3.9–3.14: un documento fiscal emitido solo se corrige con NC/ND.
**Problema:** inventario, CxC y asiento se revierten mientras la factura sigue válida ante la DIAN → IVA declarado sin soporte, ingreso omitido.
**Decisión:** implementar.
**Diseño propuesto:** si `estado_dian = 'EMITIDA'` (o `cufe` no nulo), `anular` responde 409 con la ruta "emita una NC total"; se ofrece `POST /ventas/{id}/nota-credito-total` que crea la devolución total + NC electrónica ligada (ver GAP-B-004).
**Impacto técnico:** backend (guard + endpoint), front (acción "Anular con NC" en el detalle), sin DB nueva salvo lo de GAP-B-004.
**Riesgos:** ventas FE ya anuladas en producción → reporte de conciliación.
**Dependencias:** GAP-B-004 para la ruta alternativa (el guard puede salir antes).
**Pruebas:** unitaria del guard; integración anular venta con CUFE → 409.
**Criterios de aceptación:** ninguna venta con CUFE puede quedar ANULADA sin NC electrónica aceptada.

### GAP-B-002 — La devolución reescribe la venta original
**Estado:** PARTIAL (mal modelada) · **Prioridad:** P0
**AURA actual:** `svc/DevolucionServiceImpl.java:251-275` reduce `venta_detalle.cantidad/monto_descuento/impuesto/subtotal`; `:284-321` agrega `venta_detalle` nuevos a la venta original ("cambio"); `:596-622` recalcula totales y bases IVA; `anular` (`:462-489`) los vuelve a sumar buscando la línea por producto (`findFirst`).
**Referencia funcional:** baseline §3.9, §7.4 (reversión antes que borrado), §7.3 (cruces con cantidad aplicada).
**Problema:** la venta deja de ser el documento emitido; reportes, comisiones, márgenes, FE, re-impresiones y auditoría ven otra cosa; con dos líneas del mismo producto el `findFirst` corrompe cantidades.
**Decisión:** refactorizar.
**Diseño propuesto:** `venta_detalle` inmutable. `devolucion_detalle.venta_detalle_id` (FK) + cantidad devuelta; el disponible para devolver = vendido − Σ devuelto vigente. El "cambio" crea una **venta nueva** ligada (`venta.devolucion_origen_id`) y cruza el saldo a favor como medio de pago. Los totales "netos" de la venta se exponen por vista/consulta, no por escritura.
**Impacto técnico:** backend (Devolución, reportes que leen `venta_detalle`), DB (FK, columna de origen), front (pantalla de cambio).
**Riesgos:** datos históricos ya mutados: reconstrucción = `venta_detalle.cantidad + Σ devolucion_detalle.cantidad` (no anuladas); las líneas agregadas por cambios no tienen marca → heurística por `created_at > fecha_emision`.
**Dependencias:** GAP-B-021 (relaciones), GAP-B-003.
**Pruebas:** devolución parcial en 2 pasos + anulación de la primera; dos líneas del mismo producto; cambio con diferencia a favor y en contra.
**Criterios de aceptación:** ninguna operación de devolución hace UPDATE sobre `venta`/`venta_detalle`.

### GAP-B-003 — El reembolso de la devolución ignora descuentos
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `svc/DevolucionServiceImpl.java:218-221` → `precioUnitario × cantidad + impuesto proporcional`; la venta calculó `precio × cantidad − descuentoValor` (`VentaServiceImpl.java:402-405`) y además `descuentoGeneral` (`:527`).
**Referencia:** baseline §3.9 "valor ligado a origen".
**Problema:** se devuelve y se acredita más de lo cobrado (fuga de caja y de ingresos).
**Decisión:** corregir ya.
**Diseño:** valor unitario neto = `(subtotal_linea − impuesto) / cantidad` de la línea original (usa la semántica "subtotalLinea incluye IVA"), más prorrata del descuento general; IVA proporcional del impuesto de la línea.
**Impacto:** backend; prueba de regresión.
**Riesgos:** devoluciones históricas sobrevaloradas → reporte de impacto.
**Dependencias:** ninguna (hotfix).
**Pruebas:** producto 10.000 con 10% de descuento, devolver 1 → 9.000 + IVA.
**Criterios:** Σ devoluciones de una venta nunca supera su `total_pagar`.

### GAP-B-004 — NC/ND electrónicas desconectadas de venta, devolución, inventario y CxC
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `svc/FactusNotaService.java:346-395` envía un payload armado por el front; `NotaElectronicaEntity` (`:33-86`) guarda `billId` pero no `venta_id` ni `devolucion_id`; el asiento (`NotaCreditoGenerador`) acredita Clientes sin tocar `cuenta_cobrar`; ND igual; no hay efecto en inventario; `eliminar` borra físico (`:311`).
**Referencia:** baseline §3.9–3.11 y §5.
**Problema:** subledger CxC ≠ mayor Clientes; doble reversa si se usan devolución y NC; NC por devolución no reintegra stock.
**Decisión:** refactorizar.
**Diseño:** una sola "nota de venta" de dominio con tipo `DEVOLUCION | DESCUENTO | ANULACION | DEBITO`, ligada a `venta_id` y a líneas (`venta_detalle_id`, cantidad, valor). Efectos por tipo según la matriz §5 (inventario solo DEVOLUCION; CxC por aplicación). Si la venta tiene CUFE, la nota se emite a Factus desde el mismo documento (outbox, GAP-B-006); si no, queda como nota interna. La devolución actual pasa a ser un caso de este documento. Un solo asiento por nota.
**Impacto:** backend (servicio de notas, generadores), DB (`nota_venta`, `nota_venta_detalle` o extensión de `devolucion` + `nota_electronica.venta_id/devolucion_id`), front (flujo único).
**Riesgos:** notas ya emitidas sin vínculo → vinculación por `factus_numero`/`bill_id`.
**Dependencias:** GAP-B-002, GAP-B-006, GAP-B-021.
**Pruebas:** NC devolución (stock +, CxC −, un asiento); NC descuento (CxC −); ND (CxC +); devolución + NC sobre lo mismo → rechazado.
**Criterios:** para cada venta, Σ aplicaciones en CxC = saldo de Clientes por tercero/documento.

### GAP-B-005 — El payload de FE no representa la venta
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `svc/VentaFacturaService.java:162-176` sin descuento por línea ni general; `:181` método de pago fijo "10"; `:182` vencimiento hoy → `FactusService.java:129` siempre forma de pago contado; `:148-149` persona natural y tributo 21 fijos; IVA leído de `producto.iva_porcentaje` actual, no de la línea; unidad 70 fija.
**Referencia:** §3.14; normativa DIAN vigente (verificar: anexo técnico FE, forma y medio de pago, descuentos).
**Problema:** total DIAN ≠ total AURA en toda venta con descuento; crédito reportado como contado; NIT de empresas reportado como persona natural.
**Decisión:** corregir.
**Diseño:** construir ítems desde `venta_detalle` (precio, `monto_descuento` → `discount_rate`, IVA% = imp/(subtotalLinea−imp)); prorratear `descuento_general`; forma de pago = crédito si hay CxC, con `fecha_vencimiento` de la CxC; medio de pago por `venta_pago` (mapeo parametrizable); organización legal y tributo desde el tercero; unidad desde el producto/presentación. Validar antes de enviar que Σ calculado = `total_pagar` (tolerancia de redondeo).
**Impacto:** backend; parametrización de mapeos.
**Riesgos:** diferencias de redondeo con Factus.
**Dependencias:** ninguna.
**Pruebas:** golden files de payload (contado, crédito, descuento línea, descuento general, NIT, exento).
**Criterios:** total del bill devuelto por Factus = `venta.total_pagar`.

### GAP-B-006 — Emisión FE no idempotente
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `@Retry(name="factus-bill")` (`FactusService.java:55-56`) con reintento en `SocketTimeoutException` (`application.properties:128-132`); nómina lo evitó a propósito (`:134-139`). Check `EMITIDA` sin lock (`VentaFacturaService.java:72`); `@Transactional` envolviendo la llamada HTTP; `reference_code` = `prefijo + "-" + (prefijo-consecutivo)` con consecutivo por **sucursal** (`VentaFacturaService.java:179`, `FactusService.java:119`) → dos sucursales colisionan.
**Referencia:** §3.14 (envío, estado, seguimiento).
**Problema:** timeout tras aceptación → reintento rechazado por duplicado → venta sin CUFE aunque la DIAN la tenga; doble clic concurrente.
**Decisión:** implementar.
**Diseño:** estado `PENDIENTE_ENVIO → ENVIANDO → EMITIDA | RECHAZADA | DESCONOCIDO`; reserva atómica (`UPDATE ... WHERE estado_dian IS NULL`); `reference_code` único por empresa (`FE-{ventaId}`); sin retry automático en timeout: pasa a DESCONOCIDO y un job reconcilia consultando Factus por reference; llamada HTTP fuera de la transacción de BD.
**Impacto:** backend, DB (columnas de estado/intentos o tabla `documento_electronico`), job.
**Riesgos:** ventas históricas con reference viejo.
**Dependencias:** GAP-B-009 (numeración).
**Pruebas:** simular timeout con WireMock; doble petición concurrente.
**Criterios:** ninguna venta queda con bill en Factus y sin CUFE local por más de un ciclo del job.

### GAP-B-007 — Anular una compra no anula la CxP ni revierte pagos
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `svc/CompraServiceImpl.java:1137-1218`: revierte inventario/lotes/seriales, anula el CE, reversa asiento; **no** anula `cuenta_pagar`, **no** valida abonos, **no** revierte `compra_pago`/tesorería/egreso de caja (existe `revertirPagosAnteriores` `:1436-1491` pero solo lo usa `actualizar`), **no** valida NC hijas vigentes ni DS aceptado. `CuentaPagar` no filtra compras anuladas.
**Referencia:** §7.4; §5 (anulación debe revertir los mismos ejes).
**Problema:** pasivo fantasma que puede pagarse; saldo bancario y arqueo desalineados con el mayor.
**Decisión:** corregir.
**Diseño:** anular = (1) bloquear si hay NC vigentes, abonos o DS aceptado; (2) `revertirPagosAnteriores`; (3) anular CxP (`estado='anulada'`, saldo 0, fecha y motivo); (4) inventario; (5) asiento. Todo en la misma transacción, con motivo y usuario.
**Impacto:** backend; DB (motivo/usuario de anulación en `compra`).
**Riesgos:** compras ya anuladas con CxP viva → script de conciliación.
**Dependencias:** ninguna (hotfix).
**Pruebas:** anular compra contado efectivo, contado banco, crédito sin abonos, crédito con abonos (rechazo), con NC (rechazo).
**Criterios:** tras anular, ni CxP pendiente ni movimiento de dinero vigente asociado a la compra.

### GAP-B-008 — Anular una venta no revierte banco, devoluciones ni comisiones
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `svc/VentaServiceImpl.java:728-831`: los pagos a cuenta bancaria registraron `tesoreriaService.registrarMovimientoDeDocumento` (`:565-573`) y no se revierten; no valida devoluciones vigentes (doble reversa, ver §4.3-2); `comision_venta` no se toca y `TurnoCajaQueryRepository.java:278-288` la resta del efectivo esperado aunque la venta esté anulada.
**Decisión:** corregir.
**Diseño:** bloquear si hay devoluciones/notas vigentes (anular primero esas o usar NC total); revertir tesorería por cada `venta_pago` con cuenta bancaria; marcar `comision_venta` como anulada (excluir en liquidación y cierre).
**Impacto:** backend; DB (estado en `comision_venta`).
**Dependencias:** GAP-B-001.
**Pruebas:** anular venta con transferencia; con devolución vigente (rechazo); con comisión pendiente.
**Criterios:** saldo de la cuenta bancaria vuelve al valor previo; comisión fuera de liquidación.

### GAP-B-009 — Numeración de documentos duplicable
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** venta `MAX(consecutivo)+1` por sucursal y `catch → 1L` (`repo/ventas/VentaQueryRepository.java:124-136`; TODO en `VentaServiceImpl.java:284-289`); devolución `count+1` (`DevolucionServiceImpl.java:187`); OC `count por año +1` (`OrdenCompraServiceImpl.java:70-74`); cotización `obtenerSiguienteConsecutivo` (`CotizacionServiceImpl.java:95`). La tabla `venta` es pre-V14: no hay migración que pruebe un UNIQUE (ver `docs/ESTADO.md` §3.1). Recibo de caja y notas contables ya lo resuelven con advisory lock + UNIQUE (`mig/V167`, `repo/contabilidad/NotaDiarioQueryRepository.java:352`).
**Problema:** números repetidos o "1" silencioso ante error; con `count` los anulados/eliminados reutilizan números.
**Decisión:** implementar.
**Diseño:** tabla `documento_consecutivo(empresa_id, sucursal_id, tipo, prefijo, siguiente)` con `UPDATE ... RETURNING` (o advisory lock por clave) + `UNIQUE` por documento; quitar el `catch`.
**Impacto:** DB (tabla + UNIQUE; verificar duplicados existentes antes del índice), backend.
**Riesgos:** duplicados históricos impiden crear el UNIQUE → deduplicar/renumerar marcando.
**Dependencias:** ninguna.
**Pruebas:** 50 ventas concurrentes en la misma sucursal → 50 consecutivos distintos.
**Criterios:** UNIQUE activo en venta, devolución, OC, cotización.

### GAP-B-010 — Stock actualizado sin bloqueo en venta, compra y devolución (cross-ref bloque inventario)
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** validación y escritura separadas y sin lock: `VentaServiceImpl.java:384-399` / `:478-486`; `CompraServiceImpl.java:571-585`; `DevolucionServiceImpl.java:551-559`; sin `@Lock`/`@Version` en `InventarioEntity` (grep).
**Problema:** lost update y ventas por encima del stock cuando no se permite negativo.
**Decisión:** implementar (el diseño lo lidera el bloque de inventario; aquí se exige que los tres flujos usen el mismo servicio).
**Diseño:** `InventarioStockService.mover(bodega, producto, delta, permitirNegativo)` con `SELECT ... FOR UPDATE` (orden por producto_id para evitar deadlocks) o `UPDATE ... SET stock = stock + :d WHERE ... AND (stock + :d >= 0 OR :neg) RETURNING`.
**Dependencias:** bloque inventario.
**Criterios:** prueba concurrente de 2 ventas por la última unidad → una falla.

### GAP-B-011 — Recepción de OC sin documento y sin cruce con la compra
**Estado:** MISSING (remisión) / PARTIAL (OC) · **Prioridad:** P0
**AURA actual:** `OrdenCompraServiceImpl.java:202-241` suma `cantidad_recibida` y cierra la OC; el front navega a `/compras/nueva` con `state` (`index-ordenes.component.ts:363-373`); si el usuario abandona, la OC queda CERRADA sin inventario; `compraId` nunca se asigna; `compra` no tiene `orden_compra_id`.
**Referencia:** §2.5–2.7, §4.22; regla crítica "legalizar una remisión no debe volver a ingresar inventario".
**Problema:** OC cerradas sin mercancía, sin trazabilidad OC↔compra y sin control de doble ingreso.
**Decisión:** implementar.
**Diseño (decisión AURA):** no crear un tercer documento si el cliente no lo necesita: la **recepción** se registra como `compra` con `tipo_documento='REMISION_COMPRA'` (mueve inventario, sin CxP ni asiento de proveedor o con asiento a "mercancía por legalizar" parametrizable) o directamente como factura. Cada línea de compra/remisión guarda `orden_compra_detalle_id`; la cantidad recibida de la OC se **deriva** de Σ líneas vigentes (no se escribe desde el front). La factura que legaliza una remisión aplica `compra_detalle_origen_id` + cantidad y **solo mueve inventario por la parte no remisionada**.
**Impacto:** DB (FKs de línea, tipo de documento), backend (Compra, OC), front (flujo "recibir" = guardar documento).
**Riesgos:** OCs históricas con recibido manual → migrar como "recibido sin soporte".
**Dependencias:** GAP-B-021, GAP-B-010.
**Pruebas:** OC 10 → remisión 6 → factura 6 + 4 nuevos → stock +10 exacto; anular remisión recalcula pendiente.
**Criterios:** imposible cerrar una OC sin documentos de recepción que sumen sus cantidades.

### GAP-B-012 — Gasto: editar y eliminar no son consistentes
**Estado:** PARTIAL · **Prioridad:** P0
**AURA actual:** `GastoServiceImpl.java:231-248` cambia `monto`, fecha y tributarios sin re-contabilizar ni tocar CxP/tesorería/caja/CE; `:252-266` marca ELIMINADO, anula el CE y reversa asiento, pero deja la CxP viva y no revierte pagos.
**Decisión:** corregir.
**Diseño:** editar solo campos no económicos, o aplicar el mismo patrón de `CompraServiceImpl.actualizar` (revertir pagos, sincronizar CxP, re-postear); eliminar → anular con las mismas validaciones de GAP-B-007 (abonos, DS aceptado).
**Dependencias:** GAP-B-007 (reutilizar), GAP-B-013.
**Criterios:** tras editar o anular, gasto = asiento = CxP = movimientos de dinero.

### P1

### GAP-B-013 — Compra/gasto con Documento Soporte aceptado se pueden editar o anular; falta nota de ajuste
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `DocumentoSoporteServiceImpl.java:129-173` impide solo re-emitir; `CompraServiceImpl.actualizar/anular` y `GastoServiceImpl` no consultan DS (grep sin referencias). No existe "nota de ajuste al DS". DS no habilitado aún ante la DIAN (memoria).
**Referencia:** §2.8; normativa DIAN vigente para DS y nota de ajuste (verificar).
**Diseño:** guard 409 si hay DS ACEPTADO; corrección vía NC compra + nota de ajuste DS electrónica.
**Dependencias:** GAP-B-007, GAP-B-012. **Criterios:** ningún origen con DS aceptado cambia importes.

### GAP-B-014 — Editar compra es destructivo y sin controles de contexto
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `CompraServiceImpl.java:1220-1426`: borra detalles físicamente (`:1302`), no valida NC hijas (puede dejar NC acreditando más de lo comprado), no pasa por `controlFechaRetroactiva` ni período cerrado (el re-posteo falla después del commit y queda en ErrorLog).
**Diseño:** bloquear edición si hay NC/DS/período cerrado; preferir "anular y re-registrar" con vínculo `compra_reemplaza_id`; si se conserva la edición, versionar detalles (no DELETE).
**Dependencias:** GAP-B-027.

### GAP-B-015 — Sin idempotencia de creación ni control de factura de proveedor duplicada
**Estado:** MISSING · **Prioridad:** P1
**AURA actual:** ninguna clave de idempotencia (grep); compra sin UNIQUE `(empresa, proveedor, numero_compra)`.
**Diseño:** header `Idempotency-Key` + tabla `idempotencia(empresa_id, clave, endpoint, respuesta, created_at)` en venta, compra, recibo, abono, devolución; UNIQUE parcial de factura de proveedor (tipo FACTURA_COMPRA, no anulada).
**Criterios:** doble POST idéntico → un solo documento.

### GAP-B-016 — Abonos CxC/CxP sin bloqueo
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `CuentaCobrarServiceImpl.registrarAbono` (`:171-259`) y `CuentaPagarServiceImpl.registrarAbono` (`:159-260`) usan `findByIdAndEmpresaId`; `CuentaCobrarJPARepository.bloquear` (`:13-18`) solo lo usan recibo y acuerdos; `TurnoCajaServiceImpl.registrarMovimiento` (`:221-289`) no valida monto ≤ saldo.
**Diseño:** usar `bloquear` (crear equivalente en CxP) en todos los caminos; validar tope.

### GAP-B-017 — Accesos cruzados entre empresas (IDOR) en flujos de este bloque
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `TurnoCajaServiceImpl.java:222` y `:265` `findById` de CxC/CxP sin empresa; `DevolucionServiceImpl.java:286` `productoRepository.findById` sin empresa; `VentaServiceImpl.java:209-215` acepta un turno abierto de otro usuario de la empresa.
**Diseño:** `findByIdAndEmpresaId` en todos; exigir turno del usuario (o permiso explícito). Complementa `docs/PLAN_SEGURIDAD.md`.

### GAP-B-018 — El reembolso de la devolución deduce el origen de fondos
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `DevolucionServiceImpl.java:652-719`: efectivo → turno del usuario con `ifPresent` (si no hay turno, el egreso se pierde) y primera cuenta `CAJA` activa; transferencia → primera cuenta `BANCO` activa. Contradice la regla "el origen se declara, no se deduce" (`docs/PAGO_OBLIGACIONES_ORIGEN.md`, `OrigenFondosService`).
**Diseño:** usar `OrigenFondosService.resolver` + `exigirSaldoDisponible` con origen declarado por el usuario.

### GAP-B-019 — Pedido: dinero sin registrar, sin abonos parciales, sin plan separe
**Estado:** PARTIAL/MISSING · **Prioridad:** P1
**AURA actual:** `PedidoVendedorServiceImpl.java:257-277` pasa a COBRADA sin venta → dinero sin caja, sin anticipo, sin asiento; con venta, `:283-330` crea `abono_cobrar` directo sin turno ni origen ni lock. Pedido espejo en cada venta POS (`VentaServiceImpl.java:670-726`) queda "CREADA" y puede "cobrarse".
**Referencia:** §3.2 (pedido con abonos, plan separe).
**Diseño:** abonos al pedido = **anticipo de cliente** (GAP-B-023) con origen declarado; al facturar, el anticipo se cruza; cobro posterior = recibo de caja; espejo creado en estado terminal (COBRADA/FACTURADA) y no cobrable. Reserva de disponibilidad opcional por empresa (decisión de producto).
**Dependencias:** GAP-B-023, GAP-B-021.

### GAP-B-020 — Cotización convertida sin estado ni vínculo
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `CotizacionServiceImpl.java:252-265` no cambia estado; CONVERTIDA nunca se asigna (grep); `CreateVentaDto` sin `cotizacionId`; `vencerCotizacionesExpiradas` hace `findAll()` global (`:298-308`).
**Diseño:** `venta.cotizacion_id` / relación genérica; al crear la venta, la cotización pasa a CONVERTIDA (parcial si no se tomaron todas las líneas); query de vencimiento por estado y fecha.

### GAP-B-021 — Sin grafo de documentos ni cantidades aplicadas (control de cruces)
**Estado:** MISSING · **Prioridad:** P1
**AURA actual:** relaciones ad-hoc (`compra.compra_origen_id`, `devolucion.venta_id`, `pedido.venta_id`, `nota_electronica.bill_id`); ninguna guarda cantidad/valor aplicado por línea.
**Referencia:** §2.12, §4.22, §7.3.
**Diseño:** `documento_relacion(empresa_id, origen_tipo, origen_id, origen_linea_id, destino_tipo, destino_id, destino_linea_id, cantidad, valor, estado, created_by, created_at)`; pendientes = origen − Σ aplicado vigente; vista "Documentos relacionados" en cada detalle; reporte de control de cruces (pedidos/OC/remisiones pendientes).

### GAP-B-022 — NC por descuento de compra, ND de compra y ND/NC de venta sin efecto en subledger
**Estado:** MISSING/PARTIAL · **Prioridad:** P1
**AURA actual:** NC compra exige cantidades (`CompraServiceImpl.java:191-234`); no hay ND compra; ND/NC de venta no tocan CxC (GAP-B-004).
**Diseño:** tipos `NOTA_CREDITO_VALOR` y `NOTA_DEBITO` en compra (sin inventario, prorrateo de IVA/retenciones, efecto CxP); en ventas se resuelve dentro de GAP-B-004.

### GAP-B-023 — Anticipos incompletos
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `AnticipoService.java:47-78` sin movimiento de caja/tesorería, método por defecto EFECTIVO (deducido), sin anulación ni lock; cruce (`:85-163`) escribe CxC/CxP sin `bloquear`. NC compra SALDO_A_FAVOR no genera anticipo.
**Diseño:** anticipo con origen declarado (caja/banco), comprobante RC/CE, anulación con validación de cruces, cruce con lock; NC compra saldo a favor → anticipo de proveedor automático; abonos de pedido → anticipo de cliente.

### GAP-B-024 — Precio, impuesto y descuento confiados al cliente; sin límites de descuento
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `VentaServiceImpl.java:402-407` usa `precioUnitario`, `descuentoValor`, `impuestoValor` del request; compra igual con `impuestoValor` (`CompraServiceImpl.java:528`); reglas de descuento y precios por cliente solo en el front; descuento manual libre (`pos.component.ts:1339-1340`).
**Nota de producto:** el **cambio de precio en el POS no es descuento** y no se propone convertirlo (memoria "pos-cambio-precio-no-es-descuento").
**Diseño:** recalcular impuesto en backend desde la configuración del producto y validar contra lo enviado (tolerancia); `descuento_maximo_pct` por rol/usuario (y override con autorización registrada); registrar `precio_lista` vs `precio_vendido` en la línea para auditoría del cambio de precio sin restringirlo.

### GAP-B-025 — Comisiones de ventas anuladas o devueltas siguen vigentes
**Estado:** PARTIAL · **Prioridad:** P1 (se resuelve en TASK-B-003)
**AURA actual:** `ComisionServiceImpl.procesarComisionVenta` (`:343`) genera; nada la revierte; `TurnoCajaQueryRepository.java:278-288` la resta del esperado sin filtrar anuladas; consultas de comisión sin filtro de estado de venta.

### GAP-B-026 — Estados como strings libres e inconsistentes
**Estado:** DIFFERENT (deuda) · **Prioridad:** P1
**AURA actual:** CxC usa `pagada`, `parcial`, `pendiente`, `activa`, `anulada` (`CuentaCobrarServiceImpl.java:244,315,346,415,459`; `DevolucionServiceImpl.java:368,510`); venta `COMPLETADA/PAGO_PARCIAL/ANULADA`; compra `RECIBIDA/ANULADA`; turno `ABIERTA/CERRADA`; OC y pedidos con literales repetidos.
**Diseño:** enums Java + `CHECK` en BD + normalización de datos (`activa`→`pendiente`/`parcial`).

### GAP-B-027 — Borrado físico en documentos con efecto económico
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `CuentaCobrarServiceImpl.java:341` y `:418`, `CuentaPagarServiceImpl.java:343` y `:418` (`delete` de abonos); `CompraServiceImpl.java:1302` (detalles); `CotizacionServiceImpl.java:187`; `OrdenCompraServiceImpl.java:168`; `FactusNotaService.java:311`.
**Diseño:** anulación lógica (`anulado`, `anulado_por`, `anulado_at`, `motivo`) para abonos y detalles económicos; en borradores administrativos (cotización/OC BORRADOR) el borrado es aceptable.

### GAP-B-028 — FE bajo demanda sin cola de pendientes; verificar POS electrónico
**Estado:** PARTIAL · **Prioridad:** P1 (compliance a verificar)
**AURA actual:** la FE se genera solo si alguien la pide (`VentaServiceImpl.java:654-656`, `VentaController.java:89`); no hay listado de ventas pendientes de transmitir ni envío masivo; ventas `tipo_documento='POS'` sin documento electrónico.
**Referencia:** §3.14; **verificar normativa DIAN vigente** sobre documento equivalente electrónico POS y plazos de transmisión antes de implementar.
**Diseño:** política por empresa (`EMITIR_SIEMPRE | BAJO_DEMANDA`), cola de pendientes con envío individual/masivo sobre el outbox de GAP-B-006, alerta de ventas sin transmitir.

### GAP-B-029 — Retenciones en compra por porcentaje manual
**Estado:** PARTIAL · **Prioridad:** P1
**AURA actual:** `CompraServiceImpl.java:616-631` aplica `%` que manda el front sobre toda la base, sin base mínima ni concepto; existe `TarifaRetencionServiceImpl`/`mig/V57__tabla_retenciones.sql` sin uso aquí; alimenta el borrador 350 (V181).
**Diseño:** sugerir retenciones desde concepto + calidad tributaria del tercero + base mínima en UVT (parametrizada, verificar vigencia anual); override con permiso.

### P2

### GAP-B-030 — Recepción de FE de proveedores (XML) y eventos RADIAN
**Estado:** MISSING · **Prioridad:** P2 · Carga de XML/AttachedDocument, precarga de compra, eventos acuse/recibo del bien/aceptación. **Verificar normativa vigente** (obligatoriedad según factura a crédito/título valor).

### GAP-B-031 — Turno de caja: controles de arqueo
**Estado:** PARTIAL · **Prioridad:** P2 · `TurnoCajaServiceImpl.java:156-200` cierra cualquier usuario de la empresa, sin conteo por denominaciones ni cierre ciego, `metodoPago "efectivo"` en minúscula (`:233`, `:276`).

### GAP-B-032 — Remisión de venta y devolución de remisión
**Estado:** DIFFERENT · **Prioridad:** P2 · Hoy despacho = venta a crédito. Solo si hay clientes con despacho previo a facturación (distribución); reutiliza el diseño de GAP-B-011/021.

### GAP-B-033 — Reportes de compras
**Estado:** MISSING · **Prioridad:** P2 · Por proveedor, producto, centro de costo, forma de pago, comparativo mensual; Excel/PDF; procesamiento asíncrono para rangos grandes.

### GAP-B-034 — Importación/exportación de documentos
**Estado:** PARTIAL · **Prioridad:** P2 · Extender `ImportacionController` (validar/confirmar) a cotizaciones, pedidos, compras; exportación encabezado+detalle de listados.

### GAP-B-035 — Smart create en documentos
**Estado:** MISSING · **Prioridad:** P2 · Alta rápida de tercero/producto desde el autocomplete estándar sin salir del documento.

### GAP-B-036 — Envío de documentos por email/WhatsApp
**Estado:** PARTIAL · **Prioridad:** P2 · Cotización, OC, venta, recibo, estado de cuenta: email propio (SMTP/servicio) y WhatsApp (link inicialmente; API después).

### GAP-B-037 — Desglose de IVA hardcodeado 0/5/19
**Estado:** PARTIAL · **Prioridad:** P2 · `VentaServiceImpl.java:510-523` y `DevolucionServiceImpl.java:617-621`; mover a tabla por tarifa (`venta_impuesto`).

### GAP-B-038 — Flujo `factura` legacy y scheduler de reintentos vivo
**Estado:** DIFFERENT · **Prioridad:** P2 · `FacturaRetryScheduler.java:30` cada 5 min sobre un flujo que ya no emite; retirar o apagar por propiedad y documentar.

### GAP-B-039 — Vista previa de contabilización
**Estado:** MISSING · **Prioridad:** P2 · Ejecutar el generador en modo simulación (sin persistir) para compra/venta/gasto antes de guardar.

### P3

| GAP | Capacidad | Estado | Nota |
|---|---|---|---|
| GAP-B-040 | Replicar documento / gastos recurrentes | MISSING | Copiar compra/gasto a nueva fecha |
| GAP-B-041 | Comisiones por recaudo, rangos, metas, forma de pago | PARTIAL | Hoy por producto/categoría |
| GAP-B-042 | Moneda extranjera / TRM / bimoneda | MISSING | Solo si hay clientes importadores/exportadores |
| GAP-B-043 | Factura de exportación | MISSING | Depende de GAP-B-042 |
| GAP-B-044 | Ingresos para terceros (mandato) | MISSING | Inmobiliarias/transporte |
| GAP-B-045 | Campos XML DIAN personalizados (OC, despacho) | MISSING | Tras GAP-B-005 |
| GAP-B-046 | Factura de servicio sectorial / AIU | MISSING | Construcción; cruza con proyectos/frentes |

---

## 6. Fortalezas (conservar)

| Fortaleza | Evidencia | Por qué es mejor |
|---|---|---|
| NC de compra con validación contra origen, tres destinos del dinero y reversión completa al anular | `svc/CompraServiceImpl.java:114-234`, `:860-1063` | Supera la referencia: controla acumulado acreditable, proveedor, sucursal y stock |
| Origen de fondos declarado, "salida de caja otro día", freno de fechas retroactivas | `svc/CompraServiceImpl.java:495-511`, `:759-806`; `docs/PLAN_CAJA_FONDOS.md` | Evita arqueos falsos y egresos fantasma |
| Edición de compra que rehace pagos, CxP, comprobante y asiento | `svc/CompraServiceImpl.java:1407-1423` | Base para el patrón de anulación (GAP-B-007) |
| Recibo de caja multi-factura con locks y anulación con control de turno cerrado | `svc/ReciboCajaServiceImpl.java:110`, `:276-309` | Patrón a replicar en abonos sueltos y anticipos |
| Retenciones al recaudo como abonos vinculados | `svc/RetencionRecaudoService.java`; `mig/V181` | Resuelve §3.13 sin hardcode |
| Venta inmutable (sin edición) | `controllers/VentaController.java` | Correcto para documento fiscal |
| Contabilidad por eventos AFTER_COMMIT y cuentas por concepto | ADR-003, ADR-006; `svc/ContabilidadAutoServiceImpl.java` | No hay cuentas hardcodeadas en ventas/compras |
| Presentaciones, lotes FEFO y seriales integrados en compra, venta, devolución y NC | `CompraServiceImpl:249-281, 1581-1652`; `VentaServiceImpl:337-354, 494-504` | Trazabilidad fina |
| Control de crédito (cupo, bloqueo, autorización de un solo uso) | `svc/VentaServiceImpl.java:255-271, 649` | Superior a la referencia |
| Cotización con vencimiento y reactivación única | `svc/CotizacionServiceImpl.java:267-309` | Protege precios viejos |
| Comprobante de egreso único por compra, anulable, no borrable | `svc/CompraServiceImpl.java:819-851`; `mig/V143` | Soporte documental correcto |
| Caja menor, traslados de fondos con anulación, carritos abandonados | V177/V178/V182 | Controles de mostrador que la referencia no muestra |
| Anti-reenvío de notas por `reference_code` | `svc/FactusNotaService.java:351-354` | Patrón a llevar a la FE de venta |

---

## 7. No aplicables (por ahora) y fuera de bloque

| Capacidad | Decisión | Motivo |
|---|---|---|
| Moneda extranjera, exportación, ingresos para terceros, AIU | NOT_APPLICABLE hoy → P3 | Perfil actual de clientes (POS/comercio/obra) sin evidencia de demanda |
| WhatsApp vía QR de una línea (como la referencia) | No copiar | Diseñar integración por API/links; la limitación de "una línea" no aporta |
| Remisión de venta como documento obligatorio | DIFFERENT | El POS factura al despachar; solo para distribución |
| Venta de activo fijo (§3.23) | Fuera de bloque | Bloque de activos/contabilidad |
| Costeo (último costo en `producto.costo`, `CompraServiceImpl.java:1545-1551`) | Cross-ref | Bloque de inventario; afecta costo de venta de este bloque |
| Permisos por acción/campo en anular, descuentos, precios | Cross-ref | `docs/PLAN_SEGURIDAD.md` (642 endpoints sin control de rol); aquí solo se exigen las acciones |

---

## 8. Fases del bloque

Agrupadas por dependencia, no por tamaño. Cada fase requiere migración Flyway + espejo idempotente en el proyecto Laravel (Flyway está apagado; `ddl-auto=validate`).

| Fase | Objetivo | Gaps | Depende de | Salida verificable |
|---|---|---|---|---|
| **B0 — Contención** (hotfixes, sin cambio de modelo) | Cerrar las vías de corrupción inmediata | 001, 003, 005, 006 (quitar retry + reserva de estado), 007, 008, 012, 017 | — | Tests de regresión de anulación/devolución/FE; script de conciliación de datos afectados |
| **B1 — Integridad transaccional** | Numeración, locks, idempotencia, estados, borrado lógico | 009, 010 (con bloque inventario), 015, 016, 026, 027 | B0 | UNIQUE activos; pruebas concurrentes verdes |
| **B2 — Grafo documental** | Relaciones con cantidades aplicadas y control de cruces | 021, 020, 011, 023 | B1 | Vista "documentos relacionados"; reporte de pendientes; OC→recepción→factura sin doble ingreso |
| **B3 — Notas y devoluciones** | Devolución inmutable, nota de venta unificada, notas de compra por valor, edición segura | 002, 004, 022, 014, 018, 025, 019 | B2 | Matriz §4 sin ⚠ en notas; subledger = mayor |
| **B4 — Fiscal electrónico** | Outbox FE/NC/DS, cola de pendientes, nota de ajuste DS, RADIAN | 006 (completo), 028, 013, 030, 045 | B3 + verificación normativa | Reconciliación Factus sin huérfanos |
| **B5 — Políticas comerciales** | Precio/impuesto server-side, límites de descuento, retenciones parametrizadas, arqueo | 024, 029, 031, 037 | B1 (y permisos de seguridad) | Ventas rechazadas fuera de política; retenciones sugeridas |
| **B6 — Productividad y reportes** | Reportes compras, import/export, smart create, envío, vista previa, limpieza legacy | 033, 034, 035, 036, 039, 038, 032 | B2 | — |
| **B7 — Especializaciones** | P3 | 040–046 | B4/B6 | — |

Siguiente bloque ejecutable: **B0** (todas sus tareas son independientes entre sí salvo TASK-B-003 que usa el guard de TASK-B-001).

---

## 9. Tareas

### 9.1 P0

## TASK-B-001 — Bloquear la anulación de ventas emitidas electrónicamente

**Épica:** B0 Contención · **Módulo:** Ventas/FE · **Tipo:** Backend / Frontend / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
`anular` revierte inventario, CxC y asiento de una venta con CUFE sin emitir NC.

### Evidencia AS-IS
- Archivo: `svc/VentaServiceImpl.java:728-831`; `controllers/VentaController.java:72-75`
- Componente: `front/features/ventas/detalle/detalle-venta.component.ts:61`, `.html:124`
- Tabla: `venta` (`cufe`, `estado_dian`)
- Endpoint: `PATCH /api/ventas/{id}/anular`
- Comportamiento: anula sin consultar el estado DIAN.

### Referencia funcional
Baseline §3.9–3.14: corrección de documentos fiscales solo por notas.

### Decisión
Implementar.

### Diseño propuesto
Guard en `VentaServiceImpl.anular`: si `cufe != null || "EMITIDA".equals(estadoDian)` → 409 "La venta tiene factura electrónica: emita una nota crédito". Aplica también a `PedidoVendedorServiceImpl.anular` (`:349-351`), que delega. Front: ocultar "Anular" y mostrar "Emitir nota crédito" cuando hay CUFE.

### Backend
Guard + mensaje; registrar intento en log de auditoría.

### Frontend
Condición `!venta.cufe` en la zona de anular; botón a la NC (modal existente `features/notas/nota-credito-modal`).

### Base de datos
Ninguna.

### API
Mismo endpoint, nuevo 409.

### Seguridad y permisos
Sin cambio (la restricción es de negocio, no de rol).

### Inventario/contabilidad
Evita reversas sin soporte fiscal.

### Auditoría
Log del intento rechazado (usuario, venta).

### Migración de datos
Reporte: `SELECT id FROM venta WHERE estado_venta='ANULADA' AND cufe IS NOT NULL` para conciliar con NC emitidas.

### Pruebas unitarias
Guard con y sin CUFE.

### Pruebas integración
Anular venta EMITIDA → 409 y sin cambios en inventario/CxC/asiento.

### Pruebas E2E
Detalle de venta con CUFE no muestra "Anular".

### Dependencias
Ninguna.

### Riesgos
Ventas FE con error de emisión (`RECHAZADA`) deben seguir anulables: el guard mira solo EMITIDA/CUFE.

### Criterios de aceptación
- [ ] Ninguna venta con CUFE puede pasar a ANULADA por el endpoint de anular.
- [ ] Pedido con venta FE tampoco se anula.
- [ ] Reporte de ventas ya anuladas con CUFE entregado.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas (n/a) · [ ] Pruebas verdes · [ ] Permisos probados · [ ] Auditoría validada · [ ] Manual `/ayuda` actualizado (mensaje de error nuevo)

---

## TASK-B-002 — Anulación de compra completa (CxP, pagos, validaciones)

**Épica:** B0 Contención · **Módulo:** Compras/CxP · **Tipo:** Backend / DB / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
Anular compra deja CxP viva y dinero (banco/caja) sin revertir; no valida abonos, NC hijas ni DS.

### Evidencia AS-IS
- Archivo: `svc/CompraServiceImpl.java:1137-1218`; `revertirPagosAnteriores` en `:1436-1491`
- Tabla: `compra`, `compra_pago`, `cuenta_pagar`, `abono_pagar`, `movimiento_caja`, `tesoreria_movimiento`
- Endpoint: anular de `CompraController`
- Comportamiento: solo inventario + CE + asiento.

### Referencia funcional
§7.4 y §5: la anulación revierte los mismos ejes que el documento movió.

### Decisión
Implementar.

### Diseño propuesto
Orden dentro de la transacción: (1) validar: sin NC vigentes (`findByCompraOrigenIdAndEmpresaId` no anuladas), CxP sin abonos, sin DS ACEPTADO; (2) `revertirPagosAnteriores(compra, …)`; (3) CxP → `anulada`, saldo 0; (4) inventario/lotes/seriales (actual); (5) comprobante (actual); (6) estado + motivo + usuario; (7) evento de reversa (actual). Para NC: `revertirDestinoNotaCredito` ya existe (`:1006-1063`).

### Backend
Refactor `anular(id, empresaId, usuarioId, motivo)`; nuevo `CuentaPagarService.anularPorCompra`.

### Frontend
Pedir motivo en el diálogo de anulación de compra.

### Base de datos
`compra.anulado_por INT`, `compra.anulado_at TIMESTAMP`, `compra.motivo_anulacion VARCHAR(500)` (V18x + espejo Laravel idempotente).

### API
Body `{ motivo }` en anular; 409 con causa.

### Seguridad y permisos
Acción `compras.anular` (cross-ref plan de seguridad).

### Inventario/contabilidad
Sin cambios de lógica; asegura que la reversa contable (que incluye Caja/Banco) tenga su contraparte operativa.

### Auditoría
Motivo, usuario y fecha persistidos.

### Migración de datos
Script: compras ANULADAS con CxP `pendiente/parcial` y con `compra_pago.activo=true` → listado para conciliación manual (no auto-corregir).

### Pruebas unitarias
Cada validación bloqueante.

### Pruebas integración
Contado efectivo (ingreso de reverso en turno), contado banco (saldo restituido), crédito sin abonos (CxP anulada), crédito con abonos (409), con NC (409).

### Pruebas E2E
Flujo de anulación con motivo.

### Dependencias
Ninguna (DS guard se refuerza en TASK-B-013).

### Riesgos
Turno original cerrado: el reverso entra al turno de hoy (patrón ya usado en edición, `:1467-1484`).

### Criterios de aceptación
- [ ] Tras anular: 0 CxP pendientes y 0 `compra_pago` activos de la compra.
- [ ] Saldo bancario igual al previo a la compra.
- [ ] Rechazo con mensaje si hay abonos/NC/DS.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas (Flyway + Laravel) · [ ] Pruebas verdes · [ ] Permisos probados · [ ] Auditoría validada · [ ] Documentación actualizada

---

## TASK-B-003 — Anulación de venta completa (banco, devoluciones, comisiones)

**Épica:** B0 Contención · **Módulo:** Ventas/Comisiones/Tesorería · **Tipo:** Backend / DB / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
Anular venta no revierte movimientos bancarios de sus pagos, no bloquea si hay devoluciones vigentes y deja la comisión viva (también en el cierre de turno).

### Evidencia AS-IS
- Archivo: `svc/VentaServiceImpl.java:549-574` (tesorería), `:728-831` (anular); `repo/turno_caja/TurnoCajaQueryRepository.java:278-288`; `svc/ComisionServiceImpl.java:343`
- Tablas: `venta_pago`, `tesoreria_movimiento`, `cuenta_bancaria`, `devolucion`, `comision_venta`

### Referencia funcional
§5 ventas; §3.20 comisiones.

### Decisión
Implementar.

### Diseño propuesto
Validar: sin devoluciones `COMPLETADA` ni notas vigentes; por cada `venta_pago` con `cuenta_bancaria_id` → `registrarMovimientoDeDocumento(egreso=true, "Reverso venta …")`; `comision_venta.estado='ANULADA'`; excluir anuladas en `totalComisionesTurno` y en pendientes de liquidación; si la comisión ya fue liquidada/pagada → registrar ajuste negativo para la próxima liquidación.

### Backend
`VentaServiceImpl.anular`, `ComisionService.anularPorVenta`, queries de comisión.

### Frontend
Mensaje cuando hay devoluciones vigentes.

### Base de datos
`comision_venta.estado VARCHAR(20) DEFAULT 'VIGENTE'` + CHECK; `venta.anulado_por/anulado_at/motivo_anulacion`.

### API
Body `{ motivo }`.

### Seguridad y permisos
`ventas.anular`.

### Inventario/contabilidad
Evita la doble reversa de §4.3-2.

### Auditoría
Motivo/usuario/fecha.

### Migración de datos
Marcar `comision_venta` de ventas ANULADAS como ANULADA; listar ventas anuladas con pagos bancarios para ajuste de saldo.

### Pruebas unitarias
Exclusión de comisiones anuladas en el cálculo del turno.

### Pruebas integración
Venta con transferencia → anular → saldo restituido; venta con devolución → 409.

### Pruebas E2E
Cierre de turno tras anular una venta con comisión.

### Dependencias
TASK-B-001.

### Riesgos
Liquidaciones ya pagadas: requieren ajuste, no borrado.

### Criterios de aceptación
- [ ] Saldo bancario restituido.
- [ ] Comisión fuera de liquidación y del esperado del turno.
- [ ] Venta con devolución vigente no se anula.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Permisos probados · [ ] Auditoría validada · [ ] Documentación actualizada

---

## TASK-B-004 — Devolución inmutable: no reescribir la venta; el cambio es una venta nueva

**Épica:** B3 Notas y devoluciones · **Módulo:** Ventas/Devoluciones · **Tipo:** Backend / DB / Frontend / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
La devolución hace UPDATE sobre `venta_detalle` y `venta`, y agrega líneas a la venta original.

### Evidencia AS-IS
- Archivo: `svc/DevolucionServiceImpl.java:251-275`, `:284-321`, `:323-325`, `:462-489`, `:596-622`
- Tablas: `venta`, `venta_detalle`, `devolucion`, `devolucion_detalle`
- Comportamiento: la venta refleja el neto post-devolución; líneas del "cambio" viven en la venta original; el match es por `producto_id` (`findFirst`).

### Referencia funcional
§3.9; §7.3; §7.4.

### Decisión
Refactorizar.

### Diseño propuesto
- `devolucion_detalle.venta_detalle_id` (FK, NOT NULL para nuevas).
- Disponible = `venta_detalle.cantidad − Σ devolucion_detalle.cantidad (devolución vigente)`, con lock sobre la venta.
- Cambio: crear **venta nueva** (misma sucursal/bodega/turno) con `venta.devolucion_origen_id`; el saldo a favor de la devolución se aplica como medio de pago `SALDO_DEVOLUCION`; el faltante se cobra en esa venta.
- Totales netos de la venta: consulta/vista `v_venta_neto`, nunca escritura.
- Anular devolución: solo revierte sus propios efectos.

### Backend
Reescritura de `DevolucionServiceImpl.crear/anular`; adaptar reportes que asumían venta neta (ventas, márgenes, gerencial, comisiones).

### Frontend
Pantalla de devolución por línea (no por producto); cambio como segundo paso que abre la venta nueva.

### Base de datos
FK nueva, `venta.devolucion_origen_id`, vista `v_venta_neto`; migración + espejo Laravel.

### API
`POST /devoluciones` recibe `ventaDetalleId` por línea; `POST /devoluciones/{id}/cambio`.

### Seguridad y permisos
`devoluciones.crear`, `devoluciones.anular`.

### Inventario/contabilidad
Inventario: +devuelto, −cambio (en la venta nueva). Asiento de devolución y asiento de la venta nueva separados.

### Auditoría
Venta original intacta; relación visible.

### Migración de datos
Reconstruir `venta_detalle.cantidad` original sumando devoluciones vigentes; marcar líneas agregadas por cambios (heurística `created_at > venta.fecha_emision`) para revisión manual; recalcular totales originales de la venta. Ejecutar en staging con informe antes/después.

### Pruebas unitarias
Cálculo de disponible con devoluciones múltiples y anuladas.

### Pruebas integración
Dos líneas del mismo producto; devolución 2 pasos + anular la primera; cambio con saldo a favor y en contra.

### Pruebas E2E
Devolución con cambio desde el front.

### Dependencias
TASK-B-005, TASK-B-020 (relaciones), TASK-B-010 (locks).

### Riesgos
Reportes que hoy leen la venta mutada cambian de resultado → comunicar.

### Criterios de aceptación
- [ ] Ningún UPDATE sobre `venta`/`venta_detalle` desde devoluciones.
- [ ] Σ devuelto por línea ≤ vendido.
- [ ] El cambio queda como venta nueva enlazada.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Permisos probados · [ ] Auditoría validada · [ ] Documentación y `/ayuda` actualizados

---

## TASK-B-005 — Valor de la devolución con descuentos de línea y general

**Épica:** B0 Contención · **Módulo:** Devoluciones · **Tipo:** Backend / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
El valor devuelto usa precio bruto; se reembolsa más de lo cobrado.

### Evidencia AS-IS
- Archivo: `svc/DevolucionServiceImpl.java:212-221`; comparar `svc/VentaServiceImpl.java:402-410, 527`.

### Referencia funcional
§3.9 "valor ligado a origen".

### Decisión
Corregir.

### Diseño propuesto
`netoUnit = (subtotal_linea − impuesto_valor) / cantidad` de la línea (semántica "subtotalLinea incluye IVA"); `ivaUnit = impuesto_valor / cantidad`; prorrata del `descuento_general` de la venta sobre el neto de la línea; redondeo a 2 decimales con ajuste final para no exceder el total.

### Backend
Método de cálculo único reutilizable por la nota de venta (TASK-B-006).

### Frontend
Mostrar el valor calculado por el backend (preview).

### Base de datos
Ninguna.

### API
`GET /devoluciones/preview?ventaId=&lineas=` opcional.

### Seguridad y permisos
Sin cambio.

### Inventario/contabilidad
Asiento de devolución con el valor correcto.

### Auditoría
—

### Migración de datos
Informe de devoluciones históricas con sobrevaloración (Σ devuelto − Σ neto correcto).

### Pruebas unitarias
10% de descuento de línea; descuento general; presentación.

### Pruebas integración
Devolución total de una venta con descuento → reembolso = total pagado.

### Pruebas E2E
—

### Dependencias
Ninguna.

### Riesgos
Diferencias de centavos → ajuste en la última línea.

### Criterios de aceptación
- [ ] Devolución total = `venta.total_pagar`.
- [ ] Nunca Σ devoluciones > total de la venta.

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] Documentación actualizada

---

## TASK-B-006 — Nota de venta unificada (devolución/descuento/débito) ligada a venta, CxC, inventario y NC/ND electrónica

**Épica:** B3 Notas y devoluciones · **Módulo:** Ventas/FE/Cartera · **Tipo:** Backend / DB / Frontend / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
NC/ND electrónicas no afectan inventario ni CxC ni se vinculan a la venta/devolución; riesgo de doble reversa.

### Evidencia AS-IS
- Archivo: `svc/FactusNotaService.java:346-427`; `contabilidad/application/generador/NotaCreditoGenerador.java`, `NotaDebitoGenerador.java`
- Tabla: `nota_electronica` (sin `venta_id`), `devolucion`
- Endpoint: `NotaElectronicaController`

### Referencia funcional
§3.9–3.11, §5.

### Decisión
Refactorizar.

### Diseño propuesto
Documento de dominio `nota_venta` (tipo DEVOLUCION | DESCUENTO | ANULACION_TOTAL | DEBITO) con líneas `venta_detalle_id, cantidad, valor, iva`. Efectos:
- DEVOLUCION: inventario +, CxC − (aplicación registrada, no UPDATE directo), reembolso con origen declarado si excede la deuda.
- DESCUENTO: CxC −, sin inventario.
- DEBITO: CxC + (nueva CxC o aumento con aplicación).
- Si la venta tiene CUFE: genera la NC/ND electrónica desde las líneas (payload armado en backend, no en el front) vía outbox (TASK-B-008); `nota_electronica.nota_venta_id`.
- Un único asiento por nota (el generador de NOTA_CREDITO/DEVOLUCION se unifica; costo de ventas se reversa solo en DEVOLUCION).
La `devolucion` actual queda como vista/compatibilidad o se migra a `nota_venta`.

### Backend
Servicio de notas; generadores; adaptar `FactusNotaService` a "emitir desde nota".

### Frontend
Un solo flujo "Nota a la venta" desde el detalle de venta; el modal suelto de NC queda solo para facturas sin venta local (histórico).

### Base de datos
`nota_venta`, `nota_venta_detalle`, FK `nota_electronica.nota_venta_id`, `nota_electronica.venta_id`.

### API
`POST /ventas/{id}/notas`; `GET /ventas/{id}/notas`.

### Seguridad y permisos
`ventas.nota_credito`, `ventas.nota_debito`.

### Inventario/contabilidad
Matriz §5 cumplida; una sola reversa del ingreso.

### Auditoría
Nota inmutable; anulación solo si no fue aceptada por DIAN.

### Migración de datos
Vincular `nota_electronica` existentes por `bill_id`/`factus_numero`; informe de ventas con devolución + NC sobre las mismas líneas (posible doble reversa histórica) para ajuste contable.

### Pruebas unitarias
Efectos por tipo.

### Pruebas integración
NC devolución en venta a crédito (CxC baja una vez; Clientes = subledger); NC descuento; ND; intento de NC sobre lo ya devuelto → rechazo.

### Pruebas E2E
Nota desde detalle de venta con FE.

### Dependencias
TASK-B-004, TASK-B-005, TASK-B-008, TASK-B-020.

### Riesgos
Contabilidad histórica con doble reversa requiere nota contable de ajuste (bloque contable).

### Criterios de aceptación
- [ ] Toda NC/ND electrónica nueva tiene `venta_id` y `nota_venta_id`.
- [ ] Saldo CxC por documento = saldo de Clientes por documento.
- [ ] Una sola reversa de ingreso por unidad devuelta.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes (golden files de asientos, ADR-004) · [ ] Permisos probados · [ ] Auditoría validada · [ ] Documentación actualizada

---

## TASK-B-007 — Payload de factura electrónica fiel a la venta

**Épica:** B0 Contención · **Módulo:** FE · **Tipo:** Backend / QA / Compliance · **Prioridad:** P0 · **Estado:** Proposed

### Problema
Descuentos omitidos, forma y medio de pago fijos, persona natural fija, IVA del producto actual.

### Evidencia AS-IS
- Archivo: `svc/VentaFacturaService.java:119-193`; `svc/FactusService.java:110-176`

### Referencia funcional
§3.14; anexo técnico DIAN vigente (verificar antes de implementar).

### Decisión
Corregir.

### Diseño propuesto
Construcción desde `venta_detalle` y `venta_pago`:
- precio con IVA por unidad desde la línea; `discount_rate` desde `monto_descuento`; prorrata del descuento general;
- `tax_rate` = imp/(subtotalLinea−imp) por línea (no del producto);
- `payment_form` = crédito si existe CxC, `payment_due_date` = vencimiento de la CxC;
- `payment_method_code` por medio de pago dominante con tabla de mapeo parametrizable;
- `legal_organization_id` y `tribute_id` desde el tercero (NIT → jurídica);
- `unit_measure_id` desde producto/presentación (mapeo);
- validación previa: Σ calculado = `total_pagar` ± 1.

### Backend
Nuevo `ArmadorFacturaElectronica` (patrón de `DocumentoSoporteArmador`).

### Frontend
Mostrar diferencias si la validación previa falla.

### Base de datos
Mapeos: `fe_mapeo_medio_pago(empresa_id, metodo_pago, codigo_dian)`, `unidad_medida.codigo_dian` si no existe.

### API
Sin cambio.

### Seguridad y permisos
—

### Inventario/contabilidad
—

### Auditoría
Guardar payload enviado y respuesta (como `nota_electronica.payload_json`).

### Migración de datos
Informe de ventas emitidas con descuento (total DIAN ≠ total AURA) para evaluar NC de ajuste con el contador.

### Pruebas unitarias
Golden files de payload: contado, crédito, mixto, descuento línea, descuento general, exento, NIT, presentación.

### Pruebas integración
Sandbox Factus: total devuelto = total venta.

### Pruebas E2E
—

### Dependencias
Ninguna.

### Riesgos
Cambios de redondeo frente a Factus.

### Criterios de aceptación
- [ ] 100% de golden files verdes.
- [ ] Total del bill = `venta.total_pagar` en sandbox.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Revisión con contador · [ ] Documentación actualizada

---

## TASK-B-008 — Emisión electrónica idempotente (outbox, estados, reconciliación)

**Épica:** B0 (parte mínima) / B4 (completa) · **Módulo:** FE/NC/DS · **Tipo:** Backend / DB / DevOps / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
Reintento automático sobre POST no idempotente, check sin lock, HTTP dentro de la transacción, `reference_code` que colisiona entre sucursales.

### Evidencia AS-IS
- Archivo: `svc/FactusService.java:55-56, 119`; `application.properties:128-132`; `svc/VentaFacturaService.java:54-111, 179`
- Tabla: `venta` (`estado_dian`, `cufe`)

### Referencia funcional
§3.14 envío individual/pendientes, estado y seguimiento.

### Decisión
Implementar.

### Diseño propuesto
- **B0 (mínimo):** `resilience4j.retry.instances.factus-bill.max-attempts=1` (como nómina); reserva atómica `UPDATE venta SET estado_dian='ENVIANDO' WHERE id=:id AND (estado_dian IS NULL OR estado_dian IN ('RECHAZADA','ERROR'))`; en timeout → `DESCONOCIDO`.
- **B4 (completo):** tabla `documento_electronico(id, empresa_id, origen_tipo, origen_id, tipo, reference_code UNIQUE(empresa_id, reference_code), estado, intentos, payload, respuesta, cufe_cude, numero, next_retry_at)`; `reference_code = 'FE-' || venta.id` (único por empresa); job que reconcilia DESCONOCIDO consultando Factus por reference; llamada HTTP fuera de la transacción; mismo mecanismo para NC/ND y DS.

### Backend
Servicio de outbox + job `@Scheduled` con zona America/Bogota.

### Frontend
Estado visible (Enviando / Por confirmar / Emitida / Rechazada) y botón "Reintentar" solo en RECHAZADA.

### Base de datos
Tabla nueva + migración de estados existentes.

### API
`POST /ventas/{id}/factura-electronica` responde 202 con estado si queda en proceso.

### Seguridad y permisos
`ventas.facturar_electronica`.

### Inventario/contabilidad
—

### Auditoría
Historial de intentos y respuestas.

### Migración de datos
Poblar `documento_electronico` desde ventas con CUFE; ventas con reference viejo conservan su código.

### Pruebas unitarias
Transiciones de estado.

### Pruebas integración
WireMock: timeout tras aceptación → DESCONOCIDO → reconciliado a EMITIDA; dos POST concurrentes → un envío.

### Pruebas E2E
—

### Dependencias
TASK-B-009.

### Riesgos
Endpoint de consulta por reference en Factus debe confirmarse.

### Criterios de aceptación
- [ ] Nunca más de un POST a Factus por venta.
- [ ] Cero ventas "huérfanas" tras un ciclo del job.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Monitoreo del job · [ ] Documentación actualizada

---

## TASK-B-009 — Numeración transaccional única por documento

**Épica:** B1 Integridad · **Módulo:** Ventas/Devoluciones/OC/Cotizaciones · **Tipo:** Backend / DB / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
`MAX+1`/`count+1` sin lock ni UNIQUE; `catch → 1`.

### Evidencia AS-IS
- Archivo: `repo/ventas/VentaQueryRepository.java:124-136`; `svc/DevolucionServiceImpl.java:187`; `svc/OrdenCompraServiceImpl.java:70-74`; `svc/CotizacionServiceImpl.java:95`
- Patrón bueno existente: `mig/V167__recibo_caja_cartera.sql:32` + `repo/cartera/ReciboCajaQueryRepository.java:35`

### Referencia funcional
§1 checklist EMPRESA (prefijos/consecutivos).

### Decisión
Implementar.

### Diseño propuesto
`documento_consecutivo(empresa_id, sucursal_id NULL, tipo, prefijo, siguiente BIGINT, PRIMARY KEY(empresa_id, COALESCE(sucursal_id,0), tipo, prefijo))`; obtención con `UPDATE ... SET siguiente = siguiente + 1 RETURNING siguiente - 1` en la transacción del documento; UNIQUE `(sucursal_id, consecutivo)` en venta, `(empresa_id, consecutivo)` en devolución, `(empresa_id, numero_orden)` en OC, `(empresa_id, numero)` en cotización.

### Backend
`ConsecutivoService.siguiente(tipo, empresa, sucursal)`; eliminar `catch`.

### Frontend
—

### Base de datos
Tabla + semilla desde `MAX` actual + índices UNIQUE (tras deduplicar).

### API
—

### Seguridad y permisos
—

### Inventario/contabilidad
—

### Auditoría
Huecos de numeración por rollback aceptables y documentados (no se reutilizan números).

### Migración de datos
Detectar duplicados (`GROUP BY sucursal_id, consecutivo HAVING COUNT(*)>1`) y resolver antes del UNIQUE.

### Pruebas unitarias
—

### Pruebas integración
50 hilos creando ventas → consecutivos únicos.

### Pruebas E2E
—

### Dependencias
Ninguna.

### Riesgos
Contención en sucursales de alto volumen (fila caliente) — aceptable; alternativa secuencia por sucursal.

### Criterios de aceptación
- [ ] UNIQUE activos en los 4 documentos.
- [ ] Prueba concurrente verde.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas (Flyway + Laravel) · [ ] Pruebas verdes · [ ] Documentación actualizada

---

## TASK-B-010 — Movimiento de stock con bloqueo en venta, compra y devolución (con bloque inventario)

**Épica:** B1 Integridad · **Módulo:** Inventario (cross) · **Tipo:** Backend / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
Read-modify-write de `inventario.stock_actual` sin lock.

### Evidencia AS-IS
- Archivo: `svc/VentaServiceImpl.java:356-399, 457-486`; `svc/CompraServiceImpl.java:571-585, 1155-1169, 1267-1288, 1364-1369`; `svc/DevolucionServiceImpl.java:551-559, 573-581`
- Tabla: `inventario` (UNIQUE bodega+producto `mig/V172__bodegas.sql:194`)

### Referencia funcional
Checklist INVENTARIO "concurrencia".

### Decisión
Implementar (diseño compartido con el bloque de inventario).

### Diseño propuesto
Un único `StockService.mover(...)` que hace `SELECT ... FOR UPDATE` ordenado por `producto_id` (evita deadlocks entre líneas), valida negativo con el saldo bloqueado y escribe kardex con ese saldo. Venta, compra, devolución, NC compra y anulaciones lo usan.

### Backend
Refactor de los tres servicios.

### Frontend
—

### Base de datos
—

### API
—

### Seguridad y permisos
—

### Inventario/contabilidad
Kardex encadenado confiable (ver gotcha de signo del kardex).

### Auditoría
—

### Migración de datos
Recalcular saldos contra kardex (tarea del bloque inventario).

### Pruebas unitarias
—

### Pruebas integración
Dos ventas concurrentes por la última unidad → una rechazada; 100 compras concurrentes → saldo exacto.

### Pruebas E2E
—

### Dependencias
Bloque inventario.

### Riesgos
Deadlocks si no se ordena; tiempos de bloqueo en ventas grandes.

### Criterios de aceptación
- [ ] Sin lost updates en prueba concurrente.
- [ ] Stock negativo imposible cuando el producto no lo permite.

### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] Documentación actualizada

---

## TASK-B-011 — Recepción real de la orden de compra y legalización sin doble ingreso

**Épica:** B2 Grafo documental · **Módulo:** Compras · **Tipo:** Backend / DB / Frontend / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
La recepción de OC es un contador; no hay vínculo OC↔compra ni cantidades aplicadas.

### Evidencia AS-IS
- Archivo: `svc/OrdenCompraServiceImpl.java:202-241`; `front/features/compras/ordenes/index-ordenes.component.ts:335-373`
- Tablas: `orden_compra`, `orden_compra_detalle.cantidad_recibida`, `compra`, `compra_detalle`
- Comportamiento: `compraId` nunca se asigna.

### Referencia funcional
§2.5–2.7, §4.22.

### Decisión
Implementar.

### Diseño propuesto
- `compra_detalle.orden_compra_detalle_id` (FK) y `compra_detalle.compra_detalle_origen_id` (para factura que legaliza remisión).
- `tipo_documento` admite `REMISION_COMPRA`: mueve inventario, no crea CxP; asiento opcional a "mercancía recibida por facturar" (concepto contable parametrizable, bloque contable decide).
- La factura que cruza una remisión **no mueve inventario** por las cantidades cruzadas; sí por ítems nuevos o excedentes.
- `cantidad_recibida` de la OC = Σ de líneas vigentes (vista o recalculo transaccional), nunca escrita desde el front; el endpoint `recibir` pasa a crear la remisión/factura en backend.
- Estados de OC derivados (PARCIAL/CERRADA) y anulación de remisión recalcula pendientes.

### Backend
`CompraServiceImpl` (tipo remisión, cruce), `OrdenCompraServiceImpl.recibir` → crea documento.

### Frontend
"Recibir" abre el form de compra en modo remisión/factura con líneas ligadas; no se marca nada hasta guardar.

### Base de datos
FKs de línea, CHECK de `tipo_documento`, migración de OCs históricas.

### API
`POST /ordenes-compra/{id}/recepciones` (crea remisión o factura); `POST /compras` acepta `ordenCompraDetalleId`/`compraDetalleOrigenId` por línea.

### Seguridad y permisos
`compras.recibir`, `compras.facturar`.

### Inventario/contabilidad
Regla crítica §2.7 garantizada por aplicación de cantidades.

### Auditoría
Relaciones en `documento_relacion` (TASK-B-020).

### Migración de datos
OCs con `cantidad_recibida>0` sin compra → marcar "recibido sin soporte" para revisión.

### Pruebas unitarias
Cálculo de pendiente con remisiones anuladas.

### Pruebas integración
OC 10 → remisión 6 → factura (6 cruzados + 4 nuevos) → stock +10; anular remisión con factura cruzada → rechazo.

### Pruebas E2E
Flujo completo desde la pantalla de órdenes.

### Dependencias
TASK-B-010, TASK-B-020.

### Riesgos
Cambio de hábito del usuario; mantener "factura directa" como camino corto.

### Criterios de aceptación
- [ ] OC CERRADA implica documentos que suman sus cantidades.
- [ ] Legalizar una remisión no incrementa stock por lo ya recibido.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Permisos probados · [ ] Auditoría validada · [ ] Documentación y `/ayuda` actualizados

---

## TASK-B-012 — Gasto: edición y anulación coherentes

**Épica:** B0 Contención · **Módulo:** Gastos · **Tipo:** Backend / Frontend / QA · **Prioridad:** P0 · **Estado:** Proposed

### Problema
Editar cambia importes sin re-postear ni ajustar CxP/dinero; eliminar deja CxP y pagos.

### Evidencia AS-IS
- Archivo: `svc/GastoServiceImpl.java:231-266`; creación `:62-226`.

### Referencia funcional
§2.15; §7.4.

### Decisión
Corregir.

### Diseño propuesto
- `actualizar`: si cambian monto, forma de pago, cuenta, fecha o tributarios → revertir pagos, sincronizar CxP (bloquear con abonos), re-postear (evento de contabilización que reversa el vigente), con `controlFechaRetroactiva`; si no, solo campos descriptivos.
- `eliminar` → `anular(motivo)`: bloquear si CxP con abonos o DS aceptado; revertir pagos/caja/tesorería; anular CxP; reversa asiento.

### Backend
Reutilizar helpers de compra (extraer a un componente común de "pagos de documento").

### Frontend
Diálogo con motivo; mensajes de bloqueo.

### Base de datos
`gasto.anulado_por/anulado_at/motivo_anulacion`; estado `ANULADO` (mantener `ELIMINADO` como alias histórico).

### API
`PATCH /gastos/{id}/anular`.

### Seguridad y permisos
`gastos.editar`, `gastos.anular`.

### Inventario/contabilidad
Documento = asiento tras cualquier cambio.

### Auditoría
Motivo/usuario.

### Migración de datos
Gastos ELIMINADOS con CxP viva o pagos activos → informe.

### Pruebas unitarias
—

### Pruebas integración
Editar monto de gasto contado banco; editar a crédito; anular con abonos (rechazo).

### Pruebas E2E
—

### Dependencias
TASK-B-002 (componente común).

### Riesgos
—

### Criterios de aceptación
- [ ] Gasto = asiento = CxP = movimiento de dinero tras editar/anular.

### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Permisos probados · [ ] Documentación actualizada

### 9.2 P1

## TASK-B-013 — Documento soporte: bloqueo del origen y nota de ajuste

**Épica:** B4 Fiscal · **Módulo:** Compras/Gastos/DS · **Tipo:** Backend / Frontend / Compliance · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Compra/gasto con DS ACEPTADO editables/anulables; no existe nota de ajuste.
### Evidencia AS-IS
- `svc/DocumentoSoporteServiceImpl.java:129-173, 227-235`; `svc/CompraServiceImpl.java:1137, 1220`; `svc/GastoServiceImpl.java:231, 252`; `mig/V180__documento_soporte.sql`.
### Referencia funcional
§2.8. Verificar resolución DIAN vigente (DS y nota de ajuste).
### Decisión
Implementar (guard en B0; nota de ajuste en B4 cuando el DS esté habilitado).
### Diseño propuesto
Guard 409 en editar/anular si existe DS ACEPTADO; nota de ajuste = NC compra (TASK-B-021) + emisión electrónica por outbox (TASK-B-008).
### Backend
`DocumentoSoporteService.tieneAceptado(tipo, id)`; armador de nota de ajuste.
### Frontend
Acción "Nota de ajuste" en el detalle.
### Base de datos
`documento_soporte.tipo` (DS | NOTA_AJUSTE) y `documento_soporte_origen_id`.
### API
`POST /documentos-soporte/{id}/nota-ajuste`.
### Seguridad y permisos
`compras.documento_soporte`.
### Inventario/contabilidad
La nota de ajuste reutiliza efectos de NC compra.
### Auditoría
Payload/respuesta guardados.
### Migración de datos
—
### Pruebas unitarias
Guard.
### Pruebas integración
Anular compra con DS aceptado → 409.
### Pruebas E2E
—
### Dependencias
TASK-B-002, TASK-B-012, TASK-B-021, TASK-B-008.
### Riesgos
DS no habilitado: la nota de ajuste no se puede probar en producción.
### Criterios de aceptación
- [ ] Ningún origen con DS aceptado cambia importes.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Documentación actualizada

---

## TASK-B-014 — Edición de compra segura

**Épica:** B3 · **Módulo:** Compras · **Tipo:** Backend / DB / QA · **Prioridad:** P1 · **Estado:** Proposed

### Problema
La edición borra detalles, ignora NC hijas y período/fecha retroactiva.
### Evidencia AS-IS
`svc/CompraServiceImpl.java:1220-1426` (DELETE `:1302`; sin `controlFechaRetroactiva`; sin chequeo de NC).
### Referencia funcional
§7.4.
### Decisión
Refactorizar.
### Diseño propuesto
Bloquear edición si hay NC vigentes, DS aceptado o período del documento cerrado (consultar `PeriodoContablePort` antes de mutar); pasar por `controlFechaRetroactiva` cuando cambia fecha o pagos; versionar líneas (`compra_detalle.vigente=false`, `reemplazado_por`) en lugar de DELETE, o sustituir la edición por "anular y copiar" con `compra.reemplaza_compra_id`.
### Backend
Validaciones previas + versión de líneas.
### Frontend
Mensajes de bloqueo; opción "Anular y crear copia".
### Base de datos
Columnas de versionado/reemplazo.
### API
`POST /compras/{id}/reemplazar`.
### Seguridad y permisos
`compras.editar`.
### Inventario/contabilidad
Re-posteo nunca queda fallido silenciosamente por período cerrado.
### Auditoría
Historia de versiones.
### Migración de datos
—
### Pruebas unitarias
Validaciones.
### Pruebas integración
Editar compra con NC → 409; con período cerrado → 409 antes de mover inventario.
### Pruebas E2E
—
### Dependencias
TASK-B-025 (borrado lógico), bloque contable (período).
### Riesgos
—
### Criterios de aceptación
- [ ] Ninguna edición deja asiento sin re-postear.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes

---

## TASK-B-015 — Idempotencia de creación y control de factura de proveedor duplicada

**Épica:** B1 · **Módulo:** Ventas/Compras/Cartera · **Tipo:** Backend / DB / Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Doble clic o reintento de red crea documentos duplicados; la misma factura de proveedor puede registrarse dos veces.
### Evidencia AS-IS
grep `idempot|X-Request` sin resultados fuera de contabilidad; sin UNIQUE sobre `numero_compra`.
### Referencia funcional
Checklist transversal (idempotencia).
### Decisión
Implementar.
### Diseño propuesto
Filtro/aspecto `@Idempotente` que lee `Idempotency-Key` (UUID generado por el front al abrir el formulario) y guarda `(empresa_id, clave, endpoint, hash_body, respuesta, created_at)`; misma clave → misma respuesta; clave con body distinto → 422. UNIQUE parcial `compra(empresa_id, proveedor_id, lower(numero_compra)) WHERE tipo_documento='FACTURA_COMPRA' AND estado<>'ANULADA' AND numero_compra IS NOT NULL`.
### Backend
Aspecto + tabla; aplicar a venta, compra, devolución, recibo, abonos CxC/CxP, anticipo, gasto.
### Frontend
Interceptor que añade la clave por formulario.
### Base de datos
`idempotencia` (TTL 48 h con limpieza), índice UNIQUE parcial.
### API
Header documentado.
### Seguridad y permisos
Clave ligada a empresa/usuario.
### Inventario/contabilidad
Evita doble ingreso/descarga.
### Auditoría
—
### Migración de datos
Detectar duplicados de factura de proveedor antes del índice.
### Pruebas unitarias
Aspecto.
### Pruebas integración
Dos POST idénticos concurrentes → 1 documento.
### Pruebas E2E
Doble clic en "Cobrar".
### Dependencias
TASK-B-009.
### Riesgos
—
### Criterios de aceptación
- [ ] Reintentos no duplican documentos.
- [ ] Factura de proveedor repetida → 409.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes

---

## TASK-B-016 — Bloqueo en abonos CxC/CxP y movimientos de turno

**Épica:** B1 · **Módulo:** Cartera/CxP/Caja · **Tipo:** Backend / QA · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Abonos concurrentes pueden exceder el saldo.
### Evidencia AS-IS
`svc/CuentaCobrarServiceImpl.java:172`, `svc/CuentaPagarServiceImpl.java:160`, `svc/TurnoCajaServiceImpl.java:222,265`, `contabilidad/infrastructure/devengo/AnticipoService.java:110,130`; lock existente `repo/cuentas_cobrar/CuentaCobrarJPARepository.java:13-18`.
### Referencia funcional
Checklist concurrencia.
### Decisión
Implementar.
### Diseño propuesto
Usar `bloquear` en todos los caminos de CxC; crear `CuentaPagarJPARepository.bloquear`; validar `monto ≤ saldo` en turno.
### Backend
Cambiar lecturas a métodos con lock.
### Frontend
—
### Base de datos
—
### API
—
### Seguridad y permisos
—
### Inventario/contabilidad
Subledger nunca negativo.
### Auditoría
—
### Migración de datos
Detectar CxC/CxP con `total_abonado > total_deuda`.
### Pruebas unitarias
—
### Pruebas integración
Dos abonos concurrentes por el saldo total → uno falla.
### Pruebas E2E
—
### Dependencias
Ninguna.
### Riesgos
—
### Criterios de aceptación
- [ ] Prueba concurrente verde en CxC, CxP, turno, anticipo.
### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes

---

## TASK-B-017 — Cierre de accesos cruzados entre empresas en caja, devoluciones y ventas

**Épica:** B0 · **Módulo:** Caja/Devoluciones/Ventas · **Tipo:** Backend / QA / Seguridad · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Lecturas por id sin filtro de empresa y uso de turnos ajenos.
### Evidencia AS-IS
`svc/TurnoCajaServiceImpl.java:222, 265`; `svc/DevolucionServiceImpl.java:286`; `svc/VentaServiceImpl.java:209-215`.
### Referencia funcional
Checklist SEGURIDAD (scope empresa/sucursal).
### Decisión
Corregir.
### Diseño propuesto
`findByIdAndEmpresaId` en todos; exigir `turno.usuario = usuario` salvo permiso `caja.operar_turno_ajeno`; test de arquitectura (ArchUnit o grep en CI) que prohíba `findById` en repos multiempresa desde servicios.
### Backend
Cambios puntuales + test.
### Frontend
—
### Base de datos
—
### API
404 en lugar de operar datos ajenos.
### Seguridad y permisos
Complementa `docs/PLAN_SEGURIDAD.md`.
### Inventario/contabilidad
—
### Auditoría
Log de intentos 404 cruzados.
### Migración de datos
—
### Pruebas unitarias
—
### Pruebas integración
Empresa A intenta abonar CxC de empresa B → 404.
### Pruebas E2E
—
### Dependencias
Ninguna.
### Riesgos
—
### Criterios de aceptación
- [ ] 0 `findById` sin empresa en los servicios de este bloque.
### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] Permisos probados

---

## TASK-B-018 — Origen de fondos declarado en reembolsos de devolución

**Épica:** B3 · **Módulo:** Devoluciones/Caja · **Tipo:** Backend / Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
El reembolso deduce turno y cuenta; si no hay turno, el egreso se pierde.
### Evidencia AS-IS
`svc/DevolucionServiceImpl.java:652-719` (`findByUsuarioIdAndEstado` + `ifPresent`, `findFirstByEmpresaIdAndTipoAndActivaIsTrue`), actualización directa de `saldo_actual`.
### Referencia funcional
Regla AURA: `docs/PAGO_OBLIGACIONES_ORIGEN.md`; memoria "gotcha-cuenta-pago-id-no-es-declaracion".
### Decisión
Refactorizar.
### Diseño propuesto
El request declara medio, cuenta bancaria o turno; usar `OrigenFondosService.resolver` + `exigirSaldoDisponible` + `tesoreriaService.registrarMovimientoDeDocumento` + comprobante de egreso.
### Backend
Sustituir `registrarMovimientosDinero`/`revertirMovimientosDinero`.
### Frontend
Selector de origen en el paso de reembolso (mismo componente de compras/CxP).
### Base de datos
—
### API
Campos de origen en `CreateDevolucionDto`.
### Seguridad y permisos
—
### Inventario/contabilidad
Asiento con la cuenta declarada.
### Auditoría
Comprobante de egreso emitido.
### Migración de datos
—
### Pruebas unitarias
—
### Pruebas integración
Reembolso en efectivo sin turno → error explícito, no pérdida.
### Pruebas E2E
—
### Dependencias
TASK-B-004.
### Riesgos
—
### Criterios de aceptación
- [ ] Todo reembolso deja movimiento de caja o banco y comprobante.
### Definition of Done
- [ ] Código integrado · [ ] Pruebas verdes · [ ] Documentación actualizada

---

## TASK-B-019 — Pedido con abonos (anticipo), plan separe y cobro por recibo

**Épica:** B2 · **Módulo:** Pedidos/Cartera/Caja · **Tipo:** Backend / DB / Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Cobro antes de despacho no registra el dinero; cobro posterior sin caja ni origen; pedido espejo cobrable.
### Evidencia AS-IS
`svc/PedidoVendedorServiceImpl.java:257-330`; `svc/VentaServiceImpl.java:670-726`.
### Referencia funcional
§3.2 (pedido con abonos, plan separe).
### Decisión
Implementar.
### Diseño propuesto
Abonos al pedido = anticipo de cliente ligado (`anticipo.pedido_id`) con origen declarado; al despachar/facturar, cruce automático del anticipo contra la CxC; cobro posterior por recibo de caja; espejo creado como `FACTURADO` (terminal) y no cobrable; plan separe = pedido con fecha límite, abonos y opción de reserva de stock (flag por empresa; reserva en tabla `reserva_inventario` a coordinar con bloque inventario); vencimiento → devolución del anticipo o saldo a favor.
### Backend
Servicios de pedido y anticipo.
### Frontend
Abonar desde el pedido; estado de cuenta del pedido.
### Base de datos
`anticipo.pedido_id`, estados de pedido tipados, `pedido.fecha_limite`.
### API
`POST /pedidos/{id}/abonos`.
### Seguridad y permisos
`pedidos.abonar`.
### Inventario/contabilidad
Anticipo (pasivo) → cruce al facturar.
### Auditoría
—
### Migración de datos
Pedidos COBRADOS sin venta → informe.
### Pruebas unitarias
—
### Pruebas integración
Abono 30% → despacho → CxC por 70%; cierre de turno incluye el abono.
### Pruebas E2E
Plan separe completo.
### Dependencias
TASK-B-022, TASK-B-020.
### Riesgos
Decisión de producto sobre reserva de stock.
### Criterios de aceptación
- [ ] Todo peso cobrado en un pedido aparece en caja/banco y en un pasivo o CxC.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] `/ayuda` actualizado

---

## TASK-B-020 — Grafo documental, conversión de cotización y control de cruces

**Épica:** B2 · **Módulo:** Transversal · **Tipo:** Backend / DB / Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
No hay relación documento→documento con cantidades; la cotización nunca pasa a CONVERTIDA.
### Evidencia AS-IS
`svc/CotizacionServiceImpl.java:252-265, 298-308`; `dto/ventas/CreateVentaDto.java` (sin `cotizacionId`); relaciones ad-hoc en `compra.compra_origen_id`, `devolucion.venta_id`, `pedido_vendedor.venta_id`.
### Referencia funcional
§2.12, §4.22, §7.3.
### Decisión
Implementar.
### Diseño propuesto
`documento_relacion` (ver GAP-B-021) con escritura desde cada servicio en la misma transacción; `CreateVentaDto.cotizacionId` y líneas con `cotizacionDetalleId`; cotización → CONVERTIDA/PARCIAL; componente "Documentos relacionados" en detalles de venta, compra, OC, cotización, pedido; reporte de control de cruces (pendientes por documento). `vencerCotizacionesExpiradas` con query por estado/fecha.
### Backend
`DocumentoRelacionService` + consultas.
### Frontend
Panel reutilizable (estándar UI: una `.card` con secciones).
### Base de datos
Tabla + índices por origen y destino; backfill desde FKs existentes.
### API
`GET /documentos/{tipo}/{id}/relacionados`; `GET /reportes/cruces`.
### Seguridad y permisos
Lectura según permiso del documento.
### Inventario/contabilidad
Base para impedir doble afectación.
### Auditoría
Relación inmutable (anulación lógica).
### Migración de datos
Backfill NC compra→compra, devolución→venta, pedido→venta.
### Pruebas unitarias
Cálculo de pendientes.
### Pruebas integración
Convertir cotización dos veces → segunda rechazada.
### Pruebas E2E
Navegar la cadena desde una factura.
### Dependencias
TASK-B-009.
### Riesgos
—
### Criterios de aceptación
- [ ] Toda conversión/cruce nuevo queda en `documento_relacion` con cantidades.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Documentación actualizada

---

## TASK-B-021 — NC de compra por valor y ND de compra

**Épica:** B3 · **Módulo:** Compras/CxP · **Tipo:** Backend / DB / Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Solo existe NC por devolución de cantidades; no hay descuento posterior ni ND.
### Evidencia AS-IS
`svc/CompraServiceImpl.java:153-234, 527-535`.
### Referencia funcional
§2.10, §2.11.
### Decisión
Implementar.
### Diseño propuesto
`tipo_documento` `NOTA_CREDITO_VALOR` (línea con valor y producto/concepto, cantidad 0, sin inventario, tope = valor pendiente de acreditar de la factura) y `NOTA_DEBITO_COMPRA` (aumenta CxP, sin inventario; nuevas unidades exigen nueva compra); prorrateo de IVA descontable y retenciones; destinos iguales a la NC actual. Decidir con el contador si el descuento posterior ajusta el costo del inventario remanente (bloque inventario/costeo).
### Backend
Extensión de validaciones y generador contable.
### Frontend
Selector de tipo de nota.
### Base de datos
CHECK de tipos.
### API
Mismo `POST /compras` con tipo.
### Seguridad y permisos
`compras.nota_credito`, `compras.nota_debito`.
### Inventario/contabilidad
Sin movimiento de stock; asiento por valor.
### Auditoría
Relación con la factura (TASK-B-020).
### Migración de datos
—
### Pruebas unitarias
Topes.
### Pruebas integración
NC valor con CRUCE_CXP; ND con CxP aumentada.
### Pruebas E2E
—
### Dependencias
TASK-B-020.
### Riesgos
Costeo del descuento posterior.
### Criterios de aceptación
- [ ] Matriz §4.1 sin ❌ en NC descuento y ND.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes (golden de asientos)

---

## TASK-B-022 — Anticipos completos (origen, comprobante, anulación, saldo a favor de NC)

**Épica:** B2 · **Módulo:** Cartera/CxP/Tesorería · **Tipo:** Backend / DB / Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Anticipo sin caja/tesorería, sin anulación, sin lock; saldo a favor de NC compra invisible.
### Evidencia AS-IS
`contabilidad/infrastructure/devengo/AnticipoService.java:47-163`; `svc/CompraServiceImpl.java:872-873`.
### Referencia funcional
§2.13.
### Decisión
Implementar.
### Diseño propuesto
Crear con `OrigenFondosService` (movimiento de caja o banco + RC/CE); `anular` bloqueado si tiene cruces vigentes; `anularCruce`; locks; NC compra SALDO_A_FAVOR crea anticipo PROVEEDOR con `origen_compra_id`; ofrecer anticipos disponibles al registrar compra/venta a crédito.
### Backend
Servicio ampliado; mover a paquete de cartera/tesorería si aplica.
### Frontend
Pantalla de anticipos por tercero, cruce desde factura.
### Base de datos
`anticipo.origen_tipo/origen_id`, `anticipo_cruce.anulado*`.
### API
`PATCH /anticipos/{id}/anular`, `PATCH /anticipos/cruces/{id}/anular`.
### Seguridad y permisos
`anticipos.*`.
### Inventario/contabilidad
Asientos ANTICIPO/ANTICIPO_CRUCE existentes + reversas.
### Auditoría
—
### Migración de datos
NC compra SALDO_A_FAVOR históricas → anticipos con saldo (validar contra mayor).
### Pruebas unitarias
—
### Pruebas integración
Anticipo efectivo aparece en arqueo; cruce parcial; anulación de cruce.
### Pruebas E2E
—
### Dependencias
TASK-B-016.
### Riesgos
—
### Criterios de aceptación
- [ ] Todo anticipo tiene movimiento de dinero y comprobante; saldo por tercero = cuenta contable de anticipos.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes

---

## TASK-B-023 — Precio/impuesto validados en backend y límites de descuento

**Épica:** B5 · **Módulo:** POS/Ventas/Compras · **Tipo:** Backend / Frontend / DB · **Prioridad:** P1 · **Estado:** Proposed

### Problema
El backend confía en precio, descuento e impuesto del cliente; descuentos sin tope.
### Evidencia AS-IS
`svc/VentaServiceImpl.java:402-407`; `svc/CompraServiceImpl.java:528`; `front/features/pos/pos.component.ts:1339-1340`; reglas en `svc/ReglaDescuentoServiceImpl.java` sin uso en venta.
### Referencia funcional
§3.3.
### Decisión
Implementar respetando que **cambio de precio ≠ descuento** (no se restringe ni se convierte el cambio de precio).
### Diseño propuesto
Recalcular impuesto por línea desde la configuración del producto (tabla de impuestos V90) y rechazar diferencias > tolerancia; `descuento_maximo_pct` por rol y por usuario (override) con autorización registrada (`venta_detalle.autorizado_por`); guardar `precio_lista` en la línea para auditar cambios de precio; aplicar `ReglaDescuento` automáticas en backend y devolverlas al front como sugerencia.
### Backend
`PoliticaComercialService`.
### Frontend
Mensaje de tope y flujo de autorización (PIN supervisor).
### Base de datos
Columnas en rol/usuario y venta_detalle.
### API
Error 422 con detalle por línea.
### Seguridad y permisos
Permiso `ventas.descuento_sobre_limite`.
### Inventario/contabilidad
IVA contable = IVA calculado.
### Auditoría
Descuentos autorizados trazables.
### Migración de datos
—
### Pruebas unitarias
Cálculo de impuesto y topes.
### Pruebas integración
Venta con impuesto manipulado → 422.
### Pruebas E2E
Descuento sobre tope con autorización.
### Dependencias
Plan de seguridad (roles).
### Riesgos
Rechazos por redondeo → tolerancia configurable.
### Criterios de aceptación
- [ ] Ninguna venta persiste con impuesto distinto al calculado.
- [ ] Descuentos sobre tope solo con autorización registrada.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Permisos probados

---

## TASK-B-024 — Estados tipados en documentos del bloque

**Épica:** B1 · **Módulo:** Transversal · **Tipo:** Backend / DB · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Estados como literales libres y con mayúsculas mezcladas.
### Evidencia AS-IS
Ver GAP-B-026.
### Referencia funcional
§5 paso 5 (estados libres como strings).
### Decisión
Refactorizar.
### Diseño propuesto
Enums Java (`EstadoVenta`, `EstadoCompra`, `EstadoCuenta`, `EstadoTurno`, `EstadoOrdenCompra`, `EstadoPedido`, `EstadoCotizacion`) con `@Enumerated(STRING)` o converter que tolere legado; `CHECK` en BD; normalizar `activa`→`pendiente`/`parcial`, minúsculas→mayúsculas donde la UI lo permita (coordinar reportes SQL que comparan literales).
### Backend
Reemplazo de literales.
### Frontend
Mapas de etiquetas actualizados.
### Base de datos
UPDATE de normalización + CHECK (+ espejo Laravel).
### API
Mismos valores serializados.
### Seguridad y permisos
—
### Inventario/contabilidad
—
### Auditoría
—
### Migración de datos
Normalización previa al CHECK.
### Pruebas unitarias
Converters.
### Pruebas integración
Reportes de cartera y cierre de turno siguen cuadrando.
### Pruebas E2E
—
### Dependencias
Ninguna.
### Riesgos
Queries nativas con literales (memoria "gotcha-rowmapper-manual").
### Criterios de aceptación
- [ ] CHECK activo en las tablas del bloque.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes

---

## TASK-B-025 — Anulación lógica en lugar de borrado físico

**Épica:** B1 · **Módulo:** Cartera/CxP/Compras/Notas · **Tipo:** Backend / DB · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Abonos, cruces, detalles y notas se borran físicamente.
### Evidencia AS-IS
`svc/CuentaCobrarServiceImpl.java:341, 418`; `svc/CuentaPagarServiceImpl.java:343, 418`; `svc/CompraServiceImpl.java:1302`; `svc/FactusNotaService.java:311`.
### Referencia funcional
§7.4.
### Decisión
Refactorizar.
### Diseño propuesto
Columnas `anulado`, `anulado_por`, `anulado_at`, `motivo_anulacion` en abonos y líneas económicas; todas las sumas filtran vigentes; `eliminarAbono` → `anularAbono`; notas electrónicas eliminadas en Factus quedan `ELIMINADA` localmente.
### Backend
Reemplazar `delete*`; ajustar queries de saldo (incluidas las JDBC de cierre de turno y cartera).
### Frontend
Mostrar abonos anulados tachados.
### Base de datos
Columnas + índices parciales.
### API
`PATCH .../abonos/{id}/anular`.
### Seguridad y permisos
`cartera.anular_abono`.
### Inventario/contabilidad
Reversa de asiento ya existe.
### Auditoría
Historia completa.
### Migración de datos
—
### Pruebas unitarias
—
### Pruebas integración
Saldos y cierre de turno excluyen anulados.
### Pruebas E2E
—
### Dependencias
TASK-B-024.
### Riesgos
Queries que olviden el filtro → revisión sistemática.
### Criterios de aceptación
- [ ] 0 DELETE sobre tablas económicas del bloque.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes

---

## TASK-B-026 — Cola de ventas pendientes de FE y política de emisión

**Épica:** B4 · **Módulo:** FE · **Tipo:** Backend / Frontend / Compliance · **Prioridad:** P1 · **Estado:** Proposed

### Problema
La FE depende de que alguien la pida; no hay cola ni envío masivo; POS sin documento electrónico.
### Evidencia AS-IS
`svc/VentaServiceImpl.java:654-656`; `controllers/VentaController.java:89`.
### Referencia funcional
§3.14. **Antes de implementar, verificar en fuentes oficiales DIAN vigentes** la obligación de documento equivalente electrónico POS y plazos de transmisión.
### Decisión
Implementar tras verificación normativa.
### Diseño propuesto
`empresa.politica_fe` (`EMITIR_AL_VENDER | BAJO_DEMANDA`); al vender con EMITIR_AL_VENDER se encola en el outbox (TASK-B-008) tras el commit; pantalla "Pendientes de transmitir" con envío individual/masivo y alertas en la campana para ventas sin transmitir > N horas.
### Backend
Encolado AFTER_COMMIT; consulta de pendientes.
### Frontend
Pantalla de pendientes (estándar UI de módulo).
### Base de datos
Campo de política.
### API
`GET /fe/pendientes`, `POST /fe/enviar-lote`.
### Seguridad y permisos
`fe.enviar`.
### Inventario/contabilidad
—
### Auditoría
Historial de envíos.
### Migración de datos
—
### Pruebas unitarias
—
### Pruebas integración
Venta con política automática → encolada → emitida.
### Pruebas E2E
—
### Dependencias
TASK-B-007, TASK-B-008.
### Riesgos
Costo por documento en Factus.
### Criterios de aceptación
- [ ] Ninguna venta de empresa con política automática queda sin intento de emisión.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Validación normativa documentada

---

## TASK-B-027 — Retenciones en compras parametrizadas

**Épica:** B5 · **Módulo:** Compras/Impuestos · **Tipo:** Backend / Frontend · **Prioridad:** P1 · **Estado:** Proposed

### Problema
Retenciones por porcentaje manual sobre toda la base, sin concepto ni base mínima.
### Evidencia AS-IS
`svc/CompraServiceImpl.java:616-631, 1384-1403`; `svc/TarifaRetencionServiceImpl.java`; `mig/V57__tabla_retenciones.sql`.
### Referencia funcional
§1.12, §2.7 (retenciones según plantilla/tercero).
### Decisión
Implementar.
### Diseño propuesto
Sugerencia automática: concepto de retención (por producto/categoría contable o por documento) × calidad tributaria del proveedor y de la empresa × base mínima en UVT del año (parametrizable, verificar valores vigentes); el usuario puede ajustar con permiso; persistir concepto y base por retención (tabla `compra_retencion`) para alimentar el borrador 350.
### Backend
`CalculadoraRetenciones` compartida con gastos.
### Frontend
Retenciones sugeridas editables.
### Base de datos
`compra_retencion(compra_id, tipo, concepto_id, base, tarifa, valor)`.
### API
`GET /compras/retenciones-sugeridas`.
### Seguridad y permisos
`compras.editar_retencion`.
### Inventario/contabilidad
Cuentas por concepto (bloque contable).
### Auditoría
—
### Migración de datos
Compras históricas: una fila por tipo con concepto "sin clasificar".
### Pruebas unitarias
Bases mínimas, no responsable de IVA, autorretenedor.
### Pruebas integración
Borrador 350 coincide con Σ `compra_retencion`.
### Pruebas E2E
—
### Dependencias
Bloque contable (conceptos); V181.
### Riesgos
Tablas normativas anuales → responsable de actualización.
### Criterios de aceptación
- [ ] Retenciones sugeridas correctas en los casos de prueba validados por contador.
### Definition of Done
- [ ] Código integrado · [ ] Migraciones verificadas · [ ] Pruebas verdes · [ ] Revisión contador

### 9.3 P2 / P3 (tabla)

| ID | Gap | Prioridad | Épica | Resumen de trabajo | Dependencias |
|---|---|---|---|---|---|
| TASK-B-028 | GAP-B-030 | P2 | B4 | Carga de XML/ZIP de proveedores, precarga de compra, eventos RADIAN vía proveedor tecnológico; verificar obligatoriedad vigente | TASK-B-008, TASK-B-011 |
| TASK-B-029 | GAP-B-031 | P2 | B5 | Cierre solo por dueño o permiso, conteo por denominaciones, cierre ciego opcional, normalizar `metodoPago` | TASK-B-017 |
| TASK-B-030 | GAP-B-032 | P2 | B6 | Remisión de venta y devolución de remisión con legalización (reusa grafo) solo si hay demanda de distribución | TASK-B-020 |
| TASK-B-031 | GAP-B-033 | P2 | B6 | Reportes de compras (proveedor, producto, centro de costo, forma de pago, comparativo) Excel/PDF + asíncrono | TASK-B-020 |
| TASK-B-032 | GAP-B-034 | P2 | B6 | Importar cotizaciones/pedidos/compras con validar/confirmar; exportar encabezado+detalle | TASK-B-015 |
| TASK-B-033 | GAP-B-035 | P2 | B6 | Alta rápida de tercero y producto dentro del autocomplete estándar | — |
| TASK-B-034 | GAP-B-036 | P2 | B6 | Envío de PDF por email (servicio propio) y WhatsApp (link → API) para cotización, OC, venta, recibo | — |
| TASK-B-035 | GAP-B-037 | P2 | B5 | `venta_impuesto` por tarifa; eliminar columnas fijas 5/19 en nuevos cálculos | TASK-B-023 |
| TASK-B-036 | GAP-B-038 | P2 | B6 | Apagar `FacturaRetryScheduler` por propiedad, retirar flujo `factura` legacy tras verificar que no hay pendientes reales | — |
| TASK-B-037 | GAP-B-039 | P2 | B6 | Endpoint de simulación de asiento (generador sin persistir) en compra/venta/gasto | Bloque contable |
| TASK-B-038 | GAP-B-040 | P3 | B7 | Replicar documento (compra/gasto) a nueva fecha; plantillas recurrentes | TASK-B-015 |
| TASK-B-039 | GAP-B-041 | P3 | B7 | Comisiones por recaudo, rangos, metas y forma de pago | TASK-B-003 |
| TASK-B-040 | GAP-B-042 | P3 | B7 | Monedas, TRM, documento en moneda extranjera, diferencia en cambio | Bloque contable |
| TASK-B-041 | GAP-B-043 | P3 | B7 | Factura de exportación | TASK-B-040 |
| TASK-B-042 | GAP-B-044 | P3 | B7 | Ingresos para terceros (beneficiario, % o valor, cuenta) | TASK-B-007 |
| TASK-B-043 | GAP-B-045 | P3 | B7 | Campos XML personalizados (orden de compra, despacho) mapeados al payload | TASK-B-007 |
| TASK-B-044 | GAP-B-046 | P3 | B7 | Factura de servicio sectorial / AIU ligada a proyectos/frentes | TASK-B-007 |

---

## 10. Dependencias externas al bloque

- **Bloque inventario:** TASK-B-010 (servicio de stock con lock), costeo (promedio vs último costo), reservas para plan separe.
- **Bloque contable:** conceptos para "mercancía recibida por facturar", unificación de generadores NOTA_CREDITO/DEVOLUCION, período cerrado consultable antes de mutar, simulación de asientos.
- **Seguridad (`docs/PLAN_SEGURIDAD.md`):** permisos por acción (`*.anular`, descuentos, FE).
- **Esquema:** toda migración nueva requiere espejo idempotente en el proyecto Laravel (Flyway apagado, `ddl-auto=validate`; ojo con `ADD COLUMN IF NOT EXISTS` y tipos distintos).
- **Normativa DIAN vigente:** FE (forma/medio de pago, descuentos), documento equivalente POS, DS y nota de ajuste, RADIAN, UVT/bases de retención. Nada de esto se da por vigente desde los videos.
