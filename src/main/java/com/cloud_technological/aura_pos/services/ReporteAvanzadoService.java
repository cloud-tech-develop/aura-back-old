package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.dto.reportes.ReporteMargenesProductoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteMovimientosCajaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteResumenAvanzadoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteRotacionInventarioDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteTopProductoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteVentasCategoriaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteVentasVendedorDto;

public interface ReporteAvanzadoService {
    List<ReporteVentasCategoriaDto> ventasPorCategoria(Integer empresaId, LocalDate desde, LocalDate hasta);
    List<ReporteTopProductoDto> topProductos(Integer empresaId, LocalDate desde, LocalDate hasta, int limite);
    List<ReporteVentasVendedorDto> ventasPorVendedor(Integer empresaId, LocalDate desde, LocalDate hasta);
    List<ReporteMargenesProductoDto> margenesPorProducto(Integer empresaId, LocalDate desde, LocalDate hasta);
    List<ReporteRotacionInventarioDto> rotacionInventario(Integer empresaId);
    ReporteResumenAvanzadoDto resumenAvanzado(Integer empresaId, LocalDate desde, LocalDate hasta);
    ReporteMovimientosCajaDto resumenMovimientosCaja(Integer empresaId, LocalDate desde, LocalDate hasta);
}
