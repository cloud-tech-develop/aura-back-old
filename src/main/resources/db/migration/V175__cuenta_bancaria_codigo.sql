-- V175 — Código de la cuenta bancaria.
--
-- Las cuentas se distinguían solo por el nombre ("Bancolombia", "Bancolombia
-- nómina"…). El código (CB-001, CB-002…) las identifica sin ambigüedad en
-- reportes, conciliaciones e importaciones. Se asigna solo al crear y se puede
-- cambiar; es único por empresa.

ALTER TABLE cuenta_bancaria ADD COLUMN IF NOT EXISTS codigo VARCHAR(20);

-- Las cuentas que ya existen reciben su código en orden de creación.
UPDATE cuenta_bancaria cb
   SET codigo = t.codigo
  FROM (SELECT id,
               'CB-' || LPAD(ROW_NUMBER() OVER (PARTITION BY empresa_id ORDER BY id)::text, 3, '0') AS codigo
          FROM cuenta_bancaria
         WHERE codigo IS NULL) t
 WHERE cb.id = t.id
   AND NOT EXISTS (SELECT 1 FROM cuenta_bancaria x
                    WHERE x.empresa_id = cb.empresa_id AND x.codigo = t.codigo);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cuenta_bancaria_codigo
    ON cuenta_bancaria (empresa_id, UPPER(codigo))
    WHERE codigo IS NOT NULL;
