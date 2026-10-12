package com.cloud_technological.aura_pos.repositories.firma_contable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lecturas de la firma contable (V199, docs/PLAN_FIRMA_CONTABLE.md).
 *
 * <p>"Acceso vigente" = el vínculo firma→cliente activo + la firma y el cliente
 * activos + el usuario activo y de la firma + (es administrador de la firma, que
 * atiende todos sus clientes, o tiene el cliente asignado). Todas las lecturas que
 * deciden si alguien entra a un cliente usan esa misma condición.
 */
@Repository
public class FirmaContableQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static final String ACCESO_VIGENTE = """
            FROM empresa_firma_cliente efc
            JOIN empresa f ON f.id = efc.firma_empresa_id AND f.activa AND f.es_firma_contable
            JOIN empresa c ON c.id = efc.cliente_empresa_id AND c.activa
            JOIN usuario u ON u.empresa_id = efc.firma_empresa_id AND u.activo
            LEFT JOIN tercero t ON t.id = u.tercero_id
            WHERE efc.activo
              -- El administrador de la firma atiende todos sus clientes; los demás, los asignados.
              AND (u.rol IN ('ADMIN', 'SUPER_ADMIN')
                   OR EXISTS (SELECT 1 FROM usuario_firma_cliente ufc
                               WHERE ufc.usuario_id = u.id AND ufc.vinculo_id = efc.id AND ufc.activo))
            """;

    // ── Inicio de sesión y selección de empresa (F3) ────────────────────────

    /** Clientes a los que el usuario de la firma puede entrar hoy, por nombre. */
    public List<EmpresaDisponible> clientesDelUsuario(Integer usuarioId) {
        return jdbc.query("""
            SELECT c.id, c.razon_social, c.nombre_comercial, c.nit, c.dv, c.logo_url, efc.id AS vinculo_id
            """ + ACCESO_VIGENTE + """
              AND u.id = :usuarioId
            ORDER BY c.razon_social
            """, new MapSqlParameterSource("usuarioId", usuarioId), FirmaContableQueryRepository::empresaDisponible);
    }

    /**
     * El acceso vigente del usuario a ese cliente, o null. Es lo que revisa
     * seleccionar-empresa y, con caché, el filtro de cada petición.
     */
    public AccesoCliente acceso(Integer usuarioId, Integer clienteEmpresaId) {
        List<AccesoCliente> r = jdbc.query("""
            SELECT efc.id AS vinculo_id, efc.firma_empresa_id, efc.cliente_empresa_id, efc.perfil_id,
                   efc.modalidad, u.rol
            """ + ACCESO_VIGENTE + """
              AND u.id = :usuarioId
              AND efc.cliente_empresa_id = :clienteId
            """, new MapSqlParameterSource().addValue("usuarioId", usuarioId).addValue("clienteId", clienteEmpresaId),
                (rs, i) -> new AccesoCliente(rs.getLong("vinculo_id"), rs.getInt("firma_empresa_id"),
                        rs.getInt("cliente_empresa_id"), (Long) rs.getObject("perfil_id", Long.class),
                        rs.getString("modalidad"), rs.getString("rol")));
        return r.isEmpty() ? null : r.get(0);
    }

    /** ¿Sigue vigente este vínculo para este usuario? (filtro por petición). */
    public boolean accesoVigente(Integer usuarioId, Long vinculoId) {
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(*)
            """ + ACCESO_VIGENTE + """
              AND u.id = :usuarioId
              AND efc.id = :vinculoId
            """, new MapSqlParameterSource().addValue("usuarioId", usuarioId).addValue("vinculoId", vinculoId),
                Integer.class);
        return n != null && n > 0;
    }

    // ── Plataforma (F2) ─────────────────────────────────────────────────────

    /** Firmas contables activas, para el buscador de "Crear empresa". */
    public List<EmpresaDisponible> firmas(String texto) {
        String t = texto == null ? "" : texto.trim();
        return jdbc.query("""
            SELECT e.id, e.razon_social, e.nombre_comercial, e.nit, e.dv, e.logo_url, NULL::BIGINT AS vinculo_id
            FROM empresa e
            WHERE e.es_firma_contable AND e.activa
              AND (:texto = '' OR e.razon_social ILIKE '%' || :texto || '%'
                   OR e.nombre_comercial ILIKE '%' || :texto || '%' OR e.nit LIKE :texto || '%')
            ORDER BY e.razon_social
            LIMIT 30
            """, new MapSqlParameterSource("texto", t), FirmaContableQueryRepository::empresaDisponible);
    }

    /** El vínculo activo del cliente (máximo uno), o null. */
    public VinculoFila vinculoActivoDelCliente(Integer clienteEmpresaId) {
        List<VinculoFila> r = jdbc.query("""
            SELECT efc.id, efc.firma_empresa_id, f.razon_social AS firma_nombre, f.nit AS firma_nit,
                   efc.cliente_empresa_id, c.razon_social AS cliente_nombre, c.nit AS cliente_nit,
                   efc.perfil_id, efc.created_at, efc.modalidad,
                   (SELECT COUNT(*) FROM usuario_firma_cliente ufc
                     WHERE ufc.vinculo_id = efc.id AND ufc.activo) AS usuarios
            FROM empresa_firma_cliente efc
            JOIN empresa f ON f.id = efc.firma_empresa_id
            JOIN empresa c ON c.id = efc.cliente_empresa_id
            WHERE efc.cliente_empresa_id = :clienteId AND efc.activo
            """, new MapSqlParameterSource("clienteId", clienteEmpresaId), FirmaContableQueryRepository::vinculo);
        return r.isEmpty() ? null : r.get(0);
    }

    // ── La firma (F4) ───────────────────────────────────────────────────────

    /** Clientes activos de la firma, con cuántos de sus usuarios los atienden. */
    public List<VinculoFila> clientesDeLaFirma(Integer firmaEmpresaId) {
        return jdbc.query("""
            SELECT efc.id, efc.firma_empresa_id, f.razon_social AS firma_nombre, f.nit AS firma_nit,
                   efc.cliente_empresa_id, c.razon_social AS cliente_nombre, c.nit AS cliente_nit,
                   efc.perfil_id, efc.created_at, efc.modalidad,
                   (SELECT COUNT(*) FROM usuario_firma_cliente ufc
                     WHERE ufc.vinculo_id = efc.id AND ufc.activo) AS usuarios
            FROM empresa_firma_cliente efc
            JOIN empresa f ON f.id = efc.firma_empresa_id
            JOIN empresa c ON c.id = efc.cliente_empresa_id
            WHERE efc.firma_empresa_id = :firmaId AND efc.activo
            ORDER BY c.razon_social
            """, new MapSqlParameterSource("firmaId", firmaEmpresaId), FirmaContableQueryRepository::vinculo);
    }

    /** Vínculos (activos) que el usuario de la firma tiene asignados. */
    public List<Long> vinculosDelUsuario(Integer usuarioId) {
        return jdbc.queryForList("""
            SELECT ufc.vinculo_id
            FROM usuario_firma_cliente ufc
            JOIN empresa_firma_cliente efc ON efc.id = ufc.vinculo_id AND efc.activo
            WHERE ufc.usuario_id = :usuarioId AND ufc.activo
            """, new MapSqlParameterSource("usuarioId", usuarioId), Long.class);
    }

    /** Fila de asignación usuario↔vínculo (activa o no), para reactivarla en vez de duplicarla. */
    public Long asignacionId(Integer usuarioId, Long vinculoId) {
        List<Long> r = jdbc.queryForList("""
            SELECT id FROM usuario_firma_cliente WHERE usuario_id = :usuarioId AND vinculo_id = :vinculoId
            """, new MapSqlParameterSource().addValue("usuarioId", usuarioId).addValue("vinculoId", vinculoId),
                Long.class);
        return r.isEmpty() ? null : r.get(0);
    }

    // ── El cliente (F5) ─────────────────────────────────────────────────────

    /** Usuarios de la firma que hoy entran al cliente (para "Firma contable con acceso"). */
    public List<UsuarioConAcceso> usuariosConAcceso(Integer clienteEmpresaId) {
        return jdbc.query("""
            SELECT u.id, u.username,
                   NULLIF(TRIM(COALESCE(t.nombres, '') || ' ' || COALESCE(t.apellidos, '')), '') AS nombre
            """ + ACCESO_VIGENTE + """
              AND efc.cliente_empresa_id = :clienteId
            ORDER BY nombre NULLS LAST, u.username
            """, new MapSqlParameterSource("clienteId", clienteEmpresaId),
                (rs, i) -> new UsuarioConAcceso(rs.getInt("id"), rs.getString("username"), rs.getString("nombre")));
    }

    // ── Clientes gestionados y solicitudes (V200, §9 del plan) ──────────────

    /** Clientes gestionados activos de la firma (contra su cupo). */
    public int gestionadosActivos(Integer firmaEmpresaId) {
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM empresa_firma_cliente
            WHERE firma_empresa_id = :firmaId AND activo AND modalidad = 'GESTIONADO'
            """, new MapSqlParameterSource("firmaId", firmaEmpresaId), Integer.class);
        return n != null ? n : 0;
    }

    /** La firma activa con ese NIT (sin dígito de verificación), o null. */
    public EmpresaDisponible firmaPorNit(String nit) {
        List<EmpresaDisponible> r = jdbc.query("""
            SELECT e.id, e.razon_social, e.nombre_comercial, e.nit, e.dv, e.logo_url, NULL::BIGINT AS vinculo_id
            FROM empresa e
            WHERE e.es_firma_contable AND e.activa
              AND regexp_replace(e.nit, '[^0-9]', '', 'g') = :nit
            """, new MapSqlParameterSource("nit", nit), FirmaContableQueryRepository::empresaDisponible);
        return r.isEmpty() ? null : r.get(0);
    }

    private static final String SOLICITUD_SELECT = """
            SELECT s.id, s.cliente_empresa_id, c.razon_social AS cliente_nombre, c.nit AS cliente_nit,
                   s.firma_empresa_id, f.razon_social AS firma_nombre, f.nit AS firma_nit,
                   s.estado, s.solicitado_at, s.respondido_at, s.motivo
            FROM solicitud_firma_cliente s
            JOIN empresa c ON c.id = s.cliente_empresa_id
            JOIN empresa f ON f.id = s.firma_empresa_id
            """;

    /** La última solicitud del cliente (pendiente o ya respondida), o null. */
    public SolicitudFila ultimaSolicitudDelCliente(Integer clienteEmpresaId) {
        List<SolicitudFila> r = jdbc.query(SOLICITUD_SELECT + """
            WHERE s.cliente_empresa_id = :clienteId
            ORDER BY s.id DESC
            LIMIT 1
            """, new MapSqlParameterSource("clienteId", clienteEmpresaId), FirmaContableQueryRepository::solicitud);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Solicitudes pendientes que recibió la firma, la más antigua primero. */
    public List<SolicitudFila> solicitudesPendientesDeLaFirma(Integer firmaEmpresaId) {
        return jdbc.query(SOLICITUD_SELECT + """
            WHERE s.firma_empresa_id = :firmaId AND s.estado = 'PENDIENTE'
            ORDER BY s.solicitado_at
            """, new MapSqlParameterSource("firmaId", firmaEmpresaId), FirmaContableQueryRepository::solicitud);
    }

    /** Pendiente del cliente, o null. */
    public SolicitudFila solicitudPendienteDelCliente(Integer clienteEmpresaId) {
        List<SolicitudFila> r = jdbc.query(SOLICITUD_SELECT + """
            WHERE s.cliente_empresa_id = :clienteId AND s.estado = 'PENDIENTE'
            """, new MapSqlParameterSource("clienteId", clienteEmpresaId), FirmaContableQueryRepository::solicitud);
        return r.isEmpty() ? null : r.get(0);
    }

    private static SolicitudFila solicitud(ResultSet rs, int i) throws SQLException {
        return new SolicitudFila(rs.getLong("id"), rs.getInt("cliente_empresa_id"), rs.getString("cliente_nombre"),
                rs.getString("cliente_nit"), rs.getInt("firma_empresa_id"), rs.getString("firma_nombre"),
                rs.getString("firma_nit"), rs.getString("estado"), rs.getObject("solicitado_at", LocalDateTime.class),
                rs.getObject("respondido_at", LocalDateTime.class), rs.getString("motivo"));
    }

    // ── Mapeo ───────────────────────────────────────────────────────────────

    private static EmpresaDisponible empresaDisponible(ResultSet rs, int i) throws SQLException {
        return new EmpresaDisponible(rs.getInt("id"), rs.getString("razon_social"), rs.getString("nombre_comercial"),
                rs.getString("nit"), rs.getString("dv"), rs.getString("logo_url"),
                (Long) rs.getObject("vinculo_id", Long.class));
    }

    private static VinculoFila vinculo(ResultSet rs, int i) throws SQLException {
        return new VinculoFila(rs.getLong("id"), rs.getInt("firma_empresa_id"), rs.getString("firma_nombre"),
                rs.getString("firma_nit"), rs.getInt("cliente_empresa_id"), rs.getString("cliente_nombre"),
                rs.getString("cliente_nit"), (Long) rs.getObject("perfil_id", Long.class),
                rs.getObject("created_at", LocalDateTime.class), rs.getInt("usuarios"), rs.getString("modalidad"));
    }

    // ── Filas ───────────────────────────────────────────────────────────────

    /** Una empresa que el usuario puede elegir; {@code vinculoId} null = su propia empresa o una firma. */
    public record EmpresaDisponible(Integer id, String razonSocial, String nombreComercial, String nit, String dv,
            String logoUrl, Long vinculoId) {
    }

    /**
     * Acceso de un usuario de la firma a un cliente. {@code rolEnFirma} es su rol en la
     * firma: en un cliente gestionado, los administradores de la firma lo administran.
     */
    public record AccesoCliente(Long vinculoId, Integer firmaEmpresaId, Integer clienteEmpresaId, Long perfilId,
            String modalidad, String rolEnFirma) {

        public boolean gestionado() {
            return "GESTIONADO".equals(modalidad);
        }

        /** Administrador de la firma en un cliente que la firma gestiona: entra como ADMIN. */
        public boolean administraElCliente() {
            return gestionado() && ("ADMIN".equalsIgnoreCase(rolEnFirma) || "SUPER_ADMIN".equalsIgnoreCase(rolEnFirma));
        }
    }

    public record VinculoFila(Long id, Integer firmaEmpresaId, String firmaNombre, String firmaNit,
            Integer clienteEmpresaId, String clienteNombre, String clienteNit, Long perfilId,
            LocalDateTime desde, int usuarios, String modalidad) {
    }

    /** Solicitud de un cliente a una firma (V200). */
    public record SolicitudFila(Long id, Integer clienteEmpresaId, String clienteNombre, String clienteNit,
            Integer firmaEmpresaId, String firmaNombre, String firmaNit, String estado,
            LocalDateTime solicitadoAt, LocalDateTime respondidoAt, String motivo) {
    }

    public record UsuarioConAcceso(Integer id, String username, String nombre) {
    }
}
