# Plan — Nómina para producción real (novedades, prestaciones, cierre de huecos)

**Contexto:** empresas reales (constructoras) van a liquidar con dinero real desde
el primer mes. Salarios no altos, vienen de **otro sistema** (traen saldos), y la
nómina electrónica se prueba en paralelo. "Algunas empresas sí, otras no" → todo lo
nuevo debe respetar la config (`modoNomina` SIMPLIFICADO/COMPLETO y toggles).

## Estado real del código (verificado 2026-07-20)

- **Motor de liquidación** (`MotorLiquidacion`): calcula sobre `dias_trabajados`
  (hoy **manual**). Novedades entran con `valor_total` **manual** y se clasifican
  por flags (`es_deduccion`, `constituye_ibc`). NO auto-calcula incapacidad/licencia
  ni ajusta días por ausencias.
- **Prestaciones** (`CalculadoraPrestaciones`): ✅ ya calcula cesantías, intereses,
  prima, vacaciones (valor) e indemnización (CST art. 64). Falta el **ciclo E2E** y
  el **saldo de vacaciones** (causadas vs tomadas).
- **Retefuente** (`CalculadoraRetefuente`): existe; requiere que los conceptos
  `RETEFUENTE_P1/P2` estén en el catálogo, si no **no retiene** (solo warn).
- **Novedad** (`NominaNovedadEntity`): ya tiene `fecha_inicio`, `fecha_fin`,
  `subtipo`, `numero_autorizacion`, `cantidad`, `valor_unitario`, `valor_total`.
  Tipos existentes: `INCAPACIDAD`, `LICENCIA_MATERNIDAD`, `LICENCIA_REMUNERADA`,
  `LICENCIA_NO_REMUNERADA`, `VACACIONES`.

**Conclusión:** la pieza que falta y sostiene todo es la **conciliación de días**:
que una novedad con fechas descuente/ajuste solos los días de salario y agregue su
pago propio. Sobre eso se montan incapacidades, licencias y vacaciones.

---

## F0 — Conciliación de días y novedades (CIMIENTO) 🔑 ✅ HECHO (2026-07-20)

> Implementado y compilando. V129 (`dias` + `afecta_dias_salario` en nomina_novedad),
> entidad + `AddNovedadDto` con fechas/subtipo/nº autorización, `MotorLiquidacion`
> resta `diasAusencia` al salario y al auxilio, validación de días ≤ período, y el
> front captura fechas (p-calendar) para incapacidad/LNR/vacaciones/maternidad con
> subtipo para incapacidad. Falta: correr V129 en la BD y probar el cálculo real.


**Problema:** hoy quien liquida pone `dias_trabajados` a mano y el valor de la
novedad a mano. Un peso mal = riesgo. 

**Objetivo:** dado el período y las novedades con fechas, el motor calcula:
- `diasBase` del período (30 / 15).
- `diasAusencia` = suma de días de novedades que **no se pagan como salario**
  (incapacidad, licencia no remunerada, vacaciones disfrutadas).
- `diasSalario = diasBase − diasAusencia` → salario proporcional real.
- Cada novedad de ausencia aporta **su propio pago** (incapacidad, vacaciones) o
  cero (licencia no remunerada).

**Backend:**
- `NominaNovedadEntity`: agregar `dias` (derivado de fecha_inicio/fecha_fin con
  `diasComerciales`) y `afecta_dias_salario` (bool).
- `MotorLiquidacion.calcularBases`: restar `diasAusencia` al salario proporcional;
  no descontar dos veces (el pago de la novedad ya es aparte).
- Validación: suma de días de novedades de ausencia ≤ días del período.

**Criterio de aceptación:** empleado con 30 días, 5 de incapacidad → salario por 25
días + valor incapacidad; el neto cuadra a mano.

---

## F1 — Incapacidades (auto-cálculo) ✅ HECHO (2026-07-20)

> `CalculadoraIncapacidad` (EG 2/3 con piso SMMLV, 2 días empleador + resto EPS;
> AT/EL 100% ARL; maternidad 100% EPS). `agregarNovedad` auto-calcula el valor de
> INCAPACIDAD y LICENCIA_MATERNIDAD si no vino manual (cantidad=días, valor/día,
> total; separa quién paga en la descripción). Front: subtipo EPS/ARL, valor
> "automático", hint. Pendiente: EG >90 días al 50% y promedio salario variable.


**Subtipos** (`subtipo` de la novedad):
- **ENFERMEDAD_GENERAL (EG):** días 1–2 a cargo **empleador** al **66.67%**; día 3+
  a cargo **EPS** al 66.67% (piso: 1 SMMLV/día; primeros 90 días 66.67%).
- **ACCIDENTE/ENF. LABORAL (AT/EL):** **100%** desde el día 1, a cargo **ARL**.
- **MATERNIDAD/PATERNIDAD:** **100%** a cargo **EPS** (licencia, ver F2).

**Base:** IBC diario (salario/30; con salario variable, promedio — fase posterior).

