# Diseño técnico — Contabilidad avanzada Aura Nube

> Objetivo: llevar el motor contable actual (fases 0–8 + apertura) al nivel de un ERP
> contable competitivo en Colombia: cuentas por categoría de producto, impuestos como
> reglas, estados de comprobante, dimensiones, conciliación bancaria y exógena.
> Este documento está anclado al código real de `aura-back-old` (verificado 2026-07-08).

---

## 1. Punto de partida (lo que YA existe en el código)

El motor central es `ContabilidadAutoServiceImpl` con estos generadores:

| Generador | Origen | Prefijo / tipoOrigen |
|---|---|---|
| `generarDesdeVenta` | VentaContabilizableEvent (AFTER_COMMIT) | VE / VENTA |
| `generarDesdeCompra` | CompraContabilizableEvent | CO / COMPRA |
| `generarDesdeDevolucion` | DevolucionContabilizableEvent | DV / DEVOLUCION |
| `reversar(origenTipo, origenId)` | ContabilidadReversaEvent | RV / ANULACION_* |
| `generarDesdeAbonoCobro` / `generarDesdeAbonoPago` | AbonoContabilizableEvent | RC / EG |
| `generarDesdeGasto` / `generarDesdeMerma` | OperacionContabilizableEvent | GT / MM |
| `generarDesdeNomina` / `generarDesdePagoNomina` / `generarDesdePagoPrestacion` | OperacionContabilizableEvent | NO / … |
| `generarDesdeObligacion` / `generarDesdePagoCuota` | OperacionContabilizableEvent | OB / CU |
| `generarDesdeMovimientoCaja` / `generarDesdeTesoreria` | eventos tesorería | — |
| `generarCierre` | dentro de `cerrarPeriodo` (REQUIRED) | CE / CIERRE |

Infraestructura ya construida:

- **PUC** sembrado por empresa (`PlanCuentasServiceImpl.seedPUC`) + `PlanCuentaEntity`.
- **Config concepto→cuenta por empresa**: enum `ConceptoContable` (con código default) +
  `CuentaConfigEntity` + `ConfiguracionContableService.resolverCuenta(empresaId, concepto)`.
  Endpoint `/api/contabilidad/configuracion-cuentas`.
- **Resolución de medio de pago**: `resolverCuentaPago(empresaId, metodoPago, cuentaBancariaId)`
  — usa `CuentaBancariaEntity.cuentaContableId` si existe, si no EFECTIVO→CAJA / resto→BANCOS.
- **Validación de cuadre**: `AsientoBalanceValidator` (débitos = créditos, no negativos).
- **Períodos contables** con cierre que genera asiento a 3605 (utilidad/pérdida).
- **Apertura / saldos iniciales**: `AperturaContableServiceImpl` + `CreateSaldosInicialesDto`.
- **Retenciones en compras**: retefuente/reteiva/reteica calculadas en `CompraEntity`,
  acreditadas a 2365/2367/2368. `TarifaRetencionEntity` existe (V57).
- **Dimensiones parciales**: `asiento_detalle` tiene `tercero_id` y `centro_costo_id` (V51).
- **Nómina desglosada** por auxiliar 5105xx / pasivos 2370-2525.
- **Estado del asiento**: campo `estado` en `AsientoContableEntity`, hoy siempre `"CONTABILIZADO"`.

**Regla vigente que se mantiene en todo lo nuevo:** documento operativo → evento
AFTER_COMMIT → generador con `resolverCuenta`/`resolverCuentaPago` → asiento cuadrado con
`tipoOrigen`+`origenId` (trazabilidad doble vía) → reversa por contraasiento, nunca edición.

---

## 2. Brechas vs. contabilidad avanzada

