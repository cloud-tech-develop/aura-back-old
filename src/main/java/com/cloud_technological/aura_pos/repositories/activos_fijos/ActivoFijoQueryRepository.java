package com.cloud_technological.aura_pos.repositories.activos_fijos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.activos_fijos.ActivoFijoTableDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.AdicionActivoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.DepreciacionPeriodoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.MantenimientoActivoDto;
import com.cloud_technological.aura_pos.dto.activos_fijos.ReporteActivosDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ActivoFijoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /** Nombre del tercero: razón social o nombres y apellidos. */
    private static final String NOMBRE_TERCERO = """
            COALESCE(NULLIF(TRIM(COALESCE(%1$s.razon_social, '')), ''),
                     TRIM(CONCAT_WS(' ', %1$s.nombres, %1$s.apellidos)))""";

    public PageImpl<ActivoFijoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                af.id,
                af.codigo,
                af.descripcion,
                af.categoria,
                af.fecha_adquisicion,
                af.valor_compra,
                af.valor_adiciones,
                af.depreciacion_acumulada,
                (af.valor_compra + af.valor_adiciones - af.depreciacion_acumulada) AS valor_en_libros,
                af.metodo_depreciacion,
                af.vida_util_meses,
                af.estado,
                af.placa,
                af.compra_id,
                COALESCE(%s, af.responsable) AS responsable,
                cc.nombre AS centro_costo_nombre,
                COUNT(*) OVER() AS total_rows
            FROM activo_fijo af
            LEFT JOIN tercero rt ON rt.id = af.responsable_tercero_id
            LEFT JOIN centros_costos cc ON cc.id = af.centro_costo_id
            WHERE af.empresa_id = :empresaId
              AND af.deleted_at IS NULL
        """.formatted(NOMBRE_TERCERO.formatted("rt")));

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                 AND (LOWER(af.codigo) LIKE :search OR LOWER(af.descripcion) LIKE :search
                      OR LOWER(COALESCE(af.placa, '')) LIKE :search OR LOWER(COALESCE(af.serial, '')) LIKE :search)
                """);
            params.addValue("search", "%" + search + "%");
        }
        if (pageable.getParams() instanceof Map<?, ?> filtros) {
            Object estado = filtros.get("estado");
            if (estado != null && !estado.toString().isBlank()) {
                sql.append(" AND af.estado = :estado ");
                params.addValue("estado", estado.toString());
            }
            Object categoria = filtros.get("categoria");
            if (categoria != null && !categoria.toString().isBlank()) {
                sql.append(" AND af.categoria = :categoria ");
                params.addValue("categoria", categoria.toString());
            }
        }

        sql.append(" ORDER BY af.codigo ASC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ActivoFijoTableDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ActivoFijoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /** Cuotas ya registradas del activo: la base del cálculo prospectivo. */
    public int mesesDepreciados(Long activoId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM depreciacion_periodo WHERE activo_id = :id",
                new MapSqlParameterSource("id", activoId), Integer.class);
        return n != null ? n : 0;
    }

    public boolean depreciadoEnPeriodo(Long activoId, Long periodoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM depreciacion_periodo WHERE activo_id = :a AND periodo_id = :p)
            """, new MapSqlParameterSource().addValue("a", activoId).addValue("p", periodoId), Boolean.class));
    }

    /** Ids de las depreciaciones del período, para reversarlas. */
    public List<Long> depreciacionesDelPeriodo(Integer empresaId, Long periodoId) {
        return jdbc.queryForList("""
            SELECT id FROM depreciacion_periodo WHERE empresa_id = :e AND periodo_id = :p ORDER BY id
            """, new MapSqlParameterSource().addValue("e", empresaId).addValue("p", periodoId), Long.class);
    }

    /** Depreciaciones hechas después de este período: reversar uno intermedio desordena la historia. */
    public boolean hayDepreciacionPosterior(Integer empresaId, Long periodoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM depreciacion_periodo d
                  JOIN periodo_contable p  ON p.id = d.periodo_id
                  JOIN periodo_contable pr ON pr.id = :p
                 WHERE d.empresa_id = :e
                   AND (p.anio * 100 + p.mes) > (pr.anio * 100 + pr.mes))
            """, new MapSqlParameterSource().addValue("e", empresaId).addValue("p", periodoId), Boolean.class));
    }

    public List<DepreciacionPeriodoDto> historial(Long activoId) {
        return jdbc.query("""
            SELECT d.id, d.activo_id, d.periodo_id, d.valor, d.asiento_id, d.calculado_en,
                   d.metodo, d.unidades,
                   p.anio || '-' || LPAD(p.mes::text, 2, '0') AS periodo
              FROM depreciacion_periodo d
              JOIN periodo_contable p ON p.id = d.periodo_id
             WHERE d.activo_id = :id
             ORDER BY p.anio DESC, p.mes DESC
            """, new MapSqlParameterSource("id", activoId), new BeanPropertyRowMapper<>(DepreciacionPeriodoDto.class));
    }

    public List<MantenimientoActivoDto> mantenimientos(Long activoId) {
        return jdbc.query("""
            SELECT m.id, m.activo_id, m.fecha, m.tipo, m.descripcion, m.costo, m.tercero_id, m.proximo,
                   %s AS tercero_nombre
              FROM activo_fijo_mantenimiento m
              LEFT JOIN tercero t ON t.id = m.tercero_id
             WHERE m.activo_id = :id
             ORDER BY m.fecha DESC, m.id DESC
            """.formatted(NOMBRE_TERCERO.formatted("t")),
                new MapSqlParameterSource("id", activoId), new BeanPropertyRowMapper<>(MantenimientoActivoDto.class));
    }

    public List<AdicionActivoDto> adiciones(Long activoId) {
        return jdbc.query("""
            SELECT a.id, a.activo_id, a.fecha, a.descripcion, a.valor, a.meses_adicionales,
                   a.cuenta_contrapartida_id, a.tercero_id, a.asiento_id,
                   pc.codigo || ' - ' || pc.nombre AS cuenta_contrapartida
              FROM activo_fijo_adicion a
              LEFT JOIN plan_cuenta pc ON pc.id = a.cuenta_contrapartida_id
             WHERE a.activo_id = :id
             ORDER BY a.fecha DESC, a.id DESC
            """, new MapSqlParameterSource("id", activoId), new BeanPropertyRowMapper<>(AdicionActivoDto.class));
    }

    /** Suma de los meses que alargaron la vida útil las adiciones (la vida de la ficha ya los incluye). */
    public String nombreTercero(Long terceroId) {
        if (terceroId == null) return null;
        List<String> r = jdbc.queryForList("SELECT %s FROM tercero t WHERE t.id = :id"
                .formatted(NOMBRE_TERCERO.formatted("t")), new MapSqlParameterSource("id", terceroId), String.class);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Hijos vivos del activo (componentes): no se da de baja el padre con hijos activos. */
    public long hijosActivos(Long activoId) {
        Long n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM activo_fijo
             WHERE activo_padre_id = :id AND deleted_at IS NULL AND estado IN ('ACTIVO', 'DEPRECIADO')
            """, new MapSqlParameterSource("id", activoId), Long.class);
        return n != null ? n : 0;
    }

    /**
     * Informe de activos vivos o retirados, con la depreciación del mes de
     * corte (si ya se calculó) y totales.
     */
    public List<ReporteActivosDto.Fila> reporte(Integer empresaId, String estado, String categoria,
            Long centroCostoId, Long periodoId) {
        StringBuilder sql = new StringBuilder("""
            SELECT af.id, af.codigo, af.descripcion, af.categoria, af.placa, af.estado,
                   af.fecha_adquisicion, af.vida_util_meses,
                   COALESCE(cc.nombre, 'Sin centro de costo') AS centro_costo,
                   COALESCE(%s, NULLIF(af.responsable, ''), 'Sin responsable') AS responsable,
                   (af.valor_compra + af.valor_adiciones) AS costo,
                   af.depreciacion_acumulada,
                   (af.valor_compra + af.valor_adiciones - af.depreciacion_acumulada) AS valor_en_libros,
                   COALESCE(dm.valor, 0) AS depreciacion_mes
              FROM activo_fijo af
              LEFT JOIN centros_costos cc ON cc.id = af.centro_costo_id
              LEFT JOIN tercero rt ON rt.id = af.responsable_tercero_id
              LEFT JOIN depreciacion_periodo dm ON dm.activo_id = af.id AND dm.periodo_id = :periodoId
             WHERE af.empresa_id = :empresaId AND af.deleted_at IS NULL
            """.formatted(NOMBRE_TERCERO.formatted("rt")));
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("periodoId", periodoId != null ? periodoId : -1L);
        if (estado != null && !estado.isBlank()) {
            if ("VIGENTES".equals(estado)) {
                sql.append(" AND af.estado IN ('ACTIVO', 'DEPRECIADO') ");
            } else {
                sql.append(" AND af.estado = :estado ");
                params.addValue("estado", estado);
            }
        }
        if (categoria != null && !categoria.isBlank()) {
            sql.append(" AND af.categoria = :categoria ");
            params.addValue("categoria", categoria);
        }
        if (centroCostoId != null) {
            sql.append(" AND af.centro_costo_id = :cc ");
            params.addValue("cc", centroCostoId);
        }
        sql.append(" ORDER BY af.codigo ");
        return jdbc.query(sql.toString(), params, new BeanPropertyRowMapper<>(ReporteActivosDto.Fila.class));
    }

    /** Valor en libros total por cuenta del activo, para cuadrarlo contra el mayor. */
    public BigDecimal costoVigentePorCuenta(Integer empresaId, Long cuentaActivoId) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(valor_compra + valor_adiciones), 0) FROM activo_fijo
             WHERE empresa_id = :e AND cuenta_activo_id = :c AND deleted_at IS NULL
               AND estado IN ('ACTIVO', 'DEPRECIADO')
            """, new MapSqlParameterSource().addValue("e", empresaId).addValue("c", cuentaActivoId), BigDecimal.class);
    }
}
