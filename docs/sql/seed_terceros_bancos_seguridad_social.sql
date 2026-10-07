-- ════════════════════════════════════════════════════════════════════════════
-- Seed de terceros nacionales para TODAS las empresas:
--   · Bancos (rol BANCO + es_banco)
--   · EPS, AFP (pensión), Fondos de cesantías, ARL, Cajas de compensación
--
-- Por qué: PILA rechaza la planilla si el contrato no tiene afiliación vigente
-- a EPS/AFP/CCF/ARL, y la afiliación exige un tercero con ese ROL y con
-- codigo_seguridad_social (AfiliacionService.afiliar). Nadie los había creado.
--
-- CÓDIGOS PILA: listado oficial de la UGPP, actualización enero 2024
--   https://www.ugpp.gov.co/wp-content/uploads/2025/01/bd_codigos_pila_2024.pdf
-- NIT: el listado UGPP no los trae. Se revisaron recalculando el DV DIAN; los
--   marcados ⚠ conviene confirmarlos en el RUES antes de pagarles o de exógena.
--
-- MISMO NIT, DOS CÓDIGOS: Compensar, Comfenalco Valle, Cajacopi, Comfachocó y
--   Comfaoriente son EPS y Caja con el mismo NIT, pero tercero tiene UN solo
--   codigo_seguridad_social, y la base exige NIT único por empresa → UN tercero
--   con los dos roles y el código de la EPS (ver LIMITACIÓN al final).
--
-- IDEMPOTENTE: se puede correr varias veces. Si la empresa ya tenía el tercero
--   (mismo NIT, sin código), se le completa el código y el rol, sin duplicarlo.
-- Sin BEGIN/COMMIT a propósito: envolverlo si se corre a mano.
-- ════════════════════════════════════════════════════════════════════════════

DROP TABLE IF EXISTS tmp_entidad;
CREATE TEMP TABLE tmp_entidad (
    nit      VARCHAR(20),
    dv       VARCHAR(1),
    nombre   VARCHAR(150),
    codigo   VARCHAR(20),   -- codigo_seguridad_social (NULL para bancos)
    roles    TEXT[],
    ess_tipo VARCHAR(10)    -- tipo en entidad_seguridad_social (NULL = no aplica)
);

INSERT INTO tmp_entidad (nit, dv, nombre, codigo, roles, ess_tipo) VALUES
-- ── EPS ─────────────────────────────────────────────────────────────────────
('830113831','0','ALIANSALUD EPS S.A.',                       'EPS001', '{EPS}', 'EPS'),
('800130907','4','SALUD TOTAL EPS-S S.A.',                    'EPS002', '{EPS}', 'EPS'),
('800251440','6','EPS SANITAS S.A.S.',                        'EPS005', '{EPS}', 'EPS'),
('860066942','7','COMPENSAR EPS',                             'EPS008', '{EPS}', 'EPS'),
('800088702','2','EPS SURA',                                  'EPS010', '{EPS}', 'EPS'),
('890303093','5','COMFENALCO VALLE EPS',                      'EPS012', '{EPS}', 'EPS'),
('830003564','7','EPS FAMISANAR S.A.S.',                      'EPS017', '{EPS}', 'EPS'),
('805001157','2','SERVICIO OCCIDENTAL DE SALUD EPS SOS',      'EPS018', '{EPS}', 'EPS'),
('900156264','2','NUEVA EPS S.A.',                            'EPS037', '{EPS}', 'EPS'),
('900604350','0','SAVIA SALUD EPS',                           'EPS040', '{EPS}', 'EPS'),
('900298372','9','CAPITAL SALUD EPS-S S.A.S.',                'EPSC34', '{EPS}', 'EPS'),
('817001773','3','ASOCIACION INDIGENA DEL CAUCA EPS-I AIC',   'EPSIC3', '{EPS}', 'EPS'),
('837000084','5','MALLAMAS EPS-I',                            'EPSIC5', '{EPS}', 'EPS'),
('809008362','2','PIJAOS SALUD EPS-I',                        'EPSIC6', '{EPS}', 'EPS'),
('806008394','7','MUTUAL SER EPS',                            'ESSC07', '{EPS}', 'EPS'),
('901021565','8','EMSSANAR EPS S.A.S.',                       'ESSC18', '{EPS}', 'EPS'),
('900226715','3','COOSALUD EPS S.A.',                         'ESSC24', '{EPS}', 'EPS'),
('900935126','7','ASMET SALUD EPS S.A.S.',                    'ESSC62', '{EPS}', 'EPS'),
('839000495','6','ANAS WAYUU EPS-I',                          'EPSIC4', '{EPS}', 'EPS'),
('890102044','1','CAJACOPI EPS',                              'CCFC55', '{EPS}', 'EPS'),
('891600091','8','COMFACHOCO EPS-S',                          'CCFC20', '{EPS}', 'EPS'),
('890500675','6','COMFAORIENTE EPS-S',                        'CCFC50', '{EPS}', 'EPS'),
('824001398','1','DUSAKAWI EPS-I',                            'EPSIC1', '{EPS}', 'EPS'),
('891856000','7','CAPRESOCA EPS-S',                           'EPSC25', '{EPS}', 'EPS'),

