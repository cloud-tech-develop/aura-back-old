package com.cloud_technological.aura_pos.repositories.contabilidad;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioFiltroDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioSoporteDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

/**
 * Consultas del módulo de notas contables (comprobante de diario CD).
 *
 * <p>Una nota es un {@code asiento_contable} MANUAL con tipo_comprobante 'CD'.
 * Los RowMapper son por nombre de columna: una columna agregada al SELECT y no
 * declarada en el DTO no viaja, sin error.
 */
@Repository
public class NotaDiarioQueryRepository {

    /** Filtro que identifica una nota contable dentro de asiento_contable. */
    public static final String ES_NOTA = "a.tipo_origen = 'MANUAL' AND a.tipo_comprobante = 'CD'";

    private static final String NOMBRE_USUARIO = """
        COALESCE(
            NULLIF(TRIM(COALESCE(%1$s.nombres, '') || ' ' || COALESCE(%1$s.apellidos, '')), ''),
            NULLIF(TRIM(COALESCE(%1$s.razon_social, '')), ''),
            %2$s.username)
        """;

    private static final String SELECT_TABLA = """
        SELECT
            a.id,
            a.numero_comprobante,
            a.fecha::text                                   AS fecha,
            a.descripcion,
            a.total_debito,
            a.total_credito,
            a.estado,
            (SELECT COUNT(*) FROM asiento_detalle ad WHERE ad.asiento_id = a.id)::int AS cantidad_lineas,
            %s                                              AS elaborado_por,
            %s                                              AS contabilizado_por,
            a.created_at::text                              AS created_at,
            a.clasificacion,
            rd.numero_comprobante                           AS reversa_de_numero,
            rp.numero_comprobante                           AS revertido_por_numero,
            (SELECT COUNT(*) FROM nota_diario_soporte s
              WHERE s.asiento_id = a.id AND s.deleted_at IS NULL)::int AS cantidad_soportes,
            COUNT(*) OVER()                                 AS total_rows
        FROM asiento_contable a
        LEFT JOIN usuario u  ON u.id  = a.usuario_id
        LEFT JOIN tercero ut ON ut.id = u.tercero_id
        LEFT JOIN usuario uc ON uc.id = a.contabilizado_por
        LEFT JOIN tercero ct ON ct.id = uc.tercero_id
        LEFT JOIN asiento_contable rd ON rd.id = a.reversa_de_id
        LEFT JOIN asiento_contable rp ON rp.id = a.revertido_por_id
        WHERE a.empresa_id = :empresaId
          AND %s
        """.formatted(NOMBRE_USUARIO.formatted("ut", "u"), NOMBRE_USUARIO.formatted("ct", "uc"), ES_NOTA);

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public PageImpl<NotaDiarioTableDto> listar(PageableDto<NotaDiarioFiltroDto> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        NotaDiarioFiltroDto f = pageable.getParams();

        StringBuilder sql = new StringBuilder(SELECT_TABLA);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(a.descripcion) LIKE :search
                  OR LOWER(COALESCE(a.numero_comprobante, '')) LIKE :search)
                """);
            params.addValue("search", "%" + search + "%");
        }
        if (f != null && notBlank(f.getFechaDesde())) {
            sql.append(" AND a.fecha >= CAST(:desde AS DATE) ");
            params.addValue("desde", f.getFechaDesde());
        }
        if (f != null && notBlank(f.getFechaHasta())) {
            sql.append(" AND a.fecha <= CAST(:hasta AS DATE) ");
            params.addValue("hasta", f.getFechaHasta());
        }
        if (f != null && notBlank(f.getEstado())) {
            sql.append(" AND a.estado = :estado ");
            params.addValue("estado", f.getEstado().trim().toUpperCase());
        }
        if (f != null && notBlank(f.getClasificacion())) {
            sql.append(" AND a.clasificacion = :clasificacion ");
            params.addValue("clasificacion", f.getClasificacion().trim().toUpperCase());
        }

        // Los borradores arriba: son el trabajo pendiente del contador.
        sql.append("""
             ORDER BY (a.estado = 'BORRADOR') DESC, a.fecha DESC, a.id DESC
             OFFSET :offset LIMIT :limit
            """);
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<NotaDiarioTableDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(NotaDiarioTableDto.class));
        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public NotaDiarioDto obtener(Long id, Integer empresaId) {
        String sql = """
            SELECT
                a.id,
                a.numero_comprobante,
                a.fecha::text                AS fecha,
                a.descripcion,
                a.total_debito,
                a.total_credito,
                a.estado,
                CASE WHEN pc.id IS NULL THEN NULL
                     ELSE pc.anio || '-' || LPAD(pc.mes::text, 2, '0') END AS periodo,
                %s                           AS elaborado_por,
                a.created_at::text           AS created_at,
                a.updated_at::text           AS updated_at,
                %s                           AS contabilizado_por,
                a.contabilizado_at::text     AS contabilizado_at,
                %s                           AS anulado_por,
                a.anulado_at::text           AS anulado_at,
                a.motivo_anulacion,
                a.clasificacion,
                a.reversion_automatica,
                a.reversa_de_id,
                rd.numero_comprobante        AS reversa_de_numero,
                a.revertido_por_id,
                rp.numero_comprobante        AS revertido_por_numero,
                a.plantilla_id,
                npl.nombre                   AS plantilla_nombre
            FROM asiento_contable a
            LEFT JOIN asiento_contable rd ON rd.id = a.reversa_de_id
            LEFT JOIN asiento_contable rp ON rp.id = a.revertido_por_id
            LEFT JOIN nota_diario_plantilla npl ON npl.id = a.plantilla_id
            LEFT JOIN periodo_contable pc ON pc.id = a.periodo_contable_id
            LEFT JOIN usuario u  ON u.id  = a.usuario_id
            LEFT JOIN tercero ut ON ut.id = u.tercero_id
            LEFT JOIN usuario uc ON uc.id = a.contabilizado_por
            LEFT JOIN tercero ct ON ct.id = uc.tercero_id
            LEFT JOIN usuario ua ON ua.id = a.anulado_por
            LEFT JOIN tercero at2 ON at2.id = ua.tercero_id
            WHERE a.id = :id
              AND a.empresa_id = :empresaId
              AND %s
            """.formatted(NOMBRE_USUARIO.formatted("ut", "u"), NOMBRE_USUARIO.formatted("ct", "uc"),
                NOMBRE_USUARIO.formatted("at2", "ua"), ES_NOTA);
        List<NotaDiarioDto> rows = jdbc.query(sql,
                new MapSqlParameterSource("id", id).addValue("empresaId", empresaId),
                new BeanPropertyRowMapper<>(NotaDiarioDto.class));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<NotaDiarioLineaDto> lineas(Long asientoId) {
        return lineasDe("asiento_detalle", "asiento_id", "ad.id", asientoId);
    }

    /** Líneas de una plantilla, con los mismos nombres resueltos que las de una nota. */
    public List<NotaDiarioLineaDto> lineasPlantilla(Long plantillaId) {
        return lineasDe("nota_diario_plantilla_linea", "plantilla_id", "ad.orden, ad.id", plantillaId);
    }

    /** {@code tabla}, {@code fk} y {@code orden} son constantes de esta clase, nunca del usuario. */
    private List<NotaDiarioLineaDto> lineasDe(String tabla, String fk, String orden, Long padreId) {
        String sql = """
            SELECT
                ad.id,
                ad.cuenta_id,
                pc.codigo                    AS cuenta_codigo,
                pc.nombre                    AS cuenta_nombre,
                ad.descripcion,
                ad.debito,
                ad.credito,
                ad.tercero_id,
                COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''),
                         NULLIF(TRIM(COALESCE(t.nombres, '') || ' ' || COALESCE(t.apellidos, '')), '')) AS tercero_nombre,
                t.numero_documento           AS tercero_documento,
                ad.centro_costo_id,
                cc.nombre                    AS centro_costo_nombre,
                ad.proyecto_id,
                p.nombre                     AS proyecto_nombre,
                ad.frente_id,
                pf.nombre                    AS frente_nombre
            FROM %s ad
            JOIN plan_cuenta pc          ON pc.id = ad.cuenta_id
            LEFT JOIN tercero t          ON t.id  = ad.tercero_id
            LEFT JOIN centros_costos cc  ON cc.id = ad.centro_costo_id
            LEFT JOIN proyecto p         ON p.id  = ad.proyecto_id
            LEFT JOIN proyecto_frente pf ON pf.id = ad.frente_id
            WHERE ad.%s = :padreId
            ORDER BY %s
            """.formatted(tabla, fk, orden);
        return jdbc.query(sql, new MapSqlParameterSource("padreId", padreId),
                new BeanPropertyRowMapper<>(NotaDiarioLineaDto.class));
    }

    public List<NotaDiarioSoporteDto> soportes(Long asientoId) {
        String sql = """
            SELECT s.id, s.nombre_archivo, s.archivo_url, s.content_type, s.tamano_bytes,
                   %s AS subido_por,
                   s.created_at::text AS created_at
              FROM nota_diario_soporte s
              LEFT JOIN usuario u  ON u.id  = s.usuario_id
              LEFT JOIN tercero ut ON ut.id = u.tercero_id
             WHERE s.asiento_id = :asientoId AND s.deleted_at IS NULL
             ORDER BY s.id
            """.formatted(NOMBRE_USUARIO.formatted("ut", "u"));
        return jdbc.query(sql, new MapSqlParameterSource("asientoId", asientoId),
                new BeanPropertyRowMapper<>(NotaDiarioSoporteDto.class));
    }

    // ── Importación: de lo que escribe el contador a ids ─────────────────

    /** Opción resuelta para la importación: id, texto para mostrar y si se puede usar. */
    public record Resuelto(Long id, String etiqueta, boolean usable, String motivo) {}

    /** Cuentas por código exacto. usable = activa y auxiliar. */
    public Map<String, Resuelto> cuentasPorCodigo(Integer empresaId, Collection<String> codigos) {
        Map<String, Resuelto> out = new HashMap<>();
        if (codigos.isEmpty()) return out;
        jdbc.query("""
            SELECT id, codigo, nombre, COALESCE(activa, FALSE) AS activa, COALESCE(auxiliar, FALSE) AS auxiliar
              FROM plan_cuenta
             WHERE empresa_id = :empresaId AND codigo IN (:codigos)
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("codigos", codigos), rs -> {
            boolean activa = rs.getBoolean("activa");
            boolean auxiliar = rs.getBoolean("auxiliar");
            String motivo = !activa ? "está inactiva" : !auxiliar ? "no es de movimiento (elija una auxiliar)" : null;
            out.put(rs.getString("codigo"), new Resuelto(rs.getLong("id"),
                    rs.getString("codigo") + " - " + rs.getString("nombre"), activa && auxiliar, motivo));
        });
        return out;
    }

    /** Terceros por número de documento (sin DV). Si hay dos con el mismo, se toma el más antiguo. */
    public Map<String, Resuelto> tercerosPorDocumento(Integer empresaId, Collection<String> documentos) {
        Map<String, Resuelto> out = new HashMap<>();
        if (documentos.isEmpty()) return out;
        jdbc.query("""
            SELECT DISTINCT ON (t.numero_documento) t.id, t.numero_documento,
                   COALESCE(NULLIF(TRIM(COALESCE(t.razon_social, '')), ''),
                            NULLIF(TRIM(COALESCE(t.nombres, '') || ' ' || COALESCE(t.apellidos, '')), '')) AS nombre
              FROM tercero t
             WHERE t.empresa_id = :empresaId AND t.deleted_at IS NULL AND t.numero_documento IN (:docs)
             ORDER BY t.numero_documento, t.id
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("docs", documentos), rs -> {
            out.put(rs.getString("numero_documento"), new Resuelto(rs.getLong("id"),
                    rs.getString("numero_documento") + " — " + rs.getString("nombre"), true, null));
        });
        return out;
    }

    /** Centros de costo por código. */
    public Map<String, Resuelto> centrosCostoPorCodigo(Integer empresaId, Collection<String> codigos) {
        Map<String, Resuelto> out = new HashMap<>();
        if (codigos.isEmpty()) return out;
        jdbc.query("""
            SELECT id, codigo, nombre, COALESCE(activo, TRUE) AS activo
              FROM centros_costos
             WHERE empresa_id = :empresaId AND deleted_at IS NULL AND codigo IN (:codigos)
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("codigos", codigos), rs -> {
            boolean activo = rs.getBoolean("activo");
            out.put(rs.getString("codigo"), new Resuelto(rs.getLong("id"),
                    rs.getString("codigo") + " — " + rs.getString("nombre"), activo, activo ? null : "está inactivo"));
        });
        return out;
    }

    // ── Validaciones ─────────────────────────────────────────────────────

    /** Cuenta del plan con lo necesario para decidir si admite movimiento. */
    public record CuentaMovimiento(Long id, String codigo, String nombre, boolean activa, boolean auxiliar) {}

    /** Las cuentas de la empresa entre {@code ids}; las ajenas o inexistentes no vuelven. */
    public Map<Long, CuentaMovimiento> cuentas(Integer empresaId, Collection<Long> ids) {
        Map<Long, CuentaMovimiento> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        jdbc.query("""
            SELECT id, codigo, nombre, COALESCE(activa, FALSE) AS activa, COALESCE(auxiliar, FALSE) AS auxiliar
              FROM plan_cuenta
             WHERE empresa_id = :empresaId AND id IN (:ids)
            """, new MapSqlParameterSource("empresaId", empresaId).addValue("ids", ids),
            rs -> {
                out.put(rs.getLong("id"), new CuentaMovimiento(rs.getLong("id"), rs.getString("codigo"),
                        rs.getString("nombre"), rs.getBoolean("activa"), rs.getBoolean("auxiliar")));
            });
        return out;
    }

    /** Dimensiones que se validan contra la empresa. El nombre de tabla nunca viene del usuario. */
    public enum Dimension {
        TERCERO("tercero", "deleted_at IS NULL"),
        CENTRO_COSTO("centros_costos", "deleted_at IS NULL"),
        PROYECTO("proyecto", "deleted_at IS NULL"),
        FRENTE("proyecto_frente", "deleted_at IS NULL");

        final String tabla;
        final String vigente;

        Dimension(String tabla, String vigente) {
            this.tabla = tabla;
            this.vigente = vigente;
        }
    }

    /** Cuántos de {@code ids} existen, están vigentes y son de la empresa. */
    public int contarDeEmpresa(Dimension d, Integer empresaId, Collection<Long> ids) {
        if (ids.isEmpty()) return 0;
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + d.tabla + " WHERE empresa_id = :empresaId AND id IN (:ids) AND " + d.vigente,
                new MapSqlParameterSource("empresaId", empresaId).addValue("ids", ids), Integer.class);
        return n != null ? n : 0;
    }

    /** frente_id → proyecto_id, para exigir que el frente sea del proyecto de la línea. */
    public Map<Long, Long> proyectoDeFrentes(Collection<Long> frenteIds) {
        Map<Long, Long> out = new HashMap<>();
        if (frenteIds.isEmpty()) return out;
        jdbc.query("SELECT id, proyecto_id FROM proyecto_frente WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", frenteIds),
                rs -> { out.put(rs.getLong("id"), rs.getLong("proyecto_id")); });
        return out;
    }

    /**
     * Serializa la asignación de consecutivos de una serie dentro de la
     * transacción. El siguiente número sale de un MAX()+1: sin este candado dos
     * contabilizaciones simultáneas leen el mismo MAX y la segunda revienta
     * contra el índice único (empresa_id, numero_comprobante).
     */
    public void bloquearSerie(Integer empresaId, String prefijo) {
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_advisory_xact_lock(CAST(:empresaId AS INTEGER), hashtext(:prefijo))",
                new MapSqlParameterSource("empresaId", empresaId).addValue("prefijo", "serie-" + prefijo),
                Integer.class);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
