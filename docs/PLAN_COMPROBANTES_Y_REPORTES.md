# Plan · Comprobantes manuales + Kardex y reportes operativos

> **Estado 2026-09-06: PLAN COMPLETO** — A, B, C y D implementadas (back + front,
> compilando, 32 tests nuevos en verde).
> Motor: PostgreSQL. Migraciones: **`V154`** (comprobante → origen de fondos, ya corrida)
> y **`V155`** (índices del kardex, **sin correr**). Las dos espejadas en Laravel.
>
> **Falta para poner todo en uso:** correr `V155` y los tres SQL de menú
> (`menu_submodulo_reporte_kardex.sql`, `..._gastos.sql`, `..._cartera.sql`).
> Nada de esto está commiteado todavía.
> Repos: backend `aura-back-old`, frontend `aura-frontend` (`D:\Proyectos Camilo\aura-post\aura-frontend`).

Cuatro trabajos que se piden juntos pero son de dos naturalezas distintas:

| Parte | Qué es | Riesgo |
|---|---|---|
| **A** · Cartera sin filtrar por beneficiario | Bug de UX | Bajo |
| **B** · El comprobante no entra al cierre de caja | **Hueco de control de efectivo** | **Alto** |
| **C** · Reporte de movimiento de inventario (kardex) | Funcionalidad nueva | Medio |
| **D** · Reportes de CxC, CxP y gastos | Funcionalidad nueva | Bajo |

Orden recomendado: **B → A → C → D**. B es el único que hoy produce plata sin rastro;
A es cosmético al lado de eso y se resuelve en el mismo archivo.

---

## Parte A · La cartera muestra todos los terceros — ✅ HECHO

### Qué pasa

En `form-comprobante-contable.component.ts:270` (`cargarCartera`) el cuerpo que se manda es:

```ts
const pageable = { page, rows, search: this.carteraSearch || null };
```

No lleva el tercero. El `beneficiarioTerceroId` que el usuario eligió arriba
(`form-comprobante-contable.component.html:46`) no viaja, y `onBeneficiarioChange()`
(línea 226) sólo autollena nombre y teléfono: **no recarga la cartera**.

Además, aunque se mandara, hoy no funcionaría igual en los dos lados — los dos endpoints
leen el filtro de sitios distintos:

| Endpoint | Dónde lee el tercero |
|---|---|
| `CuentasCobrarController.listar` (`/api/cuentas-cobrar/page`) | `@RequestParam clienteId` → **query string** |
| `CuentasPagarController.listar` (`/api/cuentas-pagar/page`) | `pageable.params.proveedorId` → **cuerpo** |

El front en ambos casos hace `http.post(url, pageable)`. O sea: en CxP el filtro entraría
si se manda dentro de `params`; en CxC se ignora en silencio, porque Spring no lee un
`@RequestParam` del body. Ese es el motivo real de que "aparezcan todos".

### La decisión

Unificar el contrato **en el backend** antes de tocar el front. `CuentasCobrarController`
pasa a aceptar el filtro también por `pageable.params` (manteniendo el `@RequestParam` por
compatibilidad con lo que ya lo use). Un solo contrato: filtros dentro del body.

### Fases

- **A1 · Backend, contrato único (CxC).** En `CuentasCobrarController.listar`, leer
  `fechaDesde/fechaHasta/clienteId/estado` de `pageable.getParams()` cuando no vengan como
  query param. Precedencia: query param gana (no romper llamadores actuales).
  De paso, quitar la reflexión muerta de `CuentasPagarController` (líneas 71–87: relee el
  mismo `params` que ya leyó arriba; es código que no aporta).
- **A2 · Front, mandar el filtro.** `cargarCartera` arma
  `{ page, rows, search, params: { estado: 'pendiente', clienteId|proveedorId: beneficiarioId } }`.
- **A3 · Front, reaccionar al beneficiario.** `onBeneficiarioChange()` limpia `this.sel`,
  llama `sincronizarCartera()` y recarga la cartera desde la página 0.
