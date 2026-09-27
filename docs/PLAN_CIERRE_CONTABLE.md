# Plan: cierre contable profesional

Fecha: 2026-09-17 · Alcance: solo local (`aura-pos`), nada en producción.

## 1. Diagnóstico

Había dos cosas llamadas "cierre" y ninguna se comportaba como tal.

| Pieza | Qué era |
|---|---|
| `/contabilidad/cierre` ("Cierre Contable") | Un tablero de KPIs armado desde ventas, compras, mermas y gastos. No pasa por el mayor, no cierra nada, no deja papel de trabajo. La utilidad que muestra no amarra con el balance. |
| `/contabilidad/periodos-contables` | El cierre formal de verdad: valida borradores, extractos sin conciliar y comprobantes descuadrados, genera el asiento de cancelación de resultados y la reclasificación de sobregiros. |

**El problema de fondo estaba en el motor.** `PeriodoContableJpa.abiertoPara` ignoraba la
fecha del asiento y devolvía el único período ABIERTO de la empresa:

- Solo podía haber un período abierto. Cerrar enero sin abrir febrero dejaba el sistema
  sin período y **todo asiento automático fallaba** ("No hay un período contable ABIERTO"):
  se caían ventas y compras.
- No se podía registrar febrero mientras el contador cerraba enero, que es justo como se
  trabaja.
- Una factura de enero registrada en febrero quedaba marcada en el período de febrero: el
  balance por período no amarraba con las fechas.
- No existía reapertura: un mes mal cerrado no tenía vuelta atrás.

## 2. Fases

1. **Motor de períodos** (hecho, ver abajo): período por fecha, varios abiertos, apertura
   automática, cierre en orden y reapertura con traza.
2. **Lista de chequeo de cierre** con semáforos y acción a un clic: borradores, descuadres,
   conciliación bancaria, depreciación del mes, causación de nómina, anticipos sin cruzar,
   movimientos sin tercero, cuentas con saldo contrario a su naturaleza, y los cruces de
   auxiliar contra mayor (cartera vs 1305, proveedores vs 2205, inventario vs 1435, caja vs
   arqueos).
3. **Ajustes de cierre asistidos**: botones que arman el comprobante en borrador
   (depreciación, amortización de diferidos, deterioro de cartera, ajuste de inventario)
   para que el contador los revise y apruebe.
4. **Acta de cierre en PDF**: período, quién cerró, cuándo, resultado de cada chequeo,
   balance de comprobación y el asiento de cierre. Es lo que el contador archiva.
5. **Comparativos**: el informe de resultados como pestaña dentro del cierre, contra el mes
   anterior y contra el año anterior.

## 3. Fase 1 — cómo quedó (2026-09-17)

**V171** (`V171__periodos_por_fecha.sql` + Laravel `000171`), corrida solo en local:
`periodo_contable` gana `reaperturas`, `fecha_reapertura`, `usuario_reapertura_id`,
`motivo_reapertura` y `creado_automatico`; tabla nueva `periodo_contable_evento`
(APERTURA / CIERRE / REAPERTURA con motivo y usuario). El backfill crea el período de cada
mes que tenga asientos y **reasigna cada asiento al período de su fecha** — solo los que
estaban sin período o en uno abierto: un mes ya cerrado es historia firmada y no se toca
desde una migración.

**`PeriodoContableResolver`** es ahora el único camino para saber a qué período va un
asiento:

- El período es el del **mes de la fecha** del documento.
- Si ese mes no existe, **se abre solo** (`ON CONFLICT DO NOTHING`, porque dos ventas
  simultáneas pueden ser las primeras del mes) y queda marcado `creado_automatico`.
- Si ese mes está **cerrado**, bloquea con un mensaje que dice qué hacer: corregir la fecha
  o reabrir el período.
- Rechaza fechas a más de un mes hacia adelante: siempre son error de digitación.

Se cambiaron los 17 puntos que resolvían "el período abierto": asientos manuales y
comprobantes (fecha del DTO), saldos iniciales, ventas (fecha de emisión), compras (fecha
de la compra), y los once generadores automáticos (gasto, merma, nómina, pagos,
obligaciones, caja, tesorería, devolución…), cada uno con la fecha de su documento. La
reversa sigue cayendo en el período de hoy, no en el del asiento original: reversar un
documento de un mes cerrado no reabre ese mes.

