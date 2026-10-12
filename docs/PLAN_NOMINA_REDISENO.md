# Plan: rediseño del módulo de nómina (vinculación → prenómina → nómina)

Fecha: 2026-10-10 · Estado: **propuesta para aprobar** · Agente: `.claude/agents/nomina-aura.md`

## 1. Por qué

El motor de cálculo de Aura ya liquida bien (días, incapacidades, vacaciones con saldo, prestaciones con
promedio variable, retefuente, FSP, Ley 1607, aprendices). Lo que falta es **la forma de trabajar** de una
nómina de empresa mediana o grande (50–300 empleados):

- Hoy el contrato es una ficha corta; no hay **tipo de vinculación**, **grupo de nómina**, cargo de catálogo,
  período de prueba, jornada, jefe, sede ni soporte.
- Hoy el "período" es el documento de nómina, sin **tipo de comprobante**, sin **prenómina** para revisar y
  sin separar nóminas por grupo (administrativos, operativos, aprendices).
- Hoy **Prestaciones** junta prima, cesantías, intereses y vacaciones en una pantalla; en la práctica la
  prima y las cesantías se liquidan como un documento más (comprobante propio) y **solo las vacaciones**
  necesitan módulo aparte (causación, disfrute, saldo).
- El retiro es un botón "terminar contrato"; no hay **módulo de retiros** que lleve a la liquidación
  definitiva y deje historial.
- No hay **incremento salarial** masivo (cada enero se edita empleado por empleado) ni **archivo plano de
  cesantías** para consignar a los fondos.

Fuera de alcance: todo lo específico de instituciones educativas (cátedra, docentes por categoría, cargas
académicas, puntos) y el manejo presupuestal público.

## 2. Mapa de módulos (menú RR. HH. → Nómina)

| Módulo | Qué es | Hoy en Aura |
|---|---|---|
| Empleados | Datos personales, bancarios, hoja de vida | Existe |
| **Vinculaciones** | El contrato completo del empleado con la empresa | `ContratoLaboral` (ampliar) |
| Novedades | Horas extra, bonificaciones, préstamos, embargos, ausentismos; autorización | Parcial |
| **Vacaciones** | Causación por período, disfrute (tiempo/dinero), retorno anticipado, colectivas | Dentro de Prestaciones |
| **Prenómina / Nómina** | Documento por tipo de comprobante con empleados, liquidación y detalle | `PeriodoNomina` (evolucionar) |
| **Retiros** | Registro del retiro → liquidación definitiva | "Terminar contrato" |
| **Incremento salarial** | Aumento masivo con fecha de aplicación (y retroactivo si aplica) | No existe |
| **Cesantías – archivo plano** | Consignación anual a los fondos | No existe |
| PILA · Nómina electrónica · Reportes | Salidas | Existen (con brechas del agente) |
| Configuración | Conceptos, tipos de vinculación, tipos de comprobante, grupos, cargos, motivos | Parcial |

## 3. Vinculación (ampliar `ContratoLaboral`)

Formulario en una card con pestañas (estándar de Aura):

**General**
- Código (consecutivo), **estado** (Activa · Suspendida · Retirada), empleado.
- **Tipo de vinculación** (catálogo de la empresa: "Administrativo indefinido", "Operario obra",
  "Aprendiz SENA", "Temporal"…). Del tipo salen los conceptos permitidos y en qué comprobantes entra.
- **Temporal** (sí/no) · **Tipo de contrato** (indefinido, fijo, obra o labor, aprendizaje, ocasional).
- **Cargo** (catálogo de cargos con nivel y salario de referencia) · **Grupo de nómina** (para liquidar
  juntos).
- **Tipo de remuneración** (mensual ordinario, salario integral, por día/jornal, por hora) y **valor**.
- **Frecuencia de pago** (mensual, quincenal, semanal) · **Horas de trabajo/semana** (jornada legal vigente
  por Ley 2101: 46 → 44 → 42 h) · **Calendario laboral** (festivos y días hábiles).
- **Vigencia** (fecha de inicio) · **fecha final** (fijo/obra) · **período de prueba** y **fin del período
  de prueba** (alerta antes de vencer, igual que el vencimiento del contrato).
- Ley de primer empleo / beneficio tributario (sí/no, informativo para el contador).

**Ubicación y jerarquía**
- **Sede**, **área/dependencia**, departamento y municipio del lugar de trabajo, dirección y teléfono.
- **Jefe directo** (autoriza novedades y vacaciones) · **Evaluador** (opcional).
- Centros de costo con % (ya existe).

