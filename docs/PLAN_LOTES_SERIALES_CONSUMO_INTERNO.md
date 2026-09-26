# Plan · Lotes, seriales y consumo interno

Diseñado el 2026-09-16. **F0–F6 hechos (sin commit, migraciones solo en local).** V163 es el consumo interno (F5); lotes V164 y seriales V165. Cada una lleva espejo idempotente en Laravel (`aura-pos-migracion-old`).

**Objetivo:**
- Que el lote (con su fecha de vencimiento) y el serial **entren con la compra** y **salgan con cada documento**, sin que nadie los lleve a mano.
- Agregar un documento nuevo, **Consumo interno** (el "descargue de inventario"), para lo que el negocio saca del inventario para su propio uso.

---

## 0. Qué hay hoy (diagnóstico 2026-09-16)

### Lotes (`lote`: producto, sucursal, codigo_lote, fecha_vencimiento, stock_actual, costo_unitario)
| Dónde | Estado |
|---|---|
| **Compra** | **No crea lotes.** `CompraServiceImpl` hace `detalle.setLote(null)` al crear y al editar, y `resolverLote` está comentado. La anulación sí intenta revertir un lote que nunca se asigna. |
| **Pantalla Inventario › Lotes** | `LoteServiceImpl.crear` guarda un lote con `stock_actual` **sin mover el inventario ni el kardex**. El stock del lote es un número paralelo que no cuadra con `inventario.stock_actual`. |
| **Venta** | Descuenta del lote solo si llega `loteId`, pero **el POS nunca lo envía**. `venta_detalle.lote_id` es único, así que una línea no puede salir de dos lotes. |
| **Merma, traslado** | Descuentan del lote si se elige a mano. El traslado crea el lote en destino con el mismo código. |
| **Devolución, reconteo** | Tienen la columna `lote_id`; no hay reglas. |
| **Seguridad** | Venta, merma y traslado buscan el lote con `findById`, **sin validar la empresa**. |
| **Dashboard** | Ya existe "lotes por vencer" (`LoteVencimientoDto`), pero se alimenta de los lotes manuales. |

### Seriales (`serial_producto`: producto, sucursal, serial, estado DISPONIBLE/VENDIDO/GARANTIA)
| Dónde | Estado |
|---|---|
| **Compra** | No pide seriales. Solo se crean a mano en Inventario › Seriales, sin enlace a la compra, sin costo y sin proveedor. |
| **Venta** | Los marca VENDIDO si llegan `serialIds`, pero **el POS no los pide**. |
| **Anular venta** | 🔴 **Bug grave:** `serialJPARepository.findAll()` filtra por VENDIDO y los pasa **todos** a DISPONIBLE, **de todas las empresas**, no solo los de esa venta (`VentaServiceImpl` ~línea 792). |
| **Devolución, merma, traslado, obsequio** | No tocan seriales. |
| **Invariante** | Nada garantiza que los seriales DISPONIBLES coincidan con el stock. |

### Salidas de inventario que ya existen
| Documento | Para qué | Contabilidad |
|---|---|---|
| **Merma** (`motivo_merma`) | Pérdida: vencido, dañado, robo | Asiento por evento; el motivo decide si afecta contabilidad |
| **Obsequio** | Regalo a un tercero | `ObsequioGenerador`, con IVA sobre el valor comercial |
| — | **Uso del propio negocio** | **No existe.** Hoy se registra como merma (queda como pérdida) o no se registra |

---

## Modelo objetivo

1. **Stock por lote que cuadra.** Para un producto con `maneja_lotes`, en cada sucursal `Σ lote.stock_actual = inventario.stock_actual`. Ningún documento mueve uno sin el otro.
2. **Una línea puede salir de varios lotes.** Tabla puente `<documento>_detalle_lote (detalle_id, lote_id, cantidad_base)` para venta, merma, obsequio, consumo interno y traslado. Las anulaciones y devoluciones reversan exactamente esa tabla.
3. **FEFO automático** (lo primero en vencer sale primero). El sistema elige los lotes; el usuario puede cambiarlos. Nadie tiene que escoger un lote para vender un producto.
4. **Seriales que cuadran.** Para un producto con `maneja_serial`, los seriales DISPONIBLES de la sucursal = stock. Cada serial sabe de qué compra entró y en qué documento salió.
5. **Lote y serial siempre en unidad base.** El serial exige cantidades enteras; no se vende "Caja ×10" de un producto con serial sin leer 10 seriales.

---

## F0 · Arreglos urgentes y diagnóstico — ✅ HECHO 2026-09-16 (sin commit, sin migración)

