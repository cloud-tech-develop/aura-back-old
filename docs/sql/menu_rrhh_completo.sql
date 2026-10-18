-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: Recursos Humanos COMPLETO (consolida todos los submódulos)    ║
-- ║                                                                      ║
-- ║  Crea cualquier submódulo de Recursos Humanos que falte, con el      ║
-- ║  mismo `codigo` que usa sidebar.config.ts / PermisoRutas, y lo       ║
-- ║  activa para toda empresa que tenga activo el módulo.                ║
-- ║                                                                      ║
-- ║  Reemplaza a correr por separado menu_submodulos_nomina_r1/r2/r3.    ║
-- ║  Idempotente: se puede correr varias veces sin duplicar. No toca     ║
-- ║  los que ya existen ni apaga nada.                                   ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 0) Diagnóstico (correr antes y después): qué falta o está apagado.
-- SELECT v.codigo,
--        s.id IS NOT NULL                      AS existe,
--        s.activo                              AS activo_catalogo,
--        (SELECT COUNT(*) FROM empresa_submodulo es
--          WHERE es.submodulo_id = s.id AND es.activo) AS empresas_activas
-- FROM (VALUES ('empleados'),('vinculaciones'),('saldos-iniciales'),('proyectos-y-frentes'),
--              ('comisiones'),('liquidar-comisiones'),('conceptos'),('prenomina'),('nomina-documento'),
--              ('liquidacion-nomina'),('retiros'),('vacaciones'),('incrementos'),('nomina-electronica'),
--              ('pila'),('digitacion-asistencia'),('revision-asistencia-frente'),('preliquidacion-frente'),
--              ('turnos-empleado'),('marcaje'),('revision-asistencia'),('novedades-asistencia'),
--              ('autorizaciones'),('configuracion-laboral'),('calendario-laboral'),('config-nomina'),
--              ('cuentas-nomina'),('cargos'),('tipos-vinculacion'),('tipos-comprobante-nomina'),
--              ('grupos-nomina'),('niveles-cargo'),('tipos-cargo'),('tipos-embargo'),('motivos-retiro'),
--              ('motivos-ausentismo'),('calendarios-laborales'),('riesgos-arl')) AS v(codigo)
-- LEFT JOIN modulos m ON m.codigo = 'recursos-humanos'
-- LEFT JOIN submodulos s ON s.modulo_id = m.id AND s.codigo = v.codigo
-- ORDER BY existe, v.codigo;

-- 1) Catálogo: crea los submódulos que falten bajo Recursos Humanos.
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, v.nombre, v.codigo, v.descripcion, true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + v.orden,
       NOW(), NOW()
