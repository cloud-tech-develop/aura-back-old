-- ── V99: Fase 1.b — enlazar `empleados` a `tercero` ─────────────────────────
--
-- Hoy `empleados` duplica nombres, apellidos, tipo_documento y numero_documento
-- respecto a `tercero`. El día que un empleado sea también proveedor, es la
-- misma persona dos veces sin forma de saberlo.
--
-- La nómina electrónica necesita datos fiscales y de identificación del
-- trabajador que viven (o deben vivir) en `tercero`. Sin esto habría que
-- duplicarlos otra vez.
--
-- Punto a favor: `tercero.es_empleado` YA existe — esta fase estaba anticipada.
--
-- ⚠️ Esta migración solo agrega la columna. El emparejamiento va en el script
--    de reconciliación (ver comentario al final). NO automatizar los ambiguos.

ALTER TABLE empleados
    ADD COLUMN IF NOT EXISTS tercero_id BIGINT REFERENCES tercero(id);

CREATE INDEX IF NOT EXISTS idx_empleados_tercero ON empleados(tercero_id);

COMMENT ON COLUMN empleados.tercero_id IS
    'Identidad de la persona. `empleados` pasa a ser el vínculo persona-empresa; '
    'los datos de identificación se leen de `tercero`.';

-- ── Emparejamiento automático: solo los casos SIN ambigüedad ────────────────
-- Empareja por (empresa_id, tipo_documento, numero_documento) cuando hay
-- exactamente UN tercero candidato. Los demás quedan NULL para revisión.
UPDATE empleados e
   SET tercero_id = t.id
  FROM tercero t
 WHERE e.tercero_id IS NULL
   AND t.deleted_at IS NULL
   AND t.empresa_id      = e.empresa_id
   AND t.tipo_documento   = e.tipo_documento
   AND t.numero_documento = e.numero_documento
   AND (
        SELECT COUNT(*) FROM tercero t2
         WHERE t2.deleted_at IS NULL
           AND t2.empresa_id      = e.empresa_id
           AND t2.tipo_documento   = e.tipo_documento
           AND t2.numero_documento = e.numero_documento
       ) = 1;

-- Marcar el rol EMPLEADO en los terceros emparejados
INSERT INTO tercero_rol (tercero_id, rol)
SELECT DISTINCT e.tercero_id, 'EMPLEADO'
  FROM empleados e
 WHERE e.tercero_id IS NOT NULL
ON CONFLICT DO NOTHING;

UPDATE tercero t
   SET es_empleado = TRUE
 WHERE EXISTS (SELECT 1 FROM empleados e WHERE e.tercero_id = t.id);


-- ═══════════════════════════════════════════════════════════════════════════
-- PASOS MANUALES ANTES DE V100 (no se pueden automatizar)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- 1. EMPLEADOS SIN TERCERO — revisar y resolver a mano:
--
--    SELECT e.id, e.empresa_id, e.nombres, e.apellidos,
--           e.tipo_documento, e.numero_documento
--      FROM empleados e
--     WHERE e.tercero_id IS NULL;
--
--    Para cada uno: o existe un tercero con typo en el documento (corregir y
--    enlazar), o hay varios candidatos (elegir), o no existe (crear tercero
--    con rol EMPLEADO). NO crear terceros a ciegas: se duplica gente.
--
-- 2. DOCUMENTOS DUPLICADOS EN TERCERO — la razón de que el UPDATE de arriba
--    deje NULLs:
--
--    SELECT empresa_id, tipo_documento, numero_documento, COUNT(*),
--           string_agg(id::text, ', ') AS ids
--      FROM tercero
--     WHERE deleted_at IS NULL
--     GROUP BY empresa_id, tipo_documento, numero_documento
--    HAVING COUNT(*) > 1;
--
--    Resolver la duplicidad (fusionar) antes de continuar.
--
-- 3. BACKFILL DE nombre1/2 + apellido1/2 (de V97) — REQUIERE REVISIÓN HUMANA.
--
--    Hay clientes y proveedores de POS ya cargados. Partir `nombres`/`apellidos`
--    por heurística FALLA con: apellidos compuestos ("DE LA ROSA", "VAN DER"),
--    nombres de una sola palabra, razón social en campo de nombres.
--
--    Punto de partida para el caso simple (2 palabras exactas), a validar:
--
--      UPDATE tercero
--         SET nombre1   = split_part(trim(nombres), ' ', 1),
--             nombre2   = NULLIF(split_part(trim(nombres), ' ', 2), ''),
--             apellido1 = split_part(trim(apellidos), ' ', 1),
--             apellido2 = NULLIF(split_part(trim(apellidos), ' ', 2), '')
--       WHERE tipo_persona = 'NATURAL'
--         AND array_length(string_to_array(trim(nombres),   ' '), 1) <= 2
--         AND array_length(string_to_array(trim(apellidos), ' '), 1) <= 2;
--
--    Y sacar el resto a revisión:
--
--      SELECT id, nombres, apellidos FROM tercero
--       WHERE tipo_persona = 'NATURAL'
--         AND (nombre1 IS NULL OR apellido1 IS NULL);
--
--    Los terceros JURIDICA no necesitan desagregación (usan razon_social).
--
-- 4. Solo cuando (1) y (2) estén en CERO en TODOS los ambientes → correr V100.
-- ═══════════════════════════════════════════════════════════════════════════
