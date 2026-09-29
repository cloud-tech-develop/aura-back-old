# Plan para mejorar la PILA — hacia un validador tipo UGPP

Contraste entre **lo que hay hoy** en el código y **lo que exige el agente validador de PILA**, y un plan por fases para cerrar la brecha.

> Regla de oro (del agente): **el validador NO liquida nómina**. Solo revisa, compara, clasifica hallazgos y decide si la planilla está LISTA / LISTA_CON_ADVERTENCIAS / BLOQUEADA / NO_EVALUABLE. El generador actual ya respeta esto (toma los aportes de la nómina, no los recalcula).

---

## 1. Qué tenemos hoy

**Modelo de datos (V111 + V119) — sólido y bien pensado:**
- `pila_encabezado` (aportante, snapshot), `pila_planilla` (registro 01), `pila_cotizante` (registro 02, ~120 columnas), `pila_archivo`.
- IBC y días **por subsistema** (pensión/salud/ARL/CCF distintos), banderas de novedad (ING, RET, TDE/TAE, TDP/TAP, VSP, VST, SLN, IGE, LMA, VAC_LR, IRL…) con **pares de fechas**, entidades como **códigos literales** (snapshot).
- V119 agregó a `nomina_novedad`: `fecha_inicio/fecha_fin`, `subtipo` (INCAPACIDAD → GENERAL|RIESGO_LABORAL), `numero_autorizacion`, y tipos nuevos (SLN, LMA, VAC, SUSPENSION).

**Generación (`GeneradorPila`):**
- Arma encabezado + planilla + un cotizante por contrato vigente.
- Deriva banderas (`ResolverNovedadesPila`), calcula IBC/días por subsistema (`BasesPila`), consolida quincenas.
- **Toma los aportes de la nómina liquidada, no los recalcula** (correcto).
- Períodos distintos salud (anticipado) vs pensión/ARL/CCF (vencido).

**Validación previa (`AfiliacionService.validarParaPila`) — mínima:**
- Verifica: tipo de cotizante presente, afiliaciones obligatorias vigentes con **código oficial** de catálogo, nivel de riesgo ARL / centro de trabajo.
- Devuelve `List<String>` y **bloquea la generación con 409** si hay problemas.

**Frontend (`nomina/pila`):** generar por período, listar cotizantes, mostrar los problemas del 409.

**Explícitamente NO hecho:** archivo plano (depende del operador), representante legal / clase-naturaleza de aportante / código de operador / actividad económica (hoy son TODO/warn).

---

## 2. Brechas frente al agente validador

El agente describe un **motor de validación de 17 pasos** que produce **hallazgos estructurados** (código `PILA-XXX-NNN`, severidad, campo, valor recibido, condición esperada, acción, fundamento, riesgo) y **estados** por cotizante y por planilla. Hoy tenemos un chequeo plano previo. Brechas:

