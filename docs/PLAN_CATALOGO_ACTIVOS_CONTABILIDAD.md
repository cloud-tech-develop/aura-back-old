# Plan: catálogo unificado, activos fijos y contabilidad para el contador (Fases 2–4)

Fecha: 2026-09-30 · Repos: `aura-back-old` (back) y `aura-frontend` (front) · Migraciones desde **V185**, espejo idempotente en Laravel (`aura-pos-migracion-old`).

Continúa el plan acordado el 2026-09-29 (F0 cerrar lo abierto y F1 integridad, ya hechas). Referencia funcional: World Office (video 1, catálogo; videos 2–4, contabilidad). No se copia: se adapta a lo que AURA ya tiene.

Decisiones del usuario (2026-09-30):
- **Impuestos múltiples por producto** (bolsa, ultraprocesados, bebidas azucaradas) **quedan fuera** de estas fases: bloque aparte, verificando antes las tarifas vigentes.
- **Activo fijo comprado con cantidad N → N fichas**, una por unidad.

---

## Fase 2 · Catálogo unificado

### Diagnóstico
| Tema | Hoy | Problema |
|---|---|---|
| Qué es el ítem | `tipo_producto` (ESTANDAR/KIT/PESABLE/SERVICIO) y `uso_producto` (VENTA/INSUMO/AMBOS) | No hay forma de decir "esto es un activo", "un gasto" o "un diferido" |
| Compra de cualquier línea | `CompraServiceImpl.crear` mueve inventario, lotes, kardex y costo promedio para **toda** línea | Comprar un servicio o un computador para la oficina sube stock que nunca se va a vender |
| Cuenta del débito de la compra | `ResolucionCuentaProducto.inventarioId()` → 1435 por defecto | Un activo o un gasto comprado cae en inventario salvo que se ponga la cuenta a mano en la cabecera |
| Activo comprado | Ficha manual en `/activos-fijos` | La compra no crea la ficha: doble digitación y cuentas mal puestas |
| Diferido | Solo desde el gasto (`gasto.es_diferido`) | Un seguro o una licencia comprados en la factura del proveedor no se difieren |

### Diseño

**Pantallas nuevas:** Contabilidad › Categorías Contables (`/contabilidad/categorias-contables`) y Compras › Sugerido de Compra (`/compras/sugerido`). El form de producto pregunta "¿Qué es este ítem?".

**Notas de implementación:**
- La nota crédito de compra no acepta líneas de activo/intangible/diferido (se anula la compra o se da de baja el activo).
- Las sueltas no se ofrecen en productos con lotes (los lotes se escriben en presentaciones completas).
- Los fletes del asiento de compra se reparten proporcionalmente entre las cuentas del débito (antes iban todos a la cuenta mayor).

**2.1 Clasificación del ítem (V185).** `producto.clasificacion`:

| Clasificación | Mueve inventario | Débito de la compra (por defecto) | Se vende en POS | Al comprar |
|---|---|---|---|---|
| PRODUCTO | Sí | Inventario 1435 (categoría) | Sí | Stock, kardex, costo promedio |
| SERVICIO | No | Costo 6135 (categoría) | Sí | — |
| GASTO | No | Gasto 5195 (`GASTO_GENERAL`) | No | — |
| DOTACION | No | 510551 Dotación (`GASTO_DOTACION`) | No | — |
| ACTIVO_FIJO | No | 1524 (`ACTIVO_FIJO_COMPRA`) | No | Crea **una ficha por unidad** |
| INTANGIBLE | No | 1635 (`INTANGIBLES`) | No | Crea ficha (amortización) |
| DIFERIDO | No | 1705 (`GASTOS_PAGADOS_ANTICIPADO`) | No | Crea el diferido y sus cuotas |

