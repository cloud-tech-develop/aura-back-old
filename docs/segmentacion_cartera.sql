-- ═══════════════════════════════════════════════════════════════════════════
-- Segmentación de cartera: ¿cuántos clientes hacen proyectos vs. retail puro?
--
-- Resuelve el pendiente que condiciona la prioridad de la Fase 4 del
-- PLAN_MIGRACION_NOMINA.md (costeo por proyecto).
--
-- Ejecutar contra PRODUCCIÓN, en réplica de lectura si existe.
-- Solo SELECT: no modifica nada.
--
-- Ventana de actividad: 90 días. Ajustar en la CTE `params` si el ciclo de
-- negocio es más largo (una obra puede no tener movimiento en 3 meses y
-- seguir viva).
-- ═══════════════════════════════════════════════════════════════════════════

WITH params AS (
    SELECT (CURRENT_DATE - INTERVAL '90 days')::date AS desde
),

-- ── Uso de proyectos por empresa ────────────────────────────────────────────
proyectos AS (
    SELECT
        p.empresa_id,
        COUNT(*)                                              AS proyectos_total,
        COUNT(*) FILTER (WHERE p.estado = 'ACTIVO')           AS proyectos_activos,
        MAX(p.created_at)                                     AS ultimo_proyecto
    FROM proyecto p
    WHERE p.deleted_at IS NULL
    GROUP BY p.empresa_id
),

frentes AS (
    SELECT
        f.empresa_id,
        COUNT(*) FILTER (WHERE f.estado = 'ACTIVO')           AS frentes_activos
    FROM proyecto_frente f
    WHERE f.deleted_at IS NULL
    GROUP BY f.empresa_id
),

-- Asistencia por frente: la señal MÁS fuerte de uso real.
-- Crear un proyecto es barato; capturar asistencia todos los días no.
asistencia AS (
    SELECT
        a.empresa_id,
        COUNT(*)                                              AS dias_asistencia_90d,
        COUNT(DISTINCT a.frente_id)                           AS frentes_con_asistencia,
        MAX(a.fecha)                                          AS ultima_asistencia
    FROM asistencia_frente a, params
    WHERE a.deleted_at IS NULL
      AND a.fecha >= params.desde
    GROUP BY a.empresa_id
),

-- ── Uso de nómina ───────────────────────────────────────────────────────────
empleados_c AS (
    SELECT
        e.empresa_id,
        COUNT(*) FILTER (WHERE e.activo)                      AS empleados_activos
    FROM empleados e
    GROUP BY e.empresa_id
),

nominas AS (
    SELECT
        n.empresa_id,
        COUNT(*)                                              AS nominas_90d,
        MAX(n.created_at)                                     AS ultima_nomina
    FROM nomina n, params
    WHERE n.created_at >= params.desde
    GROUP BY n.empresa_id
),

-- ── Uso de retail / POS ─────────────────────────────────────────────────────
ventas AS (
    SELECT
        v.empresa_id,
        COUNT(*)                                              AS ventas_90d,
        COALESCE(SUM(v.total_pagar), 0)                       AS monto_ventas_90d
    FROM venta v, params
    WHERE v.fecha_emision >= params.desde
    GROUP BY v.empresa_id
),

-- ── Consolidado + clasificación ─────────────────────────────────────────────
base AS (
    SELECT
        e.id                                                  AS empresa_id,
        e.razon_social,
        e.activa,
        s.estado                                              AS suscripcion_estado,
        s.tipo_plan,
        COALESCE(s.valor, 0)                                  AS valor_plan,
        COALESCE(pr.proyectos_activos, 0)                     AS proyectos_activos,
        COALESCE(fr.frentes_activos, 0)                       AS frentes_activos,
        COALESCE(asi.dias_asistencia_90d, 0)                  AS dias_asistencia_90d,
        COALESCE(em.empleados_activos, 0)                     AS empleados_activos,
        COALESCE(no.nominas_90d, 0)                           AS nominas_90d,
        COALESCE(ve.ventas_90d, 0)                            AS ventas_90d,
        COALESCE(ve.monto_ventas_90d, 0)                      AS monto_ventas_90d
    FROM empresa e
    LEFT JOIN empresa_suscripcion s ON s.empresa_id = e.id AND s.deleted_at IS NULL
    LEFT JOIN proyectos   pr  ON pr.empresa_id  = e.id
    LEFT JOIN frentes     fr  ON fr.empresa_id  = e.id
    LEFT JOIN asistencia  asi ON asi.empresa_id = e.id
    LEFT JOIN empleados_c em  ON em.empresa_id  = e.id
    LEFT JOIN nominas     no  ON no.empresa_id  = e.id
    LEFT JOIN ventas      ve  ON ve.empresa_id  = e.id
    WHERE e.activa = TRUE
),