**Seguridad social** (ya existe en parte)
- Tipo y **subtipo de cotizante**, **empleado en el extranjero** (no cotiza salud/pensión en Colombia),
  afiliaciones EPS/AFP/ARL/caja/fondo de cesantías con fecha, nivel de riesgo ARL y centro de trabajo.

**Soporte**
- **Acto administrativo / documento interno** (número, fecha, archivo) y **documento externo** (contrato
  firmado), adjuntos con el almacenamiento R2 que ya existe.

## 4. Tipos de comprobante de nómina (configuración)

Cada empresa define sus comprobantes; cada uno con consecutivo propio:

| Clase | Ejemplos | Qué liquida |
|---|---|---|
| **Periódico** | "Nómina administrativa", "Nómina operativa", "Aprendices" | Conceptos permanentes + novedades del período. Se asocia a **tipos de vinculación**: solo esos empleados se pueden agregar. |
| No periódico | **Prima de servicios** (junio/diciembre) | Prima del semestre. |
| No periódico | **Cesantías** e **intereses de cesantías** | Consolidado anual (cesantías → fondo, intereses → empleado). |
| No periódico | **Liquidación definitiva** / **reliquidación definitiva** | Retiro: prestaciones, vacaciones, indemnización, descuentos pendientes. |
| No periódico | **Retroactivo** | Diferencias por incremento con fecha anterior. |
| No periódico | Bonificación / otros pagos | Pagos fuera de nómina ordinaria. |

## 5. Prenómina → nómina (evolución de `PeriodoNomina`)

### 5.1 Encabezado
Documento (Prenómina / Nómina) · número · **tipo de comprobante** · fecha · **período de días trabajados**
(desde–hasta) · **fecha de liquidación** · **fecha de pago** · **simulación** (sí/no) · descripción ·
estado (Preliminar · Confirmado · Anulado) con fecha de confirmación · total.

### 5.2 Pestañas (aparecen al guardar el encabezado)
1. **Empleados** — agregar por *tipo de lista*: un empleado · una vinculación · un grupo de nómina · un
   tipo de vinculación · todos los activos del comprobante. Muestra vinculación, cargo, salario, centro de
   costo; quitar uno o varios. Valida que no estén ya liquidados en otro documento del mismo período y tipo.
2. **Liquidación** — una fila por empleado: **devengado, deducido, neto a pagar**, aporte empleador,
   estado (liquidado / **no liquidado** con causa). Botón *Liquidar* (todos o seleccionados, proceso en
   segundo plano con barra de progreso). Al abrir un empleado: **tabla de detalle** por concepto (código,
   concepto, cantidad, base, %, devengado, deducido, aporte empleador, tercero) y la **traza** de cada línea.
3. **Novedades** — las novedades autorizadas que entraron (y las que no, con motivo).
4. **No liquidados** — empleados con error y su causa (sin salario vigente, sin afiliación, etc.).
5. **Proyección contable** — el asiento que se va a generar, agrupado por cuenta y tercero.

### 5.3 Ciclo
1. Crear prenómina (preliminar) → agregar empleados → liquidar (se puede repetir; la simulación nunca
   afecta nada).
2. **Confirmar prenómina**: exige cero no liquidados, total > 0, período contable abierto.
3. **Generar nómina** desde la prenómina confirmada (copia las líneas con referencia; puede revisarla
   otra persona) → **Confirmar nómina**: asiento por concepto y por entidad, saldos de novedades/cuotas/
   embargos, vacaciones disfrutadas, % fijo de retención (procedimiento 2).
4. **Pagar** (empleado o lote; transferencia programada hasta confirmar banco) → **nómina electrónica**.
5. **Anular/reversar** devuelve todo.

> Decisión abierta (D1): prenómina y nómina como **dos documentos** (permite que una persona prepare y
> otra apruebe) o **un documento con dos estados** (más simple). Recomendado: dos documentos para empresas
> con revisión, con la opción de "confirmar y generar nómina" en un paso.

## 6. Retiros
- **Registrar retiro**: empleado/vinculación, **fecha de retiro**, **motivo** (catálogo: renuncia, despido
  con justa causa, despido sin justa causa, terminación de contrato fijo/obra, mutuo acuerdo, pensión,
  fallecimiento, fin de etapa productiva), fecha de aplicación, soporte (carta, acta).
- Al registrar: la vinculación pasa a *Retirada* desde esa fecha, se marca la novedad **RET** para PILA y
  queda **pendiente de liquidar**.
- Desde el retiro: **generar prenómina de liquidación definitiva** (prestaciones desde la última
  consignación, vacaciones pendientes en dinero, indemnización según motivo y tipo de contrato, descuento
  de saldos de préstamos/anticipos).
