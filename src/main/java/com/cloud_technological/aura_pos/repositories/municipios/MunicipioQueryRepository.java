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
}
