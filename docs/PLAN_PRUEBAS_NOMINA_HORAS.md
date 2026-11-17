# Plan de pruebas — Nómina por horas (2026-11-16)

Cubre los 7 puntos trabajados para la empresa que liquida por horario (entrada,
salida, descanso y horas extra) y por **proyecto**, no por frente.

| # | Qué se arregló / agregó | Migración |
|---|---|---|
| 1 | El proceso de liquidación se caía al cerrar si un empleado fallaba (`COMPLETADO_CON_ERRORES` no cabía en 20) | V238 |
| 2 | Horas de frente/asistencia nunca pasaban un tope "con autorización"; faltaban los conceptos de recargo | V238 |
| 3 | Proyecto sin frentes: frente **General** automático | V241 |
| 4 | Salario mínimo, auxilio de transporte y UVT por **año** | V239 |
| 5 | Plantilla de Excel del mes para el marcaje + descanso por día + aprobar varios días | V242 |
| 6 | Botón **Cargar conceptos base** en la plantilla del cargo | — |
| 7 | Pasar un tope de horas ya no bloquea: la novedad queda **Por autorizar** | V240 |
| 8 | Configuración de nómina: usa frentes, forma de registro (Excel / manual / ambas) y si exige soporte | V243 |

## 0. Antes de empezar

- [ ] Migraciones Laravel `000238` a `000247` corridas en **local** (ya están corridas) — en producción van antes de desplegar este back.
- [ ] Backend en Docker con `docker compose watch` arrancado (`docker logs aura-backend` → "Started AuraPosApplication").
- [ ] Front compilando (`ng build` pasa; se corrigieron 2 pantallas que venían rotas de un merge: formulario de concepto y conceptos de la vinculación).
- [ ] La empresa de prueba tiene **configuración laboral vigente** (Laboral → Jornada). Sin ella cada día marca una alerta crítica y no se puede enviar a revisión.
- Los períodos no se crean a mano: los define la prenómina (que luego pasa a nómina). Enviar asistencia a nómina ya no exige un período: si aún no hay prenómina, las horas esperan y se toman por fecha al liquidar.

Datos sugeridos: 1 proyecto nuevo "Obra demo" sin frentes, 2 trabajadores con contrato activo
(uno con salario mínimo, para que aplique auxilio de transporte), mes de octubre de 2026.

## 0b. Configuración de la asistencia por obra (V243)

En **Nómina → Configuración → Asistencia por obra** (selects, sin switches):

| Opción | Valores | Por defecto |
|---|---|---|
| ¿Divide los proyectos en frentes? | Sí / No | Sí |
| ¿Cómo se registran las horas? | Plantilla de Excel del mes / Digitación día por día / Las dos formas | Las dos formas |
| ¿Un supervisor aprueba la asistencia antes de la nómina? (V245) | Sí / No | Sí |
| ¿Exige soporte para enviar a revisión? (solo con supervisor) | Sí / No | Sí |

Configuración para la demo: Modo de liquidación **Mixta** · **No** usa frentes · **Excel** · **No** supervisor (sin revisión).
Empleados que requieren asistencia: marcar "Requiere control de asistencia" en su ficha; los demás se liquidan normal.

