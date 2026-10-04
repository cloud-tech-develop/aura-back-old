package com.cloud_technological.aura_pos.repositories.bodegas;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.bodegas.BodegaDto;
import com.cloud_technological.aura_pos.dto.bodegas.BodegaTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class BodegaQueryRepository {
    /** Solo sus sedes, si el perfil no tiene todas (PLAN_PERMISOS P9). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.permisos.AlcanceSede alcanceSede;


    /**
     * Cuidado al tocar este SELECT: el RowMapper es por nombre de columna, así
     * que una columna agregada aquí y no declarada en {@link BodegaTableDto}
     * simplemente no viaja, sin error.
     */
    private static final String SELECT_TABLA = """
        SELECT
            b.id,
            b.codigo,
            b.nombre,
            b.sucursal_id,
            s.nombre AS sucursal_nombre,
            b.responsable_usuario_id,
            COALESCE(
                NULLIF(TRIM(COALESCE(t.nombres, '') || ' ' || COALESCE(t.apellidos, '')), ''),
                NULLIF(TRIM(COALESCE(t.razon_social, '')), ''),
                u.username
            ) AS responsable_nombre,
            b.es_principal,
            b.permite_venta,
            b.ubicacion,
            b.observacion,
            b.activa,
            (SELECT COUNT(*) FROM inventario i
              WHERE i.bodega_id = b.id AND i.stock_actual <> 0)          AS referencias,
            (SELECT COALESCE(SUM(i.stock_actual * COALESCE(p.costo, 0)), 0)
               FROM inventario i
               JOIN producto p ON p.id = i.producto_id
              WHERE i.bodega_id = b.id)                                  AS valor_inventario,
            COUNT(*) OVER() AS total_rows
        FROM bodega b
        JOIN sucursal s ON s.id = b.sucursal_id
        LEFT JOIN usuario u ON u.id = b.responsable_usuario_id
        LEFT JOIN tercero t ON t.id = u.tercero_id
        WHERE b.empresa_id = :empresaId /*SEDE:b.sucursal_id*/
        """;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public PageImpl<BodegaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder(SELECT_TABLA);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(b.nombre) LIKE :search
                  OR LOWER(COALESCE(b.codigo, '')) LIKE :search
                  OR LOWER(s.nombre) LIKE :search)
                """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY s.nombre ASC, b.es_principal DESC, b.nombre ASC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<BodegaTableDto> list = jdbc.query(alcanceSede.aplicar(sql.toString(), params), params,
                new BeanPropertyRowMapper<>(BodegaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public BodegaTableDto obtener(Long id, Integer empresaId) {
        String sql = SELECT_TABLA + " AND b.id = :id ";
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("id", id);
        return jdbc.query(alcanceSede.aplicar(sql, params), params, new BeanPropertyRowMapper<>(BodegaTableDto.class))
                .stream().findFirst().orElse(null);
    }

    /**
     * Combos. {@code sucursalId} nulo trae las de toda la empresa (traslados
     * entre sedes); {@code soloVenta} deja fuera averías y cuarentena.
     */
    public List<BodegaDto> list(Integer empresaId, Integer sucursalId, boolean soloVenta) {
        StringBuilder sql = new StringBuilder("""
            SELECT b.id, b.codigo, b.nombre, b.sucursal_id,
                   s.nombre AS sucursal_nombre, b.es_principal, b.permite_venta
            FROM bodega b
            JOIN sucursal s ON s.id = b.sucursal_id
            WHERE b.empresa_id = :empresaId
              AND b.activa = TRUE
            """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (sucursalId != null) {
            sql.append(" AND b.sucursal_id = :sucursalId ");
            params.addValue("sucursalId", sucursalId);
        }
        if (soloVenta) {
            sql.append(" AND b.permite_venta = TRUE ");
        }

        sql.append(" ORDER BY s.nombre ASC, b.es_principal DESC, b.nombre ASC ");
        return jdbc.query(alcanceSede.aplicar(sql.toString(), params), params, new BeanPropertyRowMapper<>(BodegaDto.class));
    }

    /** Si tiene saldo o movimientos no se puede borrar: solo desactivar. */
    public boolean tieneMovimiento(Long bodegaId) {
        String sql = """
            SELECT EXISTS (SELECT 1 FROM inventario WHERE bodega_id = :id AND stock_actual <> 0)
                OR EXISTS (SELECT 1 FROM movimiento_inventario WHERE bodega_id = :id)
            """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql,
                new MapSqlParameterSource("id", bodegaId), Boolean.class));
    }
}
