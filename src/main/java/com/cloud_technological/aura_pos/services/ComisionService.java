package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.comision.ComisionConfigDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionConfigTableDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionLiquidacionDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionLiquidacionTableDto;
import com.cloud_technological.aura_pos.dto.comision.ComisionVentaDto;
import com.cloud_technological.aura_pos.dto.comision.CreateComisionConfigDto;
import com.cloud_technological.aura_pos.dto.comision.CreateLiquidacionDto;
import com.cloud_technological.aura_pos.dto.comision.MarcarPagadaDto;
import com.cloud_technological.aura_pos.dto.comision.TecnicoDto;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface ComisionService {

    // ── Técnicos y vendedores (usuarios de la empresa) ───────
    List<TecnicoDto> listarTecnicos(Integer empresaId);
    List<TecnicoDto> listarVendedores(Integer empresaId);

    // ── Configuración ─────────────────────────────────────────
    PageImpl<ComisionConfigTableDto> listarConfig(PageableDto<Object> pageable, Integer empresaId);
    ComisionConfigDto obtenerConfigPorId(Long id, Integer empresaId);
    ComisionConfigDto crearConfig(CreateComisionConfigDto dto, Integer empresaId);
    ComisionConfigDto actualizarConfig(Long id, CreateComisionConfigDto dto, Integer empresaId);
    void toggleConfig(Long id, Integer empresaId);

    // ── Liquidaciones ─────────────────────────────────────────
    PageImpl<ComisionLiquidacionTableDto> listarLiquidaciones(PageableDto<Object> pageable, Integer empresaId);
    ComisionLiquidacionDto obtenerLiquidacionPorId(Long id, Integer empresaId);
    ComisionLiquidacionDto crearLiquidacion(CreateLiquidacionDto dto, Integer empresaId);
    void marcarPagada(Long id, MarcarPagadaDto dto, Integer empresaId);

    // ── Pendientes ────────────────────────────────────────────
    List<ComisionVentaDto> listarPendientesTecnico(Integer tecnicoId, Integer empresaId, String modalidad, String fechaDesde, String fechaHasta);
    List<ComisionVentaDto> listarPendientesVendedor(Long vendedorId, Integer empresaId, String fechaDesde, String fechaHasta);

    // ── Hook desde VentaService ───────────────────────────────
    void procesarComisionVenta(VentaDetalleEntity detalle, Integer empresaId);
}
