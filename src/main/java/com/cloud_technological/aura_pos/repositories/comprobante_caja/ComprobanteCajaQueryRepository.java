package com.cloud_technological.aura_pos.repositories.comprobante_caja;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.cloud_technological.aura_pos.dto.comprobante.ComprobanteCajaDto;

@Repository
public class ComprobanteCajaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public List<ComprobanteCajaDto> paginar(Integer empresaId, String tipo,
            String desde, String hasta, int page, int rows) {
        int offset = page * rows;
        String tipoFiltro = (tipo != null && !tipo.isBlank()) ? "AND c.tipo = :tipo\n" : "";

        String sql = """
            SELECT c.id, c.numero_comprobante, c.tipo, c.concepto, c.monto,
                   c.metodo_pago, c.entregado_a, c.origen, c.origen_id,
                   c.turno_caja_id, c.usuario_id, c.created_at,
                   c.anulado, c.motivo_anulacion,
                   COUNT(*) OVER() AS total_rows
            FROM comprobante_caja c
            WHERE c.empresa_id = :empresaId
              AND DATE(c.created_at) BETWEEN CAST(:desde AS DATE) AND CAST(:hasta AS DATE)
            """ + tipoFiltro + """
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT :rows OFFSET :offset
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("desde", desde)
                .addValue("hasta", hasta)
                .addValue("rows", rows)
                .addValue("offset", offset);
        if (tipo != null && !tipo.isBlank()) params.addValue("tipo", tipo);

        return jdbc.query(sql, params, (rs, i) -> {
            ComprobanteCajaDto dto = new ComprobanteCajaDto();
            dto.setId(rs.getLong("id"));
            dto.setNumeroComprobante(rs.getString("numero_comprobante"));
            dto.setTipo(rs.getString("tipo"));
            dto.setConcepto(rs.getString("concepto"));
            dto.setMonto(rs.getBigDecimal("monto"));
            dto.setMetodoPago(rs.getString("metodo_pago"));
            dto.setEntregadoA(rs.getString("entregado_a"));
            dto.setOrigen(rs.getString("origen"));
            dto.setOrigenId(rs.getObject("origen_id") != null ? rs.getLong("origen_id") : null);
            dto.setTurnoCajaId(rs.getObject("turno_caja_id") != null ? rs.getLong("turno_caja_id") : null);
            dto.setUsuarioId(rs.getObject("usuario_id") != null ? rs.getInt("usuario_id") : null);
            dto.setCreatedAt(rs.getString("created_at"));
            // El mapeo es manual: cada columna nueva del SELECT hay que leerla
            // aquí también, o el campo viaja en null y el front no se entera.
            dto.setAnulado(rs.getBoolean("anulado"));
            dto.setMotivoAnulacion(rs.getString("motivo_anulacion"));
            dto.setTotalRows(rs.getLong("total_rows"));
            return dto;
        });
    }

    public String siguienteNumeroComprobante(Integer empresaId, String prefix) {
        // Serie compartida entre asientos, comprobantes de caja y notas de
        // diario: el mismo candado que NotaDiarioQueryRepository.bloquearSerie,
        // para que dos documentos simultáneos no tomen el mismo número.
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_advisory_xact_lock(CAST(:empresaId AS INTEGER), hashtext(:serie))",
                Map.of("empresaId", empresaId, "serie", "serie-" + prefix), Integer.class);
        // Contador UNIFICADO por prefijo: comparte serie con los asientos contables.
        String sql = """
            SELECT COALESCE(MAX(n), 0) + 1 FROM (
                SELECT CAST(SUBSTRING(numero_comprobante FROM LENGTH(:prefix) + 2) AS INTEGER) AS n
                FROM comprobante_caja
                WHERE empresa_id = :empresaId AND numero_comprobante LIKE :prefixLike
                UNION ALL
                SELECT CAST(SUBSTRING(numero_comprobante FROM LENGTH(:prefix) + 2) AS INTEGER) AS n
                FROM asiento_contable
                WHERE empresa_id = :empresaId AND numero_comprobante LIKE :prefixLike
            ) t
            """;
        Integer siguiente = jdbc.queryForObject(sql,
                Map.of("empresaId", empresaId, "prefix", prefix, "prefixLike", prefix + "-%"),
                Integer.class);
        return String.format("%s-%06d", prefix, siguiente != null ? siguiente : 1);
    }

    /**
     * Asiento vigente del documento que amparó el comprobante de caja. El
     * comprobante contable manual apunta directo a su asiento; los demás, al
     * documento (compra, gasto, abono, movimiento de caja…) por su tipo de origen.
     */
    public Long asientoDeComprobante(Long comprobanteId, Integer empresaId) {
        java.util.List<Long> r = jdbc.queryForList("""
            SELECT a.id
            FROM comprobante_caja c
            JOIN asiento_contable a ON a.empresa_id = c.empresa_id
             AND (
                  (c.origen = 'COMPROBANTE' AND a.id = c.origen_id)
               OR (c.origen <> 'COMPROBANTE' AND a.origen_id = c.origen_id
                   AND a.tipo_origen = CASE c.origen
                        WHEN 'ABONO_CXC' THEN 'ABONO_COBRAR'
                        WHEN 'ABONO_CXP' THEN 'ABONO_PAGAR'
                        WHEN 'MANUAL'    THEN 'MOVIMIENTO_CAJA'
                        ELSE c.origen END)
             )
            WHERE c.id = :id AND c.empresa_id = :empresaId
              AND a.estado IN ('CONTABILIZADO', 'ANULADO')
            ORDER BY (a.estado = 'CONTABILIZADO') DESC, a.id DESC
            LIMIT 1
            """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", comprobanteId)
                .addValue("empresaId", empresaId), Long.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Cabecera del asiento para el PDF del comprobante. */
    public com.cloud_technological.aura_pos.dto.comprobante.ComprobanteContableDto asientoParaComprobante(Long asientoId) {
        java.util.List<com.cloud_technological.aura_pos.dto.comprobante.ComprobanteContableDto> r = jdbc.query("""
            SELECT a.id AS asiento_id, a.numero_comprobante AS numero_asiento, a.fecha, a.descripcion, a.estado,
                   t.numero_documento AS beneficiario_documento, u.username AS elaborado_por,
                   a.total_debito, a.total_credito
            FROM asiento_contable a
            LEFT JOIN tercero t ON t.id = a.beneficiario_tercero_id
            LEFT JOIN usuario u ON u.id = a.usuario_id
            WHERE a.id = :id
            """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", asientoId),
                new org.springframework.jdbc.core.BeanPropertyRowMapper<>(
                        com.cloud_technological.aura_pos.dto.comprobante.ComprobanteContableDto.class));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Líneas del asiento con cuenta, tercero y centro de costo. */
    public java.util.List<com.cloud_technological.aura_pos.dto.comprobante.ComprobanteContableDto.Linea>
            lineasParaComprobante(Long asientoId) {
        return jdbc.query("""
            SELECT pc.codigo AS cuenta_codigo, pc.nombre AS cuenta_nombre,
                   COALESCE(NULLIF(t.razon_social, ''), NULLIF(TRIM(CONCAT(t.nombres, ' ', t.apellidos)), '')) AS tercero_nombre,
                   t.numero_documento AS tercero_documento, cc.nombre AS centro_costo_nombre,
                   ad.descripcion, ad.debito, ad.credito
            FROM asiento_detalle ad
            JOIN plan_cuenta pc ON pc.id = ad.cuenta_id
            LEFT JOIN tercero t ON t.id = ad.tercero_id
            LEFT JOIN centros_costos cc ON cc.id = ad.centro_costo_id
            WHERE ad.asiento_id = :id
            ORDER BY (ad.debito > 0) DESC, ad.id
            """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", asientoId),
                new org.springframework.jdbc.core.BeanPropertyRowMapper<>(
                        com.cloud_technological.aura_pos.dto.comprobante.ComprobanteContableDto.Linea.class));
    }
}
