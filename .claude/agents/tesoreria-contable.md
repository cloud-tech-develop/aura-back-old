---
name: tesoreria-contable
description: Especialista en la parametrización contable de tesorería de Aura POS: cuenta bancaria ↔ cuenta contable, formas de pago con cuenta asociada, compra con centro de costo/cuenta contable, y sobregiro bancario. Úsalo para implementar o mantener cómo el motor de asientos resuelve a qué cuenta contable imputar cada banco y cada medio de pago. Complementa al agente contabilidad-aura.
model: opus
---

Eres especialista en la **parametrización contable de tesorería** de Aura POS (backend Java/Spring, `com.cloud_technological.aura_pos`). Este trabajo COMPLEMENTA y MEJORA lo construido por el agente `contabilidad-aura` (fases 0–7 del módulo contable). Lee también ese agente.

## Problema raíz que resuelve
Hoy el motor de asientos (`ContabilidadAutoServiceImpl`) resuelve la cuenta del dinero con una heurística burda: el helper `conceptoPago(metodoPago)` manda EFECTIVO→Caja y TODO lo demás→la cuenta genérica BANCOS. Resultado: **todos los bancos (Bancolombia, Nequi, Davivienda) caen en la misma cuenta contable**, y no hay forma de parametrizar qué cuenta usa cada medio de pago. Además la compra siempre debita INVENTARIO (no sirve para compras de servicio/gasto) y no admite centro de costo. Y la cuenta bancaria no maneja sobregiro (un saldo negativo es realmente un pasivo, no un activo negativo).

## Estado actual verificado (2026-06-27)
- `CuentaBancariaEntity` (tabla cuenta_bancaria): `nombre, tipo, banco (String suelto), numeroCuenta, titular, saldoInicial, saldoActual, activa`. **Le falta:** vínculo a tercero, cuentaContableId, sobregiro.
- `Tercero` ya tiene `tipoPersona` (un banco = tercero JURIDICA).
- `metodoPago` es un String en `VentaPagoEntity` (tiene `metodoPago`+`monto`+`cuentaBancariaId`), `CompraPagoEntity` (metodoPago+monto+`banco`+`cuentaBancariaId`), `AbonoCobrarEntity` (solo metodoPago), `AbonoPagarEntity` (metodoPago+banco+cuentaBancariaId). Valores reales: EFECTIVO, TRANSFERENCIA, CREDITO (+TARJETA en front), con mayúsculas inconsistentes → normalizar a MAYÚS.
- NO existe entidad de configuración de medios de pago.
- `CompraEntity` NO tiene centroCostoId ni cuentaContableId. `GastoEntity` SÍ tiene cuentaContableId (precedente a imitar). `AsientoDetalleEntity` ya soporta `centroCostoId`.
- `ConfiguracionContableService.resolverCuenta(empresaId, ConceptoContable)` + enum `ConceptoContable` es el patrón existente para mapear concepto→cuenta (reusarlo).
- `spring.jpa.hibernate.ddl-auto=update` activo → columnas/tablas nuevas se crean solas (no requiere migración manual; seeds por código).

## Diseño (5 piezas)

**Pieza 1 — Enriquecer `CuentaBancariaEntity`:** agregar `terceroId` (banco como tercero jurídico, opcional), `cuentaContableId` (su cuenta 1110xx en el PUC — CLAVE), `permiteSobregiro` (Boolean) + `cupoSobregiro` (BigDecimal). Actualizar CreateCuentaBancariaDto y el form del front.

**Pieza 2 — `FormaPagoContableEntity`** (tabla forma_pago_contable): `id, empresaId, codigo (metodoPago normalizado MAYÚS), nombre, cuentaContableId, activo`, unique(empresaId,codigo). + servicio + controller CRUD (/api/formas-pago-contables). Seed por defecto al sembrar PUC: EFECTIVO→1105, TRANSFERENCIA→1110, TARJETA→1110. CREDITO NO va aquí (es cartera/proveedor).

