package com.cloud_technological.aura_pos.repositories.consumo_interno;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.consumo_interno.ConceptoConsumoInternoDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConsumoInternoDetalleDto;
import com.cloud_technological.aura_pos.dto.consumo_interno.ConsumoInternoTableDto;
import com.cloud_technological.aura_pos.dto.productos.ConsumoComponenteDto;
import com.cloud_technological.aura_pos.repositories.inventario_consumo.InventarioConsumoComponenteQueryRepository;
import com.cloud_technological.aura_pos.services.ConsumoComposicionService;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class ConsumoInternoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private InventarioConsumoComponenteQueryRepository consumoQueryRepository;

    public PageImpl<ConsumoInternoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                ci.id,
                s.nombre AS sucursal_nombre,
                c.nombre AS concepto_nombre,
                COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos))) AS responsable_nombre,
                ci.fecha,
                ci.costo_total,
                ci.iva_total,
                ci.estado,
                COUNT(*) OVER() AS total_rows
            FROM consumo_interno ci
            INNER JOIN sucursal s                  ON s.id = ci.sucursal_id
            INNER JOIN concepto_consumo_interno c  ON c.id = ci.concepto_id
            LEFT  JOIN tercero t                   ON t.id = ci.responsable_tercero_id
            WHERE ci.empresa_id = :empresaId
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(s.nombre) LIKE :search
                OR LOWER(c.nombre) LIKE :search
                OR LOWER(ci.estado) LIKE :search
                OR CAST(ci.id AS TEXT) LIKE :search
                OR LOWER(COALESCE(t.razon_social, TRIM(CONCAT(t.nombres, ' ', t.apellidos)))) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY ci.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ConsumoInternoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ConsumoInternoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<ConsumoInternoDetalleDto> obtenerDetalles(Long consumoInternoId) {
        List<ConsumoInternoDetalleDto> detalles = jdbcTemplate.query("""
            SELECT
                d.id,
                d.producto_id,
                p.nombre       AS producto_nombre,
                p.sku          AS producto_sku,
                um.abreviatura AS unidad_abreviatura,
                d.lote_id,
                l.codigo_lote,
                d.cantidad,
                d.costo_unitario,
                d.base_comercial_unitaria,
                d.iva_valor,
                d.producto_presentacion_id,
                pp.nombre AS presentacion_nombre,
                d.cantidad_presentacion
            FROM consumo_interno_detalle d
            INNER JOIN producto p                ON p.id = d.producto_id
            LEFT  JOIN unidad_medida um          ON um.id = p.unidad_medida_base_id
            LEFT  JOIN producto_presentacion pp  ON pp.id = d.producto_presentacion_id
            LEFT  JOIN lote l                    ON l.id = d.lote_id
            WHERE d.consumo_interno_id = :id
            ORDER BY d.id
            """,
            new MapSqlParameterSource("id", consumoInternoId),
            new BeanPropertyRowMapper<>(ConsumoInternoDetalleDto.class));

        Map<Long, List<ConsumoComponenteDto>> componentes = consumoQueryRepository.porDetalle(
                ConsumoComposicionService.ORIGEN_CONSUMO_INTERNO,
                detalles.stream().map(ConsumoInternoDetalleDto::getId).toList());
        detalles.forEach(d -> d.setComponentes(componentes.getOrDefault(d.getId(), List.of())));
        return detalles;
    }

    public List<ConceptoConsumoInternoDto> listarConceptos(Integer empresaId) {
        return jdbcTemplate.query("""
            SELECT c.id, c.nombre, c.cuenta_id, pc.codigo AS cuenta_codigo, pc.nombre AS cuenta_nombre,
                   c.genera_iva, c.activo
            FROM concepto_consumo_interno c
            LEFT JOIN plan_cuenta pc ON pc.id = c.cuenta_id
            WHERE c.empresa_id = :empresaId
            ORDER BY c.activo DESC, c.nombre
            """,
            new MapSqlParameterSource("empresaId", empresaId),
            new BeanPropertyRowMapper<>(ConceptoConsumoInternoDto.class));
    }
}
