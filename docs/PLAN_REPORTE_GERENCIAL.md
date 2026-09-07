# Plan · Reporte gerencial de auditoría (PDF)

> **Estado 2026-09-06: G1–G5 HECHOS** — motor de hallazgos, indicadores, gráficas,
> causas probables, PDF completo (22 tests en verde) y pantalla en el front.
> **G6 descartado**: 472 ms para un mes.
> Queda por correr `docs/sql/menu_submodulo_reporte_gerencial.sql` para que el
> ítem aparezca en el sidebar.
> Audiencia: **el dueño del negocio**, con anexo técnico para el contador.
> Los descuadres heredados van en **sección aparte**, marcados como tales.
> Repos: backend `aura-back-old`, frontend `aura-frontend`.

## 1. Qué lo hace auditoría y no un dashboard

Un dashboard muestra cifras. Una auditoría **contrasta cada cifra contra otra
fuente independiente y reporta lo que no cuadra**.

Ese contraste es posible porque el sistema guarda la misma verdad por dos
caminos en varios sitios. Ahí es donde se mira:

| # | Fuente A | Fuente B | Qué destapa el desacuerdo |
|---|---|---|---|
| 1 | `turno_caja.total_efectivo_real` (lo contado) | base + ventas efectivo + ingresos − egresos − comisiones | Faltantes y sobrantes por cajero |
| 2 | `cuentas_cobrar.saldo_pendiente` | `total_deuda −` suma de abonos vivos | Saldos desfasados: abonos borrados, anulaciones a medias |
| 3 | `cuentas_pagar.saldo_pendiente` | idem | Lo mismo del lado del proveedor |
| 4 | `SUM(movimiento_inventario.cantidad)` | `SUM(saldo_nuevo − saldo_anterior)` | Filas del kardex mal grabadas |
| 5 | Último `saldo_nuevo` del kardex | `inventario.stock_actual` | Stock que no cuadra con su propia historia |
| 6 | `asiento.total_debito` | `asiento.total_credito` | Asientos que no cumplen partida doble |
| 7 | Activo | Pasivo + Patrimonio + Resultado | La ecuación contable no cierra |
| 8 | Ventas del período | Facturas electrónicas emitidas | Ventas sin facturar |
| 9 | Abonos con `turno_caja_id` | Abonos sin turno ni `caja_otro_dia` | Plata que entró sin caer en ningún arqueo |
| 10 | Gastos del período | Los que tienen soporte y tercero | Lo que no aguanta una revisión de renta |

**Cada cifra del PDF cita su fuente.** Un reporte de auditoría que no puede
señalar de dónde sale un número no se puede defender frente a quien lo cuestione,
y es exactamente lo que va a pasar cuando el hallazgo incomode.

## 2. Principios

- **Los hallazgos van primero.** El semáforo abre el documento; los indicadores
  vienen después. Quien lo abre quiere saber si tiene un problema, no leer doce
  páginas hasta encontrarlo.
- **Todo hallazgo lleva monto.** "12 documentos con problemas" no mueve a nadie.
  "$4.300.000 en cartera cuyo saldo no cuadra con sus abonos" sí.
- **El reporte no recalcula reglas de negocio.** Compone lo que ya calculan los
  servicios existentes. Si una cifra sale mal, se arregla donde se produce — un
  reporte que "corrige" al vuelo esconde el problema de fondo.
- **Lo heredado va aparte.** Sección propia, marcada, con la nota de que ya está
  diagnosticado. Si no, el primer reporte parece decir "el sistema está mal"
  cuando dice "hay deuda técnica conocida".

## 3. Lo que ya existe (no se reconstruye)

| Área | De dónde sale |
|---|---|
| Resultado, balance, flujo de caja | `AsientoContableService`: `estadoResultados`, `balanceGeneralDetallado`, `flujoCaja` |
| Ventas, márgenes, top productos, rotación | `ReporteAvanzadoService` |
| Caja: arqueos y movimientos | `TurnoCajaServiceImpl.construirResumen`, `ReporteAvanzadoService.movimientosCaja` |
| Hallazgos de caja retroactiva | `SupervisionRetroactivaQueryRepository` (incluye `COMPROBANTE_SIN_ARQUEO`) |
| Cartera por edades y abonos | `ReporteCarteraQueryRepository` |
| Gastos con deducible/IVA/retenciones | `ReporteGastosQueryRepository` |
| Inventario y kardex | `KardexQueryRepository` |
| Facturación electrónica | `ReporteFacturacionElectronicaService` |
| Estilos y armado de Excel/PDF | `ExcelEstilos`, el PDF del kardex (iText) |