- Lista de **retiros hechos**: empleado, fecha, motivo, estado (pendiente · liquidado · pagado), documento
  de liquidación. Anular un retiro reactiva la vinculación si la liquidación no está confirmada.

## 7. Vacaciones (módulo aparte)
- **Causación** por empleado: períodos de servicio (año laborado), días de derecho (15 hábiles por año,
  proporcional), disfrutados, en dinero, **pendientes**; histórico de causaciones.
- **Registro**: días **en tiempo** y **en dinero** (compensadas, máx. lo legal), fechas de disfrute
  (inicio, fin, **regreso**), documento soporte, observaciones; calcula hábiles con el calendario laboral.
- **Retorno anticipado**: los días no disfrutados vuelven a pendientes.
- **Vacaciones colectivas** (en masa).
- Se pagan en la **nómina periódica** del período en que se disfrutan (o por comprobante propio si la
  empresa paga por anticipado) y se reportan en PILA (VAC-LR) y nómina electrónica.
- Saldos iniciales: ya existen (F7).

## 8. Incremento salarial
- Seleccionar por *tipo de lista* (empleado, vinculación, grupo, tipo de vinculación, todos).
- **Tipo de incremento**: porcentaje · valor adicional · nuevo valor · igualar al SMMLV.
- **Redondeo**: ninguno, al peso, a la centena (arriba/abajo/automático), al mil.
- **Fecha de aplicación**, descripción, soporte.
- Estados: **Pendiente** (vista previa: salario actual → nuevo, por empleado) → **Aplicado** (crea el
  registro en el historial salarial con esa vigencia) → **Reversado**.
- Si la fecha de aplicación es anterior a nóminas ya confirmadas → ofrece **generar retroactivo** (§4).

## 9. Cesantías – archivo plano
- Fuente: el comprobante de **Cesantías** confirmado del año (consolidado al 31 de diciembre).
- Agrupa por **fondo de cesantías** (afiliación del empleado) y genera la planilla de consignación con el
  layout del operador (configurable por layout, igual que el plano de PILA).
- Lista de **archivos generados** (año, fondo, empleados, valor, fecha, usuario) y descarga.
- Pago: registra la consignación por fondo (cierra la 2510 a nombre de cada fondo); vence el 14 de febrero.
- Intereses de cesantías: se pagan al empleado (31 de enero) por su comprobante.

## 9b. Configuración: catálogos en módulos separados

Cada catálogo es su propia pantalla (estándar de Aura: `.page > .card`, botón arriba, buscador, tabla,
formulario). Todos llevan código, nombre, activo y lo propio:

| Catálogo | Campos propios | Para qué |
|---|---|---|
| **Tipos de vinculación** | tipo de contrato por defecto, **tipo de comprobante** en que se liquida, tipo de remuneración, calendario, ¿única por empleado?, ¿principal?, ¿exige fecha fin? | Agrupa empleados y decide en qué nómina entran. |
| **Grupos de nómina** | — | Liquidar juntos (administrativos, operativos, obra X). |
| **Niveles de cargo** | orden | Directivo, profesional, técnico, asistencial, operativo. |
| **Tipos de cargo** | — | Planta, temporal, por proyecto. |
| **Cargos** (módulo aparte) | nivel, tipo, número de plazas, tipo de remuneración, **salario de referencia** y rango mín–máx, misión, funciones, análisis salarial; **conceptos permitidos** con tope (p. ej. horas extra máx./mes) | Catálogo que usa la vinculación; controla plazas y topes. |
| **Calendarios laborales** | días laborables lun–dom + festivos | Días hábiles para vacaciones y ausentismos; jornadas distintas (lun–sáb). |
| **Tipos de riesgo ARL** | porcentaje (clase I–V) | Tarifa ARL de la vinculación. |
| **Tipos de aporte** | código PILA | EPS, pensión, ARL, caja, cesantías, aporte voluntario. |
| **Entidades** | tipo de aporte, código PILA/administradora, NIT (tercero) | EPS/AFP/ARL/CCF/fondos como terceros: a quién se le paga. |
| **Tipos de embargo** | prioridad por defecto, **límite legal** (% máx.), ¿va a depósito judicial? | Alimentos, ejecutivo/civil, cooperativa, fiscal. |
| **Motivos de retiro** | ¿genera indemnización?, código para PILA y nómina electrónica | Retiros (§6). |
| **Motivos de ausentismo** | quién paga (empresa/EPS/ARL), %, ¿remunerado?, código PILA (IGE, IRL, LMA, LPA, SLN, VAC) | Ausentismos (§3.5 del agente). |
| **Tipos de sanción** | ¿es proceso disciplinario?, ¿atención escrita? | Llamado de atención, suspensión, terminación. |
| **Gravedad de faltas** | — | Leve, grave, gravísima. |
| **Ocurrencias** | número de ocurrencia | 1.ª, 2.ª, 3.ª vez. |
| **Faltas** | descripción, **caducidad (días)**, política/reglamento | Catálogo del reglamento interno. |
| **Clasificación de faltas** | falta × gravedad × ocurrencia → **sanción a aplicar** y **días de suspensión** | Matriz que sugiere la sanción. |
| **Sindicatos** | tercero, concepto de cuota, valor/% | Cuota sindical por afiliado. |
| **Validaciones PILA** | lista 1, lista 2, tipo de cruce | Matriz de cruces como datos (no código). |