-- ── AFP (pensión) + fondos de cesantías ─────────────────────────────────────
-- Los privados administran pensión Y cesantías con el mismo NIT: un tercero,
-- dos roles. El código de cesantías no va en PILA; se reusa el de la AFP.
('800138188','1','PROTECCION S.A. PENSIONES Y CESANTIAS',     '230201', '{AFP,CESANTIAS}', 'AFP'),
('800144331','3','PORVENIR S.A. PENSIONES Y CESANTIAS',       '230301', '{AFP,CESANTIAS}', 'AFP'),
('800148514','2','SKANDIA PENSIONES Y CESANTIAS S.A.',        '230901', '{AFP,CESANTIAS}', 'AFP'),
('800149496','2','COLFONDOS S.A. PENSIONES Y CESANTIAS',      '231001', '{AFP,CESANTIAS}', 'AFP'),
('900336004','7','COLPENSIONES',                              '25-14',  '{AFP}',           'AFP'),
('860007379','8','CAXDAC',                                    '25-2',   '{AFP}',           'AFP'),  -- ⚠ NIT
('899999734','7','FONPRECON',                                 '25-3',   '{AFP}',           'AFP'),  -- ⚠ NIT
-- FNA solo cesantías. No tiene código PILA; 'FNA' es interno para que la
-- afiliación pase la validación de código obligatorio.
('899999284','4','FONDO NACIONAL DEL AHORRO',                 'FNA',    '{CESANTIAS}',     NULL),

-- ── ARL ─────────────────────────────────────────────────────────────────────
('890903790','5','ARL SURA - SEGUROS DE VIDA SURAMERICANA',   '14-11', '{ARL}', 'ARL'),
('860503617','3','SEGUROS DE VIDA ALFA S.A.',                 '14-17', '{ARL}', 'ARL'),
('860011153','6','POSITIVA COMPAÑIA DE SEGUROS S.A.',         '14-23', '{ARL}', 'ARL'),
('800226175','3','COLMENA SEGUROS RIESGOS LABORALES',         '14-25', '{ARL}', 'ARL'),
('830008686','1','LA EQUIDAD SEGUROS DE VIDA',                '14-29', '{ARL}', 'ARL'),
('830054904','6','MAPFRE COLOMBIA VIDA SEGUROS S.A.',         '14-30', '{ARL}', 'ARL'),
('860002183','9','AXA COLPATRIA SEGUROS DE VIDA S.A.',        '14-4',  '{ARL}', 'ARL'),
('860002503','2','COMPAÑIA DE SEGUROS BOLIVAR S.A.',          '14-7',  '{ARL}', 'ARL'),
('860022137','5','SEGUROS DE VIDA AURORA S.A.',               '14-8',  '{ARL}', 'ARL'),  -- ⚠ NIT