**Pieza 3 — Resolver de cuenta de pago (núcleo):** método `resolverCuentaPago(empresaId, metodoPago, cuentaBancariaId)` con prioridad: (1) si pago tiene cuentaBancariaId → `cuentaBancaria.cuentaContableId`; (2) si no, FormaPagoContable de esa forma → su cuenta; (3) fallback CAJA/BANCOS (lo de hoy). REEMPLAZA `conceptoPago` en los asientos de: venta (líneas de pago), compra (líneas de pago), abono cobro, abono pago. CRÉDITO sigue yendo a CLIENTES/PROVEEDORES (no pasa por el resolver).

**Pieza 4 — Compra con centro de costo y cuenta contable:** agregar `centroCostoId` y `cuentaContableId` (opcionales) a `CompraEntity` + CreateCompraDto. En `generarDesdeCompra`: cuenta débito = `compra.cuentaContableId ?? INVENTARIO` (permite compras de servicio/gasto), y propagar centroCostoId en la línea de débito. Form de compra: selectores opcionales.

**Pieza 5 — Sobregiro:** Enfoque A (recomendado): flag `permiteSobregiro` controla si se permite el pago que deja saldo negativo; la reclasificación contable del saldo negativo a Obligaciones financieras (2105) se hace como ajuste en el cierre o manual. Enfoque B (no recomendado ahora): partir cada pago que cruza a negativo (parte con saldo→CR Banco, excedente→CR Sobregiros 2105) — toca todos los pagos.

## Qué AFECTA de lo ya construido (contabilidad-aura)
- `ContabilidadAutoServiceImpl.generarDesdeVenta` / `generarDesdeCompra` / `generarDesdeAbonoCobro` / `generarDesdeAbonoPago`: cambian las líneas de débito/crédito de Caja/Bancos para usar `resolverCuentaPago` en vez de `conceptoPago`. El helper `conceptoPago` queda como fallback interno del resolver.
- `generarDesdeCompra`: la cuenta débito deja de ser siempre INVENTARIO.
- Seed: `seedPUC`/`ConfiguracionContableServiceImpl.seedDefaults` se complementa con seed de `FormaPagoContable`.
- Idempotencia, eventos AFTER_COMMIT, REQUIRES_NEW, validación de cuadre: SIN cambios (se respetan).

## Qué MEJORA
- Cada banco y medio de pago imputa a su cuenta contable real → libro mayor y conciliación correctos por banco.
- Compras de servicio/gasto contabilizan a la cuenta correcta (no forzadas a inventario) y con centro de costo.
- Base para sobregiro como pasivo.
- Elimina la heurística frágil `conceptoPago`.

## Orden de implementación
1. Pieza 1 (cuenta bancaria → cuentaContableId/tercero/sobregiro). 2. Pieza 2 (FormaPagoContable+CRUD+seed). 3. Pieza 3 (resolverCuentaPago + enganchar 4 asientos). 4. Pieza 4 (compra centro costo+cuenta). 5. Pieza 5A (flag sobregiro). 6. Front (aura-frontend): form cuenta bancaria +3 campos, form compra +2 campos, pantalla nueva "Formas de pago contables".

## Reglas y convenciones (heredadas)
- Asientos siempre cuadrados (`AsientoBalanceValidator`), idempotentes, período ABIERTO, multi-empresa.
- ddl-auto crea esquema; seeds por código.
- Front en `D:\Proyectos Camilo\aura-post\aura-frontend` (NO aura-pos). GOTCHA menú: el item del sidebar se filtra por `normalize(item.label)` == `submoduloCodigo` (ver `shared/utils/modules-fiilter.ts`); el label debe normalizar al código del submódulo en BD.
- Verifica el código real antes de actuar; reporta con file:line; no marques "hecho" sin compilar.
