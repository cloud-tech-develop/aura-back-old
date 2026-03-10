package com.cloud_technological.aura_pos.repositories.users;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.usuarios.UsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;


@Repository
@RequiredArgsConstructor
public class UsuarioQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PageImpl<UsuarioTableDto> paginar(PageableDto<Object> pageable,Integer empresaId) {

        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
                SELECT u.id,
                       u.username,
                       u.rol,
                       TRIM(CONCAT(COALESCE(t.nombres, ''), ' ', COALESCE(t.apellidos, ''))) AS nombre_completo,
                       t.numero_documento,
                       t.telefono,
                       u.activo,
                       COUNT(*) OVER() AS total_rows
                FROM usuario u
                LEFT JOIN tercero t ON t.id = u.tercero_id
                WHERE u.empresa_id = :empresaId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append( """
                    AND (
                        LOWER(u.username) LIKE :search
                        OR LOWER(t.nombres) LIKE :search
                        OR LOWER(t.apellidos) LIKE :search
                        OR LOWER(t.numero_documento) LIKE :search
                    )
                    """);
                    params.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY u.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);
        List<UsuarioTableDto> content = jdbc.query(sql.toString(), params,
                BeanPropertyRowMapper.newInstance(UsuarioTableDto.class));

        long total = content.isEmpty() ? 0 : content.get(0).getTotalRows();
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }

    public List<UsuarioDto.UsuarioSucursalDto> sucursalesDeUsuario(Integer usuarioId) {
        String sql = """
                SELECT s.id AS sucursal_id,
                       s.nombre AS sucursal_nombre,
                       us.es_default
                FROM usuario_sucursal us
                JOIN sucursal s ON s.id = us.sucursal_id
                WHERE us.usuario_id = :usuarioId
                  AND us.activo = TRUE
                ORDER BY us.es_default DESC, s.nombre
                """;
        return jdbc.query(sql,
                new MapSqlParameterSource("usuarioId", usuarioId),
                BeanPropertyRowMapper.newInstance(UsuarioDto.UsuarioSucursalDto.class));
    }
}
