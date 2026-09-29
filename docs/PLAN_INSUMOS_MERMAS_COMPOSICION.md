# Plan: SKU y composiciones en mermas/obsequios, uso de producto (insumos) y cuentas contables del producto

Fecha: 2026-09-14 · Repos: `aura-back-old` (back) y `aura-frontend` (front) · Migraciones: siguiente libre **V157** (V156 sin commit), espejo idempotente en Laravel.

---

## 0. Qué hay hoy (diagnóstico)

| Tema | Estado actual | Problema |
|---|---|---|
| Buscador de mermas y obsequios | Ambos llaman `GET productos/pos` (`form-merma.component.ts:136`, `form-obsequio.component.ts:134`) | `getProductos` filtra `visible_en_pos = true` (`ProductoQueryRepository.java:312,359`): **un insumo escondido del POS no se puede dar de baja**. |
| SKU en el form de merma | El SKU solo sale pegado al label del dropdown | No hay campo para escribir o escanear el SKU y agregar la línea directo. |
| Producto con receta en merma/obsequio | `MermaServiceImpl.crear` y `ObsequioServiceImpl.crear` descuentan el **padre** | El padre con receta no tiene inventario propio: da error "no tiene inventario" o descuenta un stock que no existe. Venta sí explota la receta (`VentaServiceImpl.java:430-481`), merma y obsequio no. |
| Tipo de producto | `tipo_producto` = ESTANDAR/KIT/PESABLE/SERVICIO (solo enum en front, sin CHECK en BD) + `visible_en_pos` | "Insumo" se simula escondiendo el producto del POS. No hay forma de filtrar insumos para recetas ni para compras. |
| Cuentas contables del producto | V89 creó `producto.categoria_contable_id` + overrides `cuenta_ingreso/costo/inventario_id`; la entidad los mapea y `ResolucionCuentaProductoJpa` los usa | `CreateProductoDto`/`UpdateProductoDto`/`ProductoDto` **no los exponen** y el form de producto no los muestra: nadie los puede llenar. |
| Asiento de merma | `ContabilidadAutoServiceImpl.generarDesdeMerma` usa `ConceptoContable.INVENTARIO` plano | Ignora la categoría contable del producto. El obsequio (`ObsequioGenerador`) sí resuelve por producto. |

---

## F0 · Verificación previa (30 min)

- [ ] Confirmar que las columnas de V89 existen en **local** y en **prod**: `SELECT column_name FROM information_schema.columns WHERE table_name='producto' AND column_name LIKE 'cuenta_%' OR column_name='categoria_contable_id';`. En Laravel están en `000089_create_categoria_contable_producto.php`.
- [ ] Confirmar que existe la categoría "General" sembrada en las empresas de prod.
- [ ] Tomar las decisiones de la sección **Decisiones abiertas**.

---

## F1 · Buscador de inventario + campo SKU (mermas y obsequios)

### Backend
1. Nuevo endpoint `GET /api/productos/inventario?search=&sucursalId=` en `ProductoController`.
   - **Sin** filtro `visible_en_pos`; `activo = true`, `deleted_at IS NULL`.
   - Coincidencia exacta de `sku` o `codigo_barras` primero, luego `ILIKE` por nombre. `LIMIT 30`.
   - Devuelve: `id, nombre, sku, codigoBarras, stockActual, costo, manejaLotes, manejaInventario, permitirStockNegativo, tieneComposicion` (EXISTS sobre `producto_composicion`), `unidadAbreviatura`.
   - Mapear todas las columnas en el DTO ([gotcha RowMapper manual]).
2. `GET /api/productos/inventario/sku/{codigo}?sucursalId=`: búsqueda exacta por SKU o código de barras, 404 si no existe. Es la que usa el campo SKU.

### Frontend (`form-merma` y `form-obsequio`)
1. Campo **SKU / código de barras** encima de la tabla: se escribe o se escanea, con Enter:
   - si el producto ya está en una línea → suma 1 a la cantidad;
   - si no → agrega la línea precargada (nombre, stock, costo, lotes).
   - Si no existe → aviso y el campo queda seleccionado para reintentar.
2. Columna **SKU** visible en la tabla de líneas.
3. El dropdown pasa a usar `productos/inventario` en vez de `productos/pos`.

**Aceptación:** un insumo con `visible_en_pos=false` aparece en merma y obsequio; escanear dos veces el mismo SKU deja una sola línea con cantidad 2.

