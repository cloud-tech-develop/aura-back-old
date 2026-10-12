package com.cloud_technological.aura_pos.repositories.nomina_tipos_vinculacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.TipoVinculacionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/** Consultas de tipos de vinculación (listados, selects, validaciones y semilla). */
@Repository
@RequiredArgsConstructor
public class NominaTipoVinculacionQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String COLUMNAS = """
            SELECT v.id, v.codigo, v.nombre, v.tipo_contrato, v.tipo_remuneracion, v.frecuencia_pago,
                   v.exige_fecha_fin, v.unica_por_empleado, v.descripcion, v.activo,
                   COUNT(*) OVER() AS total_rows
              FROM nomina_tipo_vinculacion v
            """;

    /**
     * Listado paginado con búsqueda por código o nombre.
     *
     * @param pageable  página, filas y texto de búsqueda
     * @param empresaId empresa
     * @return página ordenada por código
     */
    public PageImpl<TipoVinculacionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder(COLUMNAS)
                .append(" WHERE v.empresa_id = :empresaId AND v.deleted_at IS NULL ");
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(v.nombre) LIKE :search OR LOWER(v.codigo) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY v.codigo OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<TipoVinculacionTableDto> lista = jdbc.query(sql.toString(), params, this::mapear);
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /**
     * Tipos activos para selects.
     *
     * @param empresaId empresa
     * @return activos ordenados por nombre
     */
    public List<TipoVinculacionTableDto> activos(Integer empresaId) {
        return jdbc.query(COLUMNAS + """
                 WHERE v.empresa_id = :empresaId AND v.activo = TRUE AND v.deleted_at IS NULL
                 ORDER BY v.nombre
                """, new MapSqlParameterSource("empresaId", empresaId), this::mapear);
    }

    /**
     * ¿Ya existe el código en la empresa? (sin contar el tipo que se edita).
     *
     * @param excluirId id a ignorar (null al crear)
     */
    public boolean existeCodigo(Integer empresaId, String codigo, Long excluirId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_tipo_vinculacion
                 WHERE empresa_id = :empresaId AND LOWER(codigo) = LOWER(:codigo)
                   AND deleted_at IS NULL AND (CAST(:excluir AS BIGINT) IS NULL OR id <> :excluir)
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("codigo", codigo)
                .addValue("excluir", excluirId), Long.class);
        return n != null && n > 0;
    }

    /** ¿La empresa ya tiene tipos de vinculación (vigentes)? Si no, se le siembran. */
    public boolean tieneElementos(Integer empresaId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_tipo_vinculacion
                 WHERE empresa_id = :empresaId AND deleted_at IS NULL
                """, new MapSqlParameterSource("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /**
     * Inserta un tipo inicial si el código no existe (tolera dos siembras a la vez).
     */
    public void sembrar(Integer empresaId, String codigo, String nombre, String tipoContrato,
            String tipoRemuneracion, String frecuenciaPago, boolean exigeFechaFin) {
        jdbc.update("""
                INSERT INTO nomina_tipo_vinculacion (empresa_id, codigo, nombre, tipo_contrato, tipo_remuneracion,
                       frecuencia_pago, exige_fecha_fin, unica_por_empleado, activo, created_at)
                SELECT :empresaId, :codigo, :nombre, :tipoContrato, :tipoRemuneracion,
                       :frecuenciaPago, :exigeFechaFin, TRUE, TRUE, now()
                 WHERE NOT EXISTS (SELECT 1 FROM nomina_tipo_vinculacion
                                    WHERE empresa_id = :empresaId AND codigo = :codigo AND deleted_at IS NULL)
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("codigo", codigo)
                .addValue("nombre", nombre).addValue("tipoContrato", tipoContrato)
                .addValue("tipoRemuneracion", tipoRemuneracion).addValue("frecuenciaPago", frecuenciaPago)
                .addValue("exigeFechaFin", exigeFechaFin));
    }

    private TipoVinculacionTableDto mapear(ResultSet rs, int fila) throws SQLException {
        TipoVinculacionTableDto dto = new TipoVinculacionTableDto();
        dto.setId(rs.getLong("id"));
        dto.setCodigo(rs.getString("codigo"));
        dto.setNombre(rs.getString("nombre"));
        dto.setTipoContrato(rs.getString("tipo_contrato"));
        dto.setTipoRemuneracion(rs.getString("tipo_remuneracion"));
        dto.setFrecuenciaPago(rs.getString("frecuencia_pago"));
        dto.setExigeFechaFin(rs.getBoolean("exige_fecha_fin"));
        dto.setUnicaPorEmpleado(rs.getBoolean("unica_por_empleado"));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setActivo(rs.getBoolean("activo"));
        dto.setTotalRows(rs.getLong("total_rows"));
        return dto;
    }
}
