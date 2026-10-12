package com.cloud_technological.aura_pos.repositories.nomina_vinculaciones;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.vinculacion.FiltroVinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vinculacion.VinculacionTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/**
 * Consultas de vinculaciones ({@code contrato_laboral} con sus catálogos):
 * listado, detalle con nombres, consecutivo y validaciones de pertenencia.
 */
@Repository
@RequiredArgsConstructor
public class VinculacionQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Listado paginado con búsqueda por código, empleado (nombre o documento) o cargo,
     * y filtros por estado, tipo de vinculación, cargo y grupo.
     *
     * @param pageable  página, filas, búsqueda y filtros en {@code params}
     * @param empresaId empresa
     * @return página ordenada por código descendente (las más recientes primero)
     */
    public PageImpl<VinculacionTableDto> listar(PageableDto<FiltroVinculacionDto> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        FiltroVinculacionDto f = pageable.getParams() != null ? pageable.getParams() : new FiltroVinculacionDto();

        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.codigo, c.empleado_id,
                       CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre, e.numero_documento,
                       tv.nombre AS tipo_vinculacion_nombre, COALESCE(k.nombre, c.cargo) AS cargo_nombre,
                       g.nombre AS grupo_nombre, c.tipo_contrato, c.tipo_remuneracion, c.salario_base,
                       c.fecha_inicio, c.fecha_fin, c.fecha_fin_prueba, c.es_principal, c.estado,
                       COUNT(*) OVER() AS total_rows
                  FROM contrato_laboral c
                  JOIN empleados e ON e.id = c.empleado_id
                  LEFT JOIN nomina_tipo_vinculacion tv ON tv.id = c.tipo_vinculacion_id
                  LEFT JOIN nomina_cargo k ON k.id = c.cargo_id
                  LEFT JOIN nomina_catalogo g ON g.id = c.grupo_nomina_id
                 WHERE c.empresa_id = :empresaId AND c.deleted_at IS NULL
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append("""
                     AND (LOWER(COALESCE(c.codigo, '')) LIKE :search
                          OR LOWER(CONCAT(e.nombres, ' ', e.apellidos)) LIKE :search
                          OR e.numero_documento LIKE :search
                          OR LOWER(COALESCE(k.nombre, c.cargo, '')) LIKE :search)
                    """);
            params.addValue("search", "%" + search + "%");
        }
        if (f.getEstado() != null) {
            sql.append(" AND c.estado = :estado ");
            params.addValue("estado", f.getEstado());
        }
        if (f.getTipoVinculacionId() != null) {
            sql.append(" AND c.tipo_vinculacion_id = :tipoId ");
            params.addValue("tipoId", f.getTipoVinculacionId());
        }
        if (f.getCargoId() != null) {
            sql.append(" AND c.cargo_id = :cargoId ");
            params.addValue("cargoId", f.getCargoId());
        }
        if (f.getGrupoNominaId() != null) {
            sql.append(" AND c.grupo_nomina_id = :grupoId ");
            params.addValue("grupoId", f.getGrupoNominaId());
        }
        sql.append(" ORDER BY c.codigo DESC NULLS LAST, c.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<VinculacionTableDto> lista = jdbc.query(sql.toString(), params, this::mapearFila);
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /**
     * Detalle de una vinculación con los nombres de empleado, catálogos, sede, jefe y evaluador.
     *
     * @return la vinculación, o vacío si no es de la empresa o está borrada
     */
    public Optional<VinculacionDto> obtener(Long id, Integer empresaId) {
        return jdbc.query("""
                SELECT c.*, CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre, e.numero_documento,
                       e.tercero_id AS empleado_tercero_id, j.tercero_id AS jefe_tercero_id,
                       ev.tercero_id AS evaluador_tercero_id,
                       tv.nombre AS tipo_vinculacion_nombre, COALESCE(k.nombre, c.cargo) AS cargo_nombre,
                       g.nombre AS grupo_nombre, cal.nombre AS calendario_nombre, arl.nombre AS riesgo_nombre,
                       s.nombre AS sucursal_nombre,
                       CONCAT(j.nombres, ' ', j.apellidos) AS jefe_nombre,
                       CONCAT(ev.nombres, ' ', ev.apellidos) AS evaluador_nombre
                  FROM contrato_laboral c
                  JOIN empleados e ON e.id = c.empleado_id
                  LEFT JOIN nomina_tipo_vinculacion tv ON tv.id = c.tipo_vinculacion_id
                  LEFT JOIN nomina_cargo k ON k.id = c.cargo_id
                  LEFT JOIN nomina_catalogo g ON g.id = c.grupo_nomina_id
                  LEFT JOIN nomina_catalogo cal ON cal.id = c.calendario_id
                  LEFT JOIN nomina_catalogo arl ON arl.id = c.riesgo_arl_id
                  LEFT JOIN sucursal s ON s.id = c.sucursal_id
                  LEFT JOIN empleados j ON j.id = c.jefe_directo_id
                  LEFT JOIN empleados ev ON ev.id = c.evaluador_id
                 WHERE c.id = :id AND c.empresa_id = :empresaId AND c.deleted_at IS NULL
                """, new MapSqlParameterSource("id", id).addValue("empresaId", empresaId), this::mapearDetalle)
                .stream().findFirst();
    }

    /**
     * Siguiente consecutivo de vinculación de la empresa (VIN-00001, VIN-00002…).
     * El índice único (empresa, código) frena dos altas simultáneas.
     */
    public String siguienteCodigo(Integer empresaId) {
        Long ultimo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(codigo FROM 5) AS BIGINT)), 0)
                  FROM contrato_laboral
                 WHERE empresa_id = :empresaId AND codigo ~ '^VIN-[0-9]+$'
                """, new MapSqlParameterSource("empresaId", empresaId), Long.class);
        return String.format("VIN-%05d", (ultimo != null ? ultimo : 0) + 1);
    }

    /**
     * ¿El empleado ya tiene otra vinculación activa de ese tipo? (tipos "únicos por empleado").
     *
     * @param excluirId vinculación que se edita (null al crear)
     */
    public boolean tieneOtraActivaDelTipo(Long empleadoId, Long tipoVinculacionId, Long excluirId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM contrato_laboral
                 WHERE empleado_id = :empleadoId AND tipo_vinculacion_id = :tipoId
                   AND estado <> 'TERMINADO' AND deleted_at IS NULL
                   AND (CAST(:excluir AS BIGINT) IS NULL OR id <> :excluir)
                """, new MapSqlParameterSource("empleadoId", empleadoId).addValue("tipoId", tipoVinculacionId)
                .addValue("excluir", excluirId), Long.class);
        return n != null && n > 0;
    }

    /**
     * Vinculaciones vigentes (no terminadas) en un cargo, sin contar la que se edita.
     * Se compara contra las plazas del cargo.
     */
    public int ocupadasEnCargo(Long cargoId, Long excluirId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM contrato_laboral
                 WHERE cargo_id = :cargoId AND estado <> 'TERMINADO' AND deleted_at IS NULL
                   AND (CAST(:excluir AS BIGINT) IS NULL OR id <> :excluir)
                """, new MapSqlParameterSource("cargoId", cargoId).addValue("excluir", excluirId), Integer.class);
        return n != null ? n : 0;
    }

    /**
     * Ficha de empleado de un tercero en la empresa (el buscador de terceros entrega el
     * tercero; la vinculación guarda el empleado).
     *
     * @return id del empleado, o vacío si el tercero no tiene ficha de empleado
     */
    public Optional<Long> empleadoPorTercero(Long terceroId, Integer empresaId) {
        return jdbc.query("""
                SELECT id FROM empleados WHERE tercero_id = :terceroId AND empresa_id = :empresaId
                 ORDER BY id DESC LIMIT 1
                """, new MapSqlParameterSource("terceroId", terceroId).addValue("empresaId", empresaId),
                (rs, i) -> rs.getLong(1)).stream().findFirst();
    }

    /** ¿La sede es de la empresa? */
    public boolean sucursalDeLaEmpresa(Integer sucursalId, Integer empresaId) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sucursal WHERE id = :id AND empresa_id = :empresaId
                """, new MapSqlParameterSource("id", sucursalId).addValue("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /**
     * Horas semanales de la jornada legal vigente en una fecha (Ley 2101: 47 → 46 → 44 → 42),
     * según la configuración de jornada de la empresa o la general.
     *
     * @return horas legales, o vacío si no hay configuración
     */
    public Optional<BigDecimal> jornadaLegal(Integer empresaId, LocalDate fecha) {
        return jdbc.query("""
                SELECT horas_semanales_legales FROM jornada_laboral_config
                 WHERE (empresa_id = :empresaId OR empresa_id IS NULL)
                   AND fecha_inicio_vigencia <= :fecha
                   AND (fecha_fin_vigencia IS NULL OR fecha_fin_vigencia >= :fecha)
                 ORDER BY empresa_id NULLS LAST, fecha_inicio_vigencia DESC
                 LIMIT 1
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("fecha", fecha),
                (rs, i) -> rs.getBigDecimal(1)).stream().findFirst();
    }

    private VinculacionTableDto mapearFila(ResultSet rs, int fila) throws SQLException {
        VinculacionTableDto d = new VinculacionTableDto();
        d.setId(rs.getLong("id"));
        d.setCodigo(rs.getString("codigo"));
        d.setEmpleadoId(rs.getLong("empleado_id"));
        d.setEmpleadoNombre(rs.getString("empleado_nombre"));
        d.setEmpleadoDocumento(rs.getString("numero_documento"));
        d.setTipoVinculacionNombre(rs.getString("tipo_vinculacion_nombre"));
        d.setCargoNombre(rs.getString("cargo_nombre"));
        d.setGrupoNominaNombre(rs.getString("grupo_nombre"));
        d.setTipoContrato(rs.getString("tipo_contrato"));
        d.setTipoRemuneracion(rs.getString("tipo_remuneracion"));
        d.setSalarioBase(rs.getBigDecimal("salario_base"));
        d.setFechaInicio(rs.getObject("fecha_inicio", LocalDate.class));
        d.setFechaFin(rs.getObject("fecha_fin", LocalDate.class));
        d.setFechaFinPrueba(rs.getObject("fecha_fin_prueba", LocalDate.class));
        d.setEsPrincipal(rs.getBoolean("es_principal"));
        d.setEstado(rs.getString("estado"));
        d.setTotalRows(rs.getLong("total_rows"));
        return d;
    }

    private VinculacionDto mapearDetalle(ResultSet rs, int fila) throws SQLException {
        VinculacionDto d = new VinculacionDto();
        d.setId(rs.getLong("id"));
        d.setCodigo(rs.getString("codigo"));
        d.setEstado(rs.getString("estado"));
        d.setEmpleadoId(rs.getLong("empleado_id"));
        d.setTerceroId(rs.getObject("empleado_tercero_id", Long.class));
        d.setEmpleadoNombre(rs.getString("empleado_nombre"));
        d.setEmpleadoDocumento(rs.getString("numero_documento"));
        d.setTipoVinculacionId(rs.getObject("tipo_vinculacion_id", Long.class));
        d.setTipoVinculacionNombre(rs.getString("tipo_vinculacion_nombre"));
        d.setCargoId(rs.getObject("cargo_id", Long.class));
        d.setCargoNombre(rs.getString("cargo_nombre"));
        d.setGrupoNominaId(rs.getObject("grupo_nomina_id", Long.class));
        d.setGrupoNominaNombre(rs.getString("grupo_nombre"));
        d.setTipoContrato(rs.getString("tipo_contrato"));
        d.setTemporal(rs.getBoolean("temporal"));
        d.setTipoRemuneracion(rs.getString("tipo_remuneracion"));
        d.setSalarioBase(rs.getBigDecimal("salario_base"));
        d.setFrecuenciaPago(rs.getString("periodicidad"));
        d.setHorasSemana(rs.getBigDecimal("horas_semana"));
        d.setCalendarioId(rs.getObject("calendario_id", Long.class));
        d.setCalendarioNombre(rs.getString("calendario_nombre"));
        d.setFechaInicio(rs.getObject("fecha_inicio", LocalDate.class));
        d.setFechaFin(rs.getObject("fecha_fin", LocalDate.class));
        d.setFechaFinPrueba(rs.getObject("fecha_fin_prueba", LocalDate.class));
        d.setFase(rs.getString("fase"));
        d.setEsPrincipal(rs.getBoolean("es_principal"));
        d.setPrimerEmpleo(rs.getBoolean("primer_empleo"));
        d.setSucursalId(rs.getObject("sucursal_id", Integer.class));
        d.setSucursalNombre(rs.getString("sucursal_nombre"));
        d.setArea(rs.getString("area"));
        d.setMunicipioTrabajo(rs.getString("municipio_trabajo"));
        d.setMunicipioTrabajoId(rs.getObject("municipio_trabajo_id", Long.class));
        d.setDireccionTrabajo(rs.getString("direccion_trabajo"));
        d.setTelefonoTrabajo(rs.getString("telefono_trabajo"));
        d.setJefeDirectoId(rs.getObject("jefe_directo_id", Long.class));
        d.setJefeDirectoTerceroId(rs.getObject("jefe_tercero_id", Long.class));
        d.setJefeDirectoNombre(d.getJefeDirectoId() != null ? rs.getString("jefe_nombre") : null);
        d.setEvaluadorId(rs.getObject("evaluador_id", Long.class));
        d.setEvaluadorTerceroId(rs.getObject("evaluador_tercero_id", Long.class));
        d.setEvaluadorNombre(d.getEvaluadorId() != null ? rs.getString("evaluador_nombre") : null);
        d.setTipoCotizante(rs.getString("tipo_cotizante"));
        d.setSubtipoCotizante(rs.getString("subtipo_cotizante"));
        d.setRiesgoArlId(rs.getObject("riesgo_arl_id", Long.class));
        d.setRiesgoArlNombre(rs.getString("riesgo_nombre"));
        d.setEnExtranjero(rs.getBoolean("en_extranjero"));
        d.setDocInternoTipo(rs.getString("doc_interno_tipo"));
        d.setDocInternoNumero(rs.getString("doc_interno_numero"));
        d.setDocInternoFecha(rs.getObject("doc_interno_fecha", LocalDate.class));
        d.setDocInternoUrl(rs.getString("doc_interno_url"));
        d.setDocExternoUrl(rs.getString("doc_externo_url"));
        d.setCausaRetiro(rs.getString("causa_retiro"));
        d.setObservacion(rs.getString("observacion"));
        return d;
    }
}
