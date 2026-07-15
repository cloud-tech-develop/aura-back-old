# ADR-006 — Parametrizar destinos, nunca lógica; guardarraíles por clase PUC

**Estado:** aceptada (2026-07-09) · **Decisión de producto**

## Contexto
El modelo de producto es "opinado por defecto, parametrizable acotado": el sistema
contabiliza bien sin configurar nada; el contador remapea CUENTAS destino, nunca la
estructura del asiento. Sin guardarraíles, un mapeo erróneo (INGRESOS_VENTAS → clase 1)
produce estados financieros basura y destruye la confianza del contador.

## Decisión
- Cada `ConceptoContable` declara sus prefijos PUC permitidos
  (`INGRESOS_VENTAS→4`, `CLIENTES→13`, `IVA→24`, `NOMINA_*→51/52 · 23/25`, …).
- `ConfiguracionContableService.actualizar` rechaza cuentas inexistentes, inactivas,
  no auxiliares (de movimiento) o fuera de la clase permitida, con mensaje accionable.
- La cuenta contable de una cuenta bancaria solo admite 11xx (ya vigente).
- Todo cambio de mapeo queda en `contabilidad_config_log` (V85): quién, qué, cuándo,
  de qué cuenta a cuál. Solo INSERT.
- Prohibido hardcodear códigos PUC fuera del enum y los seeds (regla de PR).

## Consecuencias
- Concepto nuevo = entrada en el enum CON prefijos + seed idempotente.
- La UI de configuración filtra el dropdown de cuentas por la clase permitida.
- Centros de costo / dimensiones nunca se simulan con cuentas del PUC.