| # | Capacidad | Estado hoy | Fase |
|---|---|---|---|
| 1 | Formas de pago parametrizables (medio de pago→cuenta) | Solo banco→cuenta; EFECTIVO/resto hardcoded | C1 (pieza 2 tesorería) |
| 2 | Compra con destino contable (inventario/gasto/activo) + centro de costo | Compra siempre debita INVENTARIO | C1 (pieza 4) |
| 3 | Sobregiro bancario | No existe | C1 (pieza 5) |
| 4 | Cuentas por **categoría de producto** (ingreso/inventario/costo) con jerarquía producto→categoría→empresa | Una sola cuenta global por concepto | C2 |
| 5 | Impuestos como **reglas parametrizables** (tabla, % vigencia, cuenta por impuesto) | IVA implícito en producto; 2408 única para generado y descontable | C3 |
| 6 | Estados de comprobante (BORRADOR→CONTABILIZADO) y modo "revisión del contador" | Todo nace CONTABILIZADO | C4 |
| 7 | Dimensiones proyecto/frente/canal en el asiento + reportes por dimensión | Solo tercero + centro de costo | C5 |
| 8 | Conciliación bancaria | No existe | C6 |
| 9 | Información exógena DIAN | No existe nada | C7 |
| 10 | Diferencias de cierre de caja contabilizadas | Movimientos de caja se contabilizan; falta faltante/sobrante | C4 (pequeño) |

Lo que el documento de referencia pide y **ya está resuelto**: asiento automático de venta
con split contado/crédito multi-pago, costo de venta + salida de inventario, compras con
retenciones, cartera y proveedores con abonos, nómina, depreciación, cierre, reversas,
saldos iniciales, jerarquía de resolución de cuentas (nivel empresa), trazabilidad.

---

## 3. FASE C1 — Terminar parametrización de tesorería (piezas 2, 4, 5)

Ya diseñada en `.claude/agents/tesoreria-contable.md`; se resume el contrato final:

### 3.1 Pieza 2 — `FormaPagoContableEntity`

```sql
-- V85__forma_pago_contable.sql
CREATE TABLE forma_pago_contable (
  id BIGSERIAL PRIMARY KEY,
  empresa_id INT NOT NULL REFERENCES empresa(id),
  codigo VARCHAR(40) NOT NULL,           -- EFECTIVO, TRANSFERENCIA, TARJETA, NEQUI...
  nombre VARCHAR(80) NOT NULL,
  cuenta_contable_id BIGINT REFERENCES plan_cuenta(id),
  requiere_cuenta_bancaria BOOLEAN NOT NULL DEFAULT FALSE,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (empresa_id, codigo)
);
```

- Seed idempotente al crear empresa: EFECTIVO→1105, TRANSFERENCIA/TARJETA→1110.
- `resolverCuentaPago` cambia su prioridad a:
  `cuentaBancaria.cuentaContableId → formaPagoContable(codigo) → fallback CAJA/BANCOS`.
- CRUD `/api/contabilidad/formas-pago` + pantalla en aura-frontend (tabla plana).

### 3.2 Pieza 4 — Compra con destino contable

- `CompraEntity` += `centroCostoId`, `cuentaContableId` (nullable).
- Débito de la compra = `cuentaContableId ?? resolución por categoría de producto (C2) ?? INVENTARIO`.
- El centro de costo se propaga a todas las líneas del asiento.

### 3.3 Pieza 5 — Sobregiro (enfoque A)

- `CuentaBancariaEntity` += `permiteSobregiro`, `cupoSobregiro`.
- El motor NO bloquea saldo negativo si `permiteSobregiro`; en el cierre de período se
  reclasifica el saldo crédito de 1110 a 21xx (sobregiros) con asiento de reclasificación
  y su reversa al abrir el período siguiente.

---

## 4. FASE C2 — Categorías contables de producto (jerarquía de cuentas)

Es el salto más importante hacia "ERP": hoy toda venta acredita UNA cuenta de ingreso.
Con esto, bebidas gravadas, servicios y activos fijos contabilizan distinto sin que el
cajero vea cuentas.

### 4.1 Modelo

`CategoriaEntity` (categoría comercial) se mantiene; la contable es una tabla aparte
para no mezclar taxonomía de venta con parametrización contable:

