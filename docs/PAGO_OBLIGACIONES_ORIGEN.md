# Origen de pago en cuotas de obligaciones financieras

> Estado: **Implementado (Opción 1 — alcance mínimo)** · Fecha: 2026-06-30

## Problema

Al pagar una cuota de una obligación financiera (préstamo), el sistema **forzaba**
que el dinero saliera de la **misma cuenta bancaria del desembolso**
(`ObligacionFinancieraEntity.cuentaBancariaId`). Tanto el descuento de saldo real
como el crédito del asiento contable salían de esa cuenta, sin opción de elegir.

Era el **único flujo de tesorería sin selección de origen**: ventas y compras ya
permiten elegir `metodoPago` + `cuentaBancariaId` por cada pago.

### Por qué es un bug contable (no solo UX)

La partida doble permite cancelar un pasivo con **cualquier** activo; el origen del
desembolso es irrelevante para el pago. Si el desembolso entró al Banco X pero la
cuota se paga en efectivo o desde el Banco Y, atar el crédito a la cuenta del
desembolso registra la salida de un activo que **no se movió** → descuadre
**silencioso** que solo aparece en la conciliación bancaria (la ecuación
`Activo = Pasivo + Patrimonio` se mantiene, por eso no salta a la vista).

## Solución implementada

Se permite elegir el origen del dinero al pagar la cuota, reusando la
infraestructura ya existente `resolverCuentaPago(empresaId, metodoPago, cuentaBancariaId)`
(la misma que usan ventas y compras). Alcance: **solo activos monetarios** (caja +
cualquier cuenta bancaria).

### Cambios

| Archivo | Cambio |
|---|---|
| `dto/obligaciones/PagarCuotaDto.java` | **Nuevo.** `metodoPago` + `cuentaBancariaId`, ambos opcionales. |
| `entity/CuotaAmortizacionEntity.java` | +`metodoPago`, +`cuentaBancariaIdPago` (persisten el origen para que el motor —que corre AFTER_COMMIT— lo relea). |
| `services/ObligacionFinancieraService.java` | Firma de `pagarCuota` recibe `PagarCuotaDto pago`. |
| `controllers/ObligacionFinancieraController.java` | `@RequestBody(required=false) PagarCuotaDto`. |
| `services/.../ObligacionFinancieraServiceImpl.java` | Resuelve origen, persiste en la cuota, ramifica descuento (banco vs egreso de caja). |
| `services/.../ContabilidadAutoServiceImpl.java` | `generarDesdePagoCuota` lee el origen de la cuota en vez del desembolso. |

### Regla de resolución de origen (compatible hacia atrás)

```
metodoPago   = pago.metodoPago?.toUpperCase()            // null si no se envía
esEfectivo   = metodoPago contiene "EFECTIVO"
cuentaOrigen = pago.cuentaBancariaId                      // 1º: lo elegido
               ?? (esEfectivo ? null                      // 2º: efectivo → caja
                              : obligacion.cuentaBancariaId) // 3º: fallback desembolso
```

- **Sin body** (o todo null) → sale de la cuenta del desembolso = **comportamiento anterior**.
- **cuentaOrigen != null** → descuenta de esa cuenta bancaria; el asiento acredita su `cuentaContableId`.
- **EFECTIVO sin cuenta** → egreso de caja en el turno abierto (patrón de compras CONTADO); el asiento acredita CAJA.

El asiento de la cuota sigue siendo: `DB capital (2105) + DB interés (gasto fin.) / CR Caja|Bancos del origen`.

## Fuera de alcance (decisión consensuada — agendar para Fase C)

- **Pagos parciales / abonos extraordinarios a capital.** Rompen la correspondencia
  capital/interés precalculada de la tabla y exigen **recálculo de amortización**
  (reducir plazo vs reducir cuota). Requiere un `PagoObligacionEntity` (1..N, con
  `cuotaId` nullable) en vez de campos en la cuota, e indexar el asiento por
  `PAGO_OBLIGACION/pagoId`. Hoy `pagarCuota` solo cancela cuotas completas de la tabla.
- **Reversa de pago individual de cuota.** Hoy `anular(obligacion)` reversa solo el
  desembolso y se bloquea si hay cuotas pagadas. No hay "revertir pago de cuota".
  Cuando se agregue: cuota → PENDIENTE, devolver saldo a obligación y a la cuenta
  origen, y publicar `ContabilidadReversaEvent("CUOTA_OBLIGACION", cuotaId, ...)`.
  El contraasiento genérico ya respeta el origen persistido (otra razón por la que
  persistir `cuentaBancariaIdPago` es indispensable).
