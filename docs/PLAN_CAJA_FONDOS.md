# Plan · Origen de fondos, caja menor y documentos retroactivos

> **Estado 2026-08-25: PLAN COMPLETO + V151 + V153** ("ya salió de la caja, otro día") — las 8 fases, backend (V145–V150, 32 tests en verde),
> front (`aura-frontend`, build limpio) y migraciones espejo en el proyecto Laravel.
> Motor: **PostgreSQL**. Última migración previa al plan: `V144`.
>
> **Antes de usarlo**: correr V145–V151 y V153, y los dos SQL de menú
> (`menu_submodulo_traslados_fondos.sql`, `menu_submodulo_supervision_caja.sql`).

## 1. El problema

Una factura de compra o un gasto con **fecha de días anteriores** descuadra el arqueo del
**día de hoy**. Los dos puntos exactos:

- `OrigenFondosServiceImpl` — cuando el pago era efectivo y el documento no declaraba turno,
  tomaba *el turno abierto de la sucursal*, o sea el de hoy.
- `MovimientoCajaEntity` no tenía fecha propia: solo `createdAt`. El movimiento nacía con la
  fecha de digitación, no con la del hecho económico.

El resultado es que el cajero de hoy responde por plata que él no gastó.

### La distinción que faltaba

Dos fechas que no tienen por qué coincidir:

| Fecha | Qué es | Para qué sirve |
|---|---|---|
| **Fecha del documento** | La de la factura del proveedor | Contabilidad, IVA, exógena, período contable |
| **Fecha del movimiento** | Cuándo salió la plata físicamente | Arqueo de caja, saldo bancario |

Una factura vieja **no afecta caja por sí sola**. Lo que afecta caja es el pago. Casos:

| Caso | Realidad | Comportamiento correcto |
|---|---|---|
| A · Factura vieja, se paga hoy de la caja | La plata sale hoy | Caja de HOY. No es un bug. |
| B · Factura vieja, la pagó el dueño / banco / caja menor | Nunca tocó la caja del punto | Cero impacto en caja |
| C · Factura vieja, salió de esa caja ese día y nadie la digitó | El turno cerró mal desde el día 1 | Único caso que justifica tocar el pasado |

**El caso B es el volumen real y no tenía salida limpia en la UI.** Por eso el plan ataca
primero B (fases 1–4), luego blinda A (fase 6) y sólo al final construye la corrección de C
(fase 7).

### Por qué NO reabrir el turno cerrado

1. **Rompe la evidencia.** El arqueo cerrado prueba que el cajero entregó $X. Reescribible = no es prueba.
2. **Ya generó asiento.** `TurnoCajaServiceImpl` dispara `DIFERENCIA_CAJA` al cerrar.
   Reabrir obliga a reversar y regenerar.
3. **Choca con el cierre contable.** Si el período está cerrado no se puede ni reversar.
4. **Es la puerta de fraude más obvia:** "reabro el día donde falta plata y lo cuadro".

La fase 7 propone el **ajuste retroactivo sin reapertura**, que da el mismo resultado
conservando la evidencia.

---

## 2. Fases

### ✅ FASE 1 — Fecha propia del movimiento de caja · `V145`

Base de todo lo demás.

**Migración** `V145__movimiento_caja_fecha.sql`

```sql
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS fecha DATE;
UPDATE movimiento_caja SET fecha = created_at::date WHERE fecha IS NULL;
ALTER TABLE movimiento_caja ALTER COLUMN fecha SET DEFAULT CURRENT_DATE;
ALTER TABLE movimiento_caja ALTER COLUMN fecha SET NOT NULL;

ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS fecha_documento DATE;
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS origen_tipo     VARCHAR(30);
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS origen_id       BIGINT;
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS origen_inferido BOOLEAN NOT NULL DEFAULT FALSE;
```

> **Gotcha (V148).** La columna `fecha` **ya existía** desde que se creó la tabla en 2024, como
> `TIMESTAMP DEFAULT now()`. Nunca estuvo mapeada en la entidad, así que en la práctica
> guardaba lo mismo que `created_at`. El `ADD COLUMN IF NOT EXISTS` de la V145 no hizo nada y
> la columna se quedó en timestamp, con la entidad declarando `LocalDate` — con
> `ddl-auto=validate` la app no arranca. La **V148** hace la conversión (`ALTER COLUMN fecha
> TYPE DATE USING fecha::date`, quitando el DEFAULT antes). La V145 ya quedó corregida para
> instalaciones nuevas. No se pierde información: el instante exacto sigue en `created_at`.

