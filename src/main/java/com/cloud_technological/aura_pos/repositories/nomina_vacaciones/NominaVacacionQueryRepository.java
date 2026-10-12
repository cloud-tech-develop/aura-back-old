package com.cloud_technological.aura_pos.repositories.nomina_vacaciones;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.vacaciones.FiltroVacacionDto;
import com.cloud_technological.aura_pos.dto.nomina.vacaciones.VacacionDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/** Consultas de vacaciones (rediseño R7): listado, saldo, cruces y lo que entra a la nómina. */
@Repository
@RequiredArgsConstructor
public class NominaVacacionQueryRepository {

    /** Vacaciones que caen en un período de nómina (para volverlas novedad). */
    public record EnPeriodo(Long id, String tipo, LocalDate fechaInicio, LocalDate fechaFin,
            LocalDate fechaRegresoAnticipado, BigDecimal diasHabiles, String estado) {
    }

    /** Vinculación candidata a vacaciones colectivas. */
    public record Vinculacion(Long contratoId, String empleado) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT v.*, c.codigo, CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre, e.numero_documento
              FROM nomina_vacacion v
              JOIN contrato_laboral c ON c.id = v.contrato_id
              JOIN empleados e ON e.id = v.empleado_id
            """;

    /**
     * Listado paginado, de las más recientes a las más antiguas.
     *
     * @param pageable búsqueda por empleado, documento o vinculación; filtros de estado y empleado
     */
    public PageImpl<VacacionDto> listar(PageableDto<FiltroVacacionDto> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        FiltroVacacionDto f = pageable.getParams() != null ? pageable.getParams() : new FiltroVacacionDto();

        StringBuilder sql = new StringBuilder("SELECT r.*, COUNT(*) OVER() AS total_rows FROM (")
                .append(SELECT).append(") r WHERE r.empresa_id = :empresaId ");
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(r.empleado_nombre) LIKE :search OR r.numero_documento LIKE :search")
                    .append(" OR LOWER(COALESCE(r.codigo, '')) LIKE :search OR LOWER(COALESCE(r.lote, '')) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }
        if (f.getEstado() != null) {
            sql.append(" AND r.estado = :estado ");
            params.addValue("estado", f.getEstado());
        }
        if (f.getEmpleadoId() != null) {
            sql.append(" AND r.empleado_id = :empleadoId ");
            params.addValue("empleadoId", f.getEmpleadoId());
        }
        sql.append(" ORDER BY r.fecha_inicio DESC, r.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<VacacionDto> lista = jdbc.query(sql.toString(), params, (rs, i) -> {
            VacacionDto dto = mapear(rs);
            dto.setTotalRows(rs.getLong("total_rows"));
            return dto;
        });
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /** Unas vacaciones; vacío si no son de la empresa. */
    public Optional<VacacionDto> obtener(Long id, Integer empresaId) {
        return jdbc.query("SELECT * FROM (" + SELECT + ") r WHERE r.id = :id AND r.empresa_id = :empresaId",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId), (rs, i) -> mapear(rs))
                .stream().findFirst();
    }

    /**
     * Días tomados en el módulo por el empleado: hábiles registrados menos los no
     * disfrutados. Cuentan también las programadas a futuro, para no darlas dos veces.
     */
    public BigDecimal diasTomados(Long empleadoId) {
        BigDecimal d = jdbc.queryForObject("""
                SELECT COALESCE(SUM(dias_habiles - dias_no_disfrutados), 0) FROM nomina_vacacion
                 WHERE empleado_id = :empleadoId AND estado <> 'ANULADA'
                """, new MapSqlParameterSource("empleadoId", empleadoId), BigDecimal.class);
        return d != null ? d : BigDecimal.ZERO;
    }

    /** Días ya compensados en dinero al empleado (para el tope de la mitad, CST art. 189). */
    public BigDecimal diasCompensados(Long empleadoId) {
        BigDecimal d = jdbc.queryForObject("""
                SELECT COALESCE(SUM(dias_habiles), 0) FROM nomina_vacacion
                 WHERE empleado_id = :empleadoId AND estado <> 'ANULADA' AND tipo = 'DINERO'
                """, new MapSqlParameterSource("empleadoId", empleadoId), BigDecimal.class);
        return d != null ? d : BigDecimal.ZERO;
    }

    /**
     * ¿La vinculación ya tiene vacaciones en tiempo que se cruzan con esas fechas?
     * Las interrumpidas cuentan solo hasta el día anterior al regreso.
     */
    public boolean seCruza(Long contratoId, LocalDate desde, LocalDate hasta) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina_vacacion
                 WHERE contrato_id = :contratoId AND tipo = 'TIEMPO' AND estado <> 'ANULADA'
                   AND fecha_inicio <= :hasta
                   AND COALESCE(fecha_regreso_anticipado - 1, fecha_fin) >= :desde
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("desde", desde)
                .addValue("hasta", hasta), Integer.class);
        return n != null && n > 0;
    }

    /**
     * ¿Ya hay una nómina aprobada o pagada de la vinculación cuyo período toca esas
     * fechas? Entonces las vacaciones de esos días ya no se pueden cambiar.
     */
    public boolean nominaCerradaEntre(Long contratoId, LocalDate desde, LocalDate hasta) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nomina n
                  JOIN periodo_nomina p ON p.id = n.periodo_id
                 WHERE n.contrato_id = :contratoId AND n.es_prenomina = FALSE
                   AND n.estado IN ('APROBADO', 'PAGADO')
                   AND p.fecha_inicio <= :hasta AND p.fecha_fin >= :desde
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("desde", desde)
                .addValue("hasta", hasta), Integer.class);
        return n != null && n > 0;
    }

    /**
     * Vacaciones de la vinculación que se pagan en el período: en tiempo, las que se
     * cruzan con él; en dinero, las que empiezan dentro de él.
     */
    public List<EnPeriodo> enPeriodo(Long contratoId, LocalDate desde, LocalDate hasta) {
        return jdbc.query("""
                SELECT id, tipo, fecha_inicio, fecha_fin, fecha_regreso_anticipado, dias_habiles, estado
                  FROM nomina_vacacion
                 WHERE contrato_id = :contratoId AND estado <> 'ANULADA'
                   AND ((tipo = 'TIEMPO' AND fecha_inicio <= :hasta
                         AND COALESCE(fecha_regreso_anticipado - 1, fecha_fin) >= :desde)
                     OR (tipo = 'DINERO' AND fecha_inicio BETWEEN :desde AND :hasta))
                 ORDER BY fecha_inicio
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("desde", desde)
                .addValue("hasta", hasta),
                (rs, i) -> new EnPeriodo(rs.getLong("id"), rs.getString("tipo"),
                        rs.getObject("fecha_inicio", LocalDate.class), rs.getObject("fecha_fin", LocalDate.class),
                        rs.getObject("fecha_regreso_anticipado", LocalDate.class), rs.getBigDecimal("dias_habiles"),
                        rs.getString("estado")));
    }

    /** Vinculaciones activas con derecho a vacaciones (sin prestación de servicios), por nombre. */
    public List<Vinculacion> vinculacionesActivas(Integer empresaId) {
        return jdbc.query("""
                SELECT c.id, CONCAT(e.nombres, ' ', e.apellidos) AS empleado
                  FROM contrato_laboral c JOIN empleados e ON e.id = c.empleado_id
                 WHERE c.empresa_id = :empresaId AND c.estado = 'ACTIVO' AND c.deleted_at IS NULL
                   AND c.tipo_contrato <> 'PRESTACION_SERVICIOS'
                 ORDER BY e.apellidos, e.nombres
                """, new MapSqlParameterSource("empresaId", empresaId),
                (rs, i) -> new Vinculacion(rs.getLong("id"), rs.getString("empleado")));
    }

    private static VacacionDto mapear(ResultSet rs) throws SQLException {
        VacacionDto dto = new VacacionDto();
        dto.setId(rs.getLong("id"));
        dto.setContratoId(rs.getLong("contrato_id"));
        dto.setCodigoVinculacion(rs.getString("codigo"));
        dto.setEmpleadoId(rs.getLong("empleado_id"));
        dto.setEmpleadoNombre(rs.getString("empleado_nombre"));
        dto.setEmpleadoDocumento(rs.getString("numero_documento"));
        dto.setTipo(rs.getString("tipo"));
        dto.setFechaInicio(rs.getObject("fecha_inicio", LocalDate.class));
        dto.setFechaFin(rs.getObject("fecha_fin", LocalDate.class));
        dto.setFechaRegreso(rs.getObject("fecha_regreso", LocalDate.class));
        dto.setDiasHabiles(rs.getBigDecimal("dias_habiles"));
        dto.setFechaRegresoAnticipado(rs.getObject("fecha_regreso_anticipado", LocalDate.class));
        dto.setDiasNoDisfrutados(rs.getBigDecimal("dias_no_disfrutados"));
        dto.setEstado(rs.getString("estado"));
        dto.setLote(rs.getString("lote"));
        dto.setObservacion(rs.getString("observacion"));
        dto.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return dto;
    }
}