| # | Área (paso del agente) | Hoy | Brecha |
|---|---|---|---|
| 1 | Hallazgos estructurados + severidades | `List<String>` bloqueante | Falta modelo de hallazgo (código/severidad/campo/acción) y catálogo `PILA-*` |
| 2 | Estados cotizante/planilla | binario (genera o 409) | Falta APTO / APTO_CON_ADVERTENCIAS / BLOQUEADO / NO_EVALUABLE |
| 3 | Contexto de planilla (Paso 1) | implícito | Falta validar tipo planilla, tipo/clase/naturaleza aportante, exoneración, parámetros del período |
| 4 | Tipo/subtipo cotizante (Paso 3) | solo "presente" | Falta matriz de compatibilidad (subtipo, aportante, planilla, subsistemas, novedades) |
| 5 | Identificación (Paso 5) | parcial | Falta longitud/formato por tipo doc, duplicados, coherencia extranjero |
| 6 | Días (Paso 6) | no valida | Falta 0–30, sin decimales, coherencia con novedades/fechas, suma ≤ 30 por subsistema |
| 7 | Novedades (Paso 7) | se derivan, no se validan | Falta catálogo por período, permitidas por tipo cotizante/planilla, incompatibilidades, coherencia fechas↔días↔IBC |
| 8 | Fechas (Paso 8) | parcial | Falta coherencia ingreso≤retiro, novedad dentro de período, días entre fechas |
| 9 | IBC (Paso 9) | se calcula, no se valida | Falta mínimo legal/proporcional, tope, salario integral, IBC=0 permitido, coherencia entre subsistemas → ADVERTENCIA |
| 10 | Entidades (Paso 10) | código oficial obligatorio | Falta catálogo con **vigencia** por período, entidad↔subsistema, coherencia traslado |
| 11 | ARL/clase riesgo (Paso 11) | parcial | Falta tarifa↔clase de riesgo, centro de trabajo, actividad económica |
| 12 | Tarifas (Paso 12) | se copian de config | Falta tarifas vigentes por período/tipo cotizante/exoneración |
| 13 | Control de aportes (Paso 13) | no compara | Falta `IBC×tarifa` vs aporte reportado con tolerancia de redondeo → INFORMACIÓN/ERROR |
| 14 | Exoneración (Paso 14) | booleano copiado | Falta validar aplicabilidad y coherencia salud/SENA/ICBF |
| 15 | Múltiples registros (Paso 15) | 1 línea por contrato | Falta partir en varias líneas por cambio de IBC/traslado/novedad; detectar duplicados |
| 16 | Estado del empleado (Paso 16) | implícito | Falta coherencia estado ERP ↔ novedad PILA (retiro/suspensión/reingreso) |
| 17 | Riesgos UGPP (Paso 17) | no | Falta señalar omisión/inexactitud/IBC inferior/días inferiores |
| 18 | Archivo: encabezado/detalle/totales (Sección 9) | encabezado incompleto | Falta rep. legal, aportante completo, totales por subsistema y su cuadre |
| 19 | Archivo plano | no | Depende del operador (Aportes en Línea / SOI): layout ~120 columnas |
| 20 | Front del reporte | solo lista 409 | Falta semáforo por cotizante, hallazgos por severidad, estado de planilla |

---

## 3. Principio de arquitectura

Crear un **motor de validación independiente del generador**, de solo lectura, que corra sobre el modelo ya generado (`pila_cotizante`) y/o sobre contratos+nómina, y produzca un **reporte de validación** — sin tocar ni corregir datos (regla del agente).

```
ValidacionPilaService
  ├── contexto(encabezado, planilla, parámetros período)   → hallazgos CTX/APO/PLA
  └── por cada cotizante:
        Regla identificación (ID) · días (DIA) · fechas (FEC) · IBC (IBC)
        novedades (NOV) · entidades (ENT) · tarifas (TAR) · valores (VAL)
        múltiples registros (REG) · riesgos UGPP (UGPP)
        → List<HallazgoPila> + EstadoCotizante
  → EstadoPlanilla + totales + reporte
```

- **Modelo `HallazgoPila`**: `codigo, severidad (ERROR|ADVERTENCIA|INFORMACION|NO_EVALUABLE), campo, valorRecibido, descripcion, condicionEsperada, accionSugerida, fundamento, riesgo, cotizanteId`.
- **Catálogo de códigos** = el de la sección 14 del agente (`PILA-CTX/APO/PLA/ID/COT/ENT/DIA/NOV/IBC/TAR/VAL/FEC/REG/ARC/UGPP/OK`).
- **Estados**: cotizante (APTO / APTO_CON_ADVERTENCIAS / BLOQUEADO / NO_EVALUABLE) y planilla (LISTA / LISTA_CON_ADVERTENCIAS / BLOQUEADA / NO_EVALUABLE).
- **Sin catálogo/parámetro ⇒ NO_EVALUABLE** (nunca inventar tarifas, códigos ni compatibilidades).

---

## 4. Plan por fases

### P1 — Motor de hallazgos con lo que YA existe (base) ✅ HECHO (2026-07-20)
Reglas que no dependen de catálogos nuevos:
- Contexto (Paso 1), identificación (Paso 5), días 0–30 y coherencia con novedades (Paso 6), fechas (Paso 8), rangos de IBC con SMMLV+tope (Paso 9), aporte obligatorio + banda de efectividad (Paso 13, conservador), duplicados del mismo documento (Paso 15 básico).
- **Entregado**: enums `SeveridadPila` / `EstadoCotizantePila` / `EstadoPlanillaPila` (`services/nomina/pila/validacion/`), DTOs `ValidacionPilaDtos` (Hallazgo/CotizanteValidacion/ReporteValidacion), `ValidacionPilaService`, y endpoint `GET /api/pila/{periodo}/validar`. Backend compila.
- Lo que depende de catálogo → **NO_EVALUABLE** (tipo de cotizante `PILA-COT-002`, etc.).
- **Conservador a propósito** (evitar falsos ERROR): IBC bajo SMMLV → ADVERTENCIA (no ERROR, hay excepciones de tiempo parcial); control de aportes por banda de efectividad (el control exacto `IBC×tarifa` con redondeo oficial se hace en P2 cuando estén las tarifas/base confirmadas).
- Pendiente P1: **frontend del reporte** (es P7 en este plan).

