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
            dto.setTotalRows(rs.getLong("total_rows"));
            return dto;
        });
    }

    public String siguienteNumeroComprobante(Integer empresaId, String prefix) {
        String sql = """
            SELECT COALESCE(MAX(
                CAST(SUBSTRING(numero_comprobante FROM LENGTH(:prefix) + 2) AS INTEGER)
            ), 0) + 1
            FROM comprobante_caja
            WHERE empresa_id = :empresaId
              AND numero_comprobante LIKE :prefixLike
            """;
        Integer siguiente = jdbc.queryForObject(sql,
                Map.of("empresaId", empresaId, "prefix", prefix, "prefixLike", prefix + "-%"),
                Integer.class);
        return String.format("%s-%06d", prefix, siguiente != null ? siguiente : 1);
    }
}
