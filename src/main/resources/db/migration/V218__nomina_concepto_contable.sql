-- V218 — Cuenta y tercero por concepto de nómina (rediseño R4c-2, brecha N3).
--
-- El asiento de nómina (R4c) usa por defecto la parametrización contable de la
-- empresa y reglas fijas de tercero (EPS, AFP, ARL, caja, DIAN, empleado). Esta
-- tabla deja que el contador cambie, por concepto, la cuenta del débito, la del
-- crédito y a nombre de quién va el crédito. NULL = se usa el valor por defecto.
--
-- tercero_credito: EMPLEADO | EPS | AFP | ARL | CCF | DIAN | NINGUNO | FIJO
--   (FIJO = siempre tercero_fijo_id, p. ej. el juzgado de un embargo).

CREATE TABLE IF NOT EXISTS nomina_concepto_contable (
    id                 BIGSERIAL    PRIMARY KEY,
    empresa_id         INTEGER      NOT NULL REFERENCES empresa(id),
    concepto_codigo    VARCHAR(30)  NOT NULL,
    cuenta_debito_id   BIGINT       REFERENCES plan_cuenta(id),
    cuenta_credito_id  BIGINT       REFERENCES plan_cuenta(id),
    tercero_credito    VARCHAR(20),
    tercero_fijo_id    BIGINT       REFERENCES tercero(id),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP,
    CONSTRAINT ck_nomina_concepto_contable_tercero CHECK (tercero_credito IS NULL OR tercero_credito IN
        ('EMPLEADO', 'EPS', 'AFP', 'ARL', 'CCF', 'DIAN', 'NINGUNO', 'FIJO')),
    CONSTRAINT ck_nomina_concepto_contable_fijo CHECK (tercero_credito IS DISTINCT FROM 'FIJO'
        OR tercero_fijo_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nomina_concepto_contable
    ON nomina_concepto_contable (empresa_id, concepto_codigo);