- [ ] Guardar y recargar: los 3 valores se conservan.
- [ ] **Sin frentes**: Proyectos → Frentes se titula "Trabajadores del proyecto", solo muestra el General, sin botón "Nuevo frente" y con la nota. Crear un frente por API → 400 "La empresa trabaja por proyecto sin frentes…".
- [ ] **Sin frentes** en un proyecto que ya tenía frentes (PRY-001): al guardar la configuración, el proyecto queda con su General y en él los trabajadores activos y el turno de sus frentes (V244 lo hizo para las empresas que ya estaban así).
- [ ] **Sin frentes**: Digitación elige sola el "Proyecto completo" aunque el proyecto tenga frentes viejos; Revisión → Aprobar varios días toma sola el General.
- [ ] **Registro = Excel**: en Digitación las horas se ven pero no se editan, no está "Guardar borrador", sale la nota azul y "Importar mes (Excel)" funciona. Guardar un borrador por API → 400 "La empresa registra las horas con la plantilla de Excel…".
- [ ] **Registro = Excel**: no aparece la tarjeta "Soporte firmado / Subir PDF escaneado" aunque se exija soporte; la tabla de trabajadores ocupa todo el ancho y la nota azul tiene el enlace "Ver Excel importado". Un día importado se envía a revisión sin PDF.
- [ ] **Registro = Excel**: "Enviar a revisión" de un día importado funciona sin intentar guardarlo primero.
- [ ] **Registro = Digitación**: no aparece "Importar mes (Excel)"; validar o confirmar el Excel por API → 400.
- [ ] **Sin soporte**: en Digitación desaparece la tarjeta del PDF y la tabla ocupa todo el ancho; "Enviar a revisión" queda activo sin soporte y el back lo acepta. El Excel importado igual se guarda como soporte si hay almacenamiento, y no salen avisos de soporte.
- [ ] **Con soporte (Sí)**: un día sin PDF ni Excel no se envía ("…aún no se ha cargado el soporte (PDF o el Excel importado)").
- [ ] **Sin supervisor (V245)**: en Digitación no está "Enviar a revisión" (sale la nota "pasa directo a la prenómina"), ni la tarjeta del PDF; el importador termina en "Listo para la prenómina".
- [ ] **Sin supervisor**: liquidar la prenómina del mes → los días del empleado en borrador o en revisión quedan "Enviado a nómina" solos y sus horas extra y recargos salen en la liquidación. Los días cuentan para los jornaleros.
- [ ] **Sin supervisor + Mixta**: un empleado marcado sin ningún día registrado → la fila falla con "el empleado requiere asistencia y no tiene días registrados…"; uno no marcado se liquida normal.
- [ ] **Sin supervisor**: un día con alerta crítica (p. ej. sin jornada configurada) → la fila falla con "Asistencia del AAAA-MM-DD: …".
- [ ] **Modo Sin asistencia**: las horas aprobadas/enviadas a nómina igual entran a la liquidación (antes se perdían).
- [ ] Una empresa sin configuración guardada se comporta como antes (frentes, ambas formas, soporte obligatorio).

## 1. Salario mínimo y auxilio por año (punto 4)

- [ ] **Nómina → Configuración**: en "Valores legales" se ve *SMMLV 2026 $1.750.905* y *Auxilio de transporte 2026 $249.095* como texto, con la nota de la norma.
- [ ] Liquidar a un empleado de salario mínimo en un período de 2026: el sueldo base y el auxilio salen con los valores 2026; en la traza del auxilio aparece 249.095.
- [ ] Reliquidar (o liquidar) un período de **2025**: usa 1.423.500 / 200.000.
- [ ] Retención en la fuente de 2026 usa UVT **52.374** (tabla `uvt_valor`).
- [ ] Editor de fórmulas de conceptos: el ejemplo de SMMLV muestra 1.750.905.
- [ ] ⚠ Verificar con el decreto y la resolución DIAN los valores 2026 antes de producción (están en `nomina_parametro_anual` / `uvt_valor`).

## 2. Proyecto sin frentes (punto 3)

- [ ] Crear un proyecto nuevo → en **Proyectos y frentes** aparece solo el frente *General (todo el proyecto)* con la etiqueta "Todo el proyecto", sin botón de eliminar.
- [ ] Los proyectos que ya existían sin frentes también tienen su General (lo creó la migración).
- [ ] Asignar los 2 trabajadores al frente General.
- [ ] **Digitación**: al elegir el proyecto, el campo Frente dice "Proyecto completo" y queda elegido solo.
- [ ] Un proyecto con frentes propios sigue mostrando el selector, con "Todo el proyecto (General)" de primero.
- [ ] Intentar eliminar el General por API → "El frente General representa el proyecto completo y no se elimina."