**Hecho (solo back; el front no cambia):**
- **Anular venta:** libera solo los seriales de `venta_detalle_serial` de esa línea (`findByVentaDetalleId`), aunque el producto ya no maneje serial.
- **`LoteJPARepository.buscarParaSalida`:** valida empresa, producto, sucursal, lote activo y stock suficiente del lote. Se usa en venta, merma, obsequio y traslado. El traslado ya validaba el stock; ahora también lo demás.
- **Venta con serial:** busca el serial por empresa y valida que sea del producto y de la sucursal.
- **`/lotes/disponibles` y `/seriales/disponibles`:** filtran por la empresa del token. Antes devolvían los de cualquier empresa.
- **Alta de serial:** la duplicidad se revisa por producto, sin distinguir mayúsculas ni espacios. Antes se buscaba en toda la tabla y el mismo serial en otra empresa lo bloqueaba (o reventaba si había dos).
- **Borrar serial:** solo si está DISPONIBLE y nunca salió en una venta.
- **Devolución:** no se tocó. Solo guarda `lote_id`, no mueve stock del lote; queda para F3.
- **SQL:** `docs/sql/f0_diagnostico_lotes_seriales.sql` (7 consultas, la reparación de seriales liberados va comentada).

**Resultado en local (`aura-pos`):**
- Solo la empresa 1 (datos de prueba) tiene algo: 2 productos con lote, 2 lotes y 2 seriales.
- **Ningún lote cuadra:** MANGO tiene 99,1 en inventario y ningún lote; Cerveza tiene 145 en inventario y 90 en un lote vencido de un producto que no maneja lotes.
- **Seriales:** los 2 están en productos que no manejan serial.
- Sin descuentos entre empresas y sin seriales liberados por el bug.
- **Prod sin correr:** pedirle al usuario que lo corra (solo lectura).


**Bugs:**
1. **Anular venta:** devolver a DISPONIBLE solo los seriales de `venta_detalle_serial` de esa venta.
2. **Lote de otra empresa:** reemplazar `findById` por `findByIdAndSucursalEmpresaId` en venta, merma, traslado y devolución, y validar que el lote sea del producto y de la sucursal del documento.

**SQL de diagnóstico** (`docs/sql/f0_diagnostico_lotes_seriales.sql`, solo lectura):
- Productos con `maneja_lotes` y la diferencia `inventario.stock_actual − Σ lote.stock_actual` por sucursal.
- Lotes con stock negativo, vencidos con stock y lotes de productos que ya no manejan lotes.
- Productos con `maneja_serial`: stock contra seriales DISPONIBLES.
- Seriales repetidos por empresa.

**Aceptación:** anular una venta con serial deja DISPONIBLE solo ese serial; mandar el `loteId` de otra empresa responde 400.

---

## F1 + F2 · Estado 2026-09-16 — ✅ HECHO (sin commit; V164/000164 corrida solo en local)

**Hecho:**
- **V164:** `lote` + empresa_id, fecha_fabricacion, compra_detalle_id, created_at; índice FEFO; `uq_lote_codigo` (producto, sucursal, código sin mayúsculas ni espacios; no se crea si hay duplicados); `compra_detalle_lote`; `lote_ajuste`; cuadre `V164_cuadre_sin_lote`. Solo se creó la tabla puente de compra: las de venta, merma, obsequio y traslado entran con F3.
- **`LoteStockService`:** `entrar` (crea o suma, costo promedio, rechaza el mismo código con otra fecha), `salir` (elegidos o FEFO, opción de incluir vencidos), `devolver`, `retirarEntrada` (bloquea si ya salió mercancía) y `cuadrarSinLote`.
- **Compra:**
  - **Crear:** los lotes escritos tienen que sumar la línea, y van en la presentación de la línea; el back pasa a base. Si no llegan lotes, entra a SIN-LOTE; el front no lo permite, solo protege a otros llamadores.
  - **Nota crédito:** FEFO con vencidos incluidos.
  - **Editar y anular:** reversan por `compra_detalle_lote`, y se bloquean si del lote ya salió mercancía.
  - **Kardex:** un movimiento por lote.
- **Producto:** al activar "maneja lotes", su stock pasa a SIN-LOTE.
- **Pantalla Lotes:** ya no crea (el endpoint responde 400 y se borró `form-lote`). Muestra compra de origen, proveedor y unidad. `PUT /lotes/{id}` corrige código y vencimiento con motivo en `lote_ajuste`. Solo se desactiva un lote en cero. "Por vencer" incluye los ya vencidos con stock.
- **Front de compra:** botón "Lotes x/y" en la línea con diálogo (código, vence, cantidad). No guarda si no cuadra. El detalle de la compra muestra los lotes.