- La cuenta del débito sigue la cadena existente: override del producto → categoría contable → concepto de la empresa. Para las clasificaciones que no son PRODUCTO, `cuenta_inventario_id` (del producto o de la categoría) se lee como **"cuenta de la compra"**; el form cambia la etiqueta.
- La categoría contable gana los datos que la ficha necesita: `cuenta_depreciacion_id`, `cuenta_gasto_depreciacion_id`, `vida_util_meses`, `meses_diferido`.
- Datos existentes: `tipo_producto = 'SERVICIO'` → `clasificacion = 'SERVICIO'`; el resto queda PRODUCTO.
- Conceptos nuevos: `ACTIVO_FIJO_COMPRA` (1524), `INTANGIBLES` (1635), `DEPRECIACION_ACUMULADA` (1592), `GASTO_DEPRECIACION` (5160), `GASTO_DOTACION` (510551). V185 siembra en cada empresa las cuentas que falten (1524, 1528, 16, 1635, 1698, 510551).

**2.2 Compra según clasificación.** En crear, editar y anular:
- Solo PRODUCTO mueve inventario, lotes, seriales, kardex y costo promedio.
- ACTIVO_FIJO/INTANGIBLE: tras guardar la línea, N fichas (`activo_fijo.compra_id`, `compra_detalle_id`) con valor = neto de la línea / N, fecha = fecha de la compra, cuentas y vida útil de la categoría, proveedor como tercero, centro de costo de la compra.
- DIFERIDO: una fila en `diferido` (origen COMPRA) por el neto de la línea y los meses de la categoría.
- La contabilidad de la compra no cambia de forma: el débito ya va a la cuenta de la clasificación. La ficha **no** genera asiento de alta (ya lo hizo la compra).
- Anular o editar: si alguna ficha ya se depreció o el diferido ya amortizó, se bloquea con mensaje; si no, se eliminan y se recrean.

**2.3 Diferido generalizado (V185).** Tabla `diferido` (origen GASTO | COMPRA, monto, meses, fecha de inicio, cuenta de gasto, cuenta del diferido, tercero, centro de costo, estado). `diferido_amortizacion` gana `diferido_id`; `gasto_id` pasa a opcional. Los diferidos de gasto existentes siguen funcionando por `gasto_id`. `DiferidoService` amortiza los dos; el crédito de la cuota usa la cuenta del diferido (la que se debitó al comprar) y no siempre la 1705.

**2.4 Conversiones.**
- Compra: "4 pacas + 2 und" en **una** línea (`compra_detalle.cantidad_suelta`): cantidad base = presentación × factor + suelta.
- Servicios: se permiten presentaciones en SERVICIO (Año = 12 meses) — misma mecánica "contiene N".
- F7 de presentaciones: stock "3 cajas + 4 und" en listado de productos e inventario (pipe del front).

**2.5 Reorden por bodega.** `inventario.stock_maximo` y `inventario.punto_reorden`. Reporte "Sugerido de compra": productos con stock ≤ punto de reorden (o ≤ mínimo), sugerido = máximo − stock.

---

## Fase 3 · Activos fijos completos

**3.1 Depreciación correcta.**
- Línea recta: cuota = (costo − residual) / vida útil, **fija**; hoy divide lo que queda por la vida total y la cuota decrece.
- Empieza el mes siguiente a `fecha_inicio_depreciacion` (por defecto la fecha de adquisición); no deprecia meses anteriores al inicio ni del mismo mes de compra.
- Saldo decreciente (doble) y unidades de producción (con la lectura del mes) como métodos alternos.
- Tope: nunca deprecia por debajo del residual; la última cuota absorbe el redondeo.
- Asiento por el motor (tipo DEPRECIACION), un comprobante por período con una línea por activo agrupada por cuentas.
- Reversar la depreciación de un período (si el período está abierto).

**3.2 Ficha completa (V186).** Placa, serial, marca, modelo, responsable (empleado/tercero), ubicación, póliza (aseguradora, número, vence), activo padre, fecha de inicio de depreciación, compra de origen. Tablas `activo_fijo_mantenimiento` (fecha, tipo preventivo/correctivo, costo, proveedor, descripción, próximo) y `activo_fijo_adicion` (mejora que aumenta el costo y opcionalmente la vida útil, con su asiento).