## 3. Descanso por día en la digitación (punto 5)

- [ ] En Digitación hay columna **Descanso (min)**; vacía = el del turno.
- [ ] 07:00 a 20:00 con 60 min → la columna Horas muestra 12.
- [ ] Guardar borrador y recargar: el descanso se conserva.
- [ ] Descanso 800 → error "El descanso debe estar entre 0 y 720 minutos."

## 4. Marcaje del mes desde Excel (punto 5) — el caso de la demo

1. [ ] Digitación → proyecto "Obra demo" → **Importar mes (Excel)**.
2. [ ] Elegir el mes (10/2026) → **Plantilla**: baja `marcaje-obra-demo-2026-10.xlsx` con una fila por trabajador y día (31 × 2 = 62), domingos resaltados, hoja Instrucciones y lista de estados en la columna Estado.
3. [ ] Llenar: lunes a sábado 07:00–20:00 con 60 de descanso; un día "No asistió"; un domingo trabajado; dejar el resto vacío.
4. [ ] Subir el archivo → muestra días, registros válidos, horas trabajadas, extra y con recargo, sin guardar nada.
5. [ ] Errores a provocar (cada uno debe salir en su fila, y el botón Importar queda deshabilitado):
   - [ ] Documento que no está asignado al frente.
   - [ ] Mismo trabajador dos veces el mismo día.
   - [ ] Hora "25:00" o "siete".
   - [ ] "Asistió" sin salida.
   - [ ] Estado inventado ("de fiesta").
   - [ ] Fecha futura.
6. [ ] Corregir y volver a subir → **Importar** → "N día(s) guardados como borrador".
7. [ ] Cada día importado tiene el Excel como soporte (en Digitación se ve "Soporte cargado" con el nombre del .xlsx).
8. [ ] Un día que ya tenía otro trabajador digitado a mano conserva a ese trabajador.
9. [ ] Un día ya **aprobado** no se puede pisar: la fila sale con error.
10. [ ] Las alertas "horas extra superan el límite del día/semana" salen como **ADVERTENCIA**, no crítica.
11. [ ] Paso 3 → **Enviar a revisión**: envía todos los días del archivo; los que no se puedan muestran su causa.
12. [ ] Sin almacenamiento R2 configurado: importa igual y avisa que el soporte no se guardó.

## 5. Aprobar varios días (punto 5)

- [ ] **Revisión de asistencia** → elegir el proyecto → aparece el bloque "Aprobar varios días".
- [ ] Frente (se elige solo si hay uno), rango 01–31/10, "Aprobar y enviar a nómina" → **Aprobar rango**.
- [ ] Los días quedan en *Enviado a nómina* y se generan las novedades de horas extra y recargos.
- [ ] "Solo aprobar" deja los días en Aprobado sin enviarlos.
- [ ] Aprobar y enviar a nómina días de una quincena que todavía no tiene prenómina: se envían sin error y al crear y liquidar la prenómina sus horas aparecen.

## 6. Liquidar la prenómina con esas horas (puntos 1, 2 y 7)

- [ ] Crear la prenómina del mes con los 2 trabajadores → **Liquidar**.
- [ ] El proceso termina (antes se caía con "value too long for type character varying(20)").
- [ ] Si un empleado falla, el proceso queda "Completado con errores" y la fila muestra el motivo exacto.
- [ ] En la liquidación aparecen líneas separadas: Hora extra diurna, Hora extra nocturna, **Recargo nocturno** (14), **Recargo dominical/festivo** (15), **Hora extra dominical/festiva** (16). Ya no como "Otro devengo".
- [ ] Un domingo o festivo trabajado en la asistencia liquida sin error (V246: antes "viola la restricción chk_novedad_tipo" y un error de SQL). Igual un retroactivo salarial.
- [ ] Reliquidar a un empleado ya liquidado con horas de asistencia, y quitar una novedad manual de un liquidado: no sale "viola la llave foránea nomina_detalle_novedad_id_fkey" (V247).
- [ ] Con tope de horas en el cargo (p. ej. 2 al día / 48 al mes): las horas del frente **pasan** porque llegan aprobadas por el supervisor.

