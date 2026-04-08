package com.cloud_technological.aura_pos.repositories.activos_fijos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.activos_fijos.ActivoFijoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ActivoFijoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public PageImpl<ActivoFijoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                af.id,
                af.codigo,
                af.descripcion,
                af.categoria,
                af.fecha_adquisicion,
                af.valor_compra,
                af.depreciacion_acumulada,
                (af.valor_compra - af.depreciacion_acumulada)  AS valor_en_libros,
                af.metodo_depreciacion,
                af.vida_util_meses,
                af.estado,
                COUNT(*) OVER() AS total_rows
            FROM activo_fijo af
            WHERE af.empresa_id = :empresaId
              AND af.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(af.codigo) LIKE :search OR LOWER(af.descripcion) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY af.codigo ASC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ActivoFijoTableDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ActivoFijoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }
}
