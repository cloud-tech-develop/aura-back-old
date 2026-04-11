-- Trazabilidad fiscal en el detalle de asientos contables
ALTER TABLE asiento_detalle
    ADD COLUMN tercero_id    BIGINT,
    ADD COLUMN centro_costo_id BIGINT REFERENCES centros_costos(id);
