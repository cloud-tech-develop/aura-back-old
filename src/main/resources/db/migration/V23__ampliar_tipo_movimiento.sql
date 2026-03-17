-- ── V23: Ampliar tipo_movimiento para soportar tipos más largos ───────────────
ALTER TABLE movimiento_inventario
    ALTER COLUMN tipo_movimiento TYPE VARCHAR(50);