```sql
-- V86__categoria_contable_producto.sql
CREATE TABLE categoria_contable_producto (
  id BIGSERIAL PRIMARY KEY,
  empresa_id INT NOT NULL REFERENCES empresa(id),
  nombre VARCHAR(80) NOT NULL,               -- "Mercancía gravada", "Servicios", "Insumos"
  tipo VARCHAR(20) NOT NULL DEFAULT 'BIEN',  -- BIEN | SERVICIO | INSUMO | ACTIVO_FIJO
  cuenta_ingreso_id BIGINT REFERENCES plan_cuenta(id),      -- 4135 / 4145
  cuenta_inventario_id BIGINT REFERENCES plan_cuenta(id),   -- 1435 / 1455 / null si servicio
  cuenta_costo_id BIGINT REFERENCES plan_cuenta(id),        -- 6135 / 61xx
  cuenta_devolucion_id BIGINT REFERENCES plan_cuenta(id),   -- 4175 (opcional; default: ingreso)
  impuesto_id BIGINT,                                        -- FK a impuesto (C3), nullable
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (empresa_id, nombre)
);

ALTER TABLE producto ADD COLUMN categoria_contable_id BIGINT
  REFERENCES categoria_contable_producto(id);
-- overrides excepcionales por producto (normalmente NULL):
ALTER TABLE producto ADD COLUMN cuenta_ingreso_id BIGINT REFERENCES plan_cuenta(id);
ALTER TABLE producto ADD COLUMN cuenta_costo_id BIGINT REFERENCES plan_cuenta(id);
ALTER TABLE producto ADD COLUMN cuenta_inventario_id BIGINT REFERENCES plan_cuenta(id);
```

Seed por empresa: una categoría "General" con las cuentas actuales (4135/1435/6135) y
**todos los productos sin categoría caen ahí** → cero fricción para clientes existentes.

### 4.2 Servicio de resolución (nuevo)

```java
// services/ResolucionCuentaProductoService
Long resolverIngreso(ProductoEntity p, Integer empresaId);
Long resolverCosto(ProductoEntity p, Integer empresaId);
Long resolverInventario(ProductoEntity p, Integer empresaId);
```

Orden (la jerarquía del diseño, niveles 1-2-5-6):
```
1. producto.cuentaXxxId                         (excepción puntual)
2. producto.categoriaContable.cuentaXxxId       (regla normal)
3. config.resolverCuenta(empresaId, CONCEPTO)   (nivel empresa, ya existe)
```

### 4.3 Cambios al motor

`generarDesdeVenta` — hoy: 1 línea INGRESOS + 1 línea IVA + 1 par COGS. Pasa a **agrupar
por cuenta resuelta**:

```java
Map<Long, BigDecimal> ingresosPorCuenta = new LinkedHashMap<>();
Map<Long, BigDecimal> costoPorCuentaCosto = ...;
Map<Long, BigDecimal> costoPorCuentaInventario = ...;
for (VentaDetalleEntity det : detalles) {
    ingresosPorCuenta.merge(resolver.resolverIngreso(det.getProducto(), empresaId),
                            baseLinea(det), BigDecimal::add);
    if (esBienConInventario(det)) {
        costoPorCuentaCosto.merge(resolver.resolverCosto(...), det.getCostoLinea(), ...);
        costoPorCuentaInventario.merge(resolver.resolverInventario(...), det.getCostoLinea(), ...);
    }
}
// una línea de crédito por cada cuenta de ingreso; par débito/crédito por cada cuenta COGS
```

Lo mismo en `generarDesdeCompra` (débito por categoría: inventario vs 1455 vs gasto vs
15xx activo) y `generarDesdeDevolucion` (proporcional por línea, cuenta de devolución).
Los débitos (caja/bancos/clientes) y retenciones no cambian.

**Servicios (`tipo = SERVICIO`)**: sin par COGS ni inventario; ingreso a 4145.

### 4.4 API / Front

- CRUD `/api/contabilidad/categorias-producto` (mismo patrón de `ConfiguracionContableController`).
- Front: pantalla "Categorías contables" en el módulo Contabilidad (tabla plana + form
  plano estándar) y un dropdown "Categoría contable" en el form de producto (default General).

### 4.5 Criterio de aceptación

