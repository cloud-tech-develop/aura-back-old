package com.cloud_technological.aura_pos.repositories.importacion;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lo que el importador de catálogo necesita para reconocer lo que ya existe
 * (docs/PLAN_EXCEL_IMPORTADORES_MODULOS.md §2). Las llaves van normalizadas:
 * minúsculas, sin tildes ni espacios de más (ver {@code CatalogoImportacionService.llave}).
 */
@Repository
public class CatalogoImportacionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Categorías vivas de la empresa: nombre en minúsculas → id. */
    public Map<String, Long> categorias(Integer empresaId) {
        return mapa("""
            SELECT id, nombre FROM categoria WHERE empresa_id = :empresaId AND deleted_at IS NULL
            """, empresaId);
    }

    public Map<String, Long> marcas(Integer empresaId) {
        return mapa("""
            SELECT id, nombre FROM marca WHERE empresa_id = :empresaId AND deleted_at IS NULL
            """, empresaId);
    }

    /** Unidades (catálogo global) por nombre y por abreviatura. */
    public Map<String, Long> unidades() {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("""
            SELECT id, nombre, abreviatura FROM unidad_medida WHERE deleted_at IS NULL AND COALESCE(activo, TRUE)
            """, new MapSqlParameterSource(), rs -> {
                long id = rs.getLong("id");
                String n = rs.getString("nombre");
                String a = rs.getString("abreviatura");
                if (n != null) out.putIfAbsent(llave(n), id);
                if (a != null) out.putIfAbsent(llave(a), id);
            });
        return out;
    }

    /** Productos vivos por SKU (código interno). */
    public Map<String, Long> productosPorSku(Integer empresaId) {
        return mapa("""
            SELECT id, sku AS nombre FROM producto
            WHERE empresa_id = :empresaId AND deleted_at IS NULL AND sku IS NOT NULL AND TRIM(sku) <> ''
            """, empresaId);
    }

    /** Productos vivos por código de barras. */
    public Map<String, Long> productosPorCodigoBarras(Integer empresaId) {
        return mapa("""
            SELECT id, codigo_barras AS nombre FROM producto
            WHERE empresa_id = :empresaId AND deleted_at IS NULL
              AND codigo_barras IS NOT NULL AND TRIM(codigo_barras) <> ''
            """, empresaId);
    }

    // ── Segunda parte (2b): listas, presentaciones, precios, recetas ────────

    /** Listas de precios de la empresa por nombre. */
    public Map<String, Long> listas(Integer empresaId) {
        return mapa("""
            SELECT id, nombre FROM lista_precios WHERE empresa_id = :empresaId
            """, empresaId);
    }

    /** Presentaciones por "productoId|nombre". */
    public Map<String, Long> presentaciones(Integer empresaId) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("""
            SELECT pp.id, pp.producto_id, pp.nombre
            FROM producto_presentacion pp
            JOIN producto p ON p.id = pp.producto_id AND p.empresa_id = :empresaId AND p.deleted_at IS NULL
            """, new MapSqlParameterSource("empresaId", empresaId), rs -> {
                out.putIfAbsent(rs.getLong("producto_id") + "|" + llave(rs.getString("nombre")), rs.getLong("id"));
            });
        return out;
    }

    /** Presentaciones por código de barras → [presentación, producto]. */
    public Map<String, long[]> presentacionesPorCodigoBarras(Integer empresaId) {
        Map<String, long[]> out = new HashMap<>();
        jdbc.query("""
            SELECT pp.id, pp.producto_id, pp.codigo_barras
            FROM producto_presentacion pp
            JOIN producto p ON p.id = pp.producto_id AND p.empresa_id = :empresaId AND p.deleted_at IS NULL
            WHERE pp.codigo_barras IS NOT NULL AND TRIM(pp.codigo_barras) <> ''
            """, new MapSqlParameterSource("empresaId", empresaId), rs -> {
                out.putIfAbsent(llave(rs.getString("codigo_barras")),
                        new long[] { rs.getLong("id"), rs.getLong("producto_id") });
            });
        return out;
    }

    /**
     * Precios ya asignados → id. Llave {@link #llavePrecio}: por presentación si la tiene
     * (el precio de una presentación puede no traer el producto), si no por producto.
     */
    public Map<String, Long> precios(Integer empresaId) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("""
            SELECT pr.id, pr.lista_precio_id, pr.producto_id, pr.producto_presentacion_id
            FROM producto_precio pr
            JOIN lista_precios l ON l.id = pr.lista_precio_id AND l.empresa_id = :empresaId
            """, new MapSqlParameterSource("empresaId", empresaId), rs -> {
                long pres = rs.getLong("producto_presentacion_id");
                out.putIfAbsent(llavePrecio(rs.getLong("lista_precio_id"), rs.getLong("producto_id"), pres),
                        rs.getLong("id"));
            });
        return out;
    }

    /** Productos de la empresa que ya tienen receta (composición). */
    public java.util.Set<Long> conReceta(Integer empresaId) {
        return new java.util.HashSet<>(jdbc.queryForList("""
            SELECT DISTINCT c.producto_padre_id
            FROM producto_composicion c
            JOIN producto p ON p.id = c.producto_padre_id AND p.empresa_id = :empresaId
            """, new MapSqlParameterSource("empresaId", empresaId), Long.class));
    }

    // ── Inventario (2c): sedes, bodegas, saldos ─────────────────────────────

    /** Sedes activas por nombre y por código. */
    public Map<String, Long> sucursales(Integer empresaId) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query("""
            SELECT id, nombre, codigo FROM sucursal WHERE empresa_id = :empresaId AND COALESCE(activa, TRUE)
            """, new MapSqlParameterSource("empresaId", empresaId), rs -> {
                long id = rs.getLong("id");
                if (rs.getString("nombre") != null) out.putIfAbsent(llave(rs.getString("nombre")), id);
                if (rs.getString("codigo") != null) out.putIfAbsent(llave(rs.getString("codigo")), id);
            });
        return out;
    }

    /** Una bodega activa de la empresa. */
    public record BodegaFila(long id, long sucursalId, String nombre, String codigo, boolean principal) {
    }

    public java.util.List<BodegaFila> bodegas(Integer empresaId) {
        return jdbc.query("""
            SELECT id, sucursal_id, nombre, codigo, COALESCE(es_principal, FALSE) AS principal
            FROM bodega WHERE empresa_id = :empresaId AND COALESCE(activa, TRUE)
            ORDER BY id
            """, new MapSqlParameterSource("empresaId", empresaId), (rs, i) -> new BodegaFila(rs.getLong("id"),
                rs.getLong("sucursal_id"), rs.getString("nombre"), rs.getString("codigo"), rs.getBoolean("principal")));
    }

    /** Saldo por "bodegaId|productoId" → [inventarioId, stock]. */
    public Map<String, java.math.BigDecimal[]> inventarios(Integer empresaId) {
        Map<String, java.math.BigDecimal[]> out = new HashMap<>();
        jdbc.query("""
            SELECT i.id, i.bodega_id, i.producto_id, COALESCE(i.stock_actual, 0) AS stock
            FROM inventario i
            JOIN sucursal s ON s.id = i.sucursal_id AND s.empresa_id = :empresaId
            WHERE i.bodega_id IS NOT NULL
            """, new MapSqlParameterSource("empresaId", empresaId), rs -> {
                out.put(rs.getLong("bodega_id") + "|" + rs.getLong("producto_id"),
                        new java.math.BigDecimal[] { java.math.BigDecimal.valueOf(rs.getLong("id")), rs.getBigDecimal("stock") });
            });
        return out;
    }

    private Map<String, Long> mapa(String sql, Integer empresaId) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("empresaId", empresaId), rs -> {
            String n = rs.getString("nombre");
            if (n != null) out.putIfAbsent(llave(n), rs.getLong("id"));
        });
        return out;
    }

    public static String llavePrecio(long listaId, long productoId, long presentacionId) {
        return presentacionId != 0 ? listaId + "|P|" + presentacionId : listaId + "|" + productoId + "|0";
    }

    /** Minúsculas, sin tildes y con un solo espacio: "Bebidas  Frías" = "bebidas frias". */
    public static String llave(String s) {
        if (s == null) return "";
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
