# Plan: conceptos de nómina con fórmulas y cargos como plantilla

Fecha: 2026-10-24. Continúa el rediseño de nómina (docs/PLAN_NOMINA_REDISENO.md).

## 1. Objetivo

1. Los **conceptos** se identifican con un **código numérico** por empresa (1 SUELDO,
   32 SALUD…). Nunca un código de texto a la vista del usuario.
2. Cada concepto tiene pestañas: Información general · Fórmula · Afectado por ·
   Hace base · Contable · Centros de costo.
3. La **fórmula** es dinámica: el usuario la escribe con un editor asistido y la
   prueba contra un empleado antes de guardarla.
4. El **cargo** es la plantilla: define sus conceptos, sus novedades permitidas y
   los topes de horas extra y recargos. La vinculación los hereda.
5. Un **buscador avanzado** de conceptos permite agregarlos al cargo (y a la
   vinculación) en lote.

## 2. Estado de partida (2026-10-24)

| Pieza | Hoy |
|---|---|
| `concepto_nomina` | `codigo` VARCHAR (`APO_SALUD`…) que el motor, la PILA, la NE y la contabilidad usan como llave. `base` = enum acotado. Globales (`empresa_id` NULL) + personalización por empresa por el mismo código. |
| `concepto_nomina_base` | Existe (V104): qué conceptos entran en la base de otro. Solo lo usa PRO_INT_CES. |
| `nomina_cargo` | Código, nombre, nivel, tipo, plazas, rango salarial, misión, funciones. |
| `vinculacion_concepto`, `vinculacion_novedad_permitida` | Por contrato y por `concepto_codigo` (texto). El motor ya los aplica (R3.2d). |
| `nomina_concepto_contable` | Cuenta/tercero por `concepto_codigo` (V218). |
| Motor | Códigos escritos en el código (`APO_SALUD`, `RETEFUENTE_P1`, exonerados 1607, `AUX_TRANSP`…). |

## 3. Decisiones

| # | Decisión | Elegido |
|---|---|---|
| D1 | Conceptos de ley | Siguen siendo globales; al editarlos la empresa obtiene su copia (personalización, mismo mecanismo de hoy). Marca `de_ley`: clase, tipo, cálculo y número quedan bloqueados en la copia. |
| D2 | Herencia cargo → vinculación | **Viva**: la vinculación toma lo del cargo y solo guarda excepciones (agregar, quitar, cambiar tope). Cambiar el cargo pide fecha desde; lo liquidado no cambia. |
| D3 | Presupuestal | Fuera de alcance por ahora (solo contable). |
| D4 | Novedad por encima del tope | Si la fila dice "requiere autorización" → queda pendiente de autorizar (R9); si no → se rechaza. |
| D5 | Código de texto | El usuario no lo ve ni lo escribe. Durante la transición `concepto_nomina.codigo` queda como **clave interna** (se genera sola en conceptos nuevos). La fase 2 la retira del motor: todo pasa a `tipo_id` y `concepto_id`. |
| D6 | Fórmulas | Lenguaje de expresiones cerrado, evaluado con un intérprete propio o EvalEx (BigDecimal, solo funciones registradas). **Nunca** `eval()`, SpEL ni scripts. |

## 4. Modelo de datos

### 4.1 `nomina_concepto_tipo` (catálogo del sistema, ids fijos)
Qué **es** el concepto. Lo usa el motor, la PILA, la NE y la contabilidad; el
usuario lo escoge en un select. Ids fijos en semilla y espejados en el enum Java
`TipoConceptoNomina`.

`id INT PK · nombre · clase · codigo_dian · activo`

