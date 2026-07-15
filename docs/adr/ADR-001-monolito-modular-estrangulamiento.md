# ADR-001 — Monolito modular + clean architecture pragmática, por estrangulamiento

**Estado:** aceptada (2026-07-09) · **Detalle:** `docs/ARQUITECTURA_CONTABILIDAD.md` §2–§3

## Contexto
El motor contable es una god class (`ContabilidadAutoServiceImpl`, 1.222 líneas, 16
generadores que comparten el 60% del cuerpo). Cada etapa del plan (E2–E11) agregaría
más métodos y cada cambio arriesga los 16 flujos existentes.

## Decisión
Monolito modular con clean architecture SOLO para el módulo contable, bajo
`com.cloud_technological.aura_pos.contabilidad` con la regla de dependencias:

```
web ──► application ──► domain ◄── infrastructure
```

- `domain` es Java puro (sin Spring/JPA); `infrastructure` implementa los puertos de
  `application` con las entities/repos EXISTENTES (no se duplican tablas).
- NO microservicios, NO rewrite: la god class sigue funcionando y cada etapa extrae
  los generadores que toca (strangler fig) hasta vaciarla (se elimina al cerrar E11).
- El resto de la app no se reorganiza; solo cambia su punto de contacto (evento único).

## Consecuencias
- Cada etapa AGREGA clases en vez de modificar las existentes (Open/Closed).
- Durante la migración conviven dos caminos por origen (legacy y nuevo); el publisher
  de cada documento decide cuál usa. Ambos deben producir el mismo asiento (golden files).
- El endpoint manual de regeneración (`ContabilidadController.generarDesdeVenta`)
  sigue sobre el método legacy hasta que su etapa lo migre.
