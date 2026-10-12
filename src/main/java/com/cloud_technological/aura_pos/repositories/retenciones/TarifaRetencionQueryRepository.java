package com.cloud_technological.aura_pos.repositories.retenciones;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.retenciones.TarifaRetencionDto;
import com.cloud_technological.aura_pos.utils.PageableDto;
import java.util.Map;

@Repository
public class TarifaRetencionQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * Siguiente auxiliar libre bajo la subcuenta de reteICA (236805xx) y el id
     * de esa subcuenta, para colgarle la cuenta de un municipio nuevo.
     */
    public Map<String, Object> siguienteAuxiliarReteica(Integer empresaId) {
        return jdbc.queryForMap("""
                SELECT (SELECT id FROM plan_cuenta WHERE empresa_id = :e AND codigo = '236805' LIMIT 1) AS padre_id,
                       COALESCE(MAX(CAST(SUBSTRING(codigo FROM 7 FOR 2) AS INTEGER)), 0) + 1 AS siguiente
                  FROM plan_cuenta
                 WHERE empresa_id = :e AND codigo ~ '^236805[0-9]{2}$'
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("e", empresaId));
    }

    private static final String SELECT_TARIFA = """
            SELECT
                t.id, t.empresa_id, t.tipo, t.concepto, t.codigo_concepto,
                t.tarifa_natural, t.tarifa_juridica, t.base_minima, t.activo,
                t.cuenta_contable_id, pc.codigo AS cuenta_codigo, pc.nombre AS cuenta_nombre,
                t.tercero_acreedor_id,
                COALESCE(NULLIF(ta.razon_social, ''), TRIM(CONCAT(ta.nombres, ' ', ta.apellidos)))
                    AS tercero_acreedor_nombre
            FROM tarifa_retencion t
            LEFT JOIN plan_cuenta pc ON pc.id = t.cuenta_contable_id
            LEFT JOIN tercero ta ON ta.id = t.tercero_acreedor_id
            WHERE t.empresa_id = :empresaId
            """;

    public PageImpl<TarifaRetencionDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 25;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder(SELECT_TARIFA);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        String filtro = " AND (LOWER(t.concepto) LIKE :search OR LOWER(t.tipo) LIKE :search) ";
        if (!search.isEmpty()) {
            sql.append(filtro);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY t.tipo ASC, t.concepto ASC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<TarifaRetencionDto> list = jdbc.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(TarifaRetencionDto.class));

        String countSql = "SELECT COUNT(*) FROM tarifa_retencion t WHERE t.empresa_id = :empresaId"
                + (search.isEmpty() ? "" : filtro);
        Long count = jdbc.queryForObject(countSql, params, Long.class);
        long total = count != null ? count : list.size();

        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /** Tarifas activas de la empresa con su cuenta, para los selectores. */
    public List<TarifaRetencionDto> activas(Integer empresaId) {
        return jdbc.query(SELECT_TARIFA + " AND t.activo ORDER BY t.tipo, t.concepto",
                new MapSqlParameterSource("empresaId", empresaId),
                new BeanPropertyRowMapper<>(TarifaRetencionDto.class));
    }

    /** Tipo (RETEFUENTE | RETEIVA | RETEICA) y cuenta de una tarifa de la empresa. */
    public record TipoYCuenta(String tipo, Long cuentaContableId) {
    }

    public Optional<TipoYCuenta> tipoYCuenta(Long tarifaId, Integer empresaId) {
        return jdbc.query("""
                SELECT tipo, cuenta_contable_id FROM tarifa_retencion
                WHERE id = :id AND empresa_id = :empresaId
                """,
                new MapSqlParameterSource("id", tarifaId).addValue("empresaId", empresaId),
                (rs, i) -> new TipoYCuenta(rs.getString("tipo"), rs.getObject("cuenta_contable_id", Long.class)))
                .stream().findFirst();
    }

    /**
     * Catálogo inicial: tipo, concepto, tarifa natural/jurídica y las cuentas de
     * su concepto de la más específica a la más general. Es el mismo de la
     * migración V203 (empresas existentes); este sirve a las empresas nuevas.
     */
    private static final String CATALOGO = """
            (VALUES
              ('RETEFUENTE', 'Compras generales (declarantes)',          2.5,  2.5, ARRAY['23654001','236540','2365']),
              ('RETEFUENTE', 'Compras generales (no declarantes)',       3.5,  3.5, ARRAY['23654001','236540','2365']),
              ('RETEFUENTE', 'Servicios generales (declarantes)',        4.0,  4.0, ARRAY['23652501','236525','2365']),
              ('RETEFUENTE', 'Servicios generales (no declarantes)',     6.0,  6.0, ARRAY['23652502','236525','2365']),
              ('RETEFUENTE', 'Honorarios',                              11.0, 11.0, ARRAY['23651502','236515','2365']),
              ('RETEFUENTE', 'Honorarios (no declarantes)',             10.0, 10.0, ARRAY['23651501','236515','2365']),
              ('RETEFUENTE', 'Comisiones',                              11.0, 11.0, ARRAY['236520','23651502','2365']),
              ('RETEFUENTE', 'Comisiones (no declarantes)',             10.0, 10.0, ARRAY['236520','23651501','2365']),
              ('RETEFUENTE', 'Arrendamiento de bienes inmuebles',        3.5,  3.5, ARRAY['23653001','236530','2365']),
              ('RETEFUENTE', 'Arrendamiento de bienes muebles',          4.0,  4.0, ARRAY['23653001','236530','2365']),
              ('RETEFUENTE', 'Transporte de carga',                      1.0,  1.0, ARRAY['23652501','236525','2365']),
              ('RETEFUENTE', 'Transporte de pasajeros',                  3.5,  3.5, ARRAY['23652501','236525','2365']),
              ('RETEFUENTE', 'Servicios temporales (sobre AIU)',         1.0,  1.0, ARRAY['23652501','236525','2365']),
              ('RETEFUENTE', 'Aseo y vigilancia (sobre AIU)',            2.0,  2.0, ARRAY['23652501','236525','2365']),
              ('RETEFUENTE', 'Hoteles y restaurantes',                   3.5,  3.5, ARRAY['23652501','236525','2365']),
              ('RETEFUENTE', 'Contratos de construcción',                2.0,  2.0, ARRAY['23652501','236525','2365']),
              ('RETEIVA',    'ReteIVA general (15% del IVA)',           15.0, 15.0, ARRAY['23670501','236705','2367']),
              ('RETEICA',    'ReteICA actividades comerciales',          1.0,  1.0, ARRAY['23680501','236805','2368']),
              ('RETEICA',    'ReteICA actividades de servicios',         1.0,  1.0, ARRAY['23680501','236805','2368']),
              ('RETEICA',    'ReteICA actividades industriales',         0.7,  0.7, ARRAY['23680501','236805','2368'])
            ) AS c(tipo, concepto, pct_natural, pct_juridica, cuentas)
            """;

    /** La primera auxiliar activa de la empresa entre las cuentas del concepto. */
    private static final String CUENTA_DEL_CONCEPTO = """
            (SELECT pc.id
               FROM plan_cuenta pc
              WHERE pc.empresa_id = :empresaId
                AND pc.codigo = ANY (c.cuentas)
                AND pc.auxiliar AND pc.activa
              ORDER BY array_position(c.cuentas, pc.codigo::text)
              LIMIT 1)
            """;

    /** Siembra el catálogo inicial con la cuenta de cada concepto. No toca las tarifas que ya existan. */
    public int sembrarCatalogo(Integer empresaId) {
        return jdbc.update("""
                INSERT INTO tarifa_retencion (empresa_id, tipo, concepto, tarifa_natural, tarifa_juridica,
                                              base_minima, cuenta_contable_id, activo, created_at)
                SELECT :empresaId, c.tipo, c.concepto, c.pct_natural, c.pct_juridica, 0,
                """ + CUENTA_DEL_CONCEPTO + """
                       , true, now()
                  FROM """ + CATALOGO + """
                ON CONFLICT (empresa_id, tipo, concepto) DO NOTHING
                """, new MapSqlParameterSource("empresaId", empresaId));
    }

    /**
     * Las tarifas del catálogo sembradas antes de que la empresa tuviera PUC
     * quedaron sin cuenta: al cargar el PUC se les pone la de su concepto. Solo
     * llena las vacías del catálogo; nunca cambia una cuenta elegida ni toca las
     * tarifas que creó el usuario.
     */
    public int completarCuentasCatalogo(Integer empresaId) {
        return jdbc.update("""
                UPDATE tarifa_retencion t
                   SET cuenta_contable_id = """ + CUENTA_DEL_CONCEPTO + """
                  FROM """ + CATALOGO + """
                 WHERE t.empresa_id = :empresaId
                   AND t.cuenta_contable_id IS NULL
                   AND t.tipo = c.tipo AND t.concepto = c.concepto
                """, new MapSqlParameterSource("empresaId", empresaId));
    }
}
