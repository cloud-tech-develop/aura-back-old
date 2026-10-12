package com.cloud_technological.aura_pos.repositories.nomina_incrementos;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.incremento.FiltroIncrementoDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoDetalleDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoDto;
import com.cloud_technological.aura_pos.services.nomina.documento.TipoListaNomina;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/** Consultas de incrementos salariales (R8): listado, vista previa, retroactivos. */
@Repository
@RequiredArgsConstructor
public class NominaIncrementoQueryRepository {

    /** Vinculación de la lista con su salario actual. */
    public record Vinculacion(Long contratoId, Long empleadoId, BigDecimal salario) {
    }

    /** Línea guardada del incremento (para aplicar y reversar). */
    public record Detalle(Long id, Long contratoId, BigDecimal salarioAnterior, BigDecimal salarioNuevo,
            Long historialId) {
    }

    /** Vinculación con nóminas confirmadas que piden retroactivo y hasta qué fecha. */
    public record Retroactivo(Long contratoId, LocalDate hasta) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Nóminas periódicas ya confirmadas (aprobadas o pagadas) de la vinculación con un
     * salario menor al nuevo, cuyo período termina en la fecha de aplicación o después.
     * Se usa como subconsulta con alias {@code x} y los parámetros del contexto.
     */
    private static final String NOMINAS_RETRO = " " + """
            FROM nomina x
            JOIN periodo_nomina px ON px.id = x.periodo_id
            LEFT JOIN nomina_documento dx ON dx.id = x.documento_id
            LEFT JOIN nomina_tipo_comprobante tx ON tx.id = dx.tipo_comprobante_id
            WHERE x.es_prenomina = FALSE AND x.estado IN ('APROBADO', 'PAGADO')
              AND (x.documento_id IS NULL OR tx.clase = 'PERIODICO')
            """;

    private static final String SELECT = """
            SELECT i.*, 'INC-' || LPAD(i.numero::TEXT, 5, '0') AS numero_completo,
                   CASE WHEN doc.id IS NULL THEN NULL
                        ELSE 'PRE-' || tc.prefijo || '-' || LPAD(doc.numero::TEXT, 5, '0') END AS documento_numero,
                   doc.estado AS documento_estado,
                   (SELECT COUNT(*) FROM nomina_incremento_detalle d WHERE d.incremento_id = i.id) AS empleados,
                   (SELECT COALESCE(SUM(d.salario_anterior), 0) FROM nomina_incremento_detalle d
                     WHERE d.incremento_id = i.id) AS total_anterior,
                   (SELECT COALESCE(SUM(d.salario_nuevo), 0) FROM nomina_incremento_detalle d
                     WHERE d.incremento_id = i.id) AS total_nuevo,
                   (SELECT COUNT(*) FROM nomina_incremento_detalle d
                     WHERE d.incremento_id = i.id AND EXISTS (SELECT 1 """ + NOMINAS_RETRO + """
                        AND x.contrato_id = d.contrato_id AND px.fecha_fin >= i.fecha_aplicacion
                        AND x.salario_base < d.salario_nuevo)) AS con_retroactivo
              FROM nomina_incremento i
              LEFT JOIN nomina_documento doc ON doc.id = i.documento_id
              LEFT JOIN nomina_tipo_comprobante tc ON tc.id = doc.tipo_comprobante_id
            """;

