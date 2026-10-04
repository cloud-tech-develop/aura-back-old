package com.cloud_technological.aura_pos.repositories.merma;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.merma.MermaDetalleDto;
import com.cloud_technological.aura_pos.dto.merma.MermaTableDto;
import com.cloud_technological.aura_pos.dto.productos.ConsumoComponenteDto;
import com.cloud_technological.aura_pos.repositories.inventario_consumo.InventarioConsumoComponenteQueryRepository;
import com.cloud_technological.aura_pos.services.ConsumoComposicionService;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class MermaQueryRepository {
    /** Solo sus sedes, si el perfil no tiene todas (PLAN_PERMISOS P9). */
    @org.springframework.beans.factory.annotation.Autowired
    private com.cloud_technological.aura_pos.services.permisos.AlcanceSede alcanceSede;


    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private InventarioConsumoComponenteQueryRepository consumoQueryRepository;

    public PageImpl<MermaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                m.id,
                s.nombre AS sucursal_nombre,
                mm.nombre AS motivo_nombre,
                m.fecha,
                m.costo_total,
                m.estado,
                COUNT(*) OVER() AS total_rows
            FROM merma m
            INNER JOIN sucursal s ON m.sucursal_id = s.id
            INNER JOIN motivo_merma mm ON m.motivo_id = mm.id
            WHERE m.empresa_id = :empresaId /*SEDE:m.sucursal_id*/
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(s.nombre) LIKE :search
                OR LOWER(mm.nombre) LIKE :search
                OR LOWER(m.estado) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY m.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<MermaTableDto> list = jdbcTemplate.query(alcanceSede.aplicar(sql.toString(), params), params,
                new BeanPropertyRowMapper<>(MermaTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public List<MermaDetalleDto> obtenerDetalles(Long mermaId) {
        String sql = """
            SELECT
                md.id,
                md.producto_id,
                p.nombre AS producto_nombre,
                p.sku    AS producto_sku,
                md.lote_id,
                l.codigo_lote,
                md.cantidad,
                md.costo_unitario,
                ROUND(md.cantidad * md.costo_unitario, 2) AS subtotal_costo,
                md.producto_presentacion_id,
                pp.nombre AS presentacion_nombre,
                md.cantidad_presentacion
            FROM merma_detalle md
            INNER JOIN producto p ON md.producto_id = p.id
            LEFT JOIN producto_presentacion pp ON pp.id = md.producto_presentacion_id
            LEFT JOIN lote l ON md.lote_id = l.id
            WHERE md.merma_id = :mermaId
            ORDER BY md.id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("mermaId", mermaId);
        List<MermaDetalleDto> detalles = jdbcTemplate.query(alcanceSede.aplicar(sql, params), params,
                new BeanPropertyRowMapper<>(MermaDetalleDto.class));

        Map<Long, List<ConsumoComponenteDto>> componentes = consumoQueryRepository.porDetalle(
                ConsumoComposicionService.ORIGEN_MERMA,
                detalles.stream().map(MermaDetalleDto::getId).toList());
        detalles.forEach(d -> d.setComponentes(componentes.getOrDefault(d.getId(), List.of())));
        return detalles;
    }
}
