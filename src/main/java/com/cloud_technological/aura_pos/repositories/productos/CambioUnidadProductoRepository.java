package com.cloud_technological.aura_pos.repositories.productos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.productos.CambioUnidadPreviewDto;

/**
 * Lecturas y escrituras de "Pasar a unidad". Todo por JDBC y en bloque: toca
 * inventario, lotes, kardex, líneas de venta, precios y recetas de un producto.
 */
@Repository
public class CambioUnidadProductoRepository {

    /** Documentos sin presentación cuyas cantidades quedan en la unidad vieja. */
    private static final Map<String, String> COLUMNA_DOCUMENTO = Map.of(
            "COMPRA", "ultimo_compra_id",
            "MERMA", "ultimo_merma_id",
            "OBSEQUIO", "ultimo_obsequio_id",
            "CONSUMO_INTERNO", "ultimo_consumo_interno_id",
            "TRASLADO", "ultimo_traslado_id");

    public record CambioPosterior(String productoNombre, LocalDateTime fecha) {
    }

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    // ── Lecturas para la vista previa ─────────────────────────────────────

    public boolean esPadreDeReceta(Long productoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM producto_composicion WHERE producto_padre_id = :p)",
                producto(productoId), Boolean.class));
    }

    public int reconteosAbiertos(Long productoId) {
        return contar("""
                SELECT COUNT(DISTINCT r.id)
                  FROM reconteo_detalles d
                  JOIN reconteos r ON r.id = d.reconteo_id
                 WHERE d.producto_id = :p
                   AND r.estado IN ('BORRADOR', 'EN_CONTEO')
                """, producto(productoId));
    }

    /** Cotizaciones, pedidos y órdenes de compra abiertas: sus cantidades siguen en la unidad vieja. */
    public int documentosPendientes(Long productoId) {
        return contar("""
                SELECT
                  (SELECT COUNT(DISTINCT c.id)
                     FROM cotizacion_detalle d JOIN cotizacion c ON c.id = d.cotizacion_id
                    WHERE d.producto_id = :p AND c.estado = 'PENDIENTE')
                + (SELECT COUNT(DISTINCT pv.id)
                     FROM pedido_vendedor_detalle d JOIN pedido_vendedor pv ON pv.id = d.pedido_vendedor_id
                    WHERE d.producto_id = :p AND pv.estado IN ('CREADA', 'PENDIENTE_DESPACHO'))
                + (SELECT COUNT(DISTINCT oc.id)
                     FROM orden_compra_detalle d JOIN orden_compra oc ON oc.id = d.orden_compra_id
                    WHERE d.producto_id = :p AND oc.estado IN ('BORRADOR', 'CONFIRMADA', 'ENVIADA', 'RECIBIDA_PARCIAL'))
                """, producto(productoId));
    }

    /** Precios por volumen y por cliente solo existen por presentación: los de la pequeña se pierden. */
    public int preciosDinamicosDePresentacion(Long presentacionId) {
        return contar("""
                SELECT (SELECT COUNT(*) FROM precio_volumen WHERE producto_presentacion_id = :u AND deleted_at IS NULL)
                     + (SELECT COUNT(*) FROM precio_cliente WHERE producto_presentacion_id = :u AND deleted_at IS NULL)
                """, new MapSqlParameterSource("u", presentacionId));
    }

    /** movimientos, lotes, lineasVenta, recetas, preciosLista. */
    public Map<String, Integer> conteos(Long productoId, Long presentacionId) {
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT
                  (SELECT COUNT(*) FROM movimiento_inventario WHERE producto_id = :p) AS movimientos,
                  (SELECT COUNT(*) FROM lote WHERE producto_id = :p) AS lotes,
                  (SELECT COUNT(*) FROM venta_detalle WHERE producto_id = :p) AS lineas_venta,
                  (SELECT COUNT(*) FROM producto_composicion WHERE producto_hijo_id = :p) AS recetas,
                  (SELECT COUNT(*) FROM producto_precio
                    WHERE (producto_id = :p AND producto_presentacion_id IS NULL)
                       OR producto_presentacion_id = :u) AS precios_lista
                """, producto(productoId).addValue("u", presentacionId));
        Map<String, Integer> conteos = new HashMap<>();
        fila.forEach((k, v) -> conteos.put(k, v == null ? 0 : ((Number) v).intValue()));
        return conteos;
    }

    public List<CambioUnidadPreviewDto.StockSucursal> stockPorSucursal(Long productoId) {
        return jdbc.query("""
                SELECT s.nombre, SUM(i.stock_actual) AS stock_actual
                  FROM inventario i
                  JOIN sucursal s ON s.id = i.sucursal_id
                 WHERE i.producto_id = :p
                 GROUP BY s.id, s.nombre
                 ORDER BY s.nombre
                """, producto(productoId), (rs, i) -> {
            CambioUnidadPreviewDto.StockSucursal s = new CambioUnidadPreviewDto.StockSucursal();
            s.setSucursalNombre(rs.getString("nombre"));
            s.setAntes(rs.getBigDecimal("stock_actual"));
            return s;
        });
    }

    /** La unidad cuyo nombre aparece en el de la presentación ("ARROZ DIANA UNIDAD" → UNIDAD); si no, UNIDAD. */
    public Long unidadSugerida(String nombrePresentacion) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM unidad_medida
                 WHERE activo = true AND deleted_at IS NULL
                   AND (UPPER(:nombre) LIKE '%' || UPPER(TRIM(nombre)) || '%' OR UPPER(TRIM(nombre)) = 'UNIDAD')
                 ORDER BY CASE WHEN UPPER(:nombre) LIKE '%' || UPPER(TRIM(nombre)) || '%' THEN 0 ELSE 1 END,
                          LENGTH(TRIM(nombre)) DESC, id
                 LIMIT 1
                """, new MapSqlParameterSource("nombre", nombrePresentacion == null ? "" : nombrePresentacion),
                Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public boolean existeUnidadMedida(Long unidadId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM unidad_medida WHERE id = :id AND activo = true AND deleted_at IS NULL)",
                new MapSqlParameterSource("id", unidadId), Boolean.class));
    }

    public Map<String, Long> ultimosDocumentos() {
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT COALESCE((SELECT MAX(id) FROM compra), 0)   AS compra,
                       COALESCE((SELECT MAX(id) FROM merma), 0)    AS merma,
                       COALESCE((SELECT MAX(id) FROM obsequio), 0) AS obsequio,
                       COALESCE((SELECT MAX(id) FROM consumo_interno), 0) AS consumo_interno,
                       COALESCE((SELECT MAX(id) FROM traslado), 0) AS traslado
                """, new MapSqlParameterSource());
        Map<String, Long> ultimos = new HashMap<>();
        fila.forEach((k, v) -> ultimos.put(k, ((Number) v).longValue()));
        return ultimos;
    }

    // ── Escrituras del cambio ─────────────────────────────────────────────

    /** Las demás presentaciones estaban expresadas en la base grande: ahora contienen N veces más. */
    public void multiplicarFactorOtrasPresentaciones(Long productoId, Long pequenaId, BigDecimal n) {
        jdbc.update("""
                UPDATE producto_presentacion
                   SET factor_conversion = ROUND(factor_conversion * :n, 8)
                 WHERE producto_id = :p AND id <> :u AND factor_conversion > 0
                """, producto(productoId).addValue("u", pequenaId).addValue("n", n));
    }

    public void desmarcarDefaultCompra(Long productoId) {
        jdbc.update("UPDATE producto_presentacion SET es_default_compra = false WHERE producto_id = :p",
                producto(productoId));
    }

    /** La pequeña pasa a ser la base: ya no es una presentación (factor 1 para las líneas viejas que la citen). */
    public void retirarPresentacionPequena(Long pequenaId) {
        jdbc.update("""
                UPDATE producto_presentacion
                   SET factor_conversion = 1, activo = false,
                       es_default_compra = false, es_default_venta = false, codigo_barras = NULL
                 WHERE id = :u
                """, new MapSqlParameterSource("u", pequenaId));
    }

    public void actualizarProducto(Long productoId, Long unidadNuevaId, String codigoBarras,
            BigDecimal precioNuevo, BigDecimal costoNuevo, BigDecimal n) {
        jdbc.update("""
                UPDATE producto
                   SET unidad_medida_base_id = :unidad,
                       codigo_barras = CAST(:codigo AS VARCHAR),
                       precio   = :precio,
                       precio_2 = ROUND(precio_2 / :n, 2),
                       precio_3 = ROUND(precio_3 / :n, 2),
                       costo    = :costo,
                       updated_at = now()
                 WHERE id = :p
                """, producto(productoId)
                .addValue("unidad", unidadNuevaId)
                .addValue("codigo", codigoBarras)
                .addValue("precio", precioNuevo)
                .addValue("costo", costoNuevo)
                .addValue("n", n));
    }

    public void convertirInventario(Long productoId, BigDecimal n) {
        jdbc.update("""
                UPDATE inventario
                   SET stock_actual = stock_actual * :n,
                       stock_minimo = stock_minimo * :n,
                       updated_at = now()
                 WHERE producto_id = :p
                """, producto(productoId).addValue("n", n));
    }

    public void convertirLotes(Long productoId, BigDecimal n) {
        jdbc.update("""
                UPDATE lote
                   SET stock_actual = stock_actual * :n,
                       costo_unitario = ROUND(costo_unitario / :n, 2)
                 WHERE producto_id = :p
                """, producto(productoId).addValue("n", n));
        // El rastro de lotes (F2/F3) también queda en la unidad nueva: anular
        // una venta vieja devuelve al lote lo que salió, ya multiplicado.
        jdbc.update("""
                UPDATE documento_lote
                   SET cantidad_base = cantidad_base * :n
                 WHERE lote_id IN (SELECT id FROM lote WHERE producto_id = :p)
                """, producto(productoId).addValue("n", n));
        jdbc.update("""
                UPDATE compra_detalle_lote
                   SET cantidad_base = cantidad_base * :n
                 WHERE lote_id IN (SELECT id FROM lote WHERE producto_id = :p)
                """, producto(productoId).addValue("n", n));
    }

    /**
     * El reporte de kardex suma entradas y salidas con saldo_nuevo - saldo_anterior:
     * un movimiento de "cambio de unidad" contaría como entrada. Se reescribe el
     * historial del producto en la unidad nueva, con el mismo valor.
     */
    public void convertirKardex(Long productoId, BigDecimal n) {
        jdbc.update("""
                UPDATE movimiento_inventario
                   SET cantidad = cantidad * :n,
                       saldo_anterior = saldo_anterior * :n,
                       saldo_nuevo = saldo_nuevo * :n,
                       costo_historico = ROUND(costo_historico / :n, 2)
                 WHERE producto_id = :p
                """, producto(productoId).addValue("n", n));
    }

    /**
     * Venta y devolución guardan la presentación: la cantidad escrita no cambia,
     * cambia a qué apunta. Sin presentación (era la base grande) → la nueva
     * presentación grande; la pequeña (ahora la base) → sin presentación.
     */
    public void reapuntarLineasVenta(Long productoId, Long pequenaId, Long grandeId) {
        MapSqlParameterSource params = producto(productoId).addValue("u", pequenaId).addValue("g", grandeId);
        for (String tabla : List.of("venta_detalle", "devolucion_detalle")) {
            jdbc.update("UPDATE " + tabla + """
                     SET producto_presentacion_id = CASE WHEN producto_presentacion_id = :u THEN NULL ELSE :g END
                   WHERE producto_id = :p
                     AND (producto_presentacion_id IS NULL OR producto_presentacion_id = :u)
                    """, params);
        }
    }

    public void reapuntarPreciosLista(Long productoId, Long pequenaId, Long grandeId) {
        jdbc.update("""
                UPDATE producto_precio
                   SET producto_id = CASE WHEN producto_presentacion_id = :u THEN :p ELSE NULL END,
                       producto_presentacion_id = CASE WHEN producto_presentacion_id = :u THEN NULL ELSE :g END
                 WHERE (producto_id = :p AND producto_presentacion_id IS NULL)
                    OR producto_presentacion_id = :u
                """, producto(productoId).addValue("u", pequenaId).addValue("g", grandeId));
    }

    /** Recetas donde el producto es componente: el consumo en base se multiplica por N. */
    public void convertirRecetas(Long productoId, Long pequenaId, Long grandeId,
            Long unidadAnteriorId, Long unidadNuevaId, BigDecimal n) {
        jdbc.update("""
                UPDATE producto_composicion
                   SET cantidad = ROUND(cantidad * :n, 6),
                       factor_unidad = ROUND(COALESCE(factor_unidad, 1) * :n, 6),
                       unidad_medida_id = CASE WHEN producto_presentacion_id = :u
                                               THEN :unidadNueva ELSE unidad_medida_id END,
                       producto_presentacion_id = CASE
                           WHEN producto_presentacion_id = :u THEN NULL
                           WHEN producto_presentacion_id IS NULL
                                AND (unidad_medida_id IS NULL OR unidad_medida_id = CAST(:unidadAnterior AS BIGINT))
                                THEN :g
                           ELSE producto_presentacion_id END
                 WHERE producto_hijo_id = :p
                """, producto(productoId)
                .addValue("u", pequenaId)
                .addValue("g", grandeId)
                .addValue("unidadAnterior", unidadAnteriorId)
                .addValue("unidadNueva", unidadNuevaId)
                .addValue("n", n));
    }

    public void registrarCambio(Integer empresaId, Long productoId, BigDecimal n, Long unidadAnteriorId,
            Long unidadNuevaId, Long grandeId, Long pequenaId, Map<String, Long> ultimos, Long usuarioId) {
        jdbc.update("""
                INSERT INTO producto_cambio_unidad
                    (empresa_id, producto_id, factor, unidad_anterior_id, unidad_nueva_id,
                     presentacion_grande_id, presentacion_pequena_id,
                     ultimo_compra_id, ultimo_merma_id, ultimo_obsequio_id, ultimo_traslado_id,
                     ultimo_consumo_interno_id, usuario_id)
                VALUES (:empresa, :p, :n, CAST(:unidadAnterior AS BIGINT), :unidadNueva,
                        :g, :u, :compra, :merma, :obsequio, :traslado,
                        :consumoInterno, CAST(:usuario AS BIGINT))
                """, producto(productoId)
                .addValue("empresa", empresaId)
                .addValue("n", n)
                .addValue("unidadAnterior", unidadAnteriorId)
                .addValue("unidadNueva", unidadNuevaId)
                .addValue("g", grandeId)
                .addValue("u", pequenaId)
                .addValue("compra", ultimos.get("compra"))
                .addValue("merma", ultimos.get("merma"))
                .addValue("obsequio", ultimos.get("obsequio"))
                .addValue("consumoInterno", ultimos.get("consumo_interno"))
                .addValue("traslado", ultimos.get("traslado"))
                .addValue("usuario", usuarioId));
    }

    // ── Control de documentos viejos ──────────────────────────────────────

    /** El primer cambio de unidad posterior al documento entre los productos indicados. */
    public Optional<CambioPosterior> cambioPosteriorA(String documento, Long documentoId,
            Collection<Long> productoIds) {
        String columna = COLUMNA_DOCUMENTO.get(documento);
        if (columna == null)
            throw new IllegalArgumentException("Documento sin control de cambio de unidad: " + documento);

        String sql = """
                SELECT p.nombre, c.created_at
                  FROM producto_cambio_unidad c
                  JOIN producto p ON p.id = c.producto_id
                 WHERE c.producto_id IN (:ids)
                   AND :doc <= c.""" + columna + """

                 ORDER BY c.created_at
                 LIMIT 1
                """;
        List<CambioPosterior> filas = jdbc.query(sql,
                new MapSqlParameterSource("ids", productoIds).addValue("doc", documentoId),
                (rs, i) -> new CambioPosterior(rs.getString("nombre"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
        return filas.stream().findFirst();
    }

    private static MapSqlParameterSource producto(Long productoId) {
        return new MapSqlParameterSource("p", productoId);
    }

    private int contar(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0 : n.intValue();
    }
}
