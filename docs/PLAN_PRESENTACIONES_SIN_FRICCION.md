# Plan: presentaciones sin fricción (caja ×10 que se vende suelta)

Fecha: 2026-09-15 · Repos: `aura-back-old` (back) y `aura-frontend` (front) · Migraciones: siguiente libre **V159** (V156–V158 sin commit), espejo idempotente en Laravel.

**Objetivo:** que una droguería o una tienda configure "la caja trae 10 y vendo la pastilla a $800" en segundos, sin saber qué es un factor de conversión, y que después la presentación aparezca sola en el POS, en compras, en mermas y en obsequios, sin tener que buscarla aparte.

---

## 0. Qué hay hoy (diagnóstico)

| Tema | Estado actual | Problema |
|---|---|---|
| Qué significa el factor | El back **divide**: `factor_conversion` = cuántas presentaciones caben en 1 unidad base (`VentaServiceImpl.java:817`, `DevolucionServiceImpl`, `ProductoComposicionServiceImpl:470`, `form-receta.component.ts:209`). El POS muestra `stock × factor` (`ProductoQueryRepository.java:485`). | El form de producto (`form-productos.component.html:1043`) y el de presentaciones (`form-presentaciones.component.html:132`) enseñan lo contrario: "Caja x 12 = 12", "1 presentación mueve *factor* unidades base". Quien siguió la ayuda de la pantalla quedó con el inventario mal descontado. |
| Unidad base del stock | Con la convención del back, la base es la unidad **grande** (la caja). | Vender una pastilla deja 0,1 cajas: por eso hubo que subir el stock a 6 decimales (V156). La gente no cuenta así. |
| POS | La consulta devuelve una fila por producto **más** una por cada presentación (`UNION ALL`, `ProductoQueryRepository.java:407-504`): cada presentación es una **tarjeta aparte**. | "Acetaminofén" muestra 2–3 tarjetas casi iguales; en el carrito no se puede cambiar de unidad a caja sin borrar la línea. |
| Compras | `CompraDetalleEntity` y `CreateCompraDetalleDto` no tienen presentación; `form-compra` busca con `productoService.search`. | La factura del proveedor dice "5 cajas": hay que convertir a mano cantidad y costo. |
| Merma y obsequio | `MermaDetalleEntity` / `ObsequioDetalleEntity` no tienen presentación; el buscador `/productos/inventario` no las trae. | No se puede registrar "se venció una caja" ni "regalé un sixpack" sin convertir. |
| Código de barras de la presentación | `codigo_barras ... unique()` **global** (Laravel `000011`). La tabla no tiene `empresa_id`. | Dos droguerías no pueden registrar la misma caja con su EAN real: la segunda recibe error de duplicado. |
| Configuración | Pestaña de presentaciones con nombre, factor, precio obligatorio (`min 0.01`), código de barras, flags de compra/venta. | Para 100 productos es inviable. No hay importación de productos ni edición masiva. |

Ya existe y se aprovecha: `es_default_venta` y `es_default_compra`, precios por lista/volumen/cliente por presentación (`ProductoPrecioEntity`, `PrecioVolumenEntity`, `PrecioClienteEntity`), selector de medida en recetas (V138), `venta_detalle` y `devolucion_detalle` ya guardan la presentación.

---

## Modelo objetivo (una sola regla en todo el sistema)

1. **El inventario vive en la unidad más pequeña que se vende** (la pastilla, la botella, el huevo). Para pesables, en su unidad de peso.
2. **Una presentación "contiene N unidades base".** Caja ×10 → `factor_conversion = 10`. Cantidad en base = cantidad × factor. Es lo que ya dicen las pantallas y lo que entiende cualquiera.
3. En pantalla nunca se dice "factor": se dice **"trae"** o **"contiene"**.
4. El stock se muestra como la gente cuenta: **"3 cajas + 4 und"** (cuando el producto tiene una presentación de factor entero ≥ 2); el número en unidades base queda como dato secundario.

---

## F0 · Diagnóstico de datos (antes de tocar código)

Correr primero en local (`aura-pos`) y luego, **solo lectura**, en prod.