**Probado en local (9002, empresa 4, VINIPEL 5818):**
- **Activar lotes:** SIN-LOTE 100.
- **Compra de 10 en VIN-A (6) y VIN-B (4):** inventario 110 = lotes 110, kardex por lote.
- **Rechazos:** suma que no cuadra; mismo código con otra fecha.
- **Editar:** A 10, B 0.
- **Nota crédito de 3:** salió de VIN-A por FEFO.
- **Editar o anular la compra con salida:** bloqueado. Anular la nota crédito devuelve; anular la compra deja 100 = 100.
- **Pantalla de lotes:** corregir fecha deja fila en `lote_ajuste`, desactivar con stock se rechaza, crear se rechaza, y el listado muestra la compra de origen.

**(Resuelto con F3.) Hasta F3 el cuadre se rompía con las salidas:** venta, merma, obsequio, consumo interno y traslado todavía no descuentan lotes, salvo que se elija uno a mano. Un producto con lotes que se venda queda con más stock en lotes que en inventario, y la compra después no se podrá editar ni anular por la regla de "ya salió". No activar lotes en productos reales hasta terminar F3.

**Pendiente, fuera de F1/F2:** la nota crédito no deja elegir el lote en el front (el back ya acepta `loteId`); fecha de fabricación sin campo en el front.

## F1 · Lotes que cuadran con el inventario (base de datos y regla central)

**V164:**
- `lote`: agregar `empresa_id`, `fecha_fabricacion` (opcional), `compra_detalle_id` (el que lo creó) y `created_at`. Índice único `(producto_id, sucursal_id, codigo_lote)` y un índice `(producto_id, sucursal_id, fecha_vencimiento)` para FEFO.
- Tablas puente `venta_detalle_lote`, `merma_detalle_lote`, `obsequio_detalle_lote`, `traslado_detalle_lote` y `compra_detalle_lote`, todas con `(detalle_id, lote_id, cantidad_base)`. La columna `lote_id` actual queda solo como lectura de lo histórico.
- **Cuadre inicial**, una sola vez y con la marca en `migracion_datos_aplicada`: para cada producto con `maneja_lotes`, si el inventario tiene más que la suma de sus lotes, crea el lote `SIN-LOTE` (sin vencimiento) con la diferencia. Si los lotes suman más que el inventario, solo lo reporta, sin tocar nada.

**Back, un único servicio `LoteStockService`:**
- `entrar(producto, sucursal, codigo, vencimiento, cantidadBase, costo, origen)`: crea o suma al lote.
- `salirFefo(producto, sucursal, cantidadBase, lotesElegidos?)` → `List<(lote, cantidad)>`. Con lotes elegidos los respeta; si no, reparte por vencimiento ascendente (sin vencimiento al final) y salta los vencidos si la regla lo pide (ver decisión 2).
- `reversar(asignaciones)`: devuelve lo que salió a los mismos lotes.
- Lo usan todos los documentos. `inventario` y `lote` se mueven en la misma transacción.

**Pantalla Inventario › Lotes:** deja de crear stock. Queda como consulta: lote, vencimiento, stock, costo, días para vencer, compra de origen. Solo permite editar el código y la fecha de vencimiento, con un motivo que queda en la auditoría.

---

## F2 · La compra crea los lotes

**Front (`form-compra`):** si el producto maneja lotes, la línea muestra un enlace **"Lotes (0/4 bultos)"**. Abre una mini tabla:

```
Código lote     Vence        Cantidad
L2409-A         2027-03-01   3
L2409-B         2027-05-15   1        → suma 4 = cantidad de la línea ✔
```

- La cantidad se escribe en la presentación de la línea (bultos); el back la pasa a base.
- **Atajo:** con un solo lote basta llenar código y fecha en la misma fila, sin abrir nada.
- No deja guardar la compra si la suma no cuadra.

**Back:** `compra_detalle_lote`; `LoteStockService.entrar` por cada fila, con el costo de la línea. **Editar y anular** reversan los lotes; si de ese lote ya se vendió, se bloquea con "El lote L2409-A ya tiene salidas: haz una nota crédito".

**Nota crédito a proveedor:** sale de los lotes que eligió el usuario, o FEFO.

**Aceptación:** compra de 4 bultos de arroz en 2 lotes → 200 kg en inventario, lote A 150 kg y lote B 50 kg; anular la compra deja ambos en 0 y el inventario igual que antes.

