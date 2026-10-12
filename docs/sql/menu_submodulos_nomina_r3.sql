-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Menú: nómina R3/R4 — tipos de comprobante, prenómina y nómina       ║
-- ║                                                                      ║
-- ║  Submódulos aparte de Recursos Humanos para darles su propio permiso:║
-- ║  recursos-humanos.tipos-comprobante-nomina y recursos-humanos.       ║
-- ║  prenomina (igual que el `codigo` en sidebar.config.ts y PermisoRutas)║
-- ║                                                                      ║
-- ║  Idempotente: se puede correr varias veces sin duplicar.             ║
-- ╚════════════════════════════════════════════════════════════════════╝

-- 1) Catálogo: crea los submódulos bajo Recursos Humanos (si no existen).
INSERT INTO submodulos (modulo_id, nombre, codigo, descripcion, activo, orden, created_at, updated_at)
SELECT m.id, v.nombre, v.codigo, v.descripcion, true,
       COALESCE((SELECT MAX(orden) FROM submodulos WHERE modulo_id = m.id), 0) + v.orden,
       NOW(), NOW()
FROM modulos m
JOIN (VALUES
    ('Tipos de comprobante de nómina', 'tipos-comprobante-nomina',
     'Nómina periódica, prima, cesantías, liquidación definitiva: clase, prefijo y empleados que admite', 1),
    ('Prenómina', 'prenomina',
     'Documento por tipo de comprobante: empleados, liquidación y detalle antes de confirmar la nómina', 2),
    ('Nómina', 'nomina-documento',
     'Nóminas generadas desde la prenómina confirmada: revisar, confirmar (asiento) y anular', 3),
    ('Cuentas contables de nómina', 'cuentas-nomina',
     'Cuenta del débito, del crédito y tercero por concepto de nómina (cambios sobre la parametrización)', 4),
    ('Retiros', 'retiros',
     'Registrar el retiro de una vinculación y generar su liquidación definitiva', 5),
    ('Vacaciones', 'vacaciones',
     'Registrar vacaciones en tiempo o en dinero, colectivas y regreso anticipado; se pagan en la nómina', 6),
    ('Incrementos salariales', 'incrementos',
     'Incremento por porcentaje, valor o SMMLV con vista previa, aplicar, reversar y generar el retroactivo', 7)
) AS v(nombre, codigo, descripcion, orden) ON TRUE
WHERE m.codigo = 'recursos-humanos'
  AND NOT EXISTS (
      SELECT 1 FROM submodulos s
      WHERE s.modulo_id = m.id AND s.codigo = v.codigo
  );

-- 2) Activación por empresa: los enciende para toda empresa que tenga
--    activo el módulo Recursos Humanos.
INSERT INTO empresa_submodulo (empresa_id, submodulo_id, activo, created_at, updated_at)
SELECT em.empresa_id, s.id, true, NOW(), NOW()
FROM modulos m
JOIN submodulos s ON s.modulo_id = m.id
JOIN empresa_modulo em ON em.modulo_id = m.id AND em.activo = true
WHERE m.codigo = 'recursos-humanos'
  AND s.codigo IN ('tipos-comprobante-nomina', 'prenomina', 'nomina-documento', 'cuentas-nomina', 'retiros',
                   'vacaciones', 'incrementos')
  AND NOT EXISTS (
      SELECT 1 FROM empresa_submodulo es
      WHERE es.empresa_id = em.empresa_id AND es.submodulo_id = s.id
  );
