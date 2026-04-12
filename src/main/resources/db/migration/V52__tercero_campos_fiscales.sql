ALTER TABLE tercero
  ADD COLUMN tipo_persona          VARCHAR(10)   DEFAULT 'NATURAL',
  ADD COLUMN regimen               VARCHAR(30)   DEFAULT 'NO_RESPONSABLE_IVA',
  ADD COLUMN gran_contribuyente    BOOLEAN       NOT NULL DEFAULT FALSE,
  ADD COLUMN auto_retenedor        BOOLEAN       NOT NULL DEFAULT FALSE,
  ADD COLUMN codigo_ciiu           VARCHAR(10),
  ADD COLUMN actividad_economica   VARCHAR(200),
  ADD COLUMN pais                  VARCHAR(60)   DEFAULT 'Colombia',
  ADD COLUMN codigo_pais           VARCHAR(5)    DEFAULT 'CO';