---

## F2 · Composiciones en mermas y obsequios

Regla (igual que venta): **si el producto tiene receta, lo que sale de inventario son sus componentes**, no el padre. Un solo nivel, igual que `VentaServiceImpl` (las subrecetas se costean, pero no se explotan en inventario).

### Backend
1. **Servicio compartido** `ExplosionComposicionService` (sacarlo de lo que ya hace venta):
   - `explotar(productoId, cantidadBase, sucursalId, empresaId)` → lista de `ConsumoComponente(hijo, cantidad, costoUnitario, stockDisponible, suficiente)`.
   - Omite hijos con `maneja_inventario = false` (igual que venta).
   - Venta **no se toca** en esta fase; se migra a este servicio después.
2. **V157 — snapshot de lo consumido**: tabla `inventario_consumo_componente`
   ```
   id, empresa_id, origen VARCHAR(20)  -- MERMA | OBSEQUIO (VENTA más adelante)
   detalle_id BIGINT                   -- merma_detalle.id / obsequio_detalle.id
   producto_hijo_id, cantidad NUMERIC(18,6), costo_unitario NUMERIC(18,6),
   created_at
   INDEX (origen, detalle_id)
   ```
   Por qué snapshot: si mañana cambian la receta, **anular** tiene que devolver lo que realmente salió, no lo que dice la receta hoy. (Anular y devolver una venta tienen hoy ese bug: releen la receta en `VentaServiceImpl.java:724` y `:821`. Queda anotado, fuera de alcance.)
3. `MermaServiceImpl.crear` / `ObsequioServiceImpl.crear`:
   - El detalle sigue guardando el **padre** (lo que reporta el usuario: "se dañaron 3 panes").
   - Si `tieneComposicion`: validar stock de cada hijo (respetando `permitir_stock_negativo`), descontar inventario del hijo, kardex con referencia `"Merma #N [componente de Pan]"`, guardar snapshot.
   - **Costo de la línea = suma de componentes** (el `costoUnitario` que manda el front se ignora para compuestos). Obsequio: la base comercial y el IVA siguen saliendo del precio del **padre**.
   - No se permite lote en una línea compuesta.
4. `anular`: si la línea tiene snapshot, reingresa los hijos desde el snapshot con `ANULACION_MERMA` / `ANULACION_OBSEQUIO`.
5. Endpoint de vista previa para el front: `GET /api/productos/composicion/{padreId}/explosion?cantidad=&sucursalId=`.
6. Contabilidad: el asiento de la merma acredita **la cuenta de inventario de cada componente** (ver F4); `LectorObsequioJpa` debe leer las líneas de consumo para que el crédito a inventario salga por componente.
7. `obtenerDetalles` de merma y obsequio devuelve los componentes consumidos por línea (para el detalle y el PDF).

### Frontend
1. Línea compuesta: chip **"Receta"** y fila desplegable con los componentes (cantidad × cantidad de la línea, stock, costo).
2. Stock de la línea compuesta = "alcanza para N" (mínimo entre hijos); la alerta de stock se evalúa por componente.
3. Costo unitario de la línea compuesta en solo lectura (lo calcula el back).
4. `detalle-merma` y `detalle-obsequio` muestran los componentes consumidos.

**Aceptación:** merma de 3 panes (receta 0,05 kg harina) → kardex −0,15 kg de harina, sin movimiento del pan; anular reingresa 0,15 kg aunque la receta haya cambiado entre tanto.

---

## F3 · Uso del producto: VENTA / INSUMO / AMBOS

`tipo_producto` dice **cómo se vende o se mide** (estándar, kit, pesable, servicio). Que sea insumo es **otra cosa**: la harina es PESABLE **e** INSUMO. Por eso va en una columna aparte y no como un valor más de `tipo_producto`.

### V158
```sql
ALTER TABLE producto ADD COLUMN IF NOT EXISTS uso_producto VARCHAR(10) NOT NULL DEFAULT 'VENTA';
-- CHECK (uso_producto IN ('VENTA','INSUMO','AMBOS')) con guard en pg_constraint
-- Backfill (solo cuando la columna se crea):
--   visible_en_pos = false                                   → INSUMO
--   visible y componente de alguna receta                    → AMBOS
--   el resto                                                 → VENTA
```
Se ajustó al implementar: hoy "oculto del POS" es justamente como se marcaba un insumo, esté o no en una receta.
Con `ddl-auto=validate`, el tipo en BD y en la entidad tiene que coincidir ([gotcha ADD COLUMN IF NOT EXISTS]).

