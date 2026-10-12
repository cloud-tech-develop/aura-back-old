package com.cloud_technological.aura_pos.repositories.municipios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.municipios.MunicipioDto;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MunicipioQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Departamentos de Colombia, para el select que filtra los municipios.
     *
     * @return nombres de departamento ordenados
     */
    public List<String> departamentos() {
        return jdbc.queryForList("SELECT DISTINCT departamento FROM municipios ORDER BY departamento",
                new HashMap<String, Object>(), String.class);
    }

    /**
     * Municipios de un departamento, filtrados por nombre.
     *
     * @param departamento nombre exacto del departamento
     * @param search       texto a buscar en el nombre (vacío = todos)
     */
    public List<MunicipioDto> buscarEnDepartamento(String departamento, String search) {
        Map<String, Object> params = new HashMap<>();
        params.put("departamento", departamento);
        params.put("search", "%" + (search != null ? search : "") + "%");
        return jdbc.query("""
            SELECT id, codigo, nombre, departamento, nombre || ' - ' || departamento as label
              FROM municipios
             WHERE departamento = :departamento AND LOWER(nombre) LIKE LOWER(:search)
             ORDER BY nombre
            """, params, BeanPropertyRowMapper.newInstance(MunicipioDto.class));
    }

    public List<MunicipioDto> buscar(String search) {
        String sql = """
            SELECT id, codigo, nombre, departamento,
                   nombre || ' - ' || departamento as label
            FROM municipios
            WHERE LOWER(nombre) LIKE LOWER(:search)
               OR LOWER(departamento) LIKE LOWER(:search)
            ORDER BY nombre
            LIMIT 20
            """;
        
        Map<String, Object> params = new HashMap<>();
        params.put("search", "%" + search + "%");

        return jdbc.query(sql, params, BeanPropertyRowMapper.newInstance(MunicipioDto.class));
    }

    public MunicipioDto findById(Long id) {
        String sql = """
            SELECT id, codigo, nombre, departamento,
                   nombre || ' - ' || departamento as label
            FROM municipios
            WHERE id = :id
            """;
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        return jdbc.query(sql, params, BeanPropertyRowMapper.newInstance(MunicipioDto.class))
                .stream().findFirst().orElse(null);
    }

    /** Busca por código DANE (el que se manda a Factus/DIAN). */
    public MunicipioDto findByCodigo(String codigo) {
        String sql = """
            SELECT id, codigo, nombre, departamento,
                   nombre || ' - ' || departamento as label
            FROM municipios
            WHERE codigo = :codigo
            LIMIT 1
            """;
        Map<String, Object> params = new HashMap<>();
        params.put("codigo", codigo);
        return jdbc.query(sql, params, BeanPropertyRowMapper.newInstance(MunicipioDto.class))
                .stream().findFirst().orElse(null);
    }
}
