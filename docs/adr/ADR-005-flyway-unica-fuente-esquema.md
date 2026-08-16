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

## Enmienda (2026-08-13) — el runner pasa a Laravel

`spring.flyway.enabled=false`. El esquema se aplica desde el proyecto de
migraciones en Laravel (`aura-pos-migracion-old`), no desde este backend.

**Por qué:** los dos proyectos apuntan a la misma BD y ambos migraban, así que
cada cambio se aplicaba dos veces. Además, con Flyway encendido cualquier
retoque a un `.sql` ya aplicado tumba el arranque con *checksum mismatch*
aunque el esquema esté correcto — que es exactamente lo que pasó con V100 y
V140.

**Lo que NO cambia:** `ddl-auto=validate` sigue puesto. Hibernate no crea nada,
y si a la BD le falta algo que una entity declara, la app no arranca. La
garantía del punto 1 se mantiene; solo cambió quién ejecuta las migraciones.

**Lo que cambia en la práctica:**
- Los `.sql` de `db/migration` quedan como espejo y documentación del esquema.
  Se siguen escribiendo para que el historial del backend sea legible, pero
  **no se ejecutan**: lo que aplica el cambio es su migración en Laravel.
- Toda tabla o columna nueva necesita su par: el `.sql` aquí y la migración
  PHP allá, con el mismo número.
- Si se reactiva Flyway habrá que hacer `flyway repair`: su historial quedará
  desalineado respecto a lo aplicado desde Laravel.