---

## F3 · Estado 2026-09-16 — ✅ HECHO (sin commit; V165/000165 corrida solo en local)

**Hecho:**
- **V165:** `documento_lote` (origen, detalle_id, lote_id, cantidad_base), una sola tabla para todas las salidas y entradas que no son compra; `empresa.lotes_bloquear_vencidos` (true) y `empresa.lotes_dias_alerta` (30).
- **`LoteStockService`:** `salidaDocumento` (elegido o FEFO, vencidos según regla, faltante permitido si el producto admite negativo), `entradaDocumento` (SIN-LOTE por defecto), `entradaEspejo` (traslado), `entradaDevolucion` (vuelve a los lotes de la venta, primero al que vence más tarde), `revertirDocumento` y `kardex` (un movimiento por lote).
- **Integrado en:**

| Documento | Crear | Anular |
|---|---|---|
| Venta (simple y con receta) | FEFO; bloquea vencidos | Devuelve a los mismos lotes |
| Merma | FEFO, **incluye vencidos** | Devuelve |
| Obsequio y consumo interno | FEFO; bloquea vencidos | Devuelve |
| Componentes de receta (merma, obsequio, consumo interno) | FEFO; la merma incluye vencidos | Devuelve |
| Traslado | Sale FEFO (incluye vencidos) y entra al destino con el mismo código y vencimiento | Devuelve al origen y retira del destino (bloquea si ya se usó) |
| Devolución | A los lotes de la venta, o SIN-LOTE | Retira |
| Cambio en devolución | FEFO; bloquea vencidos | — |
| Reconteo | Faltante FEFO / sobrante a SIN-LOTE / lote contado | — |
| Ajuste directo de inventario (`PUT /inventario/{id}`) | Baja FEFO / sube a SIN-LOTE | — |

- **Pasar a unidad:** también multiplica `documento_lote` y `compra_detalle_lote`.
- **Endpoints:**
  - `GET/PUT /lotes/reglas`.
  - `/lotes/disponibles` trae `diasParaVencer` y ordena con los sin vencimiento al final.
  - `/productos/pos` trae `manejaLotes`, `proximoVencimiento`, `diasParaVencer`, `stockVencido`, `diasAlertaVencimiento` y `bloquearVencidos`.
- **Por vencer (dashboard y panel):** usan los días de la empresa e incluyen los vencidos con stock.
- **Front:**
  - **POS:** chips "Vence en N d" y "N vencido", aviso al agregar al carrito, y el stock vendible descuenta lo vencido.
  - **Merma, obsequio, consumo interno y traslado:** URL de lotes corregida (daba 404); selector opcional "Automático (vence primero)" con el vencimiento en la etiqueta; ya no exige lote.
  - **Lotes:** botón "Reglas".
  - **Dashboard:** "Vencido".

**Probado en local (9002, empresa 4, VINIPEL; VIN-C forzado a vencido):**
- **Venta de 3:** salió de SIN-LOTE, sin tocar el vencido.
- **Venta de 98 con 97 vendibles:** "faltan 1 (hay 5 en lotes vencidos)".
- **Merma de 2:** salió de VIN-C vencido. Obsequio y consumo interno salieron de SIN-LOTE.
- **Las 4 anulaciones:** devolvieron a los mismos lotes; kardex por lote; inventario = lotes en cada paso.
- **Ajuste directo:** a 101 y de vuelta a 105, cuadra.
- **Endpoints:** POS, reglas y disponibles responden bien.

**No probado:**
- **Traslado:** la empresa 4 tiene una sola sucursal.
- **Devolución, reconteo y receta con componente con lotes.**
- **Front en el navegador:** solo se compiló.

**Encontrado, fuera de alcance:**
- **Anular devolución:** no reversa los productos agregados en el "cambio".
- **Devolución de producto con receta:** reintegra el padre y no los componentes.
- Ambos ya existían.

**Gotcha visto dos veces:** el IDE deja clases "Unresolved compilation" en `target/classes`. Hubo además un campo duplicado real que Maven no detectó porque no recompiló. Antes de probar: `touch` de los cambiados + `mvnw -o compile` completo.

## F3 · Las salidas descuentan lotes solas (venta, merma, obsequio, traslado)

**Venta (POS):**
- Sin cambiar el flujo: al cobrar, el back reparte cada línea por FEFO y guarda `venta_detalle_lote`.
- **Aviso en el carrito** si el primer lote vence en ≤ N días (configurable) y **bloqueo o aviso** si solo queda stock vencido (decisión 2).
- **Opcional:** botón "Lote" en la línea para elegirlo a mano (farmacias). El código del lote no se imprime en la tirilla salvo que se configure.
- **Anular y devolver:** reversan a los mismos lotes. En la devolución parcial, a los lotes de vencimiento más lejano primero.

