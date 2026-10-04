# Plan: módulo de Facturación (factura de venta tipo ERP, fuera del POS)

> Estado: **FV0+FV1 hechos local 2026-10-03 (V195 sin correr)**; FV2–FV5 pendientes. Fase **FV** del roadmap ([audit-erp/02-roadmap.md](audit-erp/02-roadmap.md)).
> Arranca después de subir los lotes de productos y permisos.

## Problema (verificado en el código el 2026-10-02)

En una empresa mediana la factura no se hace en el punto de venta: la hace facturación/cartera,
muchas veces a crédito, con servicios, retenciones y orden de compra del cliente. Hoy en AURA:

- La única forma de crear una factura es `POST /api/ventas/create`, con DTO de POS:
  `turnoCajaId` (o `sucursalId` si no hay caja), `tipoDocumento = "POS"` por defecto y pagos en el momento.
- Cotización y pedido de vendedor se convierten **cargándolos al carrito del POS** (D1).
- La FE se emite aparte, sobre una venta ya hecha: `POST /api/ventas/{id}/factura-electronica`.
- Toda línea exige `productoId`: no hay línea de servicio con descripción libre.
- `FacturaController` (`/api/facturas`) es el flujo viejo `factura` (ver gotcha de la tabla factura vieja); no crea facturas.

## Principio de diseño

**Pantalla y servicio propios, misma tabla `venta`.** La factura de Facturación se guarda en `venta`/`venta_detalle`
con origen `FACTURACION`, para reutilizar stock, kardex, costo promedio, asiento, cartera, Factus, reportes de FE
y anulación. Una tabla aparte duplicaría todo eso (ya pasó con la tabla `factura`).
El POS queda igual para el mostrador.

## Fases

### FV0 · Modelo (siguiente migración libre, V194+, + espejo Laravel idempotente)
- `venta`: `origen` (`POS`|`FACTURACION`), `estado` con `BORRADOR` (solo origen FACTURACION), `condicion_pago_id`,
  `vendedor_id`, `centro_costo_id`, `orden_compra_cliente`, `fecha_emision` controlada, `notas_factura`.
- `venta_detalle`: `descripcion` editable y `es_servicio` (no mueve inventario).
- Tabla `condicion_pago` por empresa (nombre, días): Contado, 30, 60, 90; el vencimiento se calcula.
- Antes de decidir si se reutiliza `venta.tipo_documento`, consultar en prod qué valores tiene hoy.

### FV1 · Factura en borrador y emisión (sin caja)
- `FacturaVentaService` (nuevo, no toca `VentaService` del POS): crear/editar borrador, emitir, anular.
- Borrador: no consume consecutivo, ni stock, ni asiento. Se puede editar y borrar.
- Emitir: consecutivo de la resolución de Facturación + stock (bodega elegida) + asiento + CxC, en una transacción,
  usando los servicios únicos de F2 (numerador y movimiento de stock).
- Contado sin turno de caja: el cobro entra por forma de pago con cuenta (no por turno).
  Crédito: queda en cartera con el vencimiento de la condición de pago.
- Front: menú **Ventas › Facturas** (submódulo `ventas.facturas`, primero del grupo Ventas, junto a
  Cotizaciones, Notas Crédito/Débito y Devoluciones; permiso propio, independiente de `principal.punto-de-venta`)
  (listado `.page > .card`, formulario plano, detalle en una card),
  `<app-tercero-autocomplete>`, `p-calendar`; SQL de menú y tema en `/ayuda`.

### FV2 · Factura electrónica al emitir
- Emitir = enviar a Factus en el mismo flujo, con estado reservado antes de enviar (B-008 paso 1, ya hecho en F1)
  y reintento desde la cola de pendientes (B-026).
- Envío de PDF + XML al correo del cliente.

### FV3 · Retenciones en la factura
- Retefuente, reteIVA y reteICA que practica el cliente, calculadas por base mínima y tarifa
  (requiere C-011/B-027 de F6). Asiento a 1355xx y cartera por el neto.
- Convive con la retención al recaudar (punto 4): la factura con retención ya no la pide en el recibo.

### FV4 · Facturar desde otros documentos
- Cotización → factura (reusar `CotizacionConversionService`, pendiente por línea).
- Pedido / remisión → factura parcial con `documento_relacion` (D0).
- Aplicar anticipos del cliente (B-022).

### FV5 · Notas y extras
- NC/ND desde la factura (se cruza con D4: nota de venta unificada + NC/ND electrónica).
- Facturas recurrentes (servicios mensuales), AIU (construcción), copiar factura.

## Decisiones que el usuario debe tomar
1. **Líneas de servicio:** ¿línea sin producto con descripción libre, o producto genérico "servicio" con descripción editable?
   (Recomendado: producto de servicio + descripción editable: conserva cuenta contable e impuesto por producto.)
2. **Resolución DIAN:** ¿Facturación usa una resolución/prefijo propio, separado del POS? Revisar si el POS
   debe emitir documento equivalente electrónico POS y Facturación factura electrónica de venta (norma vigente).
