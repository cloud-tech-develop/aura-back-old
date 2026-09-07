package com.cloud_technological.aura_pos.repositories.auditoria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.auditoria.HallazgoDetalleDto;

/**
 * Los cruces de la auditoría.
 *
 * <p>Cada método compara <b>dos fuentes que deberían decir lo mismo</b> y
 * devuelve las filas donde no coinciden. Ninguno reimplementa una regla de
 * negocio: si una cifra sale mal, se arregla donde se produce — un reporte que
 * "corrige" al vuelo esconde el problema que existe para destapar.
 *
 * <p>Cada cruce viene en dos versiones: el conteo con su monto (lo que necesita
 * el semáforo) y el detalle acotado (lo que necesita el anexo). El detalle se
 * pide aparte porque un período con miles de descuadres devolvería una
 * respuesta inmanejable en la portada.
 */
@Repository
public class AuditoriaQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * El resumen de un cruce: cuántas filas, cuánta plata y —cuando aplica— la
     * magnitud física.
     *
     * <p>{@code unidades} existe porque un descuadre de inventario valorizado en
     * $0 (producto sin costo cargado) se lee como "no pasa nada" cuando lo que
     * pasa es que hay unidades sin origen.
     */
    public record Resumen(int cantidad, BigDecimal monto, BigDecimal unidades) {
        static final Resumen VACIO = new Resumen(0, BigDecimal.ZERO, null);

        public Resumen(int cantidad, BigDecimal monto) {
            this(cantidad, monto, null);
        }
    }

    // ══ (1) Caja: lo contado contra lo que debería haber ══════════════════

    /**
     * Arqueos cerrados con diferencia.
     *
     * <p>Se usa {@code diferencia_ajustada} cuando existe: es la que queda
     * después de las correcciones retroactivas, y reportar la original haría
     * aparecer como pendiente algo que ya se resolvió.
     */
    private static final String CAJA_DIFERENCIAS = """
              FROM turno_caja t
              JOIN caja cj    ON cj.id = t.caja_id
              JOIN sucursal s ON s.id = cj.sucursal_id
              LEFT JOIN usuario u ON u.id = t.usuario_id
             WHERE s.empresa_id = :empresaId
               AND t.estado = 'CERRADA'
               AND t.fecha_cierre::date BETWEEN :desde AND :hasta
               AND ABS(COALESCE(t.diferencia_ajustada, t.diferencia, 0)) >= :umbral
            """;

    public Resumen cajaDiferencias(Integer empresaId, LocalDate desde, LocalDate hasta,
            BigDecimal umbral) {
        return resumen("SELECT COUNT(*) AS cantidad,"
                + " COALESCE(SUM(ABS(COALESCE(t.diferencia_ajustada, t.diferencia, 0))), 0) AS monto"
                + CAJA_DIFERENCIAS, empresaId, desde, hasta, umbral);
    }

    public List<HallazgoDetalleDto> cajaDiferenciasDetalle(Integer empresaId, LocalDate desde,
            LocalDate hasta, BigDecimal umbral, int limite) {
        return detalle("""
                SELECT 'Turno #' || t.id                        AS referencia,
                       t.fecha_cierre::date                     AS fecha,
                       cj.nombre || ' · ' || COALESCE(u.username, 'sin usuario')
                           || CASE WHEN COALESCE(t.diferencia_ajustada, t.diferencia, 0) < 0
                                   THEN ' — faltante' ELSE ' — sobrante' END AS descripcion,
                       ABS(COALESCE(t.diferencia_ajustada, t.diferencia, 0)) AS monto,
                       'TURNO_CAJA'                             AS origen_tipo,
                       t.id                                     AS origen_id
                """ + CAJA_DIFERENCIAS
                + " ORDER BY ABS(COALESCE(t.diferencia_ajustada, t.diferencia, 0)) DESC LIMIT :limite",
                empresaId, desde, hasta, umbral, limite);
    }

    // ══ (2)(3) Cartera: el saldo guardado contra los abonos vivos ═════════

    /**
     * {@code saldo_pendiente} es una columna denormalizada que actualizan los
     * abonos. Si se separó de la suma real, alguien borró un abono a mano o una
     * anulación quedó a medias — y el tercero va a reclamar por la cifra que no
     * cuadra.
     */
    private String carteraDescuadre(String tabla, String tablaAbonos, String fk) {
        return "  FROM " + tabla + " c\n"
                + " WHERE c.empresa_id = :empresaId\n"
                + "   AND c.deleted_at IS NULL\n"
                + "   AND c.fecha_emision::date BETWEEN :desde AND :hasta\n"
                + "   AND ABS(COALESCE(c.saldo_pendiente, 0) - (COALESCE(c.total_deuda, 0)"
                + "       - COALESCE((SELECT SUM(a.monto) FROM " + tablaAbonos + " a"
                + "          WHERE a." + fk + " = c.id AND a.deleted_at IS NULL), 0))) >= :umbral\n";
    }

    public Resumen carteraDescuadre(Integer empresaId, LocalDate desde, LocalDate hasta,
            boolean porCobrar, BigDecimal umbral) {
        String base = porCobrar
                ? carteraDescuadre("cuentas_cobrar", "abonos_cobrar", "cuenta_cobrar_id")
                : carteraDescuadre("cuentas_pagar", "abonos_pagar", "cuenta_pagar_id");
        String abonos = porCobrar
                ? "(SELECT SUM(a.monto) FROM abonos_cobrar a WHERE a.cuenta_cobrar_id = c.id AND a.deleted_at IS NULL)"
                : "(SELECT SUM(a.monto) FROM abonos_pagar a WHERE a.cuenta_pagar_id = c.id AND a.deleted_at IS NULL)";
        return resumen("SELECT COUNT(*) AS cantidad,"
                + " COALESCE(SUM(ABS(COALESCE(c.saldo_pendiente,0) - (COALESCE(c.total_deuda,0)"
                + " - COALESCE(" + abonos + ",0)))), 0) AS monto\n" + base,
                empresaId, desde, hasta, umbral);
    }

    public List<HallazgoDetalleDto> carteraDescuadreDetalle(Integer empresaId, LocalDate desde,
            LocalDate hasta, boolean porCobrar, BigDecimal umbral, int limite) {
        String base = porCobrar
                ? carteraDescuadre("cuentas_cobrar", "abonos_cobrar", "cuenta_cobrar_id")
                : carteraDescuadre("cuentas_pagar", "abonos_pagar", "cuenta_pagar_id");
        String abonos = porCobrar
                ? "COALESCE((SELECT SUM(a.monto) FROM abonos_cobrar a WHERE a.cuenta_cobrar_id = c.id AND a.deleted_at IS NULL), 0)"
                : "COALESCE((SELECT SUM(a.monto) FROM abonos_pagar a WHERE a.cuenta_pagar_id = c.id AND a.deleted_at IS NULL), 0)";
        return detalle("SELECT c.numero_cuenta AS referencia,\n"
                + "       c.fecha_emision::date AS fecha,\n"
                + "       'Saldo guardado ' || COALESCE(c.saldo_pendiente, 0)\n"
                + "           || ' · según abonos ' || (COALESCE(c.total_deuda,0) - " + abonos + ")\n"
                + "                                    AS descripcion,\n"
                + "       ABS(COALESCE(c.saldo_pendiente,0) - (COALESCE(c.total_deuda,0) - " + abonos + ")) AS monto,\n"
                + "       '" + (porCobrar ? "CUENTA_COBRAR" : "CUENTA_PAGAR") + "' AS origen_tipo,\n"
                + "       c.id AS origen_id\n"
                + base + " ORDER BY monto DESC LIMIT :limite",
                empresaId, desde, hasta, umbral, limite);
    }

    // ══ (4) Kardex: la columna cantidad contra la variación del saldo ═════

    /**
     * {@code cantidad} y {@code saldo_nuevo − saldo_anterior} deberían decir lo
     * mismo.
     *
     * <p>El reconteo se compara en valor absoluto: guarda la cantidad sin signo
     * y pone el sentido en el nombre del tipo. Es una inconsistencia conocida y
     * deliberada, así que reportarla sería ruido — lo que importa es que la
     * magnitud coincida.
     */
    private static final String KARDEX_SIGNO = """
              FROM movimiento_inventario m
              JOIN producto p ON p.id = m.producto_id
              JOIN sucursal s ON s.id = m.sucursal_id
             WHERE s.empresa_id = :empresaId
               AND m.created_at::date BETWEEN :desde AND :hasta
               AND CASE WHEN m.tipo_movimiento LIKE 'RECONTEO%'
                        THEN ABS(COALESCE(m.cantidad, 0))
                             <> ABS(COALESCE(m.saldo_nuevo,0) - COALESCE(m.saldo_anterior,0))
                        ELSE COALESCE(m.cantidad, 0)
                             <> (COALESCE(m.saldo_nuevo,0) - COALESCE(m.saldo_anterior,0))
                   END
            """;

    public Resumen kardexSigno(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return resumen("SELECT COUNT(*) AS cantidad,"
                + " COALESCE(SUM(ABS(COALESCE(m.cantidad,0)"
                + " - (COALESCE(m.saldo_nuevo,0) - COALESCE(m.saldo_anterior,0)))"
                + " * COALESCE(m.costo_historico,0)), 0) AS monto" + KARDEX_SIGNO,
                empresaId, desde, hasta);
    }

    public List<HallazgoDetalleDto> kardexSignoDetalle(Integer empresaId, LocalDate desde,
            LocalDate hasta, int limite) {
        return detalle("""
                SELECT COALESCE(m.referencia_origen, 'Movimiento #' || m.id) AS referencia,
                       m.created_at::date                                    AS fecha,
                       p.nombre || ' · ' || m.tipo_movimiento
                           || ' · cantidad ' || COALESCE(m.cantidad, 0)
                           || ' vs saldo ' || (COALESCE(m.saldo_nuevo,0) - COALESCE(m.saldo_anterior,0))
                                                                             AS descripcion,
                       ABS(COALESCE(m.cantidad,0)
                           - (COALESCE(m.saldo_nuevo,0) - COALESCE(m.saldo_anterior,0)))
                           * COALESCE(m.costo_historico,0)                    AS monto,
                       'MOVIMIENTO_INVENTARIO'                                AS origen_tipo,
                       m.id                                                   AS origen_id
                """ + KARDEX_SIGNO + " ORDER BY m.id DESC LIMIT :limite",
                empresaId, desde, hasta, limite);
    }

    // ══ (5) Kardex contra el stock actual ════════════════════════════════

    /**
     * El último {@code saldo_nuevo} de cada producto y sucursal debería ser su
     * {@code inventario.stock_actual}.
     *
     * <p>Este cruce NO se acota al período: el stock es acumulado, así que un
     * descuadre de hace tres meses sigue estando mal hoy. Es la única foto de
     * "cuánto inventario existe que su historia no explica".
     */
    private static final String KARDEX_VS_STOCK = """
              FROM inventario i
              JOIN sucursal s ON s.id = i.sucursal_id
              JOIN producto p ON p.id = i.producto_id
              JOIN LATERAL (
                   SELECT m.saldo_nuevo, m.created_at
                     FROM movimiento_inventario m
                    WHERE m.producto_id = i.producto_id
                      AND m.sucursal_id = i.sucursal_id
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT 1
              ) ult ON TRUE
             WHERE s.empresa_id = :empresaId
               AND ABS(COALESCE(i.stock_actual,0) - COALESCE(ult.saldo_nuevo,0)) > 0.0001
            """;

    /**
     * Se valoriza con el costo y, si el producto no lo tiene cargado, con el
     * precio de venta. Un descuadre real valorizado en $0 porque falta el costo
     * se lee como "no pasa nada"; por eso además se devuelven las unidades.
     */
    public Resumen kardexVsStock(Integer empresaId) {
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return unResumen("SELECT COUNT(*) AS cantidad,"
                + " COALESCE(SUM(ABS(COALESCE(i.stock_actual,0) - COALESCE(ult.saldo_nuevo,0))"
                + " * COALESCE(NULLIF(p.costo, 0), NULLIF(p.precio, 0), 0)), 0) AS monto,"
                + " COALESCE(SUM(ABS(COALESCE(i.stock_actual,0) - COALESCE(ult.saldo_nuevo,0))), 0)"
                + " AS unidades" + KARDEX_VS_STOCK, params);
    }

    public List<HallazgoDetalleDto> kardexVsStockDetalle(Integer empresaId, int limite) {
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("limite", limite);
        return jdbcTemplate.query("""
                SELECT COALESCE(p.sku, 'Producto #' || p.id)  AS referencia,
                       ult.created_at::date                    AS fecha,
                       p.nombre || ' · ' || s.nombre
                           || ' · stock ' || COALESCE(i.stock_actual,0)
                           || ' vs kardex ' || COALESCE(ult.saldo_nuevo,0) AS descripcion,
                       ABS(COALESCE(i.stock_actual,0) - COALESCE(ult.saldo_nuevo,0))
                           * COALESCE(NULLIF(p.costo, 0), NULLIF(p.precio, 0), 0) AS monto,
                       'PRODUCTO'                              AS origen_tipo,
                       p.id                                    AS origen_id
                """ + KARDEX_VS_STOCK + " ORDER BY monto DESC LIMIT :limite",
                params, new BeanPropertyRowMapper<>(HallazgoDetalleDto.class));
    }

    // ══ (6) Contabilidad: partida doble ══════════════════════════════════

    private static final String ASIENTOS_DESCUADRADOS = """
              FROM asiento_contable a
             WHERE a.empresa_id = :empresaId
               AND a.estado <> 'ANULADO'
               AND a.fecha BETWEEN :desde AND :hasta
               AND ABS(COALESCE(a.total_debito,0) - COALESCE(a.total_credito,0)) > 0.01
            """;

    public Resumen asientosDescuadrados(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return resumen("SELECT COUNT(*) AS cantidad,"
                + " COALESCE(SUM(ABS(COALESCE(a.total_debito,0) - COALESCE(a.total_credito,0))), 0) AS monto"
                + ASIENTOS_DESCUADRADOS, empresaId, desde, hasta);
    }

    public List<HallazgoDetalleDto> asientosDescuadradosDetalle(Integer empresaId, LocalDate desde,
            LocalDate hasta, int limite) {
        return detalle("""
                SELECT COALESCE(a.numero_comprobante, 'Asiento #' || a.id) AS referencia,
                       a.fecha                                             AS fecha,
                       a.descripcion || ' · débito ' || COALESCE(a.total_debito,0)
                           || ' vs crédito ' || COALESCE(a.total_credito,0) AS descripcion,
                       ABS(COALESCE(a.total_debito,0) - COALESCE(a.total_credito,0)) AS monto,
                       'ASIENTO'                                           AS origen_tipo,
                       a.id                                                AS origen_id
                """ + ASIENTOS_DESCUADRADOS + " ORDER BY monto DESC LIMIT :limite",
                empresaId, desde, hasta, limite);
    }

    /**
     * La cabecera del asiento contra la suma de sus líneas.
     *
     * <p>Un asiento puede cumplir partida doble en la cabecera y tener detalles
     * que no suman eso: los totales se guardan al crear y las líneas se pueden
     * haber tocado después.
     */
    private static final String CABECERA_VS_DETALLE = """
              FROM asiento_contable a
              JOIN (SELECT asiento_id,
                           SUM(COALESCE(debito,0))  AS db,
                           SUM(COALESCE(credito,0)) AS cr
                      FROM asiento_detalle GROUP BY asiento_id) d ON d.asiento_id = a.id
             WHERE a.empresa_id = :empresaId
               AND a.estado <> 'ANULADO'
               AND a.fecha BETWEEN :desde AND :hasta
               AND (ABS(COALESCE(a.total_debito,0) - d.db) > 0.01
                    OR ABS(COALESCE(a.total_credito,0) - d.cr) > 0.01)
            """;

    public Resumen cabeceraVsDetalle(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return resumen("SELECT COUNT(*) AS cantidad,"
                + " COALESCE(SUM(ABS(COALESCE(a.total_debito,0) - d.db)), 0) AS monto"
                + CABECERA_VS_DETALLE, empresaId, desde, hasta);
    }

    public List<HallazgoDetalleDto> cabeceraVsDetalleDetalle(Integer empresaId, LocalDate desde,
            LocalDate hasta, int limite) {
        return detalle("""
                SELECT COALESCE(a.numero_comprobante, 'Asiento #' || a.id) AS referencia,
                       a.fecha                                             AS fecha,
                       'Cabecera ' || COALESCE(a.total_debito,0)
                           || ' vs líneas ' || d.db                        AS descripcion,
                       ABS(COALESCE(a.total_debito,0) - d.db)              AS monto,
                       'ASIENTO'                                           AS origen_tipo,
                       a.id                                                AS origen_id
                """ + CABECERA_VS_DETALLE + " ORDER BY monto DESC LIMIT :limite",
                empresaId, desde, hasta, limite);
    }

    // ══ (8) Facturación electrónica ══════════════════════════════════════

    private static final String FACTURAS_SIN_ACEPTAR = """
              FROM factura f
             WHERE f.empresa_id = :empresaId
               AND f.deleted_at IS NULL
               AND f.fecha_hora_emision::date BETWEEN :desde AND :hasta
               AND COALESCE(f.estado_dian, 'PENDIENTE') = 'PENDIENTE'
            """;

    public Resumen facturasSinAceptar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return resumen("SELECT COUNT(*) AS cantidad, COALESCE(SUM(f.valor), 0) AS monto"
                + FACTURAS_SIN_ACEPTAR, empresaId, desde, hasta);
    }

    public List<HallazgoDetalleDto> facturasSinAceptarDetalle(Integer empresaId, LocalDate desde,
            LocalDate hasta, int limite) {
        return detalle("""
                SELECT COALESCE(f.prefijo, '') || COALESCE(f.consecutivo::text, f.id::text) AS referencia,
                       f.fecha_hora_emision::date AS fecha,
                       'Emitida sin confirmación de la DIAN' AS descripcion,
                       COALESCE(f.valor, 0)       AS monto,
                       'FACTURA'                  AS origen_tipo,
                       f.id                       AS origen_id
                """ + FACTURAS_SIN_ACEPTAR + " ORDER BY f.fecha_hora_emision DESC LIMIT :limite",
                empresaId, desde, hasta, limite);
    }

    // ══ (10) Gastos sin soporte ══════════════════════════════════════════

    /**
     * Un gasto marcado como deducible sin documento soporte no baja el impuesto:
     * la DIAN lo rechaza. La plata está y se sabe dónde — por eso es MEDIA — pero
     * el beneficio tributario que el negocio cree tener no existe.
     */
    private static final String GASTOS_SIN_SOPORTE = """
              FROM gasto g
             WHERE g.empresa_id = :empresaId
               AND g.estado = 'ACTIVO'
               AND g.deducible
               AND g.fecha BETWEEN :desde AND :hasta
               AND (g.numero_doc_soporte IS NULL OR TRIM(g.numero_doc_soporte) = ''
                    OR g.tercero_id IS NULL)
            """;

    public Resumen gastosSinSoporte(Integer empresaId, LocalDate desde, LocalDate hasta) {
        return resumen("SELECT COUNT(*) AS cantidad, COALESCE(SUM(g.monto), 0) AS monto"
                + GASTOS_SIN_SOPORTE, empresaId, desde, hasta);
    }

    public List<HallazgoDetalleDto> gastosSinSoporteDetalle(Integer empresaId, LocalDate desde,
            LocalDate hasta, int limite) {
        return detalle("""
                SELECT 'Gasto #' || g.id AS referencia,
                       g.fecha           AS fecha,
                       g.categoria || ' · '
                           || CASE WHEN g.tercero_id IS NULL THEN 'sin tercero'
                                   ELSE 'sin documento soporte' END AS descripcion,
                       COALESCE(g.monto, 0) AS monto,
                       'GASTO'              AS origen_tipo,
                       g.id                 AS origen_id
                """ + GASTOS_SIN_SOPORTE + " ORDER BY g.monto DESC LIMIT :limite",
                empresaId, desde, hasta, limite);
    }

    // ══ (9) HEREDADO: abonos que no cayeron en ningún arqueo ═════════════

    /**
     * Antes de V154 el comprobante manual no declaraba de dónde salía la plata:
     * el abono nacía sin turno y con {@code metodo_pago = 'COMPROBANTE'}. Esa
     * plata entró o salió sin aparecer en el cierre de nadie.
     */
    private String abonosSinArqueo(boolean porCobrar) {
        String tabla = porCobrar ? "abonos_cobrar" : "abonos_pagar";
        String cuentas = porCobrar ? "cuentas_cobrar" : "cuentas_pagar";
        String fk = porCobrar ? "cuenta_cobrar_id" : "cuenta_pagar_id";
        return "  FROM " + tabla + " a\n"
                + "  JOIN " + cuentas + " c ON c.id = a." + fk + "\n"
                + " WHERE c.empresa_id = :empresaId\n"
                + "   AND a.deleted_at IS NULL\n"
                + "   AND a.turno_caja_id IS NULL\n"
                + "   AND NOT a.caja_otro_dia\n"
                + "   AND UPPER(COALESCE(a.metodo_pago, '')) = 'COMPROBANTE'\n"
                + "   AND a.fecha_pago::date BETWEEN :desde AND :hasta\n";
    }

    public Resumen abonosSinArqueo(Integer empresaId, LocalDate desde, LocalDate hasta,
            boolean porCobrar) {
        return resumen("SELECT COUNT(*) AS cantidad, COALESCE(SUM(a.monto), 0) AS monto\n"
                + abonosSinArqueo(porCobrar), empresaId, desde, hasta);
    }

    public List<HallazgoDetalleDto> abonosSinArqueoDetalle(Integer empresaId, LocalDate desde,
            LocalDate hasta, boolean porCobrar, int limite) {
        return detalle("SELECT COALESCE(a.referencia, 'Abono #' || a.id) AS referencia,\n"
                + "       a.fecha_pago::date AS fecha,\n"
                + "       c.numero_cuenta || ' · no entró a ningún arqueo' AS descripcion,\n"
                + "       COALESCE(a.monto, 0) AS monto,\n"
                + "       '" + (porCobrar ? "ABONO_COBRAR" : "ABONO_PAGAR") + "' AS origen_tipo,\n"
                + "       a.id AS origen_id\n"
                + abonosSinArqueo(porCobrar) + " ORDER BY a.fecha_pago DESC LIMIT :limite",
                empresaId, desde, hasta, limite);
    }

    // ══ HEREDADO: facturas sin fecha de emisión ══════════════════════════

    /**
     * Sin {@code fecha_hora_emision} la factura existe pero desaparece de todo
     * reporte que filtre por fecha — que son todos.
     *
     * <p>No se acota al período justamente porque no tiene fecha con la cual
     * acotarlo: ese es el hallazgo.
     */
    public Resumen facturasSinFecha(Integer empresaId) {
        return unResumen("""
                SELECT COUNT(*) AS cantidad, COALESCE(SUM(f.valor), 0) AS monto
                  FROM factura f
                 WHERE f.empresa_id = :empresaId
                   AND f.deleted_at IS NULL
                   AND f.fecha_hora_emision IS NULL
                """, new MapSqlParameterSource("empresaId", empresaId));
    }

    public List<HallazgoDetalleDto> facturasSinFechaDetalle(Integer empresaId, int limite) {
        return jdbcTemplate.query("""
                SELECT COALESCE(f.prefijo, '') || COALESCE(f.consecutivo::text, f.id::text) AS referencia,
                       f.created_at::date  AS fecha,
                       'Sin fecha de emisión: no sale en ningún reporte por fecha' AS descripcion,
                       COALESCE(f.valor, 0) AS monto,
                       'FACTURA'            AS origen_tipo,
                       f.id                 AS origen_id
                  FROM factura f
                 WHERE f.empresa_id = :empresaId
                   AND f.deleted_at IS NULL
                   AND f.fecha_hora_emision IS NULL
                 ORDER BY f.id DESC LIMIT :limite
                """,
                new MapSqlParameterSource("empresaId", empresaId).addValue("limite", limite),
                new BeanPropertyRowMapper<>(HallazgoDetalleDto.class));
    }

    // ══ Contexto: qué pasó en el inventario durante esos turnos ══════════

    /**
     * Los movimientos de inventario "sospechosos" ocurridos dentro de la
     * ventana de unos turnos concretos.
     *
     * <p>Es lo que convierte "hay un sobrante de $954.000" en una pista: si
     * durante ese mismo turno salió mercancía como merma, obsequio o ajuste de
     * reconteo, las dos cosas cuentan la misma historia — alguien vendió y
     * cubrió la salida por otra vía. Por separado, cada una parece un problema
     * distinto y ninguno se resuelve.
     *
     * <p>Se excluyen VENTA y COMPRA a propósito: son el movimiento normal del
     * negocio y llenarían la lista tapando lo que sí hay que mirar.
     */
    public List<HallazgoDetalleDto> movimientosDuranteTurnos(Integer empresaId,
            List<Long> turnoIds, int limite) {
        if (turnoIds == null || turnoIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT COALESCE(m.referencia_origen, 'Movimiento #' || m.id) AS referencia,
                       m.created_at::date                                    AS fecha,
                       'Turno #' || t.id || ' · ' || p.nombre || ' · '
                           || m.tipo_movimiento || ' · '
                           || ABS(COALESCE(m.saldo_nuevo,0) - COALESCE(m.saldo_anterior,0))
                           || ' unidades'                                    AS descripcion,
                       ABS(COALESCE(m.saldo_nuevo,0) - COALESCE(m.saldo_anterior,0))
                           * COALESCE(NULLIF(p.costo, 0), NULLIF(p.precio, 0), 0) AS monto,
                       'MOVIMIENTO_INVENTARIO'                               AS origen_tipo,
                       m.id                                                  AS origen_id
                  FROM turno_caja t
                  JOIN caja cj ON cj.id = t.caja_id
                  JOIN sucursal s ON s.id = cj.sucursal_id
                  JOIN movimiento_inventario m
                       ON m.sucursal_id = s.id
                      AND m.created_at >= t.fecha_apertura
                      AND m.created_at <= COALESCE(t.fecha_cierre, NOW())
                  JOIN producto p ON p.id = m.producto_id
                 WHERE s.empresa_id = :empresaId
                   AND t.id IN (:turnoIds)
                   AND m.tipo_movimiento NOT IN ('VENTA', 'COMPRA')
                 ORDER BY monto DESC
                 LIMIT :limite
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("empresaId", empresaId)
                        .addValue("turnoIds", turnoIds).addValue("limite", limite),
                new BeanPropertyRowMapper<>(HallazgoDetalleDto.class));
    }

    // ══ Utilidades ═══════════════════════════════════════════════════════

    private Resumen resumen(String sql, Integer empresaId, LocalDate desde, LocalDate hasta) {
        return unResumen(sql, new MapSqlParameterSource("empresaId", empresaId)
                .addValue("desde", desde).addValue("hasta", hasta));
    }

    private Resumen resumen(String sql, Integer empresaId, LocalDate desde, LocalDate hasta,
            BigDecimal umbral) {
        return unResumen(sql, new MapSqlParameterSource("empresaId", empresaId)
                .addValue("desde", desde).addValue("hasta", hasta)
                .addValue("umbral", umbral));
    }

    private Resumen unResumen(String sql, MapSqlParameterSource params) {
        Map<String, Object> fila = jdbcTemplate.queryForMap(sql, params);
        Object cantidad = fila.get("cantidad");
        Object monto = fila.get("monto");
        if (cantidad == null) {
            return Resumen.VACIO;
        }
        Object unidades = fila.get("unidades");
        return new Resumen(((Number) cantidad).intValue(),
                monto instanceof BigDecimal bd ? bd : BigDecimal.ZERO,
                unidades instanceof BigDecimal u ? u : null);
    }

    private List<HallazgoDetalleDto> detalle(String sql, Integer empresaId, LocalDate desde,
            LocalDate hasta, BigDecimal umbral, int limite) {
        return jdbcTemplate.query(sql, new MapSqlParameterSource("empresaId", empresaId)
                        .addValue("desde", desde).addValue("hasta", hasta)
                        .addValue("umbral", umbral).addValue("limite", limite),
                new BeanPropertyRowMapper<>(HallazgoDetalleDto.class));
    }

    private List<HallazgoDetalleDto> detalle(String sql, Integer empresaId, LocalDate desde,
            LocalDate hasta, int limite) {
        return jdbcTemplate.query(sql, new MapSqlParameterSource("empresaId", empresaId)
                        .addValue("desde", desde).addValue("hasta", hasta)
                        .addValue("limite", limite),
                new BeanPropertyRowMapper<>(HallazgoDetalleDto.class));
    }
}