**Merma, obsequio:** columna Lote con "Automático (FEFO)" por defecto y lista de lotes con su vencimiento. La merma por motivo "Vencido" propone los lotes vencidos.

**Traslado:** la salida es FEFO o elegida; la entrada crea en destino el mismo código con el mismo vencimiento y costo.

**Reconteo:** los productos con lote se cuentan por lote; la diferencia ajusta cada lote y el total.

**Recetas:** los componentes con lote salen por FEFO.

**Aceptación:** vender 160 kg con lote A (150, vence antes) y B (50) → A queda en 0 y B en 40; anular la venta los deja otra vez en 150 y 50.

---

## F4 · Estado 2026-09-16 — ✅ HECHO (sin commit; V166/000166 corrida solo en local)

**Hecho:**
- **V166:**
  - **`serial_producto`:** + empresa_id, costo, fecha_ingreso, compra_detalle_id, documento_salida_tipo/id y garantia_cliente_hasta.
  - **Unicidad:** **se quitó el UNIQUE(serial) global** (bloqueaba el mismo IMEI entre empresas) y queda `uq_serial_producto` (producto, UPPER(TRIM(serial))).
  - **Tablas y columnas nuevas:** `producto.meses_garantia` y `documento_serial` (origen, detalle_id, serial_id, estado_anterior, sucursal_anterior_id). La venta sigue usando `venta_detalle_serial`.
- **`SerialStockService`:**
  - **Compra:** `entradaCompra` (uno por unidad, sin repetidos) y `revertirEntradaCompra` (borra solo si no tiene historial).
  - **Salidas:** `salida` y `revertirSalida` para merma, obsequio, consumo interno y nota crédito.
  - **Venta:** `venta` (VENDIDO + garantía) y `anularVenta`.
  - **Devolución:** `devolucion` (DISPONIBLE si reintegra, EN_GARANTIA si no).
  - **Traslado:** `traslado` y `revertirTraslado`.
- **Integrado en:** compra (crear, editar, anular), nota crédito, venta (ahora **exige** los seriales), merma, obsequio, consumo interno, traslado y devolución. Un producto con serial agregado en el "cambio" de una devolución se rechaza: se vende desde el POS.
- **Endpoints:** `GET /seriales/buscar?codigo=`, `GET /seriales/venta-detalle/{id}` y `GET /seriales/trazabilidad?serial=` (compra, venta, devolución, merma, obsequio, consumo interno y traslado en orden). `POST /seriales/create` solo si hay stock sin serial.
- **`manejaSerial` en:** `/productos/inventario`, listado de productos, detalle de compra (con `seriales`/`serialIds`) e ítems acreditables.
- **Front:**
  - **Componentes compartidos:** `serial-picker` (escanear o marcar; disponibles o vendidos de una línea) y `serial-entrada` (escanear, pegar lista, generar consecutivo).
  - **Compra y nota crédito:** botón "Seriales x/N".
  - **POS:** escanear un serial agrega el producto con ese serial; botón en la línea; no deja cobrar sin seriales.
  - **Merma, obsequio, consumo interno, traslado y devolución:** botón de seriales.
  - **Pantalla Seriales:** columnas compra, costo y garantía, más diálogo de **Trazabilidad**.
  - **Producto:** "Meses de garantía".
- **Arreglado de paso:** el form de traslado buscaba productos en `inventario/disponible`, que no existe (404). Ahora usa `/productos/inventario`.

**Probado en local (9002, empresa 4, producto 89, garantía 12 meses):**
- **Compra:** 2 seriales para 3 unidades rechazado; 3 IMEI creados con costo y compra de origen; serial repetido (en minúsculas) rechazado.
- **Venta:** sin serial rechazada; con IMEI-001 queda VENDIDO con garantía 2027-09-16; buscar por código lo encuentra.
- **Merma:** IMEI-002 queda en MERMA.
- **Anular compra con serial vendido:** bloqueado.
- **Devolución sin reintegro:** EN_GARANTIA.
- **Anulaciones:** devolución, merma y venta dejan los 3 DISPONIBLES.
- **Trazabilidad:** compra → venta → devolución.
- **Nota crédito con IMEI-003:** DEVUELTO_PROVEEDOR; anular vuelve a DISPONIBLE.
- **Listados y registro manual:** OK.