**Backend hecho**
- `MovimientoCajaEntity`: `fecha`, `fechaDocumento`, `origenTipo`, `origenId`, `origenInferido`,
  constantes `ORIGEN_*` y el helper `esDeOtraFecha()`. El `@PrePersist` deja `fecha = hoy` como
  red de seguridad para cualquier llamador que no la informe.
- Los **ocho** puntos que crean movimientos de caja pasan a informar fecha y origen:
  `CompraServiceImpl` (pago y reverso), `GastoServiceImpl`, `DevolucionServiceImpl` (×3),
  `ObligacionFinancieraServiceImpl`, `TurnoCajaServiceImpl` (movimiento manual).

`fecha_documento` se deja en NULL cuando coincide con la del movimiento, para que el cierre
filtre por "distinto de NULL" sin comparar fila por fila contra la fecha del turno.

---

### ✅ FASE 2 — Cuentas marcadas como medio de pago · `V146`

Antes, `OrigenFondosServiceImpl` aceptaba **cualquier** cuenta activa como origen de pago: se
podía pagar un gasto acreditando una cuenta de ingresos y nadie lo impedía.

**Migración** `V146__plan_cuenta_medio_pago.sql` — flag `es_medio_pago` + semilla del
disponible (`1105%`, `1110%`, `1120%` auxiliares y activas). Se deja fuera 1115 (remesas en
tránsito) a propósito: es cuenta puente de recaudo, no un medio con el que se paga.

**Backend hecho**
- `PlanCuentaEntity.esMedioPago`, expuesto en `PlanCuentaDto` y editable vía `CreatePlanCuentaDto`.
- `GET /api/contabilidad/plan-cuentas/medios-pago` → lista para el combo del front.
- `OrigenFondosServiceImpl.validarMedioPago()`: exige `activa && esMedioPago && auxiliar`, con
  mensajes que dicen qué hacer ("Habilítela desde el plan de cuentas…").
- `seedPUC` marca el disponible automáticamente y **siembra `110505 Caja Menor`** bajo la 1105,
  además de `1120 Cuentas de Ahorro`.

---

### ✅ FASE 3 — Origen de fondos explícito en compra y gasto

**3.1 · Fallback cerrado.** `turnoAbiertoDeLaSucursal()` solo infiere el turno si hay
**exactamente uno** abierto. Con dos o más pide elegir: tomar "el más antiguo" equivalía a
adivinar de cuál cajón salió la plata. Cuando infiere, el movimiento queda marcado
`origen_inferido = true` para auditoría.

**3.2 · La cuenta contable manda aunque haya caja abierta.** Ya era así en el orden de
evaluación; ahora está documentado y validado. Que exista una caja abierta no significa que la
plata haya salido de ella — quien registra el documento es quien lo sabe.

**3.3 · Crédito.** `CreateGastoDto.formaPago` y `CreateCompraDto.formaPago` ya soportaban
`CREDITO`: genera CxP y no toca caja. Ahora el front lo ofrece como primera opción del bloque,
no como un detalle escondido en "forma de pago".

**✅ Front (`aura-frontend`)** — bloque obligatorio "¿De dónde sale la plata?" en
`form-gasto` y `form-compra`, con las cuatro opciones como tarjetas (no un dropdown: la
elección cambia a quién le afecta el arqueo, tiene que verse de un golpe):

```
( ) No se ha pagado — queda como cuenta por pagar     → formaPago = CREDITO
( ) Caja  [ cajas con turno abierto ]                 → turnoCajaId
( ) Banco [ cuentas bancarias ]                       → cuentaBancariaId
( ) Otra cuenta [ GET plan-cuentas/medios-pago ]      → cuentaPagoId / cuentaContableId
      └─ aquí aparece CAJA MENOR, ANTICIPOS A EMPLEADOS, FONDOS POR LEGALIZAR
```

- **El selector arranca vacío.** Al principio venía con "Caja" preseleccionado, y quien no
  tocaba el bloque seguía cargando a la caja del punto una factura pagada de otro lado — justo
  lo que el campo existe para evitar. Sin origen elegido no se guarda.