-- ── Cajas de compensación familiar ──────────────────────────────────────────
('890900842','6','COMFENALCO ANTIOQUIA',                      'CCF03', '{CCF}', 'CCF'),
('890900841','9','COMFAMA',                                   'CCF04', '{CCF}', 'CCF'),
('890102044','1','CAJACOPI ATLANTICO',                        'CCF05', '{CCF}', 'CCF'),
('890102002','2','COMBARRANQUILLA',                           'CCF06', '{CCF}', 'CCF'),
('890101994','9','COMFAMILIAR ATLANTICO',                     'CCF07', '{CCF}', 'CCF'),
('890480023','7','COMFENALCO CARTAGENA',                      'CCF08', '{CCF}', 'CCF'),
('890480110','1','COMFAMILIAR CARTAGENA',                     'CCF09', '{CCF}', 'CCF'),
('891800213','8','COMFABOY',                                  'CCF10', '{CCF}', 'CCF'),
('890806490','5','CONFA CALDAS',                              'CCF11', '{CCF}', 'CCF'),
('891190047','2','COMFACA',                                   'CCF13', '{CCF}', 'CCF'),
('891500182','0','COMFACAUCA',                                'CCF14', '{CCF}', 'CCF'),
('892399989','8','COMFACESAR',                                'CCF15', '{CCF}', 'CCF'),
('891080005','1','COMFACOR',                                  'CCF16', '{CCF}', 'CCF'),
('860013570','3','CAFAM',                                     'CCF21', '{CCF}', 'CCF'),
('860007336','1','COLSUBSIDIO',                               'CCF22', '{CCF}', 'CCF'),
('860066942','7','COMPENSAR CAJA DE COMPENSACION',            'CCF24', '{CCF}', 'CCF'),
('860045904','7','COMFACUNDI',                                'CCF26', '{CCF}', 'CCF'),
('891600091','8','COMFACHOCO CAJA DE COMPENSACION',           'CCF29', '{CCF}', 'CCF'),
('892115006','5','COMFAGUAJIRA',                              'CCF30', '{CCF}', 'CCF'),
('891180008','2','COMFAMILIAR HUILA',                         'CCF32', '{CCF}', 'CCF'),
('891780093','3','CAJAMAG',                                   'CCF33', '{CCF}', 'CCF'),
('892000146','3','COFREM',                                    'CCF34', '{CCF}', 'CCF'),
('891280008','1','COMFAMILIAR NARIÑO',                        'CCF35', '{CCF}', 'CCF'),
('890500675','6','COMFAORIENTE CAJA DE COMPENSACION',         'CCF36', '{CCF}', 'CCF'),
('890500516','3','COMFANORTE',                                'CCF37', '{CCF}', 'CCF'),
('890270275','5','CAFABA',                                    'CCF38', '{CCF}', 'CCF'),
('890200106','1','CAJASAN',                                   'CCF39', '{CCF}', 'CCF'),
('890201578','7','COMFENALCO SANTANDER',                      'CCF40', '{CCF}', 'CCF'),
('892200015','5','COMFASUCRE',                                'CCF41', '{CCF}', 'CCF'),
('890000381','0','COMFENALCO QUINDIO',                        'CCF43', '{CCF}', 'CCF'),
('891480000','1','COMFAMILIAR RISARALDA',                     'CCF44', '{CCF}', 'CCF'),
('800211025','1','COMFATOLIMA',                               'CCF48', '{CCF}', 'CCF'),  -- ⚠ NIT
('890700148','4','COMFENALCO TOLIMA',                         'CCF50', '{CCF}', 'CCF'),
('890303093','5','COMFENALCO VALLE CAJA DE COMPENSACION',     'CCF56', '{CCF}', 'CCF'),
('890303208','5','COMFANDI',                                  'CCF57', '{CCF}', 'CCF'),
('891200337','8','COMFAMILIAR PUTUMAYO',                      'CCF63', '{CCF}', 'CCF'),
('892400320','5','CAJASAI',                                   'CCF64', '{CCF}', 'CCF'),
('800231969','4','CAFAMAZ',                                   'CCF65', '{CCF}', 'CCF'),
('844003392','8','COMFACASANARE',                             'CCF69', '{CCF}', 'CCF'),

