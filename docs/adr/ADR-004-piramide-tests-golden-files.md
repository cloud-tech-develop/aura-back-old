# ADR-004 — Pirámide de tests con golden files de asientos

**Estado:** aceptada (2026-07-09) · **Detalle:** `docs/ARQUITECTURA_CONTABILIDAD.md` §7

## Contexto
El módulo contable nació sin tests; el cuadre se validaba solo en runtime. La
separación domain/application existe PARA poder probar generadores sin base de datos.

## Decisión
1. Unit de dominio (sin Spring): `AsientoBuilder`/`ReglasAsiento` cubren descuadre,
   negativos, redondeo, estados.
2. Unit de generadores (puertos mockeados): mínimo contado/crédito/mixto/borde por
   generador. Assert central SIEMPRE: Σ débitos = Σ créditos + cuentas esperadas.
3. Golden files en `src/test/resources/asientos-esperados/*.json`: la matriz de
   validación E0 codificada como regresión permanente (`GoldenAsientos`).
4. Integración por flujo (Testcontainers PostgreSQL): smoke, una por etapa nueva.
5. Regla de PR: código contable nuevo sin test de cuadre no se mergea. Los
   generadores legacy adquieren tests AL MIGRARSE, no antes.

## Consecuencias
- `VentaGeneradorTest` + 3 golden files son el patrón a copiar en cada etapa.
- En tests unitarios los resolvers devuelven el código PUC default como id de cuenta
  (convención documentada en el README de asientos-esperados).
