# Plan: cadena documental (trazabilidad de documentos)

> Estado: **D0 y D1 implementados full-stack 2026-10-01 (local, sin commit; V189 sin correr).** D2–D7 diseñadas, sin implementar.
> Origen: fase F4 del roadmap de la auditoría (`docs/audit-erp/02-roadmap.md`),
> tareas B-020, B-011, B-004, B-006/C-006, B-021, B-022, B-019, B-014, B-018.
> Detalle de cada tarea: `docs/audit-erp/parts/B-compras-ventas-caja.md`.

## Objetivo

Que cada documento sepa de dónde viene y a dónde fue, con **cantidades y valores
aplicados por línea**, sin que el inventario, la cartera (CxC) o las cuentas por
pagar (CxP) se afecten dos veces:

```
Ventas:  Cotización → (Pedido) → Venta/Factura → Devolución / Nota crédito / Nota débito → Recibo
Compras: Orden de compra → Recepción (remisión) → Factura de compra → NC / ND de compra → Egreso
```

## Estado actual (verificado en el código el 2026-09-30)

| Punto | Hoy |
|---|---|
| Relación entre documentos | No existe `documento_relacion`; solo campos sueltos (`compra.compra_origen_id`, `devolucion.venta_id`, `pedido.venta_id`). Ninguno guarda cantidad aplicada. |
| Cotización → venta | `CotizacionServiceImpl.convertirAVenta` no cambia el estado; nada asigna CONVERTIDA; `CreateVentaDto` no tiene `cotizacionId`. |
| Orden de compra | `OrdenCompraServiceImpl.recibirMercancia` suma `cantidad_recibida` a mano y cierra la OC aunque la compra nunca se guarde; `compra` no tiene `orden_compra_id`. |
| Devolución | `DevolucionServiceImpl` hace UPDATE a `venta_detalle.cantidad` y agrega líneas del cambio a la venta original; busca la línea por producto (`findFirst`), no por línea. |
| Reembolso de devolución | Deduce el origen del dinero (primera cuenta CAJA/BANCO), contrario a la regla "el origen se declara". |
| NC/ND electrónicas de venta | `nota_electronica` no tiene `venta_id` ni `devolucion_id`; no tocan `cuenta_cobrar` ni inventario. Devolución + NC sobre lo mismo = doble reversa. |
| Compras | Solo NC por cantidades; no hay NC por valor ni ND de compra. |
| Pedido | Puede quedar COBRADA sin venta (dinero sin caja ni asiento). |
| Anticipos | **Sí existen** (`AnticipoEntity`, `AnticipoCruceEntity`): se reutilizan para los abonos del pedido. |

## Fases

Cada fase se puede subir sola. Migraciones desde **V189** (la última es V188, recargo de forma de pago), cada una con su espejo en Laravel (idempotente, un `DB::statement` por sentencia).

### D0 · Base: relaciones entre documentos (prerrequisito)
- Tabla `documento_relacion(empresa_id, origen_tipo, origen_id, origen_linea_id, destino_tipo, destino_id, destino_linea_id, cantidad, valor, estado VIGENTE/ANULADA, created_by, created_at)` + índices por origen y por destino.
- `DocumentoRelacionService`: `registrar(...)` dentro de la misma transacción del documento, `aplicado(origen línea)`, `pendiente(origen línea) = cantidad origen − Σ aplicado vigente`, `anularPorDestino(tipo, id)` al anular el destino.
- Consultas en un `DocumentoRelacionQueryRepository` (regla: JPA solo findById/save/delete).
- Endpoint `GET /api/documentos/{tipo}/{id}/relacionados` (hacia atrás y hacia adelante).
- Front: componente `<app-documentos-relacionados tipo id>` (lista con enlace a cada documento), puesto en el detalle de venta, compra, OC, cotización, pedido y devolución.
- **Prueba:** registrar y anular relaciones; pendiente correcto con aplicaciones parciales y anuladas.

### D1 · Cotización → venta
- `CreateVentaDto.cotizacionId` y en cada línea `cotizacionDetalleId`; el POS/venta los manda al venir de "Convertir a venta".
- Al crear la venta: relación por línea; cotización → **CONVERTIDA** (todo tomado) o **PARCIAL**; no se puede convertir más de lo pendiente.
- Anular la venta devuelve la cotización a VIGENTE/PARCIAL.
- `vencerCotizacionesExpiradas`: query por estado y fecha (hoy hace `findAll()` global).
- **Prueba:** cotización 10 → venta 6 → PARCIAL (pendiente 4) → venta 4 → CONVERTIDA → anular la segunda → PARCIAL.

### D2 · Orden de compra → recepción → factura (sin doble ingreso)
- `compra_detalle.orden_compra_detalle_id`; `compra.orden_compra_id`.
- La recepción es una **compra con `tipo_documento = 'REMISION_COMPRA'`**: mueve inventario, no crea CxP (asiento a "mercancía por legalizar" o sin asiento: **decisión pendiente, ver abajo**).
- La cantidad recibida de la OC **se deriva** de Σ líneas vigentes; se quita la escritura manual de `recibirMercancia` (el botón "Recibir" abre la compra y solo al guardarla cuenta).
- La factura que legaliza una remisión guarda `compra_detalle_origen_id` y **solo mueve inventario por lo no remisionado**.
- Migración: OCs históricas con recibido manual quedan marcadas "recibido sin soporte".
- **Prueba:** OC 10 → remisión 6 → factura 6 + 4 nuevos → stock +10 exacto; anular la remisión recalcula el pendiente.