    /** Listado paginado, del más reciente al más antiguo. */
    public PageImpl<IncrementoDto> listar(PageableDto<FiltroIncrementoDto> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        FiltroIncrementoDto f = pageable.getParams() != null ? pageable.getParams() : new FiltroIncrementoDto();

        StringBuilder sql = new StringBuilder("SELECT r.*, COUNT(*) OVER() AS total_rows FROM (")
                .append(SELECT).append(" WHERE i.empresa_id = :empresaId) r WHERE TRUE ");
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        if (!search.isEmpty()) {
            sql.append(" AND (LOWER(r.numero_completo) LIKE :search OR LOWER(COALESCE(r.descripcion, '')) LIKE :search) ");
            params.addValue("search", "%" + search + "%");
        }
        if (f.getEstado() != null) {
            sql.append(" AND r.estado = :estado ");
            params.addValue("estado", f.getEstado());
        }
        sql.append(" ORDER BY r.fecha_aplicacion DESC, r.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<IncrementoDto> lista = jdbc.query(sql.toString(), params, (rs, i) -> {
            IncrementoDto dto = mapear(rs);
            dto.setTotalRows(rs.getLong("total_rows"));
            return dto;
        });
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /** Un incremento con sus totales; vacío si no es de la empresa. */
    public Optional<IncrementoDto> obtener(Long id, Integer empresaId) {
        return jdbc.query(SELECT + " WHERE i.id = :id AND i.empresa_id = :empresaId",
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId), (rs, i) -> mapear(rs))
                .stream().findFirst();
    }

    /** Vista previa: vinculaciones del incremento por nombre, con cuántas nóminas piden retroactivo. */
    public List<IncrementoDetalleDto> detalle(Long incrementoId) {
        return jdbc.query("""
                SELECT d.*, c.codigo, CONCAT(e.nombres, ' ', e.apellidos) AS empleado_nombre, e.numero_documento,
                       COALESCE(k.nombre, c.cargo) AS cargo,
                       (SELECT COUNT(*) """ + NOMINAS_RETRO + """
                           AND x.contrato_id = d.contrato_id AND px.fecha_fin >= i.fecha_aplicacion
                           AND x.salario_base < d.salario_nuevo) AS nominas_retroactivo
                  FROM nomina_incremento_detalle d
                  JOIN nomina_incremento i ON i.id = d.incremento_id
                  JOIN contrato_laboral c ON c.id = d.contrato_id
                  JOIN empleados e ON e.id = d.empleado_id
                  LEFT JOIN nomina_cargo k ON k.id = c.cargo_id
                 WHERE d.incremento_id = :id
                 ORDER BY e.apellidos, e.nombres
                """, new MapSqlParameterSource("id", incrementoId), (rs, i) -> {
                    IncrementoDetalleDto dto = new IncrementoDetalleDto();
                    dto.setId(rs.getLong("id"));
                    dto.setContratoId(rs.getLong("contrato_id"));
                    dto.setCodigoVinculacion(rs.getString("codigo"));
                    dto.setEmpleadoId(rs.getLong("empleado_id"));
                    dto.setEmpleadoNombre(rs.getString("empleado_nombre"));
                    dto.setEmpleadoDocumento(rs.getString("numero_documento"));
                    dto.setCargo(rs.getString("cargo"));
                    dto.setSalarioAnterior(rs.getBigDecimal("salario_anterior"));
                    dto.setSalarioNuevo(rs.getBigDecimal("salario_nuevo"));
                    dto.setDiferencia(dto.getSalarioNuevo().subtract(dto.getSalarioAnterior()));
                    dto.setNominasRetroactivo(rs.getInt("nominas_retroactivo"));
                    return dto;
                });
    }

    /** Líneas guardadas del incremento. */
    public List<Detalle> lineas(Long incrementoId) {
        return jdbc.query("""
                SELECT id, contrato_id, salario_anterior, salario_nuevo, historial_id
                  FROM nomina_incremento_detalle WHERE incremento_id = :id ORDER BY id
                """, new MapSqlParameterSource("id", incrementoId),
                (rs, i) -> new Detalle(rs.getLong("id"), rs.getLong("contrato_id"), rs.getBigDecimal("salario_anterior"),
                        rs.getBigDecimal("salario_nuevo"), rs.getObject("historial_id", Long.class)));
    }

    /**
     * Vinculaciones activas de la lista (sin prestación de servicios ni aprendices,
     * que no ganan salario) con su salario actual.
     */
    public List<Vinculacion> vinculaciones(Integer empresaId, TipoListaNomina tipoLista, List<Long> ids) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.empleado_id, COALESCE(c.salario_base, 0) AS salario
                  FROM contrato_laboral c JOIN empleados e ON e.id = c.empleado_id
                 WHERE c.empresa_id = :empresaId AND c.estado = 'ACTIVO' AND c.deleted_at IS NULL
                   AND c.tipo_contrato NOT IN ('PRESTACION_SERVICIOS', 'APRENDIZAJE')
                """);
        switch (tipoLista) {
            case EMPLEADO -> sql.append(" AND e.tercero_id IN (:ids) ");
            case VINCULACION -> sql.append(" AND c.id IN (:ids) ");
            case GRUPO -> sql.append(" AND c.grupo_nomina_id IN (:ids) ");
            case TIPO_VINCULACION -> sql.append(" AND c.tipo_vinculacion_id IN (:ids) ");
            case TODOS -> { }
        }
        sql.append(" ORDER BY e.apellidos, e.nombres ");
        return jdbc.query(sql.toString(), new MapSqlParameterSource("empresaId", empresaId).addValue("ids", ids),
                (rs, i) -> new Vinculacion(rs.getLong("id"), rs.getLong("empleado_id"), rs.getBigDecimal("salario")));
    }

    /** Reemplaza las líneas del incremento (vista previa recalculada). */
    public void reemplazarDetalle(Long incrementoId, List<Vinculacion> vinculaciones, List<BigDecimal> nuevos) {
        jdbc.update("DELETE FROM nomina_incremento_detalle WHERE incremento_id = :id",
                new MapSqlParameterSource("id", incrementoId));
        MapSqlParameterSource[] batch = new MapSqlParameterSource[vinculaciones.size()];
        for (int i = 0; i < vinculaciones.size(); i++) {
            Vinculacion v = vinculaciones.get(i);
            batch[i] = new MapSqlParameterSource("incrementoId", incrementoId).addValue("contratoId", v.contratoId())
                    .addValue("empleadoId", v.empleadoId()).addValue("anterior", v.salario())
                    .addValue("nuevo", nuevos.get(i));
        }
        jdbc.batchUpdate("""
                INSERT INTO nomina_incremento_detalle (incremento_id, contrato_id, empleado_id, salario_anterior, salario_nuevo)
                VALUES (:incrementoId, :contratoId, :empleadoId, :anterior, :nuevo)
                """, batch);
    }

    /** Quita una vinculación de la vista previa. */
    public int quitarDetalle(Long incrementoId, Long detalleId) {
        return jdbc.update("DELETE FROM nomina_incremento_detalle WHERE incremento_id = :id AND id = :detalleId",
                new MapSqlParameterSource("id", incrementoId).addValue("detalleId", detalleId));
    }

    /** Guarda (o limpia, con null) la fila del historial salarial que creó la línea. */
    public void marcarHistorial(Long detalleId, Long historialId) {
        jdbc.update("UPDATE nomina_incremento_detalle SET historial_id = :historialId WHERE id = :id",
                new MapSqlParameterSource("id", detalleId).addValue("historialId", historialId));
    }

    /** Siguiente número de incremento de la empresa. */
    public int siguienteNumero(Integer empresaId) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(MAX(numero), 0) + 1 FROM nomina_incremento WHERE empresa_id = :e",
                new MapSqlParameterSource("e", empresaId), Integer.class);
        return n != null ? n : 1;
    }

    /**
     * Vinculaciones del incremento con nóminas confirmadas que piden retroactivo y el
     * fin del último período afectado.
     */
    public List<Retroactivo> retroactivos(Long incrementoId) {
        return jdbc.query("""
                SELECT d.contrato_id, MAX(px.fecha_fin) AS hasta
                  FROM nomina_incremento_detalle d
                  JOIN nomina_incremento i ON i.id = d.incremento_id
                  JOIN nomina x ON x.contrato_id = d.contrato_id
                  JOIN periodo_nomina px ON px.id = x.periodo_id
                  LEFT JOIN nomina_documento dx ON dx.id = x.documento_id
                  LEFT JOIN nomina_tipo_comprobante tx ON tx.id = dx.tipo_comprobante_id
                 WHERE d.incremento_id = :id
                   AND x.es_prenomina = FALSE AND x.estado IN ('APROBADO', 'PAGADO')
                   AND (x.documento_id IS NULL OR tx.clase = 'PERIODICO')
                   AND px.fecha_fin >= i.fecha_aplicacion AND x.salario_base < d.salario_nuevo
                 GROUP BY d.contrato_id
                """, new MapSqlParameterSource("id", incrementoId),
                (rs, i) -> new Retroactivo(rs.getLong("contrato_id"), rs.getObject("hasta", LocalDate.class)));
    }

    /**
     * ¿La vinculación ya tiene nóminas confirmadas con el salario nuevo después de
     * aplicado el incremento? Entonces reversarlo dejaría esas nóminas sin soporte.
     */
    public boolean nominaConfirmadaDespues(Long contratoId, LocalDate fecha, LocalDateTime aplicadoAt) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) " + NOMINAS_RETRO + """
                   AND x.contrato_id = :contratoId AND px.fecha_fin >= :fecha
                   AND COALESCE(x.updated_at, x.created_at) >= :aplicado
                """, new MapSqlParameterSource("contratoId", contratoId).addValue("fecha", fecha)
                .addValue("aplicado", aplicadoAt), Integer.class);
        return n != null && n > 0;
    }

    private static IncrementoDto mapear(ResultSet rs) throws SQLException {
        IncrementoDto dto = new IncrementoDto();
        dto.setId(rs.getLong("id"));
        dto.setNumero(rs.getInt("numero"));
        dto.setNumeroCompleto(rs.getString("numero_completo"));
        dto.setTipoLista(rs.getString("tipo_lista"));
        String ids = rs.getString("lista_ids");
        if (ids != null && !ids.isBlank())
            dto.setIds(Arrays.stream(ids.split(",")).map(String::trim).map(Long::valueOf).toList());
        dto.setTipoIncremento(rs.getString("tipo_incremento"));
        dto.setValor(rs.getBigDecimal("valor"));
        dto.setRedondeo(rs.getString("redondeo"));
        dto.setFechaAplicacion(rs.getObject("fecha_aplicacion", LocalDate.class));
        dto.setDescripcion(rs.getString("descripcion"));
        dto.setEstado(rs.getString("estado"));
        dto.setDocumentoId(rs.getObject("documento_id", Long.class));
        dto.setDocumentoNumero(rs.getString("documento_numero"));
        dto.setDocumentoEstado(rs.getString("documento_estado"));
        dto.setEmpleados(rs.getInt("empleados"));
        dto.setTotalAnterior(rs.getBigDecimal("total_anterior"));
        dto.setTotalNuevo(rs.getBigDecimal("total_nuevo"));
        dto.setConRetroactivo(rs.getInt("con_retroactivo"));
        dto.setAplicadoAt(rs.getObject("aplicado_at", LocalDateTime.class));
        dto.setReversadoAt(rs.getObject("reversado_at", LocalDateTime.class));
        dto.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return dto;
    }
}