### 4.2 `concepto_nomina` (nuevas columnas)
| Columna | Valores |
|---|---|
| `numero` INT | Código visible. Único por empresa (comparte número con sus versiones de vigencia y con el global que personaliza). |
| `tipo_id` INT → `nomina_concepto_tipo` | |
| `clase` | + `NO_IMPUTABLE` (cuenta días, no mueve plata). |
| `entrada` | `NINGUNA` · `VALOR` · `CANTIDAD` · `PORCENTAJE` |
| `redondeo_precision` | `NINGUNO` · `ENTERO` · `DECENA` · `CENTENA` · `MIL` |
| `redondeo_modo` | `AUTOMATICO` · `ARRIBA` · `ABAJO` |
| `frecuencia` | `CADA_PAGO` · `MENSUAL` · `SEMESTRAL` · `ANUAL` · `LIQUIDACION` |
| `grupo_id` → `nomina_catalogo` (GRUPO_CONCEPTO) | |
| `aplica_art383` BOOLEAN | |
| `de_ley` BOOLEAN | |
| `descripcion` TEXT, `updated_at` | |
| (fase 3) `formula` TEXT, `formula_dependencias` | |

Los conceptos existentes reciben número y tipo por migración. Numeración de los
globales: 1–99 devengados, 100–199 deducciones, 200–299 aportes, 300–399
provisiones. Los propios de cada empresa que no casen con un global, desde 1000.

### 4.3 Bases acumuladoras (fase 2)
`nomina_base_acumuladora` (IBC, PRESTACIONAL, VACACIONES, PRIMA, RETEFUENTE…) y
`concepto_hace_base (concepto_id, base_id)`. Reemplaza `constituye_ibc` y el enum
`base`. "Afectado por" es la vista inversa.

### 4.4 Cargo (fase 5)
- `nomina_cargo_concepto (cargo_id, concepto_numero, valor, porcentaje, fecha_desde, fecha_hasta)`
- `nomina_cargo_novedad (cargo_id, concepto_numero, tope_cantidad, tope_valor, requiere_autorizacion)`
- `nomina_cargo_tope_horas (cargo_id, tipo_hora, tope_periodo, tope_mes)`; nivel
  dirección/confianza/manejo ⇒ tope 0 (art. 162 CST).
- Columnas `perfil`, `desempeno` y adjuntos en R2.
- Vinculación: columna `origen` (CARGO / PROPIO) y `excluido` en sus tablas.

## 5. Lenguaje de fórmulas (fase 3)

```
SALARIO_BASICO / 30 * DIAS_LIQUIDADOS
BASE_IBC * 4%
SI(SALARIO_BASICO <= 2 * SMMLV, AUX_TRANSPORTE / 30 * DIAS_LIQUIDADOS, 0)
[1] * 8.33%
PROMEDIO([281], 12) / 12
TABLA("FSP", BASE_IBC / SMMLV) * BASE_IBC
```
- Variables del empleado, del período y de las bases; `[n]` = valor del concepto n.
- Funciones registradas: SI, MIN, MAX, REDONDEAR, PROMEDIO, ACUMULADO, TABLA, DIAS_ENTRE.
- Al guardar: se analiza, se extraen dependencias, se ordena por grafo y se
  rechazan ciclos (el campo `orden` deja de ser manual).
- "Probar con empleado": evalúa sin guardar y devuelve la traza paso a paso.
- Límites: longitud, profundidad y tiempo de evaluación.

## 6. Fases

