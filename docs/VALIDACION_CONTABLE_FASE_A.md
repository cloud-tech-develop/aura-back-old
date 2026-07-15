# Validación contable — Fase A (blindar la confianza)

Objetivo: confirmar en runtime que **cada operación genera su asiento y SIEMPRE cuadra**, y que el Balance mantiene `ecuacionContable = 0`. Si algo falla, pégame el resultado y lo arreglamos.

> Regla de oro de cada paso: el asiento debe cumplir **total_debito = total_credito** y **no** debe quedar nada en `error_log`.

---

## 0. Prerrequisitos (una sola vez)

- [ ] **Reiniciar el backend** con el build más reciente (`mvnw clean package -DskipTests` y levantar).
- [ ] Usar una **empresa con PUC sembrado**. Si es nueva: Contabilidad → Plan de Cuentas → **"Cargar PUC Básico"** (siembra cuentas + config + formas de pago).
- [ ] **Abrir un período contable** que cubra hoy: Contabilidad → Períodos → Abrir.

### Consultas de verificación (las usarás en cada paso)
**A) Últimos asientos y su cuadre** (SQL):
```sql
SELECT id, numero_comprobante, tipo_origen, descripcion, total_debito, total_credito,
       (total_debito = total_credito) AS cuadra
FROM asiento_contable
WHERE empresa_id = :EMP
ORDER BY id DESC
LIMIT 20;
```

**B) Detalle de un asiento** (SQL):
```sql
SELECT pc.codigo, pc.nombre, ad.descripcion, ad.debito, ad.credito, ad.tercero_id
FROM asiento_detalle ad
JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
WHERE ad.asiento_id = :ASIENTO_ID
ORDER BY ad.id;
```

**C) Errores de auto-posting** (debe estar VACÍO):
```sql
SELECT id, endpoint, mensaje, detalle, created_at
FROM error_log
WHERE endpoint LIKE 'contabilidad/auto/%'
ORDER BY id DESC LIMIT 20;
```

**D) Balance / ecuación contable** (API): `GET /api/contabilidad/balance?hasta=YYYY-MM-DD`
→ revisar que `ecuacionContable` ≈ **0**.

---

## 1. VENTA de contado (prefijo `VT`, tipoOrigen `VENTA`)
- [ ] POS → vender de **contado** (efectivo) un producto con IVA.
- [ ] Verificar asiento `VT-…`. Esperado: **DB Caja** + **CR Ingresos (4135)** + **CR IVA (2408)** + **DB Costo (6135) / CR Inventario (1435)**.
- [ ] `total_debito = total_credito`. Sin error_log.

## 2. VENTA a crédito / híbrida
- [ ] POS → venta con cliente, parte efectivo + parte crédito.
- [ ] Esperado: **DB Caja** (efectivo) + **DB Clientes (1305)** (saldo crédito, con tercero) + CR Ingresos + CR IVA + par Costo/Inventario.
- [ ] Cuadra. (Esta venta deja un saldo en cartera → se usa en el paso 6.)

## 3. COMPRA (prefijo `CO`, tipoOrigen `COMPRA`)
- [ ] Compras → registrar una compra **a crédito** con IVA (y retención si aplica).
- [ ] Esperado: **DB Inventario (1435)** + **DB IVA descontable (2408)** + (CR Retenciones si hubo) + **CR Proveedores (2205)** (con tercero).
- [ ] Cuadra. (Deja saldo en cuentas por pagar → paso 7.)

## 4. GASTO (prefijo `GT`, tipoOrigen `GASTO`)
- [ ] Contabilidad → Gastos → crear un gasto con cuenta contable.
- [ ] Esperado: **DB cuenta de gasto** (la elegida) + (DB IVA / CR retenciones si hubo) + **CR Caja** por el neto.
- [ ] Cuadra.