**Lo genuinamente nuevo son los cruces (§1) y el PDF.**

## 4. Estructura del PDF

```
Portada        Empresa, período, quién y cuándo lo generó
               SEMÁFORO: hallazgos por severidad + plata involucrada

1. Cómo va el negocio     Ventas, costo, margen, gastos, resultado.
                          Comparado contra el período anterior.
2. Caja                   Arqueos del período, faltantes/sobrantes por cajero,
                          movimientos sin documento.
3. Cartera                Saldo por edades, los 10 que más deben, recaudo del mes.
4. Inventario             Valor del stock, rotación, mermas, descuadres del kardex.
5. Gastos                 Por categoría, deducible vs no deducible, sin soporte.
6. Contabilidad           Resultado, balance resumido, partida doble, período abierto.

7. Hallazgos del período  Tabla con severidad, monto, dónde mirarlo.
8. Descuadres heredados   Aparte y marcados: vienen de antes, ya diagnosticados.

Anexo A                   Metodología: qué se cruzó contra qué (para el contador).
Anexo B                   Detalle de cada hallazgo, documento por documento.
```

Del 1 al 6 en lenguaje llano y pocas cifras por página. Los anexos son densos a
propósito: es donde el contador va a buscar el soporte.

## 5. Severidad

| Nivel | Criterio | Ejemplo |
|---|---|---|
| **ALTA** | Plata que no se puede explicar, o contabilidad que no cierra | Arqueo con faltante, ecuación contable descuadrada, abono sin arqueo |
| **MEDIA** | Cuadra pero no se puede defender ante un tercero | Gasto deducible sin soporte, venta sin factura electrónica |
| **BAJA** | Higiene de datos | Gasto sin centro de costo, producto sin categoría contable |

La severidad **no** depende del monto: un asiento descuadrado de $1.000 es ALTA
porque la contabilidad no cierra. El monto va aparte, para priorizar dentro del
mismo nivel.

## 6. Fases

- **G1 · Motor de hallazgos.** ✅ **HECHO.** `AuditoriaService` +
  `AuditoriaQueryRepository`, endpoint `POST /api/reportes/auditoria` que
  devuelve JSON. Sin PDF a propósito: primero se validan los cruces contra datos
  reales.

  **Nueve cruces implementados** (§1 lista diez; el de *ventas sin factura
  electrónica* se dejó fuera a propósito — ver abajo):
  arqueos con diferencia, saldo de CxC y de CxP contra sus abonos, cantidad del
  kardex contra la variación de saldo, stock contra el kardex, partida doble,
  cabecera del asiento contra sus líneas, facturas sin aceptar por la DIAN, y
  gastos deducibles sin soporte. Heredados: abonos sin arqueo (CxC y CxP) y
  facturas sin fecha de emisión.

  **Por qué falta "ventas sin factura electrónica":** no hay forma en el esquema
  de distinguir qué ventas debían facturarse. Contarlas todas haría que cada
  venta de mostrador apareciera como hallazgo, y **un hallazgo falso destruye la
  confianza en todo el reporte** — el lector deja de creerle también a los
  verdaderos. Se agrega cuando exista la marca que lo distinga.

  **Decisiones del motor:**
  - Un cruce sin filas **no se reporta**. Nada de hallazgos en cero "para que se
    vea que se revisó": eso entierra los que sí importan.
  - El detalle solo se consulta si el cruce encontró algo.
  - Los cruces de **stock** y de **facturas sin fecha** ignoran el rango: el
    stock es acumulado y las facturas sin fecha no tienen con qué acotarse — ese
    es justamente el hallazgo.
  - El reconteo se compara en **valor absoluto**: guarda la cantidad sin signo a
    propósito, así que reportarlo sería ruido.
  - **Máximo un trimestre** por consulta, y se mide `duracionMs` en cada corrida.

  **Validado contra producción (agosto 2026):** 498 ms para un mes con detalle.
  **G6 no hace falta**; el reporte puede generarse síncrono.

  La corrida real obligó a dos correcciones — que es exactamente para lo que
  servía hacerla antes del PDF:

  1. **Umbral de materialidad.** Reportó un arqueo con **22 centavos** de
     sobrante junto a uno de $954.000, los dos en ALTA y con el mismo peso
     visual. Los centavos son redondeo de conteo. Ahora hay `umbralMonto`
     (por defecto $1.000) que aplica a **caja y cartera**; **no** a los asientos,
     donde la partida doble tiene que cuadrar al centavo.
  2. **Descuadre de inventario valorizado en $0.** Encontró un producto con 10
     unidades que su kardex no explica, valorizadas en cero porque al producto
     le falta el costo. Un hallazgo que dice "$0" se lee como "no pasa nada".
     Ahora se valoriza con el costo y, si no hay, con el precio de venta; y el
     hallazgo lleva `magnitud` con las unidades.

  **Y descartó una hipótesis:** `heredados` salió vacío, o sea que **no hay
  facturas sin `fecha_hora_emision`**. La causa de que el reporte de facturación
  electrónica salga vacío en producción es otra — ver
  `docs/sql/diagnostico_reporte_facturas_electronicas.sql`.
