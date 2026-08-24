-- ── Diagnóstico de fechas corridas por zona horaria ───────────────────────
--
-- SOLO LECTURA. No modifica nada: sirve para decidir qué corregir y desde
-- cuándo, antes de escribir un solo UPDATE.
--
-- Había dos fuentes de desfase, y dejan huellas distintas:
--
--   A) El FRONT mandaba las fechas con toISOString(), que pasa a UTC. Una
--      compra registrada a las 2:09 p.m. llegaba como 19:09. Afecta a las
--      fechas que el usuario elige en un formulario (compra.fecha, gasto.fecha,
--      fecha_emision de cuentas, etc.) y ocurría SIEMPRE, sin importar dónde
--      estuviera el servidor.
--
--   B) La JVM del servidor corría en UTC, así que LocalDateTime.now() devolvía
--      cinco horas de más. Afecta a las marcas que pone el backend solo
--      (created_at, fecha_pago, movimiento_caja.fecha...).
--
-- La huella de (A) es la más fácil de reconocer: en un mismo registro, la fecha
-- que vino del formulario está ~5h por delante de la que puso el backend.

-- ── 1. Compras: fecha del formulario vs marca del backend ─────────────────
-- Un desfase cercano a 5h en registros creados el mismo día delata el toISOString.
SELECT
    'compra' AS tabla,
    date_trunc('month', created_at) AS mes,
    count(*) AS registros,
    count(*) FILTER (
        WHERE fecha - created_at BETWEEN INTERVAL '4 hours 30 minutes'
                                     AND INTERVAL '5 hours 30 minutes'
    ) AS sospechosos_5h,
    round(avg(EXTRACT(EPOCH FROM (fecha - created_at)) / 3600)::numeric, 2) AS desfase_horas_prom
FROM compra
WHERE created_at IS NOT NULL AND fecha IS NOT NULL
GROUP BY 1, 2
ORDER BY 2 DESC;

-- ── 2. Lo mismo, registro por registro, para los últimos 60 días ──────────
SELECT
    id,
    numero_compra,
    fecha            AS fecha_del_formulario,
    created_at       AS marca_del_backend,
    round(EXTRACT(EPOCH FROM (fecha - created_at)) / 3600::numeric, 2) AS desfase_horas
FROM compra
WHERE created_at IS NOT NULL
  AND created_at > now() - INTERVAL '60 days'
ORDER BY id DESC;

-- ── 3. Marcas del backend en horas imposibles ─────────────────────────────
-- Si la JVM estuvo en UTC, las marcas de la tarde-noche colombiana quedaron
-- entre las 00:00 y las 05:00 del día siguiente. Un negocio que no abre de
-- madrugada no debería tener actividad en esa franja: lo que aparezca ahí
-- probablemente ocurrió cinco horas antes.
SELECT
    tabla,
    count(*) FILTER (WHERE hora BETWEEN 0 AND 4)  AS madrugada_0_5,
    count(*) FILTER (WHERE hora BETWEEN 5 AND 22) AS horario_normal,
    count(*) AS total
FROM (
    SELECT 'compra.created_at'        AS tabla, EXTRACT(HOUR FROM created_at)   AS hora FROM compra        WHERE created_at IS NOT NULL
    UNION ALL
    SELECT 'venta.fecha_emision',                EXTRACT(HOUR FROM fecha_emision)       FROM venta         WHERE fecha_emision IS NOT NULL
    UNION ALL
    SELECT 'movimiento_caja.created_at',         EXTRACT(HOUR FROM created_at)          FROM movimiento_caja WHERE created_at IS NOT NULL
) t
GROUP BY tabla
ORDER BY tabla;

-- ── 4. Movimientos de caja cuya fecha no coincide con su turno ────────────
-- El caso que más duele: si la fecha quedó corrida al día siguiente, el
-- movimiento no cuadra contra el arqueo del turno en que realmente ocurrió.
SELECT
    m.id,
    m.tipo,
    m.monto,
    m.fecha        AS fecha_movimiento,
    m.created_at   AS marca_backend,
    t.id           AS turno_id,
    t.fecha_apertura,
    t.fecha_cierre