**Backend:**
- `CalculadoraIncapacidad`: entra (subtipo, días, IBC diario, fecha) → devuelve
  `{ valorEmpleador, valorEntidad, total, traza }` separando quién paga.
- El motor agrega la línea de devengado por incapacidad y marca días de ausencia
  (F0). `numero_autorizacion` y entidad obligatorios.
- Conceptos nuevos: `INC_EG`, `INC_AT`, `INC_MATERNIDAD`.

**Front:** al crear novedad INCAPACIDAD → subtipo + fechas + nº autorización; el
sistema calcula el valor (editable con override + motivo).

**Criterio:** incapacidad EG de 5 días con salario 1.300.000 da el valor de ley y
separa los 2 días del empleador de los 3 de EPS.

---

## F2 — Licencias ✅ HECHO (2026-07-20)

> Cubierto por F0 (LNR descuenta días) + F1 (maternidad 100%) + el resolver PILA
> existente (`ResolverNovedadesPila` ya mapea LNR→SLN, remunerada/vacaciones→VAC-LR,
> maternidad→LMA, incapacidad→IGE/IRL). Front: la licencia remunerada ahora pide
> fechas (no descuenta salario) para que PILA la reporte. Pendiente de verificar:
> base de aportes durante LNR (salud con piso, sin pensión) contra contador.


- **LICENCIA_REMUNERADA** (luto 5 días, etc.): se paga al 100%, cuenta como tiempo
  laborado para prestaciones, aportes siguen.
- **LICENCIA_NO_REMUNERADA (LNR):** **no se paga**; descuenta días de salario (F0).
  Contrato en **suspensión**: salud y pensión **se siguen cotizando** (regla a
  confirmar con contador: base y quién asume). Marcar novedad en PILA (SLN).
- **LICENCIA_MATERNIDAD/PATERNIDAD:** 100% a cargo EPS (reusa F1 maternidad).

**Backend:** el motor trata REMUNERADA como día pagado, NO_REMUNERADA como ausencia
sin pago. Novedad LNR debe reflejarse como novedad SLN en PILA (ver [[plan-validacion-pila]]).

**Front:** "el sistema la crea" → alta guiada de licencia con tipo + fechas; calcula
días y efecto automático.

**Criterio:** LNR de 3 días baja el salario proporcional 3 días y no rompe PILA.

---

## F3 — Vacaciones con saldo (EL HUECO PEDIDO) 🎯 ✅ HECHO (2026-07-20)

> Implementado y compilando. V130 (`permite_vacaciones_anticipadas` en config +
> `vacaciones_saldo_inicial` en empleados), `VacacionesService.saldo` (causadas
> 15/360 × antigüedad + saldo inicial − tomadas), endpoint
> `GET /nomina/empleado/{id}/vacaciones-saldo`, validación en `agregarNovedad`
> (bloquea si excede y no hay anticipadas), front: chip de saldo en el form de
> novedad + botón deshabilitado si excede + toggle en config de nómina. Falta:
> correr V130 y probar; carga masiva de saldo inicial va en F7.


**Problema:** "que vayan a crear vacaciones y el empleado no cubra." Hoy no hay
saldo: se puede pedir vacaciones sin días causados.

**Backend:**
- Tabla `vacaciones_saldo` / vista: por empleado, `dias_causados`
  (1.25/mes = 15/360 desde ingreso) − `dias_tomados` − `dias_compensados` +
  `saldo_inicial` = **saldo disponible**.
- Al crear novedad VACACIONES: validar `diasSolicitados ≤ saldoDisponible`. Config
  `permite_vacaciones_anticipadas` (default **false** → bloquea; true → advierte).
- Registrar días tomados y descontar del saldo. Vacaciones disfrutadas: se pagan
  (salario **sin auxilio**, ya está en `CalculadoraPrestaciones.vacaciones`), son
  ausencia de salario ordinario (F0) pero **cuentan como tiempo de servicio**.
- **Saldo inicial** por empleado (vienen de otro sistema) → F7.

**Front:** al pedir vacaciones mostrar saldo disponible; si no cubre, bloquear o
advertir según config, con el faltante explícito.

**Criterio:** empleado con 8 días de saldo no puede tomar 15 (o se advierte); al
tomar 8, el saldo queda en 0 y la próxima liquidación lo refleja.

---

## F4 — Prima / Cesantías / Liquidación definitiva (E2E) ✅ HECHO (2026-07-20)

> Ya existían crear individual, liquidación definitiva, lotes, pago con asiento.
> Agregado: **generación masiva** `POST /api/prestaciones/generar-lote` (prima/
> cesantías/intereses para todos los activos en un lote, recortando al ingreso) +
> **consumo del saldo inicial de cesantías** (F7) en la primera liquidación (se
> suma y se pone en cero). Front: botón "Generar en lote" + diálogo en prestaciones.
> Pendiente: prima/cesantías en la nómina electrónica (va con F6).