```sql
-- 1. Cuántos productos tienen presentaciones, por empresa
SELECT p.empresa_id, COUNT(DISTINCT p.id) AS productos, COUNT(pp.id) AS presentaciones
FROM producto_presentacion pp
JOIN producto p ON p.id = pp.producto_id
WHERE pp.activo = true AND p.deleted_at IS NULL
GROUP BY p.empresa_id ORDER BY productos DESC;

-- 2. En qué dirección las cargaron (se deduce del precio)
--    GRANDE  = la presentación vale más que la unidad base → la cargaron como "contiene N" (lo que dice el form)
--    PEQUEÑA = vale menos → la cargaron como "caben N en 1 base" (lo que hace el back)
SELECT p.empresa_id, p.id, p.nombre, p.precio AS precio_base,
       pp.id AS presentacion_id, pp.nombre AS presentacion, pp.factor_conversion, pp.precio AS precio_pres,
       CASE
         WHEN pp.factor_conversion <= 1 OR COALESCE(pp.precio,0) = 0 OR COALESCE(p.precio,0) = 0 THEN 'REVISAR'
         WHEN pp.precio > p.precio THEN 'GRANDE'
         ELSE 'PEQUEÑA'
       END AS lectura
FROM producto_presentacion pp
JOIN producto p ON p.id = pp.producto_id
WHERE pp.activo = true AND p.deleted_at IS NULL
ORDER BY lectura, p.empresa_id, p.nombre;

-- 3. Ventas con presentaciones GRANDES (el back descontó 1/factor en vez de ×factor)
SELECT p.empresa_id, COUNT(*) AS lineas, SUM(vd.cantidad) AS cantidad_vendida
FROM venta_detalle vd
JOIN producto_presentacion pp ON pp.id = vd.producto_presentacion_id
JOIN producto p ON p.id = pp.producto_id
WHERE pp.precio > p.precio AND pp.factor_conversion > 1
GROUP BY p.empresa_id;

-- 4. Códigos de barras de presentación que chocan con el de un producto de la misma empresa
SELECT p.empresa_id, pp.codigo_barras, pp.id AS presentacion_id, p2.id AS producto_con_mismo_codigo
FROM producto_presentacion pp
JOIN producto p  ON p.id = pp.producto_id
JOIN producto p2 ON p2.empresa_id = p.empresa_id AND p2.codigo_barras = pp.codigo_barras AND p2.deleted_at IS NULL
WHERE pp.codigo_barras IS NOT NULL;
```

Con el resultado se decide la migración de F1 (ver **Decisiones abiertas**). Si casi todo sale GRANDE, F1 es barata: basta con cambiar el cálculo del back.

La versión que se corre de verdad está en `docs/sql/f0_diagnostico_presentaciones.sql`: fuerza solo lectura y, cuando la presentación no tiene precio, clasifica por el nombre (caja/paca/bulto → GRANDE_NOMBRE; unidad/kg/gramo → PEQUENA_NOMBRE).

### Resultado en local (`aura-pos`, 2026-09-15)

| Empresa | Productos / presentaciones | Lectura | Qué pasa |
|---|---|---|---|
| 4 · FERRETOTAL | 3 / 3 | 2 PEQUEÑA, 1 factor 1 | **Datos reales en la convención del back.** Arroz: base PACA, presentación "UNIDAD ×25" a $2.500. Cemento: base bulto, "X 1KG ×50" a $6.000. |
| 1 · Ferretería Don Pepe | 6 / 8 | 5 factor 1, 2 GRANDE_NOMBRE, 1 PEQUEÑA_NOMBRE | Datos de prueba: presentaciones **sin precio**, nombres que no corresponden (CLAVO PARAGUAS → "COCACOLA 1LT"), factor 1 en casi todas. |

- **Ventas con presentación:** 3 líneas, todas de FERRETOTAL (2 de cemento por kilo y 1 de arroz por unidad). El inventario **cuadra con la convención del back**: 10 pacas − 1/25 = 9,96; 5 bultos − 2/50 = 4,96; costo de la unidad de arroz $840 = $21.000 ÷ 25. O sea: hoy no hay inventario mal descontado; hay inventario **en la unidad grande y con decimales**.
- **Otras referencias:** 1 precio de lista apunta a una presentación (empresa 1, "Arroz a bolas de kilo"); ninguna en precio por volumen, precio por cliente, recetas ni devoluciones.
- **Stock con decimales:** 3 productos con presentación (2 de FERRETOTAL).
- **Códigos de barras:** ningún choque dentro de la empresa; la restricción global `producto_presentacion_codigo_barras_key UNIQUE (codigo_barras)` existe tal cual.
- **Prod (`aura-db`): pendiente.** La conexión desde Claude Code quedó bloqueada por permisos; hay que correr el mismo archivo a mano.