## 9c. Procesos de gestión humana (módulos separados)

| Módulo | Qué hace |
|---|---|
| **Afiliaciones (aportes)** | Por empleado: tipo de aporte, entidad, fecha, tarifa especial de pensión (alto riesgo), riesgo ARL, actividad CIIU. **Afiliaciones en masa** (cambio de ARL/caja o riesgo para un grupo). |
| **Conceptos en masa** | Asignar un concepto permanente (auxilio de rodamiento, bonificación fija) con valor a muchos empleados por lista. |
| **Novedades masivas** | Lote de novedades de un concepto a muchos empleados, a mano o desde Excel. |
| **Préstamos (amortizaciones)** | Valor, número de cuotas, frecuencia, fecha de inicio, quién aprueba, tercero acreedor (empresa, cooperativa), **refinanciar** (la nueva reemplaza el saldo). Tabla de cuotas y saldo. |
| **Embargos** | Expediente, número de proceso, juzgado/despacho, demandante (persona, banco, cuenta), tipo, prioridad, **forma de descuento** (% / valor fijo / cuotas), saldo, frecuencia, **pago directo o depósito judicial**, cuenta judicial, oficina destino. |
| **Depósitos judiciales** | Por mes: genera el archivo plano para el banco de los embargos que van a cuenta judicial y registra el pago a nombre de cada juzgado. |
| **Ausentismos** | Solicitud con número, motivo, diagnóstico CIE-10, fechas y **horas**, días reales, aprobación (quién y cuándo), sustitución patronal. |
| **Autorización de novedades** | Bandeja con filtros (empleado, concepto, fechas, estado) y **cambio de estado masivo**; notifica al empleado. |
| **Vacaciones** | §7, con solicitud y aprobación del jefe. |
| **Incremento salarial** | §8, con aprobación. |
| **Retiros** | §6: número, fecha de proceso, motivo, fecha de aplicación, aprobación, estado de la vinculación antes → después. |
| **Traslados/reubicaciones** | Cambio de área, jefe, evaluador o cargo con fecha; **intercambio** entre dos empleados. Queda en el historial de la vinculación. |
| **Reemplazos temporales** (opcional) | Un empleado cubre el cargo de otro por un tiempo con **diferencia salarial** (valor o %), centro de costo del cargo cubierto. |
| **Procesos disciplinarios** | Falta → hechos, lugar, fecha y hora → **descargos** (preguntas y explicación) → gravedad y ocurrencia → **sanción sugerida** por la matriz → si es suspensión, genera la **novedad de suspensión** (no remunerada, SLN en PILA) en la nómina; notificación por correo. |
| **Documentos y certificados** | Certificado laboral (con o sin salario, con firmas), oficios y cartas desde plantillas; radicado y fecha. |
| **Acumulados iniciales** | Saldos por concepto al migrar desde otro sistema (amplía los saldos iniciales F7). |

## 9d. Ideas para que Aura se destaque

1. **Portal del empleado**: Aura ya crea usuario para el empleado; que vea sus desprendibles y
   certificados, solicite vacaciones y permisos, y el jefe los apruebe desde la campana.
2. **Alertas en la campana**: vencimiento de contrato (30/15 días), fin del período de prueba,
   vacaciones acumuladas (más de 2 períodos), incapacidades sin cobrar a la EPS, embargos por terminar,
   préstamos con saldo de empleados retirados.
3. **Topes legales automáticos**: horas extra (2 diarias, 12 semanales), embargos (límite del excedente
   del SMMLV; alimentos 50 %), descuentos que dejen el neto bajo el mínimo legal.
4. **Simulador de costo de un empleado** (salario + prestaciones + aportes) para cotizar contrataciones.
5. **Costo de nómina por proyecto/centro de costo** con la dimensión que ya existe en Aura (obra/frente).
6. **Checklist de cierre de nómina** del mes (como el cierre contable): todos liquidados, PILA generada,
   nómina electrónica enviada, pagos confirmados, incapacidades por cobrar.

## 10. Fases propuestas

