-- ── V150: corregir un arqueo ya cerrado sin reabrirlo ──────────────────────
--
-- El caso que falta: de verdad salió plata de una caja, el turno se cerró sin
-- registrarla, y el faltante quedó como una diferencia sin explicación.
--
-- La salida obvia sería reabrir el turno y volver a cerrarlo. NO se hace, por
-- cuatro razones:
--   1. Rompe la evidencia. El arqueo cerrado es la prueba de que el cajero
--      entregó $X; si se puede reescribir, deja de probar nada.
--   2. El cierre ya generó su asiento de DIFERENCIA_CAJA. Reabrir obliga a
--      reversarlo y regenerarlo.
--   3. Si el período contable ya cerró, ni siquiera se puede reversar.
--   4. Es la puerta de fraude más obvia del sistema: "reabro el día donde falta
--      plata y lo cuadro".
--
-- En su lugar, el ajuste se AGREGA sobre el turno cerrado y el cierre original
-- queda intacto. El resumen pasa a mostrar tres cifras — cierre original,
-- ajustes posteriores, saldo ajustado — en vez de una sola reescrita.

-- ── El movimiento que corrige ───────────────────────────────────────────────
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS es_ajuste_retroactivo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS motivo_ajuste  VARCHAR(500);
ALTER TABLE movimiento_caja ADD COLUMN IF NOT EXISTS autorizado_por INT;

ALTER TABLE movimiento_caja DROP CONSTRAINT IF EXISTS fk_movimiento_caja_autorizado_por;
ALTER TABLE movimiento_caja ADD  CONSTRAINT fk_movimiento_caja_autorizado_por
    FOREIGN KEY (autorizado_por) REFERENCES usuario(id);

-- Un ajuste sin motivo no es un ajuste: es alguien cuadrando la caja a mano.
ALTER TABLE movimiento_caja DROP CONSTRAINT IF EXISTS chk_movimiento_caja_ajuste_motivado;
ALTER TABLE movimiento_caja ADD  CONSTRAINT chk_movimiento_caja_ajuste_motivado
    CHECK (es_ajuste_retroactivo = FALSE OR motivo_ajuste IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_mov_caja_ajuste
    ON movimiento_caja (turno_caja_id)
    WHERE es_ajuste_retroactivo = TRUE;

-- ── El turno conserva su cierre original ────────────────────────────────────
-- `diferencia` NO se toca nunca: es lo que el cajero firmó ese día. Al primer
-- ajuste se copia a `diferencia_original` para dejar constancia de que la
-- columna viva pudo haber cambiado, y `diferencia_ajustada` lleva el resultado
-- de aplicar los ajustes encima.
ALTER TABLE turno_caja ADD COLUMN IF NOT EXISTS diferencia_original NUMERIC(15,2);
ALTER TABLE turno_caja ADD COLUMN IF NOT EXISTS diferencia_ajustada NUMERIC(15,2);