**Lectura para F1 (sujeta a lo que diga prod):** el caso real es PEQUEÑA, así que F1 sí necesita la inversión de productos (base PACA → base UNIDAD). Con 2–3 productos por empresa la migración es chica y se puede revisar fila por fila; los datos de prueba de la empresa 1 conviene limpiarlos a mano en vez de automatizarlos.

---

## F1 · Una sola convención — ✅ HECHO 2026-09-15 (sin commit, V159 sin correr)

**Decisión tomada:** en prod no se corre diagnóstico, así que no se invierten productos a ciegas. La migración **voltea el factor** guardado (`factor := 1/factor`): el inventario se mueve exactamente igual que antes y no se toca stock, kardex ni historial. Los productos con base grande (paca) siguen así hasta que se pasen a unidad con una herramienta por producto (**F1b**, pendiente).

### Backend (hecho)
1. `utils/PresentacionConversion.aBase(cantidad, presentacion)`: cantidad × factor. Si el factor es menor que 1 y es 1/N (con tolerancia 0,0001), divide por N: 1.200 und con factor 0,08333333 dan 100 exacto y no 99,999996. Probado con jshell. Límite que ya existía antes: 12 ventas **separadas** de 1 und suman 0,999996, porque cada línea se redondea a los 6 decimales del stock. Se resuelve en F1b, cuando el producto pasa a base unidad. Reemplaza los `cantidadEnBase` de `VentaServiceImpl` (crear y anular) y `DevolucionServiceImpl` (crear y anular) y la resolución de `factor_unidad` en `ProductoComposicionServiceImpl`.
2. POS (`ProductoQueryRepository.getProductos`): stock de la fila de presentación = `FLOOR(ROUND(stock / factor, 4))`.
3. `ProductoPresentacionServiceImpl`: factor > 0; código de barras único **dentro de la empresa** contra otras presentaciones activas y contra `codigo_barras`/`sku` de productos (`ProductoPresentacionQueryRepository.codigoBarrasEnUso`). La entidad deja de declarar `unique`.
4. Mensaje de stock insuficiente en venta: ya no dice "bultos"; muestra la cantidad base y la presentación pedida.
5. **V159** (`V159__presentacion_factor_contiene.sql` + Laravel `000159`):
   - `factor_conversion` a `NUMERIC(18,8)` (en la base real era 38,2);
   - volteo una sola vez, marcado en la tabla nueva `migracion_datos_aplicada` (compartida Flyway/Laravel), respaldo en `producto_presentacion_bak_v159`;
   - quita cualquier `UNIQUE (codigo_barras)` sin depender del nombre de la restricción y crea índice normal.
   - Probada en `aura-pos` dentro de una transacción con ROLLBACK: dos corridas seguidas voltean una sola vez (5 presentaciones), stock del POS igual que antes (arroz 249 und, cemento 248 kg).
   - ⚠ **Desplegar junto con este back**: con el código viejo los factores volteados descontarían al revés. `down()` no revierte el volteo (restaurar desde el respaldo a mano).

### Frontend (hecho)
- `form-receta`: sin el inverso; etiqueta "1 Caja = N und".
- Form de producto y de presentaciones: el campo se llama "Contiene", admite 8 decimales y, si es menor que 1, muestra la equivalencia inversa ("1 PACA = 25 ARROZ DIANA UNIDAD"). Listados con 6 decimales.

### También hecho
- `ProductoServiceImpl` (crear, actualizar y cambiar código de barras) rechaza un código que ya use una presentación activa de la empresa (`ProductoQueryRepository.codigoUsadoPorPresentacion`).

### Pendiente
- Datos de prueba de la empresa 1 en local (presentaciones sin precio y nombres cruzados): limpiar a mano.

## F1b · "Pasar a unidad" por producto — ✅ HECHO 2026-09-15 (sin commit, V160 sin correr)