### P2 — Catálogos y matrices oficiales (Resolución 2388/2016, Anexo Técnico 2)
- Tablas paramétricas por período: tipos de cotizante y subtipos, tipos/clase/naturaleza de aportante, tipos de planilla, catálogo de novedades, **catálogo de entidades EPS/AFP/ARL/CCF con vigencia**, tarifas por subsistema/período/exoneración, matrices de compatibilidad.
- Habilita los Pasos 3, 7, 10, 12 (hoy NO_EVALUABLE) → ERROR/OK reales.

**P2a ✅ HECHO (2026-07-20): catálogo de tipos de cotizante + subsistemas obligatorios.**
- `V123__pila_tipo_cotizante.sql` (catálogo GLOBAL: código, nombre, obligaciones salud/pensión/ARL/CCF, activo, vigencia). Subconjunto inicial: 01 Dependiente, 02 Servicio doméstico, 51 Tiempo parcial (obligaciones establecidas en ley). **No inventa códigos**: un tipo que no esté en el catálogo → NO_EVALUABLE (no ERROR).
- Entidad `PilaTipoCotizanteEntity` + `PilaTipoCotizanteRepo`. Motor cableado:
  - `PILA-COT-002` ahora real: tipo en catálogo y activo → OK; inactivo → ERROR; ausente del catálogo → NO_EVALUABLE.
  - **Paso 10 (entidades)** activado: exige EPS/AFP/ARL/CCF (`PILA-ENT-001..004`) SOLO cuando el tipo de cotizante obliga a ese subsistema.
- Backend compila. El front del reporte (P7) ya muestra los hallazgos nuevos sin cambios.

**P2b ✅ HECHO (2026-07-20): catálogo de entidades EPS/AFP/ARL/CCF con vigencia.**
- `V124__pila_entidad.sql` (catálogo GLOBAL: tipo, código, nombre, vigencia, activo; unique tipo+código). **Se crea VACÍO** (no se inventan códigos): mientras un tipo no tenga filas, solo se valida presencia; al cargar el catálogo de un tipo, se activa la validación de existencia y vigencia para ese tipo.
- Entidad `PilaEntidadEntity` + `PilaEntidadRepo`. Motor cableado: si el código de entidad está presente y su tipo tiene catálogo cargado → valida que exista (`PILA-ENT-005`) y esté vigente para el período.
- **Carga del catálogo**: `POST /api/pila/catalogo/entidades` (upsert bulk, `PilaCatalogoService`) + `GET /api/pila/catalogo/entidades/total`. El usuario/admin sube el catálogo oficial del período.
- Backend compila.

**P2b-2 — pendiente (acoplado a P5):** control exacto `IBC×tarifa` de aportes. Se difiere porque los aportes almacenados son del **empleador** y la **exoneración** (Ley 1607: empleador exonerado paga 0 salud/SENA/ICBF) haría saltar falsos ERROR. Se hará junto con la validación de exoneración (P5). Hoy queda el chequeo conservador por banda de efectividad (P1).

**P2c ✅ HECHO (2026-07-20): catálogos de tipo de planilla y tipo de aportante.**
- `V125__pila_catalogo.sql`: catálogo GLOBAL clave-valor genérico (`dominio`, `codigo`, `nombre`, `activo`) para las listas cortas de códigos. Seed inicial: `TIPO_PLANILLA=E` (Empleados), `TIPO_APORTANTE=1` (Empleador) — lo que produce el generador para un empleador estándar.
- Entidad `PilaCatalogoEntity` + `PilaCatalogoRepo.findByDominio`. Motor: `validarContextoCodigo` valida tipo de planilla (`PILA-PLA-001/002`) y tipo de aportante (`PILA-APO-001/002`) — ausente→NO_EVALUABLE, en catálogo y activo→OK, inactivo→ERROR, presente pero no en catálogo→NO_EVALUABLE (no inventa).
- Backend compila. (Nota: el generador aún no setea `tipoAportante` en el encabezado → sale NO_EVALUABLE hasta que P4 lo capture.)

