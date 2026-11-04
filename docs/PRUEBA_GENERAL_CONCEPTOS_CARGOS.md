# Prueba general: conceptos, fórmulas y cargos de nómina

Fecha: 2026-10-24. Cubre F1–F7 de docs/PLAN_CONCEPTOS_CARGOS_NOMINA.md. Base local con V230–V233.

## 0. Antes de empezar
- Backend en Docker arriba (`docker logs aura-backend` → "Started AuraPosApplication").
- Front recargado. Usuario administrador de una empresa con nómina en modo COMPLETO.
- SQL del menú corrido: `docs/sql/menu_submodulos_nomina_conceptos.sql` (Grupos de conceptos).

## 1. Conceptos (Nómina → Conceptos)
| # | Paso | Esperado |
|---|---|---|
| 1.1 | Abrir el listado | Código numérico (1, 2, 100, 200…), nunca texto. Paginado. |
| 1.2 | Filtrar por clase Deducido y buscar "salud" | Sale 100 Salud empleado. |
| 1.3 | Editar el 100: cambiar redondeo a Centena - Hacia arriba, guardar | Guarda; etiqueta "De ley (ajustado)"; clase/tipo/cálculo bloqueados. |
| 1.4 | Nuevo concepto: código propuesto, nombre "SUBSIDIO ALIMENTACIÓN", Devengado, tipo Auxilio, base Valor fijo 120.000 | Se crea con el número; abre su página. |
| 1.5 | Nueva vigencia del 1.4 desde el mes siguiente, 150.000 | La anterior queda cerrada el día antes. |
| 1.6 | Nuevo concepto con base **Fórmula**: `SI(SALARIO_BASICO <= 2 * SMMLV; 50000; 0)` | Editor valida en verde; "Probar" calcula 50.000 con los ejemplos. |
| 1.7 | Escribir `SALARIO_X * 2` | Error en rojo: la variable no existe. |
| 1.8 | Fórmula que use su propio número `[n]` | Error: no puede usarse a sí misma. |
| 1.9 | Pestaña Afectado por del 100 | Lista salario y lo que constituye IBC. |
| 1.10 | Pestaña Contable del 1.4: cambiar la cuenta débito | Guarda; aparece igual en Cuentas de nómina. |
| 1.11 | Pestaña Centros de costo del 1.4: elegir un centro | Guarda. |

## 2. Cargos (Nómina → Cargos)
| # | Paso | Esperado |
|---|---|---|
| 2.1 | Nuevo cargo AUXILIAR ADMINISTRATIVO, nivel Asistencial | Crea y muestra todas las pestañas. |
| 2.2 | Conceptos → Agregar conceptos: marcar 1.4 y 1.6 | Se agregan; los ya agregados salen en gris en el buscador. |
| 2.3 | Novedades permitidas → agregar 10 Hora extra diurna, tope 10, requiere autorización Sí | Guarda. |
| 2.4 | Máximo de horas: HED 3 al día | Rechaza: la ley permite 2. Con 2 guarda. |
| 2.5 | Cambiar nivel a Directivo e intentar tope de horas extra | Rechaza (art. 162 CST). |
| 2.6 | Adjuntos: subir un PDF | Aparece en la lista y abre. |
| 2.7 | Otro cargo → Copiar de otro cargo | Trae conceptos, novedades y topes. |

## 3. Vinculación (herencia)
| # | Paso | Esperado |
|---|---|---|
| 3.1 | Asignar el cargo 2.1 a una vinculación | Pestañas Conceptos y Novedades muestran filas "Del cargo". |
| 3.2 | Excluir el 1.4 para ese empleado | Sale "Excluido"; "Volver a heredar" lo restaura. |
| 3.3 | Personalizar el 1.4 con otro valor | Queda fila propia; reemplaza la del cargo. |