Venta mixta con 2 productos de categorías distintas + 1 servicio → asiento con 3 créditos
de ingreso a cuentas distintas, 1 solo par COGS por cada cuenta de costo, cuadrado, y una
empresa sin configurar nada contabiliza EXACTAMENTE igual que hoy.

---

## 5. FASE C3 — Impuestos como reglas parametrizables

### 5.1 Modelo

```sql
-- V87__impuestos.sql
CREATE TABLE impuesto (
  id BIGSERIAL PRIMARY KEY,
  empresa_id INT NOT NULL REFERENCES empresa(id),
  nombre VARCHAR(80) NOT NULL,        -- "IVA 19%", "IVA 5%", "INC 8%", "Excluido"
  tipo VARCHAR(20) NOT NULL,          -- IVA | INC | EXCLUIDO | EXENTO
  porcentaje NUMERIC(6,3) NOT NULL DEFAULT 0,
  cuenta_generado_id BIGINT REFERENCES plan_cuenta(id),     -- ventas: 240801
  cuenta_descontable_id BIGINT REFERENCES plan_cuenta(id),  -- compras: 240802
  vigente_desde DATE, vigente_hasta DATE,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (empresa_id, nombre)
);
ALTER TABLE producto ADD COLUMN impuesto_id BIGINT REFERENCES impuesto(id);
```

Seed por empresa: IVA 19, IVA 5, INC 8, Excluido, Exento. Y en el seed del PUC, abrir
subcuentas **240801 IVA generado** / **240802 IVA descontable** (hoy ambas van a 2408 —
esto rompe el reporte de IVA neto). Los conceptos `IVA_GENERADO` / `IVA_DESCONTABLE`
cambian su default a esas subcuentas; empresas existentes se migran con UPDATE de
`cuenta_config`.

### 5.2 Compatibilidad

El producto hoy tiene % IVA propio (V53/V58: desglose y "IVA incluido"). Migración de
datos: por cada empresa, mapear `porcentajeIva` del producto → `impuesto_id` equivalente.
El cálculo en venta/compra no cambia de lugar (sigue en el documento); lo que cambia es
que el **asiento agrupa impuesto por `impuesto.cuenta_xxx`** en vez de una sola línea 2408.
`venta_desglose_iva` sigue siendo la fuente del valor.

### 5.3 Retenciones

`TarifaRetencionEntity` ya existe; se le agrega `cuenta_contable_id` (hoy la cuenta sale
del concepto fijo 2365/2367/2368, que sigue de fallback). Retención en VENTAS (nos
practican, 1355) queda para cuando el modelo de venta capture "cliente agente retenedor" —
misma pendiente declarada de Fase 1.

---

## 6. FASE C4 — Estados de comprobante y modo contador

### 6.1 Ciclo de estados

`AsientoContableEntity.estado` ya existe. Formalizar:

```
BORRADOR → CONTABILIZADO → (REVERTIDO | ANULADO)
```

- Config por empresa (nueva fila en `accounting settings` o en `EmpresaEntity`):
  `modoContabilizacion = AUTOMATICO | REVISION`.
  - `AUTOMATICO` (default, pymes): igual que hoy, todo nace CONTABILIZADO.
  - `REVISION` (contador): los asientos automáticos nacen `BORRADOR`; endpoint
    `POST /api/contabilidad/asientos/{id}/contabilizar` (individual y masivo por rango).
- **Invariantes**: BORRADOR no suma en reportes oficiales (libro mayor, balances) pero sí
  aparece en "pendientes"; CONTABILIZADO es inmutable (solo reversa); nada se contabiliza
  en período CERRADO (validación ya existe, extenderla al cambio de estado).
- Reportes nuevos: **comprobantes pendientes** y **comprobantes descuadrados** (query
  sobre asientos donde Σdébito ≠ Σcrédito — no debería existir por el validator, es red de
  seguridad/auditoría).

### 6.2 Log de posting

Tabla `contabilidad_posting_log` (asiento_id, origen, estado, error, usuario, timestamp).
Hoy los fallos del listener van a `ErrorLogService`; el log de posting da la vista
positiva ("qué se contabilizó y desde dónde") que exige auditoría/revisoría.

