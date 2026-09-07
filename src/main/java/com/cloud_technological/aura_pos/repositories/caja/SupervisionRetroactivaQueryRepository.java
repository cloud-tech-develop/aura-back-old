package com.cloud_technological.aura_pos.repositories.caja;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.caja.MovimientoRetroactivoDto;

/**
 * Lo que entró a una caja sin ser del turno.
 *
 * <p>Consultas unidas porque responden la misma pregunta desde tablas
 * distintas: documentos viejos autorizados a mano, movimientos declarados de
 * otro día — compras, gastos y abonos de cartera, en los dos sentidos —, pagos
 * cuyo documento es de otra fecha, cajas que el sistema dedujo sin que nadie
 * las eligiera, y correcciones sobre arqueos ya cerrados.
 *
 * <p>Los movimientos de otro día no descuadran ningún arqueo, así que no pasan
 * por el freno ni piden autorización. Aparecen aquí precisamente por eso: es el
 * único sitio donde quedan visibles.
 *
 * <p>Se mapea con {@link BeanPropertyRowMapper}: los alias en snake_case caen
 * solos en las propiedades camelCase del DTO. Con un RowMapper a mano, una
 * columna que se agregue al SELECT y se olvide de mapear devolvería null en
 * silencio.
 */
@Repository
public class SupervisionRetroactivaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private static final String SQL = """
        -- (1) Documentos viejos que entraron a la caja con autorización.
        --     Son los que el administrador tiene que revisar de verdad.
        --     `fecha` es cuándo se digitó y `fecha_documento` la de la factura:
        --     su diferencia es exactamente lo que el freno dejó pasar.
        SELECT CASE WHEN c.salida_caja_otro_dia THEN 'SALIDA_CAJA_OTRO_DIA'
                    ELSE 'DOCUMENTO_AUTORIZADO' END          AS tipo_hallazgo,
               c.id                                         AS referencia_id,
               c.created_at::date                           AS fecha,
               c.fecha::date                                AS fecha_documento,
               (c.created_at::date - c.fecha::date)         AS dias_atras,
               'Compra #' || c.id                           AS concepto,
               COALESCE(c.neto_a_pagar, c.total)            AS monto,
               'EGRESO'                                     AS tipo,
               NULL::bigint                                 AS turno_caja_id,
               NULL::varchar                                AS caja_nombre,
               ur.username                                  AS usuario_nombre,
               ua.username                                  AS autorizado_por_nombre,
               c.motivo_retroactivo                         AS motivo
          FROM compra c
          LEFT JOIN usuario ur ON ur.id = c.usuario_id
          LEFT JOIN usuario ua ON ua.id = c.autorizado_por
         WHERE c.empresa_id = :empresaId
           AND (c.autorizado_por IS NOT NULL OR c.salida_caja_otro_dia)
           AND c.fecha::date BETWEEN :desde AND :hasta

        UNION ALL

        SELECT CASE WHEN g.salida_caja_otro_dia THEN 'SALIDA_CAJA_OTRO_DIA'
                    ELSE 'DOCUMENTO_AUTORIZADO' END,
               g.id,
               g.created_at::date,
               g.fecha,
               (g.created_at::date - g.fecha),
               'Gasto #' || g.id,
               g.monto,
               'EGRESO',
               NULL::bigint,
               NULL::varchar,
               ur.username,
               ua.username,
               g.motivo_retroactivo
          FROM gasto g
          LEFT JOIN usuario ur ON ur.id = g.usuario_id
          LEFT JOIN usuario ua ON ua.id = g.autorizado_por
         WHERE g.empresa_id = :empresaId
           AND (g.autorizado_por IS NOT NULL OR g.salida_caja_otro_dia)
           AND g.fecha BETWEEN :desde AND :hasta

        UNION ALL

        -- (1b) Abonos de cartera declarados de otro día. No pasan por
        --     movimiento_caja: un abono entra al arqueo por su turno_caja_id, y
        --     estos se guardan sin turno justamente para no entrar. Aquí es
        --     donde se ven. `fecha_documento` es cuándo se movió la plata de
        --     verdad y `fecha` cuándo se digitó.
        SELECT 'INGRESO_CAJA_OTRO_DIA',
               ac.id,
               ac.created_at::date,
               ac.fecha_pago::date,
               (ac.created_at::date - ac.fecha_pago::date),
               'Recaudo cartera — abono #' || ac.id,
               ac.monto,
               'INGRESO',
               NULL::bigint,
               NULL::varchar,
               ur.username,
               NULL::varchar,
               NULL::varchar
          FROM abonos_cobrar ac
          JOIN cuentas_cobrar cc ON cc.id = ac.cuenta_cobrar_id
          LEFT JOIN usuario ur ON ur.id = ac.usuario_id
         WHERE cc.empresa_id = :empresaId
           AND ac.caja_otro_dia
           AND ac.deleted_at IS NULL
           AND ac.fecha_pago::date BETWEEN :desde AND :hasta

        UNION ALL

        SELECT 'SALIDA_CAJA_OTRO_DIA',
               ap.id,
               ap.created_at::date,
               ap.fecha_pago::date,
               (ap.created_at::date - ap.fecha_pago::date),
               'Pago a proveedor — abono #' || ap.id,
               ap.monto,
               'EGRESO',
               NULL::bigint,
               NULL::varchar,
               ur.username,
               NULL::varchar,
               NULL::varchar
          FROM abonos_pagar ap
          JOIN cuentas_pagar cp ON cp.id = ap.cuenta_pagar_id
          LEFT JOIN usuario ur ON ur.id = ap.usuario_id
         WHERE cp.empresa_id = :empresaId
           AND ap.caja_otro_dia
           AND ap.deleted_at IS NULL
           AND ap.fecha_pago::date BETWEEN :desde AND :hasta

        UNION ALL

        -- (1c) Cruces de comprobante que nunca cayeron en un arqueo. Hasta V154
        --     el comprobante manual (CE/RC) no preguntaba de dónde salía la
        --     plata: el cruce guardaba el abono sin turno y con metodo_pago
        --     'COMPROBANTE', un valor que MediosPago no reconoce como efectivo.
        --     El resultado es plata que entró o salió sin aparecer en el cierre
        --     de nadie.
        --
        --     Los arqueos de aquellos días ya están cerrados y no se reescriben
        --     — el conteo que firmó el cajero es la evidencia. Se listan aquí
        --     para que el administrador los vea y decida qué hacer con cada uno.
        SELECT 'COMPROBANTE_SIN_ARQUEO',
               ac.id,
               ac.fecha_pago::date,
               NULL::date,
               0,
               'Cruce de comprobante sin caja — ' || COALESCE(ac.referencia, 'abono #' || ac.id),
               ac.monto,
               'INGRESO',
               NULL::bigint,
               NULL::varchar,
               ur.username,
               NULL::varchar,
               NULL::varchar
          FROM abonos_cobrar ac
          JOIN cuentas_cobrar cc ON cc.id = ac.cuenta_cobrar_id
          LEFT JOIN usuario ur ON ur.id = ac.usuario_id
         WHERE cc.empresa_id = :empresaId
           AND ac.turno_caja_id IS NULL
           AND NOT ac.caja_otro_dia
           AND UPPER(COALESCE(ac.metodo_pago, '')) = 'COMPROBANTE'
           AND ac.deleted_at IS NULL
           AND ac.fecha_pago::date BETWEEN :desde AND :hasta

        UNION ALL

        SELECT 'COMPROBANTE_SIN_ARQUEO',
               ap.id,
               ap.fecha_pago::date,
               NULL::date,
               0,
               'Cruce de comprobante sin caja — ' || COALESCE(ap.referencia, 'abono #' || ap.id),
               ap.monto,
               'EGRESO',
               NULL::bigint,
               NULL::varchar,
               ur.username,
               NULL::varchar,
               NULL::varchar
          FROM abonos_pagar ap
          JOIN cuentas_pagar cp ON cp.id = ap.cuenta_pagar_id
          LEFT JOIN usuario ur ON ur.id = ap.usuario_id
         WHERE cp.empresa_id = :empresaId
           AND ap.turno_caja_id IS NULL
           AND NOT ap.caja_otro_dia
           AND UPPER(COALESCE(ap.metodo_pago, '')) = 'COMPROBANTE'
           AND ap.deleted_at IS NULL
           AND ap.fecha_pago::date BETWEEN :desde AND :hasta

        UNION ALL

        -- (2), (3) y (4) viven todas en movimiento_caja: el documento es de
        --     otro día, la caja la dedujo el sistema, o es un ajuste de cierre.
        SELECT CASE
                   WHEN m.es_ajuste_retroactivo THEN 'AJUSTE_CIERRE'
                   WHEN m.fecha_documento IS NOT NULL
                        AND m.fecha_documento <> m.fecha THEN 'PAGO_DE_OTRA_FECHA'
                   ELSE 'CAJA_INFERIDA'
               END,
               m.id,
               m.fecha,
               m.fecha_documento,
               COALESCE((m.fecha - m.fecha_documento), 0),
               m.concepto,
               m.monto,
               m.tipo,
               m.turno_caja_id,
               cj.nombre,
               ur.username,
               ua.username,
               m.motivo_ajuste
          FROM movimiento_caja m
          JOIN turno_caja t  ON t.id = m.turno_caja_id
          JOIN caja cj       ON cj.id = t.caja_id
          JOIN sucursal s    ON s.id = cj.sucursal_id
          LEFT JOIN usuario ur ON ur.id = m.usuario_id
          LEFT JOIN usuario ua ON ua.id = m.autorizado_por
         WHERE s.empresa_id = :empresaId
           AND m.fecha BETWEEN :desde AND :hasta
           AND (m.es_ajuste_retroactivo
                OR m.origen_inferido
                OR (m.fecha_documento IS NOT NULL AND m.fecha_documento <> m.fecha))

        ORDER BY fecha DESC, referencia_id DESC
        """;

    public List<MovimientoRetroactivoDto> listar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta);

        return jdbcTemplate.query(SQL, params,
                new BeanPropertyRowMapper<>(MovimientoRetroactivoDto.class));
    }
}