| Fase | Entrega | Depende de |
|---|---|---|
| R1 | **Catálogos (§9b)**: tipos de vinculación, grupos, niveles y tipos de cargo, **cargos**, calendarios, tipos de riesgo, tipos de aporte, entidades, tipos de embargo, motivos de retiro y de ausentismo. **2026-10-10 hecho (local, sin commit):** V209 + catálogo genérico `/api/nomina/catalogos/{catalogo}` (7 catálogos con semilla), `/api/nomina/cargos`, `/api/nomina/tipos-vinculacion` (con semilla), pantallas en `features/nomina/parametros/`, grupo "Catálogos de nómina" en el centro de RR. HH., SQL `docs/sql/menu_submodulos_nomina_r1_catalogos.sql` (corrido en local). Pasan a R2: calendarios, tipos de aporte y entidades. | — |
| R2 | **Vinculación ampliada** (§3) con migración de los contratos actuales. **2026-10-10 hecho (local, sin commit):** V210 amplía `contrato_laboral` (código VIN-, tipo de vinculación, cargo/grupo/calendario/riesgo del catálogo, remuneración, horas, prueba, sede, área, jefe, evaluador, exterior, soportes) y migra los contratos (tipo por tipo de contrato, cargos MIG-### desde el texto). `/api/nomina/vinculaciones` (reglas: fecha fin del tipo, único por empleado, plazas y rango del cargo, prueba art. 78, jornada legal, aprendiz con etapa; sincroniza cargo/integral/periodicidad/ARL que lee el motor). Pantalla Vinculaciones (listado + página con pestañas General · Ubicación y jerarquía · Seguridad social · Soporte); la ficha del empleado abre ahí. Catálogo **calendarios laborales**. SQL `docs/sql/menu_submodulos_nomina_r2_vinculaciones.sql`. Tipos de aporte y entidades pasan a R9 (afiliaciones en masa). **2026-10-10 la ficha del empleado se reemplazó por la vinculación** (V212): Empleados → Editar solo datos personales; la vinculación tiene pestañas Centros de costo, Conceptos (propios con vigencia), Novedades permitidas (tope y autorización), Aportes, Retención, Embargos e Historial. Los conceptos propios y las novedades permitidas se guardan pero el motor aún no los aplica: entra en R3. | R1 |
| R3 | **Tipos de comprobante** (§4) + **Prenómina** con pestañas Empleados y Liquidación con detalle y traza (§5.1–5.2, reutiliza el motor actual). **2026-10-11 R3.1 tipos de comprobante hecho (local, sin commit):** V214 `nomina_tipo_comprobante` (código, nombre, clase PERIODICO/PRIMA/CESANTIAS/INTERESES_CESANTIAS/LIQUIDACION_DEFINITIVA/RETROACTIVO/OTROS_PAGOS, prefijo único, frecuencia solo en periódicos) + `nomina_tipo_comprobante_vinculacion` (vacío = todos); `/api/nomina/tipos-comprobante` con semilla NOM/PRIMA/CES/INTCES/LIQ/RETRO; pantalla `features/nomina/parametros/tipos-comprobante/`; SQL `docs/sql/menu_submodulos_nomina_r3.sql` (corrido en local). **2026-10-11 R3.2a encabezado de prenómina hecho (local, sin commit):** decidido documento nuevo (no evolucionar el período) y filas de la prenómina en la misma tabla `nomina` con marca. V215 `nomina_documento` (PRENOMINA/NOMINA, número por tipo y clase, fechas desde/hasta/liquidación/pago, simulación, estado PRELIMINAR/CONFIRMADO/ANULADO, origen, totales) colgado de `periodo_nomina` con el mismo rango (se crea si no existe); `nomina.documento_id` + `nomina.es_prenomina`; `UNIQUE (contrato, periodo)` → parcial sin documento + `(contrato, documento)`. Lectores que agregan nóminas excluyen `es_prenomina` (PILA, provisiones, vacaciones tomadas, promedio variable, certificado de ingresos, dashboard, listado e historial); los métodos del flujo viejo por período solo ven filas sin documento. `/api/nomina/documentos` (page, obtener, POST /prenomina, PUT, PUT /anular), permiso `recursos-humanos.prenomina`; front `features/nomina/prenomina/` (listado + página con pestaña Encabezado). Página: encabezado arriba y pestañas Empleados · Liquidación · Presupuesto · Contabilidad · Traza · Adjuntos (Presupuesto y Adjuntos quedan vacías por decisión del usuario, sin HU). **2026-10-11 R3.2b pestaña Empleados hecha (local, sin commit):** `NominaDocumentoEmpleadoService` + `/api/nomina/documentos/{id}/empleados` (GET, POST por tipo de lista EMPLEADO/VINCULACION/GRUPO/TIPO_VINCULACION/TODOS, POST /quitar) y `/candidatos`; candidatos = contratos ACTIVOS que se cruzan con el período, sin prestación de servicios; se omiten con causa (ya en el documento, tipo de vinculación no admitido, en otra prenómina no anulada del mismo tipo y período, o liquidado por el flujo viejo si es periódico). Fila = `nomina` con `es_prenomina`, estado BORRADOR, salario copiado del contrato. El flujo viejo (`NominaServiceImpl.liquidarContrato`/`liquidarPeriodoCompleto`) rechaza/omite contratos que ya están en un documento. Front `prenomina/tabs/empleados-prenomina.component`. **2026-10-11 R3.2c liquidar hecho (local, sin commit):** V216 (`nomina.liquidada_at`, `nomina.error_liquidacion`, tipo de proceso LIQUIDACION_DOCUMENTO). `POST /{id}/liquidar` (202, en segundo plano con `LiquidacionDocumentoJob` + `ProcesoNominaService`, una transacción por fila; la que falla queda "no liquidada" con la causa y el lote sigue; al final se recalculan los totales del encabezado). `NominaServiceImpl.liquidarFilaDocumento` usa el mismo motor y reglas del flujo viejo (contrato liquidable, asistencia, días) con la **fecha de liquidación del documento**. Solo comprobantes PERIODICO (los demás esperan R5/R6). `GET /{id}/liquidacion`, `/liquidacion/{nominaId}/detalle` (con traza), `/traza` (corridas). Front: botón Liquidar + barra de progreso (polling a `/api/proceso`, que ahora acepta el permiso prenómina), pestañas Liquidación (fila desplegable con detalle por concepto y traza por línea, totales al pie) y Traza. Ajustes del usuario: Liquidar vive en la pestaña Liquidación y solo aparece si hay empleados sin liquidar (liquida solo esos, `soloPendientes`); la lista muestra solo los que pasaron por una liquidación; Reliquidar por fila; conceptos en un modal; nombre como "documento - nombre". **2026-10-11 R3.2d hecho (local, sin commit):** BUG del motor corregido: las novedades que pagan (horas extra, incapacidad, licencias, bono, comisión, otro devengo) entraban al IBC pero no generaban línea de devengado, así que no llegaban al devengado ni al neto (afectaba también el flujo viejo). V217 siembra un concepto global por tipo de novedad (código = tipo, base NOVEDAD, codigo_dian) y el motor emite una línea por novedad. Conceptos propios de la vinculación (`vinculacion_concepto` vigentes en el período): % del salario proporcional, cantidad × valor o valor; los devengados entran a las bases (IBC si el concepto constituye IBC). Novedades permitidas: si la vinculación tiene lista, las novedades que pagan deben estar en ella y sus sumas no pasan los topes de cantidad/valor (ausencias legales y deducciones nunca se restringen); si no cumple, la fila queda no liquidada con la causa (`ReglasNovedadesPermitidas`, con test). Pendiente: "requiere autorización" aún no se aplica (va con R9). Sigue **R4** Liquidar con el motor + pestaña Liquidación con detalle y traza, **R3.2d** conceptos propios y topes de novedades en el motor. | R2 |
| R4 | **Confirmar → nómina** + **asiento por concepto y por entidad** (brechas N1/N3 del agente) + pestaña Proyección contable. Partido en R4a (ciclo) → R4b (pestaña Contabilidad = proyección) → R4c (asiento por concepto y entidad + parametrización). **2026-10-11 R4a hecho (local, sin commit):** `NominaDocumentoCicloService` + endpoints `/{id}/confirmar-prenomina`, `/reabrir`, `/generar-nomina`, `/confirmar-nomina`, `/anular`. Confirmar prenómina exige no simulación, cero sin liquidar, total > 0 y período contable abierto (`PeriodoContableResolver.exigirAbierto`, fecha del documento). Generar nómina copia encabezado, filas (es_prenomina=false, BORRADOR), novedades y detalle (`NominaCopiaMapper`) a NOM-xxxxx con `documento_origen_id`. Confirmar nómina = `NominaService.aprobar` por fila (asiento viejo por empleado + nómina electrónica tras el commit). Anular nómina = `NominaService.anular` por fila (reversa; falla si alguna está pagada). Las operaciones de prenómina (encabezado, empleados, liquidar) rechazan documentos NOMINA. Menú aparte "Nómina" (`/nomina/nominas`, permiso `recursos-humanos.nomina-documento`, en `docs/sql/menu_submodulos_nomina_r3.sql`); misma pantalla que la prenómina con la clase en la data de la ruta. Pendiente: pestaña de novedades en la prenómina (hoy solo entran las de asistencia y los conceptos propios), pago por documento. Arreglos 2026-10-11: `saveAndFlush` antes de releer con SQL (la respuesta salía con el estado viejo) y consecutivo de nómina electrónica (`@Modifying` con RETURNING fallaba siempre → `NominaElectronicaQueryRepository`). **R4b hecho (local, sin commit):** `ContabilidadAutoServiceImpl.lineasNomina` arma las líneas que usan el asiento real (`generarDesdeNomina`) y la proyección (`proyectarNomina`, readOnly); `GET /{id}/contabilidad` agrupa por cuenta y descripción las filas liquidadas, con totales y advertencias (cuenta sin configurar). Pestaña Contabilidad en el front. **R4d hecho (local, sin commit):** pestaña Novedades en la prenómina (`/{id}/novedades` GET/POST/DELETE, reutiliza `NominaService.agregarNovedad/eliminarNovedad` con sus reglas; el empleado queda sin liquidar y se reliquida; `flush` antes del SQL) y pago de la nómina confirmada (`POST /{id}/pagar`, por empleado o todos los pendientes, con `NominaService.pagar` y el diálogo de pago viejo; una fila de documento no cierra el período viejo). Etiqueta "Pagada" cuando todos los empleados están pagados. Preliquidación/Auditoría salió del menú y del centro de RR. HH. (la reemplaza la prenómina). Períodos y Liquidación Nómina también salieron del menú y del centro (2026-10-11; rutas vivas, el diálogo de pago vive en `features/nomina/liquidacion/pago`). **R4c-1 hecho (local, sin commit):** asiento desde `nomina_detalle` por concepto y entidad (`AsientoNominaBuilder` + `ReglaContableNomina` con test + `AsientoNominaQueryRepository`): gasto por concepto a nombre del empleado con su proyecto/frente/centro de costo; salud → 2370 EPS, pensión/FSP → 2370 AFP, ARL y caja → 2370 de su entidad (afiliaciones vigentes a la fecha), ICBF/SENA → 2370 sin tercero, retefuente → 2365 DIAN, préstamos/embargos → 2380 empleado, provisiones → 25xx empleado, neto → 2505 empleado. Cuentas desde la parametrización contable existente. Fecha del asiento = fecha del documento (o fin del período). Nóminas viejas sin detalle siguen con el asiento por totales. La pestaña Contabilidad muestra el tercero y los avisos (afiliación faltante). Sigue **R4c-2** pantalla para cambiar cuenta/tercero por concepto (override por empresa). | R3 |
| R5 | **Retiros** → prenómina de liquidación definitiva (§6). **2026-10-11 hecho (local, sin commit):** V219 `nomina_retiro` (motivo del catálogo con indemniza, fecha fin pactada, cesantías consignadas fuera hasta, documento). Registrar = `ContratoLaboralService.terminar` (PILA ve RET por fecha_fin) + empleado inactivo si no tiene otra vinculación; anular = `reactivar` (reabre historial salarial) si no hay liquidación sin anular. Estado del retiro deducido del documento (pendiente · en liquidación · liquidado · pagado · anulado). `POST /api/nomina/retiros/{id}/liquidacion` crea la prenómina LIQ desde el día siguiente al último salario pagado; al liquidarla el motor paga los días (0 si ya estaban pagados) y `LiquidadorDefinitiva` agrega cesantías, intereses, prima, vacaciones (desde el último lote de Prestaciones o el ingreso, con promedio variable y corte de cesantías) e indemnización (si el motivo indemniza). Fórmulas compartidas con Prestaciones en `FormulasPrestaciones` (con test). Contabilidad: prestaciones pagadas debitan el pasivo provisionado (25xx), indemnización al gasto. Pantalla RR. HH. → Retiros. | R3 |
| R6 | **Prima, cesantías e intereses por comprobante** (sale de la pantalla Prestaciones) + **archivo plano de cesantías** (§9). **2026-10-11 hecho (local, sin commit, sin migración):** documentos de clase PRIMA / CESANTIAS / INTERESES_CESANTIAS se liquidan sin motor (`LiquidadorPrestacionDocumento`): una línea por empleado desde el día siguiente al último pago (nómina por documento confirmada de esa clase o módulo Prestaciones) o el ingreso, hasta el fin del período o el retiro, con promedio variable; días trabajados de la fila en 0 para que PILA no los sume. El form propone el período por clase (semestre / año). Consignación de cesantías en Excel por fondo (`CesantiasExcelExporter`, `GET /{id}/cesantias-excel`, fondo = afiliación tipo CESANTIAS vigente al cierre; hoja "SIN FONDO" para los que no tienen). Pendiente: registrar el pago por fondo a nombre del fondo (hoy el pago es por empleado con el diálogo de pago); saldo inicial de cesantías migrado no se suma (sí en Prestaciones). | R3, R4 |
| R7 | **Vacaciones** como módulo (§7). **2026-10-11 hecho (local, sin commit):** V220 `nomina_vacacion` (TIEMPO del/al con hábiles sin domingos ni festivos y regreso al siguiente hábil — `DiasHabiles`, compartido con Prestaciones; DINERO hasta la mitad de lo causado, CST art. 189; regreso anticipado devuelve los hábiles no disfrutados; anular; colectivas por lote omitiendo con causa). `/api/nomina/vacaciones` (page, saldo/{contratoId}, POST, /colectivas, /{id}/retorno, /{id}/anular), permiso `recursos-humanos.vacaciones`; no se registra ni cambia nada sobre días de una nómina aprobada o pagada. Pago (D3): al liquidar una fila de documento, `VacacionesEnNomina` vuelve novedades VACACIONES (origen VACACIONES) las vacaciones del período: en tiempo descuentan días calendario y pagan (salario + promedio variable del último año sin horas extra) ÷ 30 × hábiles, con IBC; en dinero se pagan completas en el período de su fecha, sin IBC. Saldo: suma las del módulo y deja de contar dos veces sus novedades. Liquidación definitiva: las vacaciones pendientes salen del saldo de días al retiro (`VacacionesService.saldoAl`) y no de "desde el último pago". Front `features/nomina/vacaciones/`; **Prestaciones** sale del menú y del centro de RR. HH. (la ruta sigue para consultar históricos). | R3 |
| R8 | **Incremento salarial** + **retroactivos** (§8). **2026-10-11 hecho (local, sin commit):** V221 `nomina_incremento` + `_detalle` + concepto global RETROACTIVO (base NOVEDAD, IBC). Incremento por tipo de lista, tipo PORCENTAJE / VALOR_ADICIONAL / NUEVO_VALOR / SMMLV (solo sube a quien gana menos) y redondeo (peso, centena, arriba/abajo, mil) — `CalculoIncremento` puro con test. PENDIENTE = vista previa (actual → nuevo; los que no cambian quedan fuera; quitar por fila) → Aplicar (`ContratoLaboralService.cambiarSalario` por vinculación con la fecha de aplicación; 409 si el salario cambió desde la vista previa) → Reversar (`revertirSalario`: borra la fila del historial y reabre la anterior; bloqueado si el retroactivo no está anulado, hubo otro cambio después o ya se confirmaron nóminas con el salario nuevo); Anular si nunca se aplicó. Retroactivo: si hay nóminas periódicas confirmadas desde la fecha con el salario anterior, genera una prenómina de clase RETROACTIVO con esas vinculaciones; `LiquidadorRetroactivo` (con test) calcula por nómina pagada (debido − pagado) × proporción de días pagados × parte del período en el rango, como novedades RETROACTIVO que pasan por el motor con 0 días (salen salud, pensión, aportes y provisiones); no repite períodos de otro retroactivo confirmado. El motor ya no repite conceptos propios en filas sin días. `/api/nomina/incrementos`, permiso `recursos-humanos.incrementos`; front `features/nomina/incrementos/`. Pendiente: soporte adjunto y aprobación del incremento; OTROS_PAGOS sigue sin liquidarse. | R3 |
| R9 | Novedades (§9c): autorización masiva, novedades y conceptos en masa, **préstamos**, **embargos completos** y **depósitos judiciales**, afiliaciones en masa. | R2 |
| R10 | Gestión humana (§9c): traslados, reemplazos, **procesos disciplinarios** (catálogos de faltas y sanciones + matriz), documentos y certificados. | R1, R2 |
| R11 | Ideas (§9d): portal del empleado, alertas, topes legales, simulador de costo, checklist de cierre de nómina. | R3+ |

Cada fase: back + front + migración (Flyway V+1 y espejo Laravel), pruebas del motor con un caso real y
manual `/ayuda`.

## 11. Decisiones abiertas
- **D1** ✅ Prenómina y nómina como **dos documentos** (decidido 2026-10-10).
- **D2** ¿Se mantiene "período" como agrupador mensual (para PILA y cierre) por encima de los documentos?
  Recomendado: sí — PILA y nómina electrónica se arman por **mes**, sumando todos los documentos
  confirmados del mes.
- **D3** ✅ Vacaciones se pagan **en la nómina del período** de disfrute (decidido 2026-10-10).
- **D4** ✅ Pantalla **Prestaciones**: fuera del menú desde R7 (2026-10-11); la ruta queda para consultar históricos.
