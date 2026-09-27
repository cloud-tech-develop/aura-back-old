package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.inventario.VencimientoLoteDto;
import com.cloud_technological.aura_pos.dto.notificacion.NotificacionDto;
import com.cloud_technological.aura_pos.repositories.dashboard.DashboardQueryRepository;
import com.cloud_technological.aura_pos.repositories.inventario.LoteQueryRepository;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * La campana del topbar. Avisa de inventario (lotes vencidos, por vencer y
 * stock bajo) y de cartera (facturas vencidas y por vencer, acuerdos de pago,
 * promesas y autorizaciones). Se calcula al pedirla; no hay tabla de
 * notificaciones ni estado de leído.
 */
@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    @Autowired
    private LoteQueryRepository loteQueryRepository;

    @Autowired
    private DashboardQueryRepository dashboardQueryRepository;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.cartera.AgendaCobroQueryRepository agendaCobroRepository;

    @Autowired
    private com.cloud_technological.aura_pos.services.implementations.PromesaPagoService promesaPagoService;

    @Autowired
    private com.cloud_technological.aura_pos.services.implementations.SolicitudCreditoService solicitudCreditoService;

    @Autowired
    private com.cloud_technological.aura_pos.services.implementations.AcuerdoPagoService acuerdoPagoService;

    @Autowired
    private com.cloud_technological.aura_pos.repositories.cartera.AcuerdoPagoQueryRepository acuerdoPagoQueryRepository;

    /** Una factura o cuota que vence dentro de estos días ya aparece en la campana. */
    private static final int DIAS_AVISO_VENCIMIENTO = 3;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificacionDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<NotificacionDto> notificaciones = new ArrayList<>();

        List<VencimientoLoteDto> vencimientos = loteQueryRepository.vencimientos(empresaId, null, null);
        List<VencimientoLoteDto> vencidos = vencimientos.stream()
                .filter(v -> v.getDiasParaVencer() != null && v.getDiasParaVencer() < 0).toList();
        List<VencimientoLoteDto> porVencer = vencimientos.stream()
                .filter(v -> v.getDiasParaVencer() != null && v.getDiasParaVencer() >= 0).toList();

        if (!vencidos.isEmpty()) {
            notificaciones.add(new NotificacionDto("LOTES_VENCIDOS", "danger",
                    vencidos.size() == 1 ? "1 lote vencido" : vencidos.size() + " lotes vencidos",
                    "Hay mercancía vencida en inventario: dala de baja para que no se venda ni descuadre el stock.",
                    vencidos.size(), sumarCosto(vencidos), "/inventario/lotes", "Dar de baja ahora"));
        }
        if (!porVencer.isEmpty()) {
            notificaciones.add(new NotificacionDto("LOTES_POR_VENCER", "warn",
                    porVencer.size() == 1 ? "1 lote por vencer" : porVencer.size() + " lotes por vencer",
                    "Véndelos primero o trasládalos antes de que venzan.",
                    porVencer.size(), sumarCosto(porVencer), "/inventario/lotes", "Ver vencimientos"));
        }
        int stockBajo = dashboardQueryRepository.stockBajo(empresaId).size();
        if (stockBajo > 0) {
            notificaciones.add(new NotificacionDto("STOCK_BAJO", "info",
                    stockBajo == 1 ? "1 producto con stock bajo" : stockBajo + " productos con stock bajo",
                    "Están por debajo de su stock mínimo.",
                    stockBajo, null, "/inventario/stock", "Revisar inventario"));
        }
        agregarCartera(empresaId, notificaciones);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "", false, notificaciones), HttpStatus.OK);
    }

    /**
     * Solo quien ve la cartera: autorizaciones pendientes, facturas vencidas y por
     * vencer, acuerdos de pago incumplidos y cuotas por vencer, y promesas.
     */
    private void agregarCartera(Integer empresaId, List<NotificacionDto> notificaciones) {
        String rol = securityUtils.getRol();
        if (rol == null || !(rol.equalsIgnoreCase("ADMIN") || rol.equalsIgnoreCase("SUPER_ADMIN"))) return;
        try {
            int solicitudes = solicitudCreditoService.pendientes(empresaId);
            if (solicitudes > 0) {
                notificaciones.add(0, new NotificacionDto("SOLICITUDES_CREDITO", "danger",
                        solicitudes == 1 ? "1 autorización de crédito pendiente" : solicitudes + " autorizaciones de crédito pendientes",
                        "Un cajero está esperando para cerrar una venta que pasa el cupo del cliente.",
                        solicitudes, null, "/cartera", "Responder"));
            }
            promesaPagoService.evaluar(empresaId, null);
            // Cada bloque por su lado: si uno falla, los demás siguen avisando.
            try {
                agregarVencimientos(empresaId, notificaciones);
            } catch (Exception e) {
                log.warn("Campana: no se pudieron leer los vencimientos de cartera: {}", e.getMessage());
            }
            try {
                agregarAcuerdos(empresaId, notificaciones);
            } catch (Exception e) {
                log.warn("Campana: no se pudieron evaluar los acuerdos de pago: {}", e.getMessage());
            }
            var incumplidas = agendaCobroRepository.promesasIncumplidas(empresaId);
            if (!incumplidas.isEmpty()) {
                BigDecimal monto = incumplidas.stream()
                        .map(p -> p.getMontoPrometido().subtract(p.getMontoPagado()).max(BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                notificaciones.add(new NotificacionDto("PROMESAS_INCUMPLIDAS", "warn",
                        incumplidas.size() == 1 ? "1 promesa de pago incumplida" : incumplidas.size() + " promesas de pago incumplidas",
                        "Los clientes no pagaron lo que prometieron y nadie los ha vuelto a contactar.",
                        incumplidas.size(), monto, "/cartera", "Cobrar ahora"));
            }
            var hoy = agendaCobroRepository.promesasHoy(empresaId);
            if (!hoy.isEmpty()) {
                BigDecimal monto = hoy.stream()
                        .map(p -> p.getMontoPrometido().subtract(p.getMontoPagado()).max(BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                notificaciones.add(new NotificacionDto("PROMESAS_HOY", "info",
                        hoy.size() == 1 ? "1 cliente prometió pagar hoy" : hoy.size() + " clientes prometieron pagar hoy",
                        "Confirma el pago o recuérdales antes de que cierre el día.",
                        hoy.size(), monto, "/cartera", "Ver agenda"));
            }
        } catch (Exception e) {
            // La campana no se cae por cartera: el inventario sigue avisando.
        }
    }

    /** Facturas fuera de acuerdos: las vencidas (danger) y las que vencen en los próximos días (warn). */
    private void agregarVencimientos(Integer empresaId, List<NotificacionDto> notificaciones) {
        var r = agendaCobroRepository.resumenVencimientos(empresaId, DIAS_AVISO_VENCIMIENTO);
        int vencidas = entero(r.get("vencidas"));
        if (vencidas > 0) {
            int clientes = entero(r.get("clientes_vencidas"));
            notificaciones.add(new NotificacionDto("FACTURAS_VENCIDAS", "danger",
                    vencidas == 1 ? "1 factura vencida" : vencidas + " facturas vencidas",
                    (clientes == 1 ? "Un cliente tiene" : clientes + " clientes tienen")
                            + " saldo vencido sin pagar. Gestiona el cobro antes de que envejezca.",
                    vencidas, (BigDecimal) r.get("valor_vencidas"), "/cartera", "Ver vencidas"));
        }
        int porVencer = entero(r.get("por_vencer"));
        if (porVencer > 0) {
            int hoy = entero(r.get("vencen_hoy"));
            String mensaje = hoy > 0
                    ? (hoy == 1 ? "1 vence hoy" : hoy + " vencen hoy") + "; el resto en los próximos " + DIAS_AVISO_VENCIMIENTO + " días."
                    : "Vencen en los próximos " + DIAS_AVISO_VENCIMIENTO + " días: recuérdales a los clientes.";
            notificaciones.add(new NotificacionDto("FACTURAS_POR_VENCER", "warn",
                    porVencer == 1 ? "1 factura por vencer" : porVencer + " facturas por vencer",
                    mensaje, porVencer, (BigDecimal) r.get("valor_por_vencer"), "/cartera", "Ver agenda"));
        }
    }

    private void agregarAcuerdos(Integer empresaId, List<NotificacionDto> notificaciones) {
        acuerdoPagoService.evaluar(empresaId, null, null);
        var incumplidos = acuerdoPagoQueryRepository.resumenIncumplidos(empresaId);
        int n = entero(incumplidos.get("cantidad"));
        if (n > 0) {
            notificaciones.add(new NotificacionDto("ACUERDOS_INCUMPLIDOS", "danger",
                    n == 1 ? "1 acuerdo de pago incumplido" : n + " acuerdos de pago incumplidos",
                    "Tienen cuotas vencidas sin pagar, ya pasados los días de gracia.",
                    n, (BigDecimal) incumplidos.get("valor"), "/cartera", "Ver acuerdos"));
        }
        var cuotas = acuerdoPagoQueryRepository.resumenCuotasPorVencer(empresaId, DIAS_AVISO_VENCIMIENTO);
        int c = entero(cuotas.get("cantidad"));
        if (c > 0) {
            notificaciones.add(new NotificacionDto("CUOTAS_POR_VENCER", "warn",
                    c == 1 ? "1 cuota de acuerdo por vencer" : c + " cuotas de acuerdos por vencer",
                    "Vencen hoy o en los próximos " + DIAS_AVISO_VENCIMIENTO + " días, o están en días de gracia.",
                    c, (BigDecimal) cuotas.get("valor"), "/cartera", "Ver acuerdos"));
        }
    }

    private static int entero(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private BigDecimal sumarCosto(List<VencimientoLoteDto> lotes) {
        return lotes.stream().map(v -> v.getValorCosto() != null ? v.getValorCosto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