- **A4 · Front, no dejar elegir sin beneficiario.** Con RC/CE y sin beneficiario, la tabla
  de cartera muestra un vacío con texto *"Elige el beneficiario para ver sus cuentas"* en
  vez de listar el universo. Evita el otro bug latente: seleccionar cuentas de tres
  terceros distintos en un mismo comprobante cuyo beneficiario es un cuarto.
- **A5 · Front, coherencia al cambiar de beneficiario.** Si ya había cuentas seleccionadas
  y se cambia el beneficiario, se descartan con aviso (no se puede cruzar cartera de un
  tercero contra el comprobante de otro).

### Detalle que no hay que perder

`cargarCartera` filtra en cliente con `content.filter(c => c.saldoPendiente > 0)` pero
pagina en servidor: la página 3 puede quedar vacía aunque haya pendientes más adelante.
Mandar `estado` en el filtro (A2) lo corrige de raíz — que filtre el SQL, no el navegador.

### Lo que quedó implementado (2026-09-05)

Un hallazgo que el plan no tenía: el filtro `estado` de los dos QueryRepository solo
aceptaba `activa | vencida | pagada`, y **`activa` excluye las vencidas** — justo las
facturas que más se pagan. Se agregó el valor **`pendiente`** (saldo > 0 = activa +
vencida) en `CuentaCobrarQueryRepository` y `CuentaPagarQueryRepository`; es el que manda
la pantalla de comprobantes.

| Fase | Dónde |
|---|---|
| A1 | `CuentasCobrarController.listar` lee los filtros también de `pageable.params`; el query param sigue mandando. Un `clienteId` ilegible ahora es 400, no un filtro ignorado |
| A2 | `cargarCartera` manda `params: { estado: 'pendiente', clienteId\|proveedorId }` |
| A3 | `onBeneficiarioChange()` recarga la cartera desde la página 0 |
| A4 | Sin beneficiario la pestaña muestra un aviso en vez de la cartera de todos |
| A5 | Cambiar de beneficiario descarta la selección anterior con aviso |

---

## Parte B · El comprobante no aparece en el cierre de caja — ✅ HECHO

### Qué pasa (el mecanismo exacto)

`AsientoContableServiceImpl.crearComprobante` (línea 131) crea el asiento y, si vienen
`aplicaciones`, llama a `cuentaCobrarService.aplicarCruce` / `cuentaPagarService.aplicarCruce`.
Y `CuentaCobrarServiceImpl.aplicarCruce` (línea 253) construye el abono así:

```java
AbonoCobrarEntity.builder()
        .cuentaCobrar(cuenta)
        .usuario(usuario)
        .monto(monto)
        .metodoPago("COMPROBANTE")   // ← no es un medio de pago real
        .referencia(referencia)
        .fechaPago(LocalDateTime.now())
        .build();                     // ← sin turnoCaja
```

Del otro lado, `TurnoCajaServiceImpl.construirResumen` (línea 530) arma el cierre con
exactamente tres consultas, **todas por `turno_caja_id`**:

```java
abonoCobrarRepository.findByTurnoCajaIdOrderByFechaPagoAsc(turnoId)
abonoPagarRepository .findByTurnoCajaIdOrderByFechaPagoAsc(turnoId)
movimientoCajaRepository.findByTurnoCajaIdOrderByCreatedAtAsc(turnoId)
```

Un abono sin turno no está en ninguna de las tres. **El comprobante manual es hoy la única
vía por la que entra o sale plata sin dejar rastro en ningún arqueo.** Y no es un olvido de
un campo: el comprobante nunca pregunta de dónde sale la plata. La contrapartida es una
cuenta contable de disponible (`cuentasDisponibleOpts`, las 11xx), y una cuenta contable no
sabe si es el cajón de la sucursal 2 o la cuenta de Bancolombia.