Botón **"Pasar a unidad"** en la fila de una presentación que contiene menos de 1 unidad base (form de producto, pestaña Presentaciones, solo en edición). Abre una vista previa en la misma pestaña, sin diálogo encima del diálogo: stock por sucursal antes/después, precio y costo, conteos de lo que se convierte, bloqueos y avisos. Pide la unidad nueva (sugerida por nombre) y el nombre de la presentación grande, y confirma.

### Qué hace (`CambioUnidadProductoService` + `CambioUnidadProductoRepository`, una transacción)
| Qué | Cómo |
|---|---|
| Producto | unidad base = la elegida; precio = precio de la presentación pequeña (o precio ÷ N); precio_2/3 y costo ÷ N; toma el código de barras de la pequeña |
| Presentación grande nueva | "PACA x 25": contiene N, precio y costo de la base vieja, código de barras del producto, compra por defecto |
| Presentación pequeña | inactiva, contiene 1 (las líneas viejas que la citan quedan en unidad base) |
| Otras presentaciones | contienen N veces más |
| Inventario, lotes | stock ×N (y stock mínimo); costo del lote ÷N |
| Kardex | se **reescribe** el historial del producto (cantidad y saldos ×N, costo ÷N). Un movimiento de "cambio de unidad" no sirve: el reporte suma entradas y salidas con `saldo_nuevo − saldo_anterior` y lo contaría como entrada |
| Venta y devolución | la cantidad escrita no cambia; se reapunta: sin presentación → la grande; la pequeña → sin presentación. Anular y devolver ventas viejas sigue cuadrando |
| Precios de lista | los de la base pasan a la grande y los de la pequeña pasan al producto |
| Recetas donde es componente | cantidad y factor ×N; la línea escrita en la base vieja apunta a la grande |
| `producto_cambio_unidad` (**V160**) | factor, unidades, presentaciones y el último id de compra, merma, obsequio y traslado en ese momento |

### Documentos viejos
Compra, merma, obsequio y traslado no guardan la presentación. Si el documento es anterior al cambio (id ≤ último registrado) y trae el producto —en merma y obsequio también los componentes consumidos por receta—, **anularlo o editar la compra se bloquea** con un mensaje que manda a corregir con un reconteo (`validarDocumentoPrevio` en `CompraServiceImpl.anular/actualizar`, `MermaServiceImpl.anular`, `ObsequioServiceImpl.anular`, `TrasladoServiceImpl.anular`).

### Bloqueos y avisos de la vista previa
- **Bloquea:** presentación inactiva o que no cabe un número exacto de veces; producto con receta propia; reconteo en BORRADOR o EN_CONTEO con el producto.
- **Avisa:** cotizaciones pendientes, pedidos creados o por despachar y órdenes de compra abiertas (sus cantidades siguen en la unidad vieja); precios por volumen o por cliente de la presentación pequeña, que dejan de aplicar porque solo existen por presentación; que los documentos viejos no se podrán anular.

### Probado en `aura-pos` (transacción con ROLLBACK, mismas sentencias del repositorio)
ARROZ DIANA (FERRETOTAL):
- stock 9,96 paca → 249 und;
- la venta de 1 und en el kardex pasa de −0,04 a −1, con saldo 250 → 249 y costo $21.000 → $840;
- la línea de venta queda sin presentación;
- se crea "PACA x 25" a $29.000 y el POS muestra 9 pacas;
- el producto queda con precio $2.500 y costo $840.

El bloqueo marca una compra anterior al cambio y deja pasar la siguiente. Back y front compilan.

### Límites conocidos
- El nombre del producto no se toca ("ARROZ DIANA PACA X 25" sigue diciendo paca): se sugiere renombrarlo a mano.
- Costos de lote y de kardex tienen 2 decimales en la base real: dividir por N redondea (p. ej. $28.571,43 ÷ 50 = $571,43).
- Nota crédito de compra sobre una factura anterior al cambio: toma cantidades escritas por el usuario y no se bloquea.

**Aceptación F1:** paca ×25 con stock 9,96 → tras V159 vender 1 und deja 9,92 paca, igual que antes; caja ×10 nueva sobre base und → vender 2 cajas descuenta 20 und; dos empresas pueden registrar el mismo EAN; en la misma empresa, no.

---