3. **Contado sin caja:** ¿a qué cuenta entra el pago (banco/caja general elegida en la factura)?
4. **Inventario:** ¿la factura de bienes descuenta stock al emitir, o siempre viene de una remisión que ya lo descontó?

## Dependencias
- Necesita F2 (numerador y stock únicos) — hecho local, sube con los lotes.
- FV3 necesita la parametrización de retenciones de F6 (C-011, B-027): adelantarla.
- Resoluciones por sucursal/documento (A-014 de F7): adelantarla para la decisión 2.

## Estado por fase
| Fase | Estado | Migración | Notas |
|---|---|---|---|
| FV0 | Hecho local 2026-10-03 | V195 | condicion_pago, factura_venta(+detalle), venta_detalle.descripcion, submódulo ventas.facturas |
| FV1 | Hecho local 2026-10-03 | | Ver "Implementación FV1" |
| FV2 | Pendiente | | Decisión 2 |
| FV3 | Pendiente | | Depende de C-011/B-027 |
| FV4 | Pendiente | | |
| FV5 | Pendiente | | Cruza con D4 |

## Implementación FV1 (2026-10-03)

Decisiones del usuario: (1) servicio = producto tipo SERVICIO con descripción editable por línea;
(3) contado sin caja = banco elegido **o efectivo a la caja general** (sin turno ni arqueo);
(4) la factura descuenta stock al emitir.

- El borrador vive en `factura_venta` / `factura_venta_detalle` (no en `venta`): así ningún reporte que
  lea `venta` ve borradores. Emitir arma un `CreateVentaDto` y llama al **mismo `VentaService.crear`**
  del POS con `desdeFacturacion = true` (campo `@JsonIgnore`, solo lo pone el servidor): sin turno aunque
  haya efectivo, sin límites de descuento del cajero, sin exigir la acción "vender a crédito" del POS y
  sin pedido de vendedor espejo. Stock, lotes, seriales, kardex, cartera, tesorería y asiento salen de ahí.
  `venta.tipo_documento = 'FACTURA'` marca el origen.
- Numeración: el mismo consecutivo de la sede que el POS (resolución propia = decisión 2, FV2).
- FE: `POST /api/facturas-venta/{id}/factura-electronica` reusa `VentaFacturaService` con el permiso de
  Facturación; la línea viaja a Factus con su descripción.
- API `/api/facturas-venta` (permiso `ventas.facturas`), front `/ventas/facturas` (listado, formulario
  plano, ficha en una card), tema en `/ayuda`.
- Pendiente dentro de FV1: elegir presentación en la línea (hoy unidad base), vendedor en el formulario
  (el back ya lo recibe), pantalla de condiciones de pago (el API ya existe), centro de costo.

## FV5 parcial (2026-10-03): AIU y copiar factura

El usuario confirmó que la emisión a la DIAN (Factus), las retenciones y las notas crédito/débito ya
están cubiertas; de FV5 faltaban AIU y copiar factura.

- **AIU (V196):** `factura_venta.aiu` + porcentajes de Administración, Imprevistos, Utilidad e IVA sobre
  la utilidad; `factura_venta_detalle.aiu_tipo`. Las líneas de la obra se guardan con IVA 0 (costo
  directo) y `FacturaAiuService` agrega A, I y U como líneas de servicio (productos `AIU-ADMINISTRACION`,
  `AIU-IMPREVISTOS`, `AIU-UTILIDAD`, ocultos en el POS, creados la primera vez); solo la Utilidad lleva
  IVA. Como son líneas normales pasan sin cambios por la venta, el asiento, Factus y el PDF.
- **Copiar:** `POST /api/facturas-venta/{id}/copiar` → borrador nuevo (sin OC; vencimiento desde hoy;
  A, I y U se recalculan).

## FV4 y cierre de FV1 (2026-10-03, V197)

- **Desde cotización:** `POST /api/facturas-venta/desde-cotizacion/{id}?sucursalId=` → borrador con lo pendiente
  (descuento prorrateado); cada línea lleva `cotizacion_detalle_id` y la venta `cotizacionId`, así que
  `VentaService.crear` aplica la conversión D1 (PARCIAL/CONVERTIDA) y la anulación la devuelve.
- **Desde pedido de vendedor:** `POST /desde-pedido/{id}` (CREADA/PENDIENTE_DESPACHO sin venta) → al emitir
  la venta lleva `pedidoVendedorId` (crear() enlaza el pedido) y el pedido pasa a DESPACHADA.
- **Anticipos:** `POST /{id}/emitir` acepta `{anticipos:[{anticipoId, monto}]}`; a crédito, tras crear la venta
  se llama `AnticipoService.cruzar` contra la CxC de la venta (misma transacción). `GET /anticipos-cliente/{id}`.
- **Presentación por línea:** el front ofrece unidad base + presentaciones que se venden; la línea guarda
  `producto_presentacion_id` y `VentaService.crear` convierte a unidades base.
- **Centro de costo:** `factura_venta.centro_costo_id` y `venta.centro_costo_id`; `LectorVentaJpa` usa el de la
  venta y si no el de la sucursal.
- **Condiciones de pago:** pantalla `/ventas/facturas/condiciones-pago` (crear, editar, desactivar).