## 5. MERMA (prefijo `MM`, tipoOrigen `MERMA`)
- [ ] Inventario → Mermas → registrar una merma con costo.
- [ ] Esperado: **DB Pérdida por merma (5195)** / **CR Inventario (1435)** por el costo.
- [ ] Cuadra.

## 6. COBRO de cartera (prefijo `RC`, tipoOrigen `ABONO_COBRAR`)
- [ ] Cuentas → Cuentas por Cobrar → registrar un **abono** a la venta a crédito del paso 2.
- [ ] Esperado: **DB Caja/Bancos** / **CR Clientes (1305)** (con tercero) por el monto abonado.
- [ ] Cuadra.

## 7. PAGO a proveedor (prefijo `EG`, tipoOrigen `ABONO_PAGAR`)
- [ ] Cuentas → Cuentas por Pagar → registrar un **abono** a la compra del paso 3.
- [ ] Esperado: **DB Proveedores (2205)** (con tercero) / **CR Caja/Bancos** por el monto.
- [ ] Cuadra.

## 8. DEVOLUCIÓN de venta (prefijo `DV`, tipoOrigen `DEVOLUCION`)
- [ ] Ventas → Devoluciones → devolver parcialmente la venta del paso 1.
- [ ] Esperado: **DB Ingresos** + **DB IVA** (reversa) / **CR Caja** (o Clientes si afecta cartera) + par **DB Inventario / CR Costo** si reintegra.
- [ ] Cuadra.

## 9. ANULAR una venta (prefijo `RV`, tipoOrigen `ANULACION_VENTA`)
- [ ] Ventas → anular una venta que tenga asiento.
- [ ] Esperado: contraasiento `RV-…` con **débito y crédito intercambiados** del asiento original → efecto neto cero.
- [ ] Cuadra. (Probar también anular una compra → `ANULACION_COMPRA`.)

## 10. NÓMINA (prefijo `NO`, tipoOrigen `NOMINA`)
- [ ] RRHH → liquidar una nómina y **APROBARLA**.
- [ ] Esperado: **DB Gastos de personal (5105)** (devengado+aportes+provisiones) / **CR Salarios por pagar (2505)** + deducciones + aportes + provisiones.
- [ ] Cuadra.

## 11. PRÉSTAMO — desembolso (prefijo `OB`, tipoOrigen `OBLIGACION`)
- [ ] Tesorería → Obligaciones → crear un préstamo.
- [ ] Esperado: **DB Bancos (1110)** / **CR Obligaciones financieras (2105)** por el monto.
- [ ] Revisar que la **tabla de amortización** se generó y la última cuota deja saldo 0.

## 12. PRÉSTAMO — pago de cuota (prefijo `CU`, tipoOrigen `CUOTA_OBLIGACION`)
- [ ] En el detalle del préstamo → **Pagar** la primera cuota.
- [ ] Esperado: **DB Obligaciones (2105)** (capital) + **DB Gastos financieros (5305)** (interés) / **CR Bancos (1110)** (cuota total).
- [ ] Cuadra.

## 13. CIERRE del período (prefijo `CE`, tipoOrigen `CIERRE`)
- [ ] Antes: corre la consulta **C** (error_log vacío) y revisa que todos los asientos cuadren.
- [ ] Contabilidad → Períodos → **Cerrar** el período.
- [ ] Esperado: asiento `CE-…` que **cancela** todas las cuentas de Ingreso/Costo/Gasto y lleva la diferencia a **Utilidad del Ejercicio (3605)** (CR si ganancia, DB si pérdida).
- [ ] Cuadra. El **Estado de Resultados** del período sigue mostrando el resultado real; el **Balance** muestra la utilidad ya en patrimonio (3605).

## 14. PAGO de nómina (prefijo `PN`, tipoOrigen `NOMINA_PAGO`)
- [ ] RRHH → pagar la nómina aprobada del paso 10 (efectivo o banco).
- [ ] Esperado: **DB Salarios por pagar (2505)** / **CR Caja o cuenta del banco** por el neto.
- [ ] Cuadra.

