package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.nomina.PagoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.CrearPrestacionDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.LotePrestacionDto;
import com.cloud_technological.aura_pos.dto.nomina.prestacion.PrestacionDto;
import com.cloud_technological.aura_pos.services.PrestacionService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/prestaciones")
public class PrestacionController {

    @Autowired
    private PrestacionService prestacionService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestaciones obtenidas", false,
                        prestacionService.listar(empresaId)), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PrestacionDto>> crear(@RequestBody CrearPrestacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestación calculada", false,
                        prestacionService.crear(dto, empresaId)), HttpStatus.OK);
    }

    @PostMapping("/generar-lote")
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> generarLote(
            @RequestBody com.cloud_technological.aura_pos.dto.nomina.prestacion.GenerarLotePrestacionDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Lote generado", false,
                        prestacionService.generarLote(dto, empresaId)), HttpStatus.OK);
    }

    @PostMapping("/liquidacion-definitiva")
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> liquidacionDefinitiva(
            @RequestBody com.cloud_technological.aura_pos.dto.nomina.prestacion.LiquidacionDefinitivaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Liquidación definitiva calculada", false,
                        prestacionService.liquidacionDefinitiva(dto, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/aprobar")
    public ResponseEntity<ApiResponse<PrestacionDto>> aprobar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestación aprobada", false,
                        prestacionService.aprobar(id, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/pagar")
    public ResponseEntity<ApiResponse<PrestacionDto>> pagar(
            @PathVariable Long id,
            @RequestBody(required = false) PagoNominaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestación pagada", false,
                        prestacionService.pagar(id, dto, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/confirmar-pago")
    public ResponseEntity<ApiResponse<PrestacionDto>> confirmarPago(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Pago confirmado", false,
                        prestacionService.confirmarPago(id, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<PrestacionDto>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Prestación anulada", false,
                        prestacionService.anular(id, empresaId)), HttpStatus.OK);
    }

    // ── Lotes (V122): el listado muestra un lote por liquidación ────────────

    @GetMapping("/lotes")
    public ResponseEntity<ApiResponse<List<LotePrestacionDto>>> listarLotes() {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Liquidaciones obtenidas", false,
                        prestacionService.listarLotes(empresaId)), HttpStatus.OK);
    }

    @GetMapping("/lote/{lote}")
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> detalleLote(@PathVariable String lote) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Detalle de la liquidación", false,
                        prestacionService.detalleLote(lote, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/lote/{lote}/aprobar")
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> aprobarLote(@PathVariable String lote) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Liquidación aprobada", false,
                        prestacionService.aprobarLote(lote, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/lote/{lote}/pagar")
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> pagarLote(
            @PathVariable String lote,
            @RequestBody(required = false) PagoNominaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Liquidación pagada", false,
                        prestacionService.pagarLote(lote, dto, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/lote/{lote}/confirmar-pago")
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> confirmarPagoLote(@PathVariable String lote) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Pagos confirmados", false,
                        prestacionService.confirmarPagoLote(lote, empresaId)), HttpStatus.OK);
    }

    @PutMapping("/lote/{lote}/anular")
    public ResponseEntity<ApiResponse<List<PrestacionDto>>> anularLote(@PathVariable String lote) {
        Integer empresaId = securityUtils.getEmpresaId();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Liquidación anulada", false,
                        prestacionService.anularLote(lote, empresaId)), HttpStatus.OK);
    }
}