- "Otra cuenta" **siempre disponible**, haya o no caja abierta. Antes decía *"Elíjala solo si
  no hay caja abierta"* y su placeholder era *"Sale de la caja abierta"*: era un recurso de
  emergencia, no una vía legítima.
- El combo lo llena `GET plan-cuentas/medios-pago`. Antes el front filtraba el plan completo
  por `tipo === 'ACTIVO' || 'PASIVO'`, que dejaba pasar inventarios y cartera como si fueran
  medios de pago — y el backend los rechaza.
- Si la fecha del documento es anterior a hoy, la vía "Caja" muestra una advertencia que
  ofrece la alternativa en vez de solo bloquear.
- Al guardar, el origen se descompone en las tres piezas del backend y **solo viaja el
  identificador de la vía elegida**: mandar dos haría que el resolutor use el de más prioridad
  y no el que el usuario quiso. Al editar, `deducirOrigen()` deshace el camino con el mismo
  orden de evaluación del backend.

---

### ✅ FASE 4 — Traslado de fondos y CAJA MENOR · `V147`

Constituir una caja menor es un **traslado entre cuentas de efectivo**, no un gasto.

**Ciclo completo**

| Momento | Asiento | ¿Toca el arqueo del cajero? |
|---|---|---|
| 1 · Constituir el fondo | DB `110505` Caja Menor / CR `1105` Caja ó `1110` Bancos | Sí, si sale del cajón — la plata salió de verdad |
| 2 · Gasto pagado con caja menor | DB `5xxx` Gasto / CR `110505` | **No** — es el objetivo |
| 3 · Reembolso del fondo | DB `110505` / CR `1110` Bancos | No |

El paso 2 no necesitó código: es la fase 3 eligiendo "Otra cuenta → CAJA MENOR".

**Backend hecho**
- Tabla `traslado_fondos` (`V147`) con origen y destino independientes; cada extremo es
  `CAJA` (turno) · `BANCO` (cuenta bancaria) · `CUENTA` (cuenta contable).
- `TrasladoFondosEntity`, DTOs, `TrasladoFondosService(Impl)`, `TrasladoFondosController`
  (`POST/GET /api/traslados-fondos`).
- `TrasladoFondosGenerador` + `LectorTrasladoFondos(Jpa)`: prefijo `TF`,
  `siempreContabilizado = true`, DB destino / CR origen. El `GeneradorRegistry` lo descubre
  solo por ser `@Component`.
- **Ambos extremos se resuelven con `OrigenFondosService`**, el mismo que usan compras y
  gastos. No es reutilización por comodidad: si el traslado resolviera las cuentas por su
  cuenta, podría acreditar una cuenta que el gasto rechaza.
- Efectos automáticos: origen/destino `CAJA` → movimiento de caja EGRESO/INGRESO en ese turno;
  origen/destino `BANCO` → `tesoreriaService.registrarMovimientoDeDocumento(...)`, que ajusta
  saldo y deja el registro en el extracto interno para la conciliación.
- El generador toma las cuentas **ya resueltas del documento**, no las recalcula: si entre
  tanto cambiara la parametrización del banco, el asiento usaría una cuenta distinta de la que
  se validó.

**Lo que este documento resuelve además de la caja menor**
- La **consignación diaria** del efectivo al banco (CAJA → BANCO), que no tenía documento.
- Movimientos entre cuentas bancarias.
- Entrega de base entre turnos.

**✅ Front** — `features/traslados-fondos/`, ruta `/tesoreria/traslados-fondos`, ítem
"Traslados de Fondos" en el sidebar bajo Tesorería. Listado con filtros por fecha y concepto,
y diálogo de alta con los dos extremos independientes. El SQL del menú está en
`docs/sql/menu_submodulo_traslados_fondos.sql` (**falta correrlo**, o el ítem no aparece).

**Endpoint extra que hizo falta**: `GET /api/turnos/abiertos?sucursalId=` — sin él el front no
puede preguntar de qué caja sale la plata. `turnoActivo()` no servía: depende del usuario, y
el administrador no tiene turno propio pero sí necesita elegir la caja del punto.