**No probado:**
- **Traslado:** una sola sucursal.
- **Front en el navegador:** solo se compiló.

**Límites conocidos:**
- **Reconteo y ajuste manual:** en productos con serial no tocan seriales (no se sabe cuáles), así que pueden descuadrar DISPONIBLES contra stock.
- **Recetas con componentes seriados:** no piden seriales.
- **Compra con historial:** una compra cuyos seriales tuvieron historial no se anula aunque hayan vuelto a DISPONIBLE: se hace nota crédito.

## F4 · Seriales de punta a punta

**V165:**
- `serial_producto`: `empresa_id`, `compra_detalle_id`, `costo`, `fecha_ingreso`, `garantia_proveedor_hasta`, `documento_salida_tipo` / `documento_salida_id`, `garantia_cliente_hasta`, y más estados: `DISPONIBLE`, `VENDIDO`, `EN_GARANTIA`, `DEVUELTO_PROVEEDOR`, `DADO_DE_BAJA`, `CONSUMO_INTERNO`, `TRASLADO`.
- Único `(empresa_id, producto_id, serial)`.
- `producto.meses_garantia` (opcional).
- Tablas puente `merma_detalle_serial`, `obsequio_detalle_serial`, `traslado_detalle_serial`, `compra_detalle_serial` y `devolucion_detalle_serial`. `venta_detalle_serial` ya existe.

**Compra:** si el producto maneja serial, la línea exige **N seriales = cantidad base** (entera). Mini tabla para escanear uno tras otro con Enter, pegar una lista o "generar consecutivo" (IMEI-001…). Crea los seriales DISPONIBLES con costo y compra de origen.

**POS:**
- **Escanear un serial** en el buscador agrega el producto con ese serial.
- Agregar un producto con serial sin escanear pide los seriales antes de cobrar; la línea muestra "2/3 seriales".
- Guarda `garantia_cliente_hasta = fecha venta + meses_garantia`.

**Anular, devolver:** solo los seriales de ese documento. La devolución pregunta el destino: **disponible** o **en garantía / defectuoso**.

**Merma, obsequio, traslado, consumo interno, nota crédito proveedor:** eligen seriales DISPONIBLES de la sucursal (escaneo o lista).

**Pantalla "Trazabilidad de serial":** buscar un serial y ver compra (proveedor, fecha, costo) → movimientos → venta (cliente, factura, garantía hasta) → devoluciones.

**Aceptación:** compra de 3 celulares con 3 IMEI → 3 DISPONIBLES y stock 3; vender escaneando 1 IMEI → VENDIDO con garantía; anular → solo ese vuelve a DISPONIBLE; nunca hay más DISPONIBLES que stock.

---

## F5 · Consumo interno (el "descargue de inventario") — ✅ HECHO 2026-09-16 (sin commit; V163 corrida solo en local)

**Decisiones tomadas** (el usuario dijo "sigamos" sin elegir; se usaron las propuestas): el concepto trae el IVA activado y el documento lo puede cambiar; lotes vencidos bloqueados, FEFO automático y seriales obligatorios en compra quedan para F1–F4.

**Hecho:**
- **V163 + Laravel 000163:** `concepto_consumo_interno` (cuenta opcional, IVA por defecto, activo; nombre único por empresa sin mayúsculas), `consumo_interno`, `consumo_interno_detalle`, origen `CONSUMO_INTERNO` en `inventario_consumo_componente`, `producto_cambio_unidad.ultimo_consumo_interno_id` y 6 conceptos por empresa. Las empresas nuevas los reciben al abrir la pantalla.
- **Back:** `ConsumoInternoServiceImpl`, con la misma estructura del obsequio (presentación, receta, lote validado, stock, kardex `CONSUMO_INTERNO` / `ANULACION_CONSUMO_INTERNO`, bloqueo por cambio de unidad). Controlador `/api/consumos-internos` (page, id, create, anular, conceptos GET/POST/PUT). La cuenta del concepto se valida como la configuración contable: activa, auxiliar y 5xx o 15xx.
- **Contabilidad:** `ConsumoInternoGenerador` (prefijo CI): Db cuenta del concepto (o `GASTO_CONSUMO_INTERNO` = 5195) / Cr inventario; con IVA, Db 529505 / Cr IVA generado.
- **Kardex:** familia `CONSUMOS_INTERNOS`, columna "Consumo interno" en el Excel resumen y en el reporte gerencial.
- **Front:** `/consumo-interno` (listado, formulario, detalle y diálogo de conceptos con cuenta, IVA Sí/No y activo), ítem "Consumo interno" en Inventario. SQL del menú en `docs/sql/menu_submodulo_consumo_interno.sql`, corrido en local.

