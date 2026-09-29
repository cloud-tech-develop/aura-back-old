-- ── V115: Fase 4.a — dimensionar la nómina por proyecto/frente ──────────────
--
-- ╔═══════════════════════════════════════════════════════════════════════════╗
-- ║ CAPA OPCIONAL. La nómina funciona completa SIN esta fase.                 ║
-- ║                                                                           ║
-- ║ Muchas empresas no manejan proyectos ni frentes: tienen empleados que son ║
-- ║ trabajadores normales y punto. Para ellas estas columnas quedan NULL, la  ║
-- ║ cascada cae al último paso, y el resultado es IDÉNTICO a no tenerlas.     ║
-- ╚═══════════════════════════════════════════════════════════════════════════╝
--
-- QUÉ ARREGLA: la V92 dice "habilita rentabilidad por obra" y dimensionó
-- `compra` y `gasto` con proyecto_id/frente_id — PERO NO `nomina`. La mano de
-- obra, normalmente el costo mayor, no llega al proyecto. La rentabilidad por
-- obra sale incompleta y siempre optimista.
--
-- Seguro sin tocar código: agrega columnas nullable con DEFAULT.

ALTER TABLE nomina_detalle
    ADD COLUMN IF NOT EXISTS proyecto_id        BIGINT REFERENCES proyecto(id),
    ADD COLUMN IF NOT EXISTS frente_id          BIGINT REFERENCES proyecto_frente(id),
    ADD COLUMN IF NOT EXISTS centro_costo_id    BIGINT,
    -- 100 = sin distribución. Una empresa sin proyectos genera una fila por
    -- concepto con las tres dimensiones nulas y este campo en 100 — que es
    -- exactamente lo que la Fase 3.b ya producía.
    ADD COLUMN IF NOT EXISTS porcentaje_distrib NUMERIC(5,2) NOT NULL DEFAULT 100;

CREATE INDEX IF NOT EXISTS idx_nomina_detalle_proyecto
    ON nomina_detalle(proyecto_id) WHERE proyecto_id IS NOT NULL;

COMMENT ON COLUMN nomina_detalle.porcentaje_distrib IS
    'Porcentaje del concepto imputado a esta dimensión. Deja auditable el '
    'reparto. 100 = sin distribución (caso mayoritario).';


-- ═══════════════════════════════════════════════════════════════════════════
-- NOTAS PARA EL MOTOR
--
-- UNA FILA POR (concepto × dimensión). Si un empleado trabajó 10 días en el
-- frente A y 10 en el B, cada concepto genera dos filas al 50%.
--
-- EL DRIVER ES LA ASISTENCIA REAL, no un porcentaje pactado:
--
--   % frente A = horas del empleado en frente A / horas totales del período
--
-- Sale de asistencia_frente_detalle, que YA TIENE horas_ordinarias,
-- horas_extra_diurnas, horas_extra_nocturnas, horas_dominicales y
-- horas_festivas por empleado/frente/día.
--
-- Esto es MEJOR que el ERP de referencia: ellos reparten por un porcentaje
-- fijo configurado en la vinculación (com_centro_costo_nom_vinculacion).
-- Aquí se reparte por lo que DE VERDAD pasó. Es la ventaja de tener asistencia
-- por frente — que el ERP no tiene.
--
-- CASCADA DE RESOLUCIÓN (en este orden):
--   1. ¿Tiene asistencia por frente en el período? → distribuir por horas
--   2. ¿El contrato tiene contrato_centro_costo?   → distribuir por %
--   3. Si no → una fila, dimensiones NULL, porcentaje_distrib = 100
--
--   ⚠️ EL PASO 3 ES EL CASO MAYORITARIO, NO EL BORDE. Es el de cualquier
--      empresa con empleados normales. Debe ser el camino por defecto y estar
--      cubierto por tests como ciudadano de primera: una nómina sin proyectos
--      tiene que dar el MISMO resultado con o sin esta fase aplicada.
--
-- CASOS BORDE — decidir con negocio:
--   · ¿Prima y cesantías se reparten entre frentes igual que el salario, o van
--     a un centro de costo administrativo? (contablemente lo primero es
--     correcto: son costo laboral del proyecto)
--   · Ausentismos: un incapacitado no tiene horas en ningún frente. ¿Se reparte
--     por la asistencia del resto del período o va a administrativo?
--   · REDONDEO: repartir entre 3 frentes deja centavos. Definir a cuál van
--     (típicamente al mayor) y que la suma de las filas CUADRE EXACTAMENTE con
--     el total del concepto. Test obligatorio.
--
-- AL APROBAR: propagar proyecto_id/frente_id/centro_costo_id a asiento_detalle
-- (la V92 ya tiene esas columnas). Ahí es donde la cadena queda cerrada.
--
-- Marcar asistencia_frente.estado = 'ENVIADO_NOMINA' cuando su período se
-- liquide — el estado YA EXISTE en el CHECK y hoy no lo pone nadie.
-- ═══════════════════════════════════════════════════════════════════════════