**Decisión tomada — caja menor como cuenta contable pura.** El contador la crea (o la usa ya
sembrada como `110505`) y la marca como medio de pago; el saldo se ve en el libro auxiliar.
La alternativa — `CajaEntity` con `tipo = CAJA_MENOR`, responsable, fondo fijo y arqueo propio
— queda como evolución si el cliente pide control de saldo. Requeriría antes agregar
`caja.cuenta_contable_id`, que hoy no existe: todas las cajas caen en la `1105` genérica.

---

### ⬜ FASE 5 — Cuenta puente de gastos por legalizar

Para cuando el dueño o un empleado compra con su propia plata. **No requiere código**: sale de
las fases 2, 3 y 4.

- El contador crea la cuenta (p. ej. `1330xx` Anticipos a empleados, o una cuenta puente
  `FONDOS POR LEGALIZAR`) y la marca `es_medio_pago`.
- El gasto se registra con la **fecha real del documento** contra esa cuenta → asiento
  `DB gasto / CR fondos por legalizar`. **Cero impacto en caja.**
- El día del reembolso: traslado de fondos `DB fondos por legalizar / CR caja o banco` — y ahí
  sí el arqueo se afecta, en el día correcto.

Pendiente: documentarlo para el contador y precargarlo en el asistente de configuración.

---

### ✅ FASE 6 — Control de fecha retroactiva · `V149`

El freno. Las fases anteriores dieron la forma de declarar el origen y de ver el desfase; sin
esto, nada impedía seguir cargando una factura de hace tres semanas a la caja de hoy.

**La restricción aplica SOLO a la vía CAJA.** Crédito, banco y caja menor no descuadran el
arqueo de nadie: ponerles fricción empujaría al usuario de vuelta a "Caja", que es exactamente
lo contrario de lo que se busca.

**Parámetros por empresa** (en `empresa`, como `modo_contabilizacion`):

| Parámetro | Default | Qué hace |
|---|---|---|
| `dias_gracia_documento_retroactivo` | 3 | Días hacia atrás sin fricción. 3 cubre el fin de semana |
| `bloquear_caja_retroactiva` | true | Pasada la ventana, la vía CAJA se cierra salvo autorización |
| `rol_autoriza_retroactivo` | ADMIN | Rol que puede saltarse la ventana |

**Backend hecho** — `ControlFechaRetroactivaService`, con el orden de comprobaciones que
importa: primero se descartan los casos sin fricción (no sale de caja, dentro de la ventana) y
solo después se mira rol y motivo. Al revés, un cajero registrando la compra del día se
toparía con preguntas que no vienen al caso.

1. No sale de caja → pasa, ni siquiera consulta la configuración.
2. Dentro de la ventana → pasa.
3. Fuera, sin el rol, con bloqueo → **rechazo** con un mensaje que ofrece las salidas
   (caja menor, banco, cuenta por pagar) en vez de dejar al usuario sin camino.
4. Fuera, sin el rol, sin bloqueo → rechazo indicando quién puede autorizar.
5. Fuera, con el rol, sin motivo (o de menos de 10 caracteres) → se exige explicación.
   Un motivo de tres letras no explica nada; es saltarse el control.
6. Fuera, con el rol y motivo → pasa, y quedan `motivo_retroactivo` y `autorizado_por` en el
   documento. El objetivo no es solo frenar: es poder preguntar después qué pasó ese día.

Conectado en `GastoServiceImpl` y en `CompraServiceImpl` — en la compra, **antes de tocar
inventario**: si no puede pagarse desde la caja, no debe existir a medias con el stock movido.

**Front** — campo "Motivo" que aparece dentro del bloque de origen cuando la fecha es anterior
y la vía es CAJA. Se ofrece siempre que haya desfase, no solo cuando el backend lo exigirá, para
que el usuario no tenga que reintentar.

**Tests** — `ControlFechaRetroactivaServiceTest`, 11 casos: los bordes de la ventana, el rol en
mayúsculas y minúsculas, el motivo insuficiente, la empresa con gracia 0 y la vía no-caja que
ni siquiera consulta la configuración.

---

### ✅ FASE 7 — Ajuste retroactivo de caja cerrada · `V150`

El caso que faltaba: de verdad salió plata de una caja, el turno se cerró sin registrarla, y el
faltante quedó como una diferencia sin explicación.

**Por qué NO se reabre el turno** — la decisión central de esta fase:

1. Rompe la evidencia. El arqueo cerrado prueba que el cajero entregó $X; si se puede
   reescribir, deja de probar nada.