- **Dación en pago con activo no monetario** (inventario, vehículo). Marginal; se
  maneja como comprobante contable manual. Punto de extensión futuro: un
  `cuentaContableIdOrigen` opcional en `PagarCuotaDto` con prioridad en `resolverCuentaPago`.

## Riesgos conocidos / pendientes

- **Fondos insuficientes / sobregiro.** Pagar desde cualquier cuenta puede dejar
  saldo negativo. Pendiente reusar `permiteSobregiro`/`cupoSobregiro` de
  `CuentaBancariaEntity` (Pieza 1 de tesorería) y rechazar si cruza a negativo.
- **Efectivo sin turno abierto.** Hoy, igual que en compras, el egreso de caja solo
  se registra si hay turno abierto (`.ifPresent`); si no hay turno, el saldo de caja
  no se ajusta (hueco caja↔contabilidad). Pendiente endurecer (exigir turno).
- **Fecha de pago.** `pagarCuota` fuerza `LocalDate.now()`. Si se permite registrar
  pagos con fecha distinta, ajustar.

## Abonos a cuentas por pagar — origen de pago (completado)

`ContabilidadAutoServiceImpl.generarDesdeAbonoPagar` (línea ~486) ya invocaba
`abono.getCuentaBancariaId()`, pero `AbonoPagarEntity` no tenía ese campo (cambio
previo a medio terminar) y los servicios que crean abonos no lo seteaban → el motor
caía siempre al fallback por método de pago. Se completó el cableado, en línea con
el patrón de obligaciones/compras:

| Archivo | Cambio |
|---|---|
| `entity/AbonoPagarEntity.java` | +`cuentaBancariaId`. |
| `dto/cuentas_pagar/AbonoPagarDto.java` | +`cuentaBancariaId` (entrada y salida). |
| `services/.../CuentaPagarServiceImpl.java` | Inyecta `CuentaBancariaJPARepository`; `registrarAbono` setea el campo y **descuenta** el saldo de la cuenta bancaria; `eliminarAbono` **devuelve** el saldo (simetría); `toAbonoDto` mapea el campo. |

Resultado: un abono a proveedor desde una cuenta bancaria descuenta el saldo real de
esa cuenta y el asiento acredita su cuenta contable; el efectivo (sin cuenta) cae a CAJA.

> Pendiente conocido (fuera de alcance): `eliminarAbono` revierte saldos pero **no
> emite contraasiento** del pago (no publica `ContabilidadReversaEvent`). Gap previo,
> a resolver junto con la reversa de pagos individuales.

## Frontend (repo aura-frontend, Angular)

Ambos endpoints quedaron compatibles hacia atrás; el front se actualizó para **enviar**
el origen y reusa el patrón de carga de cuentas bancarias (`CuentaBancariaService.list()`).

**Pago de cuota de obligación** (`features/obligaciones/detalle`):
- `obligacion.model.ts`: +`PagarCuotaDto`, +`metodoPago`/`cuentaBancariaIdPago` en la cuota.
- `obligacion.service.ts`: `pagarCuota(obligacionId, cuotaId, pago?)` ahora manda body.
- `detalle-obligacion.component`: el botón "Pagar" abre un **diálogo** (medio de pago +
  cuenta de origen). Por defecto sugiere la cuenta del desembolso por transferencia;
  si es EFECTIVO oculta la cuenta (sale de caja). Reemplaza el antiguo `p-confirmDialog`.

**Abono a cuenta por pagar** (`features/cuentas/detalle-cuenta-pagar`):
- `cuenta-pagar.model.ts`: +`cuentaBancariaId` en `CreateAbonoPagarDto` y `AbonoPagarModel`.
- `detalle-cuenta-pagar.component`: nuevo selector **Cuenta de origen** en el form de
  abono, visible cuando el método no es efectivo; se incluye en el DTO.

Verificación: `ng build --configuration development` → **OK** (solo warnings preexistentes
en componentes ajenos).

## Verificación (backend)

- `mvnw test-compile` → **OK** (main + tests).
- `CuentaPagarServiceTest`: 3 fallas + 4 errores **preexistentes** (verificado contra
  baseline con `git stash`: idénticos números sin estos cambios). Causa: el test no
  mockea `eventPublisher`/`turnoCajaRepository` con `@InjectMocks` por constructor.
  No están relacionados con este trabajo.
