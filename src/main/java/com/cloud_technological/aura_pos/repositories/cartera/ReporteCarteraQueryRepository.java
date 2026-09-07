package com.cloud_technological.aura_pos.repositories.cartera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraAbonoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraDocumentoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraResumenDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraTerceroDto;

/**
 * Consultas del estado de cuenta de cartera, para clientes y proveedores.
 *
 * <p>Las dos caras comparten estructura: {@code cuentas_cobrar}/{@code
 * abonos_cobrar} y {@code cuentas_pagar}/{@code abonos_pagar} tienen las mismas
 * columnas con otro nombre de tabla. Por eso la consulta se arma una vez y se
 * parametrizan los nombres, en vez de mantener dos SQL casi iguales que se
 * desincronizan a la primera corrección.
 *
 * <h2>El saldo se recalcula, no se lee</h2>
 *
 * <p>{@code saldo_pendiente} es una columna denormalizada que actualizan los
 * abonos. El reporte la ignora y suma los abonos vivos: si las dos cifras se
 * separaron —un abono borrado a mano, una anulación a medias— el estado de
 * cuenta tiene que mostrar la realidad, que es lo que el tercero va a reclamar.
 */
@Repository
public class ReporteCarteraQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    /** Los nombres que cambian entre las dos caras de la cartera. */
    private record Tablas(String cuentas, String abonos, String fk, String facturaExterna) {
        static Tablas de(String tipo) {
            return ReporteCarteraFiltroDto.CXP.equalsIgnoreCase(tipo)
                    ? new Tablas("cuentas_pagar", "abonos_pagar", "cuenta_pagar_id",
                            "c.numero_factura_externo")
                    : new Tablas("cuentas_cobrar", "abonos_cobrar", "cuenta_cobrar_id",
                            "NULL::varchar");
        }
    }

    /** La versión SQL de {@code Terceros.nombreVisible}. */
    private static final String TERCERO_NOMBRE = """
            COALESCE(
                NULLIF(TRIM(t.razon_social), ''),
                NULLIF(TRIM(CONCAT_WS(' ', t.nombre1, t.nombre2, t.apellido1, t.apellido2)), ''),
                NULLIF(TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)), ''),
                'Sin tercero'
            )""";

    /**
     * El abonado real: la suma de los abonos vivos.
     *
     * <p>No se usa {@code c.total_abonado} a propósito — ver la nota de la
     * clase.
     */
    private String abonado(Tablas t) {
        return "COALESCE((SELECT SUM(a.monto) FROM " + t.abonos() + " a"
                + " WHERE a." + t.fk() + " = c.id AND a.deleted_at IS NULL), 0)";
    }

    private String saldo(Tablas t) {
        return "(COALESCE(c.total_deuda, 0) - " + abonado(t) + ")";
    }

    /** Días vencidos; negativo si todavía no vence. */
    private static final String DIAS_MORA =
            "CASE WHEN c.fecha_vencimiento IS NULL THEN 0"
            + " ELSE (CURRENT_DATE - c.fecha_vencimiento::date) END";

    // ── Documentos ──────────────────────────────────────────────────────

    public PageImpl<ReporteCarteraDocumentoDto> documentos(
            ReporteCarteraFiltroDto filtro, Integer empresaId) {

        Tablas t = Tablas.de(filtro.getTipo());
        int page = filtro.getPage() != null ? filtro.getPage() : 0;
        int size = filtro.getRows() != null ? filtro.getRows() : 50;

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        StringBuilder where = new StringBuilder();
        aplicarFiltros(where, params, filtro, t);

        String sql = "SELECT c.id, c.numero_cuenta,\n"
                + "       " + t.facturaExterna() + " AS numero_factura_externo,\n"
                + "       c.tercero_id,\n"
                + "       " + TERCERO_NOMBRE + " AS tercero_nombre,\n"
                + "       t.numero_documento AS tercero_documento,\n"
                + "       c.fecha_emision, c.fecha_vencimiento,\n"
                + "       COALESCE(c.total_deuda, 0) AS total_deuda,\n"
                + "       " + abonado(t) + " AS total_abonado,\n"
                + "       " + saldo(t) + " AS saldo_pendiente,\n"
                + "       " + DIAS_MORA + " AS dias_mora,\n"
                + "       " + edadSql(t) + " AS edad,\n"
                + "       " + estadoSql(t) + " AS estado,\n"
                + "       COUNT(*) OVER() AS total_rows\n"
                + "  FROM " + t.cuentas() + " c\n"
                + "  LEFT JOIN tercero t ON t.id = c.tercero_id\n"
                + " WHERE c.empresa_id = :empresaId AND c.deleted_at IS NULL\n"
                + where
                // Lo más vencido primero: es el orden en el que alguien va a
                // trabajar la lista, no el cronológico.
                + " ORDER BY " + DIAS_MORA + " DESC, c.fecha_emision ASC"
                + " OFFSET :offset LIMIT :limit";

        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ReporteCarteraDocumentoDto> docs = jdbcTemplate.query(sql, params,
                new BeanPropertyRowMapper<>(ReporteCarteraDocumentoDto.class));

        if (Boolean.TRUE.equals(filtro.getIncluirAbonos()) && !docs.isEmpty()) {
            adjuntarAbonos(docs, t, empresaId);
        }

        long total = docs.isEmpty() ? 0 : docs.get(0).getTotalRows();
        return new PageImpl<>(docs, PageRequest.of(page, size), total);
    }

    /**
     * Trae los abonos de los documentos de la página en una sola consulta.
     *
     * <p>Uno por documento sería N+1: con 50 documentos en pantalla son 51
     * viajes a la base para armar un reporte que se abre a diario.
     */
    private void adjuntarAbonos(List<ReporteCarteraDocumentoDto> docs, Tablas t, Integer empresaId) {
        List<Long> ids = docs.stream().map(ReporteCarteraDocumentoDto::getId).toList();

        String sql = "SELECT a.id, a." + t.fk() + " AS cuenta_id,\n"
                + """
                       a.fecha_pago, a.monto, a.metodo_pago, a.referencia,
                       a.turno_caja_id,
                       cj.nombre  AS caja_nombre,
                       u.username AS usuario_nombre,
                       a.caja_otro_dia
                  FROM """ + " " + t.abonos() + " a\n"
                + "  LEFT JOIN turno_caja tc ON tc.id = a.turno_caja_id\n"
                + "  LEFT JOIN caja cj       ON cj.id = tc.caja_id\n"
                + "  LEFT JOIN usuario u     ON u.id = a.usuario_id\n"
                + " WHERE a." + t.fk() + " IN (:ids) AND a.deleted_at IS NULL\n"
                + " ORDER BY a.fecha_pago ASC, a.id ASC";

        List<ReporteCarteraAbonoDto> abonos = jdbcTemplate.query(sql,
                new MapSqlParameterSource("ids", ids).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(ReporteCarteraAbonoDto.class));

        Map<Long, List<ReporteCarteraAbonoDto>> porCuenta = new LinkedHashMap<>();
        for (ReporteCarteraAbonoDto a : abonos) {
            porCuenta.computeIfAbsent(a.getCuentaId(), k -> new ArrayList<>()).add(a);
        }
        docs.forEach(d -> d.setAbonos(porCuenta.getOrDefault(d.getId(), List.of())));
    }

    // ── Resumen por tercero ─────────────────────────────────────────────

    public ReporteCarteraResumenDto resumen(ReporteCarteraFiltroDto filtro, Integer empresaId) {
        Tablas t = Tablas.de(filtro.getTipo());

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        StringBuilder where = new StringBuilder();
        aplicarFiltros(where, params, filtro, t);

        String base = "  FROM " + t.cuentas() + " c\n"
                + "  LEFT JOIN tercero t ON t.id = c.tercero_id\n"
                + " WHERE c.empresa_id = :empresaId AND c.deleted_at IS NULL\n"
                + where;

        String saldo = saldo(t);
        String sql = "SELECT c.tercero_id,\n"
                + "       " + TERCERO_NOMBRE + " AS tercero_nombre,\n"
                + "       t.numero_documento AS tercero_documento,\n"
                + "       t.telefono         AS tercero_telefono,\n"
                + "       COUNT(*)           AS documentos,\n"
                + "       COALESCE(SUM(c.total_deuda), 0) AS total_deuda,\n"
                + "       COALESCE(SUM(" + abonado(t) + "), 0) AS total_abonado,\n"
                + "       COALESCE(SUM(" + saldo + "), 0)      AS saldo_pendiente,\n"
                + bucket("corriente", saldo, DIAS_MORA + " <= 0")
                + bucket("mora1a30", saldo, DIAS_MORA + " BETWEEN 1 AND 30")
                + bucket("mora31a60", saldo, DIAS_MORA + " BETWEEN 31 AND 60")
                + bucket("mora61a90", saldo, DIAS_MORA + " BETWEEN 61 AND 90")
                + bucket("mora_mas90", saldo, DIAS_MORA + " > 90")
                + "       MAX(" + DIAS_MORA + ") AS dias_mora_max,\n"
                + "       COUNT(*) OVER() AS total_rows\n"
                + base
                + " GROUP BY c.tercero_id, " + TERCERO_NOMBRE
                + ", t.numero_documento, t.telefono"
                // Quien más debe, primero.
                + " ORDER BY saldo_pendiente DESC";

        List<ReporteCarteraTerceroDto> terceros = jdbcTemplate.query(sql, params,
                new BeanPropertyRowMapper<>(ReporteCarteraTerceroDto.class));

        ReporteCarteraResumenDto dto = totales(filtro, empresaId);
        dto.setTipo(ReporteCarteraFiltroDto.CXP.equalsIgnoreCase(filtro.getTipo())
                ? ReporteCarteraFiltroDto.CXP : ReporteCarteraFiltroDto.CXC);
        dto.setTerceros(terceros);
        dto.setCantidadTerceros(terceros.size());
        return dto;
    }

    /** Una columna de edad: el saldo que cae en ese rango de mora. */
    private String bucket(String alias, String saldo, String condicion) {
        return "       COALESCE(SUM(CASE WHEN " + condicion + " THEN " + saldo
                + " ELSE 0 END), 0) AS " + alias + ",\n";
    }

    private ReporteCarteraResumenDto totales(ReporteCarteraFiltroDto filtro, Integer empresaId) {
        Tablas t = Tablas.de(filtro.getTipo());
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        StringBuilder where = new StringBuilder();
        aplicarFiltros(where, params, filtro, t);

        String saldo = saldo(t);
        String sql = "SELECT COUNT(*) AS documentos,\n"
                + "       COALESCE(SUM(c.total_deuda), 0) AS total_deuda,\n"
                + "       COALESCE(SUM(" + abonado(t) + "), 0) AS total_abonado,\n"
                + "       COALESCE(SUM(" + saldo + "), 0)      AS saldo_pendiente,\n"
                + "       COALESCE(SUM(CASE WHEN " + DIAS_MORA + " <= 0 THEN " + saldo + " ELSE 0 END), 0) AS corriente,\n"
                + "       COALESCE(SUM(CASE WHEN " + DIAS_MORA + " BETWEEN 1 AND 30 THEN " + saldo + " ELSE 0 END), 0) AS mora1a30,\n"
                + "       COALESCE(SUM(CASE WHEN " + DIAS_MORA + " BETWEEN 31 AND 60 THEN " + saldo + " ELSE 0 END), 0) AS mora31a60,\n"
                + "       COALESCE(SUM(CASE WHEN " + DIAS_MORA + " BETWEEN 61 AND 90 THEN " + saldo + " ELSE 0 END), 0) AS mora61a90,\n"
                + "       COALESCE(SUM(CASE WHEN " + DIAS_MORA + " > 90 THEN " + saldo + " ELSE 0 END), 0) AS mora_mas90\n"
                + "  FROM " + t.cuentas() + " c\n"
                + "  LEFT JOIN tercero t ON t.id = c.tercero_id\n"
                + " WHERE c.empresa_id = :empresaId AND c.deleted_at IS NULL\n"
                + where;

        return jdbcTemplate.queryForObject(sql, params,
                new BeanPropertyRowMapper<>(ReporteCarteraResumenDto.class));
    }

    // ── Filtros ─────────────────────────────────────────────────────────

    private void aplicarFiltros(StringBuilder sql, MapSqlParameterSource params,
            ReporteCarteraFiltroDto f, Tablas t) {

        String saldo = saldo(t);
        String estado = f.getEstado() != null ? f.getEstado().trim().toUpperCase() : "PENDIENTE";
        switch (estado) {
            case "PAGADA" -> sql.append(" AND ").append(saldo).append(" <= 0 ");
            case "VENCIDA" -> sql.append(" AND ").append(saldo).append(" > 0 AND ")
                    .append(DIAS_MORA).append(" > 0 ");
            case "TODAS" -> { /* sin condición: incluye lo ya pagado */ }
            default -> sql.append(" AND ").append(saldo).append(" > 0 ");
        }

        if (f.getFechaDesde() != null) {
            sql.append(" AND c.fecha_emision::date >= :fechaDesde ");
            params.addValue("fechaDesde", f.getFechaDesde());
        }
        if (f.getFechaHasta() != null) {
            sql.append(" AND c.fecha_emision::date <= :fechaHasta ");
            params.addValue("fechaHasta", f.getFechaHasta());
        }
        if (f.getTerceroId() != null) {
            sql.append(" AND c.tercero_id = :terceroId ");
            params.addValue("terceroId", f.getTerceroId());
        }
        if (f.getDiasMoraMin() != null) {
            sql.append(" AND ").append(DIAS_MORA).append(" >= :diasMoraMin ");
            params.addValue("diasMoraMin", f.getDiasMoraMin());
        }
        if (f.getSearch() != null && !f.getSearch().isBlank()) {
            sql.append(" AND (LOWER(COALESCE(c.numero_cuenta, '')) LIKE :search")
               .append(" OR LOWER(COALESCE(").append(TERCERO_NOMBRE).append(", '')) LIKE :search")
               .append(" OR LOWER(COALESCE(t.numero_documento, '')) LIKE :search");
            if (ReporteCarteraFiltroDto.CXP.equalsIgnoreCase(f.getTipo())) {
                sql.append(" OR LOWER(COALESCE(c.numero_factura_externo, '')) LIKE :search");
            }
            sql.append(") ");
            params.addValue("search", "%" + f.getSearch().trim().toLowerCase() + "%");
        }
    }

    private String edadSql(Tablas t) {
        return "CASE WHEN " + saldo(t) + " <= 0 THEN 'CORRIENTE'"
                + " WHEN " + DIAS_MORA + " <= 0 THEN 'CORRIENTE'"
                + " WHEN " + DIAS_MORA + " <= 30 THEN '1-30'"
                + " WHEN " + DIAS_MORA + " <= 60 THEN '31-60'"
                + " WHEN " + DIAS_MORA + " <= 90 THEN '61-90'"
                + " ELSE '+90' END";
    }

    /**
     * El estado se calcula, no se lee de {@code c.estado}.
     *
     * <p>Esa columna la mantienen los abonos y puede quedar desfasada; además
     * "vencida" depende de la fecha de hoy, así que guardarla obligaría a
     * recorrer la tabla cada día para mantenerla al día.
     */
    private String estadoSql(Tablas t) {
        return "CASE WHEN " + saldo(t) + " <= 0 THEN 'PAGADA'"
                + " WHEN " + DIAS_MORA + " > 0 THEN 'VENCIDA'"
                + " ELSE 'PENDIENTE' END";
    }
}