| Fase | Contenido | Estado |
|---|---|---|
| **F1** | Concepto ampliado: `numero`, `tipo_id`, entrada, redondeo, frecuencia, grupo, art. 383, NO_IMPUTABLE, `de_ley`. Migración V230. API: buscar paginado con filtros, obtener, editar (personaliza globales), tipos, siguiente número. Motor sin cambios. | Hecho (local) |
| F2 | Redondeo del concepto aplicado en el motor (`RedondeoConcepto`); "Hace base" prestacional (V233 `hace_base_prestacional`, entra a `BasePrestacional`); "Afectado por" calculado (`/api/concepto/{id}/afectado-por`); NO_IMPUTABLE no mueve plata. **Pospuesto:** retirar la clave interna de texto del motor/PILA/NE/asiento (invisible para el usuario, alto riesgo). | Hecho (local), con lo pospuesto |
| F3 | Lenguaje de fórmulas propio (`AnalizadorFormula` + `EvaluadorFormula`, sin eval): variables, [n], SI/MIN/MAX/REDONDEAR/Y/O/NO, 4%. Base FORMULA en el motor con traza. Al guardar: valida, conceptos existentes, sin ciclos, orden automático. API validar/probar/variables. Editor en el front (atajos, validación en vivo, probar). | Hecho (local) |
| F4 | Front: listado paginado con filtros, página del concepto con pestañas (General, Fórmula, Afectado por, Hace base, Contable, Centros de costo), nueva vigencia (`POST /{id}/vigencia`), `<app-concepto-buscador>` (se conecta en F5). El editor de fórmula llega con F3. | Hecho (local) |
| F5 | Cargo con pestañas (General, Conceptos, Novedades permitidas, Misión, Funciones, Perfil, Desempeño, Máximo de horas extra y recargos, Adjuntos). V231 (`nomina_cargo_concepto`, `_novedad`, `_tope_horas`, `_adjunto`, perfil/desempeño). API `/api/nomina/cargos/{id}/…`; topes legales (2/día, 12/semana) y directivo sin horas extra; copiar de otro cargo. | Hecho (local) |
| F6 | Herencia viva cargo → vinculación en el motor (`VinculacionReglasQueryRepository`): conceptos con valor del cargo como propios, novedades permitidas del cargo, topes de horas (`ReglasTopesHoras`), conceptos de empresa solo si están en la plantilla (`ReglasConceptosCargo`; los de ley siempre), autorización con novedades APROBADA. V232 `excluido`. Vinculación muestra lo heredado (Del cargo) con Personalizar / Excluir / Volver a heredar. | Hecho (local) |
| F7 | Pestaña Contable (cuentas y tercero del concepto, mismo servicio de Cuentas de nómina) y Centros de costo (centro fijo por concepto, V233 `centro_costo_id`; el motor no lo reparte). | Hecho (local) |

## 7. Riesgos

- El código de texto es la llave en ~20 clases (motor, PILA, NE, asiento,
  prestaciones, definitiva, exógena). F2 se hace en un solo lote con pruebas de
  reliquidación comparando antes/después.
- Personalizar un global congela su tarifa para la empresa: un cambio de ley en el
  global no la alcanza. Mitigación: `de_ley` bloquea los campos de cálculo y una
  actualización de plataforma puede reescribirlos.

## 8. Reglas de la herencia (F6)

- **Conceptos de ley:** siempre se liquidan; el cargo no los quita ni la vinculación los excluye.
- **Conceptos de la empresa calculados por el motor:** si el cargo tiene plantilla de conceptos, solo
  se liquidan los que estén en ella y la vinculación no excluya. Cargo sin plantilla = todos (como antes).
- **Concepto del cargo con valor, porcentaje o cantidad propios:** se paga como concepto propio
  ("Concepto del cargo" en la traza). Si la vinculación tiene fila propia del mismo concepto, gana la
  de la vinculación. Un concepto que sale como propio no se calcula otra vez en el catálogo.
- **Novedades permitidas:** cargo + vinculación (la de la vinculación reemplaza a la del cargo). Lista
  vacía = no restringe. Pasar el tope con "requiere autorización" vale si todas las novedades de ese
  tipo están APROBADAS.
- **Topes de horas:** por día y semana con la fecha de la novedad; el del mes contra el total del período.
  La autorización no los levanta.

## 9. Pendiente consciente

- **Clave interna de texto** (`concepto_nomina.codigo`): el usuario ya no la ve en ninguna pantalla de
  conceptos, cargos, vinculación, cuentas de nómina ni liquidación. Sigue siendo la llave interna del
  motor, PILA, nómina electrónica, asiento, prestaciones y exógena. Cambiarla a `tipo_id`/`concepto_id`
  es un refactor de ~20 clases sin cambio visible; se hace en un lote propio con pruebas de
  reliquidación antes/después.
- **Funciones históricas** en fórmulas (PROMEDIO, ACUMULADO, TABLA) y bases de vacaciones y retención
  configurables.
- **Autorización de novedades (R9):** hoy solo las de asistencia llegan APROBADAS.
