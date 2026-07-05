package com.cloud_technological.aura_pos.repositories.proyecto;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.proyecto.ProyectoDto;
import com.cloud_technological.aura_pos.dto.proyecto.ProyectoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ProyectoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public PageImpl<ProyectoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                p.id,
                p.codigo,
                p.nombre,
                p.cliente_id,
                COALESCE(cli.razon_social, cli.nombres) AS cliente_nombre,
                p.descripcion,
                p.fecha_inicio,
                p.fecha_fin,
                p.estado,
                p.centro_costo_id,
                cc.nombre AS centro_costo_nombre,
                p.responsable_administrativo_id,
                p.requiere_control_asistencia,
                p.ciudad,
                p.ubicacion,
                p.observacion,
                (SELECT COUNT(*) FROM proyecto_frente f
                    WHERE f.proyecto_id = p.id AND f.deleted_at IS NULL) AS frentes_count,
                COUNT(*) OVER() AS total_rows
            FROM proyecto p
            LEFT JOIN tercero cli ON p.cliente_id = cli.id
            LEFT JOIN centros_costos cc ON p.centro_costo_id = cc.id
            WHERE p.empresa_id = :empresaId
              AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(p.codigo) LIKE :search OR LOWER(p.nombre) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY p.created_at DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ProyectoTableDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ProyectoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<ProyectoDto> list(Integer empresaId) {
        String sql = """
            SELECT id, codigo, nombre, estado
            FROM proyecto
            WHERE empresa_id = :empresaId
              AND estado = 'ACTIVO'
              AND deleted_at IS NULL
            ORDER BY nombre ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbc.query(sql, params, new BeanPropertyRowMapper<>(ProyectoDto.class));
    }
}