### D3 · Devolución inmutable y cambio como venta nueva
- `devolucion_detalle.venta_detalle_id` (por línea, no por producto).
- Disponible para devolver = cantidad vendida − Σ devuelto vigente, con bloqueo sobre la venta.
- **La venta original no se toca**; los netos salen de una vista `v_venta_neto`.
- El cambio crea una **venta nueva** (`venta.devolucion_origen_id`); el saldo a favor entra como medio de pago `SALDO_DEVOLUCION`.
- Reembolso con **origen declarado** (`OrigenFondosService.resolver` + `exigirSaldoDisponible`), como en caja/fondos (B-018).
- Migración de datos: reconstruir `venta_detalle.cantidad` original sumando devoluciones vigentes; informe antes/después en local.
- Revisar reportes que asumían la venta neta (ventas, márgenes, gerencial, comisiones).
- **Prueba:** dos líneas del mismo producto; devolución en 2 pasos; anular la primera; cambio con saldo a favor y en contra.

### D4 · Nota de venta unificada + NC/ND electrónica
- Un solo documento "nota de venta" con tipo `DEVOLUCION | DESCUENTO | ANULACION | DEBITO`, ligado a `venta_id` y a líneas.
- Efectos: inventario solo en DEVOLUCION; CxC siempre (NC resta, ND suma); **un solo asiento por nota**.
- Si la venta tiene CUFE, la nota se emite a Factus desde el mismo documento; si no, queda interna.
- `nota_electronica.venta_id` / `devolucion_id`; vincular las notas viejas por `factus_numero`/`bill_id`.
- Rechazar devolución + NC sobre las mismas unidades.
- **Prueba:** NC devolución (stock +, CxC −, un asiento); NC descuento (CxC −); ND (CxC +); doble reversa rechazada. Criterio: Σ aplicaciones CxC = saldo de Clientes por tercero.

### D5 · Compras: NC por valor y ND de compra
- Tipos `NOTA_CREDITO_VALOR` y `NOTA_DEBITO` en compra: sin inventario, prorrateo de IVA y retenciones, efecto en CxP, relación con la factura (D0).
- **Prueba:** descuento posterior del proveedor baja la CxP sin tocar stock; ND por fletes sube la CxP.

### D6 · Pedido con abonos y plan separe
- Abono al pedido = **anticipo de cliente** (reusar `AnticipoEntity`/cruce) con origen declarado y turno.
- Al facturar, el anticipo se cruza; lo que falte se cobra en la venta o por recibo de caja.
- Ya no se puede marcar COBRADA sin venta; el pedido espejo que crea el POS nace en estado terminal.
- Reserva de stock para plan separe: **decisión pendiente**.

### D7 · Control de cruces y edición de compra segura
- Reporte "Pendientes": cotizaciones, pedidos, OC y remisiones con saldo por aplicar (desde `documento_relacion`).
- Edición de compra (B-014): bloquear si tiene documentos aplicados, pagos o DS aceptado; si no, rehacer efectos completos.

## Decisiones que el usuario debe tomar antes de la fase respectiva
1. **D2:** la remisión de compra, ¿contabiliza a una cuenta puente "mercancía por legalizar" (p. ej. 2335xx / 1435xx) o no genera asiento hasta la factura?
2. **D3:** ¿el cambio de producto siempre como venta nueva (recomendado) aunque la venta original no tenga factura electrónica?
3. **D6:** ¿el plan separe reserva stock (no se puede vender a otro) o solo guarda el abono?

## Orden recomendado
D0 → D1 (rápida, se ve el valor) → D2 → D3 → D4 → D5 → D6 → D7.

## Cómo retomar
En una sesión nueva: "sigamos con la cadena documental". Revisar este archivo, la sección "Estado por fase" y la memoria `plan-cadena-documental`.

## Estado por fase
| Fase | Estado | Migración | Notas |
|---|---|---|---|
| D0 | **Hecho full-stack** (local, V189 sin correr) | V189 | Back: tabla `documento_relacion`, `DocumentoRelacionService` (registrar/aplicado/pendiente/anularPorDestino), QueryRepository, `GET /api/documentos/{tipo}/{id}/relacionados`, espejo Laravel. Front: `<app-documentos-relacionados tipo id>` + `DocumentoRelacionService`, integrado en el detalle de cotización. **Pendiente: cablear `registrar(...)` en los flujos reales y poner el panel en venta/compra/OC/pedido/devolución.** |
| D1 | **Hecho full-stack** (local) | — (usa V189) | `CotizacionConversionService` (bloqueo FOR UPDATE, relación por línea, PENDIENTE/PARCIAL/CONVERTIDA, liberar al anular la venta); `CreateVentaDto.cotizacionId` + `cotizacionDetalleId` por línea; convertir carga solo lo pendiente (descuento prorrateado); vencimiento por query (también vence PARCIAL); reactivar recalcula. **Decisión:** vender más de lo pendiente NO bloquea: la cotización se consume hasta su pendiente y el excedente es venta normal (el POS fusiona el mismo producto en una línea). Front: origen en cada línea del carrito, estado Parcial, "Vender lo pendiente", avance por línea, panel en detalle de venta, manual /ayuda. |
| D2 | Pendiente | | Decisión 1 |
| D3 | Pendiente | | Decisión 2 |
| D4 | Pendiente | | |
| D5 | Pendiente | | |
| D6 | Pendiente | | Decisión 3 |
| D7 | Pendiente | | |