## Estado 2026-09-15 · F2, F3, F4 y "vende por unidad" — ✅ HECHO (sin commit; V162 corrida solo en local)

**Decisión de modelo.** En compra, merma y obsequio, `cantidad` y `costo_unitario` siguen en unidad base (100 und a $2.100). Así anular, editar, nota crédito, kardex, contabilidad y reportes quedan igual. Lo escrito por el usuario (4 Pacas a $52.500) se guarda aparte: `producto_presentacion_id`, `cantidad_presentacion` y, en compra, `costo_presentacion`. El subtotal de compra se calcula con lo escrito, porque el costo unitario tiene 2 decimales en la base.

**V162:** `producto.vende_por_unidad` y las columnas de presentación en `compra_detalle`, `merma_detalle` y `obsequio_detalle`, con espejo en Laravel.

| Parte | Back | Front |
|---|---|---|
| F4 Compras | Convierte a unidad base antes de validar la nota crédito y de mover inventario (crear y editar); el detalle devuelve lo escrito | Selector Unidad/Paca en la columna Cant., con "= 100 und"; preselecciona la presentación de compra por defecto y su costo; al cambiar de presentación escala el costo; al editar recupera lo escrito; el detalle muestra "4 Paca × $52.500 · 100 u." |
| F3 Merma | Convierte a unidad base; un compuesto no se registra por presentación | Selector en Cantidad; stock y costo en unidades |
| F3 Obsequio | Igual; la base comercial sale del precio de la paca, sin IVA, repartido entre las unidades | Selector; al elegir Paca la base comercial pasa al precio de la paca |
| F2 POS | `/productos/pos` devuelve una fila por producto con `presentaciones[]` (stock en presentaciones completas) y `vendePorUnidad`; un producto que no se vende por unidad solo aparece si tiene presentación a la venta | Una tarjeta por producto con botones [Und $] [Paca $]; tocar la tarjeta agrega la opción por defecto; stock "2 Paca + 22 uds"; escanear el código de la paca agrega la paca; en el carrito se cambia Und ↔ Paca sin borrar la línea (si ya hay una línea igual, se unen); stock validado por producto en unidades; el precio de lista por unidad ya no se aplica a la paca |
| Venta | Rechaza una presentación de otro producto, una que no se vende y la unidad suelta si el producto no se vende por unidad | — |
| Producto | `vende_por_unidad` se conserva si otra pantalla no lo envía | "¿Cómo puedes vender? ✓ Por unidad" se guarda en `vendePorUnidad` |

**No incluido:** escanear el código de la paca en merma y obsequio (hoy se elige en el selector); órdenes de compra y cotizaciones por presentación.

## F2 · POS: una tarjeta por producto, las presentaciones aparecen solas

### Backend
- `/productos/pos` devuelve **una fila por producto** con `presentaciones: [{id, nombre, factor, precio, codigoBarras, stockEnPresentacion, esDefaultVenta}]` (una query extra agrupada por producto, igual que ya se hace con `getComposiciones`). Se elimina el `UNION ALL`.
- El escaneo sigue resolviendo el código de barras de la presentación directo.

### Frontend (`pos.component`)
1. **Tarjeta única**: nombre, precio de la presentación por defecto y chips con las demás: `Und $800 · Caja ×10 $7.500`. Stock: "3 cajas + 4 und".
2. **Tocar la tarjeta** agrega la presentación por defecto (`es_default_venta`, o la unidad si no hay). **Tocar un chip** agrega esa presentación. No hay que buscar "caja".
3. **Buscar por texto** ("acetam") muestra la misma tarjeta con sus chips; buscar "caja" sigue encontrando por nombre de presentación.
4. **Escanear** el código de la caja agrega la caja; escanear el de la unidad agrega la unidad.
5. **Línea del carrito con selector de presentación** (`Und ▾`): cambiarla recalcula precio (incluida lista de precios, volumen y precio de cliente de esa presentación), IVA y validación de stock sin borrar la línea. Si al cambiar ya existe otra línea del mismo producto y presentación, se fusionan.
6. Stock del carrito por producto, no por línea: 2 cajas + 5 und del mismo producto consumen 25 und del mismo stock.
7. Atajo de teclado: con la tarjeta enfocada, `1`/`2`/`3` eligen chip.