### 6.3 Diferencias de cierre de caja

Al cerrar caja con diferencia: faltante → DB `GASTO_DIFERENCIA_CAJA` (nuevo concepto,
default 519530) / CR CAJA; sobrante → DB CAJA / CR `INGRESO_SOBRANTE_CAJA` (default 4295).
Se engancha al flujo de cierre de caja existente con el mismo patrón de evento.

---

## 7. FASE C5 — Dimensiones contables (proyecto / frente / canal)

El sistema ya tiene proyectos y frentes (V77+) usados en nómina. En vez de tabla genérica
de dimensiones (sobre-ingeniería a este tamaño), columnas explícitas:

```sql
-- V88__asiento_dimensiones.sql
ALTER TABLE asiento_detalle ADD COLUMN proyecto_id BIGINT REFERENCES proyecto(id);
ALTER TABLE asiento_detalle ADD COLUMN frente_id BIGINT REFERENCES proyecto_frente(id);
```

Propagación automática (el usuario no escoge nada):
- **Nómina por proyecto**: el gasto 5105xx se reparte por proyecto/frente según las horas
  del motor de asistencia (los datos ya existen en las novedades PROYECTO_FRENTE).
- **Venta**: centro de costo desde la caja/sucursal (regla ya soportada por `centro_costo_id`).
- **Compra/gasto**: centroCostoId de C1-pieza 4 + proyectoId opcional en el form.

Reportes: a `estadoResultados` y auxiliares se les agrega filtro opcional
`centroCostoId / proyectoId / frenteId` (parámetros nuevos en las queries de
`AsientoContableQueryRepository`, `WHERE (:proyectoId IS NULL OR d.proyecto_id = :proyectoId)`).
Esto habilita "rentabilidad por obra", que para el nicho construcción es diferenciador real.

---

## 8. FASE C6 — Conciliación bancaria

```sql
-- V89__conciliacion_bancaria.sql
CREATE TABLE extracto_bancario (
  id BIGSERIAL PRIMARY KEY,
  empresa_id INT NOT NULL, cuenta_bancaria_id BIGINT NOT NULL,
  periodo VARCHAR(7) NOT NULL,           -- '2026-07'
  saldo_inicial NUMERIC(18,2), saldo_final NUMERIC(18,2),
  estado VARCHAR(15) NOT NULL DEFAULT 'ABIERTO'  -- ABIERTO | CONCILIADO
);
CREATE TABLE extracto_linea (
  id BIGSERIAL PRIMARY KEY,
  extracto_id BIGINT NOT NULL REFERENCES extracto_bancario(id),
  fecha DATE, descripcion VARCHAR(255), valor NUMERIC(18,2),   -- +crédito banco / -débito
  asiento_detalle_id BIGINT,             -- match; NULL = sin conciliar
  estado VARCHAR(15) DEFAULT 'PENDIENTE' -- PENDIENTE | CONCILIADO | AJUSTE
);
```

- Importación CSV/Excel del extracto (parser por banco, empezar genérico: fecha,
  descripción, valor).
- Matching automático sugerido por (valor exacto, fecha ±3 días) contra movimientos de la
  cuenta contable del banco; confirmación manual línea a línea.
- Líneas del banco sin registro (comisiones, GMF 4x1000, intereses) generan asiento de
  ajuste desde la misma pantalla (concepto nuevo `GASTOS_BANCARIOS` default 530515 y
  `GMF` default 530595).
- Cierre del extracto exige: saldo libro conciliado = saldo extracto.

---

## 9. FASE C7 — Información exógena DIAN

Se hace al final porque consume de TODO lo anterior (terceros completos, impuestos
separados, retenciones con cuenta). Modelo mínimo viable:

