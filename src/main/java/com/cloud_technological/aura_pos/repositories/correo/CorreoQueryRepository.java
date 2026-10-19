package com.cloud_technological.aura_pos.repositories.correo;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.correo.CorreoDtos;
import com.cloud_technological.aura_pos.services.implementations.CuentaCobroReglas;

/** Lecturas del envío de documentos por correo (V229, docs/PLAN_CUENTA_COBRO_CORREO.md). */
@Repository
public class CorreoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Correo del tercero para documentos: el de facturación electrónica o el principal. */
    private static final String CORREO_TERCERO = "NULLIF(TRIM(COALESCE(NULLIF(TRIM(t.email_fe), ''), t.email)), '')";

    private static final String NOMBRE_TERCERO = """
            COALESCE(NULLIF(TRIM(t.razon_social), ''),
                     NULLIF(TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)), ''), t.numero_documento)""";

    // ── Datos del documento para armar el correo ────────────────────────────

    /**
     * Documento que se envía: número, cliente o empleado y su correo. Null si no es
     * de la empresa.
     */
    public DocumentoCorreo factura(Long facturaId, Integer empresaId) {
        List<DocumentoCorreo> r = jdbc.query("""
            SELECT f.id, f.tipo,
            """ + "       CASE WHEN v.id IS NOT NULL THEN " + CuentaCobroReglas.SQL_NUMERO + " END AS numero,\n"
            + NOMBRE_TERCERO + " AS nombre, " + CORREO_TERCERO + """
                   AS correo
              FROM factura_venta f
              JOIN tercero t ON t.id = f.cliente_id
              LEFT JOIN venta v ON v.id = f.venta_id
             WHERE f.id = :id AND f.empresa_id = :empresaId
            """, new MapSqlParameterSource("id", facturaId).addValue("empresaId", empresaId),
                CorreoQueryRepository::documento);
        return r.isEmpty() ? null : r.get(0);
    }

    public DocumentoCorreo cotizacion(Long cotizacionId, Integer empresaId) {
        List<DocumentoCorreo> r = jdbc.query("""
            SELECT c.id, 'COTIZACION' AS tipo, c.numero,
            """ + NOMBRE_TERCERO + " AS nombre, " + CORREO_TERCERO + """
                   AS correo
              FROM cotizacion c
              LEFT JOIN tercero t ON t.id = c.tercero_id
             WHERE c.id = :id AND c.empresa_id = :empresaId
            """, new MapSqlParameterSource("id", cotizacionId).addValue("empresaId", empresaId),
                CorreoQueryRepository::documento);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Nóminas de la empresa con el empleado, su correo y el período. */
    public List<DocumentoCorreo> nominas(List<Long> nominaIds, Integer empresaId) {
        if (nominaIds == null || nominaIds.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT n.id, 'NOMINA' AS tipo,
                   TO_CHAR(p.fecha_inicio, 'DD/MM/YYYY') || ' al ' || TO_CHAR(p.fecha_fin, 'DD/MM/YYYY') AS numero,
            """ + NOMBRE_TERCERO + " AS nombre, " + CORREO_TERCERO + """
                   AS correo
              FROM nomina n
              JOIN empleados e ON e.id = n.empleado_id
              LEFT JOIN tercero t ON t.id = e.tercero_id
              LEFT JOIN periodo_nomina p ON p.id = n.periodo_id
             WHERE n.id IN (:ids) AND n.empresa_id = :empresaId
            """, new MapSqlParameterSource("ids", nominaIds).addValue("empresaId", empresaId),
                CorreoQueryRepository::documento);
    }

    // ── Cartera: cobro por correo ───────────────────────────────────────────

    private static final String SALDO_CLIENTE = """
            FROM cuentas_cobrar cc
            WHERE cc.empresa_id = :empresaId AND cc.deleted_at IS NULL
              AND cc.estado NOT IN ('pagada','anulada','castigada') AND cc.saldo_pendiente > 0
            """;

    /** Cliente con su correo, su saldo y lo vencido; null si no es de la empresa. */
    public ClienteCobro clienteCobro(Long terceroId, Integer empresaId) {
        List<ClienteCobro> r = jdbc.query("SELECT t.id, " + NOMBRE_TERCERO + " AS nombre, " + CORREO_TERCERO
                + """
                   AS correo,
                   (SELECT COALESCE(SUM(cc.saldo_pendiente), 0) """ + SALDO_CLIENTE + """
                       AND cc.tercero_id = t.id) AS saldo,
                   (SELECT COALESCE(SUM(cc.saldo_pendiente), 0) """ + SALDO_CLIENTE + """
                       AND cc.tercero_id = t.id AND cc.fecha_vencimiento::date < CURRENT_DATE) AS vencido
              FROM tercero t
             WHERE t.id = :id AND t.empresa_id = :empresaId
            """, new MapSqlParameterSource("id", terceroId).addValue("empresaId", empresaId),
                CorreoQueryRepository::clienteCobro);
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * Clientes con facturas vencidas (fuera de un acuerdo de pago vivo), el de más
     * deuda vencida primero. Incluye los que no tienen correo, para avisarlos.
     */
    public List<ClienteCobro> clientesVencidos(Integer empresaId) {
        return jdbc.query("SELECT t.id, " + NOMBRE_TERCERO + " AS nombre, " + CORREO_TERCERO + """
                   AS correo,
                   SUM(cc.saldo_pendiente) AS saldo,
                   SUM(cc.saldo_pendiente) FILTER (WHERE cc.fecha_vencimiento::date < CURRENT_DATE) AS vencido
              FROM cuentas_cobrar cc
              JOIN tercero t ON t.id = cc.tercero_id
             WHERE cc.empresa_id = :empresaId AND cc.deleted_at IS NULL
               AND cc.estado NOT IN ('pagada','anulada','castigada') AND cc.saldo_pendiente > 0
               AND NOT EXISTS (SELECT 1 FROM acuerdo_pago_cuenta apc
                                WHERE apc.cuenta_cobrar_id = cc.id AND apc.activo)
             GROUP BY t.id, t.razon_social, t.nombres, t.apellidos, t.numero_documento, t.email_fe, t.email
            HAVING SUM(cc.saldo_pendiente) FILTER (WHERE cc.fecha_vencimiento::date < CURRENT_DATE) > 0
             ORDER BY vencido DESC
            """, new MapSqlParameterSource("empresaId", empresaId), CorreoQueryRepository::clienteCobro);
    }

    /** Nombre para mostrar de la empresa, su logo y su correo (Reply-To). */
    public EmpresaCorreo empresa(Integer empresaId) {
        List<EmpresaCorreo> r = jdbc.query("""
            SELECT COALESCE(NULLIF(TRIM(e.nombre_comercial), ''), e.razon_social) AS nombre,
                   e.logo_url,
            """ + "       (SELECT " + CORREO_TERCERO + """
                      FROM tercero t
                     WHERE t.empresa_id = e.id AND t.numero_documento = e.nit AND t.deleted_at IS NULL
                     ORDER BY t.id LIMIT 1) AS correo
              FROM empresa e
             WHERE e.id = :empresaId
            """, new MapSqlParameterSource("empresaId", empresaId),
                (rs, i) -> new EmpresaCorreo(rs.getString("nombre"), rs.getString("logo_url"), rs.getString("correo")));
        return r.isEmpty() ? null : r.get(0);
    }

    // ── Cola ────────────────────────────────────────────────────────────────

    /**
     * Toma hasta {@code limite} correos pendientes y los marca ENVIANDO en una sola
     * sentencia: dos instancias del backend nunca toman el mismo.
     */
    public List<Long> tomarPendientes(int limite, int maxIntentos) {
        return jdbc.queryForList("""
            UPDATE correo_envio SET estado = 'ENVIANDO'
             WHERE id IN (SELECT id FROM correo_envio
                           WHERE estado = 'PENDIENTE' AND intentos < :max
                           ORDER BY id
                           LIMIT :limite
                           FOR UPDATE SKIP LOCKED)
            RETURNING id
            """, new MapSqlParameterSource("limite", limite).addValue("max", maxIntentos), Long.class);
    }

    /**
     * Al arrancar, los que quedaron ENVIANDO (el backend se apagó a mitad) vuelven a
     * la cola. Resend no los duplica: cada envío lleva su llave de idempotencia.
     */
    public int liberarAtascados() {
        return jdbc.update("UPDATE correo_envio SET estado = 'PENDIENTE' WHERE estado = 'ENVIANDO'",
                new MapSqlParameterSource());
    }

    // ── Historial ───────────────────────────────────────────────────────────

    private static final String FILA = """
            SELECT c.id, c.tipo, c.documento_id, c.destinatario, c.asunto, c.estado, c.error,
                   c.created_at, c.enviado_at,
                   COALESCE(NULLIF(TRIM(CONCAT_WS(' ', tu.nombres, tu.apellidos)), ''), u.username) AS usuario_nombre,
                   CASE WHEN c.tipo = 'NOMINA' THEN (
                       SELECT COALESCE(NULLIF(TRIM(t.razon_social), ''), NULLIF(TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)), ''))
                         FROM nomina n JOIN empleados e ON e.id = n.empleado_id
                         LEFT JOIN tercero t ON t.id = e.tercero_id
                        WHERE n.id = c.documento_id)
                        WHEN c.tipo = 'COBRO' THEN (
                       SELECT COALESCE(NULLIF(TRIM(t.razon_social), ''), NULLIF(TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)), ''))
                         FROM tercero t WHERE t.id = c.documento_id) END AS empleado_nombre
              FROM correo_envio c
              LEFT JOIN usuario u ON u.id = c.usuario_id
              LEFT JOIN tercero tu ON tu.id = u.tercero_id
            """;

    /** Correos de un documento, el más reciente primero. */
    public List<CorreoDtos.Fila> historial(Integer empresaId, List<String> tipos, Long documentoId) {
        return jdbc.query(FILA + """
             WHERE c.empresa_id = :empresaId AND c.tipo IN (:tipos) AND c.documento_id = :documentoId
             ORDER BY c.id DESC
             LIMIT 50
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("tipos", tipos)
                .addValue("documentoId", documentoId), CorreoQueryRepository::fila);
    }

    /** Correos de un lote masivo, en el orden en que se encolaron. */
    public List<CorreoDtos.Fila> lote(Integer empresaId, String loteId) {
        return jdbc.query(FILA + """
             WHERE c.empresa_id = :empresaId AND c.lote_id = :loteId
             ORDER BY c.id
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("loteId", loteId),
                CorreoQueryRepository::fila);
    }

    // ── Mapeo ───────────────────────────────────────────────────────────────

    private static DocumentoCorreo documento(ResultSet rs, int i) throws SQLException {
        return new DocumentoCorreo(rs.getLong("id"), rs.getString("tipo"), rs.getString("numero"),
                rs.getString("nombre"), rs.getString("correo"));
    }

    private static CorreoDtos.Fila fila(ResultSet rs, int i) throws SQLException {
        CorreoDtos.Fila f = new CorreoDtos.Fila();
        f.setId(rs.getLong("id"));
        f.setTipo(rs.getString("tipo"));
        f.setDocumentoId(rs.getLong("documento_id"));
        f.setDestinatario(rs.getString("destinatario"));
        f.setAsunto(rs.getString("asunto"));
        f.setEstado(rs.getString("estado"));
        f.setError(rs.getString("error"));
        f.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        f.setEnviadoAt(rs.getObject("enviado_at", LocalDateTime.class));
        f.setUsuarioNombre(rs.getString("usuario_nombre"));
        f.setEmpleadoNombre(rs.getString("empleado_nombre"));
        return f;
    }

    /**
     * Documento que se envía.
     *
     * @param tipo   FACTURA, CUENTA_COBRO, COTIZACION o NOMINA
     * @param numero número del documento (o período de la nómina); null en un borrador
     * @param correo correo del cliente o del empleado; null si no tiene
     */
    public record DocumentoCorreo(Long id, String tipo, String numero, String nombre, String correo) {
    }

    private static ClienteCobro clienteCobro(ResultSet rs, int i) throws SQLException {
        return new ClienteCobro(rs.getLong("id"), rs.getString("nombre"), rs.getString("correo"),
                rs.getBigDecimal("saldo"), rs.getBigDecimal("vencido"));
    }

    /** Cliente de cartera: correo, saldo abierto y la parte vencida. */
    public record ClienteCobro(Long id, String nombre, String correo, BigDecimal saldo,
            BigDecimal vencido) {
    }

    /** Nombre, logo y correo de la empresa que envía. */
    public record EmpresaCorreo(String nombre, String logoUrl, String correo) {
    }
}
