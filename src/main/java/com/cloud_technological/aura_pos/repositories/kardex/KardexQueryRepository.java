package com.cloud_technological.aura_pos.repositories.kardex;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.kardex.KardexDetalleLineaDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexFiltroDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexReporteFiltroDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexReporteLineaDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexResumenDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexTableDto;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario.Familia;


@Repository
public class KardexQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * La variación real de una fila del kardex.
     *
     * <p>No se usa la columna {@code cantidad}: casi todos los orígenes guardan
     * la salida negada, pero {@code ReconteoServiceImpl} guarda el valor
     * absoluto y pone el sentido en el nombre del tipo. {@code saldo_nuevo -
     * saldo_anterior} está en todas las filas y no depende de quién las
     * escribió, así que es lo único que cuadra contra el stock real.
     */
    private static final String DELTA =
            "(COALESCE(m.saldo_nuevo, 0) - COALESCE(m.saldo_anterior, 0))";

    public PageImpl<KardexTableDto> listar(KardexFiltroDto filtro, Integer empresaId) {
        int page = filtro.getPage() != null ? filtro.getPage() : 0;
        int size = filtro.getRows() != null ? filtro.getRows() : 20;

        StringBuilder sql = new StringBuilder("""
            SELECT
                m.id,
                m.tipo_movimiento,
                m.cantidad,
                m.saldo_anterior,
                m.saldo_nuevo,
                m.costo_historico,
                m.referencia_origen,
                m.created_at,
                p.nombre AS producto_nombre,
                p.sku    AS producto_sku,
                s.nombre AS sucursal_nombre,
                l.codigo_lote,
                COUNT(*) OVER() AS total_rows
            FROM movimiento_inventario m
            INNER JOIN producto p ON m.producto_id = p.id
            INNER JOIN sucursal s ON m.sucursal_id = s.id
            LEFT JOIN lote l ON m.lote_id = l.id
            WHERE s.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        aplicarFiltros(sql, params, filtro);

        sql.append(" ORDER BY m.created_at DESC, m.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<KardexTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(KardexTableDto.class));
        list.forEach(this::etiquetar);

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /** La etiqueta y el grupo salen del catálogo, no de la tabla. */
    private void etiquetar(KardexTableDto fila) {
        fila.setTipoEtiqueta(TipoMovimientoInventario.etiquetaDe(fila.getTipoMovimiento()));
        fila.setGrupo(TipoMovimientoInventario.de(fila.getTipoMovimiento())
                .map(t -> t.grupo().name()).orElse(null));
    }

    // ── Reporte agrupado ────────────────────────────────────────────────

    /**
     * Una fila por producto (y por sucursal o lote, según la agrupación) con lo
     * que entró y salió en el rango.
     *
     * <p>Los saldos se <b>leen</b> de los propios movimientos — el
     * {@code saldo_anterior} del primero y el {@code saldo_nuevo} del último —
     * en vez de calcularse sumando cantidades. Así el reporte cuadra contra
     * {@code inventario.stock_actual} y, cuando no cuadra, el descuadre se ve:
     * que es justo lo que se quiere de un kardex.
     *
     * <p>El saldo vive en {@code inventario}, que es por producto <b>y</b>
     * sucursal. Por eso se calcula siempre a ese nivel y se suma hacia arriba
     * cuando se agrupa solo por producto. Agrupando por lote no hay saldo que
     * mostrar — el lote no tiene stock propio — y las columnas van en null.
     */
    public PageImpl<KardexReporteLineaDto> reporte(KardexReporteFiltroDto filtro, Integer empresaId) {
        int page = filtro.getPage() != null ? filtro.getPage() : 0;
        int size = filtro.getRows() != null ? filtro.getRows() : 50;
        String agrupacion = filtro.getAgrupacion() != null
                ? filtro.getAgrupacion().trim().toUpperCase()
                : KardexReporteFiltroDto.POR_PRODUCTO;

        boolean porSucursal = KardexReporteFiltroDto.POR_PRODUCTO_SUCURSAL.equals(agrupacion);
        boolean porLote = KardexReporteFiltroDto.POR_PRODUCTO_LOTE.equals(agrupacion);

        StringBuilder filtrados = new StringBuilder("""
            SELECT m.id, m.producto_id, m.sucursal_id, m.lote_id, m.tipo_movimiento,
                   m.created_at, COALESCE(m.costo_historico, 0) AS costo_historico,
                   m.saldo_anterior, m.saldo_nuevo,
        """);
        filtrados.append("       ").append(DELTA).append(" AS delta\n");
        filtrados.append("""
              FROM movimiento_inventario m
              INNER JOIN producto p ON p.id = m.producto_id
              INNER JOIN sucursal s ON s.id = m.sucursal_id
             WHERE s.empresa_id = :empresaId
            """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        aplicarFiltros(filtrados, params, filtro);

        // El saldo inicial y el final por producto+sucursal: el primero y el
        // último movimiento del rango. array_agg con ORDER BY es la forma
        // directa de un "first/last value" dentro de un GROUP BY.
        String saldoInicial;
        String saldoFinal;
        if (porLote) {
            saldoInicial = "NULL::numeric";
            saldoFinal = "NULL::numeric";
        } else if (porSucursal) {
            saldoInicial = "(SELECT sa.saldo_ini FROM saldos sa"
                    + " WHERE sa.producto_id = f.producto_id AND sa.sucursal_id = f.sucursal_id)";
            saldoFinal = "(SELECT sa.saldo_fin FROM saldos sa"
                    + " WHERE sa.producto_id = f.producto_id AND sa.sucursal_id = f.sucursal_id)";
        } else {
            saldoInicial = "(SELECT COALESCE(SUM(sa.saldo_ini), 0) FROM saldos sa"
                    + " WHERE sa.producto_id = f.producto_id)";
            saldoFinal = "(SELECT COALESCE(SUM(sa.saldo_fin), 0) FROM saldos sa"
                    + " WHERE sa.producto_id = f.producto_id)";
        }

        StringBuilder sql = new StringBuilder();
        sql.append("WITH filtrados AS (\n").append(filtrados).append("\n),\n");
        sql.append("""
            saldos AS (
                SELECT producto_id, sucursal_id,
                       (array_agg(saldo_anterior ORDER BY created_at, id))[1]           AS saldo_ini,
                       (array_agg(saldo_nuevo ORDER BY created_at DESC, id DESC))[1]    AS saldo_fin
                  FROM filtrados
                 GROUP BY producto_id, sucursal_id
            )
            SELECT
                f.producto_id,
                p.nombre        AS producto_nombre,
                p.sku           AS producto_sku,
                cat.nombre      AS categoria_nombre,
                mar.nombre      AS marca_nombre,
            """);
        sql.append(porSucursal
                ? "    f.sucursal_id, suc.nombre AS sucursal_nombre,\n"
                : "    NULL::bigint AS sucursal_id, NULL::varchar AS sucursal_nombre,\n");
        sql.append(porLote
                ? "    f.lote_id, lot.codigo_lote,\n"
                : "    NULL::bigint AS lote_id, NULL::varchar AS codigo_lote,\n");
        sql.append("    ").append(saldoInicial).append(" AS saldo_inicial,\n");
        sql.append("    ").append(saldoFinal).append(" AS saldo_final,\n");
        sql.append("""
                COALESCE(SUM(CASE WHEN f.delta > 0 THEN  f.delta ELSE 0 END), 0) AS entradas,
                COALESCE(SUM(CASE WHEN f.delta < 0 THEN -f.delta ELSE 0 END), 0) AS salidas,
                COALESCE(SUM(f.delta), 0)                                        AS variacion_neta,
                COALESCE(SUM(CASE WHEN f.delta > 0 THEN  f.delta * f.costo_historico ELSE 0 END), 0) AS valor_entradas,
                COALESCE(SUM(CASE WHEN f.delta < 0 THEN -f.delta * f.costo_historico ELSE 0 END), 0) AS valor_salidas,
                COUNT(*) AS cantidad_movimientos,
            """);

        // Desglose por familia. Se arma desde el enum para que agregar un tipo
        // nuevo no lo deje cayendo silenciosamente en "otros".
        sql.append(desglose("compras", Familia.COMPRAS, params));
        sql.append(desglose("ventas", Familia.VENTAS, params));
        sql.append(desglose("devoluciones", Familia.DEVOLUCIONES, params));
        sql.append(desglose("mermas", Familia.MERMAS, params));
        sql.append(desglose("obsequios", Familia.OBSEQUIOS, params));
        sql.append(desglose("traslados", Familia.TRASLADOS, params));
        sql.append(desglose("anulaciones", Familia.ANULACIONES, params));
        sql.append(desglose("reconteos", Familia.RECONTEOS, params));
        sql.append("    COALESCE(SUM(CASE WHEN f.tipo_movimiento NOT IN (:todosLosTipos)"
                + " THEN f.delta ELSE 0 END), 0) AS otros,\n");
        params.addValue("todosLosTipos",
                java.util.Arrays.stream(TipoMovimientoInventario.values())
                        .map(TipoMovimientoInventario::codigo).toList());

        sql.append("    COUNT(*) OVER() AS total_rows\n");
        sql.append("""
              FROM filtrados f
              INNER JOIN producto p ON p.id = f.producto_id
              LEFT JOIN categoria cat ON cat.id = p.categoria_id
              LEFT JOIN marca mar ON mar.id = p.marca_id
            """);
        if (porSucursal) sql.append("  LEFT JOIN sucursal suc ON suc.id = f.sucursal_id\n");
        if (porLote) sql.append("  LEFT JOIN lote lot ON lot.id = f.lote_id\n");

        sql.append(" GROUP BY f.producto_id, p.nombre, p.sku, cat.nombre, mar.nombre");
        if (porSucursal) sql.append(", f.sucursal_id, suc.nombre");
        if (porLote) sql.append(", f.lote_id, lot.codigo_lote");

        sql.append(" ORDER BY p.nombre OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<KardexReporteLineaDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(KardexReporteLineaDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /** Una columna del desglose: el neto de esa familia, en unidades. */
    private String desglose(String alias, Familia familia, MapSqlParameterSource params) {
        String param = "fam" + familia.name();
        params.addValue(param, TipoMovimientoInventario.codigosDe(familia));
        return "    COALESCE(SUM(CASE WHEN f.tipo_movimiento IN (:" + param
                + ") THEN f.delta ELSE 0 END), 0) AS " + alias + ",\n";
    }

    // ── Kardex clásico de un producto ───────────────────────────────────

    /**
     * Los movimientos de un producto en orden cronológico, con su saldo
     * corrido. El orden es ascendente a propósito: el listado general va de lo
     * más nuevo a lo más viejo, pero un saldo corrido leído al revés no se
     * puede seguir con el dedo.
     */
    public PageImpl<KardexDetalleLineaDto> detalle(KardexFiltroDto filtro, Integer empresaId) {
        int page = filtro.getPage() != null ? filtro.getPage() : 0;
        int size = filtro.getRows() != null ? filtro.getRows() : 100;

        StringBuilder sql = new StringBuilder("""
            SELECT
                m.id,
                m.created_at        AS fecha,
                m.tipo_movimiento,
                m.referencia_origen,
                s.nombre            AS sucursal_nombre,
                l.codigo_lote,
                m.saldo_anterior,
                m.saldo_nuevo,
                COALESCE(m.costo_historico, 0) AS costo_historico,
            """);
        sql.append("    ").append(DELTA).append(" AS movimiento,\n");
        sql.append("    ABS(").append(DELTA)
           .append(") * COALESCE(m.costo_historico, 0) AS valor_movimiento,\n");
        sql.append("""
                COUNT(*) OVER() AS total_rows
            FROM movimiento_inventario m
            INNER JOIN producto p ON p.id = m.producto_id
            INNER JOIN sucursal s ON s.id = m.sucursal_id
            LEFT JOIN lote l ON l.id = m.lote_id
            WHERE s.empresa_id = :empresaId
            """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        aplicarFiltros(sql, params, filtro);

        sql.append(" ORDER BY m.created_at ASC, m.id ASC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<KardexDetalleLineaDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(KardexDetalleLineaDto.class));

        // La etiqueta y el grupo se resuelven en Java: son del catálogo, no de
        // la tabla, y meterlos en el SQL obligaría a repetir el enum en un CASE.
        for (KardexDetalleLineaDto linea : list) {
            linea.setTipoEtiqueta(TipoMovimientoInventario.etiquetaDe(linea.getTipoMovimiento()));
            linea.setGrupo(TipoMovimientoInventario.de(linea.getTipoMovimiento())
                    .map(t -> t.grupo().name()).orElse(null));
        }

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    // ── Resumen por familia, para el reporte gerencial ──────────────────

    /** Una familia de movimiento con lo que entró y salió en el período. */
    public record MovimientoPorFamilia(String familia, BigDecimal entradas, BigDecimal salidas,
            BigDecimal valorEntradas, BigDecimal valorSalidas, int movimientos) {
    }

    /**
     * Cuánto entró y salió del inventario, agrupado por familia de movimiento.
     *
     * <p>Responde "¿por dónde se me está yendo la mercancía?": si las mermas
     * pesan tanto como las ventas, el problema no está en el inventario sino en
     * quién lo maneja.
     *
     * <p>Como siempre en el kardex, la magnitud sale de
     * {@code saldo_nuevo − saldo_anterior} y no de la columna {@code cantidad}.
     */
    public List<MovimientoPorFamilia> movimientoPorFamilia(Integer empresaId,
            java.time.LocalDate desde, java.time.LocalDate hasta) {

        StringBuilder caso = new StringBuilder("CASE ");
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("desde", desde).addValue("hasta", hasta);
        for (Familia f : Familia.values()) {
            String p = "fam" + f.name();
            params.addValue(p, TipoMovimientoInventario.codigosDe(f));
            caso.append(" WHEN m.tipo_movimiento IN (:").append(p).append(") THEN '")
                .append(f.name()).append("'");
        }
        caso.append(" ELSE 'OTROS' END");

        String sql = "SELECT " + caso + " AS familia,\n"
                + "       COALESCE(SUM(CASE WHEN " + DELTA + " > 0 THEN " + DELTA + " ELSE 0 END), 0) AS entradas,\n"
                + "       COALESCE(SUM(CASE WHEN " + DELTA + " < 0 THEN -" + DELTA + " ELSE 0 END), 0) AS salidas,\n"
                + "       COALESCE(SUM(CASE WHEN " + DELTA + " > 0 THEN " + DELTA
                + "            * COALESCE(m.costo_historico, 0) ELSE 0 END), 0) AS valor_entradas,\n"
                + "       COALESCE(SUM(CASE WHEN " + DELTA + " < 0 THEN -" + DELTA
                + "            * COALESCE(m.costo_historico, 0) ELSE 0 END), 0) AS valor_salidas,\n"
                + "       COUNT(*) AS movimientos\n"
                + "  FROM movimiento_inventario m\n"
                + "  JOIN sucursal s ON s.id = m.sucursal_id\n"
                + " WHERE s.empresa_id = :empresaId\n"
                + "   AND m.created_at::date BETWEEN :desde AND :hasta\n"
                + " GROUP BY 1 ORDER BY salidas DESC";

        return jdbcTemplate.query(sql, params, (rs, i) -> new MovimientoPorFamilia(
                rs.getString("familia"),
                rs.getBigDecimal("entradas"), rs.getBigDecimal("salidas"),
                rs.getBigDecimal("valor_entradas"), rs.getBigDecimal("valor_salidas"),
                rs.getInt("movimientos")));
    }

    // ── Filtros compartidos ─────────────────────────────────────────────

    /**
     * Los mismos filtros para el listado, el reporte y el detalle. Compartirlos
     * es lo que garantiza que exportar devuelva exactamente lo que se ve en
     * pantalla y no un conjunto parecido.
     */
    private void aplicarFiltros(StringBuilder sql, MapSqlParameterSource params,
            KardexFiltroDto filtro) {

        if (filtro.getProductoId() != null) {
            sql.append(" AND m.producto_id = :productoId ");
            params.addValue("productoId", filtro.getProductoId());
        }
        if (filtro.getSucursalId() != null) {
            sql.append(" AND m.sucursal_id = :sucursalId ");
            params.addValue("sucursalId", filtro.getSucursalId());
        }
        if (filtro.getLoteId() != null) {
            sql.append(" AND m.lote_id = :loteId ");
            params.addValue("loteId", filtro.getLoteId());
        }
        if (filtro.getCategoriaId() != null) {
            sql.append(" AND p.categoria_id = :categoriaId ");
            params.addValue("categoriaId", filtro.getCategoriaId());
        }
        if (filtro.getMarcaId() != null) {
            sql.append(" AND p.marca_id = :marcaId ");
            params.addValue("marcaId", filtro.getMarcaId());
        }

        // Varios tipos manda sobre uno solo: si el usuario eligió una lista, es
        // más específico que el filtro de un tipo que quedó de antes.
        List<String> tipos = tiposPedidos(filtro);
        if (!tipos.isEmpty()) {
            sql.append(" AND m.tipo_movimiento IN (:tiposMovimiento) ");
            params.addValue("tiposMovimiento", tipos);
        }

        if (filtro.getFechaDesde() != null) {
            sql.append(" AND m.created_at >= :fechaDesde ");
            params.addValue("fechaDesde", filtro.getFechaDesde());
        }
        if (filtro.getFechaHasta() != null) {
            sql.append(" AND m.created_at <= :fechaHasta ");
            params.addValue("fechaHasta", filtro.getFechaHasta());
        }

        if (filtro.getSearch() != null && !filtro.getSearch().isBlank()) {
            sql.append(" AND (LOWER(p.nombre) LIKE :search"
                    + " OR LOWER(COALESCE(p.sku, '')) LIKE :search"
                    + " OR LOWER(COALESCE(p.codigo_barras, '')) LIKE :search"
                    + " OR LOWER(COALESCE(m.referencia_origen, '')) LIKE :search) ");
            params.addValue("search", "%" + filtro.getSearch().trim().toLowerCase() + "%");
        }
    }

    /** Los tipos que pidió el filtro, ya resueltos: lista, grupo o uno solo. */
    private List<String> tiposPedidos(KardexFiltroDto filtro) {
        if (filtro.getTiposMovimiento() != null && !filtro.getTiposMovimiento().isEmpty()) {
            return filtro.getTiposMovimiento().stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .toList();
        }
        if (filtro.getGrupoMovimiento() != null && !filtro.getGrupoMovimiento().isBlank()) {
            try {
                return TipoMovimientoInventario.codigosDe(
                        TipoMovimientoInventario.Grupo.valueOf(
                                filtro.getGrupoMovimiento().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Grupo desconocido: se ignora en vez de reventar el reporte.
                return new ArrayList<>();
            }
        }
        if (filtro.getTipoMovimiento() != null && !filtro.getTipoMovimiento().isBlank()) {
            return List.of(filtro.getTipoMovimiento().trim().toUpperCase());
        }
        return new ArrayList<>();
    }

    // Resumen de stock actual por producto en todas las sucursales
    public List<KardexResumenDto> resumenStockPorProducto(Long productoId, Integer empresaId) {
        String sql = """
            SELECT
                s.id AS sucursal_id,
                s.nombre AS sucursal_nombre,
                p.id AS producto_id,
                p.nombre AS producto_nombre,
                p.sku AS producto_sku,
                i.stock_actual,
                i.stock_minimo,
                CASE WHEN i.stock_actual <= i.stock_minimo THEN true ELSE false END AS stock_critico
            FROM inventario i
            INNER JOIN sucursal s ON i.sucursal_id = s.id
            INNER JOIN producto p ON i.producto_id = p.id
            WHERE s.empresa_id = :empresaId
            AND p.id = :productoId
            ORDER BY s.nombre
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("empresaId", empresaId);
        params.addValue("productoId", productoId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(KardexResumenDto.class));
    }
}
