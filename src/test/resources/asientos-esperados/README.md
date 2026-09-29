# Golden files de asientos esperados

Matriz caso → asiento esperado de la validación E0, codificada como regresión
permanente (ADR-004). Cada JSON describe el asiento que DEBE producir el
generador para un escenario de negocio.

Convenciones:
- `cuenta`: en los tests unitarios los resolvers se mockean devolviendo el
  código PUC default del concepto como id (ej. `4135` = INGRESOS_VENTAS).
  Para cuentas bancarias parametrizadas se usa `111005`.
- `debito`/`credito`: montos con hasta 2 decimales; se comparan con
  `compareTo`, no con `equals`.
- `tercero`: id del tercero de la línea o `null`.
- El orden de las partidas es el orden en que el generador las emite.

Todo generador nuevo agrega aquí sus escenarios (mínimo: contado, crédito,
mixto y un borde) — regla de PR del módulo contable.