clasificado AS (
    SELECT
        b.*,
        CASE
            -- Usa proyectos Y captura asistencia por frente: el caso que la Fase 4 sirve de lleno
            WHEN b.dias_asistencia_90d > 0 AND b.proyectos_activos > 0
                THEN 'PROYECTOS_CON_ASISTENCIA'
            -- Tiene proyectos activos pero no captura asistencia: la Fase 4 le sirve vía
            -- contrato_centro_costo (cascada paso 2), no vía horas reales
            WHEN b.proyectos_activos > 0
                THEN 'PROYECTOS_SIN_ASISTENCIA'
            -- Creó proyectos alguna vez pero ninguno activo: evaluar si abandonó
            WHEN COALESCE(b.proyectos_activos, 0) = 0 AND b.frentes_activos > 0
                THEN 'PROYECTOS_INACTIVO'
            -- Liquida nómina pero sin proyectos: la Fase 4 no le aporta
            WHEN b.nominas_90d > 0
                THEN 'NOMINA_SIN_PROYECTOS'
            -- Solo vende: retail puro
            WHEN b.ventas_90d > 0
                THEN 'RETAIL_PURO'
            ELSE 'SIN_ACTIVIDAD_90D'
        END AS segmento
    FROM base b
)

-- ═══ RESULTADO 1: resumen por segmento ═════════════════════════════════════
-- Ésta es la respuesta a la pregunta. Mirar la columna pct_ingreso, no pct_clientes.
SELECT
    segmento,
    COUNT(*)                                                  AS clientes,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 1)        AS pct_clientes,
    SUM(valor_plan)                                           AS ingreso_mensual,
    ROUND(100.0 * SUM(valor_plan)
          / NULLIF(SUM(SUM(valor_plan)) OVER (), 0), 1)       AS pct_ingreso,
    SUM(empleados_activos)                                    AS empleados,
    SUM(proyectos_activos)                                    AS proyectos,
    SUM(dias_asistencia_90d)                                  AS dias_asistencia
FROM clasificado
GROUP BY segmento
ORDER BY ingreso_mensual DESC NULLS LAST;


-- ═══ RESULTADO 2: detalle por cliente ══════════════════════════════════════
-- Correr aparte. Sirve para validar la clasificación a ojo: si un cliente que
-- sabes que es constructora sale como RETAIL_PURO, la consulta está mintiendo
-- (o el cliente no está usando el módulo, que también es un hallazgo).
/*
SELECT
    empresa_id,
    razon_social,
    segmento,
    suscripcion_estado,
    valor_plan,
    proyectos_activos,
    frentes_activos,
    dias_asistencia_90d,
    empleados_activos,
    nominas_90d,
    ventas_90d,
    monto_ventas_90d
FROM clasificado
ORDER BY valor_plan DESC NULLS LAST, dias_asistencia_90d DESC;
*/


-- ═══════════════════════════════════════════════════════════════════════════
-- CÓMO LEER EL RESULTADO
--
-- La pregunta NO es "¿cuántos clientes hacen proyectos?" sino "¿cuánto
-- ingreso hacen proyectos?". Cuatro constructoras pagando 3x pesan más que
-- dieciséis tiendas. Por eso el corte es `pct_ingreso`, no `pct_clientes`.
--
-- Criterio sugerido para la Fase 4:
--
--   PROYECTOS_CON_ASISTENCIA + PROYECTOS_SIN_ASISTENCIA (por ingreso):
--
--     > 40%  → Fase 4 va arriba, como propone el plan v5.
--     20-40% → Fase 4 después de la Fase 5 (nómina electrónica es obligatoria
--              para todos; el costeo solo para algunos).
--     < 20%  → Fase 4 baja. Reconsiderar si vale la inversión ahora.
--
-- Señal adicional: si PROYECTOS_CON_ASISTENCIA es alto, la distribución por
-- horas reales (4.a) es viable de una. Si domina PROYECTOS_SIN_ASISTENCIA,
-- la cascada cae a contrato_centro_costo y hay que priorizar 4.b sobre 4.a.
--
-- SIN_ACTIVIDAD_90D alto merece su propia conversación: son clientes que
-- pagan y no usan. Eso es riesgo de churn, no de roadmap.
--
-- ADVERTENCIA: `valor_plan` es el valor del plan de suscripción, no ingreso
-- facturado real. Si hay descuentos, cortesías o planes anuales prorrateados,
-- el pct_ingreso está sesgado. Contrastar con la fuente de facturación real
-- antes de decidir.
-- ═══════════════════════════════════════════════════════════════════════════
