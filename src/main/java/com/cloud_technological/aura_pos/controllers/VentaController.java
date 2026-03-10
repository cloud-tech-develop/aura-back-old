package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.factus.FacturaElectronicaResponseDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaDto;
import com.cloud_technological.aura_pos.dto.ventas.VentaTableDto;
import com.cloud_technological.aura_pos.services.VentaService;
import com.cloud_technological.aura_pos.services.implementations.FacturaPdfService;
import com.cloud_technological.aura_pos.services.implementations.VentaFacturaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {
    @Autowired
    private VentaService ventaService;

    @Autowired
    private SecurityUtils securityUtils;

    @Autowired
    private VentaFacturaService ventaFacturaService;

    @Autowired
    private FacturaPdfService facturaPdfService;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<VentaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<VentaTableDto> result = ventaService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VentaDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        VentaDto result = ventaService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Venta encontrada", false, result), HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<VentaDto>> crear(@Valid @RequestBody CreateVentaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        VentaDto result = ventaService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Venta registrada exitosamente", false, result), HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/anular")
    public ResponseEntity<ApiResponse<Boolean>> anular(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ventaService.anular(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Venta anulada correctamente", false, true), HttpStatus.OK);
    }
    /**
     * POST /api/ventas/{ventaId}/factura-electronica
     *
     * Sin body — el backend toma los datos del cliente
     * desde la venta ya registrada.
     *
     * Valida internamente:
     *  ✓ empresa.facturaElectronica = true
     *  ✓ venta.cliente != null
     *  ✓ venta.estadoDian != "EMITIDA"
     */
    @PostMapping("/{ventaId}/factura-electronica")
    public ResponseEntity<ApiResponse<FacturaElectronicaResponseDto>> generarFacturaElectronica(
            @PathVariable Long ventaId) {

        Integer empresaId = securityUtils.getEmpresaId();

        FacturaElectronicaResponseDto result =
                ventaFacturaService.generarFacturaElectronica(ventaId, empresaId);

        return new ResponseEntity<>(
                new ApiResponse<>(
                        HttpStatus.OK.value(),
                        "Factura electrónica generada exitosamente",
                        false,
                        result),
                HttpStatus.OK);
    }
        /**
     * GET /api/ventas/{ventaId}/factura-pdf
     * Descarga el PDF de Factus y lo retorna como blob al frontend.
     * El frontend crea un objectURL y lo muestra en un iframe local
     * (evita el X-Frame-Options de Factus).
     */
    @GetMapping("/{ventaId}/factura-pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long ventaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        byte[] pdf = facturaPdfService.obtenerPdf(ventaId, empresaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
            ContentDisposition.inline().filename("factura-" + ventaId + ".pdf").build()
        );

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