**3.3 Baja y venta.**
- Baja: asiento DB depreciación acumulada + DB pérdida (5310) · CR costo del activo. Estado DADO_DE_BAJA.
- Venta: DB caja/banco/cliente por el precio · DB depreciación acumulada · CR costo · CR 4245 utilidad o DB 5310 pérdida; IVA generado si aplica. Estado VENDIDO.
- Conceptos: `PERDIDA_BAJA_ACTIVOS` (531015), `UTILIDAD_VENTA_ACTIVOS` (424504).

**3.4 Informes.** Listado de activos con costo, depreciación acumulada y valor en libros por categoría, centro de costo y responsable; Excel. Proyección de depreciación.

---

## Fase 4 · Contabilidad para el contador

**4.1 Pantalla de parametrización** (`/contabilidad/parametrizacion`): conceptos → cuentas (el back ya existe: `ConfiguracionContableService`), formas de pago, categorías contables (incluye las cuentas de activos/diferidos de la Fase 2) e impuestos; con el historial de cambios.

**4.2 Vista previa del asiento.** `POST /contabilidad/vista-previa/{tipo}` con el documento sin guardar: corre el generador en modo simulación y devuelve las partidas. Primero para compra, gasto y nota contable.

**4.3 Copiar reglas.** Copiar la parametrización de una forma de pago a otra (crédito → ADDI, Sistecrédito) y de una categoría contable a otra.

**4.4 Saldos iniciales por pestañas.** Inventario (valorizado por producto), activos (fichas con depreciación acumulada), diferidos, cartera/proveedores y contabilidad; cuadre obligatorio antes de contabilizar (sin mandar el descuadre a 3705 en silencio).

**4.5 Balance de prueba completo.** Saldo anterior, débitos, créditos y saldo final; filtros por nivel, rango de cuentas, tercero y centro de costo; comparativo mes contra mes y año contra año. Excel.

**4.6 Traslado de cuentas y fusión de terceros.** Mover los movimientos de una cuenta a otra en un rango de fechas (con períodos abiertos) y fusionar dos terceros duplicados; todo con log de quién, cuándo y cuántos registros.

---

## Orden y estado

| Paso | Estado |
|---|---|
| 2.1–2.3 clasificación, compra y diferidos | ✅ 2026-09-30 back + front (V185 probada en local con ROLLBACK; `CompraClasificacionServiceTest` 8/8) |
| 2.4–2.5 conversiones y reorden | ✅ 2026-09-30: unidades sueltas en compra, stock "3 Cajas + 4 und" en Inventario › Stock, máximo/reorden y pantalla Sugerido de Compra |
| 3.1–3.4 activos | ✅ 2026-09-30 back + front (V186; `DepreciacionCalculoTest` 6/6): ficha completa, depreciación prospectiva (LR, doble saldo decreciente, unidades), reversa por período, adiciones, mantenimientos, baja, venta, anular retiro, proyección e informe |
| 4.1–4.6 contabilidad | ✅ 2026-09-30 back + front (V187; `AsientoCompraLineasTest` 3/3): Parametrización Contable, vista previa del asiento de compra (mismo método que el asiento real), copiar formas de pago y categorías, saldos iniciales desde auxiliares con diferencia confirmada, Balance de Prueba con comparativo, traslado de cuentas y fusión de terceros con bitácora |
| Plan de pruebas de F1–F4 | ✅ `docs/PLAN_PRUEBAS_F1_F4.md` |

**Para probar:** migraciones Laravel 000184–000187 en local, `docs/sql/menu_submodulos_f2_f4.sql`, `mvnw clean compile`. Todo probado solo con compilación, pruebas unitarias y SQL con ROLLBACK contra la base local; falta la prueba funcional.
