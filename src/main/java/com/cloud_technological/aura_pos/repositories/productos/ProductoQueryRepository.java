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
import com.cloud_technological.aura_pos.dto.productos.ProductoInventarioDto;
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

    /**
     * Listado del catálogo. Acepta en {@code params} un {@code uso} (texto
     * "INSUMO", "INSUMO,AMBOS" o lista) para filtrar por uso del producto.
     * Aquí el filtro es estricto: a diferencia del selector de componentes de
     * receta, quien filtra "Insumo" en el catálogo no quiere ver las
     * subrecetas marcadas como VENTA.
     */
    public PageImpl<ProductoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        List<String> usos = usosDeParams(pageable.getParams());

        StringBuilder sql = new StringBuilder("""
            SELECT
                p.id,
                p.sku,
                p.nombre,
                p.codigo_barras,
                c.nombre AS categoria_nombre,
                m.nombre AS marca_nombre,
                p.tipo_producto,
                p.uso_producto,
                p.clasificacion,
                p.precio,
                p.costo,
                p.activo,
                p.iva_porcentaje AS ivaPorcentaje,
                COALESCE(p.iva_incluido, false) AS iva_incluido,
                um.abreviatura AS unidad_abreviatura,
                COALESCE(p.maneja_lotes, false) AS maneja_lotes,
                COALESCE(p.maneja_serial, false) AS maneja_serial,
                COUNT(*) OVER() AS total_rows
            FROM producto p
            LEFT JOIN categoria c ON p.categoria_id = c.id
            LEFT JOIN marca m ON p.marca_id = m.id
            LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
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

        if (!usos.isEmpty()) {
            sql.append(" AND p.uso_producto IN (:usos) ");
            params.addValue("usos", usos);
        }

        List<String> clasificaciones = clasificacionesDeParams(pageable.getParams());
        if (!clasificaciones.isEmpty()) {
            sql.append(" AND p.clasificacion IN (:clasificaciones) ");
            params.addValue("clasificaciones", clasificaciones);
        }

        // Buscador avanzado de productos: categoría, marca y estado.
        if (pageable.getParams() instanceof java.util.Map<?, ?> filtros) {
            Object categoriaId = filtros.get("categoriaId");
            if (categoriaId != null && !categoriaId.toString().isBlank()) {
                sql.append(" AND p.categoria_id = :categoriaId ");
                params.addValue("categoriaId", Long.valueOf(categoriaId.toString()));
            }
            Object marcaId = filtros.get("marcaId");
            if (marcaId != null && !marcaId.toString().isBlank()) {
                sql.append(" AND p.marca_id = :marcaId ");
                params.addValue("marcaId", Long.valueOf(marcaId.toString()));
            }
            Object activo = filtros.get("activo");
            if (activo != null && !activo.toString().isBlank()) {
                sql.append(" AND p.activo = :activo ");
                params.addValue("activo", Boolean.valueOf(activo.toString()));
            }
        }

        // Coincidencia exacta de SKU o código de barras primero (escáner), luego el orden pedido.
        String orden = "p.nombre".equals(pageable.getOrder_by()) ? "p.nombre ASC" : "p.id DESC";
        if (!search.isEmpty()) {
            sql.append(" ORDER BY CASE WHEN LOWER(p.sku) = :exacto OR LOWER(p.codigo_barras) = :exacto THEN 0 ELSE 1 END, ")
               .append(orden);
            params.addValue("exacto", search);
        } else {
            sql.append(" ORDER BY ").append(orden);
        }
        sql.append(" OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<ProductoTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(ProductoTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    /**
     * Lee el filtro de uso que manda el front en {@code params.uso}. Admite un
     * texto ("INSUMO" o "INSUMO,AMBOS") o una lista; ignora valores vacíos.
     */
    private static List<String> usosDeParams(Object params) {
        return listaDeParams(params, "uso");
    }

    /** Filtro {@code params.clasificacion} del listado (V185), mismo formato que el de uso. */
    private static List<String> clasificacionesDeParams(Object params) {
        return listaDeParams(params, "clasificacion");
    }

    private static List<String> listaDeParams(Object params, String clave) {
        if (!(params instanceof java.util.Map<?, ?> paramMap)) {
            return List.of();
        }
        Object uso = paramMap.get(clave);
        if (uso == null) {
            return List.of();
        }
        java.util.stream.Stream<String> crudos = uso instanceof Iterable<?> it
                ? java.util.stream.StreamSupport.stream(it.spliterator(), false).map(String::valueOf)
                : java.util.Arrays.stream(uso.toString().split(","));
        return crudos.map(u -> u.trim().toUpperCase())
                .filter(u -> !u.isEmpty())
                .toList();
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

    /**
     * El POS resuelve el escaneo también contra los códigos de presentación: un
     * producto no puede usar el código de una presentación activa de la empresa.
     */
    public boolean codigoUsadoPorPresentacion(String codigoBarras, Integer empresaId) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM producto_presentacion pp
                JOIN producto p ON p.id = pp.producto_id
                WHERE p.empresa_id = :empresaId
                  AND p.deleted_at IS NULL
                  AND pp.activo = true
                  AND TRIM(pp.codigo_barras) = :codigo
            )
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("codigo", codigoBarras.trim());
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, params, Boolean.class));
    }

    public List<ProductoListDto> list(Integer empresaId){
        return list(empresaId, null, null);
    }

    /**
     * @param usos si viene, restringe por uso del producto. Los productos que
     *             tienen receta propia pasan siempre: una subreceta ("masa
     *             madre") es componente válido aunque la hayan marcado VENTA.
     */
    public List<ProductoListDto> list(Integer empresaId, String search, List<String> usos) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                p.id,
                p.sku,
                p.nombre,
                p.costo,
                p.precio,
                p.precio_2 AS precio2,
                p.precio_3 AS precio3,
                p.iva_porcentaje,
                p.tipo_producto,
                p.uso_producto,
                p.clasificacion,
                p.codigo_barras,
                c.nombre AS categoria_nombre
            FROM producto p
            LEFT JOIN categoria c ON c.id = p.categoria_id
            WHERE p.empresa_id = :empresaId
              AND p.deleted_at IS NULL
            """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        String texto = search != null ? search.trim().toLowerCase() : "";
        if (!texto.isEmpty()) {
            sql.append("""
                AND (LOWER(p.nombre) LIKE :search
                  OR LOWER(p.sku) LIKE :search
                  OR LOWER(p.codigo_barras) LIKE :search)
                """);
            params.addValue("search", "%" + texto + "%");
        }

        if (usos != null && !usos.isEmpty()) {
            sql.append("""
                AND (p.uso_producto IN (:usos)
                  OR EXISTS (SELECT 1 FROM producto_composicion pc WHERE pc.producto_padre_id = p.id))
                """);
            params.addValue("usos", usos.stream().map(u -> u.trim().toUpperCase()).toList());
        }

        sql.append(" ORDER BY p.nombre");
        if (!texto.isEmpty()) {
            sql.append(" LIMIT 50");
        }

        return jdbcTemplate.query(sql.toString(), params, new BeanPropertyRowMapper<>(ProductoListDto.class));
    }

    private static final String SELECT_INVENTARIO = """
        SELECT
            p.id,
            p.nombre,
            p.sku,
            p.codigo_barras,
            COALESCE(i.stock_actual, 0) AS stock_actual,
            p.costo,
            p.precio,
            p.iva_porcentaje,
            p.maneja_lotes,
            COALESCE(p.maneja_serial, false) AS maneja_serial,
            p.maneja_inventario,
            p.permitir_stock_negativo,
            EXISTS (SELECT 1 FROM producto_composicion pc WHERE pc.producto_padre_id = p.id) AS es_compuesto,
            um.abreviatura AS unidad_abreviatura,
            p.uso_producto,
            p.clasificacion
        FROM producto p
        LEFT JOIN unidad_medida um ON um.id = p.unidad_medida_base_id
        LEFT JOIN LATERAL (
            -- El saldo vive por bodega (V172): el stock de la sucursal es la
            -- suma de sus bodegas activas. Un JOIN directo duplicaría el
            -- producto una vez por bodega.
            SELECT SUM(inv.stock_actual) AS stock_actual
              FROM inventario inv
              JOIN bodega b ON b.id = inv.bodega_id
             WHERE inv.producto_id = p.id
               AND inv.sucursal_id = :sucursalId
               AND b.activa = TRUE
        ) i ON TRUE
        WHERE p.empresa_id = :empresaId
          AND p.deleted_at IS NULL
          AND p.activo = true
          -- Merma, obsequio y consumo interno sacan existencias: solo la
          -- mercancía las tiene (V185).
          AND p.clasificacion = 'PRODUCTO'
        """;

    /**
     * Productos para operaciones de inventario (merma, obsequio). No filtra
     * `visible_en_pos` ni uso: un insumo oculto del POS también se daña o se
     * regala. Primero las coincidencias exactas de SKU o código de barras.
     */
    public List<ProductoInventarioDto> buscarInventario(Integer empresaId, Long sucursalId, String search) {
        StringBuilder sql = new StringBuilder(SELECT_INVENTARIO);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("sucursalId", sucursalId);

        String texto = search != null ? search.trim().toLowerCase() : "";
        if (!texto.isEmpty()) {
            sql.append("""
                  AND (LOWER(p.nombre) LIKE :search
                    OR LOWER(p.sku) LIKE :search
                    OR LOWER(p.codigo_barras) LIKE :search)
                ORDER BY CASE WHEN LOWER(p.sku) = :exacto OR LOWER(p.codigo_barras) = :exacto THEN 0 ELSE 1 END,
                         p.nombre
                """);
            params.addValue("search", "%" + texto + "%");
            params.addValue("exacto", texto);
        } else {
            sql.append(" ORDER BY p.nombre ");
        }
        sql.append(" LIMIT 30");

        return jdbcTemplate.query(sql.toString(), params, new BeanPropertyRowMapper<>(ProductoInventarioDto.class));
    }

    /** Coincidencia exacta de SKU (sin distinguir mayúsculas) o de código de barras. Null si no hay. */
    public ProductoInventarioDto buscarInventarioPorId(Integer empresaId, Long sucursalId, Long productoId) {
        List<ProductoInventarioDto> encontrados = jdbcTemplate.query(SELECT_INVENTARIO + " AND p.id = :productoId",
                new MapSqlParameterSource().addValue("empresaId", empresaId)
                        .addValue("sucursalId", sucursalId).addValue("productoId", productoId),
                new BeanPropertyRowMapper<>(ProductoInventarioDto.class));
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    public ProductoInventarioDto buscarPorCodigo(Integer empresaId, Long sucursalId, String codigo) {
        String sql = SELECT_INVENTARIO + """
              AND (LOWER(TRIM(p.sku)) = LOWER(:codigo) OR TRIM(p.codigo_barras) = :codigo)
            ORDER BY CASE WHEN LOWER(TRIM(p.sku)) = LOWER(:codigo) THEN 0 ELSE 1 END, p.id
            LIMIT 1
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("sucursalId", sucursalId)
                .addValue("codigo", codigo);

        List<ProductoInventarioDto> encontrados = jdbcTemplate.query(sql, params,
                new BeanPropertyRowMapper<>(ProductoInventarioDto.class));
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    public List<ProductoPosDto> listarPos(Integer empresaId, Long sucursalId) {
        List<ProductoPosDto> productos = getProductos(empresaId, sucursalId);
        List<ReglaDescuentoDto> reglas = getReglasVigentes(empresaId);

        // ← NUEVO: cargar todas las composiciones de la empresa en una sola query
        List<ComponentePosDto> todasComposiciones = getComposiciones(empresaId);

        // Presentaciones a la venta, agrupadas por producto (una tarjeta por producto)
        java.util.Map<Long, List<com.cloud_technological.aura_pos.dto.productos.PresentacionPosDto>> presentacionesPorProducto =
                getPresentacionesPos(empresaId, sucursalId).stream()
                        .collect(Collectors.groupingBy(
                                com.cloud_technological.aura_pos.dto.productos.PresentacionPosDto::getProductoId));

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
            p.setPresentaciones(presentacionesPorProducto.getOrDefault(p.getId(), List.of()));
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
            um.abreviatura       AS unidadMedidaAbreviatura,
            p.vende_por_unidad   AS vendePorUnidad,
            COALESCE(p.maneja_lotes, false) AS manejaLotes,
            lv.proximo_vencimiento AS proximoVencimiento,
            (lv.proximo_vencimiento - CURRENT_DATE) AS diasParaVencer,
            COALESCE(lv.stock_vencido, 0) AS stockVencido,
            COALESCE(emp.lotes_dias_alerta, 30) AS diasAlertaVencimiento,
            COALESCE(emp.lotes_bloquear_vencidos, true) AS bloquearVencidos,
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
        LEFT JOIN LATERAL (
            -- El saldo vive por bodega (V172): el stock de la sucursal es la
            -- suma de sus bodegas activas. Un JOIN directo duplicaría el
            -- producto una vez por bodega.
            SELECT SUM(inv.stock_actual) AS stock_actual
              FROM inventario inv
              JOIN bodega b ON b.id = inv.bodega_id
             WHERE inv.producto_id = p.id
               AND inv.sucursal_id = :sucursalId
               AND b.activa = TRUE
        ) i ON TRUE
        LEFT JOIN empresa emp      ON emp.id = p.empresa_id
        -- Lotes con stock: el próximo en vencer (para avisar en el carrito) y
        -- cuánto hay ya vencido (la venta no lo saca si la empresa lo bloquea).
        LEFT JOIN LATERAL (
            SELECT MIN(l.fecha_vencimiento) FILTER (WHERE l.fecha_vencimiento >= CURRENT_DATE) AS proximo_vencimiento,
                   SUM(l.stock_actual) FILTER (WHERE l.fecha_vencimiento < CURRENT_DATE)         AS stock_vencido
              FROM lote l
             WHERE p.maneja_lotes = true
               AND l.producto_id = p.id
               AND l.sucursal_id = :sucursalId
               AND COALESCE(l.activo, true)
               AND l.stock_actual > 0
        ) lv ON true
        WHERE p.empresa_id   = :empresaId
          AND p.deleted_at   IS NULL
          AND p.visible_en_pos = true
          AND p.uso_producto <> 'INSUMO'
          -- Un gasto, un activo o un diferido se compran, no se venden (V185).
          AND p.clasificacion IN ('PRODUCTO', 'SERVICIO')
          AND p.activo       = true
          -- Un producto que no se vende por unidad solo aparece si tiene alguna
          -- presentación a la venta (F2: una tarjeta por producto).
          AND (p.vende_por_unidad = true
               OR EXISTS (SELECT 1 FROM producto_presentacion x
                           WHERE x.producto_id = p.id AND x.activo = true AND x.se_vende = true))

        ORDER BY p.nombre ASC
        """;

    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("empresaId", empresaId);
    params.addValue("sucursalId", sucursalId);

    return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(ProductoPosDto.class));
}

    /** Presentaciones a la venta de los productos del POS, con su stock en presentaciones completas. */
    private List<com.cloud_technological.aura_pos.dto.productos.PresentacionPosDto> getPresentacionesPos(
            Integer empresaId, Long sucursalId) {
        String sql = """
            SELECT
                pres.producto_id        AS productoId,
                pres.id,
                pres.nombre,
                pres.codigo_barras      AS codigoBarras,
                pres.precio,
                pres.factor_conversion  AS factorConversion,
                pres.es_default_venta   AS esDefaultVenta,
                -- Cuántas presentaciones completas alcanzan (el factor es lo que contiene cada una)
                CASE WHEN pres.factor_conversion > 0
                     THEN FLOOR(ROUND(COALESCE(i.stock_actual, 0) / pres.factor_conversion, 4))
                     ELSE 0 END         AS stock
            FROM producto_presentacion pres
            JOIN producto p        ON p.id = pres.producto_id
            LEFT JOIN LATERAL (
            -- El saldo vive por bodega (V172): el stock de la sucursal es la
            -- suma de sus bodegas activas. Un JOIN directo duplicaría el
            -- producto una vez por bodega.
            SELECT SUM(inv.stock_actual) AS stock_actual
              FROM inventario inv
              JOIN bodega b ON b.id = inv.bodega_id
             WHERE inv.producto_id = p.id
               AND inv.sucursal_id = :sucursalId
               AND b.activa = TRUE
        ) i ON TRUE
            WHERE p.empresa_id = :empresaId
              AND p.deleted_at IS NULL
              AND p.activo     = true
              AND pres.activo  = true
              AND pres.se_vende = true
            ORDER BY pres.producto_id, pres.factor_conversion, pres.id
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("empresaId", empresaId)
                .addValue("sucursalId", sucursalId);
        return jdbcTemplate.query(sql, params,
                new BeanPropertyRowMapper<>(com.cloud_technological.aura_pos.dto.productos.PresentacionPosDto.class));
    }

    /**
     * Existencias del producto en todas las bodegas. Un producto con stock no
     * puede dejar de ser PRODUCTO: ese inventario quedaría sin quién lo mueva.
     */
    public java.math.BigDecimal stockTotal(Long productoId, Integer empresaId) {
        String sql = """
            SELECT COALESCE(SUM(i.stock_actual), 0)
              FROM inventario i
              JOIN producto p ON p.id = i.producto_id
             WHERE i.producto_id = :productoId
               AND p.empresa_id = :empresaId
            """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("productoId", productoId)
                .addValue("empresaId", empresaId), java.math.BigDecimal.class);
    }
}
