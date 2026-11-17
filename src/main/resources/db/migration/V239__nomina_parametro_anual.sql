-- V239 — Salario mínimo y auxilio de transporte por AÑO.
--
-- Hasta aquí nomina_config guardaba UN valor por empresa (1.423.500 / 200.000,
-- los de 2025). Cada enero había que cambiarlo a mano y, si se reliquidaba un
-- período del año anterior, salía con el valor nuevo. Ahora el motor toma el
-- valor del año del período desde esta tabla global; nomina_config.smmlv y
-- auxilio_transporte quedan solo de respaldo para un año que no esté aquí.
--
-- También corrige la UVT de 2026: V108 la sembró con 49.799, que es la de 2025.
--
-- ⚠ Valores de ley: verificar con el decreto del Gobierno (SMMLV y auxilio) y la
--   resolución de la DIAN (UVT) antes de cada año nuevo. Idempotente.

CREATE TABLE IF NOT EXISTS nomina_parametro_anual (
    agno                INT            PRIMARY KEY,
    smmlv               NUMERIC(15,2)  NOT NULL,
    auxilio_transporte  NUMERIC(15,2)  NOT NULL,
    norma               VARCHAR(200),
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);

INSERT INTO nomina_parametro_anual (agno, smmlv, auxilio_transporte, norma) VALUES
    (2023, 1160000.00, 140606.00, 'Decretos de salario mínimo y auxilio 2023'),
    (2024, 1300000.00, 162000.00, 'Decretos de salario mínimo y auxilio 2024'),
    (2025, 1423500.00, 200000.00, 'Decretos de salario mínimo y auxilio 2025'),
    (2026, 1750905.00, 249095.00, 'Decretos de salario mínimo y auxilio 2026')
ON CONFLICT (agno) DO NOTHING;

INSERT INTO uvt_valor (agno, valor) VALUES (2025, 49799.00)
ON CONFLICT (agno) DO NOTHING;

-- Solo si sigue con el valor equivocado de V108 (no pisa un valor corregido a mano).
UPDATE uvt_valor SET valor = 52374.00 WHERE agno = 2026 AND valor = 49799.00;

-- Nuevas empresas: el respaldo arranca con el valor vigente, no con el de 2025.
ALTER TABLE nomina_config ALTER COLUMN smmlv SET DEFAULT 1750905;
ALTER TABLE nomina_config ALTER COLUMN auxilio_transporte SET DEFAULT 249095;
