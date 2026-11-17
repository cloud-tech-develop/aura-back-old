package com.cloud_technological.aura_pos.repositories.servicio_tecnico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.AbonoDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ConsultaPublica;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.FotoDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.HistorialDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Indicadores;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ItemDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.LineaPublica;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenDetalle;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenAnterior;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenFila;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.PasoPublico;
import com.cloud_technological.aura_pos.services.implementations.CuentaCobroReglas;

/**
 * Consultas del servicio técnico. Todas filtran por la empresa del token: una orden
 * de otra empresa simplemente no aparece (el servicio responde 404).
 *
 * <p>Cuidado: los RowMapper son por nombre de columna; una columna nueva en el
 * SELECT que no exista en el DTO no viaja, sin error.
 */
@Repository
public class ServicioTecnicoQueryRepository {

    /** Nombre visible de un tercero (alias t). */
    private static final String NOMBRE_TERCERO = """
        COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''),
                 NULLIF(TRIM(COALESCE(t.nombres, '') || ' ' || COALESCE(t.apellidos, '')), ''),
                 t.numero_documento)""";

    /** Nombre visible de un usuario (alias u + tercero ut). */
    private static final String NOMBRE_USUARIO = """
        COALESCE(NULLIF(TRIM(COALESCE(ut.nombres, '') || ' ' || COALESCE(ut.apellidos, '')), ''),
                 NULLIF(TRIM(COALESCE(ut.razon_social, '')), ''), u.username)""";

    /** Estados en los que la orden sigue en el taller (para vencidas). */
    private static final String EN_TALLER = "('RECIBIDO','EN_DIAGNOSTICO','ESPERANDO_APROBACION','ESPERANDO_REPUESTO','EN_REPARACION')";

    /** Tramos del tablero de la bandeja → estados que agrupan. */
    private static final Map<String, List<String>> GRUPOS = Map.of(
            "EN_BANCO", List.of("EN_DIAGNOSTICO", "EN_REPARACION"),
            "EN_ESPERA", List.of("ESPERANDO_APROBACION", "ESPERANDO_REPUESTO"),
            "PARA_ENTREGAR", List.of("LISTO", "NO_REPARADO"));

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    // ── Bandeja ─────────────────────────────────────────────────────────

    /**
     * Página de la bandeja.
     *
     * @param usuarioId para "Mis órdenes"
     */
    public PageImpl<OrdenFila> listar(int page, int size, String search, Map<String, Object> filtro,
            Integer empresaId, Long usuarioId, LocalDate hoy) {
        StringBuilder sql = new StringBuilder("""
            SELECT o.id, o.codigo, o.cliente_id,
                   %s AS cliente_nombre,
                   COALESCE(NULLIF(o.contacto_telefono, ''), t.telefono) AS cliente_telefono,
                   e.tipo AS equipo_tipo, e.marca AS equipo_marca, e.modelo AS equipo_modelo,
                   e.imei AS equipo_imei, e.color AS equipo_color,
                   o.tecnico_id, CASE WHEN u.id IS NULL THEN NULL ELSE %s END AS tecnico_nombre,
                   o.estado, o.prioridad, o.fecha_recibido, o.fecha_estimada,
                   o.total, o.total_abonado, (o.total - o.total_abonado) AS saldo,
                   (o.fecha_estimada IS NOT NULL AND o.fecha_estimada < :hoy AND o.estado IN %s) AS vencida,
                   (SELECT COUNT(*) FROM servicio_orden_foto f WHERE f.orden_id = o.id)::int AS fotos,
                   COUNT(*) OVER() AS total_rows
            FROM servicio_orden o
            JOIN tercero t ON t.id = o.cliente_id
            LEFT JOIN servicio_orden_equipo e ON e.orden_id = o.id
            LEFT JOIN usuario u ON u.id = o.tecnico_id
            LEFT JOIN tercero ut ON ut.id = u.tercero_id
            WHERE o.empresa_id = :empresaId
            """.formatted(NOMBRE_TERCERO, NOMBRE_USUARIO, EN_TALLER));
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId).addValue("hoy", hoy);