**Probado en local (backend en 9002 con token de prueba):**
- **Crear:** empresa 4, 3 × VINIPEL (costo $3.150, 19%) → stock 100 → 97, kardex −3, asiento CI-000001 cuadrado (Db 5195 $9.450 / Cr 1435; Db 529505 $2.155,46 / Cr 240801).
- **Validaciones:** cuenta 1105 rechazada, nombre repetido rechazado, anular dos veces rechazado.
- **Anular:** stock vuelve a 100 y el kardex queda con la entrada.
- **Arreglado en la prueba:** guardar concepto devolvía la cuenta vieja (faltaba `saveAndFlush`).

**Encontrado, fuera de alcance:**
- **Asiento de una empresa en modo revisión:** nace en BORRADOR, y anular el documento no lo toca, porque `reversar` solo busca CONTABILIZADO. Pasa con todos los documentos.
- **Empresa 1 local sin PUC:** su posting falla por la 4135.
- **Lotes en los forms de merma y obsequio:** llaman `lotes/disponibles?productoId=` y el endpoint es `/disponibles/{producto}/{sucursal}` (404). El form de consumo interno ya usa la ruta correcta.
- **No probado en el navegador:** la pantalla solo se compiló.


**Para qué:** el negocio usa su propio inventario, por ejemplo:
- una ferretería gasta tornillos en una reparación del local;
- un supermercado usa bolsas y productos de aseo;
- una tienda toma un producto para la cafetería del personal;
- un taller retira una herramienta para usarla.

**No es merma** (no hay pérdida), **ni obsequio** (no sale a un tercero).

**Por qué documento aparte:**
- **La cuenta:** el gasto va a una cuenta según el uso (aseo, cafetería, mantenimiento), no a pérdidas.
- **Los activos:** si lo retirado es un activo (una herramienta, un equipo), va a propiedad, planta y equipo.
- **El IVA:** el art. 421 lit. b del E.T. trata el **retiro de bienes para uso propio** como venta para efectos de IVA, salvo excepciones (por ejemplo, cuando el bien se usa como insumo de una producción gravada). Por eso cada concepto tiene su propia regla de IVA. **Validar con el contador del cliente.**

**V163:**
- `concepto_consumo_interno` (empresa, nombre, `cuenta_gasto_id` o `cuenta_activo_id`, `genera_iva_por_defecto`, activo). Semilla: Aseo y cafetería, Mantenimiento del local, Uso en exhibición, Activo fijo, Otro.
- `consumo_interno` (empresa, sucursal, usuario, fecha, `concepto_id`, `responsable_tercero_id` opcional (empleado que lo retira), `centro_costo` / dimensión si está activa, observación, costo_total, base_comercial_total, iva_total, genera_iva, estado).
- `consumo_interno_detalle`: producto, presentación, `cantidad_base`, `cantidad_presentacion`, costo_unitario, base comercial, IVA. Tablas puente de lote y serial.
- `TipoMovimientoInventario.CONSUMO_INTERNO` (SALIDA) y `ANULACION_CONSUMO_INTERNO` (ENTRADA), familia nueva `CONSUMOS_INTERNOS` en el reporte de kardex.

**Back:** `ConsumoInternoServiceImpl`, copiando la estructura de `ObsequioServiceImpl`: presentación, receta, lote FEFO, seriales, validación de stock, anular.

**Contabilidad:** `ConsumoInternoGenerador` por evento, como merma y obsequio.
```
Db  cuenta del concepto (gasto o activo)     costo
Cr  inventario (cuenta de la categoría)      costo
— si genera IVA —
Db  IVA asumido en consumo interno (gasto)   IVA
Cr  2408 IVA generado                        IVA
```
Anular publica la reversa. Entra al cierre de caja solo como información (no mueve dinero). Se agrega al reporte gerencial y al de gastos.

**Front (`inventario/consumo-interno`):** listado, formulario y detalle con el mismo diseño de merma: líneas con cantidad + presentación en la misma fila, lote automático, seriales si aplica. Encabezado: **Concepto**, **Responsable** (opcional), **¿Genera IVA?** (botones Sí/No, precargado por el concepto), observación. SQL del menú en `docs/sql/menu_consumo_interno.sql`.

**Aceptación:** consumo interno de 2 kg de arroz para cafetería con costo $1.300 → kardex −2 kg tipo "Consumo interno", asiento Db 5195xx / Cr 1435 por $2.600; con IVA, además Db gasto IVA / Cr 2408 sobre el valor comercial; anular deja inventario, lotes y asiento en cero.

