package com.cloud_technological.aura_pos.repositories.permisos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.BloqueoLog;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.CambioLog;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.NodoArbol;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.PerfilFila;

/** Lecturas de permisos por perfil (V190). */
@Repository
public class PermisoUsuarioQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public UsuarioPerfil usuario(Integer usuarioId) {
        List<UsuarioPerfil> r = jdbc.query("""
            SELECT id, empresa_id, rol, perfil_id FROM usuario WHERE id = :id
            """, new MapSqlParameterSource("id", usuarioId), (rs, i) -> new UsuarioPerfil(
                rs.getInt("id"),
                rs.getObject("empresa_id") != null ? ((Number) rs.getObject("empresa_id")).intValue() : null,
                rs.getString("rol"),
                (Long) rs.getObject("perfil_id", Long.class)));
        return r.isEmpty() ? null : r.get(0);
    }

    public Perfil perfil(Long perfilId) {
        List<Perfil> r = jdbc.query("""
            SELECT id, empresa_id, nombre, acceso_total, activo FROM perfil WHERE id = :id
            """, new MapSqlParameterSource("id", perfilId), (rs, i) -> new Perfil(
                rs.getLong("id"), rs.getInt("empresa_id"), rs.getString("nombre"),
                rs.getBoolean("acceso_total"), rs.getBoolean("activo")));
        return r.isEmpty() ? null : r.get(0);
    }

    public Long perfilIdPorCodigo(Integer empresaId, String codigo) {
        List<Long> r = jdbc.queryForList("""
            SELECT id FROM perfil WHERE empresa_id = :empresaId AND codigo = :codigo
            """, new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("codigo", codigo), Long.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * Submódulos que la empresa tiene activos: su módulo y el submódulo activos y
     * habilitados para la empresa y, si cuelga de un grupo (tercer nivel), también
     * el grupo. Apagar "Asistencia" apaga todo lo que cuelga de ella.
     */
    public List<SubmoduloHabilitado> habilitadosEmpresa(Integer empresaId) {
        return jdbc.query("""
            SELECT s.id, m.codigo AS modulo_codigo, s.codigo
            FROM submodulos s
            JOIN modulos m ON m.id = s.modulo_id AND m.activo = TRUE AND m.deleted_at IS NULL
            JOIN empresa_modulo em ON em.modulo_id = m.id AND em.empresa_id = :empresaId AND em.activo = TRUE
            JOIN empresa_submodulo es ON es.submodulo_id = s.id AND es.empresa_id = :empresaId AND es.activo = TRUE
            LEFT JOIN submodulos pa ON pa.id = s.padre_id
            LEFT JOIN empresa_submodulo esp ON esp.submodulo_id = pa.id AND esp.empresa_id = :empresaId
            WHERE s.activo = TRUE AND s.deleted_at IS NULL
              AND (s.padre_id IS NULL
                   OR (pa.activo = TRUE AND pa.deleted_at IS NULL AND COALESCE(esp.activo, FALSE) = TRUE))
            ORDER BY m.orden, m.id, s.orden, s.id
            """, new MapSqlParameterSource("empresaId", empresaId), (rs, i) -> new SubmoduloHabilitado(
                rs.getLong("id"), rs.getString("modulo_codigo") + "." + rs.getString("codigo")));
    }

    /** Todos los submódulos activos de la plataforma (para PLATFORM_ADMIN). */
    public List<SubmoduloHabilitado> todosActivos() {
        return jdbc.query("""
            SELECT s.id, m.codigo AS modulo_codigo, s.codigo
            FROM submodulos s
            JOIN modulos m ON m.id = s.modulo_id AND m.activo = TRUE AND m.deleted_at IS NULL
            WHERE s.activo = TRUE AND s.deleted_at IS NULL
            ORDER BY m.orden, m.id, s.orden, s.id
            """, new MapSqlParameterSource(), (rs, i) -> new SubmoduloHabilitado(
                rs.getLong("id"), rs.getString("modulo_codigo") + "." + rs.getString("codigo")));
    }

    /** Acciones del perfil por submódulo: [ver, crear, editar, anular]. */
    public Map<Long, Boolean[]> permisosPerfil(Long perfilId) {
        Map<Long, Boolean[]> out = new HashMap<>();
        jdbc.query("""
            SELECT submodulo_id, ver, crear, editar, anular FROM perfil_permiso WHERE perfil_id = :id
            """, new MapSqlParameterSource("id", perfilId), rs -> {
                out.put(rs.getLong("submodulo_id"), new Boolean[] {
                        rs.getBoolean("ver"), rs.getBoolean("crear"), rs.getBoolean("editar"), rs.getBoolean("anular") });
            });
        return out;
    }

    /** Excepciones del usuario por submódulo: [ver, crear, editar, anular], null = lo del perfil. */
    public Map<Long, Boolean[]> excepcionesUsuario(Integer usuarioId) {
        Map<Long, Boolean[]> out = new HashMap<>();
        jdbc.query("""
            SELECT submodulo_id, ver, crear, editar, anular FROM usuario_permiso WHERE usuario_id = :id
            """, new MapSqlParameterSource("id", usuarioId), rs -> {
                out.put(rs.getLong("submodulo_id"), new Boolean[] {
                        (Boolean) rs.getObject("ver"), (Boolean) rs.getObject("crear"),
                        (Boolean) rs.getObject("editar"), (Boolean) rs.getObject("anular") });
            });
        return out;
    }

    /** Ids de submódulo por clave {@code modulo.submodulo}; las que no existen no aparecen. */
    public Map<String, Long> submodulosPorClave(List<String> claves) {
        Map<String, Long> out = new HashMap<>();
        if (claves == null || claves.isEmpty()) return out;
        jdbc.query("""
            SELECT s.id, m.codigo || '.' || s.codigo AS clave
            FROM submodulos s JOIN modulos m ON m.id = s.modulo_id
            WHERE m.codigo || '.' || s.codigo IN (:claves)
            """, new MapSqlParameterSource("claves", claves), rs -> {
                out.put(rs.getString("clave"), rs.getLong("id"));
            });
        return out;
    }

    // ── Acciones especiales, límites y sedes (V192, P6–P9) ───────────────────

    /** Catálogo de acciones especiales de los submódulos que la empresa tiene activos. */
    public List<AccionEspecial> especialesEmpresa(Integer empresaId) {
        java.util.Set<Long> habilitados = new java.util.HashSet<>();
        for (SubmoduloHabilitado s : habilitadosEmpresa(empresaId)) habilitados.add(s.id());
        if (habilitados.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT a.id, a.submodulo_id, m.codigo || '.' || s.codigo AS clave, a.codigo, a.nombre,
                   a.descripcion, a.hereda_de
            FROM permiso_accion_especial a
            JOIN submodulos s ON s.id = a.submodulo_id
            JOIN modulos m ON m.id = s.modulo_id
            WHERE a.submodulo_id IN (:ids)
            ORDER BY m.orden, m.id, s.orden, s.id, a.orden, a.id
            """, new MapSqlParameterSource("ids", habilitados), (rs, i) -> new AccionEspecial(
                rs.getLong("id"), rs.getLong("submodulo_id"), rs.getString("clave"), rs.getString("codigo"),
                rs.getString("nombre"), rs.getString("descripcion"), rs.getString("hereda_de")));
    }

    /** accionId → permitido, lo explícito del perfil. */
    public Map<Long, Boolean> especialesPerfil(Long perfilId) {
        Map<Long, Boolean> out = new HashMap<>();
        jdbc.query("SELECT accion_id, permitido FROM perfil_accion_especial WHERE perfil_id = :id",
                new MapSqlParameterSource("id", perfilId),
                rs -> { out.put(rs.getLong("accion_id"), rs.getBoolean("permitido")); });
        return out;
    }

    /** accionId → permitido, las excepciones del usuario. */
    public Map<Long, Boolean> especialesUsuario(Integer usuarioId) {
        Map<Long, Boolean> out = new HashMap<>();
        jdbc.query("SELECT accion_id, permitido FROM usuario_accion_especial WHERE usuario_id = :id",
                new MapSqlParameterSource("id", usuarioId),
                rs -> { out.put(rs.getLong("accion_id"), rs.getBoolean("permitido")); });
        return out;
    }

    /** accionId → id de la fila en perfil_accion_especial. */
    public Map<Long, Long> filasEspecialesPerfil(Long perfilId) {
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT id, accion_id FROM perfil_accion_especial WHERE perfil_id = :id",
                new MapSqlParameterSource("id", perfilId),
                rs -> { out.put(rs.getLong("accion_id"), rs.getLong("id")); });
        return out;
    }

    /** accionId → id de la fila en usuario_accion_especial. */
    public Map<Long, Long> filasEspecialesUsuario(Integer usuarioId) {
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT id, accion_id FROM usuario_accion_especial WHERE usuario_id = :id",
                new MapSqlParameterSource("id", usuarioId),
                rs -> { out.put(rs.getLong("accion_id"), rs.getLong("id")); });
        return out;
    }

    public LimitesPerfil limitesPerfil(Long perfilId) {
        List<LimitesPerfil> r = jdbc.query("""
            SELECT descuento_max_pct, rebaja_precio_max_pct, todas_sedes FROM perfil WHERE id = :id
            """, new MapSqlParameterSource("id", perfilId), (rs, i) -> new LimitesPerfil(
                rs.getBigDecimal("descuento_max_pct"), rs.getBigDecimal("rebaja_precio_max_pct"),
                rs.getBoolean("todas_sedes")));
        return r.isEmpty() ? null : r.get(0);
    }

    public LimitesUsuario limitesUsuario(Integer usuarioId) {
        List<LimitesUsuario> r = jdbc.query("""
            SELECT descuento_max_pct, rebaja_precio_max_pct FROM usuario WHERE id = :id
            """, new MapSqlParameterSource("id", usuarioId), (rs, i) -> new LimitesUsuario(
                rs.getBigDecimal("descuento_max_pct"), rs.getBigDecimal("rebaja_precio_max_pct")));
        return r.isEmpty() ? null : r.get(0);
    }

    /** Sedes asignadas al usuario (usuario_sucursal activas). */
    public List<Long> sucursalesUsuario(Integer usuarioId) {
        return jdbc.queryForList("""
            SELECT us.sucursal_id FROM usuario_sucursal us
            WHERE us.usuario_id = :id AND COALESCE(us.activo, TRUE) = TRUE
            ORDER BY us.sucursal_id
            """, new MapSqlParameterSource("id", usuarioId), Long.class);
    }

    // ── Pantalla de perfiles (P1) ────────────────────────────────────────────

    /** Árbol de submódulos que la empresa tiene activos, en orden de menú. */
    public List<NodoArbol> arbolEmpresa(Integer empresaId) {
        java.util.Set<Long> habilitados = new java.util.HashSet<>();
        for (SubmoduloHabilitado s : habilitadosEmpresa(empresaId)) habilitados.add(s.id());
        if (habilitados.isEmpty()) return List.of();
        return jdbc.query("""
            SELECT s.id, m.codigo AS modulo_codigo, m.nombre AS modulo_nombre, s.codigo, s.nombre, s.padre_id,
                   EXISTS (SELECT 1 FROM submodulos h WHERE h.padre_id = s.id) AS es_grupo
            FROM submodulos s
            JOIN modulos m ON m.id = s.modulo_id
            WHERE s.id IN (:ids)
            ORDER BY m.orden, m.id, COALESCE((SELECT p.orden FROM submodulos p WHERE p.id = s.padre_id), s.orden),
                     CASE WHEN s.padre_id IS NULL THEN 0 ELSE 1 END, s.orden, s.id
            """, new MapSqlParameterSource("ids", habilitados), (rs, i) -> {
                NodoArbol n = new NodoArbol();
                n.setSubmoduloId(rs.getLong("id"));
                n.setModuloCodigo(rs.getString("modulo_codigo"));
                n.setModuloNombre(rs.getString("modulo_nombre"));
                n.setCodigo(rs.getString("codigo"));
                n.setClave(rs.getString("modulo_codigo") + "." + rs.getString("codigo"));
                n.setNombre(rs.getString("nombre"));
                n.setPadreId((Long) rs.getObject("padre_id", Long.class));
                n.setEsGrupo(rs.getBoolean("es_grupo"));
                return n;
            });
    }

    public List<PerfilFila> perfilesEmpresa(Integer empresaId) {
        return jdbc.query("""
            SELECT p.id, p.codigo, p.nombre, p.descripcion, p.acceso_total, p.es_sistema, p.activo,
                   p.descuento_max_pct, p.rebaja_precio_max_pct, p.todas_sedes,
                   (SELECT COUNT(*) FROM usuario u WHERE u.perfil_id = p.id) AS usuarios
            FROM perfil p
            WHERE p.empresa_id = :empresaId
            ORDER BY p.es_sistema DESC, p.nombre
            """, new MapSqlParameterSource("empresaId", empresaId), (rs, i) -> mapFila(rs));
    }

    public PerfilFila perfilFila(Long perfilId, Integer empresaId) {
        List<PerfilFila> r = jdbc.query("""
            SELECT p.id, p.codigo, p.nombre, p.descripcion, p.acceso_total, p.es_sistema, p.activo,
                   p.descuento_max_pct, p.rebaja_precio_max_pct, p.todas_sedes,
                   (SELECT COUNT(*) FROM usuario u WHERE u.perfil_id = p.id) AS usuarios
            FROM perfil p
            WHERE p.id = :id AND p.empresa_id = :empresaId
            """, new MapSqlParameterSource().addValue("id", perfilId).addValue("empresaId", empresaId),
                (rs, i) -> mapFila(rs));
        return r.isEmpty() ? null : r.get(0);
    }

    public boolean nombreEnUso(Integer empresaId, String nombre, Long exceptoId) {
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM perfil
            WHERE empresa_id = :empresaId AND LOWER(TRIM(nombre)) = LOWER(TRIM(:nombre))
              AND (CAST(:excepto AS BIGINT) IS NULL OR id <> :excepto)
            """, new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("nombre", nombre)
                .addValue("excepto", exceptoId), Integer.class);
        return n != null && n > 0;
    }

    /** submoduloId → id de la fila en perfil_permiso. */
    public Map<Long, Long> filasPerfil(Long perfilId) {
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT id, submodulo_id FROM perfil_permiso WHERE perfil_id = :id",
                new MapSqlParameterSource("id", perfilId),
                rs -> { out.put(rs.getLong("submodulo_id"), rs.getLong("id")); });
        return out;
    }

    /** submoduloId → id de la fila en usuario_permiso. */
    public Map<Long, Long> filasExcepciones(Integer usuarioId) {
        Map<Long, Long> out = new HashMap<>();
        jdbc.query("SELECT id, submodulo_id FROM usuario_permiso WHERE usuario_id = :id",
                new MapSqlParameterSource("id", usuarioId),
                rs -> { out.put(rs.getLong("submodulo_id"), rs.getLong("id")); });
        return out;
    }

    public String username(Integer usuarioId) {
        List<String> r = jdbc.queryForList("SELECT username FROM usuario WHERE id = :id",
                new MapSqlParameterSource("id", usuarioId), String.class);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<CambioLog> historial(Integer empresaId, int limite) {
        return jdbc.query("""
            SELECT l.id, l.created_at, u.username AS usuario, l.tipo, p.nombre AS perfil,
                   ua.username AS usuario_afectado, l.detalle
            FROM permiso_cambio_log l
            LEFT JOIN usuario u  ON u.id = l.usuario_id
            LEFT JOIN usuario ua ON ua.id = l.usuario_afectado_id
            LEFT JOIN perfil p   ON p.id = l.perfil_id
            WHERE l.empresa_id = :empresaId
            ORDER BY l.created_at DESC, l.id DESC
            LIMIT :limite
            """, new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("limite", limite),
                (rs, i) -> {
                    CambioLog c = new CambioLog();
                    c.setId(rs.getLong("id"));
                    c.setFecha(rs.getTimestamp("created_at").toLocalDateTime());
                    c.setUsuario(rs.getString("usuario"));
                    c.setTipo(rs.getString("tipo"));
                    c.setPerfil(rs.getString("perfil"));
                    c.setUsuarioAfectado(rs.getString("usuario_afectado"));
                    c.setDetalle(rs.getString("detalle"));
                    return c;
                });
    }

    public List<BloqueoLog> bloqueos(Integer empresaId, java.time.LocalDate desde) {
        return jdbc.query("""
            SELECT b.id, b.fecha, u.username AS usuario, u.rol, p.nombre AS perfil, b.modo, b.metodo, b.ruta,
                   b.clave, b.accion, b.veces, b.ultima_vez
            FROM permiso_bloqueo_log b
            LEFT JOIN usuario u ON u.id = b.usuario_id
            LEFT JOIN perfil p  ON p.id = u.perfil_id
            WHERE b.empresa_id = :empresaId AND b.fecha >= :desde
            ORDER BY b.ultima_vez DESC
            LIMIT 500
            """, new MapSqlParameterSource().addValue("empresaId", empresaId)
                .addValue("desde", java.sql.Date.valueOf(desde)), (rs, i) -> {
                    BloqueoLog b = new BloqueoLog();
                    b.setId(rs.getLong("id"));
                    b.setFecha(rs.getDate("fecha").toLocalDate());
                    b.setUsuario(rs.getString("usuario"));
                    b.setRol(rs.getString("rol"));
                    b.setPerfil(rs.getString("perfil"));
                    b.setModo(rs.getString("modo"));
                    b.setMetodo(rs.getString("metodo"));
                    b.setRuta(rs.getString("ruta"));
                    b.setClave(rs.getString("clave"));
                    b.setAccion(rs.getString("accion"));
                    b.setVeces(rs.getInt("veces"));
                    b.setUltimaVez(rs.getTimestamp("ultima_vez").toLocalDateTime());
                    return b;
                });
    }

    private PerfilFila mapFila(java.sql.ResultSet rs) throws java.sql.SQLException {
        PerfilFila f = new PerfilFila();
        f.setId(rs.getLong("id"));
        f.setCodigo(rs.getString("codigo"));
        f.setNombre(rs.getString("nombre"));
        f.setDescripcion(rs.getString("descripcion"));
        f.setAccesoTotal(rs.getBoolean("acceso_total"));
        f.setEsSistema(rs.getBoolean("es_sistema"));
        f.setActivo(rs.getBoolean("activo"));
        f.setUsuarios(rs.getLong("usuarios"));
        f.setDescuentoMaxPct(rs.getBigDecimal("descuento_max_pct"));
        f.setRebajaPrecioMaxPct(rs.getBigDecimal("rebaja_precio_max_pct"));
        f.setTodasSedes(rs.getBoolean("todas_sedes"));
        return f;
    }

    public record UsuarioPerfil(Integer id, Integer empresaId, String rol, Long perfilId) {
    }

    public record Perfil(Long id, Integer empresaId, String nombre, boolean accesoTotal, boolean activo) {
    }

    public record SubmoduloHabilitado(Long id, String clave) {
    }

    /** Acción especial del catálogo; su clave completa es {@code clave:codigo}. */
    public record AccionEspecial(Long id, Long submoduloId, String clave, String codigo, String nombre,
            String descripcion, String heredaDe) {
        public String claveCompleta() {
            return clave + ":" + codigo;
        }
    }

    public record LimitesPerfil(java.math.BigDecimal descuentoMaxPct, java.math.BigDecimal rebajaPrecioMaxPct,
            boolean todasSedes) {
    }

    public record LimitesUsuario(java.math.BigDecimal descuentoMaxPct, java.math.BigDecimal rebajaPrecioMaxPct) {
    }
}