**Aceptación:** buscar "acetaminofén" muestra **una** tarjeta con chips Und y Caja; se agrega 1 caja, se cambia la línea a Und y el precio pasa de $7.500 a $800 sin borrar nada; el total de stock validado es del producto.

---

## F3 · Merma y obsequio con presentación

### Backend
1. **V161**: `merma_detalle` y `obsequio_detalle` → `producto_presentacion_id BIGINT NULL` (FK) y `cantidad_base NUMERIC(18,6)`. Backfill `cantidad_base = cantidad` en las filas existentes.
2. `CreateMermaDetalleDto` / `CreateObsequioDetalleDto`: `presentacionId` opcional.
3. `crear`: `cantidad_base = aBase(cantidad, presentacion)`; stock, kardex (`"Merma #N [Caja ×10]"`) y consumo de receta se calculan con `cantidad_base`. Costo de la línea = costo base × `cantidad_base`.
4. Obsequio: la base comercial y el IVA salen del **precio de la presentación** (regalar un sixpack vale lo que vale el sixpack, no 6 × precio suelto).
5. `anular`: reingresa `cantidad_base` guardada (no recalcula con el factor de hoy, igual criterio que el snapshot de receta de V157).
6. `/productos/inventario` y `/productos/inventario/codigo/{codigo}` devuelven `presentaciones[]`; el de código resuelve también el código de barras de la presentación y dice cuál fue.
7. Lote: se permite con presentación (la caja pertenece a un lote); la validación de stock del lote usa `cantidad_base`.

### Frontend (`form-merma`, `form-obsequio`)
- Columna **Presentación** en cada línea: selector con `Und / Caja ×10 / …` precargado con la por defecto.
- Escanear el código de la caja agrega la línea **ya en Caja**; escanearlo dos veces suma 1 caja, no 10 und.
- La columna de stock muestra "3 cajas + 4 und" y la alerta compara `cantidad × factor` contra el stock base.
- `detalle-merma` / `detalle-obsequio`: "1 Caja ×10 (10 und)".

**Aceptación:** merma de 1 caja ×10 con stock 34 → kardex −10 und, stock 24; obsequio de 1 sixpack usa el precio del sixpack como base comercial; anular devuelve 10 und aunque entre tanto cambien el factor a 12.

---

## F4 · Compras con presentación (y se configura sola la primera vez)

### Backend
1. **V162**: `compra_detalle` → `producto_presentacion_id`, `cantidad_base`; ídem `orden_compra_detalle` si existe. Backfill `cantidad_base = cantidad`.
2. `CompraServiceImpl.crear`: entrada al inventario con `cantidad_base`; **costo base = costo de la línea ÷ cantidad_base**, que es el que actualiza `producto.costo` y el costo promedio. Anular usa `cantidad_base`.
3. Si la línea trae `nuevaPresentacion: {nombre, contiene}` (sin id), se crea la presentación en la misma transacción con `es_default_compra = true` y se usa.
4. Precios de venta sugeridos en la línea (`precioVenta1..3`) se interpretan por unidad base.

### Frontend (`form-compra`)
1. Selector **Presentación** por línea, precargado con `es_default_compra`.
2. **Pregunta de una sola vez**: si el producto no tiene presentaciones y el usuario escribe una cantidad, aparece el enlace *"¿Llega en caja/paca?"* → mini formulario en línea `[Caja ▾] trae [10] und` → guarda y la deja por defecto para la próxima compra.
3. Muestra la conversión en la línea: "5 Cajas = 50 und · costo und $620".

**Aceptación:** compra de 5 cajas a $6.200 c/u de un producto sin presentaciones → se crea "Caja ×10" desde la compra, entran 50 und, costo del producto $620; la siguiente compra ya viene en Caja.

---

## F5 · Form de producto en dos preguntas — 🟡 PRIMERA PARTE HECHA 2026-09-15 (sin commit)

