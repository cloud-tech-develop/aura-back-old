CREATE TABLE tarifa_retencion (
  id                  BIGSERIAL PRIMARY KEY,
  empresa_id          INTEGER NOT NULL,
  tipo                VARCHAR(20) NOT NULL,
  concepto            VARCHAR(100) NOT NULL,
  codigo_concepto     VARCHAR(20),
  tarifa_natural      NUMERIC(5,2) NOT NULL,
  tarifa_juridica     NUMERIC(5,2) NOT NULL,
  base_minima         NUMERIC(18,2) NOT NULL DEFAULT 0,
  activo              BOOLEAN NOT NULL DEFAULT TRUE,
  created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_tarifa_empresa_tipo_concepto UNIQUE (empresa_id, tipo, concepto)
);
