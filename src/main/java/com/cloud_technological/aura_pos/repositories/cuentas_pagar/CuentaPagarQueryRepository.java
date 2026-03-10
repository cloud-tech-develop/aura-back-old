package com.cloud_technological.aura_pos.repositories.cuentas_pagar;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarTableDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Repository
public class CuentaPagarQueryRepository {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public PageImpl<CuentaPagarTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        String orderBy = pageable.getOrder_by() != null ? pageable.getOrder_by() : "id";
        String order = pageable.getOrder() != null ? pageable.getOrder().toUpperCase() : "DESC";
        
        // Mapear campos de ordenamiento
        String orderByColumn = switch (orderBy) {
            case "fecha_emision" -> "cp.fecha_emision";
            case "fecha_vencimiento" -> "cp.fecha_vencimiento";
            case "total_deuda" -> "cp.total_deuda";
            case "total_abonado" -> "cp.total_abonado";
            case "saldo_pendiente" -> "cp.saldo_pendiente";
            case "estado" -> "cp.estado";
            case "numero_cuenta" -> "cp.numero_cuenta";
            case "proveedor_nombre" -> "proveedor_nombre";
            default -> "cp.id";
        };
        
        // Validar orden
        if (!order.equals("ASC") && !order.equals("DESC")) {
            order = "DESC";
        }

        StringBuilder sql = new StringBuilder("""
            SELECT
                cp.id,
                cp.numero_cuenta,
                cp.numero_factura_externo,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos), 'Consumidor Final') AS proveedor_nombre,
                t.numero_documento AS proveedor_documento,
                cp.fecha_emision,
                cp.fecha_vencimiento,
                cp.total_deuda,
                COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) AS total_abonado,
                cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) AS saldo_pendiente,
                CASE 
                    WHEN cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) <= 0 THEN 'pagada'
                    WHEN cp.fecha_vencimiento < NOW() THEN 'vencida'
                    ELSE 'activa'
                END AS estado,
                COUNT(*) OVER() AS total_rows
            FROM cuentas_pagar cp
            INNER JOIN tercero t ON cp.tercero_id = t.id
            WHERE cp.empresa_id = :empresaId
            AND cp.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (!search.isEmpty()) {
            sql.append("""
                AND (LOWER(cp.numero_cuenta) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search
                OR LOWER(t.apellidos) LIKE :search
                OR t.numero_documento LIKE :search
                OR LOWER(cp.estado) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        sql.append(" ORDER BY ").append(orderByColumn).append(" ").append(order).append(" OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<CuentaPagarTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(CuentaPagarTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public PageImpl<CuentaPagarTableDto> listarConFiltros(PageableDto<Object> pageable, Integer empresaId,
            String fechaDesde, String fechaHasta, Long proveedorId, String estado) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        String orderBy = pageable.getOrder_by() != null ? pageable.getOrder_by() : "id";
        String order = pageable.getOrder() != null ? pageable.getOrder().toUpperCase() : "DESC";
        
        // Mapear campos de ordenamiento
        String orderByColumn = switch (orderBy) {
            case "fecha_emision" -> "cp.fecha_emision";
            case "fecha_vencimiento" -> "cp.fecha_vencimiento";
            case "total_deuda" -> "cp.total_deuda";
            case "total_abonado" -> "cp.total_abonado";
            case "saldo_pendiente" -> "cp.saldo_pendiente";
            case "estado" -> "cp.estado";
            case "numero_cuenta" -> "cp.numero_cuenta";
            case "proveedor_nombre" -> "proveedor_nombre";
            default -> "cp.id";
        };
        
        // Validar orden
        if (!order.equals("ASC") && !order.equals("DESC")) {
            order = "DESC";
        }

        StringBuilder sql = new StringBuilder("""
            SELECT
                cp.id,
                cp.numero_cuenta,
                cp.numero_factura_externo,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos), 'Consumidor Final') AS proveedor_nombre,
                t.numero_documento AS proveedor_documento,
                cp.fecha_emision,
                cp.fecha_vencimiento,
                cp.total_deuda,
                COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) AS total_abonado,
                cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) AS saldo_pendiente,
                CASE 
                    WHEN cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) <= 0 THEN 'pagada'
                    WHEN cp.fecha_vencimiento < NOW() THEN 'vencida'
                    ELSE 'activa'
                END AS estado,
                COUNT(*) OVER() AS total_rows
            FROM cuentas_pagar cp
            INNER JOIN tercero t ON cp.tercero_id = t.id
            WHERE cp.empresa_id = :empresaId
            AND cp.deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (search != null && !search.isEmpty()) {
            sql.append("""
                AND (LOWER(cp.numero_cuenta) LIKE :search
                OR LOWER(t.razon_social) LIKE :search
                OR LOWER(t.nombres) LIKE :search
                OR LOWER(t.apellidos) LIKE :search
                OR t.numero_documento LIKE :search
                OR LOWER(cp.estado) LIKE :search)
            """);
            params.addValue("search", "%" + search + "%");
        }

        if (fechaDesde != null && !fechaDesde.isEmpty()) {
            sql.append(" AND cp.fecha_emision >= :fechaDesde");
            params.addValue("fechaDesde", fechaDesde);
        }

        if (fechaHasta != null && !fechaHasta.isEmpty()) {
            sql.append(" AND cp.fecha_emision <= :fechaHasta");
            params.addValue("fechaHasta", fechaHasta);
        }

        if (proveedorId != null) {
            sql.append(" AND cp.tercero_id = :proveedorId");
            params.addValue("proveedorId", proveedorId);
        }

        if (estado != null && !estado.isEmpty()) {
            // Filtrar por estado calculado dinámicamente
            if ("pagada".equals(estado)) {
                sql.append(" AND (cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0)) <= 0");
            } else if ("vencida".equals(estado)) {
                sql.append(" AND cp.fecha_vencimiento < NOW() AND (cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0)) > 0");
            } else if ("activa".equals(estado)) {
                sql.append(" AND (cp.fecha_vencimiento IS NULL OR cp.fecha_vencimiento >= NOW()) AND (cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0)) > 0");
            }
        }

        sql.append(" ORDER BY ").append(orderByColumn).append(" ").append(order).append(" OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size);
        params.addValue("limit", size);

        List<CuentaPagarTableDto> list = jdbcTemplate.query(sql.toString(), params,
                new BeanPropertyRowMapper<>(CuentaPagarTableDto.class));

        long total = list.isEmpty() ? 0 : list.get(0).getTotalRows();
        return new PageImpl<>(list, PageRequest.of(page, size), total);
    }

    public String generarNumeroCuenta() {
        // Primero verificamos si hay cuentas para el día de hoy
        String checkSql = """
            SELECT COUNT(*) FROM cuentas_pagar 
            WHERE numero_cuenta LIKE 'CP-' || TO_CHAR(NOW(), 'YYYYMMDD') || '-%'
        """;
        Integer count = jdbcTemplate.queryForObject(checkSql, new MapSqlParameterSource(), Integer.class);
        
        String siguiente;
        if (count == null || count == 0) {
            siguiente = "0001";
        } else {
            String maxSql = """
                SELECT MAX(SUBSTRING(numero_cuenta FROM 13 FOR 4)) 
                FROM cuentas_pagar 
                WHERE numero_cuenta LIKE 'CP-' || TO_CHAR(NOW(), 'YYYYMMDD') || '-%'
            """;
            String maxNum = jdbcTemplate.queryForObject(maxSql, new MapSqlParameterSource(), String.class);
            int sigNum = Integer.parseInt(maxNum != null ? maxNum : "0") + 1;
            siguiente = String.format("%04d", sigNum);
        }
        
        return "CP-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + siguiente;
    }

    public CuentaPagarResumenDto obtenerResumen(Integer empresaId, String fechaDesde, String fechaHasta, Long proveedorId, String estado) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                COUNT(*) AS total_cuentas,
                COALESCE(SUM(total_deuda), 0) AS total_deuda,
                COALESCE(SUM(total_abonado), 0) AS total_abonado,
                COALESCE(SUM(saldo_pendiente), 0) AS saldo_pendiente,
                COUNT(*) FILTER (WHERE estado = 'activa') AS cantidad_activas,
                COUNT(*) FILTER (WHERE estado = 'pagada') AS cantidad_pagadas,
                COUNT(*) FILTER (WHERE estado = 'vencida') AS cantidad_vencidas
            FROM cuentas_pagar
            WHERE empresa_id = :empresaId
            AND deleted_at IS NULL
        """);

        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);

        if (fechaDesde != null && !fechaDesde.isEmpty()) {
            sql.append(" AND fecha_emision >= :fechaDesde");
            params.addValue("fechaDesde", fechaDesde);
        }

        if (fechaHasta != null && !fechaHasta.isEmpty()) {
            sql.append(" AND fecha_emision <= :fechaHasta");
            params.addValue("fechaHasta", fechaHasta);
        }

        if (proveedorId != null) {
            sql.append(" AND tercero_id = :proveedorId");
            params.addValue("proveedorId", proveedorId);
        }

        if (estado != null && !estado.isEmpty()) {
            sql.append(" AND estado = :estado");
            params.addValue("estado", estado);
        }

        return jdbcTemplate.queryForObject(sql.toString(), params, (rs, rowNum) -> {
            CuentaPagarResumenDto dto = new CuentaPagarResumenDto();
            dto.setTotalCuentas(rs.getLong("total_cuentas"));
            dto.setTotalDeuda(rs.getBigDecimal("total_deuda"));
            dto.setTotalAbonado(rs.getBigDecimal("total_abonado"));
            dto.setSaldoPendiente(rs.getBigDecimal("saldo_pendiente"));
            dto.setCantidadActivas(rs.getLong("cantidad_activas"));
            dto.setCantidadPagadas(rs.getLong("cantidad_pagadas"));
            dto.setCantidadVencidas(rs.getLong("cantidad_vencidas"));
            return dto;
        });
    }

