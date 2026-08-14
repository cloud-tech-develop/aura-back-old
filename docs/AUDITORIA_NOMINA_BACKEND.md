# Auditoría de Nómina — Backend · Ruta de hallazgos

Seguimiento de la auditoría del módulo de nómina (motor de liquidación,
prestaciones, seguridad social, pagos). Se actualiza a medida que se resuelve
cada hallazgo. Ver también el frontend en `aura-frontend`.

**Última actualización:** 2026-07-23

## Estado

| ID | Hallazgo | Sev. | Estado | Evidencia / fix |
|----|----------|------|--------|-----------------|
| **B-01** | Prima/cesantías/intereses/vacaciones NO incluían el promedio del salario variable (comisiones, horas extra, recargos). | Crítica | ✅ **Hecho** | `NominaNovedadJPARepository.sumVariableSalarial`, `PromedioSalarialService`, `PrestacionServiceImpl.crearInterno/calcular`. Vacaciones excluyen horas extra. |
| **B-02** | La base salarial se tomaba de `empleado.getSalarioBase()` (caché), no del historial salarial por vigencia → riesgo de liquidar sobre salario desactualizado. | Alta | ✅ **Hecho** | `PrestacionServiceImpl.salarioBaseVigente()` usa `ContratoSalarioHistorialJPARepository.findEnFecha` con fallback a contrato → empleado. Aplica también a indemnización. |
| **B-03** | Indemnización leía tipo de contrato, ingreso y fecha fin del `empleado`, no del `contrato` (aunque lo recibe). | Alta | ✅ **Hecho** | `crearIndemnizacion` ahora usa `contratoLaboral.getTipoContrato/getFechaInicio/getFechaFin` con fallback al empleado. |
| **B-04** | Riesgo de doble pago de cesantías: la definitiva partía "desde la última prestación PAGADA"; una consignación anual no registrada como PAGADA re-liquidaba desde el ingreso. | Alta | ✅ **Hecho** | Ancla ahora a la última prestación **no anulada** (`findTop...EstadoNot`); nuevo `cesantiasCorteHasta` en `LiquidacionDefinitivaDto` para consignaciones externas. |
| **B-05** | (a) Umbral exoneración 1607 comparaba `totalDevengado < 10 SMMLV`. (b) Ausencias por incapacidad bajaban el IBC de pensión, cuando debe mantenerse pleno. | Media-Alta | ✅ **Hecho** | (a) ahora compara `baseIbcSinTope` (salarial). (b) `BasesLiquidacion.salarioParaIbc` + `MotorLiquidacion.diasAusenciaQueReducenIbc`: incapacidad/maternidad/vacaciones mantienen IBC; solo LNR/suspensión lo reducen. **Borde `<`/mensualización parcial: pendiente contador.** |
| **B-06** | Doble fuente de verdad del salario: `empleado.salarioBase` (que el alta nueva deja en **0**) se seguía leyendo como fuente de cálculo → incapacidades y novedades de asistencia se valorizaban sobre 0. | Alta (era Media) | ✅ **Hecho** | Helper `salarioDelContrato` en `NominaServiceImpl` (incapacidad + novedades staged) y `salarioBasePreliquidacion` en `PreliquidacionServiceImpl`. Todos los cálculos leen del contrato; `empleado.salarioBase` queda solo como fallback defensivo (display/legacy). |
| **B-07** | `pagar`/`pagarLote` marcaban `PAGADA` y descontaban banco + contabilizaban en un paso, sin confirmación real del banco. | Media | ✅ **Hecho** (falta front) | Nuevo estado `PROGRAMADA` (V134). Transferencia: `pagar` → PROGRAMADA (sin banco/contab); `confirmarPago`/`confirmarPagoLote` → PAGADA (descuenta banco + asiento). Efectivo/cheque siguen directo. Endpoints `PUT /{id}/confirmar-pago` y `/lote/{lote}/confirmar-pago`. |
| **B-08** | (Detectado durante B-01) Disfrute de vacaciones usaba `base × díasHábiles / 720` (≈ salario/48 por 15 días). | Media | ✅ **Hecho** | `calcular` distingue disfrute (`base/30 × días` → salario/2 por 15 hábiles) de causación (`/720` sobre días trabajados). Consistente con la provisión anual (4.17%/mes). |
| **B-09** | El motor NO diferenciaba por tipo de contrato: **prestación de servicios se liquidaba como nómina laboral** (SS, aportes, provisiones, auxilio) → cálculo ilegal + evidencia de contrato realidad + lo metía en la PILA de la empresa. | Crítica | ✅ **Hecho** | `findVigentesEnPeriodo` excluye `PRESTACION_SERVICIOS` (liquidación masiva + PILA); `liquidarContrato` lo rechaza con mensaje (→ cuenta de cobro/compra); prestaciones (`crear`/`generarLote`/`liquidacionDefinitiva`) lo excluyen. |
| **B-10** | Aprendiz SENA sin régimen especial: se liquidaba con SS completa + parafiscales + provisiones + auxilio. | Alta | ✅ **Hecho** | Motor excluye pensión/parafiscales/provisiones/FSP/retefuente/auxilio. Campo `fase` en contrato (V135): **ARL solo en práctica**. Apoyo de sostenimiento = SMMLV × % por fase (`aprendiz_pct_lectiva`=50 / `aprendiz_pct_practica`=75 en `nomina_config`, parametrizable) cuando el contrato no trae valor. **Pendiente contador:** base/split de salud del aprendiz (sobre 1 SMMLV, a cargo del patrocinador). Front: form de contrato debe mostrar `fase` si tipo=APRENDIZAJE; config UI puede exponer los %. |

