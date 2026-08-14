-- ── V101: Fase 1.d — cablear el rol BANCO ───────────────────────────────────
--
-- El problema (D10): `es_banco` existe como rol y TerceroQueryRepository.listarBancos()
-- lo usa para el "selector de banco (nómina/tesorería)". Pero al persistir:
--
--     cuenta_bancaria.banco  VARCHAR(200)   ← texto libre
--     empleados.banco        VARCHAR(100)   ← texto libre
--
-- El selector muestra terceros; lo que se guarda es el NOMBRE como string.
-- El rol no apunta a nada.
--
-- Por qué importa más allá de lo cosmético: es exactamente el bug que rompería
-- las afiliaciones a EPS (Fase 5.5). "SURA", "Sura EPS" y "EPS SURA" son el
-- mismo NIT y tres strings distintos → el operador de PILA rechaza el archivo.
-- Arreglarlo aquí valida el patrón sobre un caso simple antes de replicarlo.
--
-- NO AFECTA CONTABILIDAD (verificado): el motor de asientos resuelve el lado
-- del banco con cuenta_bancaria.cuenta_contable_id → plan_cuenta, y la
-- contrapartida con tesoreria_movimiento.contrapartida_cuenta_id (ver V66).
-- `banco` solo se lee para display: CuentaPdfService, FacturaQueryRepository
-- y copias entre DTOs. Ningún lector decide un débito o un crédito.

-- ── cuenta_bancaria.tercero_id ya existe (V96) — solo falta la FK real ───────
ALTER TABLE cuenta_bancaria
    ADD CONSTRAINT fk_cuenta_bancaria_tercero
    FOREIGN KEY (tercero_id) REFERENCES tercero(id);

CREATE INDEX IF NOT EXISTS idx_cuenta_bancaria_tercero
    ON cuenta_bancaria(tercero_id) WHERE tercero_id IS NOT NULL;

-- ── empleados.banco → tercero ───────────────────────────────────────────────
-- El banco donde se le consigna el sueldo. `tercero.banco` (V97) es el string
-- migrado; esta es la FK que lo reemplaza.
ALTER TABLE tercero
    ADD COLUMN IF NOT EXISTS banco_tercero_id BIGINT REFERENCES tercero(id);

COMMENT ON COLUMN tercero.banco_tercero_id IS
    'Entidad financiera donde este tercero tiene su cuenta. FK a un tercero con rol BANCO.';

-- ── Emparejamiento automático: solo coincidencia exacta ─────────────────────
-- Los demás quedan NULL para revisión. Es texto libre: va a haber variantes.
UPDATE cuenta_bancaria cb
   SET tercero_id = t.id
  FROM tercero t
  JOIN tercero_rol tr ON tr.tercero_id = t.id AND tr.rol = 'BANCO'
 WHERE cb.tercero_id IS NULL
   AND cb.banco IS NOT NULL
   AND t.deleted_at IS NULL
   AND t.empresa_id = cb.empresa_id
   AND upper(trim(t.razon_social)) = upper(trim(cb.banco));


-- ═══════════════════════════════════════════════════════════════════════════
-- PASOS MANUALES
-- ═══════════════════════════════════════════════════════════════════════════
--
-- 1. CUENTAS BANCARIAS SIN TERCERO — revisar:
--
--    SELECT cb.id, cb.empresa_id, cb.tipo, cb.nombre, cb.banco, cb.numero_cuenta
--      FROM cuenta_bancaria cb
--     WHERE cb.tercero_id IS NULL
--       AND cb.tipo = 'BANCO';
--
--    Para cada una: crear el tercero del banco con rol BANCO y enlazar.
--    Los tipos CAJA / NEQUI / DAVIPLATA / OTROS pueden no tener banco-tercero;
--    decidir con negocio (Nequi y Daviplata SÍ son entidades con NIT).
--
-- 2. VARIANTES DEL MISMO BANCO — el motivo de los NULL:
--
--    SELECT DISTINCT banco FROM cuenta_bancaria
--     WHERE tercero_id IS NULL AND banco IS NOT NULL
--     ORDER BY banco;
--
--    Esperable: "Bancolombia", "BANCOLOMBIA", "Bancolombia S.A." → un solo NIT.
--
-- 3. CUANDO (1) ESTÉ EN CERO — hacer tercero_id obligatorio para tipo BANCO:
--
--    ALTER TABLE cuenta_bancaria
--        ADD CONSTRAINT chk_cuenta_bancaria_banco_tercero
--        CHECK (tipo <> 'BANCO' OR tercero_id IS NOT NULL);
--
--    (No se incluye aquí: fallaría mientras haya cuentas sin reconciliar.)
--
-- 4. Código: CreateCuentaBancariaDto.terceroId hoy es "Opcional". Hacerlo
--    obligatorio cuando tipo = 'BANCO' y deprecar el VARCHAR `banco`.
-- ═══════════════════════════════════════════════════════════════════════════
