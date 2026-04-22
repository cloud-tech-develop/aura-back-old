package com.cloud_technological.aura_pos.repositories.productos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.productos.ComponentePosDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoListDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoPosDto;
import com.cloud_technological.aura_pos.dto.productos.ProductoTableDto;
import com.cloud_technological.aura_pos.dto.reglas_descuento.ReglaDescuentoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class ProductoQueryRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<ProductoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";

        StringBuilder sql = new StringBuilder("""
            SELECT
                p.id,
                p.sku,
                p.nombre,
                p.codigo_barras,
                c.nombre AS categoria_nombre,
                m.nombre AS marca_nombre,
                p.tipo_producto,
                p.precio,
                p.costo,
                p.activo,
                p.iva_porcentaje AS ivaPorcentaje,
                COUNT(*) OVER() AS total_rows
            FROM producto p
            LEFT JOIN categoria c ON p.categoria_id = c.id
            LEFT JOIN marca m ON p.marca_id = m.id
            WHERE p.empresa_id = :empresaId
            AND p.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                OR LOWER(p.sku) LIKE :search
                OR LOWER(p.codigo_barras) LIKE :search
                OR LOWER(c.nombre) LIKE :search
                OR LOWER(m.nombre) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY p.id DESC OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ProductoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ProductoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public boolean existeCodigoBarras(String codigoBarras, Integer empresaId) {
        String sql = """
            SELECT COUNT(*) FROM producto
            WHERE codigo_barras = :codigoBarras
            AND empresa_id = :empresaId
            AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("codigoBarras", codigoBarras);
        params.addValue("empresaId", empresaId);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existeCodigoBarrasExcluyendo(String codigoBarras, Integer empresaId, Long id) {
        String sql = """
            SELECT COUNT(*) FROM producto
            WHERE codigo_barras = :codigoBarras
            AND empresa_id = :empresaId
            AND id != :id
            AND deleted_at IS NULL
        """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("codigoBarras", codigoBarras);
        params.addValue("empresaId", empresaId);
        params.addValue("id", id);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }
    public List<ProductoListDto> list(Integer empresaId){
        String sql = """
            SELECT
                p.id,
                p.sku,
                p.nombre,
                p.costo,
                p.precio,
                p.precio_2 AS precio2,
                p.precio_3 AS precio3,
                p.iva_porcentaje,
                p.tipo_producto
            FROM producto p
            WHERE p.empresa_id = :empresaId
              AND p.deleted_at IS NULL
                """;

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoListDto.class));
    }

    public List<ProductoPosDto> listarPos(Integer empresaId, Long sucursalId) {
        List<ProductoPosDto> productos = getProductos(empresaId, sucursalId);
        List<ReglaDescuentoDto> reglas = getReglasVigentes(empresaId);

        // ← NUEVO: cargar todas las composiciones de la empresa en una sola query
        List<ComponentePosDto> todasComposiciones = getComposiciones(empresaId);

        for (ProductoPosDto p : productos) {
            // Descuentos — igual que antes
            ReglaDescuentoDto regla = reglas.stream()
                .filter(r -> aplicaAProducto(r, p))
                .findFirst().orElse(null);

            if (regla != null) {
                BigDecimal descuento = calcularDescuento(p.getPrecio(), regla);
                p.setPrecioFinal(p.getPrecio().subtract(descuento));
                p.setDescuentoNombre(regla.getNombre());
                p.setDescuentoValor(descuento);
            } else {
                p.setPrecioFinal(p.getPrecio());
            }

            // ← NUEVO: asignar componentes
            List<ComponentePosDto> misComponentes = todasComposiciones.stream()
                .filter(c -> c.getProductoPadreId().equals(p.getId()))
                .collect(Collectors.toList());

            p.setComponentes(misComponentes);
            p.setEsCompuesto(!misComponentes.isEmpty());
        }
        return productos;
    }
    private List<ComponentePosDto> getComposiciones(Integer empresaId) {
        String sql = """
            SELECT
                pc.producto_padre_id  AS productoPadreId,
                pc.producto_hijo_id   AS productoHijoId,
                ph.nombre             AS productoHijoNombre,
                pc.cantidad,
                pc.tipo
            FROM producto_composicion pc
            JOIN producto ph ON ph.id = pc.producto_hijo_id
            JOIN producto pp ON pp.id = pc.producto_padre_id
            WHERE pp.empresa_id = :empresaId
            AND pp.deleted_at IS NULL
            AND ph.deleted_at IS NULL
            ORDER BY pc.producto_padre_id, pc.id
            """;

        return jdbcTemplate.query(sql,
            new MapSqlParameterSource("empresaId", empresaId),
            new BeanPropertyRowMapper<>(ComponentePosDto.class));
    }
    private List<ReglaDescuentoDto> getReglasVigentes(Integer empresaId) {
        String sql = """
            SELECT
                r.id,
                r.nombre,
                r.tipo_descuento      AS tipoDescuento,
                r.valor,
                r.producto_id         AS productoId,
                r.categoria_id        AS categoriaId,
                r.hora_inicio         AS horaInicio,
                r.hora_fin            AS horaFin,
                r.dias_semana::text   AS diasSemanaJson
            FROM regla_descuento r
            WHERE r.empresa_id = :empresaId
            AND r.activo = true
            AND (r.fecha_inicio IS NULL OR r.fecha_inicio <= NOW())
            AND (r.fecha_fin    IS NULL OR r.fecha_fin    >= NOW())
            AND (r.hora_inicio  IS NULL OR r.hora_inicio  <= CAST(NOW() AS TIME))
            AND (r.hora_fin     IS NULL OR r.hora_fin     >= CAST(NOW() AS TIME))
            ORDER BY
                r.producto_id NULLS LAST,   -- más específico primero
                r.categoria_id NULLS LAST,
                r.id ASC
            """;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("empresaId", empresaId);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            ReglaDescuentoDto dto = new ReglaDescuentoDto();
            dto.setId(rs.getInt("id"));
            dto.setNombre(rs.getString("nombre"));
            dto.setTipoDescuento(rs.getString("tipoDescuento"));
            dto.setValor(rs.getBigDecimal("valor"));

            // productoId — puede ser null
            long productoId = rs.getLong("productoId");
            dto.setProductoId(rs.wasNull() ? null : productoId);

            // categoriaId — puede ser null
            long categoriaId = rs.getLong("categoriaId");
            dto.setCategoriaId(rs.wasNull() ? null : categoriaId);

            // diasSemana — viene como texto JSON "[1,2,5]", parsear a List<Integer>
            String diasJson = rs.getString("diasSemanaJson");
            if (diasJson != null && !diasJson.isBlank()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    List<Integer> dias = mapper.readValue(
                        diasJson,
                        new TypeReference<List<Integer>>() {}
                    );
                    dto.setDiasSemana(dias);
                } catch (Exception e) {
                    dto.setDiasSemana(Collections.emptyList());
                }
            } else {
                dto.setDiasSemana(Collections.emptyList());
            }

            return dto;
        });
    }
    private boolean aplicaAProducto(ReglaDescuentoDto r, ProductoPosDto p) {
        // Filtrar día de semana (1=Lun ... 7=Dom)
        if (r.getDiasSemana() != null && !r.getDiasSemana().isEmpty()) {
            int hoy = LocalDate.now().getDayOfWeek().getValue(); // 1-7
            if (!r.getDiasSemana().contains(hoy)) return false;
        }
        // Aplica al producto específico
        if (r.getProductoId() != null)
            return r.getProductoId().equals(p.getId());
        // Aplica a la categoría
        if (r.getCategoriaId() != null)
            return r.getCategoriaId().equals(p.getCategoriaId());
        // Aplica a todo
        return true;
    }

    private BigDecimal calcularDescuento(BigDecimal precio, ReglaDescuentoDto r) {
        if ("PORCENTAJE".equals(r.getTipoDescuento()))
            return precio.multiply(r.getValor()).divide(BigDecimal.valueOf(100));
        return r.getValor(); // MONTO fijo
    }
    private List<ProductoPosDto> getProductos(Integer empresaId, Long sucursalId) {
    String sql = """
        -- Producto base (sin presentación)
        SELECT
            p.id,
            p.sku,
            p.codigo_barras      AS codigoBarras,
            p.nombre,
            p.descripcion,
            p.imagen_url         AS imagenUrl,
            p.tipo_producto      AS tipoProducto,
            p.maneja_inventario          AS manejaInventario,
            p.maneja_lotes               AS manejaLotes,
            p.maneja_serial              AS manejaSerial,
            p.permitir_stock_negativo    AS permitirStockNegativo,
            p.precio,
            p.precio_2           AS precio2,
            p.precio_3           AS precio3,
            p.costo,
            p.iva_porcentaje     AS ivaPorcentaje,
            p.iva_incluido       AS ivaIncluido,
            p.visible_en_pos     AS visibleEnPos,
            p.impoconsumo,
            c.id                 AS categoriaId,
            c.nombre             AS categoriaNombre,
            m.id                 AS marcaId,
            m.nombre             AS marcaNombre,
            um.id                AS unidadMedidaId,
            um.nombre            AS unidadMedidaNombre,
            COALESCE(i.stock_actual, 0) AS stockActual,
            p.activo,
            NULL                 AS presentacionId,
            NULL                 AS presentacionNombre,
            NULL                 AS presentacionCodigoBarras,
            NULL                 AS presentacionPrecio,
            NULL                 AS presentacionFactorConversion
        FROM producto p
        LEFT JOIN categoria c      ON p.categoria_id          = c.id
        LEFT JOIN marca m          ON p.marca_id               = m.id
        LEFT JOIN unidad_medida um ON p.unidad_medida_base_id  = um.id
        LEFT JOIN inventario i     ON p.id = i.producto_id AND i.sucursal_id = :sucursalId
        WHERE p.empresa_id   = :empresaId
          AND p.deleted_at   IS NULL
          AND p.visible_en_pos = true
          AND p.activo       = true

        UNION ALL

        -- Una fila por cada presentación activa
        SELECT
            p.id,
            p.sku,
            p.codigo_barras      AS codigoBarras,
            p.nombre,
            p.descripcion,
            p.imagen_url         AS imagenUrl,
            p.tipo_producto      AS tipoProducto,
            p.maneja_inventario          AS manejaInventario,
            p.maneja_lotes               AS manejaLotes,
            p.maneja_serial              AS manejaSerial,
            p.permitir_stock_negativo    AS permitirStockNegativo,
            p.precio,
            p.precio_2           AS precio2,
            p.precio_3           AS precio3,
            p.costo,
            p.iva_porcentaje     AS ivaPorcentaje,
            p.iva_incluido       AS ivaIncluido,
            p.visible_en_pos     AS visibleEnPos,
            p.impoconsumo,
            c.id                 AS categoriaId,
            c.nombre             AS categoriaNombre,
            m.id                 AS marcaId,
            m.nombre             AS marcaNombre,
            um.id                AS unidadMedidaId,
            um.nombre            AS unidadMedidaNombre,
            COALESCE(i.stock_actual, 0) * pres.factor_conversion AS stockActual,
            p.activo,
            pres.id              AS presentacionId,
            pres.nombre          AS presentacionNombre,
            pres.codigo_barras   AS presentacionCodigoBarras,
            pres.precio          AS presentacionPrecio,
            pres.factor_conversion AS presentacionFactorConversion
        FROM producto p
        LEFT JOIN categoria c      ON p.categoria_id          = c.id
        LEFT JOIN marca m          ON p.marca_id               = m.id
        LEFT JOIN unidad_medida um ON p.unidad_medida_base_id  = um.id
        LEFT JOIN inventario i     ON p.id = i.producto_id AND i.sucursal_id = :sucursalId
        JOIN  producto_presentacion pres ON pres.producto_id = p.id AND pres.activo = true
        WHERE p.empresa_id   = :empresaId
          AND p.deleted_at   IS NULL
          AND p.visible_en_pos = true
          AND p.activo       = true

        ORDER BY nombre ASC, presentacionId ASC NULLS FIRST
        """;

    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("empresaId", empresaId);
    params.addValue("sucursalId", sucursalId);

    return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoPosDto.class));
}
}