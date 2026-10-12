package com.cloud_technological.aura_pos.repositories.nomina_tipos_comprobante;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/** Consultas de tipos de comprobante de nómina (listados, selects, validaciones, vínculos y semilla). */
@Repository
@RequiredArgsConstructor
public class NominaTipoComprobanteQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String COLUMNAS = """
            SELECT t.id, t.codigo, t.nombre, t.clase, t.prefijo, t.frecuencia, t.descripcion, t.activo,
                   (SELECT string_agg(x.tipo_vinculacion_id::TEXT, ',' ORDER BY x.tipo_vinculacion_id)
                      FROM nomina_tipo_comprobante_vinculacion x
                     WHERE x.tipo_comprobante_id = t.id) AS vinculacion_ids,
                   (SELECT string_agg(v.nombre, ', ' ORDER BY v.nombre)
                      FROM nomina_tipo_comprobante_vinculacion x
                      JOIN nomina_tipo_vinculacion v ON v.id = x.tipo_vinculacion_id
                     WHERE x.tipo_comprobante_id = t.id) AS vinculacion_nombres,
                   COUNT(*) OVER() AS total_rows
              FROM nomina_tipo_comprobante t
            """;

    /**
     * Listado paginado con búsqueda por código, nombre o prefijo.
     *
     * @param pageable  página, filas y texto de búsqueda
     * @param empresaId empresa
     * @return página ordenada por código
     */
    public PageImpl<TipoComprobanteTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder(COLUMNAS)
                .append(" WHERE t.empresa_id = :empresaId AND t.deleted_at IS NULL ");
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(t.nombre) LIKE :search OR LOWER(t.codigo) LIKE :search")
                    .append(" OR LOWER(t.prefijo) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY t.codigo OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<TipoComprobanteTableDto> lista = jdbc.query(sql.toString(), params, this::mapear);
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /**
     * Tipos activos para selects.
     *
     * @param empresaId empresa
     * @return activos ordenados por nombre
     */
    public List<TipoComprobanteTableDto> activos(Integer empresaId) {
        return jdbc.query(COLUMNAS + """
                 WHERE t.empresa_id = :empresaId AND t.activo = TRUE AND t.deleted_at IS NULL
                 ORDER BY t.nombre
                """, new MapSqlParameterSource("empresaId", empresaId), this::mapear);
    }

    /**
     * Un tipo con sus tipos de vinculación.
     *
     * @return el tipo, o vacío si no es de la empresa o está borrado
     */
    public Optional<TipoComprobanteTableDto> obtener(Long id, Integer empresaId) {
        return jdbc.query(COLUMNAS + " WHERE t.id = :id AND t.empresa_id = :empresaId AND t.deleted_at IS NULL ",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId), this::mapear)
                .stream().findFirst();
    }

    /**
     * ¿Ya existe ese valor en la columna (código o prefijo) dentro de la empresa? Sin
     * contar el tipo que se edita.
     *
     * @param columna   {@code codigo} o {@code prefijo} (nunca entrada del usuario)
     * @param excluirId id a ignorar (null al crear)
     */
    public boolean existe(Integer empresaId, String columna, String valor, Long excluirId) {
        if (!"codigo".equals(columna) && !"prefijo".equals(columna))
            throw new IllegalArgumentException("Columna no permitida: " + columna);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM nomina_tipo_comprobante"
                + " WHERE empresa_id = :empresaId AND LOWER(" + columna + ") = LOWER(:valor)"
                + " AND deleted_at IS NULL AND (CAST(:excluir AS BIGINT) IS NULL OR id <> :excluir)",
                new MapSqlParameterSource("empresaId", empresaId).addValue("valor", valor)
                        .addValue("excluir", excluirId), Long.class);
        return n != null && n > 0;
    }

    /**
     * Cuántos de esos tipos de vinculación son de la empresa y están vigentes.
     */
    public int contarTiposVinculacion(Integer empresaId, List<Long> ids) {
        if (ids.isEmpty()) return 0;
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_tipo_vinculacion
                 WHERE empresa_id = :empresaId AND deleted_at IS NULL AND id IN (:ids)
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("ids", ids), Integer.class);
        return n != null ? n : 0;
    }

    /**
     * Reemplaza los tipos de vinculación admitidos por el comprobante.
     *
     * @param ids tipos de vinculación (ya validados); vacío = todos
     */
    public void reemplazarTiposVinculacion(Long tipoComprobanteId, List<Long> ids) {
        MapSqlParameterSource params = new MapSqlParameterSource("tipo", tipoComprobanteId);
        jdbc.update("DELETE FROM nomina_tipo_comprobante_vinculacion WHERE tipo_comprobante_id = :tipo", params);
        if (ids.isEmpty()) return;
        MapSqlParameterSource[] filas = ids.stream()
                .map(id -> new MapSqlParameterSource("tipo", tipoComprobanteId).addValue("vinculacion", id))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO nomina_tipo_comprobante_vinculacion (tipo_comprobante_id, tipo_vinculacion_id)
                VALUES (:tipo, :vinculacion)
                """, filas);
    }

    /** ¿La empresa ya tiene tipos de comprobante (vigentes)? Si no, se le siembran. */
    public boolean tieneElementos(Integer empresaId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_tipo_comprobante
                 WHERE empresa_id = :empresaId AND deleted_at IS NULL
                """, new MapSqlParameterSource("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /**
     * Inserta un tipo inicial si el código no existe (tolera dos siembras a la vez).
     */
    public void sembrar(Integer empresaId, String codigo, String nombre, String clase, String prefijo,
            String frecuencia) {
        jdbc.update("""
                INSERT INTO nomina_tipo_comprobante (empresa_id, codigo, nombre, clase, prefijo, frecuencia,
                       activo, created_at)
                SELECT :empresaId, :codigo, :nombre, :clase, :prefijo, :frecuencia, TRUE, now()
                 WHERE NOT EXISTS (SELECT 1 FROM nomina_tipo_comprobante
                                    WHERE empresa_id = :empresaId AND deleted_at IS NULL
                                      AND (codigo = :codigo OR prefijo = :prefijo))
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("codigo", codigo)
                .addValue("nombre", nombre).addValue("clase", clase).addValue("prefijo", prefijo)
                .addValue("frecuencia", frecuencia));
    }

    private TipoComprobanteTableDto mapear(ResultSet rs, int fila) throws SQLException {
        TipoComprobanteTableDto dto = new TipoComprobanteTableDto();
        dto.setId(rs.getLong("id"));
        dto.setCodigo(rs.getString("codigo"));
        dto.setNombre(rs.getString("nombre"));
        dto.setClase(rs.getString("clase"));
        dto.setPrefijo(rs.getString("prefijo"));
        dto.setFrecuencia(rs.getString("frecuencia"));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setActivo(rs.getBoolean("activo"));
        dto.setTiposVinculacionIds(ids(rs.getString("vinculacion_ids")));
        dto.setTiposVinculacionNombres(rs.getString("vinculacion_nombres"));
        dto.setTotalRows(rs.getLong("total_rows"));
        return dto;
    }

    /** "3,5,8" → [3, 5, 8]; null → lista vacía. */
    private static List<Long> ids(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(csv.split(",")).map(Long::valueOf).toList());
    }
}