- **G2–G4 · Indicadores, PDF y anexos.** ✅ **HECHOS** en `ReporteGerencialService`,
  endpoint `POST /api/reportes/gerencial/pdf`.

  **No se creó el `ReporteGerencialDto` intermedio** que pedía G2: el servicio
  compone y renderiza directo. El front ya tiene el JSON de `/auditoria` para la
  vista previa, así que un DTO de indicadores solo para pasar por el medio no
  aportaba nada. Si G5 lo necesita, se extrae entonces.

  **El décimo cruce entró aquí:** la ecuación contable (`ECUACION_DESCUADRADA`),
  que sale gratis del `balanceGeneral` que ya se consultaba para la sección 4. Se
  evalúa a la fecha final, no dentro del rango: el balance es acumulado y
  preguntar "¿cerró en agosto?" no significa nada — la pregunta es si cierra hoy.

  **Decisiones del PDF:**
  - **Cada fuente de indicadores va en su propio try/catch.** Un negocio sin
    ventas este mes, o sin contabilidad configurada, no puede tumbar el reporte:
    la sección sale con su nota de "no hay datos". Un reporte que no se genera es
    peor que uno que dice lo que falta.
  - **El PDF fuerza `incluirDetalle`** aunque el filtro venga apagado: sin las
    filas, el anexo B queda vacío y un hallazgo es una cifra que nadie puede ir
    a mirar.
  - Los tests **leen el texto del PDF generado** con `PdfTextExtractor`, no solo
    comprueban que no reviente. Verifican que el semáforo va antes que el
    detalle, que cada hallazgo trae monto y recomendación, y que lo heredado
    sale marcado y aparte.
- **G5 · Front.** ✅ **Hecho.** `features/reportes/gerencial/` — período, umbral
  de diferencia mínima e interruptor de heredados; veredicto en una línea,
  semáforo de cuatro tarjetas y tarjetas de hallazgo desplegables de a una
  (son largas: varias abiertas no se leen) con causas probables, el cruce que
  las produjo, la recomendación, el contexto de inventario y el detalle.
  - La pantalla **es la vista previa del PDF**, no un reporte aparte: sale de la
    misma corrida del motor con el mismo filtro, así que lo que se ve es lo que
    se descarga.
  - Consulta con `incluirDetalle: true` — sin las filas, una tarjeta desplegada
    quedaría vacía.
  - Ruta `reportes/gerencial`, ítem de sidebar "Reporte gerencial" y
    `docs/sql/menu_submodulo_reporte_gerencial.sql` (código `reporte-gerencial`,
    que es lo que da `normalize(label)`).
- **G6 · Generación asíncrona.** ❌ **Descartado.** 472 ms para un mes con detalle.

## 6-bis. Lo que se agregó para volverlo un análisis (2026-09-06)

El primer PDF listaba descuadres. Le faltaba lo que convierte un dato en un
análisis: **por qué pasa**, **cómo se compara** y **qué tan sano está el negocio**.

### Causas probables

Cada hallazgo lleva ahora sus causas más frecuentes, **ordenadas por
probabilidad real en este sistema**. "Sobrante de $954.076" no le dice a nadie
dónde buscar; "ventas cobradas que no pasaron por el POS" manda a alguien al
sitio correcto.

El catálogo vive en `AuditoriaService.causasDe(codigo)`. Ejemplos:

| Hallazgo | Primera causa |
|---|---|
| Arqueo descuadrado | Ventas cobradas sin pasar por el sistema (sobrante) o gastos pagados del cajón sin registrar (faltante) |
| Cartera desfasada | Un abono eliminado directo en la base, sin pasar por el sistema |
| Stock sin historia | Un ajuste hecho sobre la tabla de inventario sin escribir en el kardex |
| Ecuación descuadrada | Los asientos descuadrados del período: revíselos primero |

