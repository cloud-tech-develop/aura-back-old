package com.cloud_technological.aura_pos.sync.nube;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.sync.SyncTablas;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;

/** Lado nube: nodos, comandos recibidos, equivalencias de ids y extracción del catálogo. */
@Repository
public class SyncNubeQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private static final String COLUMNAS_NODO = """
            id, empresa_id, sucursal_id, codigo, nombre, prefijo_ventas, activo, ultimo_contacto, created_at
            """;

    // ── Nodos ──────────────────────────────────────────────────────────

    public Optional<SyncDtos.NodoInfo> nodoPorToken(String tokenHash) {
        List<SyncDtos.NodoInfo> r = jdbc.query("SELECT " + COLUMNAS_NODO + " FROM sync_nodo WHERE token_hash = :h AND activo",
                new MapSqlParameterSource("h", tokenHash), new BeanPropertyRowMapper<>(SyncDtos.NodoInfo.class));
        return r.stream().findFirst();
    }

    public void tocar(Long nodoId) {
        jdbc.update("UPDATE sync_nodo SET ultimo_contacto = now() WHERE id = :id", new MapSqlParameterSource("id", nodoId));
    }

    public Long crearNodo(Integer empresaId, SyncDtos.NodoNuevo n, String tokenHash) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO sync_nodo (empresa_id, sucursal_id, codigo, nombre, prefijo_ventas, token_hash)
                VALUES (:empresaId, :sucursalId, :codigo, :nombre, :prefijo, :hash)
                """, new MapSqlParameterSource("empresaId", empresaId)
                .addValue("sucursalId", n.getSucursalId())
                .addValue("codigo", n.getCodigo().trim())
                .addValue("nombre", n.getNombre().trim())
                .addValue("prefijo", n.getPrefijoVentas() == null || n.getPrefijoVentas().isBlank()
                        ? null : n.getPrefijoVentas().trim().toUpperCase())
                .addValue("hash", tokenHash), kh, new String[] { "id" });
        return kh.getKey().longValue();
    }

    public Optional<SyncDtos.NodoInfo> nodo(Long id, Integer empresaId) {
        return jdbc.query("SELECT " + COLUMNAS_NODO + " FROM sync_nodo WHERE id = :id AND empresa_id = :e",
                new MapSqlParameterSource("id", id).addValue("e", empresaId),
                new BeanPropertyRowMapper<>(SyncDtos.NodoInfo.class)).stream().findFirst();
    }

    public List<SyncDtos.NodoInfo> nodos(Integer empresaId) {
        return jdbc.query("SELECT " + COLUMNAS_NODO + " FROM sync_nodo WHERE empresa_id = :e ORDER BY codigo",
                new MapSqlParameterSource("e", empresaId), new BeanPropertyRowMapper<>(SyncDtos.NodoInfo.class));
    }

    public boolean cambiarActivo(Long id, Integer empresaId, boolean activo) {
        return jdbc.update("UPDATE sync_nodo SET activo = :a WHERE id = :id AND empresa_id = :e",
                new MapSqlParameterSource("id", id).addValue("e", empresaId).addValue("a", activo)) > 0;
    }

    /**
     * Prefijos de venta reservados por los nodos de la empresa (activos o no:
     * sus ventas ya están en la base con esa serie).
     *
     * @return prefijos; vacío si la empresa no tiene nodos o la base aún no
     *         tiene la migración V226
     */
    public List<String> prefijosNodos(Integer empresaId) {
        // Se pregunta antes de consultar: en Postgres una consulta fallida aborta
        // la transacción, y esta corre dentro de la de la venta.
        Boolean existe = jdbc.queryForObject("SELECT to_regclass('public.sync_nodo') IS NOT NULL",
                new MapSqlParameterSource(), Boolean.class);
        if (!Boolean.TRUE.equals(existe)) return List.of();
        return jdbc.queryForList(
                "SELECT DISTINCT prefijo_ventas FROM sync_nodo WHERE empresa_id = :e AND prefijo_ventas IS NOT NULL",
                new MapSqlParameterSource("e", empresaId), String.class);
    }

    public boolean prefijoEnUso(Integer empresaId, String prefijo) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM sync_nodo WHERE empresa_id = :e AND upper(prefijo_ventas) = upper(:p))
                    OR EXISTS (SELECT 1 FROM sucursal WHERE empresa_id = :e AND upper(prefijo_facturacion) = upper(:p))
                """, new MapSqlParameterSource("e", empresaId).addValue("p", prefijo), Boolean.class));
    }

    // ── Comandos recibidos (idempotencia) ──────────────────────────────

    /**
     * Resultado guardado de un comando que ya se procesó.
     *
     * @return el resultado en JSON, si el uuid ya llegó antes
     */
    public Optional<String> recibido(String uuid) {
        return jdbc.queryForList("SELECT respuesta FROM sync_recibido WHERE uuid = :u AND estado = 'OK'",
                new MapSqlParameterSource("u", uuid), String.class).stream().findFirst();
    }

    public void guardarRecibido(Long nodoId, SyncDtos.Comando c, String respuesta) {
        jdbc.update("""
                INSERT INTO sync_recibido (nodo_id, uuid, tipo, id_local, estado, respuesta)
                VALUES (:nodo, :uuid, :tipo, :idLocal, 'OK', :respuesta)
                """, new MapSqlParameterSource("nodo", nodoId)
                .addValue("uuid", c.getUuid())
                .addValue("tipo", c.getTipo())
                .addValue("idLocal", c.getIdLocal())
                .addValue("respuesta", respuesta));
    }

    // ── Equivalencias de ids ───────────────────────────────────────────

    public Optional<Long> idNube(Long nodoId, String tabla, Long idLocal) {
        List<Long> r = jdbc.queryForList(
                "SELECT id_nube FROM sync_id_map WHERE nodo_id = :n AND tabla = :t AND id_local = :l",
                new MapSqlParameterSource("n", nodoId).addValue("t", tabla).addValue("l", idLocal), Long.class);
        return r.stream().findFirst();
    }

    public void guardarMapa(Long nodoId, String tabla, Long idLocal, Long idNube) {
        jdbc.update("""
                INSERT INTO sync_id_map (nodo_id, tabla, id_local, id_nube) VALUES (:n, :t, :l, :nube)
                ON CONFLICT (nodo_id, tabla, id_local) DO UPDATE SET id_nube = EXCLUDED.id_nube
                """, new MapSqlParameterSource("n", nodoId).addValue("t", tabla).addValue("l", idLocal)
                .addValue("nube", idNube));
    }

    public List<SyncDtos.Mapa> mapas(Long nodoId, String tabla) {
        return jdbc.query("SELECT tabla, id_local, id_nube FROM sync_id_map WHERE nodo_id = :n AND tabla = :t",
                new MapSqlParameterSource("n", nodoId).addValue("t", tabla),
                (rs, i) -> new SyncDtos.Mapa(rs.getString("tabla"), rs.getLong("id_local"), rs.getLong("id_nube")));
    }

    // ── Catálogo ───────────────────────────────────────────────────────

    /**
     * Filas de una tabla de la empresa como arreglo JSON.
     *
     * @param t tabla del registro {@link SyncTablas} (nombre y filtro fijos)
     * @return el JSON, o null si la tabla no existe en esta base
     */
    public String extraer(SyncTablas.Tabla t, Integer empresaId) {
        Boolean existe = jdbc.queryForObject("SELECT to_regclass(:t) IS NOT NULL",
                new MapSqlParameterSource("t", "public." + t.nombre()), Boolean.class);
        if (!Boolean.TRUE.equals(existe)) return null;
        String sql = "SELECT COALESCE(json_agg(x), '[]'::json)::text FROM \"" + t.nombre() + "\" x"
                + (t.filtro() == null ? "" : " WHERE " + t.filtro());
        return jdbc.queryForObject(sql, new MapSqlParameterSource("empresaId", empresaId), String.class);
    }

    /**
     * Último consecutivo de la serie del nodo en cada sucursal de la empresa.
     *
     * @return sucursalId (texto) → consecutivo
     */
    public Map<String, Long> pisosConsecutivo(Integer empresaId, String prefijo) {
        Map<String, Long> pisos = new LinkedHashMap<>();
        if (prefijo == null || prefijo.isBlank()) return pisos;
        jdbc.query("""
                SELECT sucursal_id, MAX(consecutivo) AS piso
                FROM venta
                WHERE empresa_id = :e AND prefijo = :p
                GROUP BY sucursal_id
                """, new MapSqlParameterSource("e", empresaId).addValue("p", prefijo),
                rs -> {
                    pisos.put(String.valueOf(rs.getInt("sucursal_id")), rs.getLong("piso"));
                });
        return pisos;
    }

    /**
     * Tercero de la empresa con ese documento.
     *
     * @return su id, si existe
     */
    public Optional<Long> terceroPorDocumento(Integer empresaId, String numeroDocumento) {
        if (numeroDocumento == null || numeroDocumento.isBlank()) return Optional.empty();
        return jdbc.queryForList("""
                SELECT id FROM tercero WHERE empresa_id = :e AND numero_documento = :d ORDER BY id LIMIT 1
                """, new MapSqlParameterSource("e", empresaId).addValue("d", numeroDocumento.trim()), Long.class)
                .stream().findFirst();
    }

    /**
     * Factura de Facturación ligada a una venta, si la venta salió de allí.
     *
     * @return id de la factura_venta
     */
    public Optional<Long> facturaDeVenta(Long ventaId, Integer empresaId) {
        return jdbc.queryForList("SELECT id FROM factura_venta WHERE venta_id = :v AND empresa_id = :e LIMIT 1",
                new MapSqlParameterSource("v", ventaId).addValue("e", empresaId), Long.class).stream().findFirst();
    }
}
