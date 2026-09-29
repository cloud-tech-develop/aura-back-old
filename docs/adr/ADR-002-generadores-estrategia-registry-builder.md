# ADR-002 — Generadores de asiento como estrategias + registry + builder

**Estado:** aceptada (2026-07-09) · **Detalle:** `docs/ARQUITECTURA_CONTABILIDAD.md` §4

## Contexto
Los 16 métodos `generarDesdeXxx` repiten idempotencia, período, resolución de cuentas,
armado de líneas, validación y persistencia; solo difieren en QUÉ partidas generan.

## Decisión
- `GeneradorAsiento` (estrategia): una clase por origen, < 150 líneas, SOLO decide
  partidas. `GeneradorRegistry`: mapa tipoOrigen → generador poblado por Spring.
- `ContabilizarDocumentoUseCase` concentra el ciclo transversal una sola vez
  (idempotencia → generar → período → persistir → posting log), `REQUIRES_NEW`.
- `AsientoBuilder` fluido valida en `build()`: es IMPOSIBLE construir un asiento
  descuadrado, con negativos o con menos de dos partidas (`ReglasAsiento`,
  escala 2 HALF_UP centralizados).
- Los generadores leen PROYECCIONES (puertos `LectorXxx`), nunca entities JPA.

## Consecuencias
- Agregar un origen = una clase nueva + su lector + golden files; nadie reabre clases.
- `VentaGenerador` es el piloto (E1); cada etapa migra los generadores que toque.
- Montos cero/nulos se omiten en el builder (líneas condicionales sin ifs); los
  negativos revientan en `Partida`.