## 15. PAGO de prestación (prefijo `PP`, tipoOrigen `PRESTACION_PAGO`)
- [ ] RRHH → pagar una prestación (prima/cesantías/vacaciones) en efectivo.
- [ ] Esperado: **DB pasivo provisionado (25xx)** por lo disponible + **DB gasto (5105xx)** por el faltante / **CR Caja** por el total.
- [ ] Cuadra.

## 16. MOVIMIENTO de caja (tipoOrigen `MOVIMIENTO_CAJA`)
- [ ] Caja → registrar un ingreso y un egreso manual con concepto contable.
- [ ] Esperado ingreso: **DB Caja / CR cuenta del concepto**; egreso: al revés. El asiento reutiliza el número del comprobante de caja.
- [ ] Cuadra.

## 17. TESORERÍA (prefijo `TS`, tipoOrigen `TESORERIA`)
- [ ] Tesorería → registrar un recaudo y un egreso bancario con contrapartida.
- [ ] Esperado: **DB Banco / CR contrapartida** (recaudo) y **DB contrapartida / CR Banco** (egreso). Banco = cuenta contable de la cuenta bancaria.
- [ ] Cuadra.

## 18. SALDOS INICIALES / apertura
- [ ] Contabilidad → Apertura → cargar saldos iniciales (caja, bancos, inventario, cartera, proveedores, capital).
- [ ] Esperado: asiento de apertura cuadrado; el Balance arranca con `ecuacionContable ≈ 0`.
- [ ] Reejecutar la apertura no duplica (idempotente).

## 19. REVERSAS restantes (prefijo `RV`)
- [ ] Anular gasto, merma, nómina y devolución → contraasiento `ANULACION_*` con débito/crédito intercambiados; anular dos veces no duplica.
- [ ] Reabrir el período cerrado y verificar que el cierre no se duplica al volver a cerrar.

---

## ✅ Cierre de la Fase A
- [ ] Consulta **A**: TODOS los asientos con `cuadra = true`.
- [ ] Consulta **C**: `error_log` de auto-posting **vacío**.
- [ ] Consulta **D**: `ecuacionContable ≈ 0` antes y después del cierre.

Si los tres se cumplen → el motor es confiable y podemos pasar a la **Fase B** (config UI + saldos iniciales).

---

## Matriz caso → asiento esperado → asiento real (E0)

Registrar aquí el resultado de cada corrida. El **asiento esperado** de cada
flujo queda codificado como golden file en
`src/test/resources/asientos-esperados/` (ADR-004) a medida que su generador
migra al motor nuevo — la venta ya tiene los suyos
(`venta-contado-efectivo.json`, `venta-credito.json`, `venta-mixta-multipago.json`).

| # | Flujo | Documento probado | Comprobante | Esperado = real | Notas |
|---|---|---|---|---|---|
| 1 | Venta contado | | VT- | ☐ | motor nuevo (VentaGenerador) |
| 2 | Venta crédito/mixta | | VT- | ☐ | motor nuevo (VentaGenerador) |
| 3 | Compra (± retenciones) | | CO- | ☐ | |
| 4 | Gasto con IVA/retenciones | | GT- | ☐ | |
| 5 | Merma | | MM- | ☐ | |
| 6 | Abono cartera | | RC- | ☐ | |
| 7 | Pago proveedor | | EG- | ☐ | |
| 8 | Devolución | | DV- | ☐ | |
| 9 | Anulaciones (venta/compra/…) | | RV- | ☐ | |
| 10 | Nómina aprobada | | NO- | ☐ | |
| 11 | Pago nómina / prestación | | PN- / PP- | ☐ | |
| 12 | Obligación + cuota | | OB- / CU- | ☐ | |
| 13 | Mov. caja / tesorería | | RC-/CE- / TS- | ☐ | |
| 14 | Saldos iniciales | | — | ☐ | |
| 15 | Cierre de período | | CE- | ☐ | |
