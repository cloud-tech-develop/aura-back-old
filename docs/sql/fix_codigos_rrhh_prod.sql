-- ╔════════════════════════════════════════════════════════════════════╗
-- ║  Fix prod: códigos de submódulos RRHH creados desde el panel         ║
-- ║                                                                      ║
-- ║  El panel generó el código a partir del nombre ("grupos-de-nomina"), ║
-- ║  pero el front busca la clave exacta de sidebar.config.ts            ║
-- ║  ("recursos-humanos.grupos-nomina"), así que no aparecían.           ║
-- ║                                                                      ║
-- ║  Se corrige por código viejo (no por id) para que sea idempotente.   ║
-- ║  perfil_permiso y empresa_submodulo van por submodulo_id: no se      ║
-- ║  pierde ningún permiso ni activación.                                ║
-- ╚════════════════════════════════════════════════════════════════════╝

UPDATE submodulos s
SET codigo = v.nuevo, updated_at = NOW()
FROM modulos m,
     (VALUES
        ('cuentas-contables-de-nomina', 'cuentas-nomina'),
        ('grupos-de-nomina',            'grupos-nomina'),
        ('incrementos-salariales',      'incrementos'),
        ('motivos-de-ausentismo',       'motivos-ausentismo'),
        ('motivos-de-retiro',           'motivos-retiro'),
        ('niveles-de-cargo',            'niveles-cargo'),
        ('nomina',                      'nomina-documento'),
        ('tipos-de-cargo',              'tipos-cargo'),
        ('tipos-de-comprobante',        'tipos-comprobante-nomina'),
        ('tipos-de-embargo',            'tipos-embargo'),
        ('tipos-de-vinculacion',        'tipos-vinculacion')
     ) AS v(viejo, nuevo)
WHERE s.modulo_id = m.id
  AND m.codigo = 'recursos-humanos'
  AND s.codigo = v.viejo
  AND NOT EXISTS (
      SELECT 1 FROM submodulos x
      WHERE x.modulo_id = m.id AND x.codigo = v.nuevo
  );
