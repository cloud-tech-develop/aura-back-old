package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.reportes.ReporteMargenesProductoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteMovimientosCajaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteResumenAvanzadoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteRotacionInventarioDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteTopProductoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteVentasCategoriaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteVentasVendedorDto;
import com.cloud_technological.aura_pos.repositories.reportes.ReporteAvanzadoQueryRepository;
import com.cloud_technological.aura_pos.services.ReporteAvanzadoService;

@Service
public class ReporteAvanzadoServiceImpl implements ReporteAvanzadoService {

    @Autowired
    private ReporteAvanzadoQueryRepository repository;

    @Override
    public List<ReporteVentasCategoriaDto> ventasPorCategoria(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        return repository.ventasPorCategoria(empresaId, desde, hasta);
    }

    @Override
    public List<ReporteTopProductoDto> topProductos(Integer empresaId,
            LocalDate desde, LocalDate hasta, int limite) {
        return repository.topProductos(empresaId, desde, hasta, limite);
    }

    @Override
    public List<ReporteVentasVendedorDto> ventasPorVendedor(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        return repository.ventasPorVendedor(empresaId, desde, hasta);
    }

    @Override
    public List<ReporteMargenesProductoDto> margenesPorProducto(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        return repository.margenesPorProducto(empresaId, desde, hasta);
    }

    @Override
    public List<ReporteRotacionInventarioDto> rotacionInventario(Integer empresaId) {
        return repository.rotacionInventario(empresaId);
    }

    @Override
    public ReporteResumenAvanzadoDto resumenAvanzado(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        return repository.resumenAvanzado(empresaId, desde, hasta);
    }

    @Override
    public ReporteMovimientosCajaDto resumenMovimientosCaja(Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        return repository.resumenMovimientosCaja(empresaId, desde, hasta);
    }
}