FROM movimiento_caja m
JOIN turno_caja t ON t.id = m.turno_caja_id
WHERE m.fecha IS NOT NULL
  AND t.fecha_apertura IS NOT NULL
  AND m.fecha <> t.fecha_apertura::date
ORDER BY m.id DESC
LIMIT 200;

-- ── 5. CRÍTICO: ¿en qué zona corría la JVM cuando se guardaron las ventas? ─
--
-- `venta.fecha_emision` es la única columna CON zona de toda la base. El driver
-- interpreta el LocalDateTime que recibe usando la zona de la JVM, así que el
-- instante guardado depende de dónde corría el servidor. Para convertirla a
-- `timestamp` (ver alinear_fecha_emision_local.sql) hay que saber cuál de las
-- dos lecturas es la buena. Esta prueba es la fácil de malinterpretar: mira el
-- patrón horario y asume que la lectura correcta produce un horario comercial
-- creíble. Úsala solo como confirmación de la consulta 6, que es la decisiva.
--
-- Mira qué columna se parece al horario real del negocio:
--
--   · Si el pico de ventas cae en `hora_si_convierto_bogota` → la JVM ya
--     estaba en America/Bogota. Usa AT TIME ZONE 'America/Bogota'.
--   · Si el pico cae en `hora_si_convierto_utc` → la JVM estaba en UTC.
--     Usa AT TIME ZONE 'UTC'.
--
-- Aplicar la conversión equivocada corre la hora de TODAS las ventas
-- históricas. No adivines.
SELECT
    EXTRACT(HOUR FROM fecha_emision AT TIME ZONE 'America/Bogota') AS hora_si_convierto_bogota,
    EXTRACT(HOUR FROM fecha_emision AT TIME ZONE 'UTC')            AS hora_si_convierto_utc,
    count(*)                                                       AS ventas
FROM venta
WHERE fecha_emision IS NOT NULL
GROUP BY 1, 2
ORDER BY 1;

-- ── 6. LA PRUEBA DECISIVA: ¿en qué zona corre la JVM del servidor? ────────
--
-- La consulta 5 mira el patrón horario y puede engañar. Esta no.
--
-- `factura.created_at` es `timestamp` SIN zona y lo escribe LocalDateTime.now():
-- guarda literalmente la hora de la JVM, sin conversión de ninguna clase. La
-- factura se crea en el mismo momento que la venta, así que comparar las dos
-- revela la zona sin ambigüedad.
--
--   · dif_bogota_h ≈ 0    → la JVM corre en America/Bogota.
--                           Convierte con AT TIME ZONE 'America/Bogota'.
--
--   · dif_utc_h ≈ -5      → la JVM corre en UTC (dif_bogota_h dará ≈ -10).
--                           Convierte con AT TIME ZONE 'UTC'.
--
-- Si las filas no son consistentes entre sí, el servidor cambió de zona en
-- algún momento y la corrección tendrá que ir por tramos de fecha.
SELECT
    v.id,
    f.created_at                                  AS marca_directa_de_la_jvm,
    v.fecha_emision AT TIME ZONE 'America/Bogota' AS como_bogota,
    v.fecha_emision AT TIME ZONE 'UTC'            AS como_utc,
    round((EXTRACT(EPOCH FROM ((v.fecha_emision AT TIME ZONE 'America/Bogota') - f.created_at)) / 3600)::numeric, 2) AS dif_bogota_h,
    round((EXTRACT(EPOCH FROM ((v.fecha_emision AT TIME ZONE 'UTC') - f.created_at)) / 3600)::numeric, 2)            AS dif_utc_h
FROM venta v
JOIN factura f ON f.venta_id = v.id
WHERE v.fecha_emision IS NOT NULL
  AND f.created_at IS NOT NULL
ORDER BY v.id DESC
LIMIT 30;