Además `metodoPago = "COMPROBANTE"` es un valor inventado que `MediosPago.esEfectivo()` no
reconoce: aunque el abono llegara a tener turno, no sumaría al efectivo esperado.

### Por qué NO basta con "ponerle el turno del usuario"

Es la trampa que `OrigenFondosService` ya documenta y que este proyecto ya resolvió una vez:
**el dinero pertenece a una caja o a una cuenta, nunca a un usuario.** El comprobante lo
suele hacer el administrador, que no tiene turno; y aunque lo tuviera, un CE pagado por
transferencia no debe tocar arqueo alguno.

### La decisión

El comprobante **declara el origen de fondos**, igual que ya lo hacen compra, gasto y abono
(fases 1–4 de `PLAN_CAJA_FONDOS.md`). Se reutiliza `OrigenFondosService` tal cual está —
incluida la vía `CAJA_OTRO_DIA` de V151/V153, que aquí aplica idéntico: *"el cliente pagó
ayer en efectivo y hoy apenas se hace el recibo"*.

La cuenta contable de la contrapartida deja de elegirse a mano y **la resuelve el origen**:
hoy el usuario puede elegir "1105 Caja" en el dropdown mientras la plata entró por banco, y
nadie lo detecta.

| Origen declarado | Cuenta de contrapartida | ¿Entra al cierre de caja? |
|---|---|---|
| Caja (turno abierto) | La de caja del medio de pago | **Sí**, en ese turno |
| Cuenta bancaria | La de la cuenta bancaria | No (sí al extracto de tesorería) |
| Cuenta contable (caja menor) | La elegida, validada `es_medio_pago` | No |
| Caja, otro día | Caja | No — ese arqueo ya cerró cuadrado |

### Fases

- **B1 · DTO.** `CreateComprobanteDto` recibe `metodoPago`, `turnoCajaId`,
  `cuentaBancariaId`, `cuentaContableId`, `sucursalId`, `cajaOtroDia`.
  `AplicacionCarteraDto` no cambia.
- **B2 · Resolver el origen en `crearComprobante`.** Antes de armar el asiento, llamar a
  `origenFondosService.resolver(...)` con `documento = "comprobante " + tipo`. Sólo para
  CE y RC: el CD (nota de diario) no mueve plata y sigue igual.
- **B3 · Propagar el turno al cruce.** `aplicarCruce` recibe el `OrigenFondos` (o
  `turnoCajaId` + `metodoPago` + `cajaOtroDia`) y crea el abono con `turnoCaja` y
  `metodoPago` **reales**, y `cajaOtroDia` cuando aplique. Ahí es donde el comprobante
  entra al cierre: `construirResumen` lo encuentra sin tocar una línea.
  `metodoPago("COMPROBANTE")` desaparece; la trazabilidad de que vino de un comprobante ya
  la da `referencia = "Comprobante CE-000123"`.
- **B4 · Contrapartida derivada, no digitada.** La línea `origen: 'BANCO'` que hoy arma
  `sincronizarCartera()` en el front toma su `cuentaId` de lo que devuelva el origen. En el
  front el dropdown "cuenta de banco/caja" se reemplaza por el mismo selector de origen de
  fondos que ya usan gasto y compra (reutilizar el componente existente).
- **B5 · CE/RC sin cartera.** Un CE que no cruza ninguna cuenta (pago de un servicio suelto)
  hoy tampoco toca caja. Con origen `CAJA` debe generar un `MovimientoCajaEntity`
  `EGRESO`/`INGRESO` con `origenTipo = MANUAL` y referencia `"Comprobante <num>"`, igual
  que hace `TurnoCajaServiceImpl.registrarMovimiento`. Sin esto el hueco queda a medio
  tapar.