2. El cierre ya generó su asiento de `DIFERENCIA_CAJA`: reabrir obliga a reversarlo.
3. Si el período contable ya cerró, ni siquiera se puede reversar.
4. Es la puerta de fraude más obvia: "reabro el día donde falta plata y lo cuadro".

**Backend hecho** — `TurnoCajaService.registrarAjusteRetroactivo()`, el único camino por el que
un movimiento entra a un turno CERRADO:

- **`turno.diferencia` no se toca nunca.** Al primer ajuste se copia a `diferencia_original`, y
  `diferencia_ajustada` lleva el resultado de aplicar los ajustes encima. Un EGRESO no
  registrado agranda el faltante (la plata ya no estaba); un INGRESO lo reduce.
- Exige el **rol autorizador** de la empresa — reutiliza
  `ControlFechaRetroactivaService.exigirRolAutorizador()`, para que "quién puede tocar el
  pasado" tenga una sola definición.
- Exige **motivo** (mínimo 10 caracteres) y guarda `autorizado_por`. Hay un CHECK en la tabla
  que lo respalda: `es_ajuste_retroactivo = FALSE OR motivo_ajuste IS NOT NULL`.
- **Valida el período contable** de la fecha del ajuste vía `PeriodoContablePort`, antes de
  guardar nada, y traduce el fallo a un mensaje que el usuario entiende.
- Sobre un turno ABIERTO se rechaza indicando la alternativa: ahí se registra el movimiento
  normal y el cierre lo recoge solo.
- El asiento nace con la **fecha del ajuste**, no la de hoy.

**Front** — `features/caja/turnos/ajuste/`: botón "Corregir arqueo" en las filas CERRADA de la
lista de turnos, y diálogo que empieza diciendo que el cierre no se reescribe (para que nadie
busque el botón de "reabrir" que a propósito no existe). El tipo se elige con tarjetas y no con
un dropdown: la diferencia entre "salió" y "entró" invierte el signo, así que va explicada.
En el detalle del cierre, sección azul **"Correcciones del cierre"** con *cierre original* y
*saldo ajustado* uno al lado del otro.

**Tests** — `AjusteRetroactivoCajaTest`, 9 casos. El primero es el que importa:
`elCierreOriginalSobreviveAlAjuste`.

### ✅ FASE 8 — Visibilidad en el arqueo

Que el cajero no cuadre a ciegas.

> **Por qué dejó de ser opcional.** En la primera prueba real, una caja recién abierta con base
> 0 mostró `totalEsperado: -189.106` por una compra del 17 registrada el 21. El dato para
> distinguirla ya se guardaba, pero **la pantalla no lo usaba**: `movimientoCajaToDto` devolvía
> `created_at` bajo el nombre `fecha`, y el DTO no exponía `fecha_documento`. El desfase era
> invisible.

**Hecho**
- `MovimientoCajaDto`: `fecha` pasa a ser el **día del dinero** (antes era `created_at` con
  hora); se agregan `registradoEn` (el instante, solo para ordenar), `fechaDocumento`,
  `origenTipo`, `origenId` y el flag `esDeOtraFecha`.
- Los abonos normalizan igual: `fecha` siempre `yyyy-MM-dd`, `registradoEn` el instante.
  Mezclar formatos rompía el orden del detalle, que compara texto.
- `ResumenTurnoDto`: `movimientosDeOtrasFechas` (subconjunto de `movimientos`, no lista
  adicional — así no se rompe a quien ya consume la original) más
  `totalIngresosOtrasFechas` / `totalEgresosOtrasFechas`.
- Pantalla de cierre (`cerrar-turno`): sección **"Documentos de otras fechas"** antes del resto,
  con borde ámbar, la etiqueta del documento origen ("Compra #49"), sus dos fechas y el total.
  La lista de movimientos manuales ahora filtra por `!esDeOtraFecha`.

**✅ Panel de supervisión** — `/caja/supervision`.

> **Cambio de premisa.** El plan original pedía una bandeja de pendientes: *"el operario
> registra sin decidir, el administrador clasifica"*. Las fases 3 y 6 dejaron eso obsoleto — el
> origen es **obligatorio** al registrar y el freno bloquea lo que no debe pasar, así que no
> queda nada a medio clasificar. Lo que sí faltaba es la contraparte: un sitio donde el
> administrador vea el rastro sin abrir turno por turno.

