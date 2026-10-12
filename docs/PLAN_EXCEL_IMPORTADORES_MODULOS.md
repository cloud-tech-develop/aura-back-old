# Plan — Excel en reportes, importadores, devoluciones en compras, causaciones y conciliación fiscal

> 2026-10-08. Orden aprobado: **1 → 2** ahora; 3, 4 y 5 después (4 y 5 con decisiones pendientes).

## Diagnóstico (código real, 2026-10-08)

| Pedido | Hoy |
|---|---|
| Excel en reportes | Faltaba en: Reporte gerencial, Balance general/EEFF, Estado de cuenta, Cartera, Cierre de turnos, Liquidación de nómina, Prestaciones, Liquidación de comisiones. El resto ya tiene (exceljs en el front, POI en el back). |
| Importar | Solo plan de cuentas, terceros, saldos y cartera (`ImportacionController`, validar/confirmar). Ninguna pantalla de catálogo ni inventario tiene "Importar". |
| Devoluciones en compras | Existe como **nota crédito de compra** (`CompraServiceImpl`, `tipoDocumento = NOTA_CREDITO`, `compraOrigenId`): devuelve kardex, lotes y seriales; destino cruce CxP / devolución de dinero / saldo a favor. Sin pantalla ni menú propios. |
| Causaciones | Existen **causaciones programadas** (`CausacionService`, asientos recurrentes en borrador). No hay documento de causación manual. |
| Conciliación fiscal | No existe. |