- **B6 · Anulación.** `AsientoContableServiceImpl.anular` (línea 237) hoy sólo pone
  `estado = ANULADO`: **no reversa el cruce de cartera ni el movimiento de caja**. Es un
  segundo bug del mismo módulo. Anular un comprobante debe revertir el abono (o crear su
  contrapartida) y el movimiento de caja, respetando la regla de no reabrir turnos cerrados
  (usar la vía de ajuste retroactivo de `PLAN_CAJA_FONDOS.md` fase 7).
- **B7 · Migración `V154`.** Columnas de trazabilidad en `asiento_contable`:
  `turno_caja_id`, `metodo_pago`, `cuenta_bancaria_id`, `caja_otro_dia`. Idempotente
  (`ADD COLUMN IF NOT EXISTS`), y **espejo en el proyecto Laravel**.
  ⚠️ Ojo con el gotcha conocido: `ADD COLUMN IF NOT EXISTS` no detecta tipo distinto y con
  `ddl-auto=validate` la app no arranca.
- **B8 · Datos históricos.** Los comprobantes ya hechos quedaron fuera de todos los cierres.
  No se reescriben arqueos cerrados. Se listan en el reporte de supervisión retroactiva
  (`SupervisionRetroactivaQueryRepository`) como "movimientos sin arqueo", para que el
  administrador los vea y decida.

### Lo que quedó implementado (2026-09-05)

| Fase | Dónde |
|---|---|
| B1 | `CreateComprobanteDto` → `metodoPago`, `turnoCajaId`, `cuentaBancariaId`, `cuentaContableId`, `sucursalId`, `cajaOtroDia` |
| B2 | `AsientoContableServiceImpl.crearComprobante` resuelve el origen para CE/RC; el CD queda intacto. Sin `metodoPago` → 400, en vez de caer en la cuenta por defecto |
| B3 | `aplicarCruce(..., origen, metodoPago)` en CxC y CxP: el abono nace con turno y método reales; `"COMPROBANTE"` solo sobrevive para la nota crédito de compra, que no mueve caja |
| B4 | `CreateAsientoDetalleDto.origen` (MANUAL/CARTERA/BANCO); la cuenta de la línea BANCO la pone el origen. En el front, el dropdown de contrapartida se reemplazó por el selector de origen de fondos de gasto/compra |
| B5 | `registrarMovimientoDeCaja`: la contrapartida **menos** lo aplicado a cartera — los abonos ya cuentan en el arqueo por su propio turno, sumarlos otra vez duplicaría la plata |
| B6 | `anular` revierte cruces (`revertirCrucesDeDocumento`, nueva en ambos servicios) y borra el movimiento de caja; con turno CERRADO devuelve 409 y remite al ajuste retroactivo |
| B7 | `V154__comprobante_origen_fondos.sql` + columnas en `AsientoContableEntity` + espejo Laravel `2026_07_03_000154_comprobante_origen_fondos.php` |
| B9 | **El CE/RC era el único movimiento de plata que no emitía su comprobante de caja**, así que no salía en la pantalla de Comprobantes — la misma desde la que se crea. Ahora lo emite por el total movido, **con el número del asiento**: la serie RC/CE es única para las dos tablas y pedir uno nuevo partiría el pago en dos documentos. Anular lo invalida conservando el número |
| B8 | Hallazgo `COMPROBANTE_SIN_ARQUEO` en `SupervisionRetroactivaQueryRepository`: los cruces históricos sin turno quedan inventariados, no se reescriben |

Tests: `ComprobanteOrigenFondosTest` (15, en verde). Los fallos de
`CuentaCobrarServiceTest` / `CuentaPagarServiceTest` son preexistentes — mismos
19/2/3 y 27/3/4 en HEAD.

**Pendiente antes de probar**: correr `V154` (`php artisan migrate` en
`aura-pos-migracion-old`). Con `ddl-auto=validate` el backend no arranca hasta que
las columnas existan.

El índice sobre `movimiento_caja (origen_tipo, origen_id)` que pedía B6 **no se
crea**: ya existe como `idx_mov_caja_origen` desde V145.

### Prueba de aceptación

