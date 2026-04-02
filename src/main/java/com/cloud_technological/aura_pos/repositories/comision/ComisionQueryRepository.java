package com.cloud_technological.aura_pos.repositories.comision;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.comision.ComisionConfigTableDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionLiquidacionTableDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionVentaDto;
import com.cloud_technological.aura_pos.dto.comision.TecnicoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ComisionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    // ── Técnicos activos de la empresa ────────────────────────
    public List<TecnicoDto> listarTecnicos(Integer empresaId) {
        String sql = """
            SELECT
                u.id,
                CONCAT(t.nombres, ' ', t.apellidos) AS nombre_completo
            FROM usuario u
            INNER JOIN tercero t ON u.tercero_id = t.id
            WHERE u.empresa_id = :empresaId
              AND u.activo = true
            ORDER BY t.nombres, t.apellidos
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, (rs, rowNum) ->
                new TecnicoDto(rs.getInt("id"), rs.getString("nombre_completo")));
    }

    // ── Config paginada ───────────────────────────────────────
    public PageImpl<ComisionConfigTableDto> listarConfig(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 15;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                cc.id,
                cc.modalidad,
                p.nombre  AS producto_nombre,
                cat.nombre AS categoria_nombre,
                CASE WHEN u.id IS NOT NULL
                     THEN CONCAT(t.nombres, ' ', t.apellidos)
                     ELSE NULL END AS tecnico_nombre,
                cc.tipo,
                cc.porcentaje_tecnico,
                cc.porcentaje_negocio,
                cc.activo,
                COUNT(*) OVER() AS total_rows
            FROM comision_config cc
            LEFT JOIN producto p   ON cc.producto_id   = p.id
            LEFT JOIN categoria cat ON cc.categoria_id = cat.id
            LEFT JOIN usuario u    ON cc.tecnico_id    = u.id
            LEFT JOIN tercero t    ON u.tercero_id     = t.id
            WHERE cc.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (
                    LOWER(COALESCE(p.nombre, ''))   LIKE :search
                 OR LOWER(COALESCE(cat.nombre, '')) LIKE :search
                 OR LOWER(COALESCE(t.nombres, ''))  LIKE :search
                 OR LOWER(COALESCE(t.apellidos,'')) LIKE :search
                )
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY cc.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ComisionConfigTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ComisionConfigTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // ── Liquidaciones paginadas ───────────────────────────────
    public PageImpl<ComisionLiquidacionTableDto> listarLiquidaciones(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 15;

        // Extraer filtro de estado del params
        String estadoFiltro = null;
        if (pageable.getParams() instanceof java.util.Map<?, ?> map) {
            Object estadoVal = map.get("estado");
            if (estadoVal != null && !estadoVal.toString().isBlank()) {
                estadoFiltro = estadoVal.toString();
            }
        }

        StringBuilder sql = new StringBuilder("""
            SELECT
                cl.id,
                CONCAT(t.nombres, ' ', t.apellidos) AS tecnico_nombre,
                CAST(cl.fecha_desde AS TEXT) AS fecha_desde,
                CAST(cl.fecha_hasta AS TEXT) AS fecha_hasta,
                cl.total_servicios,
                cl.valor_total,
                cl.estado,
                CAST(cl.fecha_pago AS TEXT) AS fecha_pago,
                COUNT(*) OVER() AS total_rows
            FROM comision_liquidacion cl
            INNER JOIN usuario u ON cl.tecnico_id = u.id
            INNER JOIN tercero t ON u.tercero_id = t.id
            WHERE cl.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (estadoFiltro != null) {
            sql.append(" AND cl.estado = :estado ");
            params.addValue("estado", estadoFiltro);
        }

        sql.append(" ORDER BY cl.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ComisionLiquidacionTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ComisionLiquidacionTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // ── Detalles de una liquidación ───────────────────────────
    public List<ComisionVentaDto> listarDetallesLiquidacion(Long liquidacionId) {
        String sql = """
            SELECT
                cv.id,
                cv.venta_id,
                cv.venta_detalle_id,
                p.nombre AS producto_nombre,
                CONCAT(t.nombres, ' ', t.apellidos) AS tecnico_nombre,
                cv.valor_total,
                cv.porcentaje_tecnico,
                cv.valor_tecnico,
                cv.valor_negocio,
                cv.liquidacion_id,
                CAST(cv.created_at AS TEXT) AS created_at
            FROM comision_venta cv
            INNER JOIN producto p ON cv.producto_id = p.id
            INNER JOIN usuario u ON cv.tecnico_id = u.id
            INNER JOIN tercero t ON u.tercero_id = t.id
            WHERE cv.liquidacion_id = :liquidacionId
            ORDER BY cv.id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("liquidacionId", liquidacionId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ComisionVentaDto.class));
    }

    // ── Comisiones pendientes de un técnico (para preview) ───
    public List<ComisionVentaDto> listarPendientesTecnico(Integer tecnicoId, Integer empresaId) {
        String sql = """
            SELECT
                cv.id,
                cv.venta_id,
                cv.venta_detalle_id,
                p.nombre AS producto_nombre,
                CONCAT(t.nombres, ' ', t.apellidos) AS tecnico_nombre,
                cv.valor_total,
                cv.porcentaje_tecnico,
                cv.valor_tecnico,
                cv.valor_negocio,
                cv.liquidacion_id,
                CAST(cv.created_at AS TEXT) AS created_at
            FROM comision_venta cv
            INNER JOIN producto p ON cv.producto_id = p.id
            INNER JOIN usuario u ON cv.tecnico_id = u.id
            INNER JOIN tercero t ON u.tercero_id = t.id
            WHERE cv.tecnico_id = :tecnicoId
              AND cv.empresa_id = :empresaId
              AND cv.liquidacion_id IS NULL
            ORDER BY cv.created_at
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tecnicoId", tecnicoId)
                .addValue("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ComisionVentaDto.class));
    }
}
