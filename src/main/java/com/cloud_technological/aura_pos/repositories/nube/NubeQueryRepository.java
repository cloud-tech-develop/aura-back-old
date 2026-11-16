package com.cloud_technological.aura_pos.repositories.nube;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de la nube de documentos (V236, docs/PLAN_NUBE_DOCUMENTOS.md).
 *
 * <p>Reglas que cumplen TODAS las consultas:
 * <ul>
 *   <li>Llevan {@code empresa_id = :empresaId}, y ese valor sale del token, nunca
 *       de la petición. Un ID de otra empresa no devuelve nada (el servicio
 *       responde 404, no 403, para no confirmar que existe).</li>
 *   <li>Una carpeta SOLO_FIRMA y sus archivos solo existen para la firma dueña
 *       ({@code privada_firma_id = :firmaId}, V237); para el cliente y para
 *       cualquier otra firma no existen. Las subcarpetas
 *       heredan la visibilidad de su padre (lo garantiza el servicio), así que
 *       basta mirar la carpeta directa.</li>
 * </ul>
 */
@Repository
public class NubeQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Filtro de visibilidad sobre la carpeta {@code c}. */
    private static final String VISIBLE_CARPETA = " AND (c.visibilidad = 'COMPARTIDA' OR c.privada_firma_id = :firmaId) ";

    /** Filtro de visibilidad de un archivo {@code a} por su carpeta (la raíz es compartida). */
    private static final String VISIBLE_ARCHIVO = """
             AND (a.carpeta_id IS NULL OR EXISTS (
                    SELECT 1 FROM nube_carpeta c
                     WHERE c.id = a.carpeta_id AND c.empresa_id = a.empresa_id
                       AND (c.visibilidad = 'COMPARTIDA' OR c.privada_firma_id = :firmaId)))
            """;

    private static final String COLUMNAS_CARPETA = """
            c.id, c.padre_id, c.nombre, c.visibilidad, c.privada_firma_id, c.nivel, c.creado_por, c.creado_por_firma_id,
            c.created_at, c.deleted_at
            """;

    private static final String COLUMNAS_ARCHIVO = """
            a.id, a.carpeta_id, a.nombre, a.extension, a.mime, a.tamano_bytes, a.sha256,
            a.subido_por, a.subido_por_firma_id, a.created_at, a.deleted_at
            """;

    // ── Carpetas ─────────────────────────────────────────────────────────────

    /** Carpeta viva de la empresa y visible para quien consulta. */
    public Optional<CarpetaFila> carpeta(Long id, Integer empresaId, Integer firmaId) {
        return jdbc.query("SELECT " + COLUMNAS_CARPETA + """
                  FROM nube_carpeta c
                 WHERE c.id = :id AND c.empresa_id = :empresaId AND c.deleted_at IS NULL
                """ + VISIBLE_CARPETA, params(empresaId, firmaId).addValue("id", id),
                NubeQueryRepository::carpetaFila).stream().findFirst();
    }

    /** Subcarpetas vivas de una carpeta ({@code padreId} null = raíz), por nombre. */
    public List<CarpetaFila> carpetas(Integer empresaId, Long padreId, Integer firmaId) {
        return jdbc.query("SELECT " + COLUMNAS_CARPETA + """
                  FROM nube_carpeta c
                 WHERE c.empresa_id = :empresaId AND c.deleted_at IS NULL
                   AND c.padre_id IS NOT DISTINCT FROM CAST(:padreId AS BIGINT)
                """ + VISIBLE_CARPETA + " ORDER BY lower(c.nombre)",
                params(empresaId, firmaId).addValue("padreId", padreId), NubeQueryRepository::carpetaFila);
    }

    /** ¿Ya hay una carpeta viva con ese nombre en el mismo lugar? (sin mayúsculas). */
    public boolean existeNombreCarpeta(Integer empresaId, Long padreId, String nombre, Long exceptoId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nube_carpeta
                 WHERE empresa_id = :empresaId AND deleted_at IS NULL
                   AND padre_id IS NOT DISTINCT FROM CAST(:padreId AS BIGINT)
                   AND lower(nombre) = lower(:nombre)
                   AND id IS DISTINCT FROM CAST(:exceptoId AS BIGINT)
                """, new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("padreId", padreId)
                .addValue("nombre", nombre).addValue("exceptoId", exceptoId), Integer.class);
        return n != null && n > 0;
    }

    // ── Archivos ─────────────────────────────────────────────────────────────

    /** Archivo vivo de la empresa y visible para quien consulta. */
    public Optional<ArchivoFila> archivo(Long id, Integer empresaId, Integer firmaId) {
        return jdbc.query("SELECT " + COLUMNAS_ARCHIVO + """
                  FROM nube_archivo a
                 WHERE a.id = :id AND a.empresa_id = :empresaId AND a.deleted_at IS NULL
                """ + VISIBLE_ARCHIVO, params(empresaId, firmaId).addValue("id", id),
                NubeQueryRepository::archivoFila).stream().findFirst();
    }

    /** Archivos vivos de una carpeta ({@code carpetaId} null = raíz), por nombre. */
    public List<ArchivoFila> archivos(Integer empresaId, Long carpetaId, Integer firmaId) {
        return jdbc.query("SELECT " + COLUMNAS_ARCHIVO + """
                  FROM nube_archivo a
                 WHERE a.empresa_id = :empresaId AND a.deleted_at IS NULL
                   AND a.carpeta_id IS NOT DISTINCT FROM CAST(:carpetaId AS BIGINT)
                """ + VISIBLE_ARCHIVO + " ORDER BY lower(a.nombre)",
                params(empresaId, firmaId).addValue("carpetaId", carpetaId), NubeQueryRepository::archivoFila);
    }

    /** ¿Ya hay un archivo vivo con ese nombre en la misma carpeta? (sin mayúsculas). */
    public boolean existeNombreArchivo(Integer empresaId, Long carpetaId, String nombre, Long exceptoId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM nube_archivo
                 WHERE empresa_id = :empresaId AND deleted_at IS NULL
                   AND carpeta_id IS NOT DISTINCT FROM CAST(:carpetaId AS BIGINT)
                   AND lower(nombre) = lower(:nombre)
                   AND id IS DISTINCT FROM CAST(:exceptoId AS BIGINT)
                """, new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("carpetaId", carpetaId)
                .addValue("nombre", nombre).addValue("exceptoId", exceptoId), Integer.class);
        return n != null && n > 0;
    }

    // ── Cuota ────────────────────────────────────────────────────────────────

    /**
     * Espacio ocupado por la empresa. La papelera cuenta: el archivo sigue
     * guardado hasta que se purga.
     */
    public Uso uso(Integer empresaId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(tamano_bytes), 0) AS bytes, COUNT(*) AS archivos
                  FROM nube_archivo
                 WHERE empresa_id = :empresaId
                """, new MapSqlParameterSource("empresaId", empresaId),
                (rs, i) -> new Uso(rs.getLong("bytes"), rs.getInt("archivos")));
    }

    /**
     * Crea la fila del espacio si falta y la bloquea hasta el fin de la transacción.
     * Así dos subidas al mismo tiempo no se pasan juntas de la cuota.
     */
    public Espacio bloquearEspacio(Integer empresaId) {
        MapSqlParameterSource p = new MapSqlParameterSource("empresaId", empresaId);
        jdbc.update("INSERT INTO nube_espacio (empresa_id) VALUES (:empresaId) ON CONFLICT (empresa_id) DO NOTHING", p);
        return jdbc.queryForObject("""
                SELECT cuota_mb, max_archivos, terminos_version, terminos_aceptados_at
                  FROM nube_espacio WHERE empresa_id = :empresaId FOR UPDATE
                """, p, NubeQueryRepository::espacio);
    }

    /** El espacio sin bloquear; valores por defecto si la empresa aún no tiene fila. */
    public Espacio espacio(Integer empresaId) {
        return jdbc.query("""
                SELECT cuota_mb, max_archivos, terminos_version, terminos_aceptados_at
                  FROM nube_espacio WHERE empresa_id = :empresaId
                """, new MapSqlParameterSource("empresaId", empresaId), NubeQueryRepository::espacio)
                .stream().findFirst().orElse(new Espacio(500, 5000, null, null));
    }

    // ── Árbol ────────────────────────────────────────────────────────────────

    /** Ruta de la raíz a la carpeta (incluida), solo dentro de la empresa. */
    public List<Tramo> ruta(Long carpetaId, Integer empresaId) {
        return jdbc.query("""
                WITH RECURSIVE r AS (
                    SELECT id, padre_id, nombre, 1 AS n FROM nube_carpeta
                     WHERE id = :id AND empresa_id = :empresaId
                    UNION ALL
                    SELECT c.id, c.padre_id, c.nombre, r.n + 1 FROM nube_carpeta c
                      JOIN r ON c.id = r.padre_id AND c.empresa_id = :empresaId
                     WHERE r.n < 10
                )
                SELECT id, nombre FROM r ORDER BY n DESC
                """, new MapSqlParameterSource().addValue("id", carpetaId).addValue("empresaId", empresaId),
                (rs, i) -> new Tramo(rs.getLong("id"), rs.getString("nombre")));
    }

    /** IDs de la carpeta y todas sus descendientes (vivas o no), con su profundidad relativa (1 = ella). */
    public List<Nodo> subarbol(Long carpetaId, Integer empresaId) {
        return jdbc.query("""
                WITH RECURSIVE r AS (
                    SELECT id, visibilidad, privada_firma_id, deleted_at, 1 AS prof FROM nube_carpeta
                     WHERE id = :id AND empresa_id = :empresaId
                    UNION ALL
                    SELECT c.id, c.visibilidad, c.privada_firma_id, c.deleted_at, r.prof + 1 FROM nube_carpeta c
                      JOIN r ON c.padre_id = r.id AND c.empresa_id = :empresaId
                     WHERE r.prof < 10
                )
                SELECT id, visibilidad, privada_firma_id, deleted_at, prof FROM r
                """, new MapSqlParameterSource().addValue("id", carpetaId).addValue("empresaId", empresaId),
                (rs, i) -> new Nodo(rs.getLong("id"), rs.getString("visibilidad"),
                        rs.getObject("privada_firma_id", Integer.class),
                        rs.getObject("deleted_at", LocalDateTime.class), rs.getInt("prof")));
    }

    /** Suma {@code delta} al nivel de esas carpetas (al moverlas de profundidad). */
    public void desplazarNivel(Integer empresaId, List<Long> ids, int delta) {
        if (ids.isEmpty() || delta == 0) return;
        jdbc.update("UPDATE nube_carpeta SET nivel = nivel + :delta, updated_at = now() "
                + "WHERE empresa_id = :empresaId AND id IN (:ids)",
                new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("ids", ids).addValue("delta", delta));
    }

    /** Vuelve SOLO_FIRMA de esa firma esas carpetas (al quedar dentro de una privada o al marcarlas). */
    public void marcarSoloFirma(Integer empresaId, List<Long> ids, Integer firmaId) {
        if (ids.isEmpty()) return;
        jdbc.update("UPDATE nube_carpeta SET visibilidad = 'SOLO_FIRMA', privada_firma_id = :firmaId, updated_at = now() "
                + "WHERE empresa_id = :empresaId AND id IN (:ids)",
                new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("ids", ids)
                        .addValue("firmaId", firmaId, Types.INTEGER));
    }

    // ── Papelera ─────────────────────────────────────────────────────────────

    /** Manda a la papelera las carpetas vivas de la lista y los archivos vivos que contienen. */
    public void enviarAPapelera(Integer empresaId, List<Long> carpetas, LocalDateTime marca, Integer usuarioId,
            LocalDateTime purgarEn) {
        if (carpetas.isEmpty()) return;
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("empresaId", empresaId)
                .addValue("ids", carpetas).addValue("marca", marca).addValue("usuarioId", usuarioId)
                .addValue("purgarEn", purgarEn);
        jdbc.update("""
                UPDATE nube_carpeta SET deleted_at = :marca, eliminado_por = :usuarioId, purgar_en = :purgarEn
                 WHERE empresa_id = :empresaId AND id IN (:ids) AND deleted_at IS NULL
                """, p);
        jdbc.update("""
                UPDATE nube_archivo SET deleted_at = :marca, eliminado_por = :usuarioId, purgar_en = :purgarEn
                 WHERE empresa_id = :empresaId AND carpeta_id IN (:ids) AND deleted_at IS NULL
                """, p);
    }

    /** Saca de la papelera lo que se borró junto con la carpeta (misma marca de tiempo). */
    public void restaurarConMarca(Integer empresaId, List<Long> carpetas, LocalDateTime marca) {
        if (carpetas.isEmpty()) return;
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("empresaId", empresaId)
                .addValue("ids", carpetas).addValue("marca", marca);
        jdbc.update("""
                UPDATE nube_carpeta SET deleted_at = NULL, eliminado_por = NULL, purgar_en = NULL, updated_at = now()
                 WHERE empresa_id = :empresaId AND id IN (:ids) AND deleted_at = :marca
                """, p);
        jdbc.update("""
                UPDATE nube_archivo SET deleted_at = NULL, eliminado_por = NULL, purgar_en = NULL, updated_at = now()
                 WHERE empresa_id = :empresaId AND carpeta_id IN (:ids) AND deleted_at = :marca
                """, p);
    }

    /** Carpeta en la papelera (borrada), de la empresa y visible. */
    public Optional<CarpetaFila> carpetaEnPapelera(Long id, Integer empresaId, Integer firmaId) {
        return jdbc.query("SELECT " + COLUMNAS_CARPETA + """
                  FROM nube_carpeta c
                 WHERE c.id = :id AND c.empresa_id = :empresaId AND c.deleted_at IS NOT NULL
                """ + VISIBLE_CARPETA, params(empresaId, firmaId).addValue("id", id),
                NubeQueryRepository::carpetaFila).stream().findFirst();
    }

    /** Archivo en la papelera, de la empresa y visible. */
    public Optional<ArchivoFila> archivoEnPapelera(Long id, Integer empresaId, Integer firmaId) {
        return jdbc.query("SELECT " + COLUMNAS_ARCHIVO + """
                  FROM nube_archivo a
                 WHERE a.id = :id AND a.empresa_id = :empresaId AND a.deleted_at IS NOT NULL
                """ + VISIBLE_ARCHIVO, params(empresaId, firmaId).addValue("id", id),
                NubeQueryRepository::archivoFila).stream().findFirst();
    }

    /**
     * Lo de primer nivel en la papelera: lo que se borró por sí mismo, no lo que
     * cayó junto con su carpeta (eso vuelve al restaurar la carpeta).
     */
    public List<CarpetaFila> papeleraCarpetas(Integer empresaId, Integer firmaId) {
        return jdbc.query("SELECT " + COLUMNAS_CARPETA + """
                  FROM nube_carpeta c
                  LEFT JOIN nube_carpeta p ON p.id = c.padre_id AND p.empresa_id = c.empresa_id
                 WHERE c.empresa_id = :empresaId AND c.deleted_at IS NOT NULL
                   AND (p.id IS NULL OR p.deleted_at IS DISTINCT FROM c.deleted_at)
                """ + VISIBLE_CARPETA + " ORDER BY c.deleted_at DESC LIMIT 500",
                params(empresaId, firmaId), NubeQueryRepository::carpetaFila);
    }

    public List<ArchivoFila> papeleraArchivos(Integer empresaId, Integer firmaId) {
        return jdbc.query("SELECT " + COLUMNAS_ARCHIVO + """
                  FROM nube_archivo a
                  LEFT JOIN nube_carpeta p ON p.id = a.carpeta_id AND p.empresa_id = a.empresa_id
                 WHERE a.empresa_id = :empresaId AND a.deleted_at IS NOT NULL
                   AND (p.id IS NULL OR p.deleted_at IS DISTINCT FROM a.deleted_at)
                """ + VISIBLE_ARCHIVO + " ORDER BY a.deleted_at DESC LIMIT 500",
                params(empresaId, firmaId), NubeQueryRepository::archivoFila);
    }

    /** Claves de objeto de los archivos (vivos o no) dentro de esas carpetas. */
    public List<ObjetoArchivo> objetosEnCarpetas(Integer empresaId, List<Long> carpetas) {
        if (carpetas.isEmpty()) return List.of();
        return jdbc.query("SELECT id, object_key FROM nube_archivo WHERE empresa_id = :empresaId AND carpeta_id IN (:ids)",
                new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("ids", carpetas),
                (rs, i) -> new ObjetoArchivo(rs.getLong("id"), rs.getString("object_key")));
    }

    /** Borra definitivamente las filas (los objetos del bucket los borra el servicio tras confirmar). */
    public void purgarCarpetas(Integer empresaId, List<Long> carpetas) {
        if (carpetas.isEmpty()) return;
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("ids", carpetas);
        jdbc.update("DELETE FROM nube_archivo WHERE empresa_id = :empresaId AND carpeta_id IN (:ids)", p);
        jdbc.update("DELETE FROM nube_carpeta WHERE empresa_id = :empresaId AND id IN (:ids)", p);
    }

    // ── Bitácora ─────────────────────────────────────────────────────────────

    /**
     * Eventos de la empresa, del más reciente. Quien no es de la firma no ve los
     * eventos de lo que está en carpetas SOLO_FIRMA.
     */
    public List<EventoFila> eventos(Integer empresaId, Integer firmaId, Long archivoId, LocalDateTime desde,
            LocalDateTime hasta, int limite) {
        return jdbc.query("""
                SELECT e.id, e.accion, e.usuario_id, u.username, e.firma_empresa_id, e.archivo_id, e.carpeta_id,
                       e.detalle::text AS detalle, e.ip, e.created_at
                  FROM nube_evento e
                  LEFT JOIN usuario u ON u.id = e.usuario_id
                 WHERE e.empresa_id = :empresaId
                   AND (COALESCE(e.detalle->>'soloFirma', 'false') <> 'true' OR e.firma_empresa_id = :firmaId)
                   AND (CAST(:archivoId AS BIGINT) IS NULL OR e.archivo_id = :archivoId)
                   AND (CAST(:desde AS TIMESTAMP) IS NULL OR e.created_at >= :desde)
                   AND (CAST(:hasta AS TIMESTAMP) IS NULL OR e.created_at < :hasta)
                 ORDER BY e.created_at DESC, e.id DESC
                 LIMIT :limite
                """, params(empresaId, firmaId).addValue("archivoId", archivoId).addValue("desde", desde)
                .addValue("hasta", hasta).addValue("limite", limite),
                (rs, i) -> new EventoFila(rs.getLong("id"), rs.getString("accion"), rs.getInt("usuario_id"),
                        rs.getString("username"), rs.getObject("firma_empresa_id", Integer.class),
                        rs.getObject("archivo_id", Long.class), rs.getObject("carpeta_id", Long.class),
                        rs.getString("detalle"), rs.getString("ip"), rs.getObject("created_at", LocalDateTime.class)));
    }

    // ── Mantenimiento (tarea programada; recorre todas las empresas) ─────────

    /** Archivos cuya papelera venció, de cualquier empresa (lote). */
    public List<ObjetoVencido> archivosVencidos(int limite) {
        return jdbc.query("""
                SELECT id, empresa_id, carpeta_id, nombre, object_key FROM nube_archivo
                 WHERE purgar_en IS NOT NULL AND purgar_en <= now()
                 ORDER BY purgar_en LIMIT :limite
                """, new MapSqlParameterSource("limite", limite),
                (rs, i) -> new ObjetoVencido(rs.getLong("id"), rs.getInt("empresa_id"),
                        rs.getObject("carpeta_id", Long.class), rs.getString("nombre"), rs.getString("object_key")));
    }

    /** Borra la fila de un archivo vencido (solo si sigue vencido y es de esa empresa). */
    public int borrarArchivoVencido(Long id, Integer empresaId) {
        return jdbc.update("DELETE FROM nube_archivo WHERE id = :id AND empresa_id = :empresaId "
                + "AND purgar_en IS NOT NULL AND purgar_en <= now()",
                new MapSqlParameterSource().addValue("id", id).addValue("empresaId", empresaId));
    }

    /**
     * Borra las carpetas vencidas que ya quedaron vacías (sin archivos ni hijas).
     * Va de las hojas hacia arriba: el servicio la llama hasta que no borre nada.
     * Devuelve las borradas (empresa, id, nombre) para la bitácora.
     */
    public List<CarpetaPurgada> borrarCarpetasVencidas() {
        return jdbc.query("""
                DELETE FROM nube_carpeta c
                 WHERE c.purgar_en IS NOT NULL AND c.purgar_en <= now()
                   AND NOT EXISTS (SELECT 1 FROM nube_archivo a
                                    WHERE a.carpeta_id = c.id AND a.empresa_id = c.empresa_id)
                   AND NOT EXISTS (SELECT 1 FROM nube_carpeta h
                                    WHERE h.padre_id = c.id AND h.empresa_id = c.empresa_id)
                RETURNING c.id, c.empresa_id, c.nombre
                """, new MapSqlParameterSource(),
                (rs, i) -> new CarpetaPurgada(rs.getLong("id"), rs.getInt("empresa_id"), rs.getString("nombre")));
    }

    /**
     * Fin del vínculo: las carpetas privadas de una firma que ya no atiende a la
     * empresa pasan a COMPARTIDA (los documentos son del cliente). Devuelve las
     * liberadas para la bitácora.
     */
    public List<CarpetaPurgada> liberarPrivadasSinVinculo() {
        return jdbc.query("""
                UPDATE nube_carpeta c
                   SET visibilidad = 'COMPARTIDA', privada_firma_id = NULL, updated_at = now()
                 WHERE c.privada_firma_id IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM empresa_firma_cliente v
                                    WHERE v.cliente_empresa_id = c.empresa_id
                                      AND v.firma_empresa_id = c.privada_firma_id AND v.activo)
                RETURNING c.id, c.empresa_id, c.nombre
                """, new MapSqlParameterSource(),
                (rs, i) -> new CarpetaPurgada(rs.getLong("id"), rs.getInt("empresa_id"), rs.getString("nombre")));
    }

    // ── Solicitudes de más espacio ───────────────────────────────────────────

    private static final String COLUMNAS_SOLICITUD = """
            s.id, s.empresa_id, e.razon_social AS empresa_nombre, e.nit AS empresa_nit, s.solicitado_por,
            u.username AS solicitado_por_nombre, s.cuota_actual_mb, s.cuota_solicitada_mb, s.estado,
            s.observacion, s.created_at, s.atendido_at,
            (SELECT COALESCE(SUM(a.tamano_bytes), 0) FROM nube_archivo a WHERE a.empresa_id = s.empresa_id) AS usado_bytes
              FROM nube_solicitud_espacio s
              JOIN empresa e ON e.id = s.empresa_id
              LEFT JOIN usuario u ON u.id = s.solicitado_por
            """;

    /** La última solicitud de la empresa (cualquier estado). */
    public Optional<SolicitudFila> ultimaSolicitud(Integer empresaId) {
        return jdbc.query("SELECT " + COLUMNAS_SOLICITUD
                + " WHERE s.empresa_id = :empresaId ORDER BY s.created_at DESC, s.id DESC LIMIT 1",
                new MapSqlParameterSource("empresaId", empresaId), NubeQueryRepository::solicitudFila)
                .stream().findFirst();
    }

    /** Pendientes de todas las empresas (panel de plataforma), la más vieja primero. */
    public List<SolicitudFila> solicitudesPendientes() {
        return jdbc.query("SELECT " + COLUMNAS_SOLICITUD + " WHERE s.estado = 'PENDIENTE' ORDER BY s.created_at",
                new MapSqlParameterSource(), NubeQueryRepository::solicitudFila);
    }

    public int contarSolicitudesPendientes() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM nube_solicitud_espacio WHERE estado = 'PENDIENTE'",
                new MapSqlParameterSource(), Integer.class);
        return n == null ? 0 : n;
    }

    /** Bloquea la solicitud pendiente para responderla una sola vez. */
    public Optional<Long> bloquearSolicitudPendiente(Long id) {
        return jdbc.query("SELECT id FROM nube_solicitud_espacio WHERE id = :id AND estado = 'PENDIENTE' FOR UPDATE",
                new MapSqlParameterSource("id", id), (rs, i) -> rs.getLong("id")).stream().findFirst();
    }

    private static SolicitudFila solicitudFila(ResultSet rs, int i) throws SQLException {
        return new SolicitudFila(rs.getLong("id"), rs.getInt("empresa_id"), rs.getString("empresa_nombre"),
                rs.getString("empresa_nit"), rs.getInt("solicitado_por"), rs.getString("solicitado_por_nombre"),
                rs.getInt("cuota_actual_mb"), rs.getInt("cuota_solicitada_mb"), rs.getString("estado"),
                rs.getString("observacion"), rs.getLong("usado_bytes"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("atendido_at", LocalDateTime.class));
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private static Espacio espacio(ResultSet rs, int i) throws SQLException {
        return new Espacio(rs.getInt("cuota_mb"), rs.getInt("max_archivos"), rs.getString("terminos_version"),
                rs.getObject("terminos_aceptados_at", LocalDateTime.class));
    }

    private static MapSqlParameterSource params(Integer empresaId, Integer firmaId) {
        return new MapSqlParameterSource().addValue("empresaId", empresaId).addValue("firmaId", firmaId, Types.INTEGER);
    }

    private static CarpetaFila carpetaFila(ResultSet rs, int i) throws SQLException {
        return new CarpetaFila(rs.getLong("id"), rs.getObject("padre_id", Long.class), rs.getString("nombre"),
                rs.getString("visibilidad"), rs.getObject("privada_firma_id", Integer.class), rs.getShort("nivel"), rs.getInt("creado_por"),
                rs.getObject("creado_por_firma_id", Integer.class),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("deleted_at", LocalDateTime.class));
    }

    private static ArchivoFila archivoFila(ResultSet rs, int i) throws SQLException {
        return new ArchivoFila(rs.getLong("id"), rs.getObject("carpeta_id", Long.class), rs.getString("nombre"),
                rs.getString("extension"), rs.getString("mime"), rs.getInt("tamano_bytes"), rs.getString("sha256"),
                rs.getInt("subido_por"), rs.getObject("subido_por_firma_id", Integer.class),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("deleted_at", LocalDateTime.class));
    }

    public record CarpetaFila(Long id, Long padreId, String nombre, String visibilidad, Integer privadaFirmaId, short nivel,
            Integer creadoPor, Integer creadoPorFirmaId, LocalDateTime createdAt, LocalDateTime deletedAt) {
    }

    /** Sin object_key ni IV: esos solo los lee el servicio por JPA al descargar. */
    public record ArchivoFila(Long id, Long carpetaId, String nombre, String extension, String mime,
            int tamanoBytes, String sha256, Integer subidoPor, Integer subidoPorFirmaId,
            LocalDateTime createdAt, LocalDateTime deletedAt) {
    }

    public record Uso(long bytes, int archivos) {
    }

    public record Espacio(int cuotaMb, int maxArchivos, String terminosVersion, LocalDateTime terminosAceptadosAt) {
    }

    public record Tramo(Long id, String nombre) {
    }

    public record Nodo(Long id, String visibilidad, Integer privadaFirmaId, LocalDateTime deletedAt, int profundidad) {
    }

    public record ObjetoArchivo(Long id, String objectKey) {
    }

    public record ObjetoVencido(Long id, Integer empresaId, Long carpetaId, String nombre, String objectKey) {
    }

    public record CarpetaPurgada(Long id, Integer empresaId, String nombre) {
    }

    public record SolicitudFila(Long id, Integer empresaId, String empresaNombre, String empresaNit,
            Integer solicitadoPor, String solicitadoPorNombre, int cuotaActualMb, int cuotaSolicitadaMb,
            String estado, String observacion, long usadoBytes, LocalDateTime createdAt, LocalDateTime atendidoAt) {
    }

    public record EventoFila(Long id, String accion, Integer usuarioId, String usuario, Integer firmaEmpresaId,
            Long archivoId, Long carpetaId, String detalle, String ip, LocalDateTime createdAt) {
    }
}