```sql
-- V90__exogena.sql
CREATE TABLE exogena_formato (        -- 1001, 1005, 1006, 1007, 1008, 1009, 2276
  id BIGSERIAL PRIMARY KEY, codigo VARCHAR(10), nombre VARCHAR(120),
  anio_gravable INT, version INT, activo BOOLEAN DEFAULT TRUE
);
CREATE TABLE exogena_concepto (       -- concepto DIAN dentro del formato (5001, 5002...)
  id BIGSERIAL PRIMARY KEY, formato_id BIGINT REFERENCES exogena_formato(id),
  codigo VARCHAR(10), nombre VARCHAR(255)
);
CREATE TABLE exogena_mapeo_cuenta (   -- cuenta o rango PUC → concepto + tipo de valor
  id BIGSERIAL PRIMARY KEY, empresa_id INT NOT NULL,
  concepto_id BIGINT REFERENCES exogena_concepto(id),
  cuenta_desde VARCHAR(10), cuenta_hasta VARCHAR(10),
  tipo_valor VARCHAR(20)              -- MOVIMIENTO_DB | MOVIMIENTO_CR | SALDO_FINAL
);
CREATE TABLE exogena_lote (           -- una generación por año/formato, versionada
  id BIGSERIAL PRIMARY KEY, empresa_id INT, formato_id BIGINT,
  anio INT, estado VARCHAR(15) DEFAULT 'BORRADOR',  -- BORRADOR|APROBADO|BLOQUEADO
  generado_por BIGINT, generado_en TIMESTAMP
);
CREATE TABLE exogena_linea (
  id BIGSERIAL PRIMARY KEY, lote_id BIGINT REFERENCES exogena_lote(id),
  tercero_id BIGINT, concepto_id BIGINT, valor NUMERIC(18,2),
  cuantia_menor BOOLEAN DEFAULT FALSE
);
CREATE TABLE exogena_error (
  id BIGSERIAL PRIMARY KEY, lote_id BIGINT, tercero_id BIGINT,
  tipo VARCHAR(40), detalle VARCHAR(255)   -- SIN_NIT, SIN_DV, SIN_MUNICIPIO, SIN_MAPEO...
);
```

Flujo: seleccionar año/formatos → **validador previo** (terceros incompletos, cuentas sin
mapeo, períodos abiertos, comprobantes en borrador — reusa datos de C4) → generar lote
agrupando `asiento_detalle` por tercero×concepto → aplicar cuantías menores (tercero
222222222) → exportar Excel con columnas del prevalidador DIAN → aprobación bloquea el
lote. Los campos fiscales del tercero ya existen (V52); el validador es el que los exige.

---

## 10. Orden recomendado y esfuerzo

La estrategia vigente no cambia: **confiabilidad primero** (un asiento malo = contador
perdido). Orden:

| Orden | Fase | Por qué primero | Tamaño |
|---|---|---|---|
| 0 | FASE A pendiente: validación runtime del ciclo completo + UI de configuración contable + probar saldos iniciales | Prerrequisito comercial de todo | continuo |
| 1 | C1 tesorería (piezas 2, 4, 5) | Ya diseñada; destraba compras de gasto/activo y bancos reales | M |
| 2 | C4 estados + posting log + diferencia de caja | Barato, y es LA feature de confianza del contador (revisar antes de contabilizar) | S/M |
| 3 | C2 categorías contables de producto | Mayor salto funcional; requiere C1.4 | M/L |
| 4 | C3 impuestos parametrizables (incluye separar 240801/240802) | Corrige deuda real del IVA y habilita multi-tarifa | M |
| 5 | C5 dimensiones proyecto/frente | Diferenciador para construcción; datos ya existen | M |
| 6 | C6 conciliación bancaria | Cierra el ciclo de tesorería | M/L |
| 7 | C7 exógena | Consume todo lo anterior; véndela como cierre de año | L |

Cada fase repite el patrón ya probado: entidad + repo + service/Impl + controller +
evento si aplica + seed idempotente + concepto nuevo en `ConceptoContable` con default +
pantalla plana en aura-frontend + criterio de aceptación con asiento cuadrado.

## 11. Verificación de completitud contra el ciclo contable académico

