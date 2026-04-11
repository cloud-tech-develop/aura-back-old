package com.cloud_technological.aura_pos.repositories.centros_costos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.centros_costos.CentroCostoDto;
import com.cloud_technological.aura_pos.dto.centros_costos.CentroCostoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class CentroCostoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public PageImpl<CentroCostoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                cc.id,
                cc.codigo,
                cc.nombre,
                cc.descripcion,
                cc.tipo,
                cc.nivel,
                cc.permite_movimientos,
                cc.presupuesto_asignado,
                cc.activo,
                p.nombre AS nombre_padre,
                p.id     AS padre_id,
                COUNT(*) OVER() AS total_rows
            FROM centros_costos cc
            LEFT JOIN centros_costos p ON cc.centro_costo_padre_id = p.id
            WHERE cc.empresa_id = :empresaId
              AND cc.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(cc.codigo) LIKE :search OR LOWER(cc.nombre) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY cc.codigo ASC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<CentroCostoTableDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(CentroCostoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<CentroCostoDto> list(Integer empresaId) {
        String sql = """
            SELECT id, codigo, nombre, tipo, permite_movimientos
            FROM centros_costos
            WHERE empresa_id = :empresaId
              AND activo = TRUE
              AND deleted_at IS NULL
            ORDER BY codigo ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(CentroCostoDto.class));
    }
}
