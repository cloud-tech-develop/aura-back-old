-- Nómina electrónica Factus: el rango de numeración (ULID) es POR EMPRESA.
-- Factus le crea a cada NIT su cliente de nómina y le entrega su propio id
-- (documento tipo 26). No es un valor global de .env: va en la empresa, al lado
-- del rango de facturación (factus_numbering_range_id, que es para facturas).

ALTER TABLE empresa
    ADD COLUMN IF NOT EXISTS factus_nomina_numbering_range_id VARCHAR(40);

COMMENT ON COLUMN empresa.factus_nomina_numbering_range_id IS
    'Rango de numeración de nómina electrónica (ULID) que Factus asigna a esta '
    'empresa. GET /v2/numbering-ranges?filter[document]=26.';
