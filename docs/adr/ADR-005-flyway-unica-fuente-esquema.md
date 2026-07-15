# ADR-005 — Flyway como única fuente de esquema; ddl-auto=validate

**Estado:** aceptada (2026-07-09), pendiente de ejecutar el switch · **Detalle:** `docs/ARQUITECTURA_CONTABILIDAD.md` §8

## Contexto
`spring.jpa.hibernate.ddl-auto=update` corre en paralelo con Flyway (V1–V85): el
esquema real depende del orden de arranque. Es la deuda más peligrosa del proyecto.

## Decisión
1. Apagar `ddl-auto=update` → `validate`. Paso previo: verificar contra una BD real
   (o Testcontainers + Flyway) que las migraciones cubren todas las entities, y
   generar la migración de sincronización con el diff que falte.
2. Todo cambio de esquema = migración Flyway versionada (V85+), ADITIVA (no DROP de
   columnas en uso; deprecación en dos releases).
3. Seeds idempotentes (`ON CONFLICT DO NOTHING` o check en servicio).
4. Los asientos guardan `cuenta_id` resuelto al momento del posting: los reportes
   jamás re-resuelven configuración — la historia es inmutable.
5. Índices en tablas nuevas: `(empresa_id, …)` siempre primero.

## Consecuencias
- Hasta ejecutar el switch, ninguna entity nueva puede confiar en que Hibernate le
  cree la tabla: TODA tabla nueva nace con su migración (V85 ya cumple).
- El switch requiere una ventana de verificación contra la BD de cada ambiente.