Contraste del plan (C1–C7) contra tres marcos: el ciclo contable de los libros de
contabilidad financiera, los 5 ciclos transaccionales de Romney & Steinbart
(*Accounting Information Systems*) y el conjunto completo de EEFF de NIIF para pymes
(Sección 3, obligatoria en Colombia vía Decreto 2420/2015).

### 11.1 Ciclo contable clásico (libro de texto)

| Paso del ciclo | Estado en Aura |
|---|---|
| 1. Identificación de transacciones (documento fuente) | ✔ eventos AFTER_COMMIT |
| 2. Registro en libro diario | ✔ asientos automáticos + manuales |
| 3. Mayorización | ✔ libro mayor |
| 4. Balance de comprobación sin ajustar | ✔ |
| 5. **Asientos de ajuste (devengo)** | ⚠️ PARCIAL: solo depreciación. Faltan deterioro de cartera, diferidos, anticipos, causaciones → **C8** |
| 6. Balance de comprobación ajustado | ✔ (mismo reporte) |
| 7. Estados financieros | ⚠️ PARCIAL: hay ESF y ER; faltan cambios en patrimonio y EFE formal → **C10** |
| 8. Asientos de cierre (ingresos/gastos→resultado) | ✔ generarCierre a 3605 |
| 9. Balance post-cierre | ✔ |
| 10. **Cierre anual fiscal** (provisión renta, reservas, distribución) | ❌ NO existe → **C9** |

### 11.2 Ciclos transaccionales (Romney & Steinbart)

| Ciclo | Estado |
|---|---|
| Revenue (ventas + cobros) | ✔ completo con C1–C3; falta **anticipo de cliente** (2805) → C8 |
| Expenditure (compras + pagos) | ✔ completo con C1; falta **anticipo a proveedor** (1330) → C8 |
| HR / Payroll | ✔ el más completo del sistema (asistencia→nómina→prestaciones) |
| Financing (préstamos, capital, dividendos) | ✔ obligaciones; dividendos → C9; aportes de capital vía asiento manual (suficiente) |
| Production / conversion | ❌ FUERA DE ALCANCE declarado (solo producto compuesto con costo agregado; manufactura real con órdenes de producción no es el mercado actual) |
| GL & reporting | ✔ |

### 11.3 NIIF para pymes — conjunto completo de EEFF (Sección 3)

| Estado financiero | Estado |
|---|---|
| Estado de situación financiera | ✔ |
| Estado de resultados | ✔ |
| **Estado de cambios en el patrimonio** | ❌ → C10 |
| **Estado de flujos de efectivo** (método indirecto) | ❌ (el "flujo de caja" actual es proyección de tesorería, no el EFE formal) → C10 |
| Notas / políticas | Plantilla exportable → C10 (opcional) |

---

## 12. FASE C8 — Ajustes de devengo (cierra el paso 5 del ciclo)

Es la brecha más importante que el plan original NO cubría. Sin esto, la contabilidad
solo refleja caja y documentos, no devengo — y NIIF exige base de acumulación.

1. **Anticipos de clientes** (2805): pago recibido sin factura → DB Caja/Banco · CR 2805;
   al facturar se cruza DB 2805 · CR Clientes. Requiere en venta/recibo un flag "es anticipo"
   y pantalla de cruce (aplicar anticipo a factura). Conceptos nuevos `ANTICIPOS_CLIENTES`.
2. **Anticipos a proveedores** (1330): espejo en compras. Concepto `ANTICIPOS_PROVEEDORES`.
3. **Gastos pagados por anticipado** (1705 seguros/arriendos): el gasto marca
   `esDiferido + meses` → DB 1705 al pagar + job mensual de amortización
   DB gasto · CR 1705 (mismo patrón del job de depreciación existente).
4. **Causaciones recurrentes**: plantilla de asiento programado (arriendo, servicios
   recibidos sin factura) — tabla `causacion_programada` (líneas + periodicidad + día),
   job mensual que genera el asiento en BORRADOR para aprobación (usa C4).
5. **Deterioro de cartera** (NIIF pymes secc. 11): propuesta automática por edades
   (parametrizable: % por tramo de mora) → asiento DB 5199 · CR 1399 en BORRADOR
   para que el contador apruebe. Nunca automático directo.
