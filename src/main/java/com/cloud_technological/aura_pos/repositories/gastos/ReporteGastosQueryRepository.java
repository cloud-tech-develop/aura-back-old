package com.cloud_technological.aura_pos.repositories.gastos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosDetalleDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosLineaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosResumenDto;
import com.cloud_technological.aura_pos.utils.CategoriaGasto;

/**
 * Consultas del reporte de gastos.
 *
 * <p>El resumen y el detalle comparten el mismo bloque de filtros: es lo que
 * garantiza que al abrir el detalle de una categoría se vean exactamente los
 * gastos que sumaron en esa fila, y no unos parecidos.
 *
 * <p>Ojo con {@code BeanPropertyRowMapper}: mapea por nombre de columna, así
 * que una columna agregada al SELECT sin su campo en el DTO se pierde en
 * silencio. Los alias de aquí están alineados a mano con los DTO.
 */
@Repository
public class ReporteGastosQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * El nombre del tercero como se muestra: razón social si la hay, si no el
     * nombre desagregado (V97) y, como último recurso, los campos viejos
     * {@code nombres}/{@code apellidos} de los terceros anteriores a esa
     * migración. Es la versión SQL de {@code Terceros.nombreVisible}.
     */
    private static final String TERCERO_NOMBRE = """
            COALESCE(
                NULLIF(TRIM(t.razon_social), ''),
                NULLIF(TRIM(CONCAT_WS(' ', t.nombre1, t.nombre2, t.apellido1, t.apellido2)), ''),
                NULLIF(TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)), '')
            )""";

    /** Los JOIN que necesitan los filtros y las agrupaciones. */
    private static final String FROM = """
            FROM gasto g
            LEFT JOIN sucursal s      ON s.id  = g.sucursal_id
            LEFT JOIN usuario u       ON u.id  = g.usuario_id
            LEFT JOIN tercero t       ON t.id  = g.tercero_id
            LEFT JOIN centros_costos cc ON cc.id = g.centro_costo_id
            LEFT JOIN plan_cuenta pc  ON pc.id = g.cuenta_contable_id
            LEFT JOIN proyecto pr     ON pr.id = g.proyecto_id
            WHERE g.empresa_id = :empresaId
            """;

    // ── Resumen agrupado ────────────────────────────────────────────────

    public ReporteGastosResumenDto resumen(ReporteGastosFiltroDto filtro, Integer empresaId) {
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        StringBuilder filtros = new StringBuilder();
        aplicarFiltros(filtros, params, filtro);

        String agrupacion = filtro.getAgrupacion() != null
                ? filtro.getAgrupacion().trim().toUpperCase()
                : ReporteGastosFiltroDto.POR_CATEGORIA;

        String clave = claveDe(agrupacion);
        String claveId = claveIdDe(agrupacion);

        String sql = "SELECT " + clave + " AS grupo, " + claveId + " AS grupo_id,\n"
                + """
                       COUNT(*)                                                            AS cantidad,
                       COALESCE(SUM(g.monto), 0)                                           AS total,
                       COALESCE(SUM(CASE WHEN g.deducible THEN g.monto ELSE 0 END), 0)      AS total_deducible,
                       COALESCE(SUM(CASE WHEN NOT g.deducible THEN g.monto ELSE 0 END), 0)  AS total_no_deducible,
                       COALESCE(SUM(CASE WHEN UPPER(g.forma_pago) = 'CONTADO' THEN g.monto ELSE 0 END), 0) AS total_contado,
                       COALESCE(SUM(CASE WHEN UPPER(g.forma_pago) = 'CREDITO' THEN g.monto ELSE 0 END), 0) AS total_credito,
                       COALESCE(SUM(g.base_iva), 0)                                        AS base_iva,
                       COALESCE(SUM(g.valor_iva), 0)                                       AS valor_iva,
                       COALESCE(SUM(g.valor_retefuente), 0)                                AS valor_retefuente,
                       COALESCE(SUM(g.valor_reteica), 0)                                   AS valor_reteica,
                       COUNT(*) OVER() AS total_rows
                """
                + FROM + filtros
                + " GROUP BY " + clave + (claveId.startsWith("NULL") ? "" : ", " + claveId)
                + " ORDER BY " + (ReporteGastosFiltroDto.POR_MES.equals(agrupacion)
                        ? "grupo ASC" : "total DESC");

        List<ReporteGastosLineaDto> lineas = jdbcTemplate.query(sql, params,
                new BeanPropertyRowMapper<>(ReporteGastosLineaDto.class));

        // La categoría se guarda por código; la etiqueta la pone el catálogo.
        // Un Excel con "OTRO_NO_DEDUCIBLE" hay que traducirlo a mano antes de
        // usarlo. Se hace aquí y no en un CASE del SQL para no repetir el enum.
        if (ReporteGastosFiltroDto.POR_CATEGORIA.equals(agrupacion)) {
            lineas.forEach(l -> l.setGrupo(CategoriaGasto.etiquetaDe(l.getGrupo())));
        }

        ReporteGastosResumenDto dto = totales(filtro, empresaId);
        // La participación se calcula contra el total del período, no contra la
        // suma de las filas: si algún día el resumen se pagina, los porcentajes
        // tienen que seguir sumando 100 sobre el mismo denominador.
        BigDecimal total = dto.getTotal();
        for (ReporteGastosLineaDto l : lineas) {
            l.setParticipacion(total != null && total.signum() != 0 && l.getTotal() != null
                    ? l.getTotal().multiply(BigDecimal.valueOf(100))
                            .divide(total, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }
        dto.setLineas(lineas);
        return dto;
    }

    /**
     * Los totales del período completo.
     *
     * <p>Van en una consulta aparte, no sumando las filas del resumen: un pie
     * de página que suma solo lo visible es una cifra que no cuadra con nada y
     * que alguien va a copiar a una declaración.
     */
    private ReporteGastosResumenDto totales(ReporteGastosFiltroDto filtro, Integer empresaId) {
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        StringBuilder filtros = new StringBuilder();
        aplicarFiltros(filtros, params, filtro);

        String sql = """
                SELECT COUNT(*)                                                           AS cantidad,
                       COALESCE(SUM(g.monto), 0)                                          AS total,
                       COALESCE(SUM(CASE WHEN g.deducible THEN g.monto ELSE 0 END), 0)     AS total_deducible,
                       COALESCE(SUM(CASE WHEN NOT g.deducible THEN g.monto ELSE 0 END), 0) AS total_no_deducible,
                       COALESCE(SUM(CASE WHEN UPPER(g.forma_pago) = 'CONTADO' THEN g.monto ELSE 0 END), 0) AS total_contado,
                       COALESCE(SUM(CASE WHEN UPPER(g.forma_pago) = 'CREDITO' THEN g.monto ELSE 0 END), 0) AS total_credito,
                       COALESCE(SUM(g.base_iva), 0)                                       AS base_iva,
                       COALESCE(SUM(g.valor_iva), 0)                                      AS valor_iva,
                       COALESCE(SUM(g.valor_retefuente), 0)                               AS valor_retefuente,
                       COALESCE(SUM(g.valor_reteica), 0)                                  AS valor_reteica
                """ + FROM + filtros;

        return jdbcTemplate.queryForObject(sql, params,
                new BeanPropertyRowMapper<>(ReporteGastosResumenDto.class));
    }

    // ── Detalle ─────────────────────────────────────────────────────────

    public PageImpl<ReporteGastosDetalleDto> detalle(ReporteGastosFiltroDto filtro, Integer empresaId) {
        int page = filtro.getPage() != null ? filtro.getPage() : 0;
        int size = filtro.getRows() != null ? filtro.getRows() : 50;

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        StringBuilder filtros = new StringBuilder();
        aplicarFiltros(filtros, params, filtro);

        String sql = "SELECT g.id, g.fecha, g.categoria, g.descripcion,\n"
                + "       " + TERCERO_NOMBRE + " AS tercero_nombre,\n"
                + """
                       t.numero_documento AS tercero_documento,
                       s.nombre           AS sucursal_nombre,
                       cc.nombre          AS centro_costo_nombre,
                       pc.codigo          AS cuenta_codigo,
                       pc.nombre          AS cuenta_nombre,
                       pr.nombre          AS proyecto_nombre,
                       g.monto, g.deducible, g.forma_pago, g.metodo_pago,
                       g.tipo_doc_soporte, g.numero_doc_soporte,
                       COALESCE(g.base_iva, 0)          AS base_iva,
                       COALESCE(g.valor_iva, 0)         AS valor_iva,
                       COALESCE(g.valor_retefuente, 0)  AS valor_retefuente,
                       COALESCE(g.valor_reteica, 0)     AS valor_reteica,
                       g.salida_caja_otro_dia,
                       g.motivo_retroactivo,
                       g.estado,
                       u.username AS usuario_nombre,
                       COUNT(*) OVER() AS total_rows
                """
                + FROM + filtros
                + " ORDER BY g.fecha DESC, g.id DESC OFFSET :offset LIMIT :limit ";

        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ReporteGastosDetalleDto> list = jdbcTemplate.query(sql, params,
                new BeanPropertyRowMapper<>(ReporteGastosDetalleDto.class));
        list.forEach(g -> g.setCategoria(CategoriaGasto.etiquetaDe(g.getCategoria())));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // ── Filtros y agrupaciones ──────────────────────────────────────────

    private void aplicarFiltros(StringBuilder sql, MapSqlParameterSource params,
            ReporteGastosFiltroDto f) {

        // Sin estado explícito solo cuentan los vigentes: un gasto eliminado
        // que sume en el total es una cifra que el contador no puede explicar.
        if (f.getEstado() != null && !f.getEstado().isBlank()
                && !"TODOS".equalsIgnoreCase(f.getEstado())) {
            sql.append(" AND g.estado = :estado ");
            params.addValue("estado", f.getEstado().trim().toUpperCase());
        }

        if (f.getFechaDesde() != null) {
            sql.append(" AND g.fecha >= :fechaDesde ");
            params.addValue("fechaDesde", f.getFechaDesde());
        }
        if (f.getFechaHasta() != null) {
            sql.append(" AND g.fecha <= :fechaHasta ");
            params.addValue("fechaHasta", f.getFechaHasta());
        }
        if (f.getSucursalId() != null) {
            sql.append(" AND g.sucursal_id = :sucursalId ");
            params.addValue("sucursalId", f.getSucursalId());
        }
        if (f.getCategoria() != null && !f.getCategoria().isBlank()) {
            sql.append(" AND g.categoria = :categoria ");
            params.addValue("categoria", f.getCategoria().trim());
        }
        if (f.getTerceroId() != null) {
            sql.append(" AND g.tercero_id = :terceroId ");
            params.addValue("terceroId", f.getTerceroId());
        }
        if (f.getCentroCostoId() != null) {
            sql.append(" AND g.centro_costo_id = :centroCostoId ");
            params.addValue("centroCostoId", f.getCentroCostoId());
        }
        if (f.getCuentaContableId() != null) {
            sql.append(" AND g.cuenta_contable_id = :cuentaContableId ");
            params.addValue("cuentaContableId", f.getCuentaContableId());
        }
        if (f.getProyectoId() != null) {
            sql.append(" AND g.proyecto_id = :proyectoId ");
            params.addValue("proyectoId", f.getProyectoId());
        }
        if (f.getFormaPago() != null && !f.getFormaPago().isBlank()) {
            sql.append(" AND UPPER(g.forma_pago) = :formaPago ");
            params.addValue("formaPago", f.getFormaPago().trim().toUpperCase());
        }
        if (f.getMetodoPago() != null && !f.getMetodoPago().isBlank()) {
            sql.append(" AND UPPER(g.metodo_pago) = :metodoPago ");
            params.addValue("metodoPago", f.getMetodoPago().trim().toUpperCase());
        }
        if (f.getDeducible() != null) {
            sql.append(" AND g.deducible = :deducible ");
            params.addValue("deducible", f.getDeducible());
        }
        if (f.getSearch() != null && !f.getSearch().isBlank()) {
            sql.append(" AND (LOWER(g.categoria) LIKE :search"
                    + " OR LOWER(COALESCE(g.descripcion, '')) LIKE :search"
                    + " OR LOWER(COALESCE(g.numero_doc_soporte, '')) LIKE :search"
                    + " OR LOWER(COALESCE(" + TERCERO_NOMBRE + ", '')) LIKE :search"
                    + " OR LOWER(COALESCE(t.numero_documento, '')) LIKE :search) ");
            params.addValue("search", "%" + f.getSearch().trim().toLowerCase() + "%");
        }
    }

    /**
     * La expresión que identifica cada grupo, ya legible.
     *
     * <p>Los nulos se etiquetan en vez de quedar en blanco: "Sin centro de
     * costo" es un hallazgo — hay plata sin imputar — y una fila vacía en el
     * reporte se lee como un error de la consulta.
     */
    private String claveDe(String agrupacion) {
        return switch (agrupacion) {
            case ReporteGastosFiltroDto.POR_TERCERO ->
                    "COALESCE(" + TERCERO_NOMBRE + ", 'Sin tercero')";
            case ReporteGastosFiltroDto.POR_CENTRO_COSTO ->
                    "COALESCE(cc.nombre, 'Sin centro de costo')";
            case ReporteGastosFiltroDto.POR_CUENTA ->
                    "COALESCE(pc.codigo || ' — ' || pc.nombre, 'Sin cuenta contable')";
            case ReporteGastosFiltroDto.POR_SUCURSAL ->
                    "COALESCE(s.nombre, 'Sin sucursal')";
            case ReporteGastosFiltroDto.POR_MES ->
                    "TO_CHAR(g.fecha, 'YYYY-MM')";
            default -> "COALESCE(NULLIF(TRIM(g.categoria), ''), 'Sin categoría')";
        };
    }

    /** El id del grupo, para poder abrir su detalle. Null donde no aplica. */
    private String claveIdDe(String agrupacion) {
        return switch (agrupacion) {
            case ReporteGastosFiltroDto.POR_TERCERO -> "g.tercero_id";
            case ReporteGastosFiltroDto.POR_CENTRO_COSTO -> "g.centro_costo_id";
            case ReporteGastosFiltroDto.POR_CUENTA -> "g.cuenta_contable_id";
            case ReporteGastosFiltroDto.POR_SUCURSAL -> "g.sucursal_id::bigint";
            default -> "NULL::bigint";
        };
    }
}