**Cierre y reapertura** (`PeriodoContableServiceImpl`):

- Se puede abrir cualquier mes hasta el siguiente al actual; ya no existe "solo un período
  abierto".
- Cerrar exige que **no queden meses anteriores abiertos** (se cierra en orden) y que el mes
  ya haya empezado. Se conservan las validaciones que ya había (borradores, descuadres,
  extractos sin conciliar).
- Los asientos de cierre (cancelación de resultados y sobregiros) ahora se fechan el
  **último día del mes que cierra**, no el día en que el contador corrió el proceso.
- **Reapertura** con motivo obligatorio: solo el último mes cerrado, bloqueada si el año ya
  tiene cierre fiscal (provisión de renta o traslado de utilidad). Anula los asientos que
  generó el cierre, cuenta las reaperturas y deja evento con usuario y motivo.

**Dos bugs preexistentes que aparecieron al probar:**

- El cierre pedía la cuenta de sobregiros (2105) aunque no hubiera ningún banco en rojo:
  cualquier empresa sin esa cuenta **no podía cerrar el mes**. Ahora la cuenta se resuelve
  solo si de verdad hay un sobregiro.
- La respuesta de cerrar el período se leía por JDBC antes de que JPA escribiera, así que
  devolvía "ABIERTO" después de cerrar. Se cambió a `saveAndFlush`.

**Front**: la pantalla de períodos muestra todos los meses abiertos (no uno), avisa cuáles
hay que cerrar primero, indica los meses que abrió el sistema, cuenta los borradores que
bloquean el cierre y agrega el botón de reabrir con su diálogo. La pantalla de KPIs pasa a
llamarse **"Resultados del Período"** y dice que el cierre formal vive en Períodos
contables; el menú se ajusta con `docs/sql/renombrar_submodulo_resultados_periodo.sql`
(el sidebar compara `normalize(label)` contra `submodulos.codigo`, así que el script crea
el submódulo `resultados-del-periodo`, le copia a cada empresa el permiso que tenía sobre
`cierre-contable` y apaga el viejo).

**Probado** por endpoints en 9002 con la empresa 4 local (13 verificaciones, 0 fallas):
apertura automática del mes con su evento, cierre en orden, bloqueo del mes cerrado,
convivencia de varios meses abiertos, fecha del asiento de cierre, reapertura (motivo
obligatorio, bloqueo por cierre fiscal, anulación del asiento de cierre, traza y contador) y
rechazo de fechas lejanas. **No probado en navegador.**

**Pendiente**: correr V171 en producción, y las fases 2 a 5.

## 4. Estilos de las tres pantallas (2026-09-17)

"Resultados del Período" tenía 1.116 líneas de SCSS propio, 21 usos de índigo/morado
—compitiendo con el azul de marca #2563eb—, ninguna variable `--aura-*` y **cero** soporte
de modo oscuro; además se salía del estándar con su propio `cc-wrapper` en vez de
`.page > .card > .card-head`.

Las tres pantallas del cierre (Resultados, Períodos contables y Cierre anual) quedaron
sobre los tokens `--aura-*` de `styles.scss`. Como el tema oscuro redefine esos tokens, el
modo oscuro funciona sin duplicar reglas: cada pantalla declara en `:host` sus variables
semánticas (`--*-ok`, `--*-bad`, `--*-warn`, `--*-primary`, `--*-soft`) derivadas de los
tokens globales, y el resto del SCSS solo usa esas. El SCSS de Resultados bajó a ~630
líneas y el encabezado ahora es la card estándar del resto de la app.

De paso, los seis banners verdes apilados de Períodos contables (uno por mes abierto) se
volvieron un solo bloque: "N meses abiertos · sigue cerrar <mes>", con un chip por mes que
muestra sus comprobantes y sus borradores, y el botón de cerrar el más antiguo.

Verificado en el navegador, en tema claro y oscuro, en las tres pantallas.