6. **Deterioro/ajuste de inventario**: ya existe merma; agregar obsolescencia
   (DB 5199/gasto · CR 1499 provisión) desde el reconteo.

## 13. FASE C9 — Cierre anual fiscal colombiano (cierra el paso 10)

El `generarCierre` actual cancela resultado a 3605 (correcto para cierre mensual).
El cierre de EJERCICIO necesita además, en este orden:

1. **Provisión impuesto de renta** (antes del cierre): DB 5405 gasto impuesto ·
   CR 2404 impuesto por pagar. El valor lo digita el contador (la renta líquida fiscal
   ≠ utilidad contable; el sistema solo sugiere `utilidad × tarifa` como referencia).
2. **Cierre a 3605** (ya existe) — ahora neto de impuesto.
3. **Traslado de ejercicio**: al abrir el año siguiente, 3605 → 3705 resultados acumulados.
4. **Apropiaciones post-asamblea** (pantalla "distribución de utilidades"): reserva legal
   330505 (10% de utilidad líquida hasta 50% del capital, C.Cio arts. 350/452),
   dividendos decretados DB 3705 · CR 2360 dividendos por pagar, y su pago posterior.
   Todo como asientos generados desde un form guiado, no manuales.

Conceptos nuevos: `GASTO_IMPUESTO_RENTA(5405)`, `IMPUESTO_RENTA_POR_PAGAR(2404)`,
`RESERVA_LEGAL(330505)`, `DIVIDENDOS_POR_PAGAR(2360)`.

## 14. FASE C10 — Estados financieros NIIF completos

1. **Estado de cambios en el patrimonio**: query por cuentas clase 3 con movimientos del
   período agrupados por concepto (aportes, reservas, resultados) — columna inicial /
   aumentos / disminuciones / final. Sale directo de `asiento_detalle`.
2. **Estado de flujos de efectivo (método indirecto)**: utilidad ± partidas no monetarias
   (depreciación, deterioros, provisiones) ± variaciones de capital de trabajo
   (Δ cartera, Δ inventario, Δ proveedores desde saldos comparativos) + flujos de
   inversión (15xx) + financiación (21xx/23xx patrimonio). Todo computable de saldos
   por clase entre dos cortes.
3. **Notas**: plantilla Word/PDF con las cifras inyectadas (opcional, diferenciador).

### Fuera de alcance declarado (decisión, no olvido)

- **Impuesto diferido** (secc. 29): solo lo exigen empresas con revisoría exigente; se
  soporta vía asiento manual. Reevaluar cuando haya clientes grupo 1/2 grandes.
- **Multimoneda / diferencia en cambio** (secc. 30): fuera hasta que exista un cliente
  importador; tocaría medio motor.
- **Producción/manufactura** (órdenes, CIF): no es el mercado actual.
- **Consolidación de estados** (multi-empresa): existe multi-empresa operativa; la
  consolidación contable formal queda para el futuro.

### Orden ajustado con las nuevas fases

C8 (ajustes de devengo) entra **antes** de C6/C7 — es más importante para "contabilidad
completa" que conciliación o exógena: `…C2 → C3 → C8 → C5 → C9 → C6 → C10 → C7`.
C9 conviene tenerlo listo antes del primer cierre de año de un cliente real.

---

## 15. Invariantes del motor (no negociables, ya vigentes)

1. Σdébitos = Σcréditos en todo asiento (`AsientoBalanceValidator`).
2. Todo asiento automático lleva `tipoOrigen` + `origenId`; todo documento anulable
   reversa por contraasiento (nunca UPDATE del asiento).
3. Idempotencia: regenerar desde el mismo origen no duplica.
4. Período cerrado = solo lectura.
5. El fallo del posting NUNCA tumba la operación de negocio (listener AFTER_COMMIT +
   ErrorLog) — la venta sale aunque la contabilidad falle, y queda rastro para reproceso.
6. El usuario operativo jamás ve cuentas contables; el contador parametriza una vez.
7. Centros de costo / dimensiones nunca se simulan con cuentas del PUC.