### Reglas
| Pantalla | Qué productos muestra |
|---|---|
| POS (`/productos/pos`) | `uso IN (VENTA, AMBOS)` **y** `visible_en_pos` |
| Componentes de receta (tipo RECETA) | `uso IN (INSUMO, AMBOS)` + productos que tienen receta propia (subrecetas) |
| Componentes de KIT | cualquiera que se venda (`VENTA, AMBOS`) |
| Mermas, obsequios, compras, ajustes, kardex | todos (`/productos/inventario`) |

- `visible_en_pos` se mantiene como interruptor, pero un INSUMO **no puede** estar visible en POS: el back lo fuerza a `false` y el front apaga y bloquea el switch.
- INSUMO: precio de venta opcional; `maneja_inventario` en true por defecto.
- Si se vuelve INSUMO un producto con ventas en borrador/cotizaciones abiertas, solo se avisa (no se bloquea).

### Back
- `ProductoEntity.usoProducto`, DTOs de crear/actualizar/detalle/tabla, validación del enum.
- Filtro `uso` opcional en `/productos/page` y `/productos/list`, y el que usa el selector de receta (`productoService.search`).

### Front
- Selector **Uso** (Venta / Insumo / Ambos) en el form de producto, junto a "Tipo de producto".
- Columna y filtro por uso en el listado de productos.
- `form-receta`: el buscador de componentes manda `uso=INSUMO,AMBOS` cuando la receta es tipo RECETA.

---

## F4 · Cuentas contables en el producto

### Back
1. `CreateProductoDto`, `UpdateProductoDto`, `ProductoDto`: `categoriaContableId`, `cuentaIngresoId`, `cuentaCostoId`, `cuentaInventarioId` (+ etiquetas `codigo – nombre` en el detalle).
2. Validar al guardar reutilizando las reglas de `CategoriaContableProductoServiceImpl.validar`: ingreso clase 4, costo clase 6/7, inventario 14, cuenta auxiliar y de la misma empresa; categoría activa y de la empresa.
3. Sembrar la categoría **"Insumos / materias primas"** (tipo INSUMO, inventario 1405) junto a "General", idempotente. Al crear un producto INSUMO sin categoría, se propone esa.
4. `generarDesdeMerma`: acreditar inventario **por cuenta resuelta de cada producto/componente** (`ResolucionCuentaProducto`), agrupando por cuenta como ya hace el obsequio.

### Front (form de producto)
- Pestaña **Contabilidad**: dropdown de categoría contable (muestra en gris las cuentas que hereda).
- Sección colapsada **"Cuentas específicas de este producto (avanzado)"** con los 3 overrides usando el selector de cuentas auxiliares que ya existe; texto de ayuda: "déjalo vacío para usar las de la categoría".
- Sin categoría → se muestra "General".

**Aceptación:** producto con override de inventario 143505 → la venta, la merma y el obsequio acreditan 143505; sin override y con categoría Insumos → 1405.

---

## Orden recomendado

1. **F1** (rápido y destraba ya a quien necesita dar de baja insumos).
2. **F4 back** (DTOs + asiento de merma por cuenta): lo necesita F2 para el asiento por componente.
3. **F2** (el más delicado: inventario + snapshot + contabilidad).
4. **F3** (migración con backfill; mejor después de F2 para que el backfill use las recetas ya limpias).
5. **F4 front**.

Cada fase: migración Flyway + espejo Laravel idempotente, compilar back y front, prueba en `aura-pos` local. **Nada en `aura-db` (prod) sin backup.**

## Decisiones abiertas

1. **Campo SKU**: ¿escáner/escritura que agrega la línea (propuesto) o solo mostrar la columna SKU?
2. **Selector de componentes de receta**: ¿restringir a INSUMO/AMBOS (propuesto) o mostrar todo marcando cuáles son insumos?
3. **Producto con receta que además tiene stock propio** (p. ej. pan ya horneado y guardado): hoy venta siempre explota la receta. Se propone lo mismo en merma y obsequio por consistencia; si en algún negocio se produce y almacena, eso pide un módulo de **producción** (orden que consume insumos y da entrada al terminado), fuera de este plan.