`GET /api/caja/supervision-retroactiva?desde&hasta` reúne cuatro hallazgos desde tablas
distintas, porque responden la misma pregunta — *¿qué entró a una caja sin ser del turno?*:

| Hallazgo | Qué es | Severidad |
|---|---|---|
| `DOCUMENTO_AUTORIZADO` | Factura vieja que pasó el freno con autorización expresa | Hay que leer el motivo |
| `AJUSTE_CIERRE` | Corrección sobre un arqueo ya cerrado | Hay que leer el motivo |
| `CAJA_INFERIDA` | Nadie eligió la caja: el sistema la dedujo (`origen_inferido`) | Informativo |
| `PAGO_DE_OTRA_FECHA` | Documento de otro día, dentro de la ventana de gracia | Solo rastreable |

En el front, los cuatro contadores van arriba y son botones que filtran la tabla: la pantalla
se abre para saber **si hay algo que revisar**, no para leer una lista. Con los cuatro en cero
muestra "Nada que revisar en este período" y se cierra.

Falta correr `docs/sql/menu_submodulo_supervision_caja.sql` para que el ítem aparezca.

---

## 3. Estado y orden

| # | Fase | Migración | Estado |
|---|---|---|---|
| 1 | Fecha del movimiento de caja | V145 | ✅ |
| 2 | Cuentas medio de pago | V146 | ✅ |
| 3 | Origen explícito en compra/gasto | — | ✅ back + front |
| 4 | Traslado de fondos / caja menor | V147 | ✅ back + front · ⬜ correr SQL del menú |
| 5 | Cuenta puente por legalizar | — | ⬜ configuración + doc |
| 6 | Control de fecha retroactiva | V149 | ✅ back + front |
| 7 | Ajuste retroactivo de caja cerrada | V150 | ✅ back + front |
| 8 | Visibilidad en el arqueo + supervisión | — | ✅ back + front |
| — | Fix del tipo de `movimiento_caja.fecha` | V148 | ✅ |

### ✅ EXTRA — "Ya salió de la caja, otro día" · `V151`

Salió de una prueba con el cliente y no estaba en el plan original. Su operación real:

> La plata sale del cajón un día. El administrador lo anota en su cuaderno, cuadra la caja
> física contra ese cuaderno y **cierra en cero**. Al día siguiente registra la factura.

Para ese documento **no debe crearse ningún movimiento de caja**:

- La caja de **hoy** no se toca — la plata no salió de ahí. Restarla produciría un
  **sobrante falso**: el cajero contaría más plata de la que el sistema espera.
- La caja de **aquel día** tampoco — ya cerró cuadrada contra el conteo físico, que sí
  contemplaba esa salida. Meterle un ajuste retroactivo le inventaría un faltante.

Contablemente la plata sí salió de caja, así que el asiento acredita `1105`. El motor ya
distingue **la cuenta contable Caja** del **arqueo de un turno**: son cosas distintas y solo la
segunda mueve el cierre del cajero. Técnicamente ya se podía — eligiendo "Otra cuenta → 1105" —
pero nadie iba a encontrar esa respuesta detrás de esa etiqueta.

- `OrigenFondosService.Tipo.CAJA_OTRO_DIA` + el flag `salidaDeCajaOtroDia` en la `Solicitud`.
  Se evalúa **antes** que todo: es una afirmación sobre un hecho pasado, no una elección
  entre cuentas.
- Columna `salida_caja_otro_dia` en `compra` y `gasto` (V151). Existe **solo para auditar**:
  la vía no pasa por el freno — no descuadra nada, no hay a quién proteger — así que sin la
  columna no dejaría rastro visible.
- Quinta opción en el selector de ambos formularios, y quinto hallazgo
  `SALIDA_CAJA_OTRO_DIA` en el panel de supervisión.
- Al **editar**, `deducirOrigen()` mira el flag antes que las cuentas: la cuenta guardada es
  CAJA y se confundiría con un pago normal, regenerando el movimiento que no debe existir.

### ✅ EXTRA — La misma vía en CxC y CxP · `V153`

El cliente lo señaló después: en cartera pasa exactamente lo mismo, y en **los dos sentidos**.

> El cliente trae el abono en efectivo un día. La plata entra al cajón, el conteo de esa
> tarde la cuenta y el turno cierra cuadrado contra el cuaderno. Al día siguiente se registra
> el abono en el sistema.