**Hecho (solo front, sin cambios de back):**
- **Página plana:** el form de producto deja de ser modal. Rutas `catalogo/productos/nuevo` y `catalogo/productos/editar/:id` (patrón de `terceros/form-plano`); el listado navega. **Se conservan las pestañas** para separar: Básico · Empaque y precios · Inventario · Contabilidad · Presentaciones (avanzado). Si falta algo al guardar, salta a la pestaña del error.
- **Sin switches nuevos:** las preguntas del empaque ("¿Cómo lo compras?", "¿También lo vendes suelto?") son botones de opción.
- **Switches arreglados en toda la app:** `p-inputSwitch` (PrimeNG 18.0.2) registraba un CSS "toggleswitch" incompleto que dañaba todos los switches de la sesión. Se cambiaron los 20 usos, en 12 pantallas, por `p-toggleswitch`.
- **Cabecera y pestaña Básico rediseñadas:**
  - la cabecera lleva el estado Activo a la derecha;
  - arriba, la imagen a la izquierda, y a la derecha nombre, SKU, código de barras, categoría y marca;
  - tarjeta "Información comercial": unidad, tipo y uso en 3 columnas, más Visible en POS;
  - tarjeta "Descripción".
- **Empaque de compra:**
  - interruptor "Lo compro en empaque" y frase "Cada [Caja ▾] trae [10] und", con lista editable de empaques comunes;
  - costo del empaque: el **costo por unidad se calcula solo** (÷ trae) y el campo de costo del producto queda en solo lectura;
  - precio del empaque: si se deja vacío se usa precio suelto × trae, y hay un botón para tomar "precio del empaque ÷ trae" como precio suelto;
  - código de barras del empaque e interruptor "También lo vendo suelto" (apagado = el empaque es la venta por defecto).
- **Al guardar** se crea o actualiza una presentación "contiene N", compra por defecto. Si se apaga el interruptor, la presentación queda inactiva. Si falla (p. ej. código de barras repetido), el producto queda guardado y se abre su edición para corregir.
- **Al editar,** el empaque es la presentación de compra por defecto que contiene más de 1 unidad. Las demás, y "Pasar a unidad", quedan en la sección avanzada.
- **Quitado:** el recálculo que pisaba precio y costo del producto al marcar una presentación como compra por defecto. Además, la presentación nueva ya no viene marcada como compra por defecto.
- **Arreglado:** faltaba `<p-confirmDialog>` en el form, así que las confirmaciones de eliminar presentación y de "Pasar a unidad" no se mostraban.

**Pestaña "Empaque y precios" rediseñada (2026-09-15), con cuatro tarjetas:**
1. **Empaque de compra:**
   - pregunta "¿Cómo lo compras?" con [Por unidad] [En empaque] y la frase "Cada [Caja ▾] contiene [10] [Unidad ▾]"; el selector de unidad es el mismo control que la unidad de inventario;
   - costo del empaque (obligatorio) y código de barras;
   - tarjeta "Costo calculado por unidad" con la fórmula a la vista;
   - si se compra por unidad, se pide directo el costo por unidad.
2. **Forma de venta:** casillas [Por unidad] [Empaque completo], con el precio por unidad (final al cliente y margen) y el precio del empaque. Es obligatoria al menos una.
3. **Impuestos:** selector de IVA (0 / 5 / 19 %, más la tarifa guardada si es otra), impoconsumo y "El precio ya incluye IVA".
4. **Precios adicionales:** Precio 2 (Mayorista) y Precio 3 (Distribuidor).

**Back para "Empaque completo" (V161):**
- **Columna nueva:** `producto_presentacion.se_vende` (por defecto true). Con false la presentación sirve para comprar pero el POS no la ofrece.
- **Guardado:** el servicio conserva el valor si otra pantalla no lo envía.
- **Arreglado de paso:** `listarPorProducto` no devolvía precio, costo ni las marcas por defecto; en edición la lista mostraba precios vacíos.
- **"Por unidad" sin marcar:** se guarda como presentación de venta por defecto. Que el POS oculte la unidad suelta queda para F2.

**Pendiente de F5:** plantilla por categoría, "Copiar de otro producto", nombres de unidad en plural.

Reemplaza la pestaña de presentaciones como camino principal (la pestaña queda como **avanzado**).

```
¿Cómo te llega?      [Unidad ▾]  → si elige Caja/Paca/Display/Bulto/Cubeta: trae [10] unidades
¿Lo vendes suelto?   (•) Sí  precio por unidad [$800]   sugerido $750 (precio caja ÷ 10 + margen)
                     ( ) No, solo por [Caja]
Precio de la Caja    [$7.500]
```