    public List<CuentaPagarTableDto> obtenerVencidas(Integer empresaId) {
        String sql = """
            SELECT
                cp.id,
                cp.numero_cuenta,
                cp.numero_factura_externo,
                COALESCE(NULLIF(t.razon_social, ''), CONCAT(t.nombres, ' ', t.apellidos), 'Consumidor Final') AS proveedor_nombre,
                t.numero_documento AS proveedor_documento,
                cp.fecha_emision,
                cp.fecha_vencimiento,
                cp.total_deuda,
                COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) AS total_abonado,
                cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) AS saldo_pendiente,
                CASE 
                    WHEN cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0) <= 0 THEN 'pagada'
                    WHEN cp.fecha_vencimiento < NOW() THEN 'vencida'
                    ELSE 'activa'
                END AS estado,
                1 AS total_rows
            FROM cuentas_pagar cp
            INNER JOIN tercero t ON cp.tercero_id = t.id
            WHERE cp.empresa_id = :empresaId
            AND cp.deleted_at IS NULL
            AND cp.fecha_vencimiento < NOW()
            AND (cp.total_deuda - COALESCE((SELECT SUM(monto) FROM abonos_pagar WHERE cuenta_pagar_id = cp.id AND deleted_at IS NULL), 0)) > 0
            ORDER BY cp.fecha_vencimiento ASC
        """;
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId);
        return jdbcTemplate.query(sql, params, new BeanPropertyRowMapper<>(CuentaPagarTableDto.class));
    }
}