- **CxC**: si el recaudo se ata al turno de hoy, el sistema espera un efectivo que hoy no
  entró → el cajero cierra con **faltante**.
- **CxP**: al revés. El abono al proveedor le resta al esperado de hoy una plata que hoy no
  salió → el cajero cierra con **sobrante**.

El mecanismo del arqueo es distinto al de compra y gasto, y ahí estaba la trampa: **un abono
no pasa por `movimiento_caja`**. Entra al cierre por su propio `turno_caja_id` combinado con
`metodo_pago LIKE '%EFECTIVO%'` (`AbonoCobrarJPARepository.sumMontoByTurnoCajaId`). Así que
"no generar movimiento de caja" no bastaba: la vía "otro día" tiene que **guardar el abono sin
turno**.

- El flag de la `Solicitud` pasó de `salidaDeCajaOtroDia` a **`cajaOtroDia`**: en CxC el
  dinero *entra*, y el nombre viejo describía mal justo el caso nuevo.
- `OrigenFondosServiceImpl` ahora devuelve **`turno == null`** en `CAJA_OTRO_DIA`, aunque el
  documento lo haya declarado. Se prefirió eso a que cada llamador se acordara de descartarlo:
  compra y gasto solo usan el turno detrás de `generaMovimientoCaja()`, así que no cambian.
- Columna `caja_otro_dia` en `abonos_cobrar` y `abonos_pagar` (V153), otra vez **solo para
  auditar** — sin ella, un abono sin turno es indistinguible de uno viejo anterior a los turnos.
- Sexto hallazgo en supervisión: `INGRESO_CAJA_OTRO_DIA` para el recaudo de cartera; el abono
  a proveedor reusa `SALIDA_CAJA_OTRO_DIA`, que sigue siendo literal.
- El asiento no cambia: `AbonoCobroGenerador`/`AbonoPagoGenerador` resuelven la cuenta por el
  medio de pago, así que sigue afectando `1105`.

**Pendiente conocido**: el freno de fecha retroactiva (fase 6) sigue sin cubrir los abonos.
Si alguien registra un abono en efectivo con `fechaPago` vieja y **no** marca "otro día", cae
en la caja de hoy sin pedir autorización ni motivo — el mismo hueco que el V149 cerró para
compra y gasto. Requiere `motivo_retroactivo`/`autorizado_por` en las dos tablas de abonos.

---

## 4. Para poner en marcha lo hecho

1. **Correr V145–V151 y V153.** Ya están replicadas de forma idempotente en el proyecto
   Laravel (`2026_07_03_000145` … `000151`, `000153`). Con `ddl-auto=validate`, la app no levanta hasta que estén
   aplicadas. Prod (`aura-db` remota) no se toca.
2. **Empresas existentes**: la semilla de `es_medio_pago` del `V146` marca su disponible
   actual. La cuenta `110505 Caja Menor` solo se siembra en empresas nuevas — para las
   existentes, el contador la crea desde el plan de cuentas y marca "es medio de pago".
3. **Correr los dos SQL de menú** — `menu_submodulo_traslados_fondos.sql` y
   `menu_submodulo_supervision_caja.sql`. Sin ellos las pantallas existen y las rutas
   responden, pero los ítems no aparecen en el sidebar: el front los oculta hasta que el
   submódulo esté activo para la empresa.

## 5. Riesgos

- **`ddl-auto=validate`**: cada campo nuevo necesita su migración corrida antes de levantar.
  Ver `docs/EJECUCION_MIGRACIONES.md`.
- **Backfill de `movimiento_caja.fecha`**: los movimientos históricos quedan con la fecha de
  digitación. Es el único dato que existe y coincide con el comportamiento previo.
- **Retrocompatibilidad del front**: mientras no mande el origen explícito, la inferencia de
  turno único mantiene el comportamiento actual con marca de auditoría. Lo que sí cambia hoy:
  con **dos cajas abiertas** en la sucursal, un pago en efectivo sin turno declarado ahora
  falla pidiendo elegir, donde antes tomaba la más antigua en silencio.
- **Tests legacy**: `CuentaCobrarServiceTest` y `CuentaPagarServiceTest` fallan (46 run, 5
  failures, 7 errors) — verificado idéntico en HEAD limpio, preexistente y ajeno a este plan.
