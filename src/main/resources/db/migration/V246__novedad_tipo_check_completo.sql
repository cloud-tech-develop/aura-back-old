-- V246 — chk_novedad_tipo vuelve a tener todos los tipos que el sistema genera.
--
-- V119 rehízo el CHECK de nomina_novedad.tipo para agregar los tipos de PILA y,
-- sin querer, quitó los que la asistencia agregó en V72 (recargo dominical/
-- festivo, hora extra dominical/festiva, ausencia no justificada, llegada tarde)
-- y nunca tuvo RETROACTIVO (R8). Resultado: un domingo o festivo trabajado, una
-- ausencia o un retroactivo tumbaban la liquidación ("viola la restricción
-- chk_novedad_tipo", y luego un error de SQL sin sentido porque la transacción
-- quedaba abortada).
--
-- Idempotente: se borra y se crea con la lista completa.

ALTER TABLE nomina_novedad DROP CONSTRAINT IF EXISTS chk_novedad_tipo;

ALTER TABLE nomina_novedad
    ADD CONSTRAINT chk_novedad_tipo CHECK (tipo IN (
        -- Horas extra y recargos (manuales o de la asistencia)
        'HORA_EXTRA_DIURNA', 'HORA_EXTRA_NOCTURNA', 'HORA_EXTRA_DOMINICAL', 'HORA_EXTRA_FESTIVO',
        'HORA_EXTRA_DOMINICAL_FESTIVA', 'RECARGO_NOCTURNO', 'RECARGO_DOMINICAL_FESTIVO',
        -- Descuentos que genera la asistencia
        'AUSENCIA_NO_JUSTIFICADA', 'LLEGADA_TARDE_DESCONTADA',
        -- Ausentismos y licencias (PILA)
        'INCAPACIDAD', 'LICENCIA_REMUNERADA', 'LICENCIA_NO_REMUNERADA', 'LICENCIA_MATERNIDAD',
        'VACACIONES', 'SUSPENSION',
        -- Devengos y deducciones
        'BONO', 'COMISION', 'PRESTAMO', 'EMBARGO', 'OTRO_DEVENGO', 'OTRO_DESCUENTO',
        -- Diferencia salarial (R8)
        'RETROACTIVO'
    ));
