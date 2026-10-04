package com.cloud_technological.aura_pos.repositories.auditoria;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.Evento;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.Filtro;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.Pagina;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.SolicitudPendiente;

/** Lecturas de la bitácora de auditoría y de las autorizaciones (V192). */
@Repository
public class BitacoraQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public Pagina pagina(Integer empresaId, Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);
        StringBuilder where = new StringBuilder(" WHERE e.empresa_id = :empresaId");
        if (f.getDesde() != null) {
            where.append(" AND e.fecha >= :desde");
            p.addValue("desde", java.sql.Timestamp.valueOf(f.getDesde().atStartOfDay()));
        }
        if (f.getHasta() != null) {
            where.append(" AND e.fecha < :hasta");
            p.addValue("hasta", java.sql.Timestamp.valueOf(f.getHasta().plusDays(1).atStartOfDay()));
        }
        if (f.getUsuarioId() != null) {
            where.append(" AND (e.usuario_id = :usuarioId OR e.autorizado_por = :usuarioId)");
            p.addValue("usuarioId", f.getUsuarioId());
        }
        if (vacio(f.getClave()) == false) {
            // "ventas" encuentra ventas.ventas, ventas.cotizaciones…; "ventas.ventas" solo esa.
            where.append(" AND (e.clave = :clave OR e.clave LIKE :clavePrefijo)");
            p.addValue("clave", f.getClave().trim());
            p.addValue("clavePrefijo", f.getClave().trim() + ".%");
        }
        if (!vacio(f.getAccion())) {
            where.append(" AND e.accion = :accion");
            p.addValue("accion", f.getAccion().trim().toUpperCase());
        }
        if (!vacio(f.getEntidad())) {
            where.append(" AND e.entidad = :entidad");
            p.addValue("entidad", f.getEntidad().trim());
        }
        if (!vacio(f.getEntidadId())) {
            where.append(" AND e.entidad_id = :entidadId");
            p.addValue("entidadId", f.getEntidadId().trim());
        }
        if (!vacio(f.getTexto())) {
            where.append(" AND (e.descripcion ILIKE :texto OR e.ruta ILIKE :texto)");
            p.addValue("texto", "%" + f.getTexto().trim() + "%");
        }
        int rows = f.getRows() != null && f.getRows() > 0 && f.getRows() <= 500 ? f.getRows() : 50;
        int page = f.getPage() != null && f.getPage() >= 0 ? f.getPage() : 0;
        p.addValue("limite", rows).addValue("salto", page * rows);

        Pagina out = new Pagina();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_evento e" + where, p, Long.class);
        out.setTotal(total != null ? total : 0);
        out.setItems(jdbc.query("""
            SELECT e.id, e.fecha, e.usuario_id, u.username AS usuario, a.username AS autorizado_por,
                   e.clave, e.accion, e.entidad, e.entidad_id, e.descripcion, e.antes, e.despues,
                   e.metodo, e.ruta, e.ip, e.origen
            FROM auditoria_evento e
            LEFT JOIN usuario u ON u.id = e.usuario_id
            LEFT JOIN usuario a ON a.id = e.autorizado_por
            """ + where + " ORDER BY e.fecha DESC, e.id DESC LIMIT :limite OFFSET :salto", p, (rs, i) -> {
                Evento e = new Evento();
                e.setId(rs.getLong("id"));
                e.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                e.setUsuarioId((Integer) rs.getObject("usuario_id", Integer.class));
                e.setUsuario(rs.getString("usuario"));
                e.setAutorizadoPor(rs.getString("autorizado_por"));
                e.setClave(rs.getString("clave"));
                e.setAccion(rs.getString("accion"));
                e.setEntidad(rs.getString("entidad"));
                e.setEntidadId(rs.getString("entidad_id"));
                e.setDescripcion(rs.getString("descripcion"));
                e.setAntes(rs.getString("antes"));
                e.setDespues(rs.getString("despues"));
                e.setMetodo(rs.getString("metodo"));
                e.setRuta(rs.getString("ruta"));
                e.setIp(rs.getString("ip"));
                e.setOrigen(rs.getString("origen"));
                return e;
            }));
        return out;
    }

    /** El código sin usar y sin vencer de la empresa con esa huella; null si no hay. */
    public Long autorizacionPorCodigo(Integer empresaId, String codigoHash) {
        List<Long> r = jdbc.queryForList("""
            SELECT id FROM autorizacion
            WHERE empresa_id = :empresaId AND codigo_hash = :hash AND estado = 'CODIGO' AND expira_en > now()
            ORDER BY id DESC LIMIT 1
            """, new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("hash", codigoHash),
                Long.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Solicitudes remotas sin responder y sin vencer de la empresa. */
    public List<SolicitudPendiente> solicitudesPendientes(Integer empresaId) {
        return jdbc.query("""
            SELECT a.id, u.username AS solicitante, a.descuento_pct, a.rebaja_pct, a.detalle, a.motivo,
                   a.created_at, a.expira_en
            FROM autorizacion a
            LEFT JOIN usuario u ON u.id = a.solicitante_id
            WHERE a.empresa_id = :empresaId AND a.estado = 'SOLICITADA' AND a.expira_en > now()
            ORDER BY a.created_at
            """, new MapSqlParameterSource("empresaId", empresaId), (rs, i) -> {
                SolicitudPendiente s = new SolicitudPendiente();
                s.setId(rs.getLong("id"));
                s.setSolicitante(rs.getString("solicitante"));
                s.setDescuentoPct(rs.getBigDecimal("descuento_pct"));
                s.setRebajaPct(rs.getBigDecimal("rebaja_pct"));
                s.setDetalle(rs.getString("detalle"));
                s.setMotivo(rs.getString("motivo"));
                s.setFecha(rs.getTimestamp("created_at").toLocalDateTime());
                s.setExpiraEn(rs.getTimestamp("expira_en").toLocalDateTime());
                return s;
            });
    }

    public String username(Integer usuarioId) {
        if (usuarioId == null) return null;
        List<String> r = jdbc.queryForList("SELECT username FROM usuario WHERE id = :id",
                new MapSqlParameterSource("id", usuarioId), String.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * El menor precio legítimo de la forma de venta, SIN IVA, para medir la rebaja:
     * precio 1–3 del producto (o el de la presentación), listas de precios activas,
     * precio especial de cliente vigente y precio por volumen. null = no tiene precio.
     *
     * <p>El precio del producto y el de la presentación vienen con IVA cuando el
     * producto es "IVA incluido" (la presentación siempre, como hace el POS); las
     * listas y precios especiales se toman como base sin IVA, igual que los aplica el POS.
     */
    public BigDecimal precioMinimoSinIva(Integer empresaId, Long productoId, Long presentacionId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("empresaId", empresaId)
                .addValue("productoId", productoId).addValue("presentacionId", presentacionId);
        List<BigDecimal> r = jdbc.queryForList("""
            WITH prod AS (
                SELECT p.id, COALESCE(p.iva_incluido, FALSE) AS iva_incluido,
                       1 + COALESCE(p.iva_porcentaje, 0) / 100.0 AS factor,
                       p.precio, p.precio_2, p.precio_3
                FROM producto p WHERE p.id = :productoId AND p.empresa_id = :empresaId
            ), candidatos AS (
                -- Unidad: precios 1–3 del producto
                SELECT CASE WHEN prod.iva_incluido THEN v / prod.factor ELSE v END AS precio
                FROM prod, LATERAL (VALUES (prod.precio), (prod.precio_2), (prod.precio_3)) AS x(v)
                WHERE CAST(:presentacionId AS BIGINT) IS NULL AND v > 0
                UNION ALL
                -- Presentación: su precio viene con IVA
                SELECT pp.precio / prod.factor
                FROM prod JOIN producto_presentacion pp ON pp.id = :presentacionId AND pp.producto_id = prod.id
                WHERE pp.precio > 0
                UNION ALL
                -- Listas de precios activas
                SELECT pr.precio
                FROM producto_precio pr
                JOIN lista_precios l ON l.id = pr.lista_precio_id AND l.empresa_id = :empresaId
                                     AND COALESCE(l.activa, TRUE) = TRUE
                WHERE pr.precio > 0
                  AND ((CAST(:presentacionId AS BIGINT) IS NULL AND pr.producto_id = :productoId
                        AND pr.producto_presentacion_id IS NULL)
                       OR pr.producto_presentacion_id = :presentacionId)
                UNION ALL
                -- Precio especial de cliente vigente (cualquier cliente: el límite no persigue al cliente)
                SELECT pc.precio_especial
                FROM precio_cliente pc
                WHERE pc.empresa_id = :empresaId AND pc.producto_presentacion_id = :presentacionId
                  AND COALESCE(pc.activo, TRUE) = TRUE AND pc.deleted_at IS NULL AND pc.precio_especial > 0
                  AND (pc.fecha_fin IS NULL OR pc.fecha_fin >= now())
                UNION ALL
                SELECT pv.precio_unitario
                FROM precio_volumen pv
                WHERE pv.empresa_id = :empresaId AND pv.producto_presentacion_id = :presentacionId
                  AND COALESCE(pv.activo, TRUE) = TRUE AND pv.precio_unitario > 0
            )
            SELECT MIN(precio) FROM candidatos
            """, p, BigDecimal.class);
        return r.isEmpty() ? null : r.get(0);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

}