1. Abrir caja. Hacer un RC en efectivo cruzando una CxC de $100.000.
2. El resumen del turno debe mostrarlo en el detalle y sumar $100.000 al esperado.
3. El mismo RC por transferencia: aparece en tesorería, **no** en el arqueo.
4. El mismo RC marcado "la plata entró otro día": no aparece en ningún arqueo, pero sí el
   asiento contra caja y sí en el listado de supervisión.

---

## Parte C · Reporte de movimiento de inventario (kardex) — ✅ HECHO

### Lo que ya existe

`KardexController` (`/api/kardex/page`) + `KardexQueryRepository.listar`. Es un **listado
paginado**, no un reporte: sin totales, sin agrupar por producto, sin exportar.

### Tres cosas rotas que hay que arreglar antes de construir encima

1. **Al front le faltan la mitad de los tipos de movimiento.** `kardex.model.ts` declara 9
   (`TIPOS_MOVIMIENTO_OPCIONES`); el backend escribe al menos 17:

   | Grupo | Tipos |
   |---|---|
   | Entradas | `COMPRA`, `TRASLADO_ENTRADA`, `DEVOLUCION`, `ANULACION_VENTA`, `ANULACION_MERMA`, `ANULACION_OBSEQUIO`, `EDICION_COMPRA`, `RECONTEO_AJUSTE_POSITIVO` |
   | Salidas | `VENTA`, `TRASLADO_SALIDA`, `MERMA`, `OBSEQUIO`, `DEVOLUCION_CAMBIO`, `NOTA_CREDITO_COMPRA`, `ANULACION_COMPRA`, `ANULACION_DEVOLUCION`, `EDICION_COMPRA_REVERSION`, `RECONTEO_AJUSTE_NEGATIVO` |
   | Mixto | `ANULACION_TRASLADO` (genera dos filas, una por sucursal) |

   Resultado: hoy **no se puede filtrar por merma, obsequio, devolución ni reconteo** desde
   la pantalla. Se ven en el listado pero no en el dropdown.

2. **El signo de `cantidad` no es homogéneo.** Casi todos los orígenes guardan la salida
   negada (`cantidad.negate()` en venta, merma, obsequio, traslado). Pero
   `ReconteoServiceImpl:211` guarda `diferencia.abs()` y distingue el sentido en el
   *nombre* del tipo (`RECONTEO_AJUSTE_POSITIVO` / `NEGATIVO`).
   **Sumar `cantidad` a ciegas da mal el reporte.** La regla correcta y única:
   `saldo_nuevo - saldo_anterior` — está en todas las filas y no depende del origen.
   Ese es el número que debe usar el reporte, y de paso valida las filas mal grabadas.

3. **El filtro `search` del front no existe en el backend.** `KardexFiltroDto` no tiene el
   campo: se manda y se descarta. Gotcha ya conocido de los QueryRepository con RowMapper
   manual, en su versión de entrada.

### Qué se construye

- **C1 · Sanear el catálogo de tipos.** Una única fuente: enum `TipoMovimientoInventario`
  en el backend con `grupo` (ENTRADA/SALIDA/NEUTRO), expuesto en
  `GET /api/kardex/tipos-movimiento`. El front deja de tener la lista hardcodeada.
  Los servicios dejan de escribir literales sueltos.