- Crea en el back la presentación con su precio y los flags: si vende suelto, venta por defecto = Und y compra por defecto = Caja; si no, ambas = Caja.
- Lista corta de nombres (Caja, Paca, Sixpack, Display, Bulto, Cubeta, Blíster, Docena) con opción de escribir otro.
- Pesables: no pregunta conversión de kg/lb/g (son fijas); solo "¿llega en bulto de [25] kg?".
- Precio de presentación deja de ser obligatorio: si viene vacío se calcula `precio base × factor`.
- **Plantilla por categoría** (`categoria.presentacion_sugerida_nombre`, `categoria.presentacion_sugerida_contiene`, `categoria.vende_suelto`, en **V163**): al crear un producto de "Medicamentos" el form ya viene con "Caja, vende suelto"; solo se escribe cuántas trae.
- Botón **"Copiar de otro producto"** que trae unidad, presentaciones y flags.

---

## F6 · Carga masiva (para quien ya tiene 100 productos)

1. **Edición en tabla** (`/catalogo/productos/presentaciones-masivo`): una fila por producto con columnas *Llega en · Trae · Vende suelto · Precio und · Precio presentación · Código de barras de la presentación*. Navegación con Tab/Enter tipo hoja de cálculo, guardado por lote (`POST /productos/presentaciones/lote`, máximo 500 filas, transacción por fila y reporte de errores por fila).
2. Filtros para trabajar por bloques: categoría, "sin presentación", proveedor.
3. **Aplicar a varios**: seleccionar filas → "Llega en Caja, vende suelto" y solo queda escribir la cantidad en cada una.
4. **Importar Excel** con esas mismas columnas (plantilla descargable desde el back, POI como en el export de facturación), con vista previa de lo que se va a crear y cambiar antes de confirmar.

---

## F7 · Mostrar el stock como se cuenta en el resto de pantallas

Solo visual, sin cambiar datos: listado de productos, inventario, kardex, traslados, reconteo y ajustes muestran "3 cajas + 4 und" junto al valor base. Un único pipe en el front (`stockPresentacion`) con la presentación de mayor factor entero.

Traslados, reconteo y ajustes siguen trabajando en unidad base en esta fase; recibir un traslado "en cajas" queda para después si se pide.

---

## Orden recomendado

1. **F0** (una hora; decide el costo de F1).
2. **F1** (bug de convención + unidad base pequeña + código de barras por empresa). Sin esto, todo lo demás hereda el error.
3. **F2** (POS) — lo que el cliente ve todos los días.
4. **F3** (merma/obsequio) — reutiliza la misma utilidad y el mismo selector.
5. **F4** (compras + pregunta de una vez) — es el que más configuración ahorra.
6. **F5** (form en dos preguntas + plantilla por categoría).
7. **F6** (tabla masiva + Excel).
8. **F7** (visual).

Cada fase: migración Flyway + espejo Laravel idempotente, compilar back y front, prueba en `aura-pos` local. **Nada en `aura-db` (prod) sin backup**; F0 en prod es solo lectura.

## Riesgos a revisar
- **Facturación electrónica:** la línea de una venta en Caja debe salir con la cantidad y la unidad de medida de la presentación (no 10 und a $750). Revisar el mapeo a Factus antes de F2.
- **Devolución de ventas viejas** tras invertir un producto en F1: depende de que la migración reasigne la presentación a las líneas históricas; probar devolver una venta anterior a la migración.
- **POS sin conexión / pestañas guardadas** con carritos de la estructura vieja (`presentacionId` por tarjeta): limpiar o migrar al cargar.
- Productos compuestos con presentación: se explota la receta con `cantidad_base`.

## Decisiones abiertas
1. **Productos cargados en la convención del back (PEQUEÑA):** ¿invertirlos en migración (propuesto) o dejarlos y pedir al cliente reconfigurarlos? Depende del número que dé F0.
2. **Ventas históricas mal descontadas (GRANDE):** ¿solo reporte para reconteo (propuesto) o ajuste automático de kardex?
3. **Tocar la tarjeta en el POS:** ¿agrega la presentación por defecto (propuesto) o abre siempre el selector de presentaciones?
4. **Precio sugerido de la unidad suelta:** ¿margen fijo por empresa, por categoría, o solo `precio caja ÷ contenido` sin margen?