-- ── Bancos ──────────────────────────────────────────────────────────────────
('860002964','4','BANCO DE BOGOTA',                           NULL, '{BANCO}', NULL),
('860007738','9','BANCO POPULAR',                             NULL, '{BANCO}', NULL),
('890903938','8','BANCOLOMBIA S.A.',                          NULL, '{BANCO}', NULL),
('860051135','4','CITIBANK COLOMBIA',                         NULL, '{BANCO}', NULL),
('860050750','1','BANCO GNB SUDAMERIS',                       NULL, '{BANCO}', NULL),
('860003020','1','BBVA COLOMBIA',                             NULL, '{BANCO}', NULL),
('890903937','0','BANCO ITAU COLOMBIA',                       NULL, '{BANCO}', NULL),
('860034594','1','SCOTIABANK COLPATRIA',                      NULL, '{BANCO}', NULL),
('890300279','4','BANCO DE OCCIDENTE',                        NULL, '{BANCO}', NULL),
('800149923','6','BANCOLDEX',                                 NULL, '{BANCO}', NULL),
('860007335','4','BANCO CAJA SOCIAL',                         NULL, '{BANCO}', NULL),
('800037800','8','BANCO AGRARIO DE COLOMBIA',                 NULL, '{BANCO}', NULL),
('860034313','7','BANCO DAVIVIENDA',                          NULL, '{BANCO}', NULL),
('860035827','5','BANCO AV VILLAS',                           NULL, '{BANCO}', NULL),
('900378212','2','BANCO W',                                   NULL, '{BANCO}', NULL),
('900215071','1','BANCAMIA',                                  NULL, '{BANCO}', NULL),
('890200756','7','BANCO PICHINCHA',                           NULL, '{BANCO}', NULL),
('900406150','5','BANCOOMEVA',                                NULL, '{BANCO}', NULL),
('900047981','8','BANCO FALABELLA',                           NULL, '{BANCO}', NULL),
('860051894','6','BANCO FINANDINA',                           NULL, '{BANCO}', NULL),
('900628110','3','BANCO SANTANDER DE NEGOCIOS COLOMBIA',      NULL, '{BANCO}', NULL),
('890203088','9','BANCO COOPERATIVO COOPCENTRAL',             NULL, '{BANCO}', NULL),
('860043186','6','BANCO SERFINANZA',                          NULL, '{BANCO}', NULL),
('860025971','5','MIBANCO',                                   NULL, '{BANCO}', NULL),
('900768933','8','BANCO MUNDO MUJER',                         NULL, '{BANCO}', NULL),
('900114346','8','BANCO J.P. MORGAN COLOMBIA',                NULL, '{BANCO}', NULL),  -- ⚠ NIT
('860006797','9','BANCO UNION',                               NULL, '{BANCO}', NULL),
('900200960','9','BAN100',                                    NULL, '{BANCO}', NULL);


-- ── 1. Catálogo nacional (global, sin empresa) ──────────────────────────────
-- Solo EPS/AFP/CCF/ARL: el CHECK de entidad_seguridad_social no admite CESANTIAS.
INSERT INTO entidad_seguridad_social (tipo, codigo_oficial, nit, nombre)
SELECT ess_tipo, codigo, nit || '-' || dv, nombre
  FROM tmp_entidad
 WHERE ess_tipo IS NOT NULL
ON CONFLICT (tipo, codigo_oficial) DO NOTHING;


-- ── Un tercero por NIT ──────────────────────────────────────────────────────
-- La base tiene UNIQUE (empresa_id, numero_documento) — lo crea Laravel, no
-- está en Flyway —, así que un NIT = un tercero. Para los 5 NIT que son EPS y
-- caja a la vez, el tercero lleva los DOS roles y como código el de la EPS
-- (ver la limitación al final del archivo).
DROP TABLE IF EXISTS tmp_tercero;
CREATE TEMP TABLE tmp_tercero AS
SELECT DISTINCT ON (nit)
       nit, dv,
       -- nombre sin el sufijo EPS / CAJA cuando el NIT es las dos cosas
       CASE WHEN COUNT(*) OVER (PARTITION BY nit) > 1
            THEN regexp_replace(nombre, '\s+(EPS(-S)?|CAJA DE COMPENSACION)$', '')
            ELSE nombre END AS nombre,
       codigo
  FROM tmp_entidad
 ORDER BY nit, (ess_tipo = 'EPS') DESC NULLS LAST;


-- ── 2. Terceros que la empresa YA tenía (mismo NIT, sin código) ─────────────
-- Se les completa el código en vez de duplicarlos.
UPDATE tercero t
   SET codigo_seguridad_social = d.codigo,
       updated_at              = NOW()
  FROM tmp_tercero d
 WHERE d.codigo IS NOT NULL
   AND t.codigo_seguridad_social IS NULL
   AND t.deleted_at IS NULL
   AND regexp_replace(t.numero_documento, '\D', '', 'g') IN (d.nit, d.nit || d.dv);