## 4. Liquidación (prenómina)
| # | Paso | Esperado |
|---|---|---|
| 4.1 | Prenómina con ese empleado → Liquidar | Liquida; sale el 1.4 ("Concepto del cargo" en la traza) y el 1.6 con su fórmula en la traza. |
| 4.2 | Revisar el 100 Salud | Valor redondeado a la centena hacia arriba; traza con "Redondeo". |
| 4.3 | Empleado de otro cargo con plantilla sin el 1.4 | No le sale el 1.4. Empleado sin cargo con plantilla: le salen todos (como antes). |
| 4.4 | Novedad de 12 horas extra diurnas (tope 10, sin aprobar) | Rechaza: supera el tope y requiere autorización. |
| 4.5 | Novedad de 3 horas extra el mismo día (tope 2/día) | Rechaza con la fecha. |
| 4.6 | Contabilidad de la prenómina | El 1.4 va a la cuenta y al centro de costo configurados. |
| 4.7 | Código en el detalle de la liquidación | Número, no texto. |

## Resultado de la corrida (2026-10-24, base local, empresa 4)

| Paso | Resultado |
|---|---|
| 1.1–1.11 | OK. Concepto 304 (valor fijo, cuenta 510545, centro ADM-001) y 305 (fórmula `SI(SALARIO_BASICO <= 2 * SMMLV; 50000; 0) + [304] * 10%`, orden 101 automático). Nueva vigencia del 304: 150.000 desde 01/11, la anterior cerrada el 31/10. |
| 2.1–2.5 | OK. Cargo PRU-66 (id 4) con conceptos 1/304/305, novedad 10 (tope 10, autorización) y tope HED 2/día. Directivo rechaza horas extra. |
| 2.6, 2.7 | No probados (adjuntos y copiar de otro cargo). |
| 3.1–3.2 | OK. VIN-00003 hereda del cargo; excluir y volver a heredar. 3.3 no probado. |
| 4.1 | OK. Prenómina PRE-NOM-00003 (nov 2026): 304 = 150.000, 305 = 65.000 con traza de la fórmula. |
| 4.2 | OK. Salud de Alex 78.636,20 → 78.700 (centena hacia arriba). |
| 4.3 | OK. Cargo sin plantilla recibe todos los conceptos de la empresa. |
| 4.4 | OK. 12 horas extra con tope 10 sin aprobar: rechazada con el mensaje de autorización. |
| 4.5 | No se puede probar desde la prenómina: el formulario de novedades no pide fecha (solo aplica el tope del mes). |
| 4.6 | OK. 304 con centro de costo fijo en el detalle. |
| 4.7 | OK. El detalle muestra números. |

### Errores encontrados y corregidos en la corrida
1. Buscador de conceptos: una respuesta vieja pisaba la nueva al escribir rápido (ahora espera 300 ms y descarta respuestas viejas; también en el buscador avanzado).
2. Diálogo de nueva vigencia congelaba la página: `minDate` salía de un getter que creaba un Date en cada ciclo.
3. Topes de horas de un cargo directivo congelaban la página (`p-inputNumber` + `ngModel` + `[disabled]`) y dejaban el cargo atrapado con topes viejos: ahora las filas dicen "No aplica" y al guardar se mandan vacías.
4. Editar un cargo, un concepto propio o las filas de la vinculación respondía con el estado anterior (`save()` sin flush + relectura SQL): `saveAndFlush`.
5. **Los devengados del catálogo (valor fijo, porcentaje, fórmula) no entraban al IBC ni a las bases:** el motor ahora calcula primero los devengados, recalcula las bases y después deducciones, aportes y provisiones.
6. Mensajes de novedades mostraban la clave interna (HORA_EXTRA_DIURNA): ahora "10 - Hora extra diurna".
7. Detalles: columna Cálculo decía "—" en fórmulas; subtítulo del cargo "0 de sin límite de plazas"; botones de fila apilados.

### Datos de prueba que quedaron en la base local
Conceptos 304 y 305 (aplican a todos los empleados de cargos sin plantilla), personalización del 100 con redondeo
a la centena, cargo PRU-66 (id 4), VIN-00003 pasó del cargo 1 al 4, prenómina PRE-NOM-00003 reliquidada y con
ALEX JOSE RIVERA agregado.