**P2 — cerrado salvo:** matriz **novedades × tipo cotizante** (Paso 7, novedades permitidas por tipo) — se hará junto con la profundización de novedades en **P3**; y completar los catálogos (tipos de cotizante, entidades, tipo planilla/aportante) con el oficial del período (carga de datos del usuario).

### P3 — Novedades avanzadas + múltiples registros ✅ HECHO (2026-07-20, lado validador)
Reglas lógicas (sin catálogos nuevos) en `ValidacionPilaService`:
- **Novedad ↔ período/ingreso/retiro** (Paso 8): ING/RET con fecha fuera del período → `PILA-FEC-004` (advertencia); novedad posterior al retiro → `PILA-FEC-005`; anterior al ingreso → `PILA-FEC-006`.
- **Traslados** (Paso 7): TDE/TAE sin EPS de traslado o TDP/TAP sin AFP de traslado → `PILA-NOV-010`.
- **Novedad ↔ IBC** (Paso 7): SLN de mes completo con IBC de pensión > 0 → `PILA-NOV-009` (advertencia; SLN no cotiza pensión).
- **Múltiples registros** (Paso 15): EPS/AFP distintas entre líneas del mismo documento sin novedad de traslado → `PILA-REG-004` (error). (Ya estaban REG-001 duplicado exacto / REG-002 múltiples válidos de P1.)
- Backend compila. El front del reporte muestra los hallazgos nuevos sin cambios.

**Pendiente de P3:** (a) matriz **novedades permitidas × tipo cotizante** — para los tipos comunes (01/02/51) todas las estándar son permitidas, así que aporta valor solo con tipos especiales del catálogo oficial; (b) **partición en varias líneas** por cambio de IBC/traslado dentro del mes: es una mejora del **generador** (no del validador), va aparte.

### P4 — Encabezado completo + totales del archivo

**P4a ✅ HECHO (2026-07-20, lado validador):**
- **Completitud del encabezado** (Sección 9.1): representante legal incompleto → `PILA-APO-005` (advertencia); actividad económica faltante → `PILA-ARC-001` (advertencia). Se clasifican como ADVERTENCIA (no ERROR) para no bloquear hasta que exista la captura del dato (P4b); informan qué falta antes de generar el archivo.
- **Coherencia de totales** (Sección 9.3): total de cotizantes del registro 01 ≠ nº de registros 02 → `PILA-ARC-003` (error); total de la planilla ≠ suma de IBC de pensión del detalle → `PILA-ARC-004` (error).
- Backend compila.

**P4b ✅ HECHO (2026-07-20): captura de la configuración del aportante.**
- `V126__pila_aportante_config.sql` (una fila por empresa: tipo/clase/naturaleza de aportante, actividad económica CIIU, código de operador, forma de presentación, representante legal desagregado). Entidad `PilaAportanteConfigEntity` + repo + `PilaAportanteConfigService`.
- **Generador**: `GeneradorPila.armarEncabezado` ahora lee la config y la vuelca al encabezado (reemplaza el TODO). Con esto las advertencias de P4a se resuelven y `tipoAportante` deja de salir NO_EVALUABLE al configurarlo.
- **Endpoints**: `GET/PUT /api/pila/aportante-config` (tenant-scoped).
- **Frontend**: botón "Configurar aportante" en el módulo PILA + diálogo con las dos secciones (clasificación del aportante · representante legal). Modelo + service. Typecheck OK.

### P5 — Riesgos UGPP + exoneración ✅ HECHO (2026-07-20)
- **Exoneración Ley 1607 (Paso 14)** en el control de aportes: los aportes almacenados son del EMPLEADOR; con exoneración, el aporte de salud del empleador es 0 para empleados **bajo el umbral** (config `umbral_exoneracion_smmlv`, def. 10 SMMLV). Casos:
  - Exonerado y bajo umbral → aporte de salud del empleador en 0 es **correcto** (ya no da falso ERROR, el gran fix de P5). Si reporta aporte → `PILA-VAL-004` (información).
  - Exonerado y **sobre** el umbral (la exoneración no aplica) con aporte 0 → `PILA-UGPP-006` (advertencia).
  - No exonerado con aporte de salud 0 → `PILA-VAL-001` (error).
  - Pensión: nunca exonerada → `PILA-VAL-001` si aporte 0.