**Se presentan como hipótesis, no como diagnósticos**, y el PDF lo dice
explícitamente. El motor detecta el desacuerdo, no la causa; sin esa advertencia
alguien toma la primera de la lista como un hecho probado.

### Gráficas — dibujadas, no renderizadas

`GraficaPdf` dibuja barras horizontales y barras apiladas con las primitivas de
iText.

**No se usó JFreeChart a propósito.** Renderiza sobre AWT/Java2D y este proyecto
ya se quemó con eso: `autoSizeColumn` reventaba en producción con
`UnsatisfiedLinkError: libfreetype.so.6` porque el servidor Linux no tiene las
librerías nativas de fuentes (ver `ExcelAnchoColumnas`). Una gráfica por AWT
terminaría igual, y falla **cuando alguien descarga el reporte**, no al arrancar.

Las barras se arman con celdas de tabla coloreadas en vez de con canvas: iText
las posiciona solo y respeta el salto de página. Con coordenadas absolutas todo
se desalinea al mover una sección.

Dónde se usan: ventas contra el período anterior, composición de la cartera por
edades, gastos por categoría, reparto del balance entre deuda y patrimonio, y las
líneas de mayor peso del estado de resultados.

### El kardex al momento del descuadre

Un sobrante de caja y una merma del mismo turno **no son dos hallazgos: son uno
con dos caras**. Por separado, cada uno parece otra cosa y ninguno se resuelve.

`AuditoriaQueryRepository.movimientosDuranteTurnos` trae lo que se movió en el
inventario dentro de la ventana horaria de los turnos descuadrados, en la misma
sucursal. **Excluye VENTA y COMPRA** a propósito: son el movimiento normal del
negocio y llenarían la lista tapando lo que hay que mirar. Lo que queda —mermas,
obsequios, reconteos, anulaciones— es exactamente el patrón de "alguien vendió y
cubrió la salida por otra vía".

Sale en el PDF debajo de las causas del hallazgo de caja, que es donde sirve.

### Sección de inventario

Nueva sección 4: **por dónde entra y por dónde se va la mercancía**. La pregunta
que responde no es "cuánto inventario tengo" —eso lo dice el módulo— sino
"cuánto de lo que salió fue venta y cuánto se fue por otro lado".

- **Torta** de las salidas por familia, valorizadas al costo (comparar unidades
  de productos distintos no significa nada).
- **Barras verticales** de unidades netas por tipo, con el cero en su sitio: lo
  que salió cuelga hacia abajo. Un gráfico donde las salidas apunten hacia
  arriba miente sobre lo que pasó.
- **La merma como porcentaje** de todo lo que salió: en valor absoluto nadie
  sabe si es mucho o poco.

Datos de `KardexQueryRepository.movimientoPorFamilia`, que arma el CASE desde el
enum — un tipo nuevo no cae en "Otros" por olvido.

### Indicadores con su lectura

Razón corriente, endeudamiento, margen bruto y margen neto — cada uno **con su
significado en lenguaje llano**. "Razón corriente 1,4" no le dice nada a quien no
es contador; "por cada peso que debe a corto plazo tiene 1,4 para responder" sí.
El margen neto negativo se enuncia como pérdida, no como un porcentaje con signo.

## 7. Riesgos

| Riesgo | Mitigación |
|---|---|
| Correr 10 cruces sobre un año es caro | Empezar acotado a **un mes** y medir en G1. Si no alcanza, G6 |
| El primer reporte sale con muchos hallazgos rojos que no son del mes | Sección 8 aparte (decisión ya tomada) |
| Los cruces duplican lógica que ya vive en los servicios | El motor **consulta**, no recalcula: los cruces son comparaciones entre dos fuentes, no reimplementaciones |
| Un hallazgo falso destruye la confianza en todo el reporte | Cada cruce se valida contra datos reales en G1, antes de que exista el PDF |
| El PDF se vuelve un volcado ilegible | Máximo una idea por página en las secciones 1–6; el detalle vive en los anexos |

## 8. Lo que este reporte va a destapar (previsible)

No es especulación: son problemas ya diagnosticados en este repo.

- Fechas históricas 5 horas adelante (`zona-horaria-produccion`, arreglo sin desplegar).
- Facturas electrónicas sin `fecha_hora_emision` — pendiente de confirmar con
  `docs/sql/diagnostico_reporte_facturas_electronicas.sql`.
- Comprobantes CE/RC anteriores a V154, cuyos abonos no cayeron en ningún arqueo.
- Los ~19 componentes con `appendTo="body"` no afectan datos, no salen aquí.

Todos van a la sección 8, marcados como heredados.