- **C2 · Filtros que faltan.** `KardexFiltroDto` recibe `search` (nombre/SKU/referencia),
  `categoriaId`, `marcaId`, `grupoMovimiento` (entrada/salida/neutro) y
  `tiposMovimiento: List<String>` (hoy es uno solo — no se puede pedir "todas las
  anulaciones").
- **C3 · Reporte agrupado por producto.** `POST /api/kardex/reporte`:
  una fila por producto (× sucursal si se pide), con
  `saldoInicial`, `entradas`, `salidas`, `saldoFinal`, `valorEntradas`, `valorSalidas`
  (usando `costo_historico`), y desglose por tipo de movimiento en columnas.
  `saldoInicial` = `saldo_anterior` del primer movimiento del rango;
  `saldoFinal` = `saldo_nuevo` del último. Se lee, no se calcula: así el reporte cuadra
  contra `inventario.stock_actual` y sirve para detectar filas huérfanas.
- **C4 · Detalle tipo kardex clásico.** `POST /api/kardex/reporte/detalle`: un producto,
  movimientos en orden cronológico con saldo corrido — la vista que pide un contador.
  Corregir el `ORDER BY m.id DESC` actual: para saldo corrido tiene que ser
  `created_at, id` ascendente.
- **C5 · Exportables.** Excel (POI, patrón de `ReporteInventarioService`) y PDF, colgados
  de `/api/reportes/kardex/excel` y `/pdf` para no partir en dos la convención de
  `ReporteController`.
- **C6 · Front.** Nueva pantalla `features/reportes/kardex` (la carpeta
  `features/reportes/inventario` está vacía, se aprovecha la estructura).
  Filtros: rango de fechas, sucursal, producto/categoría/marca, grupo y tipos (multi),
  agrupación producto | producto+sucursal | producto+lote.
  Toggle **resumen ↔ detalle**, totales al pie, botones Excel/PDF.
- **C7 · SQL de menú.** `docs/sql/menu_submodulo_reporte_kardex.sql`.
  ⚠️ Gotcha conocido: el menú **no aparece** si no se corre ese SQL.

### Lo que quedó implementado (2026-09-05)

| Fase | Dónde |
|---|---|
| C1 | `TipoMovimientoInventario` — enum con **grupo** (entrada/salida/mixto) y **familia** (el desglose del reporte), expuesto en `GET /api/kardex/tipos-movimiento`. Los 7 servicios dejaron de escribir literales y el front borró su lista |
| C2 | `KardexFiltroDto` recibe `search`, `categoriaId`, `marcaId`, `grupoMovimiento` y `tiposMovimiento` (lista) |
| C3 | `POST /api/kardex/reporte` — una fila por producto, con saldos leídos y desglose por familia |
| C4 | `POST /api/kardex/reporte/detalle` — kardex clásico, `created_at, id` **ascendente** |
| C5 | `ReporteKardexService`: Excel de las dos vistas + PDF del detalle. El resumen **no** tiene PDF: 23 columnas no se leen en una hoja |
| C6 | `features/reportes/kardex` — filtros, toggle resumen/detalle, totales al pie, Excel/PDF |
| C7 | `docs/sql/menu_submodulo_reporte_kardex.sql` (código `movimiento-de-inventario`) |
| — | `V155` + espejo Laravel: dos índices sobre `movimiento_inventario` |

Tests: `KardexCatalogoTest` (5). El primero es un **guard**: recorre los servicios
buscando literales dentro de `registrarMovimiento(...)` y falla si alguno no está
en el enum. Se verificó que detecta de verdad, no que pase por vacío.

**Dos correcciones que el plan no tenía:**

- El `getSeverity` del front clasificaba `ANULACION_COMPRA` como **entrada** cuando
  saca stock, y `ANULACION_TRASLADO` como salida cuando genera una fila de cada
  signo. Ahora el color sale del saldo, no del nombre del tipo — el mismo
  principio que el reporte.
- `KardexTableDto` no traía `producto_sku` aunque el front lo mostraba: salía
  vacío. Es el gotcha del RowMapper manual, en su versión de salida.

**Pendiente antes de usarlo**: correr `V155` y el SQL del menú (C7).

### Índice necesario

`movimiento_inventario` sólo se consulta hoy por `id DESC`. El reporte filtra por fecha +
producto + sucursal. En `V154` (o una V155 aparte):

```sql
CREATE INDEX IF NOT EXISTS idx_mov_inv_reporte
    ON movimiento_inventario (sucursal_id, producto_id, created_at);
```

Hecho en `V155`, junto con `idx_mov_inv_tipo_fecha` para el filtro por tipo.

---

## Parte D · Reportes de cuentas por cobrar, por pagar y gastos — ✅ HECHO

Los tres comparten la misma forma: filtros → resumen → detalle → Excel/PDF. Se construyen
como un solo trabajo con tres instancias, no como tres desarrollos.

### Lo que ya existe y no hay que rehacer

| Ya hecho | Dónde |
|---|---|
| Aging de cartera (edades) | `CarteraController /edades`, `/alertas`, `/dashboard` |
| Resumen CxC y CxP | `/api/cuentas-cobrar/resumen`, `/api/cuentas-pagar/resumen` |
| Balance de comprobación | `ReporteContableController` |
| Patrón Excel/PDF | `ReporteVentasService`, `ReporteInventarioService` |

Lo que falta no es el dato: es **el reporte imprimible con su detalle de abonos**.

- **D1 · Estado de cuenta por tercero (sirve a CxC y CxP).**
  `POST /api/reportes/cartera` con `tipo: CXC|CXP`. Por tercero: documentos, fecha,
  vencimiento, valor, abonado, saldo, días de mora y **el detalle de cada abono con su
  medio de pago y su turno**. Ese último dato es el que hoy no existe en ningún lado y es
  lo que cierra el círculo con la Parte B: quién recibió la plata y en qué caja cayó.
- **D2 · Reporte de gastos.** `POST /api/reportes/gastos`. `GastoEntity` ya tiene todo lo
  necesario: `categoria`, `tercero_id`, `centro_costo_id`, `cuenta_contable_id`,
  `forma_pago`, `metodo_pago`, `deducible`, `salida_caja_otro_dia`, `estado`.
  Agrupable por categoría | tercero | centro de costo | cuenta contable | mes.
  Columna **deducible / no deducible** separada: es la que pide el contador en renta.
- **D3 · Exportables.** Mismo patrón POI/PDF. Un único `ReporteCarteraService` y un
  `ReporteGastosService`.
- **D4 · Front.** `features/reportes/cartera` y `features/reportes/gastos`, misma
  estructura que la pantalla de kardex de C6 (filtros arriba, resumen, detalle expandible,
  export).
- **D5 · SQL de menú** para los tres.

### Lo que quedó implementado de D2 (2026-09-05)

| Pieza | Dónde |
|---|---|
| Consultas | `ReporteGastosQueryRepository` — resumen agrupable por categoría, tercero, centro de costo, cuenta, mes o sucursal; detalle uno a uno |
| Servicio | `ReporteGastosService` — Excel del resumen y del detalle |
| Endpoints | `POST /api/reportes/gastos/{resumen,detalle,excel,detalle/excel}` |
| Catálogo | `CategoriaGasto` (enum) + `GET /api/gastos/categorias` |
| Estilos Excel | `ExcelEstilos` — compartido, extraído de las dos copias que ya existían |
| Front | `features/reportes/gastos` — tarjetas de totales, resumen con barra de participación, detalle, Excel |
| Menú | `docs/sql/menu_submodulo_reporte_gastos.sql` (código `gastos`) |

Tests: `ReporteGastosServiceTest` (6). Leen de vuelta el `.xlsx` generado con POI,
así que cubren también el `ExcelEstilos` recién extraído.

**Tres decisiones que vale la pena recordar:**

- **Los totales salen de su propia consulta**, no de sumar las filas del resumen.
  Un pie que suma solo lo visible es una cifra que no cuadra con nada y que
  alguien va a copiar a una declaración.
- **Deducible y no deducible nunca se suman** — columnas separadas en las dos
  vistas, en el Excel y en las tarjetas. Es la partición de renta.
- **Un rango de fechas invertido es 400**, no un reporte vacío: cero gastos es
  indistinguible de "no hubo gastos" y alguien cerraría el mes creyendo que no
  gastó nada.

**Deuda que queda anotada:** el formulario de gasto sigue usando su constante
`CATEGORIAS_GASTO` en el front en vez del endpoint nuevo. Son dos fuentes para
el mismo catálogo — el mismo patrón que causó el problema del kardex.

**Pendiente antes de usarlo**: correr `docs/sql/menu_submodulo_reporte_gastos.sql`.
No hay migración: el reporte solo lee columnas que ya existían.

### Lo que quedó implementado de D1 (2026-09-06)

| Pieza | Dónde |
|---|---|
| Consultas | `ReporteCarteraQueryRepository` — **una sola** para CxC y CxP: las tablas son simétricas y se parametriza el nombre |
| Servicio | `ReporteCarteraService` — Excel del resumen por edades y del estado de cuenta |
| Endpoints | `POST /api/reportes/cartera/{resumen,documentos,excel,detalle/excel}` con `tipo: CXC\|CXP` |
| Front | `features/reportes/cartera` — tarjetas por edad, resumen por tercero, documentos con abonos desplegables |
| Menú | `docs/sql/menu_submodulo_reporte_cartera.sql` (código `estado-de-cuenta`) |

Tests: `ReporteCarteraServiceTest` (6), que leen de vuelta el `.xlsx` generado.

**Cuatro decisiones:**

- **El saldo se recalcula, no se lee.** `saldo_pendiente` es una columna
  denormalizada que actualizan los abonos; el reporte suma los abonos vivos. Si
  las dos cifras se separaron — un abono borrado a mano, una anulación a medias —
  el estado de cuenta muestra la realidad, que es lo que el tercero va a
  reclamar. Lo mismo con el estado: "vencida" depende de la fecha de hoy.
- **Una consulta para las dos caras.** Mantener dos SQL casi iguales garantiza
  que uno se quede atrás en la primera corrección.
- **El Excel del detalle fuerza `incluirAbonos`** aunque el filtro diga que no:
  un estado de cuenta sin abonos es una lista de saldos que el cliente no puede
  verificar.
- **Los abonos se traen en una sola consulta** para los documentos de la página.
  Uno por documento serían 51 viajes a la base en un reporte que se abre a diario.

**El dato que cierra el círculo con la parte B:** cada abono dice en qué caja
entró la plata. Antes de que los abonos de comprobante quedaran atados a su
turno, esa columna no habría tenido nada que mostrar.

**Pendiente antes de usarlo**: correr `docs/sql/menu_submodulo_reporte_cartera.sql`.
Sin migración: el reporte solo lee columnas existentes.

### Decisión de alcance

Los reportes leen; **no calculan saldos por su cuenta**. Si un saldo se ve mal en el
reporte, el bug está en quien lo escribió (abono, cruce, anulación), y ahí es donde se
arregla. Un reporte que "corrige" al vuelo esconde el problema de fondo.

---

## Dependencias y orden

```
B (origen de fondos en comprobante)  ──┐
                                       ├──> D1 (el estado de cuenta necesita el turno del abono)
A (filtro por beneficiario)         ───┘

C1→C2 (sanear kardex) ──> C3→C4 (reporte) ──> C5→C6→C7 (export y front)

D2 (gastos) es independiente: se puede hacer en paralelo desde el día 1.
```

## Riesgos

| Riesgo | Mitigación |
|---|---|
| B rompe comprobantes que hoy sí se guardan | El origen sólo se exige en CE/RC con contrapartida de disponible; CD intacto |
| Los abonos históricos sin turno se vuelven inconsistentes | No se tocan: se reportan (B8) |
| El reporte de kardex no cuadra por el signo de reconteo | Usar `saldo_nuevo - saldo_anterior`, nunca `SUM(cantidad)` (C, punto 2) |
| Las migraciones no corridas bloquean pruebas | V154 debe correrse en local **y** espejarse en Laravel antes de probar |
| El menú no aparece | Correr los SQL de C7 y D5 |