-- ── 3. Crear lo que falte en cada empresa (uno por NIT) ─────────────────────
-- El NOT EXISTS NO filtra deleted_at: el UNIQUE tampoco, y un tercero borrado
-- con ese NIT también haría chocar el INSERT.
INSERT INTO tercero (
    empresa_id, tipo_documento, numero_documento, dv, razon_social,
    tipo_persona, pais, codigo_seguridad_social,
    es_cliente, es_proveedor, es_empleado, es_banco,
    gran_contribuyente, auto_retenedor,
    es_autoretenedor_ica, es_autoretenedor_fuente, declarante,
    activo, created_at
)
SELECT e.id, 'NIT', d.nit, d.dv, d.nombre,
       'JURIDICA', 'Colombia', d.codigo,
       FALSE, FALSE, FALSE, (d.codigo IS NULL),   -- sin código = banco
       FALSE, FALSE,
       FALSE, FALSE, FALSE,
       TRUE, NOW()
  FROM empresa e
 CROSS JOIN tmp_tercero d
 WHERE NOT EXISTS (
         SELECT 1 FROM tercero t
          WHERE t.empresa_id = e.id
            AND (t.numero_documento = d.nit
                 OR regexp_replace(t.numero_documento, '\D', '', 'g') IN (d.nit, d.nit || d.dv)));


-- ── 4. Roles (todos los del NIT: Compensar queda EPS + CCF) ─────────────────
INSERT INTO tercero_rol (tercero_id, rol)
SELECT DISTINCT t.id, r.rol
  FROM tmp_entidad d
 CROSS JOIN LATERAL unnest(d.roles) AS r(rol)
  JOIN tercero t
    ON t.deleted_at IS NULL
   AND regexp_replace(t.numero_documento, '\D', '', 'g') IN (d.nit, d.nit || d.dv)
ON CONFLICT DO NOTHING;

-- Los bancos se listan por el booleano (TerceroQueryRepository.listarBancos).
UPDATE tercero t
   SET es_banco = TRUE, updated_at = NOW()
  FROM tmp_entidad d
 WHERE 'BANCO' = ANY (d.roles)
   AND t.deleted_at IS NULL
   AND COALESCE(t.es_banco, FALSE) = FALSE
   AND regexp_replace(t.numero_documento, '\D', '', 'g') IN (d.nit, d.nit || d.dv);


-- ── 5. Enlace al catálogo nacional (fallback de ContratoAfiliacion.codigoOficial)
UPDATE tercero t
   SET entidad_seguridad_social_id = ess.id
  FROM entidad_seguridad_social ess
 WHERE t.entidad_seguridad_social_id IS NULL
   AND t.deleted_at IS NULL
   AND t.codigo_seguridad_social = ess.codigo_oficial
   AND EXISTS (SELECT 1 FROM tercero_rol tr
                WHERE tr.tercero_id = t.id AND tr.rol = ess.tipo);


-- ── LIMITACIÓN: mismo NIT, dos códigos ──────────────────────────────────────
-- Compensar, Comfenalco Valle, Cajacopi, Comfachocó y Comfaoriente quedan con
-- el código de la EPS. Si un empleado se afilia a uno de ellos COMO CAJA, PILA
-- saldría con el código de la EPS. Arreglo de fondo: el código por rol
-- (tercero_rol.codigo_seguridad_social) en vez de por tercero.

-- ── Verificación, por empresa y rol: EPS 24, AFP 7, CESANTIAS 5, ARL 9,
--    CCF 39, BANCO 28 (103 terceros: 5 NIT son EPS y CCF), más los que la
--    empresa ya tuviera.
-- SELECT t.empresa_id, tr.rol, COUNT(*)
--   FROM tercero t JOIN tercero_rol tr ON tr.tercero_id = t.id
--  WHERE tr.rol IN ('BANCO','EPS','AFP','CESANTIAS','ARL','CCF')
--  GROUP BY 1, 2 ORDER BY 1, 2;

DROP TABLE IF EXISTS tmp_entidad;
DROP TABLE IF EXISTS tmp_tercero;
