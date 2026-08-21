-- ── V149: control de documentos con fecha retroactiva ──────────────────────
--
-- Las fases anteriores le dieron al usuario la forma de declarar de dónde sale
-- la plata, y al cajero la forma de ver qué parte de su arqueo no es suya. Falta
-- el freno: hoy nada impide cargar una factura de hace tres semanas a la caja
-- de hoy, y con eso vuelve el descuadre que todo esto vino a evitar.
--
-- La restricción aplica SOLO a la vía CAJA. Las demás — crédito, banco, caja
-- menor — no descuadran el arqueo de nadie, así que no tienen por qué pedir
-- permiso: ponerles fricción solo empujaría al usuario de vuelta a "Caja", que
-- es exactamente lo contrario de lo que se busca.
--
-- Dentro de la ventana de gracia no hay fricción alguna: pagar hoy la factura
-- de ayer es la operación más normal del mundo.

-- ── Parámetros por empresa ──────────────────────────────────────────────────
-- Viven en `empresa` como el resto de la configuración operativa
-- (modo_contabilizacion, factura_electronica…), no en una tabla aparte.

-- Días hacia atrás que se aceptan sin explicación. 3 cubre el fin de semana,
-- que es cuando más se acumulan facturas sin digitar.
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS dias_gracia_documento_retroactivo INT NOT NULL DEFAULT 3;

-- Con esto activo, pasada la ventana el documento NO puede ir a la caja salvo
-- que lo autorice el rol de abajo. Apagarlo deja pasar con motivo obligatorio.
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS bloquear_caja_retroactiva BOOLEAN NOT NULL DEFAULT TRUE;

-- Rol que puede saltarse la ventana. Se compara contra el rol del token.
ALTER TABLE empresa ADD COLUMN IF NOT EXISTS rol_autoriza_retroactivo VARCHAR(40) NOT NULL DEFAULT 'ADMIN';

ALTER TABLE empresa DROP CONSTRAINT IF EXISTS chk_empresa_dias_gracia;
ALTER TABLE empresa ADD  CONSTRAINT chk_empresa_dias_gracia
    CHECK (dias_gracia_documento_retroactivo >= 0);

-- ── Rastro en el documento ──────────────────────────────────────────────────
-- Quién autorizó y por qué. Sin esto la autorización no sirve de nada: el
-- objetivo no es solo frenar, es poder preguntar después qué pasó ese día.

ALTER TABLE compra ADD COLUMN IF NOT EXISTS motivo_retroactivo VARCHAR(500);
ALTER TABLE compra ADD COLUMN IF NOT EXISTS autorizado_por     INT;

ALTER TABLE gasto  ADD COLUMN IF NOT EXISTS motivo_retroactivo VARCHAR(500);
ALTER TABLE gasto  ADD COLUMN IF NOT EXISTS autorizado_por     INT;

ALTER TABLE compra DROP CONSTRAINT IF EXISTS fk_compra_autorizado_por;
ALTER TABLE compra ADD  CONSTRAINT fk_compra_autorizado_por
    FOREIGN KEY (autorizado_por) REFERENCES usuario(id);

ALTER TABLE gasto DROP CONSTRAINT IF EXISTS fk_gasto_autorizado_por;
ALTER TABLE gasto ADD  CONSTRAINT fk_gasto_autorizado_por
    FOREIGN KEY (autorizado_por) REFERENCES usuario(id);

-- Para el reporte "qué se autorizó fuera de plazo este mes".
CREATE INDEX IF NOT EXISTS idx_compra_retroactiva
    ON compra (autorizado_por) WHERE autorizado_por IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_gasto_retroactivo
    ON gasto (autorizado_por) WHERE autorizado_por IS NOT NULL;