FROM modulos m
JOIN (VALUES
    -- Personal
    ('Empleados',                    'empleados',                  'Hoja de vida, contratos, afiliaciones y embargos', 1),
    ('Vinculaciones',                'vinculaciones',              'Contrato completo: tipo, cargo, jornada, prueba, jefe y soportes', 2),
    ('Saldos iniciales',             'saldos-iniciales',           'Vacaciones, cesantías y acumulados traídos de otro sistema', 3),
    ('Proyectos y Frentes',          'proyectos-y-frentes',        'Obras y frentes de trabajo a los que se asigna personal', 4),
    ('Comisiones',                   'comisiones',                 'Reglas de comisión por vendedor o técnico', 5),
    ('Liquidar Comisiones',          'liquidar-comisiones',        'Calcular y pagar las comisiones causadas', 6),
    -- Nómina
    ('Conceptos',                    'conceptos',                  'Devengos y deducciones de la nómina', 7),
    ('Prenómina',                    'prenomina',                  'Documento por tipo de comprobante: empleados, liquidación y revisión', 8),
    ('Nómina',                       'nomina-documento',           'Nóminas generadas desde la prenómina: confirmar y contabilizar', 9),
    ('Liquidación de nómina',        'liquidacion-nomina',         'Detalle de la nómina liquidada', 10),
    ('Retiros',                      'retiros',                    'Retiro de una vinculación y su liquidación definitiva', 11),
    ('Vacaciones',                   'vacaciones',                 'Vacaciones en tiempo o en dinero, colectivas y regreso anticipado', 12),
    ('Incrementos salariales',       'incrementos',                'Incremento salarial con vista previa y retroactivo', 13),
    ('Nómina Electrónica',           'nomina-electronica',         'Enviar la nómina electrónica a la DIAN', 14),
    ('PILA',                         'pila',                       'Planilla de seguridad social y su validación', 15),
    -- Asistencia
    ('Digitación Asistencia',        'digitacion-asistencia',      'Registrar la asistencia diaria por frente', 16),
    ('Revisión Asistencia (Frente)', 'revision-asistencia-frente', 'Aprobar la asistencia digitada en los frentes', 17),
    ('Preliquidación (Frente)',      'preliquidacion-frente',      'Horas y recargos del frente antes de la nómina', 18),
    ('Turnos Empleado',              'turnos-empleado',            'Horarios asignados a cada empleado', 19),
    ('Marcaje',                      'marcaje',                    'Entradas y salidas del personal', 20),
    ('Revisión asistencia',          'revision-asistencia',        'Días con tardanzas, ausencias o marcajes incompletos', 21),
    ('Novedades asistencia',         'novedades-asistencia',       'Novedades de asistencia que pasan a la nómina', 22),
    ('Autorizaciones',               'autorizaciones',             'Horas extra y permisos por autorizar', 23),
    -- Parámetros
    ('Configuración laboral',        'configuracion-laboral',      'Jornada, recargos y reglas laborales', 24),
    ('Calendario laboral',           'calendario-laboral',         'Festivos y días no laborables', 25),
    ('Config. Nómina',               'config-nomina',              'Salario mínimo, auxilio, porcentajes y cuentas', 26),
    ('Cuentas contables de nómina',  'cuentas-nomina',             'Cuenta y tercero de cada concepto en el asiento de nómina', 27),
    -- Catálogos de nómina
    ('Cargos',                       'cargos',                     'Cargos con nivel, tipo, plazas y rango salarial', 28),
    ('Tipos de vinculación',         'tipos-vinculacion',          'Planta, fijo, aprendiz, jornal: contrato y frecuencia por defecto', 29),
    ('Tipos de comprobante de nómina','tipos-comprobante-nomina',  'Nómina periódica, prima, cesantías, liquidación: clase, prefijo y empleados', 30),
    ('Grupos de nómina',             'grupos-nomina',              'Empleados que se liquidan juntos', 31),
    ('Niveles de cargo',             'niveles-cargo',              'Jerarquía de los cargos', 32),
    ('Tipos de cargo',               'tipos-cargo',                'Planta, temporal, por proyecto', 33),
    ('Tipos de embargo',             'tipos-embargo',              'Clases de embargo con su prioridad y límite legal', 34),
    ('Motivos de retiro',            'motivos-retiro',             'Causas de terminación y si generan indemnización', 35),
    ('Motivos de ausentismo',        'motivos-ausentismo',         'Incapacidades, licencias y permisos', 36),
    ('Calendarios laborales',        'calendarios-laborales',      'Días que se trabajan (lunes a viernes, a sábado, turnos)', 37),
    ('Riesgos ARL',                  'riesgos-arl',                'Clases de riesgo y su tarifa', 38)
) AS v(nombre, codigo, descripcion, orden) ON TRUE
WHERE m.codigo = 'recursos-humanos'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = v.codigo
  );

-- 2) Activación por empresa: enciende los submódulos de RRHH que la
--    empresa todavía no tenga, en toda empresa con el módulo activo.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM modulos m
JOIN submodulos s ON s.modulo_id = m.id AND s.activo = true
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE m.codigo = 'recursos-humanos'
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );

-- 3) Opcional: si el diagnóstico muestra alguno con activo_catalogo = false
--    o empresas_activas = 0 aunque exista, encenderlo (descomentar):
-- UPDATE submodulos s SET activo = true, updated_at = NOW()
-- FROM modulos m
-- WHERE s.modulo_id = m.id AND m.codigo = 'recursos-humanos' AND s.activo = false;
-- UPDATE empresa_submodulo es SET activo = true, updated_at = NOW()
-- FROM submodulos s JOIN modulos m ON m.id = s.modulo_id
-- WHERE es.submodulo_id = s.id AND m.codigo = 'recursos-humanos' AND es.activo = false;
