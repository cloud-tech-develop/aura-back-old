package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.factura_venta.FacturaVentaDtos;
import com.cloud_technological.aura_pos.services.implementations.FacturaVentaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Ventas › Facturas: factura de venta fuera del POS (docs/PLAN_FACTURACION.md). */
@RestController
@RequestMapping("/api/facturas-venta")
public class FacturaVentaController {

    @Autowired
    private FacturaVentaService service;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private com.cloud_technological.aura_pos.services.implementations.VentaFacturaService ventaFacturaService;

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<FacturaVentaDtos.Fila>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        return ok("Listado", service.listar(pageable, securityUtils.getEmpresaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.Detalle>> obtener(@PathVariable Long id) {
        return ok("Factura", service.obtener(id, securityUtils.getEmpresaId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FacturaVentaDtos.Detalle>> crear(@RequestBody FacturaVentaDtos.Guardar dto) {
        return ok("Borrador guardado",
                service.crear(dto, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.Detalle>> actualizar(@PathVariable Long id,
            @RequestBody FacturaVentaDtos.Guardar dto) {
        return ok("Borrador guardado", service.actualizar(id, dto, securityUtils.getEmpresaId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> eliminar(@PathVariable Long id) {
        service.eliminar(id, securityUtils.getEmpresaId());
        return ok("Borrador eliminado", true);
    }

    @PostMapping("/{id}/emitir")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.Detalle>> emitir(@PathVariable Long id,
            @RequestBody(required = false) FacturaVentaDtos.Emitir opciones) {
        FacturaVentaDtos.Detalle d = service.emitir(id, securityUtils.getEmpresaId(), securityUtils.getUsuarioId(),
                opciones);
        return ok("Factura " + d.getNumero() + " emitida", d);
    }

    @PostMapping("/desde-cotizacion/{cotizacionId}")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.Detalle>> desdeCotizacion(@PathVariable Long cotizacionId,
            @RequestParam Integer sucursalId) {
        return ok("Borrador creado desde la cotización", service.desdeCotizacion(cotizacionId, sucursalId,
                securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PostMapping("/desde-pedido/{pedidoId}")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.Detalle>> desdePedido(@PathVariable Long pedidoId) {
        return ok("Borrador creado desde el pedido",
                service.desdePedido(pedidoId, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    /** Anticipos activos del cliente, para cruzarlos al emitir a crédito. */
    @GetMapping("/anticipos-cliente/{clienteId}")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> anticiposCliente(@PathVariable Long clienteId) {
        return ok("OK", service.anticiposCliente(clienteId, securityUtils.getEmpresaId()));
    }

    @PostMapping("/{id}/copiar")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.Detalle>> copiar(@PathVariable Long id) {
        return ok("Copia creada como borrador",
                service.copiar(id, securityUtils.getEmpresaId(), securityUtils.getUsuarioId()));
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.Detalle>> anular(@PathVariable Long id) {
        FacturaVentaDtos.Detalle d = service.anular(id, securityUtils.getEmpresaId());
        return ok("Factura " + d.getNumero() + " anulada", d);
    }

    /**
     * Factura electrónica de la factura emitida. Mismo flujo que el de la venta
     * (/api/ventas/{id}/factura-electronica), pero con el permiso de Facturación:
     * quien solo tiene Ventas › Facturas también la puede enviar.
     */
    @PostMapping("/{id}/factura-electronica")
    public ResponseEntity<ApiResponse<Object>> facturaElectronica(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        FacturaVentaDtos.Detalle d = service.obtener(id, empresaId);
        if (d.getVentaId() == null || !"EMITIDA".equals(d.getEstado()))
            throw new com.cloud_technological.aura_pos.utils.GlobalException(HttpStatus.BAD_REQUEST,
                    "Emita la factura antes de enviarla a la DIAN");
        return ok("Factura electrónica enviada", ventaFacturaService.generarFacturaElectronica(d.getVentaId(), empresaId));
    }

    // ── Condiciones de pago ─────────────────────────────────────────────

    @GetMapping("/condiciones-pago")
    public ResponseEntity<ApiResponse<List<FacturaVentaDtos.CondicionPago>>> condiciones(
            @RequestParam(defaultValue = "true") boolean soloActivas) {
        return ok("OK", service.condiciones(securityUtils.getEmpresaId(), soloActivas));
    }

    @PostMapping("/condiciones-pago")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.CondicionPago>> crearCondicion(
            @RequestBody FacturaVentaDtos.CondicionPago dto) {
        return ok("Condición guardada", service.guardarCondicion(null, dto, securityUtils.getEmpresaId()));
    }

    @PutMapping("/condiciones-pago/{id}")
    public ResponseEntity<ApiResponse<FacturaVentaDtos.CondicionPago>> actualizarCondicion(@PathVariable Long id,
            @RequestBody FacturaVentaDtos.CondicionPago dto) {
        return ok("Condición guardada", service.guardarCondicion(id, dto, securityUtils.getEmpresaId()));
    }
}