## 6b. Días pagados y calendario del mes

- [ ] Empleado con contrato **MENSUAL** y "requiere control de asistencia", que trabajó de lunes a viernes: se le pagan los días del período (30 en un mes), no solo los días trabajados. Los sábados, domingos y festivos no trabajados no son faltas.
- [ ] Un día con "No asistió" se descuenta como ausencia en el mensual.
- [ ] Contrato **JORNAL** u **HORA**: se pagan solo los días trabajados y la falta no se descuenta otra vez.
- [ ] Digitación → **Mes**: calendario con trabajadores contra días, horas de cada día, F de falta, letras de permiso/incapacidad/vacaciones, domingos y festivos resaltados, y totales de días, faltas, horas y extra. Clic en un día abre ese día.
- [ ] Importar Excel → al validar sale la **vista previa del mes** en calendario, con las filas con error en rojo (!) y las advertencias en amarillo, antes de importar.

## 7. Topes y autorización de novedades manuales (punto 7)

En el cargo: Novedades permitidas → Hora extra diurna con tope 10 y "requiere autorización"; Topes de horas → Hora extra diurna 48 al mes.

- [ ] Prenómina → Novedades → agregar 15 horas extra diurnas → se guarda con aviso "Novedad por autorizar" y la etiqueta **Por autorizar**.
- [ ] Liquidar a ese empleado → falla con "…Requiere autorización: autorice la novedad para pasar el tope."
- [ ] Botón ✓ **Autorizar** → etiqueta *Autorizada* → reliquidar → liquida.
- [ ] Novedades permitidas **sin** "requiere autorización" y tope 10: agregar 15 → se rechaza (tope fijo).
- [ ] Concepto que no está en la lista de permitidas → se rechaza "no está permitida en su vinculación ni en su cargo".
- [ ] Mismo flujo en la liquidación por período (detalle de nómina): etiqueta Por autorizar y botón ✓.
- [ ] En la base, `nomina_novedad.autorizada_por` y `autorizada_at` quedan con el usuario y la hora.

## 8. Conceptos base del cargo (punto 6)

- [ ] Cargo sin plantilla → pestaña Conceptos → **Cargar conceptos base** → agrega sueldo, auxilio, horas extra y recargos (10–16), salud, pensión, fondo de solidaridad, retención, aportes y provisiones, más los conceptos propios de la empresa que hoy se liquidan solos (para no quitarlos al estrenar la plantilla).
- [ ] Volver a pulsarlo → "El cargo ya tenía todos los conceptos base."
- [ ] Un concepto que ya está en Novedades permitidas no se duplica en Conceptos.
- [ ] Liquidar a un empleado del cargo: liquida lo mismo que antes de cargar la plantilla.

## 9. Regresión rápida

- [ ] Digitación día por día de un frente normal (con PDF) sigue igual.
- [ ] Liquidación por período sin asistencia sigue igual.
- [ ] PILA del mes de prueba usa el SMMLV 2026 (IBC mínimo).
- [ ] Prima/cesantías de un empleado de salario mínimo usan el auxilio 2026.
- [ ] Pruebas unitarias: `mvn test -Dtest='ReglasMarcajeTest,ReglasTopesHorasTest,ReglasNovedadesPermitidasTest'` → 19 pasan.

## Pendiente / fuera de alcance

- La digitación diaria sigue con `input type="date"` (no se migró a p-datepicker en esta tanda).
- El manual de ayuda (`/ayuda`) no se actualizó todavía con el importador ni la autorización de novedades.
- Producción: correr `000238`–`000247` antes de desplegar el back (V240, V242 y V243 agregan columnas que la app valida al arrancar).
