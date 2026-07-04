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

---

## ✅ Cierre de la Fase A
- [ ] Consulta **A**: TODOS los asientos con `cuadra = true`.
- [ ] Consulta **C**: `error_log` de auto-posting **vacío**.
- [ ] Consulta **D**: `ecuacionContable ≈ 0` antes y después del cierre.

Si los tres se cumplen → el motor es confiable y podemos pasar a la **Fase B** (config UI + saldos iniciales).