## 1. Excel en todos los reportes
- **Regla:** todo **reporte** se descarga en PDF y Excel; los **documentos** (factura, recibo, desprendible, comprobante) siguen solo en PDF.
- Componente común `ExcelReporteService` (front, exceljs): encabezado con empresa, NIT, título y rango; cabecera azul (#2563eb); formatos de pesos, número, porcentaje y fecha; filas de grupo y de total; varias hojas.
- Pantallas: gerencial, balance general/EEFF, estado de cuenta, cartera, cierre de turnos, liquidación de nómina, prestaciones, liquidación de comisiones.
- Todo reporte nuevo trae el botón Excel desde el inicio.

## 2. Importar en catálogo e inventario
- Botón **"Importar"** al lado de "Nuevo" en cada pantalla. Mismo flujo:
  1. **Descargar plantilla** (Excel: columnas, fila de ejemplo, hoja de instrucciones, listas desplegables).
  2. **Subir**: el back valida fila por fila y devuelve qué entra y qué tiene error ("fila 14: la categoría 'Bebidas' no existe").
  3. **Confirmar**: todo o nada. Si el registro ya existe (por código o nombre) se **actualiza** (decisión por defecto; confirmar con el usuario).
- Back: un endpoint `validar` y uno `confirmar` por catálogo, sobre el patrón de `ImportacionController`. El archivo se lee en el front (exceljs) y viaja como JSON de filas.
- Catálogo: categorías, marcas, unidades, productos (precio, costo, IVA, código de barras, categoría, marca, unidad), presentaciones, composiciones, precios por lista.
- Inventario: bodegas, **saldos iniciales** (cantidad y costo por bodega, con asiento de apertura), lotes con vencimiento, seriales, conteo físico (reconteo).
- Fases: **2a** catálogo base (categorías, marcas, unidades, productos); **2b** precios, presentaciones, composiciones; **2c** inventario (bodegas, saldos iniciales, lotes, seriales, conteo).

## 3. Devoluciones en compras (después)
Pantalla propia sobre el motor de nota crédito existente: menú Compras → Devoluciones (listado, consecutivo); nueva = compra original + líneas y cantidades (tope: comprado − ya devuelto), motivo, número de NC del proveedor, destino (cruce CxP / devolución de dinero / saldo a favor). La compra original muestra lo devuelto.

## 4. Causaciones (después — decisión pendiente)
(a) causación de gastos/servicios de proveedor con retenciones por línea y CxP (recomendada); (b) causación de ingresos sin factura con CxC; (c) ampliar las programadas.

## 5. Conciliación fiscal 2516/2517 (después — decisión pendiente: 2516, 2517 o ambos)
Mapeo PUC → renglones (por defecto + ajustable, como exógena); valor contable desde el mayor del año; el contador sube un Excel con ajustes fiscales (o el balance fiscal) y el sistema analiza (cuentas sin mapear, diferencias atípicas), calcula fiscal = contable ± ajustes, muestra ESF-Patrimonio, ERI-Renta líquida e impuesto diferido y exporta en la estructura del formato DIAN. Por año gravable, con cierre. Fecha natural: primer trimestre de 2027.

## Avance

### 1. Excel en reportes — hecho 2026-10-08 (local, sin commit)
- `shared/services/excel-reporte.service.ts`: encabezado empresa/NIT/título/rango, cabecera azul, pesos/números/%/fechas, filas de grupo y total, varias hojas, primera fila congelada.
- Botón Excel en: reporte gerencial (hallazgos + detalle), balance general, estado de cuenta (contabilidad), ficha del cliente en cartera (estado de cuenta + facturas abiertas), cierre de turno (resumen, por categoría, por medio de pago, movimientos), nómina del período (empleados + novedades), prestaciones (lista), liquidación de comisiones (servicios).
- No van a Excel (son documentos): desprendible, comprobante de prestación, factura, recibo.

### 2a. Importar catálogo base — hecho 2026-10-08 (local, sin commit)
- Back: `/api/importacion-catalogo/{categorias|marcas|productos}/{validar|confirmar}` (`CatalogoImportacionController`, `CatalogoImportacionService`, `CatalogoImportacionQueryRepository`), permiso de cada pantalla en `PermisoRutas`. `Resultado.actualizados` nuevo.
- Reglas: categoría y marca por nombre (sin tildes ni mayúsculas); producto por SKU, y por código de barras solo si la fila no trae SKU; si existe se **actualiza** solo con lo que traiga la fila; con existencias no se cambia el costo (costo promedio); categoría y marca inexistentes se crean; unidad por nombre o abreviatura (por defecto UNIDAD); IVA 0/5/19; clasificación PRODUCTO/SERVICIO; código de barras de otro producto = error; productos nuevos pasan por `ProductoService.crear` (sus validaciones y cuentas). Todo o nada.
- **Unidades de medida no se importan:** son un catálogo global de la plataforma (sin empresa).
- Front: `shared/components/importar-excel` (plantilla con instrucciones, lectura, validación automática al subir, tabla de resultados, importar) y botón "Importar" junto a "Nuevo" en categorías, marcas y productos.
- Pruebas: `CatalogoImportacionServiceTest` (12).

### 2b. Precios por lista, presentaciones y recetas — hecho 2026-10-08 (local, sin commit)
- Back: `/api/importacion-catalogo/{precios|presentaciones|recetas}/{validar|confirmar}` (`PreciosRecetasImportacionService`), permisos `precios.precio-productos`, `catalogo.presentaciones`, `catalogo.composiciones`. Todo pasa por los servicios de cada pantalla (`ProductoPrecioService`, `ProductoPresentacionService`, `ProductoComposicionService.guardarReceta`).
- Producto por SKU o código de barras (en precios, también el código de barras de una presentación).
- Precios: si ya hay precio en esa lista (producto o presentación) se cambia; la lista que no existe se crea; repetido en el archivo = error.
- Presentaciones: por (producto, nombre); nueva exige "Contiene" > 0; código de barras en uso = error.
- Recetas: las filas de un mismo producto arman la receta completa y **reemplazan** la que tenga; tipo RECETA/KIT, rendimiento, presentación del ingrediente, merma 0–99,99; componente repetido o el mismo producto = error.
- Front: botón "Importar" en Precios por presentación, Presentaciones y Recetas.
- Pruebas: `PreciosRecetasImportacionServiceTest` (10).

### 2c. Inventario — hecho 2026-10-08 (local, sin commit)
- Back: `/api/importacion-inventario/{bodegas|saldos|conteo}/{validar|confirmar}` (`InventarioImportacionService`), permisos `inventario.bodegas`, `inventario.stock`, `inventario.reconteos`.
- **Bodegas:** por (sede, nombre); sede por nombre o código, vacía = la sede de trabajo; código repetido = error; existentes se dejan.
- **Saldos iniciales:** solo en producto con inventario y **sin existencias en esa bodega** (si no: ajuste o reconteo); bodega vacía = la principal de la sede de trabajo; costo vacío = el del producto; una fila por lote (vencimiento y fabricación AAAA-MM-DD); seriales separados por coma, uno por unidad. Graba saldo, kardex (AJUSTE_MANUAL_ENTRADA "Saldo inicial importado desde Excel"), lotes (`LoteStockService.entradaDocumento`), seriales (`SerialStockService.entradaSaldoInicial`, origen nuevo SALDO_INICIAL) y costo promedio. **Sin asiento**: el valor contable va por los saldos iniciales contables (1435); el resultado muestra el valor importado para cuadrarlo.
- **Conteo físico:** en un reconteo abierto (botón "Cargar conteo (Excel)" del detalle): producto (+ lote si tiene varios), cantidad contada; muestra la diferencia contra el sistema; no ajusta el inventario: eso sigue siendo al aprobar.
- Front: botones en Bodegas, Inventario ("Importar saldos iniciales") y detalle del reconteo; `importar-excel` acepta columnas fecha, `extra` (reconteoId) y base `importacion-inventario`.
- Pruebas: `InventarioImportacionServiceTest` (11).

Punto 2 COMPLETO. Pendiente: 3 devoluciones en compras, 4 causaciones (decisión), 5 conciliación fiscal (decisión).

### 3. Devoluciones en compras — hecho 2026-10-08 (local, sin commit)
Sin migración ni SQL de menú: usa el permiso `compras.compras` y el motor de nota crédito existente.
- Back: `POST /api/compras/devoluciones/page` (filtros estado VIGENTE/ANULADA, desde, hasta, búsqueda por N° NC, compra, proveedor o motivo) y `GET /api/compras/{id}/devoluciones` (`DevolucionCompraDto`, importes en positivo). Selector de facturas acreditables acepta `params.compraId`.
- Corrección: la NC sin bodega sale de la bodega donde entró la compra (antes, de la principal). Editar una compra sin bodega conserva la que tenía.
- Front: Compras → **Devoluciones** (`/compras/devoluciones`, filtros, Excel, ver compra origen); "Nueva devolución" abre el formulario en modo nota crédito; en el detalle de una compra, sección Devoluciones + total devuelto + botón **Devolver mercancía** (abre el formulario con proveedor, sede, factura y saldos ya puestos). En modo NC el formulario titula "Devolución en compra", "N° nota crédito del proveedor" y "Motivo de la devolución"; al guardar vuelve al listado de devoluciones.

Pendiente: 4 causaciones (decisión), 5 conciliación fiscal (decisión).

### 4. Causaciones de gastos y servicios (opción A) — hecho 2026-10-08 (local, sin commit)
Decisión del usuario: (a) gastos/servicios con retenciones por línea y CxP.
- V201 `causacion_gasto` + `causacion_gasto_linea` (Laravel 2026_07_03_000201, corrida en local). No es la causación programada (E6).
- Back: `CausacionGastoService` + `CalculadoraCausacion` (IVA, retefuente por concepto con tarifa natural/jurídica según el tercero o tarifa a mano, base mínima medida sobre la suma del concepto en el documento, ReteIVA sobre el IVA, ReteICA sobre la base; todo 2 decimales). Crea UNA CxP por el neto (`cuenta_pagar_id`), consecutivo CG-n con candado, no repite factura del mismo proveedor, valida cuentas auxiliares (5/6/7/1; CxP clase 2) y período abierto.
- Asiento `CausacionGastoGenerador` (origen CAUSACION_GASTO, prefijo CG) por evento AFTER_COMMIT: DB gasto por línea con centro de costo (IVA no descontable al gasto), DB IVA descontable, CR retefuente a la cuenta del concepto o 2365, CR 2367/2368, CR CxP elegida o PROVEEDORES; tercero en todas.
- Anular: exige motivo, bloquea si la CxP tiene abonos, anula CxP y reversa con `ContabilidadReversaEvent`. No hay edición.
- Endpoints `/api/causaciones-gasto` (page, calcular, crear, get, anular) con el permiso `compras.compras` (sin SQL de menú).
- Front: Compras → **Causaciones** (listado con filtros y Excel), `/compras/causaciones/nueva` (cálculo en vivo con /calcular), ficha `/compras/causaciones/:id` con anulación en línea. Manual /ayuda actualizado (devoluciones y causaciones).
- Pruebas: `CalculadoraCausacionTest` (6), `CausacionGastoGeneradorTest` (2).
- Visto de paso (sin tocar): el gasto a crédito crea la CxP por `monto` (bruto) mientras su asiento acredita el neto.

### 5. Conciliación fiscal 2516 y 2517 — hecho 2026-10-08 (local, sin commit)
Decisión del usuario: los dos formatos.
- V202 `conciliacion_fiscal` (una por empresa, año y formato), `conciliacion_fiscal_cuenta` (foto del balance) y `conciliacion_fiscal_ajuste` (Laravel 2026_07_03_000202, corrida en local).
- `RenglonesFiscales`: renglones ESF (activos A01–A10, pasivos P01–P08, patrimonio T01–T08) y ERI (I01–I02, C01–C02, G01–G04) a nivel de rubro, por prefijo PUC (el más largo gana); 8, 9 y 59 quedan fuera (NA). No es la lista oficial de renglones del prevalidador: el contador lleva los valores allá.
- Balance: Excel con el importador estándar (`/api/conciliacion-fiscal/{id}/balance/validar|confirmar`; Cuenta + Saldo o Débito/Crédito; solo auxiliares; detecta si el saldo viene por naturaleza o débito positivo) o "Traer de la contabilidad" (ESF acumulado al 31-dic, ERI del año, sin CIERRE_ANUAL ni su reversa). Recargar reemplaza las cuentas y conserva los ajustes.
- `AnalizadorConciliacion`: contable por renglón (el resultado del año entra a T06), fiscal = contable − menor + mayor, cuadre del balance, cuentas sin renglón, ERI en cero (balance cerrado), menor valor > contable; sugerencias (renta 5405, multas/sanciones, 50 % GMF, donaciones, impuestos asumidos, "no deducible", valorizaciones 19/38, pasivos estimados 26 salvo 2610 y deterioro como temporarias "revisar"); renta líquida e impuesto a la tarifa; impuesto diferido de temporarias del ESF (activo contable > fiscal = imponible).
- Permiso `contabilidad.declaraciones` (sin SQL de menú). Front: Contabilidad → **Conciliación Fiscal** (listado + creación en línea) y ficha con pestañas Análisis / ESF / ERI / Balance (clasificar a mano) / Ajustes / Impuesto diferido, KPIs, Excel de 5 hojas, cerrar/reabrir, eliminar. Manual /ayuda actualizado.
- 2517: misma estructura; la renta de personas naturales va por la tabla del art. 241 (la tarifa es referencia). No separa rentas por cédulas.
- Pruebas: `AnalizadorConciliacionTest` (7). Probado contra la base local: el balance traído de la contabilidad de la empresa 4 cuadra (descuadre 0).

Plan COMPLETO (1 a 5).
