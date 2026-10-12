package com.cloud_technological.aura_pos.repositories.nomina_cargos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.cargos.NominaCargoTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/** Consultas de cargos (listados con nivel y tipo, selects y validaciones). */
@Repository
@RequiredArgsConstructor
public class NominaCargoQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String COLUMNAS = """
            SELECT c.id, c.codigo, c.nombre, c.nivel_id, n.nombre AS nivel_nombre,
                   c.tipo_cargo_id, t.nombre AS tipo_cargo_nombre, c.plazas,
                   c.salario_referencia, c.salario_minimo, c.salario_maximo,
                   c.mision, c.funciones, c.activo,
                   (SELECT COUNT(*) FROM contrato_laboral v
                     WHERE v.cargo_id = c.id AND v.estado <> 'TERMINADO' AND v.deleted_at IS NULL) AS ocupadas,
                   COUNT(*) OVER() AS total_rows
              FROM nomina_cargo c
              LEFT JOIN nomina_catalogo n ON n.id = c.nivel_id
              LEFT JOIN nomina_catalogo t ON t.id = c.tipo_cargo_id
            """;

    /**
     * Listado paginado de cargos, con búsqueda por código, nombre, nivel o tipo.
     *
     * @param pageable  página, filas y texto de búsqueda
     * @param empresaId empresa
     * @return página de cargos ordenados por nombre
     */
    public PageImpl<NominaCargoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder(COLUMNAS)
                .append(" WHERE c.empresa_id = :empresaId AND c.deleted_at IS NULL ");
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append("""
                     AND (LOWER(c.nombre) LIKE :search OR LOWER(c.codigo) LIKE :search
                          OR LOWER(COALESCE(n.nombre, '')) LIKE :search OR LOWER(COALESCE(t.nombre, '')) LIKE :search)
                    """);
            params.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY c.nombre OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<NominaCargoTableDto> lista = jdbc.query(sql.toString(), params, this::mapear);
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /**
     * Cargos activos para selects (vinculación, filtros de reportes).
     *
     * @param empresaId empresa
     * @return cargos activos ordenados por nombre
     */
    public List<NominaCargoTableDto> activos(Integer empresaId) {
        return jdbc.query(COLUMNAS + """
                 WHERE c.empresa_id = :empresaId AND c.activo = TRUE AND c.deleted_at IS NULL
                 ORDER BY c.nombre
                """, new MapSqlParameterSource("empresaId", empresaId), this::mapear);
    }

    /**
     * Un cargo con los nombres de nivel y tipo.
     *
     * @return el cargo, o vacío si no es de la empresa o está borrado
     */
    public Optional<NominaCargoTableDto> obtener(Long id, Integer empresaId) {
        return jdbc.query(COLUMNAS + " WHERE c.id = :id AND c.empresa_id = :empresaId AND c.deleted_at IS NULL ",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId), this::mapear)
                .stream().findFirst();
    }

    /**
     * ¿Ya existe el código en la empresa? (sin contar el cargo que se edita).
     *
     * @param excluirId id a ignorar (null al crear)
     */
    public boolean existeCodigo(Integer empresaId, String codigo, Long excluirId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_cargo
                 WHERE empresa_id = :empresaId AND LOWER(codigo) = LOWER(:codigo)
                   AND deleted_at IS NULL AND (CAST(:excluir AS BIGINT) IS NULL OR id <> :excluir)
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("codigo", codigo)
                .addValue("excluir", excluirId), Long.class);
        return n != null && n > 0;
    }

    /**
     * Cargo vigente con ese nombre (sin distinguir mayúsculas ni espacios de los bordes).
     *
     * @return el id del cargo, o vacío si no existe
     */
    public Optional<Long> idPorNombre(Integer empresaId, String nombre) {
        return jdbc.queryForList("""
                SELECT id FROM nomina_cargo
                 WHERE empresa_id = :empresaId AND deleted_at IS NULL
                   AND LOWER(nombre) = LOWER(TRIM(:nombre))
                 ORDER BY id LIMIT 1
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("nombre", nombre), Long.class)
                .stream().findFirst();
    }

    /**
     * Siguiente código para un cargo creado sin formulario (CAR-001, CAR-002…).
     */
    public String siguienteCodigoAutomatico(Integer empresaId) {
        Long ultimo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(codigo FROM 5) AS BIGINT)), 0)
                  FROM nomina_cargo
                 WHERE empresa_id = :empresaId AND codigo ~ '^CAR-[0-9]+$'
                """, new MapSqlParameterSource("empresaId", empresaId), Long.class);
        return String.format("CAR-%03d", (ultimo != null ? ultimo : 0) + 1);
    }

    private NominaCargoTableDto mapear(ResultSet rs, int fila) throws SQLException {
        NominaCargoTableDto dto = new NominaCargoTableDto();
        dto.setId(rs.getLong("id"));
        dto.setCodigo(rs.getString("codigo"));
        dto.setNombre(rs.getString("nombre"));
        dto.setNivelId(rs.getObject("nivel_id", Long.class));
        dto.setNivelNombre(rs.getString("nivel_nombre"));
        dto.setTipoCargoId(rs.getObject("tipo_cargo_id", Long.class));
        dto.setTipoCargoNombre(rs.getString("tipo_cargo_nombre"));
        dto.setPlazas(rs.getObject("plazas", Integer.class));
        dto.setOcupadas(rs.getInt("ocupadas"));
        dto.setSalarioReferencia(rs.getBigDecimal("salario_referencia"));
        dto.setSalarioMinimo(rs.getBigDecimal("salario_minimo"));
        dto.setSalarioMaximo(rs.getBigDecimal("salario_maximo"));
        dto.setMision(rs.getString("mision"));
        dto.setFunciones(rs.getString("funciones"));
        dto.setActivo(rs.getBoolean("activo"));
        dto.setTotalRows(rs.getLong("total_rows"));
        return dto;
    }
}