- **Control exacto `IBC × tarifa` (Paso 13)**: `controlAporte` compara el aporte contra `IBC × tarifa` (normaliza % o fracción) con tolerancia de redondeo (2% o $100). Diferencia relevante → `PILA-UGPP-005` (advertencia; no bloquea por la semántica empleador/redondeo). Reemplaza el chequeo por banda de P1.
- Backend compila. Reporte del front lo muestra sin cambios.

### P6 — Archivo plano (dirigido por layout, cualquier operador) ✅ HECHO (2026-07-20)
Insight: el formato PILA es un **estándar del Ministerio** (Anexo Técnico 2), no per-operador — todos los operadores aceptan el mismo layout. Lo hicimos **dinámico** (dirigido por datos):
- `V127__pila_layout_campo.sql`: tabla de layout (tipo registro 01/02, orden, código, longitud, tipo dato, relleno, alineación, activo). **Editable** → ajustar el layout no requiere recompilar. Seed inicial con los campos núcleo de registro 01 y 02.
- Entidad `PilaLayoutCampoEntity` + repo. `PilaArchivoExporter`: recorre el layout por orden, resuelve cada `codigo` del modelo (encabezado/planilla/cotizante) y formatea (ancho fijo, relleno, alineación, recorte). Money → entero en pesos; tarifas → solo dígitos.
- Endpoint `GET /api/pila/{periodo}/archivo` → contenido. Frontend: botón "Descargar archivo PILA" por planilla → `PILA_<periodo>.txt`.
- Backend compila, front typecheck OK.

**Pendiente (dato, no código):** el seed del layout es un SUBCONJUNTO INICIAL; **completar/verificar orden y longitudes contra el Anexo Técnico 2 oficial** antes de radicar en producción (y afinar el formato de tarifas). Al ser data-driven, es carga de datos en `pila_layout_campo`, no cambio de código.

---

## PLAN COMPLETO — P1 a P7 hechos (2026-07-20)
El validador tipo UGPP quedó completo (contexto, identificación, días, fechas, novedades, IBC, entidades con catálogo/vigencia, tarifas/aportes con exoneración, múltiples registros, encabezado, totales; estados por cotizante y planilla) + captura del aportante (P4b) + exportador de archivo dirigido por layout (P6) + front del reporte (P7). Quedan tareas de DATO: cargar catálogos oficiales (tipos cotizante, entidades, tipo planilla/aportante) y completar el layout del archivo contra el Anexo Técnico vigente.

### P7 — Frontend del reporte de validación ✅ HECHO (2026-07-20)
- Semáforo por cotizante (APTO/ADVERTENCIA/BLOQUEADO/NO_EVALUABLE), hallazgos agrupados por severidad con su código y acción sugerida, estado global de la planilla.
- **Entregado** (aura-frontend `features/nomina/pila`): modelos `ReporteValidacionPilaModel`/`HallazgoPilaModel`, `PilaService.validar(periodo)`, botón "Validar (tipo UGPP)" por planilla y diálogo con estado global + contadores + hallazgos de contexto + hallazgos por cotizante (código, campo, valor, descripción, acción, riesgo). Typecheck OK.
- Pendiente: bloquear el botón "generar archivo" cuando haya ERROR (se atará en P6, cuando exista el export del plano).

---

## 5. Orden sugerido
**P1 → P2 → P7** dan el mayor valor rápido: un reporte de validación real y accionable aunque el archivo plano (P6) aún no exista. P3–P5 profundizan; P6 se hace cuando se defina el operador.

## 6. Reglas que el motor debe respetar (del agente)
- No calcular nómina, no corregir datos, no inventar catálogos/tarifas/códigos.
- Sin información suficiente ⇒ NO_EVALUABLE (no aprobado).
- Un cotizante puede tener múltiples hallazgos; priorizar ERROR → riesgo UGPP → ADVERTENCIA → INFORMACIÓN.
- No declarar la planilla LISTA sin validar encabezado + detalle + totales.