        String estado = texto(filtro, "estado");
        if ("ABIERTAS".equals(estado)) {
            sql.append(" AND o.estado NOT IN ('ENTREGADO','CANCELADO') ");
        } else if (GRUPOS.containsKey(estado)) {
            // Tramos del tablero del taller: varios estados a la vez.
            sql.append(" AND o.estado IN (:grupo) ");
            p.addValue("grupo", GRUPOS.get(estado));
        } else if (estado != null) {
            sql.append(" AND o.estado = :estado ");
            p.addValue("estado", estado);
        }
        Object tecnico = filtro != null ? filtro.get("tecnicoId") : null;
        if (tecnico != null && !tecnico.toString().isBlank()) {
            sql.append(" AND o.tecnico_id = :tecnicoId ");
            p.addValue("tecnicoId", Long.valueOf(tecnico.toString()));
        }
        if (verdadero(filtro, "soloMias")) {
            sql.append(" AND o.tecnico_id = :usuarioId ");
            p.addValue("usuarioId", usuarioId);
        }
        if (verdadero(filtro, "soloVencidas"))
            sql.append(" AND o.fecha_estimada IS NOT NULL AND o.fecha_estimada < :hoy AND o.estado IN " + EN_TALLER);
        String desde = texto(filtro, "desde");
        if (desde != null) {
            sql.append(" AND o.fecha_recibido >= CAST(:desde AS date) ");
            p.addValue("desde", desde.substring(0, Math.min(10, desde.length())));
        }
        String hasta = texto(filtro, "hasta");
        if (hasta != null) {
            sql.append(" AND o.fecha_recibido < CAST(:hasta AS date) + 1 ");
            p.addValue("hasta", hasta.substring(0, Math.min(10, hasta.length())));
        }
        if (search != null && !search.isBlank()) {
            sql.append("""
                AND (LOWER(o.codigo) LIKE :q
                  OR LOWER(%s) LIKE :q
                  OR t.numero_documento LIKE :q
                  OR COALESCE(o.contacto_telefono, t.telefono, '') LIKE :q
                  OR COALESCE(e.imei, '') LIKE :q OR COALESCE(e.imei2, '') LIKE :q
                  OR LOWER(COALESCE(e.serial, '')) LIKE :q
                  OR LOWER(COALESCE(e.marca, '') || ' ' || COALESCE(e.modelo, '')) LIKE :q)
                """.formatted(NOMBRE_TERCERO));
            p.addValue("q", "%" + search.trim().toLowerCase() + "%");
        }
        sql.append("""
             ORDER BY CASE WHEN o.estado IN ('ENTREGADO','CANCELADO') THEN 1 ELSE 0 END,
                      CASE WHEN o.prioridad = 'URGENTE' THEN 0 ELSE 1 END,
                      o.fecha_recibido DESC
             OFFSET :offset LIMIT :limit
            """);
        p.addValue("offset", page * size).addValue("limit", size);