---

## F6 · Estado 2026-09-16 — ✅ HECHO (sin commit; sin migración)

**Hecho:**
- **Back:**
  - `GET /lotes/vencimientos?sucursalId=&dias=` devuelve lotes con stock vencidos o que vencen en N días (por defecto, los días de alerta de la empresa), con `valorCosto` (stock × costo del lote) y `valorVenta` (stock × precio).
  - `GET /productos/inventario/id/{id}?sucursalId=` trae el producto de inventario, para prellenar la merma.
- **Front, pantalla Lotes:** el panel lateral "Por vencer" pasa a ser el diálogo **"Vencimientos"**:
  - **Filtros:** sucursal (por defecto la del usuario) y días (por defecto los de la regla).
  - **Tarjetas:** $ vencido y $ por vencer, en costo y en venta.
  - **Tabla con selección:** botón "Elegir los vencidos" y **"Hacer merma (N)"**, que navega a Mermas con los lotes elegidos (una sola sucursal).
- **Front, Mermas:** abre el formulario ya lleno (producto, lote elegido y todo su stock), en la sucursal de los lotes y con el motivo cuyo nombre contenga "venc" si existe.
- **Dashboard:** ya usaba los días de la empresa e incluía vencidos (F3).
- **De paso, en compra, merma, obsequio y consumo interno:** sin presentaciones la cantidad ocupa toda la celda (`.cant-pres > p-inputnumber:only-child`).

**Probado:** los endpoints con curl (VIN-C vencido hace 15 d, $3.000 costo, $4.500 venta; días fuera de rango rechazados; id inexistente responde 404). El front solo se compiló.

**No hecho:**
- **Correo diario:** era opcional, falta que el usuario decida.
- **Kardex:** filtro por lote en la pantalla (el reporte ya agrupa por lote).
- **"Trasladar":** desde vencimientos.

## F6 · Vencimientos y reportes

- **Reporte "Lotes por vencer y vencidos":** filtros sucursal, categoría y días; muestra cantidad, costo inmovilizado y precio de venta. Botones "Hacer merma" (precarga los vencidos) y "Trasladar".
- **Aviso diario** en el dashboard (reutiliza `LoteVencimientoDto`, ahora con datos reales).
- **Kardex:** columna Lote y filtro por lote.
- **F7 del plan de presentaciones:** el stock por lote también se muestra como "3 Bulto + 20 kg".

---

## Orden recomendado
1. **F0**: bugs de seriales y lotes entre empresas + SQL de diagnóstico. Es corto y cierra un riesgo real.
2. **F5 Consumo interno**: no depende de lotes (nace con FEFO desactivado y se conecta en F3). Es lo que el negocio pidió usar ya.
3. **F1 + F2**: lotes que cuadran y la compra que los crea.
4. **F3**: salidas por FEFO.
5. **F4**: seriales.
6. **F6**: reportes.

Cada fase: migración Flyway + espejo Laravel idempotente, compilar back y front, probar en `aura-pos` local. **Nada en `aura-db` (prod) sin backup.**

## Riesgos
- **Productos con `maneja_lotes` y stock real:** el cuadre de V164 crea `SIN-LOTE`. Correr el diagnóstico F0 antes para saber cuántos son.
- **Ventas sin conexión o pestañas abiertas del POS:** no mandan asignación de lotes, pero no hace falta porque el back asigna por FEFO.
- **Cambio de unidad (F1b de presentaciones):** ya multiplica `lote.stock_actual`; también tendrá que convertir las tablas puente nuevas.
- **Factura electrónica:** algunos sectores (droguerías) exigen lote y vencimiento en la factura. Revisar el mapeo a Factus en F3.
- **Rendimiento del POS:** FEFO se calcula en el back al cobrar, no al agregar al carrito.

## Decisiones abiertas
1. **Consumo interno e IVA:** ¿el concepto trae el IVA activado por defecto (propuesto, según art. 421) o desactivado y que el contador lo encienda?
2. **Lotes vencidos en la venta:** ¿bloquear (propuesto para droguerías y alimentos) o solo avisar? Propuesta: configurable por empresa, bloqueo por defecto.
3. **Elegir lote en el POS:** ¿solo FEFO automático (propuesto) o permitir que el cajero lo cambie?
4. **Seriales en compra:** ¿obligatorios para guardar la compra (propuesto) o permitir guardarla y cargarlos después con estado "pendiente de seriales"?