`CalculadoraPrestaciones` ya calcula. Falta cerrar el circuito:
- Prima: generar el pago de **jun (30/06)** y **dic (20/12)** por semestre, con
  asiento contable y en nómina electrónica (`prim`).
- Cesantías: liquidación anual + **consignación al fondo antes del 14/02**;
  intereses al empleado antes del 31/01. Asiento + reporte.
- **Liquidación definitiva** al retiro: cesantías + intereses + prima proporcional +
  vacaciones + indemnización (ya existe) en un solo documento.
- Verificar `PrestacionService`/`PrestacionController` end-to-end.

**Criterio:** correr prima de un semestre y liquidación definitiva de un retiro;
cuadran a mano y generan asiento.

---

## F5 — Retefuente parametrizada ✅ HECHO (2026-07-20)

> Ya estaba casi todo: conceptos `RETEFUENTE_P1/P2` (V108), UVT + rangos art. 383
> (V108), `CalculadoraRetefuente` completa (depuración legal), motor cableado,
> front `contratos/retefuente`. Default de contrato = procedimiento 1. Agregado:
> **fallback de UVT** (si falta el año, usa el más reciente y avisa) para que no
> deje de retener en silencio en años futuros. Pendiente operativo: cargar el UVT
> nuevo cada diciembre cuando la DIAN lo publique.


- Sembrar conceptos `RETEFUENTE_P1` / `RETEFUENTE_P2` y validar `CalculadoraRetefuente`
  (UVT del año, procedimiento 1/2, depuración: aportes obligatorios, renta exenta 25%,
  dependientes, medicina prepagada, intereses vivienda).
- Para constructoras con salarios bajos casi nunca causa, pero **no debe fallar en
  silencio**: si un empleado la causa y no está el concepto, hoy solo hay `warn`.

**Criterio:** un salario que cause retención la retiene; uno que no, no.

---

## F6 — Nómina electrónica completa

Extender `FactusNominaV2Builder` (hoy solo caso regular) para mapear:
- `inca[]` incapacidades, `lice[]` licencias, `vaca[]` vacaciones.
- `prim` prima, `cesa[]` cesantías/intereses.
- `dedu` FSP (fondo solidaridad), `rete` retención en la fuente.

Ver [[nomina-electronica-factus-v2]]. Cada uno con su `*_type_code` de las tablas
oficiales de Factus.

**Criterio:** una nómina con incapacidad + una de mes de prima generan CUNE aceptado.

---

## F7 — Saldos iniciales / migración desde otro sistema ✅ HECHO (2026-07-20)

> V131 (`empleados.cesantias_saldo_inicial`, `ingresos_ytd`, `retenciones_ytd`;
> vacaciones ya en V130). Endpoints `GET/PUT /api/empleados/saldos-iniciales`.
> Pantalla `features/nomina/saldos-iniciales` (tabla editable con fecha ingreso,
> vacaciones/días, cesantías, YTD) + ítem en el sidebar. El saldo de vacaciones ya
> lo consume F3; **cesantías y YTD quedan capturados** — su consumo en la
> liquidación va con F4 (cesantías) y F5 (retefuente proc. 2 / certificado).


Las empresas **vienen de otro sistema** → sin esto los cálculos anuales salen mal:
- Por empleado: `fecha_ingreso` real, **saldo inicial de vacaciones** (días),
  **cesantías acumuladas**, y **acumulado YTD** (para retefuente procedimiento 2 y
  para el certificado de ingresos y retenciones).
- Pantalla/carga masiva de saldos iniciales.

**Criterio:** empleado con 1 año de antigüedad importado calcula vacaciones y
cesantías proporcionales correctas.

---

## F8 — Pruebas end-to-end (ciclo en paralelo) ✅ CHECKLIST LISTO (2026-07-20)

> No es código: es el procedimiento de validación. Documentado en
> `docs/CHECKLIST_CICLO_PARALELO.md` con casos concretos (incapacidad EG/ARL, LNR,
> vacaciones sin saldo, prima en lote, cesantías con saldo inicial, liquidación
> definitiva), tabla de comparación peso-por-peso, pasos de PILA y CUNE, y
> criterios de aceptación. **Falta ejecutarlo con datos reales.**


- Sembrar una constructora real de prueba con empleados + saldos iniciales.
- Correr **un mes completo** con incapacidad, LNR y vacaciones; comparar **peso por
  peso** contra el sistema actual del cliente.
- Generar archivo **PILA** y validarlo con el operador.
- Sacar **un CUNE** de nómina electrónica aceptado.
- Recién ahí: producción.

---

## Orden sugerido

`F0` (cimiento) → `F3` vacaciones (el hueco pedido) → `F1` incapacidades → `F2`
licencias → `F7` saldos iniciales → `F4` prestaciones E2E → `F5` retefuente →
`F6` electrónica completa → `F8` ciclo en paralelo.

F0 primero porque sin conciliación de días, F1/F2/F3 no pueden ajustar salario solos.