        List<OrdenFila> filas = jdbc.query(sql.toString(), p, new BeanPropertyRowMapper<>(OrdenFila.class));
        long total = filas.isEmpty() ? 0 : filas.get(0).getTotalRows();
        return new PageImpl<>(filas, PageRequest.of(page, size), total);
    }

    /** Indicadores de la bandeja. */
    public Indicadores indicadores(Integer empresaId, LocalDate hoy) {
        String sql = """
            SELECT
              COUNT(*) FILTER (WHERE estado NOT IN ('ENTREGADO','CANCELADO'))                     AS abiertas,
              COUNT(*) FILTER (WHERE estado = 'RECIBIDO')                                         AS recibidas,
              COUNT(*) FILTER (WHERE estado IN ('EN_DIAGNOSTICO','EN_REPARACION'))                AS en_proceso,
              COUNT(*) FILTER (WHERE estado IN ('ESPERANDO_APROBACION','ESPERANDO_REPUESTO'))     AS esperando_cliente,
              COUNT(*) FILTER (WHERE estado IN ('LISTO','NO_REPARADO'))                           AS listas,
              COUNT(*) FILTER (WHERE fecha_estimada IS NOT NULL AND fecha_estimada < :hoy
                                 AND estado IN %s)                                                AS vencidas,
              COUNT(*) FILTER (WHERE estado = 'ENTREGADO'
                                 AND fecha_entregado >= date_trunc('month', CAST(:hoy AS date)))  AS entregadas_mes
            FROM servicio_orden
            WHERE empresa_id = :empresaId
            """.formatted(EN_TALLER);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("empresaId", empresaId).addValue("hoy", hoy),
                new BeanPropertyRowMapper<>(Indicadores.class));
    }

    // ── Detalle ─────────────────────────────────────────────────────────

    /** Cabecera de la orden; null si no es de la empresa. Las listas se cargan aparte. */
    public OrdenDetalle detalle(Long id, Integer empresaId) {
        String sql = """
            SELECT o.id, o.codigo, o.numero, o.cliente_id,
                   %s AS cliente_nombre, t.numero_documento AS cliente_documento,
                   t.telefono AS cliente_telefono, t.email AS cliente_email,
                   o.contacto_telefono, o.contacto_email,
                   o.tecnico_id, CASE WHEN u.id IS NULL THEN NULL ELSE %s END AS tecnico_nombre,
                   o.estado, o.prioridad, o.fecha_recibido, o.fecha_estimada, o.fecha_entregado,
                   o.diagnostico, o.trabajo_realizado, o.observaciones, o.garantia_dias,
                   CASE WHEN o.fecha_entregado IS NOT NULL AND o.garantia_dias > 0
                        THEN CAST(o.fecha_entregado AS date) + o.garantia_dias END AS garantia_hasta,
                   o.total_repuestos, o.total_mano_obra, o.total, o.total_abonado,
                   (o.total - o.total_abonado) AS saldo, o.motivo_cancelacion, o.created_at,
                   o.factura_venta_id, fv.estado AS factura_estado, o.venta_id,
                   CASE WHEN v.id IS NOT NULL THEN %s END AS factura_numero,
                   cxc.saldo_pendiente AS saldo_cartera,
                   o.token_publico, o.aprobado_cliente_at
            FROM servicio_orden o
            JOIN tercero t ON t.id = o.cliente_id
            LEFT JOIN usuario u ON u.id = o.tecnico_id
            LEFT JOIN tercero ut ON ut.id = u.tercero_id
            LEFT JOIN factura_venta fv ON fv.id = o.factura_venta_id
            LEFT JOIN venta v ON v.id = COALESCE(o.venta_id, fv.venta_id)
            LEFT JOIN cuentas_cobrar cxc ON cxc.venta_id = v.id AND cxc.deleted_at IS NULL
            WHERE o.id = :id AND o.empresa_id = :empresaId
            """.formatted(NOMBRE_TERCERO, NOMBRE_USUARIO, CuentaCobroReglas.SQL_NUMERO);
        try {
            return jdbc.queryForObject(sql, new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                    new BeanPropertyRowMapper<>(OrdenDetalle.class));
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** Id del equipo de la orden; null si no tiene. */
    public Long equipoId(Long ordenId, Integer empresaId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM servicio_orden_equipo WHERE orden_id = :o AND empresa_id = :e",
                new MapSqlParameterSource("o", ordenId).addValue("e", empresaId), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public List<ItemDto> items(Long ordenId, Integer empresaId) {
        String sql = """
            SELECT i.id, i.tipo, i.producto_id, p.sku AS producto_sku, i.descripcion,
                   i.cantidad, i.valor_unitario, i.total
            FROM servicio_orden_item i
            LEFT JOIN producto p ON p.id = i.producto_id
            WHERE i.orden_id = :o AND i.empresa_id = :e
            ORDER BY CASE i.tipo WHEN 'REPUESTO' THEN 0 WHEN 'MANO_OBRA' THEN 1 ELSE 2 END, i.id
            """;
        return jdbc.query(sql, new MapSqlParameterSource("o", ordenId).addValue("e", empresaId),
                new BeanPropertyRowMapper<>(ItemDto.class));
    }

    public List<FotoDto> fotos(Long ordenId, Integer empresaId) {
        String sql = """
            SELECT id, nombre, mime, tamano_bytes, momento, created_at
            FROM servicio_orden_foto
            WHERE orden_id = :o AND empresa_id = :e
            ORDER BY created_at, id
            """;
        return jdbc.query(sql, new MapSqlParameterSource("o", ordenId).addValue("e", empresaId),
                new BeanPropertyRowMapper<>(FotoDto.class));
    }

    public int contarFotos(Long ordenId, Integer empresaId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM servicio_orden_foto WHERE orden_id = :o AND empresa_id = :e",
                new MapSqlParameterSource("o", ordenId).addValue("e", empresaId), Integer.class);
        return n == null ? 0 : n;
    }

    public List<HistorialDto> historial(Long ordenId, Integer empresaId) {
        String sql = """
            SELECT h.id, h.tipo, h.estado_anterior, h.estado_nuevo, h.nota, h.created_at,
                   CASE WHEN u.id IS NULL THEN NULL ELSE %s END AS usuario_nombre
            FROM servicio_orden_historial h
            LEFT JOIN usuario u ON u.id = h.usuario_id
            LEFT JOIN tercero ut ON ut.id = u.tercero_id
            WHERE h.orden_id = :o AND h.empresa_id = :e
            ORDER BY h.created_at DESC, h.id DESC
            """.formatted(NOMBRE_USUARIO);
        return jdbc.query(sql, new MapSqlParameterSource("o", ordenId).addValue("e", empresaId),
                new BeanPropertyRowMapper<>(HistorialDto.class));
    }

    /** Abonos de la orden con el número del recibo de caja. */
    public List<AbonoDto> abonos(Long ordenId, Integer empresaId) {
        String sql = """
            SELECT a.id, a.recibo_caja_id, r.numero AS recibo_numero, a.monto, a.metodo_pago, a.estado,
                   a.created_at, CASE WHEN u.id IS NULL THEN NULL ELSE %s END AS usuario_nombre
            FROM servicio_orden_abono a
            LEFT JOIN recibo_caja r ON r.id = a.recibo_caja_id
            LEFT JOIN usuario u ON u.id = a.usuario_id
            LEFT JOIN tercero ut ON ut.id = u.tercero_id
            WHERE a.orden_id = :o AND a.empresa_id = :e
            ORDER BY a.created_at, a.id
            """.formatted(NOMBRE_USUARIO);
        return jdbc.query(sql, new MapSqlParameterSource("o", ordenId).addValue("e", empresaId),
                new BeanPropertyRowMapper<>(AbonoDto.class));
    }

    /** Suma de los abonos activos. */
    public BigDecimal totalAbonado(Long ordenId, Integer empresaId) {
        BigDecimal t = jdbc.queryForObject("""
            SELECT COALESCE(SUM(monto), 0) FROM servicio_orden_abono
             WHERE orden_id = :o AND empresa_id = :e AND estado = 'ACTIVO'
            """, new MapSqlParameterSource("o", ordenId).addValue("e", empresaId), BigDecimal.class);
        return t == null ? BigDecimal.ZERO : t;
    }

    /** Anticipos de los abonos activos que todavía tienen saldo: [anticipoId, saldo]. */
    public List<AnticipoAbono> anticiposConSaldo(Long ordenId, Integer empresaId) {
        return jdbc.query("""
            SELECT an.id, an.saldo
            FROM servicio_orden_abono a
            JOIN anticipo an ON an.id = a.anticipo_id AND an.empresa_id = a.empresa_id
            WHERE a.orden_id = :o AND a.empresa_id = :e AND a.estado = 'ACTIVO'
              AND an.estado = 'ACTIVO' AND an.saldo > 0
            ORDER BY an.id
            """, new MapSqlParameterSource("o", ordenId).addValue("e", empresaId),
                (rs, i) -> new AnticipoAbono(rs.getLong("id"), rs.getBigDecimal("saldo")));
    }

    public record AnticipoAbono(Long anticipoId, BigDecimal saldo) {
    }

    /** Porcentaje de IVA del producto (para facturar con el valor de la orden como precio final). */
    public BigDecimal ivaProducto(Long productoId, Integer empresaId) {
        List<BigDecimal> r = jdbc.queryForList(
                "SELECT COALESCE(iva_porcentaje, 0) FROM producto WHERE id = :id AND empresa_id = :e",
                new MapSqlParameterSource("id", productoId).addValue("e", empresaId), BigDecimal.class);
        return r.isEmpty() ? BigDecimal.ZERO : r.get(0);
    }

    public String nombreProducto(Long productoId, Integer empresaId) {
        if (productoId == null) return null;
        List<String> r = jdbc.queryForList("SELECT nombre FROM producto WHERE id = :id AND empresa_id = :e",
                new MapSqlParameterSource("id", productoId).addValue("e", empresaId), String.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Otras órdenes del mismo equipo (IMEI 1 o 2), las más recientes primero. */
    public List<OrdenAnterior> anteriores(Long ordenId, String imei, String imei2, Integer empresaId) {
        if ((imei == null || imei.isBlank()) && (imei2 == null || imei2.isBlank())) return List.of();
        String sql = """
            SELECT o.id, o.codigo, o.estado, o.fecha_recibido, e.problemas_reportados, o.trabajo_realizado
            FROM servicio_orden o
            JOIN servicio_orden_equipo e ON e.orden_id = o.id
            WHERE o.empresa_id = :e AND o.id <> :o
              AND (e.imei IN (:imeis) OR e.imei2 IN (:imeis))
            ORDER BY o.fecha_recibido DESC
            LIMIT 10
            """;
        List<String> imeis = new ArrayList<>();
        if (imei != null && !imei.isBlank()) imeis.add(imei);
        if (imei2 != null && !imei2.isBlank()) imeis.add(imei2);
        return jdbc.query(sql, new MapSqlParameterSource("e", empresaId).addValue("o", ordenId).addValue("imeis", imeis),
                new BeanPropertyRowMapper<>(OrdenAnterior.class));
    }

    // ── Consulta pública ────────────────────────────────────────────────

    /** Orden por su clave pública; null si no existe. Sin datos personales del cliente. */
    public ConsultaPublica consultaPublica(String token) {
        String sql = """
            SELECT COALESCE(NULLIF(TRIM(em.nombre_comercial), ''), em.razon_social) AS empresa_nombre,
                   em.telefono AS empresa_telefono, em.logo_url AS empresa_logo_url,
                   o.codigo, split_part(TRIM(COALESCE(NULLIF(t.nombres, ''), t.razon_social, '')), ' ', 1) AS cliente_nombre,
                   o.estado, e.tipo AS equipo_tipo, e.marca AS equipo_marca, e.modelo AS equipo_modelo,
                   e.color AS equipo_color, RIGHT(e.imei, 4) AS imei_final,
                   e.problemas_reportados, o.diagnostico, o.trabajo_realizado,
                   o.fecha_recibido, o.fecha_estimada, o.fecha_entregado,
                   o.total,
                   -- Con factura, lo pagado y el saldo salen de la cartera (abonos cruzados + cobro).
                   CASE WHEN cxc.id IS NOT NULL THEN o.total - cxc.saldo_pendiente ELSE o.total_abonado END AS total_abonado,
                   COALESCE(cxc.saldo_pendiente, o.total - o.total_abonado) AS saldo,
                   o.garantia_dias,
                   o.aprobado_cliente_at, (o.estado = 'ESPERANDO_APROBACION') AS puede_aprobar,
                   c.condiciones
            FROM servicio_orden o
            JOIN empresa em ON em.id = o.empresa_id
            JOIN tercero t ON t.id = o.cliente_id
            LEFT JOIN servicio_orden_equipo e ON e.orden_id = o.id
            LEFT JOIN servicio_tecnico_config c ON c.empresa_id = o.empresa_id
            LEFT JOIN cuentas_cobrar cxc ON cxc.venta_id = o.venta_id AND cxc.deleted_at IS NULL
            WHERE o.token_publico = :token
            """;
        List<ConsultaPublica> r = jdbc.query(sql, new MapSqlParameterSource("token", token),
                new BeanPropertyRowMapper<>(ConsultaPublica.class));
        return r.isEmpty() ? null : r.get(0);
    }

    public List<LineaPublica> lineasPublicas(String token) {
        return jdbc.query("""
            SELECT i.tipo, i.descripcion, i.cantidad, i.total
            FROM servicio_orden_item i
            JOIN servicio_orden o ON o.id = i.orden_id AND o.empresa_id = i.empresa_id
            WHERE o.token_publico = :token
            ORDER BY CASE i.tipo WHEN 'REPUESTO' THEN 0 WHEN 'MANO_OBRA' THEN 1 ELSE 2 END, i.id
            """, new MapSqlParameterSource("token", token), new BeanPropertyRowMapper<>(LineaPublica.class));
    }

    public List<PasoPublico> pasosPublicos(String token) {
        return jdbc.query("""
            SELECT h.estado_nuevo AS estado, h.created_at AS fecha
            FROM servicio_orden_historial h
            JOIN servicio_orden o ON o.id = h.orden_id AND o.empresa_id = h.empresa_id
            WHERE o.token_publico = :token AND h.tipo = 'ESTADO'
            ORDER BY h.created_at, h.id
            """, new MapSqlParameterSource("token", token), new BeanPropertyRowMapper<>(PasoPublico.class));
    }

    /** Id y empresa de la orden por su clave pública; null si no existe. */
    public Long ordenPorToken(String token) {
        List<Long> r = jdbc.queryForList("SELECT id FROM servicio_orden WHERE token_publico = :t",
                new MapSqlParameterSource("t", token), Long.class);
        return r.isEmpty() ? null : r.get(0);
    }

    // ── Totales y consecutivo ───────────────────────────────────────────

    /** Suma de las líneas por tipo: [repuestos, mano de obra, total]. */
    public BigDecimal[] totales(Long ordenId, Integer empresaId) {
        String sql = """
            SELECT COALESCE(SUM(total) FILTER (WHERE tipo = 'REPUESTO'), 0)  AS repuestos,
                   COALESCE(SUM(total) FILTER (WHERE tipo = 'MANO_OBRA'), 0) AS mano_obra,
                   COALESCE(SUM(total), 0)                                   AS total
            FROM servicio_orden_item
            WHERE orden_id = :o AND empresa_id = :e
            """;
        return jdbc.queryForObject(sql, new MapSqlParameterSource("o", ordenId).addValue("e", empresaId),
                (rs, i) -> new BigDecimal[] { rs.getBigDecimal("repuestos"), rs.getBigDecimal("mano_obra"),
                        rs.getBigDecimal("total") });
    }

    /** Crea la configuración de la empresa si no existe (con los valores por defecto). */
    public void asegurarConfig(Integer empresaId) {
        jdbc.update("""
            INSERT INTO servicio_tecnico_config (empresa_id, created_at)
            VALUES (:e, now())
            ON CONFLICT (empresa_id) DO NOTHING
            """, new MapSqlParameterSource("e", empresaId));
    }

    /**
     * Toma el siguiente número de orden de la empresa. El UPDATE bloquea la fila,
     * así dos recepciones al tiempo no reciben el mismo número.
     */
    public Consecutivo tomarConsecutivo(Integer empresaId) {
        asegurarConfig(empresaId);
        return jdbc.queryForObject("""
            UPDATE servicio_tecnico_config
               SET siguiente_numero = siguiente_numero + 1, updated_at = now()
             WHERE empresa_id = :e
            RETURNING siguiente_numero - 1 AS numero, prefijo, garantia_dias
            """, new MapSqlParameterSource("e", empresaId),
                (rs, i) -> new Consecutivo(rs.getInt("numero"), rs.getString("prefijo"), rs.getInt("garantia_dias")));
    }

    /** Número tomado, prefijo y garantía por defecto de la empresa. */
    public record Consecutivo(int numero, String prefijo, int garantiaDias) {
    }

    // ── Pertenencia a la empresa ────────────────────────────────────────

    public boolean terceroDeEmpresa(Long terceroId, Integer empresaId) {
        return existe("SELECT 1 FROM tercero WHERE id = :id AND empresa_id = :e", terceroId, empresaId);
    }

    public boolean usuarioDeEmpresa(Long usuarioId, Integer empresaId) {
        return existe("SELECT 1 FROM usuario WHERE id = :id AND empresa_id = :e AND activo = true", usuarioId, empresaId);
    }

    public boolean productoDeEmpresa(Long productoId, Integer empresaId) {
        return existe("SELECT 1 FROM producto WHERE id = :id AND empresa_id = :e AND deleted_at IS NULL", productoId,
                empresaId);
    }

    private boolean existe(String sql, Long id, Integer empresaId) {
        if (id == null) return false;
        return !jdbc.queryForList(sql, new MapSqlParameterSource("id", id).addValue("e", empresaId)).isEmpty();
    }

    private static String texto(Map<String, Object> filtro, String clave) {
        Object v = filtro != null ? filtro.get(clave) : null;
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    private static boolean verdadero(Map<String, Object> filtro, String clave) {
        Object v = filtro != null ? filtro.get(clave) : null;
        return v != null && Boolean.parseBoolean(v.toString());
    }
}
