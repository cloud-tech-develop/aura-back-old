-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Diagnóstico: el reporte de facturas/notas electrónicas sale vacío   ║
-- ║                                                                      ║
-- ║  SOLO LECTURA. No modifica nada.                                     ║
-- ║                                                                      ║
-- ║  El reporte filtra por tres cosas y basta que falle una para que no  ║
-- ║  devuelva nada:                                                      ║
-- ║                                                                      ║
-- ║    factura:  empresa_id = X                                          ║
-- ║              AND deleted_at IS NULL                                  ║
-- ║              AND fecha_hora_emision >= desde                         ║
-- ║              AND fecha_hora_emision <  hasta + 1 día                 ║
-- ║                                                                      ║
-- ║    nota_electronica: lo mismo pero por created_at                    ║
-- ║                                                                      ║
-- ║  El módulo de Facturación Electrónica NO filtra por fecha, por eso   ║
-- ║  ahí sí se ven. La diferencia está en la fecha o en el deleted_at.   ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- Reemplace por la empresa que está consultando.
\set empresa_id 1

-- ── (1) ¿Hay filas, y en qué estado están? ────────────────────────────
--
-- Si `con_fecha_emision` es 0 mientras `total` no lo es, ese es el problema:
-- una fecha nula NUNCA cumple `>= desde`, así que el reporte devuelve cero
-- aunque las facturas existan y se vean en el módulo.
SELECT 'factura' AS tabla,
       COUNT(*)                                                  AS total,
       COUNT(*) FILTER (WHERE empresa_id = :empresa_id)           AS de_la_empresa,
       COUNT(*) FILTER (WHERE empresa_id IS NULL)                 AS sin_empresa,
       COUNT(*) FILTER (WHERE deleted_at IS NOT NULL)             AS borradas,
       COUNT(*) FILTER (WHERE fecha_hora_emision IS NULL)         AS sin_fecha_emision,
       MIN(fecha_hora_emision)                                    AS emision_mas_vieja,
       MAX(fecha_hora_emision)                                    AS emision_mas_nueva
  FROM factura

UNION ALL

SELECT 'nota_electronica',
       COUNT(*),
       COUNT(*) FILTER (WHERE empresa_id = :empresa_id),
       COUNT(*) FILTER (WHERE empresa_id IS NULL),
       0,
       COUNT(*) FILTER (WHERE created_at IS NULL),
       MIN(created_at),
       MAX(created_at)
  FROM nota_electronica;

-- ── (2) ¿La hora está corrida 5 horas? ────────────────────────────────
--
-- La JVM de producción corre en UTC y guarda `LocalDateTime.now()` tal cual,
-- así que lo escrito por el backend queda 5 horas adelante de la hora de
-- Colombia. Eso no vacía el reporte, pero sí saca las facturas emitidas
-- después de las 7 p.m. del último día del rango.
--
-- Si `hora_promedio` da alrededor de 5 horas más de lo que uno esperaría de
-- un día de trabajo (p. ej. 19 en vez de 14), el desfase está presente.
SELECT DATE_TRUNC('day', fecha_hora_emision)::date            AS dia,
       COUNT(*)                                                AS facturas,
       MIN(fecha_hora_emision)::time                           AS primera,
       MAX(fecha_hora_emision)::time                           AS ultima,
       ROUND(AVG(EXTRACT(HOUR FROM fecha_hora_emision)), 1)    AS hora_promedio
  FROM factura
 WHERE empresa_id = :empresa_id
   AND deleted_at IS NULL
   AND fecha_hora_emision IS NOT NULL
 GROUP BY 1
 ORDER BY 1 DESC
 LIMIT 15;

-- ── (3) La consulta EXACTA del reporte, para reproducir el vacío ──────
--
-- Ajuste las dos fechas al rango que se pidió en pantalla. Si esto devuelve 0
-- y el punto (1) muestra facturas de esos días, el filtro de fecha es el
-- culpable; si devuelve filas, el problema no está en la consulta.
SELECT COUNT(*) AS filas_que_vería_el_reporte
  FROM factura f
 WHERE f.empresa_id = :empresa_id
   AND f.deleted_at IS NULL
   AND f.fecha_hora_emision >= DATE '2026-09-01'
   AND f.fecha_hora_emision <  DATE '2026-09-07';   -- hasta + 1 día

-- ── (4) Las últimas 10 facturas, sin ningún filtro de fecha ───────────
--
-- Para ver con qué valores reales están guardadas y comparar contra lo que
-- el usuario ve en el módulo.
SELECT id, empresa_id, prefijo, consecutivo,
       fecha_hora_emision, created_at, deleted_at, estado_dian, valor
  FROM factura
 WHERE empresa_id = :empresa_id
 ORDER BY id DESC
 LIMIT 10;
