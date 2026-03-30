package com.cloud_technological.aura_pos.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.cartera.CarteraDashboardDto;
import com.cloud_technological.aura_pos.dto.cartera.ClienteCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.CreateGestionCobroDto;
import com.cloud_technological.aura_pos.dto.cartera.CreateTerceroCreditoDto;
import com.cloud_technological.aura_pos.dto.cartera.CuentaVencidaAlertaDto;
import com.cloud_technological.aura_pos.dto.cartera.EdadCarteraDto;
import com.cloud_technological.aura_pos.dto.cartera.ValidacionCreditoDto;
import com.cloud_technological.aura_pos.entity.TerceroCreditoEntity;

public interface CarteraService {

    // Dashboard y analítica
    CarteraDashboardDto dashboard(Integer empresaId);
    List<EdadCarteraDto> edadesCartera(Integer empresaId);
    List<CuentaVencidaAlertaDto> alertasVencidas(Integer empresaId, int limit);
    PageImpl<ClienteCarteraDto> listarClientes(Integer empresaId, int page, int rows, String search);

    // Cupo de crédito
    TerceroCreditoEntity abrirCredito(CreateTerceroCreditoDto dto, Integer empresaId, Long usuarioId);
    TerceroCreditoEntity actualizarCredito(Long id, CreateTerceroCreditoDto dto, Integer empresaId, Long usuarioId);
    TerceroCreditoEntity obtenerCreditoTercero(Long terceroId, Integer empresaId);

    // Validación POS
    ValidacionCreditoDto validarVenta(Long terceroId, BigDecimal monto, Integer empresaId);

    // Motor de score y cupo automático (se llama al registrar pagos)
    void recalcularScore(Long terceroId, Integer empresaId);
    void evaluarReglasAutomaticas(Long terceroId, Integer empresaId, String evento);

    // Gestión de cobros
    void registrarGestion(CreateGestionCobroDto dto, Integer empresaId, Long usuarioId);

    // Solicitudes de autorización
    void aprobarSolicitud(Long solicitudId, Integer empresaId, Long usuarioId);
    void rechazarSolicitud(Long solicitudId, String motivo, Integer empresaId, Long usuarioId);
}
