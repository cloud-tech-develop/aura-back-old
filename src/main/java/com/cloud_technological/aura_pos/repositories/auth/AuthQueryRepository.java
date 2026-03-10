package com.cloud_technological.aura_pos.repositories.auth;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.auth.SucursalSimpleDto;
import com.cloud_technological.aura_pos.utils.MapperRepository;

@Repository
public class AuthQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    /**
     * Obtiene las sucursales habilitadas para un usuario específico.
     * Hace el JOIN entre usuario_sucursal y sucursal.
     */
    public List<SucursalSimpleDto> findSucursalesByUsuario(Integer usuarioId) {
        String sql = """
            SELECT 
                s.id, 
                s.nombre, 
                us.es_default
            FROM usuario_sucursal us
            INNER JOIN sucursal s ON s.id = us.sucursal_id
            WHERE us.usuario_id = :usuarioId 
              AND us.activo = true 
              AND s.activa = true
        """;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("usuarioId", usuarioId);

        List<Map<String, Object>> resultList = namedParameterJdbcTemplate.queryForList(sql, params);
        
        // Usamos TU MapperRepository para convertir la lista de mapas a DTOs
        return MapperRepository.mapListToDtoList(resultList, SucursalSimpleDto.class);
    }
    
    /**
     * Obtiene info extra del usuario que JPA a veces complica,
     * como el nombre de la empresa o configuración extra (opcional)
     */
    public Map<String, Object> findEmpresaInfo(Integer usuarioId) {
         String sql = """
            SELECT e.razon_social, e.logo_url
            FROM usuario u
            JOIN empresa e ON e.id = u.empresa_id
            WHERE u.id = :usuarioId
        """;
         
         MapSqlParameterSource params = new MapSqlParameterSource();
         params.addValue("usuarioId", usuarioId);
         
         try {
             return namedParameterJdbcTemplate.queryForMap(sql, params);
         } catch (Exception e) {
             return null;
         }
    }
}
