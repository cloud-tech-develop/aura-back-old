CREATE TABLE activo_fijo (
  id                      BIGSERIAL PRIMARY KEY,
  empresa_id              INTEGER NOT NULL,
  codigo                  VARCHAR(30) NOT NULL,
  descripcion             VARCHAR(200) NOT NULL,
  categoria               VARCHAR(50) NOT NULL,
  fecha_adquisicion       DATE NOT NULL,
  valor_compra            NUMERIC(18,2) NOT NULL,
  vida_util_meses         INTEGER NOT NULL,
  metodo_depreciacion     VARCHAR(20) NOT NULL DEFAULT 'LINEA_RECTA',
  depreciacion_acumulada  NUMERIC(18,2) NOT NULL DEFAULT 0,
  valor_residual          NUMERIC(18,2) NOT NULL DEFAULT 0,
  ubicacion               VARCHAR(100),
  responsable             VARCHAR(100),
  estado                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
  cuenta_activo_id        BIGINT REFERENCES plan_cuenta(id),
  cuenta_depreciacion_id  BIGINT REFERENCES plan_cuenta(id),
  cuenta_gasto_dep_id     BIGINT REFERENCES plan_cuenta(id),
  centro_costo_id         BIGINT REFERENCES centros_costos(id),
  periodo_contable_id     BIGINT REFERENCES periodo_contable(id),
  tercero_id              BIGINT REFERENCES tercero(id),
  observaciones           TEXT,
  created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMP,
  deleted_at              TIMESTAMP,
  CONSTRAINT uq_activo_empresa_codigo UNIQUE (empresa_id, codigo)
);

CREATE TABLE depreciacion_periodo (
  id              BIGSERIAL PRIMARY KEY,
  activo_id       BIGINT NOT NULL REFERENCES activo_fijo(id),
  empresa_id      INTEGER NOT NULL,
  periodo_id      BIGINT NOT NULL REFERENCES periodo_contable(id),
  valor           NUMERIC(18,2) NOT NULL,
  asiento_id      BIGINT REFERENCES asiento_contable(id),
  calculado_en    TIMESTAMP NOT NULL DEFAULT NOW()
);
