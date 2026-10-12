package com.cloud_technological.aura_pos.repositories.nomina;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.ExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.FamiliarDto;

import lombok.RequiredArgsConstructor;

/**
 * Consultas de la hoja de vida del empleado: cuentas con el nombre del banco,
 * pertenencia a la empresa y la copia de la cuenta principal al tercero.
 */
@Repository
@RequiredArgsConstructor
public class EmpleadoHojaVidaQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** ¿El empleado es de la empresa? */
    public boolean empleadoDeLaEmpresa(Long empleadoId, Integer empresaId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM empleados WHERE id = :id AND empresa_id = :empresaId",
                new MapSqlParameterSource("id", empleadoId).addValue("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /** Cuentas del empleado con el nombre del banco; la principal primero. */
    public List<CuentaBancariaDto> cuentas(Long empleadoId) {
        return jdbc.query("""
                SELECT c.id, c.banco_tercero_id, COALESCE(NULLIF(b.razon_social, ''), b.nombres) AS banco_nombre,
                       c.tipo_cuenta, c.numero_cuenta, c.principal, c.activa
                  FROM empleado_cuenta_bancaria c
                  LEFT JOIN tercero b ON b.id = c.banco_tercero_id
                 WHERE c.empleado_id = :id
                 ORDER BY c.principal DESC, c.activa DESC, c.id
                """, new MapSqlParameterSource("id", empleadoId), (rs, i) -> {
            CuentaBancariaDto d = new CuentaBancariaDto();
            d.setId(rs.getLong("id"));
            d.setBancoTerceroId(rs.getLong("banco_tercero_id"));
            d.setBancoNombre(rs.getString("banco_nombre"));
            d.setTipoCuenta(rs.getString("tipo_cuenta"));
            d.setNumeroCuenta(rs.getString("numero_cuenta"));
            d.setPrincipal(rs.getBoolean("principal"));
            d.setActiva(rs.getBoolean("activa"));
            return d;
        });
    }

    /** Familiares del empleado: primero el cónyuge/compañero, luego hijos y demás. */
    public List<FamiliarDto> familiares(Long empleadoId) {
        return jdbc.query("""
                SELECT id, parentesco, tipo_documento, numero_documento, nombre, fecha_nacimiento, genero,
                       telefono, ocupacion, convive, depende_economicamente, beneficiario_caja,
                       beneficiario_salud, contacto_emergencia
                  FROM empleado_familiar
                 WHERE empleado_id = :id
                 ORDER BY CASE parentesco WHEN 'CONYUGE' THEN 0 WHEN 'COMPANERO' THEN 0 WHEN 'HIJO' THEN 1 ELSE 2 END,
                          fecha_nacimiento NULLS LAST, id
                """, new MapSqlParameterSource("id", empleadoId), BeanPropertyRowMapper.newInstance(FamiliarDto.class));
    }

    /** Historial laboral del empleado, del más reciente al más antiguo. */
    public List<ExperienciaDto> experiencia(Long empleadoId) {
        return jdbc.query("""
                SELECT id, empresa, cargo, fecha_inicio, fecha_fin, motivo_retiro, jefe_inmediato, telefono, funciones
                  FROM empleado_experiencia
                 WHERE empleado_id = :id
                 ORDER BY fecha_inicio DESC, id DESC
                """, new MapSqlParameterSource("id", empleadoId), BeanPropertyRowMapper.newInstance(ExperienciaDto.class));
    }

    /**
     * Nombre del banco si el tercero es de la empresa y tiene rol BANCO.
     *
     * @return nombre, o vacío si no es un banco válido
     */
    public Optional<String> nombreBanco(Long bancoTerceroId, Integer empresaId) {
        return jdbc.query("""
                SELECT COALESCE(NULLIF(razon_social, ''), nombres) FROM tercero
                 WHERE id = :id AND empresa_id = :empresaId AND COALESCE(es_banco, FALSE) AND deleted_at IS NULL
                """, new MapSqlParameterSource("id", bancoTerceroId).addValue("empresaId", empresaId),
                (rs, i) -> rs.getString(1)).stream().findFirst();
    }

    /** Deja como única principal la cuenta indicada. */
    public void marcarUnicaPrincipal(Long empleadoId, Long cuentaId) {
        jdbc.update("""
                UPDATE empleado_cuenta_bancaria SET principal = (id = :cuentaId), updated_at = now()
                 WHERE empleado_id = :empleadoId
                """, new MapSqlParameterSource("empleadoId", empleadoId).addValue("cuentaId", cuentaId));
    }

    /** Si no queda ninguna principal activa, la primera activa pasa a serlo. */
    public void asegurarPrincipal(Long empleadoId) {
        jdbc.update("""
                UPDATE empleado_cuenta_bancaria SET principal = TRUE, updated_at = now()
                 WHERE id = (SELECT id FROM empleado_cuenta_bancaria
                              WHERE empleado_id = :id AND activa ORDER BY id LIMIT 1)
                   AND NOT EXISTS (SELECT 1 FROM empleado_cuenta_bancaria
                                    WHERE empleado_id = :id AND principal AND activa)
                """, new MapSqlParameterSource("id", empleadoId));
    }

    /**
     * Después de editar el tercero de un empleado: vuelve a copiarle la cuenta principal
     * de sus empleados que manejan cuentas en la hoja de vida. Así el formulario de datos
     * personales (que no muestra el banco) no pisa la cuenta con datos viejos.
     */
    public void resincronizarCuentaDelTercero(Long terceroId) {
        List<Long> empleados = jdbc.queryForList("""
                SELECT e.id FROM empleados e
                 WHERE e.tercero_id = :t
                   AND EXISTS (SELECT 1 FROM empleado_cuenta_bancaria c WHERE c.empleado_id = e.id)
                """, new MapSqlParameterSource("t", terceroId), Long.class);
        empleados.forEach(this::copiarPrincipalAlTercero);
    }

    /**
     * Copia la cuenta principal activa al tercero del empleado (de ahí leen el pago y
     * la nómina electrónica). Sin principal, deja los datos bancarios del tercero vacíos.
     */
    public void copiarPrincipalAlTercero(Long empleadoId) {
        jdbc.update("""
                UPDATE tercero t
                   SET banco_tercero_id = p.banco_tercero_id, banco = p.banco_nombre,
                       tipo_cuenta = p.tipo_cuenta, numero_cuenta = p.numero_cuenta, updated_at = now()
                  FROM empleados e
                  LEFT JOIN LATERAL (
                       SELECT c.banco_tercero_id, COALESCE(NULLIF(b.razon_social, ''), b.nombres) AS banco_nombre,
                              c.tipo_cuenta, c.numero_cuenta
                         FROM empleado_cuenta_bancaria c
                         LEFT JOIN tercero b ON b.id = c.banco_tercero_id
                        WHERE c.empleado_id = e.id AND c.principal AND c.activa
                        LIMIT 1) p ON TRUE
                 WHERE e.id = :id AND t.id = e.tercero_id
                """, new MapSqlParameterSource("id", empleadoId));
    }
}
