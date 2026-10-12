package com.cloud_technological.aura_pos.repositories.causacion_gasto;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Detalle;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Fila;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Linea;
import com.cloud_technological.aura_pos.utils.PageableDto;

/** Consultas de la causación de gastos (V201). */
@Repository
public class CausacionGastoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private com.cloud_technological.aura_pos.services.permisos.AlcanceSede alcanceSede;

    /**
     * Siguiente consecutivo de la empresa. El candado de la transacción evita que
     * dos causaciones simultáneas saquen el mismo número (el índice único lo
     * respalda).
     */
    public int siguienteConsecutivo(Integer empresaId) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('causacion_gasto'), :empresaId)", p);
        Integer n = jdbc.queryForObject(
                "SELECT COALESCE(MAX(consecutivo), 0) + 1 FROM causacion_gasto WHERE empresa_id = :empresaId",
                p, Integer.class);
        return n != null ? n : 1;
    }

    /** La misma factura del mismo proveedor ya causada (y no anulada). */
    public boolean soporteRepetido(Integer empresaId, Long terceroId, String numeroSoporte) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM causacion_gasto
                WHERE empresa_id = :empresaId AND tercero_id = :terceroId
                  AND UPPER(TRIM(numero_soporte)) = UPPER(TRIM(:numero)) AND estado <> 'ANULADA'
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("terceroId", terceroId)
                .addValue("numero", numeroSoporte), Integer.class);
        return n != null && n > 0;
    }

    public List<Linea> lineas(Long causacionId) {
        return jdbc.query("""
                SELECT l.id, l.cuenta_id, pc.codigo AS cuenta_codigo, pc.nombre AS cuenta_nombre,
                       l.descripcion, l.centro_costo_id, cc.nombre AS centro_costo_nombre,
                       l.base, l.tarifa_iva, l.valor_iva, l.iva_descontable,
                       l.tarifa_retencion_id, tr.concepto AS concepto_retencion,
                       l.tarifa_retefuente, l.valor_retefuente,
                       l.tarifa_reteiva, l.valor_reteiva, l.tarifa_reteica, l.valor_reteica
                FROM causacion_gasto_linea l
                LEFT JOIN plan_cuenta pc ON pc.id = l.cuenta_id
                LEFT JOIN centros_costos cc ON cc.id = l.centro_costo_id
                LEFT JOIN tarifa_retencion tr ON tr.id = l.tarifa_retencion_id
                WHERE l.causacion_id = :id
                ORDER BY l.id
                """, new MapSqlParameterSource("id", causacionId), new BeanPropertyRowMapper<>(Linea.class));
    }

    /** Cabecera con nombres, saldo de la cuenta por pagar y comprobante vigente. */
    public Detalle detalle(Long id, Integer empresaId) {
        List<Detalle> r = jdbc.query("""
                SELECT c.id, 'CG-' || c.consecutivo AS numero, c.tercero_id,
                       COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS tercero_nombre,
                       t.numero_documento AS tercero_documento,
                       c.sucursal_id, s.nombre AS sucursal_nombre,
                       c.fecha, c.fecha_vencimiento, c.tipo_soporte, c.numero_soporte, c.descripcion,
                       c.cuenta_pagar_contable_id, pc.codigo AS cuenta_pagar_codigo, pc.nombre AS cuenta_pagar_nombre,
                       c.cuenta_pagar_id, cp.saldo_pendiente,
                       c.subtotal, c.iva, c.retefuente, c.reteiva, c.reteica, c.total_pagar,
                       t.tipo_persona, c.estado, c.motivo_anulacion, c.created_at,
                       (SELECT a.numero_comprobante FROM asiento_contable a
                         WHERE a.empresa_id = c.empresa_id AND a.tipo_origen = 'CAUSACION_GASTO'
                           AND a.origen_id = c.id
                         ORDER BY a.id DESC LIMIT 1) AS comprobante
                FROM causacion_gasto c
                JOIN tercero t ON t.id = c.tercero_id
                LEFT JOIN sucursal s ON s.id = c.sucursal_id
                LEFT JOIN plan_cuenta pc ON pc.id = c.cuenta_pagar_contable_id
                LEFT JOIN cuentas_pagar cp ON cp.id = c.cuenta_pagar_id
                WHERE c.id = :id AND c.empresa_id = :empresaId
                """, new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(Detalle.class));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Filtros en {@code params}: estado (CAUSADA | ANULADA), desde, hasta (yyyy-MM-dd), terceroId. */
    public PageImpl<Fila> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 15;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.consecutivo, c.fecha, c.fecha_vencimiento,
                       COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos)) AS tercero_nombre,
                       c.tipo_soporte, c.numero_soporte, c.descripcion,
                       c.subtotal, c.iva, (c.retefuente + c.reteiva + c.reteica) AS retenciones,
                       c.total_pagar,
                       CASE WHEN c.estado = 'ANULADA' THEN 0 ELSE COALESCE(cp.saldo_pendiente, 0) END AS saldo_pendiente,
                       c.estado, COUNT(*) OVER() AS total_rows
                FROM causacion_gasto c
                JOIN tercero t ON t.id = c.tercero_id
                LEFT JOIN cuentas_pagar cp ON cp.id = c.cuenta_pagar_id
                WHERE c.empresa_id = :empresaId
                """);
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);
        sql.append(alcanceSede.filtro("c.sucursal_id", p));

        if (pageable.getParams() instanceof Map<?, ?> f) {
            Object estado = f.get("estado");
            if ("CAUSADA".equals(estado) || "ANULADA".equals(estado)) {
                sql.append(" AND c.estado = :estado ");
                p.addValue("estado", estado);
            }
            Object desde = f.get("desde");
            if (desde != null && !desde.toString().isBlank()) {
                sql.append(" AND c.fecha >= CAST(:desde AS DATE) ");
                p.addValue("desde", desde.toString().substring(0, 10));
            }
            Object hasta = f.get("hasta");
            if (hasta != null && !hasta.toString().isBlank()) {
                sql.append(" AND c.fecha <= CAST(:hasta AS DATE) ");
                p.addValue("hasta", hasta.toString().substring(0, 10));
            }
            if (f.get("terceroId") instanceof Number n) {
                sql.append(" AND c.tercero_id = :terceroId ");
                p.addValue("terceroId", n.longValue());
            }
        }
        if (!search.isEmpty()) {
            sql.append("""
                    AND (LOWER(COALESCE(c.numero_soporte, '')) LIKE :search
                         OR ('cg-' || c.consecutivo) LIKE :search
                         OR LOWER(COALESCE(c.descripcion, '')) LIKE :search
                         OR LOWER(COALESCE(t.razon_social, '')) LIKE :search
                         OR LOWER(COALESCE(t.nombres, '')) LIKE :search
                         OR COALESCE(t.numero_documento, '') LIKE :search)
                    """);
            p.addValue("search", "%" + search + "%");
        }
        sql.append(" ORDER BY c.fecha DESC, c.id DESC OFFSET :offset LIMIT :limit ");
        p.addValue("offset", (long) page * size);
        p.addValue("limit", size);

        List<Fila> list = jdbc.query(sql.toString(), p, new BeanPropertyRowMapper<>(Fila.class));
        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }
}