Leyenda: ✅ hecho · 🟡 mitigado/parcial · ⬜ pendiente

## Notas de implementación

### B-01 — promedio del salario variable
- Fuente: `nomina_novedad` (devengado, `constituye_ibc = true`, no anuladas) del empleado en el rango de períodos que cruzan `[fechaDesde, fechaHasta]`.
- Promedio = total variable ÷ meses de la ventana (`díasRef / 30`) → valor mensual sumado al salario antes de prorratear por días.
- Vacaciones: `excluirHorasExtra = true` (trabajo suplementario no hace base de vacaciones); comisiones y recargos sí entran.
- Fallback seguro: sin novedades variables el promedio es 0 → resultado idéntico al comportamiento previo para salario fijo.

### B-02 — salario por vigencia
- `salarioBaseVigente(empleado, contrato, fechaCorte)`: historial (`findEnFecha`) → contrato → empleado.
- Fecha de corte = `fechaHasta` de la prestación (retiro en indemnización). Correcto para cesantías/prima liquidadas sobre el último salario del período.

## Pendiente transversal (no numerado como hallazgo)
- Los **devengos variables (novedades) no se emiten como líneas de `nomina_detalle`**: se pliegan en las bases. Rompe el desprendible detallado y la nómina electrónica DIAN (que exige cada devengado en su etiqueta). Revisar al abordar nómina electrónica.
- Pruebas unitarias de B-01/B-02 aún sin escribir.

## Dependencias de frontend (aura-frontend) — ✅ HECHAS
- **B-07** ✅: `EstadoPrestacion` incluye `PROGRAMADA`; `prestacion.service` tiene
  `confirmarPago`/`confirmarPagoLote`; el componente muestra el estado, un botón
  **"Confirmar pago"** (visible en PROGRAMADA) y el toast del pago distingue
  transferencia (queda programada) de efectivo (pagada).
- **B-04** ✅: `LiquidacionDefinitivaDto.cesantiasCorteHasta` + campo de fecha
  "Cesantías ya consignadas hasta" en el diálogo de definitiva.
- **B-10** ✅: `FaseAprendiz` + `FASE_APRENDIZ_OPTS`; `form-contrato` muestra el
  selector **fase** solo si `tipoContrato = APRENDIZAJE`, con validación. *(Pendiente
  opcional: exponer `aprendizPctLectiva/Practica` en la config de nómina.)*
- **B-09** ✅ (sin cambio): los mensajes de rechazo del backend (servicios no se
  liquida por nómina) ya se muestran vía `err.error.message` en liquidar, generar-lote,
  crear y definitiva. *(Opcional: ocultar "liquidar" para contratos de servicios.)*

Front verificado con `ng build` (bundle OK; el único warning es un `?.` redundante
preexistente en `pila.component.html`, ajeno a estos cambios).

## Próximo bloque de auditoría (sin empezar)
Seguridad social / PILA (conecta con B-05) **o** novedades + asistencia/frentes
(conciliación de días y riesgo de alteración de horas aprobadas).
