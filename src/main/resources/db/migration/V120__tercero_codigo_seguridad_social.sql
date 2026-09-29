-- ── V120: código oficial UGPP para terceros que son EPS/AFP/CCF/ARL ─────────
--
-- Las entidades de seguridad social se crean como terceros con su rol
-- (EPS/AFP/CCF/ARL) — no en un catálogo aparte. Pero PILA exige el código
-- oficial UGPP de cada entidad: sin él, el operador rechaza el archivo.
--
-- Ese código no es el NIT ni un texto libre cualquiera: es el identificador
-- nacional de la entidad. Vive aquí, en el tercero que se paga.

ALTER TABLE tercero
    ADD COLUMN IF NOT EXISTS codigo_seguridad_social VARCHAR(20);

COMMENT ON COLUMN tercero.codigo_seguridad_social IS
    'Código oficial UGPP cuando el tercero es EPS/AFP/CCF/ARL. Lo exige PILA. '
    'Null para el resto de terceros.';
