package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas del traslado de cuentas y de la fusión de terceros (Fase 4, V187).
 */
@Repository
public class HerramientasContadorQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    // ── Traslado de cuentas ─────────────────────────────────────────────

    private static final String FILTRO_TRASLADO = """
              FROM asiento_detalle d
              JOIN asiento_contable a ON a.id = d.asiento_id
             WHERE a.empresa_id = :e AND d.cuenta_id = :origen
               AND a.fecha BETWEEN :desde AND :hasta
            """;

    private MapSqlParameterSource paramsTraslado(Integer empresaId, Long origenId, LocalDate desde, LocalDate hasta,
            Long terceroId) {
        return new MapSqlParameterSource().addValue("e", empresaId).addValue("origen", origenId)
                .addValue("desde", desde).addValue("hasta", hasta).addValue("t", terceroId);
    }

    /** Líneas que se moverían y su valor neto (débito − crédito). */
    public Map<String, Object> resumenTraslado(Integer empresaId, Long origenId, LocalDate desde, LocalDate hasta,
            Long terceroId) {
        return jdbc.queryForMap("SELECT COUNT(*) AS lineas, COALESCE(SUM(d.debito), 0) AS debitos, "
                + "COALESCE(SUM(d.credito), 0) AS creditos " + FILTRO_TRASLADO
                + (terceroId != null ? " AND d.tercero_id = :t" : ""),
                paramsTraslado(empresaId, origenId, desde, hasta, terceroId));
    }

    /** Meses cerrados que tocaría el traslado (tienen líneas de la cuenta en el rango). */
    public List<String> periodosCerradosEnTraslado(Integer empresaId, Long origenId, LocalDate desde, LocalDate hasta,
            Long terceroId) {
        return jdbc.queryForList("""
            SELECT DISTINCT p.anio || '-' || LPAD(p.mes::text, 2, '0') AS periodo
              FROM asiento_detalle d
              JOIN asiento_contable a ON a.id = d.asiento_id
              JOIN periodo_contable p ON p.id = a.periodo_contable_id
             WHERE a.empresa_id = :e AND d.cuenta_id = :origen
               AND a.fecha BETWEEN :desde AND :hasta
               AND p.estado <> 'ABIERTO'
            """ + (terceroId != null ? " AND d.tercero_id = :t" : "") + " ORDER BY 1",
                paramsTraslado(empresaId, origenId, desde, hasta, terceroId), String.class);
    }

    /**
     * Los movimientos que moverá el traslado, para que el contador los vea y
     * elija: fecha, comprobante, origen del asiento, tercero y valores.
     */
    public List<Map<String, Object>> movimientosTraslado(Integer empresaId, Long origenId, LocalDate desde,
            LocalDate hasta, Long terceroId) {
        return jdbc.queryForList("""
            SELECT d.id, a.id AS asiento_id, a.fecha, a.numero_comprobante, a.tipo_origen, a.origen_id,
                   COALESCE(NULLIF(d.descripcion, ''), a.descripcion) AS descripcion,
                   d.debito, d.credito, d.tercero_id,
                   COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''),
                            NULLIF(TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)), '')) AS tercero_nombre,
                   p.estado AS estado_periodo
              FROM asiento_detalle d
              JOIN asiento_contable a ON a.id = d.asiento_id
              LEFT JOIN periodo_contable p ON p.id = a.periodo_contable_id
              LEFT JOIN tercero t ON t.id = d.tercero_id
             WHERE a.empresa_id = :e AND d.cuenta_id = :origen
               AND a.fecha BETWEEN :desde AND :hasta
            """ + (terceroId != null ? " AND d.tercero_id = :t" : "") + " ORDER BY a.fecha, a.id, d.id LIMIT 2000",
                paramsTraslado(empresaId, origenId, desde, hasta, terceroId));
    }

    /**
     * Mueve los movimientos del rango; si vienen {@code detalleIds}, solo esos
     * (siempre dentro del mismo filtro: no se puede mover una línea de otra
     * cuenta o de otra empresa pasando su id).
     */
    public int trasladar(Integer empresaId, Long origenId, Long destinoId, LocalDate desde, LocalDate hasta,
            Long terceroId, List<Long> detalleIds) {
        MapSqlParameterSource p = paramsTraslado(empresaId, origenId, desde, hasta, terceroId)
                .addValue("destino", destinoId);
        String filtroIds = "";
        if (detalleIds != null && !detalleIds.isEmpty()) {
            filtroIds = " AND d.id IN (:ids)";
            p.addValue("ids", detalleIds);
        }
        return jdbc.update("""
            UPDATE asiento_detalle d SET cuenta_id = :destino
              FROM asiento_contable a
             WHERE a.id = d.asiento_id AND a.empresa_id = :e AND d.cuenta_id = :origen
               AND a.fecha BETWEEN :desde AND :hasta
            """ + (terceroId != null ? " AND d.tercero_id = :t" : "") + filtroIds, p);
    }

    /** Meses cerrados que tocan solo los movimientos elegidos. */
    public List<String> periodosCerradosDeLineas(Integer empresaId, List<Long> detalleIds) {
        return jdbc.queryForList("""
            SELECT DISTINCT p.anio || '-' || LPAD(p.mes::text, 2, '0')
              FROM asiento_detalle d
              JOIN asiento_contable a ON a.id = d.asiento_id
              JOIN periodo_contable p ON p.id = a.periodo_contable_id
             WHERE a.empresa_id = :e AND d.id IN (:ids) AND p.estado <> 'ABIERTO'
             ORDER BY 1
            """, new MapSqlParameterSource().addValue("e", empresaId).addValue("ids", detalleIds), String.class);
    }

    public void logTraslado(Integer empresaId, Long origenId, Long destinoId, LocalDate desde, LocalDate hasta,
            Long terceroId, int lineas, String motivo, Long usuarioId) {
        jdbc.update("""
            INSERT INTO traslado_cuenta_log (empresa_id, cuenta_origen_id, cuenta_destino_id, desde, hasta,
                                             tercero_id, lineas_movidas, motivo, usuario_id)
            VALUES (:e, :o, :d, :desde, :hasta, :t, :n, :m, :u)
            """, new MapSqlParameterSource().addValue("e", empresaId).addValue("o", origenId).addValue("d", destinoId)
                .addValue("desde", desde).addValue("hasta", hasta).addValue("t", terceroId).addValue("n", lineas)
                .addValue("m", motivo).addValue("u", usuarioId));
    }

    public List<Map<String, Object>> historialTraslados(Integer empresaId) {
        return jdbc.queryForList("""
            SELECT l.id, l.desde, l.hasta, l.lineas_movidas, l.motivo, l.created_at,
                   o.codigo || ' - ' || o.nombre AS origen, d.codigo || ' - ' || d.nombre AS destino
              FROM traslado_cuenta_log l
              LEFT JOIN plan_cuenta o ON o.id = l.cuenta_origen_id
              LEFT JOIN plan_cuenta d ON d.id = l.cuenta_destino_id
             WHERE l.empresa_id = :e
             ORDER BY l.created_at DESC
             LIMIT 100
            """, new MapSqlParameterSource("e", empresaId));
    }

    // ── Configuración que apunta a una cuenta ───────────────────────────

    /**
     * Lo que decide a qué cuenta van los documentos NUEVOS: conceptos, formas
     * de pago, categorías, productos, impuestos, conceptos de caja, cuentas
     * bancarias, retenciones, activos, diferidos y plantillas. Lo ya
     * contabilizado (asientos, ventas, pagos) no está aquí: eso es historia.
     */
    private static final List<Referencia> CONFIG_CUENTA = List.of(
            new Referencia("cuenta_config", "cuenta_id"),
            new Referencia("forma_pago_contable", "cuenta_contable_id"),
            new Referencia("categoria_contable_producto", "cuenta_ingreso_id"),
            new Referencia("categoria_contable_producto", "cuenta_inventario_id"),
            new Referencia("categoria_contable_producto", "cuenta_costo_id"),
            new Referencia("categoria_contable_producto", "cuenta_devolucion_id"),
            new Referencia("categoria_contable_producto", "cuenta_depreciacion_id"),
            new Referencia("categoria_contable_producto", "cuenta_gasto_depreciacion_id"),
            new Referencia("producto", "cuenta_ingreso_id"),
            new Referencia("producto", "cuenta_costo_id"),
            new Referencia("producto", "cuenta_inventario_id"),
            new Referencia("impuesto", "cuenta_generado_id"),
            new Referencia("impuesto", "cuenta_descontable_id"),
            new Referencia("concepto_caja", "cuenta_contable_id"),
            new Referencia("concepto_consumo_interno", "cuenta_id"),
            new Referencia("cuenta_bancaria", "cuenta_contable_id"),
            new Referencia("tarifa_retencion", "cuenta_contable_id"),
            new Referencia("activo_fijo", "cuenta_activo_id"),
            new Referencia("activo_fijo", "cuenta_depreciacion_id"),
            new Referencia("activo_fijo", "cuenta_gasto_dep_id"),
            new Referencia("diferido", "cuenta_gasto_id"),
            new Referencia("diferido", "cuenta_diferido_id"),
            new Referencia("causacion_programada_linea", "cuenta_id"),
            new Referencia("nota_diario_plantilla_linea", "cuenta_id"));

    /** Las de CONFIG_CUENTA que existen en esta base (alguna tabla puede no estar migrada). */
    public List<Referencia> configuracionDeCuentas() {
        Set<String> existentes = new LinkedHashSet<>(jdbc.queryForList("""
            SELECT table_name || '.' || column_name
              FROM information_schema.columns
             WHERE table_schema = current_schema()
            """, new MapSqlParameterSource(), String.class));
        List<Referencia> refs = new ArrayList<>();
        for (Referencia r : CONFIG_CUENTA) {
            if (existentes.contains(r.tabla() + "." + r.columna())) refs.add(r);
        }
        return refs;
    }

    /** ¿La cuenta todavía tiene movimientos en el mayor? */
    public boolean tieneMovimientos(Long cuentaId) {
        Boolean b = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM asiento_detalle WHERE cuenta_id = :c)",
                new MapSqlParameterSource("c", cuentaId), Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    /** Deja la cuenta como agrupadora: ya no recibe movimiento ni es medio de pago. */
    public void volverAgrupadora(Long cuentaId) {
        jdbc.update("UPDATE plan_cuenta SET auxiliar = FALSE, es_medio_pago = FALSE WHERE id = :c",
                new MapSqlParameterSource("c", cuentaId));
    }

    // ── Fusión de terceros ──────────────────────────────────────────────

    /** Tabla y columna que apuntan a un tercero. */
    public record Referencia(String tabla, String columna) {
    }

    /**
     * Toda columna que referencia a un tercero: las que tienen llave foránea a
     * tercero(id) y las que siguen la convención *tercero_id sin llave.
     */
    public List<Referencia> referenciasATercero() {
        Set<String> vistas = new LinkedHashSet<>();
        List<Referencia> refs = new ArrayList<>();
        List<Map<String, Object>> filas = new ArrayList<>(jdbc.queryForList("""
            SELECT c.conrelid::regclass::text AS tabla, a.attname AS columna
              FROM pg_constraint c
              JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
             WHERE c.contype = 'f' AND c.confrelid = 'tercero'::regclass
            """, new MapSqlParameterSource()));
        filas.addAll(jdbc.queryForList("""
            SELECT c.table_name AS tabla, c.column_name AS columna
              FROM information_schema.columns c
              JOIN information_schema.tables t
                ON t.table_schema = c.table_schema AND t.table_name = c.table_name
             WHERE c.table_schema = current_schema() AND c.column_name LIKE '%tercero_id'
               AND t.table_type = 'BASE TABLE'
               -- las bitácoras guardan el origen a propósito
               AND c.table_name NOT LIKE '%log'
            """, new MapSqlParameterSource()));
        for (Map<String, Object> f : filas) {
            String tabla = String.valueOf(f.get("tabla")).replace("\"", "");
            String columna = String.valueOf(f.get("columna"));
            if ("tercero".equals(tabla) && "id".equals(columna)) continue;
            if (vistas.add(tabla + "." + columna)) {
                refs.add(new Referencia(tabla, columna));
            }
        }
        return refs;
    }

    public int contar(Referencia r, Long terceroId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM \"" + r.tabla() + "\" WHERE \"" + r.columna() + "\" = :t",
                new MapSqlParameterSource("t", terceroId), Integer.class);
        return n != null ? n : 0;
    }

    public int mover(Referencia r, Long origenId, Long destinoId) {
        return jdbc.update("UPDATE \"" + r.tabla() + "\" SET \"" + r.columna() + "\" = :d WHERE \"" + r.columna() + "\" = :o",
                new MapSqlParameterSource().addValue("o", origenId).addValue("d", destinoId));
    }

    /** Roles: el destino gana los que tenía el origen y el origen se queda sin roles. */
    public void fusionarRoles(Long origenId, Long destinoId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("o", origenId).addValue("d", destinoId);
        jdbc.update("""
            INSERT INTO tercero_rol (tercero_id, rol)
            SELECT :d, r.rol FROM tercero_rol r
             WHERE r.tercero_id = :o
               AND NOT EXISTS (SELECT 1 FROM tercero_rol x WHERE x.tercero_id = :d AND x.rol = r.rol)
            """, p);
        jdbc.update("DELETE FROM tercero_rol WHERE tercero_id = :o", p);
    }

    /** Cupo de crédito: si los dos lo tienen se conserva el del destino. */
    public void fusionarCredito(Long origenId, Long destinoId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("o", origenId).addValue("d", destinoId);
        Integer delDestino = jdbc.queryForObject("SELECT COUNT(*) FROM tercero_credito WHERE tercero_id = :d", p, Integer.class);
        if (delDestino != null && delDestino > 0) {
            jdbc.update("DELETE FROM tercero_credito WHERE tercero_id = :o", p);
        } else {
            jdbc.update("UPDATE tercero_credito SET tercero_id = :d WHERE tercero_id = :o", p);
        }
    }

    public void desactivarTercero(Long terceroId) {
        jdbc.update("UPDATE tercero SET activo = FALSE, deleted_at = now() WHERE id = :t",
                new MapSqlParameterSource("t", terceroId));
    }

    public void logFusion(Integer empresaId, Long origenId, Long destinoId, String documento, String nombre,
            String detalle, int registros, String motivo, Long usuarioId) {
        jdbc.update("""
            INSERT INTO fusion_tercero_log (empresa_id, tercero_origen_id, tercero_destino_id, origen_documento,
                                            origen_nombre, detalle, registros_movidos, motivo, usuario_id)
            VALUES (:e, :o, :d, :doc, :nom, :det, :n, :m, :u)
            """, new MapSqlParameterSource().addValue("e", empresaId).addValue("o", origenId).addValue("d", destinoId)
                .addValue("doc", documento).addValue("nom", nombre).addValue("det", detalle).addValue("n", registros)
                .addValue("m", motivo).addValue("u", usuarioId));
    }

    public List<Map<String, Object>> historialFusiones(Integer empresaId) {
        return jdbc.queryForList("""
            SELECT l.id, l.origen_documento, l.origen_nombre, l.registros_movidos, l.motivo, l.created_at, l.detalle,
                   COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''), TRIM(CONCAT_WS(' ', t.nombres, t.apellidos)))
                       AS destino_nombre
              FROM fusion_tercero_log l
              LEFT JOIN tercero t ON t.id = l.tercero_destino_id
             WHERE l.empresa_id = :e
             ORDER BY l.created_at DESC
             LIMIT 100
            """, new MapSqlParameterSource("e", empresaId));
    }
}
