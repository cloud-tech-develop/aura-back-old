package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

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
import com.cloud_technological.aura_pos.services.implementations.CuentaPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;

import com.cloud_technological.aura_pos.dto.cuentas_pagar.AbonoPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CuentaPagarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_pagar.CreateCuentaPagarDto;
import com.cloud_technological.aura_pos.services.CuentaPagarService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/cuentas-pagar")
public class CuentasPagarController {

    @Autowired
    private CuentaPagarService cuentaPagarService;

    @Autowired
    private CuentaPdfService cuentaPdfService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<CuentaPagarTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        
        // Extraer filtros - puede venir en params o como campos directos del pageable
        String fechaDesde = null;
        String fechaHasta = null;
        Long proveedorId = null;
        String estado = null;
        
        // Primero buscar en params
        if (pageable.getParams() != null) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> params = (java.util.Map<String, Object>) pageable.getParams();
            fechaDesde = (String) params.get("fechaDesde");
            fechaHasta = (String) params.get("fechaHasta");
            Object proveedorIdObj = params.get("proveedorId");
            if (proveedorIdObj != null) {
                proveedorId = proveedorIdObj instanceof Number ? ((Number) proveedorIdObj).longValue() : Long.parseLong(proveedorIdObj.toString());
            }
            estado = (String) params.get("estado");
        }
        
        // Si no están en params, buscar como campos directos usando reflexión
        if (estado == null) {
            try {
                var paramsField = pageable.getClass().getDeclaredField("params");
                paramsField.setAccessible(true);
                Object paramsValue = paramsField.get(pageable);
                if (paramsValue instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> mapParams = (java.util.Map<String, Object>) paramsValue;
                    if (mapParams.containsKey("estado")) {
                        estado = mapParams.get("estado") != null ? mapParams.get("estado").toString() : null;
                    }
                }
            } catch (Exception e) {
                // Ignorar errores de reflexión
            }
        }
        
        PageImpl<CuentaPagarTableDto> result;
        
        if (fechaDesde != null || fechaHasta != null || proveedorId != null || estado != null) {
            result = cuentaPagarService.listarConFiltros(pageable, empresaId, fechaDesde, fechaHasta, proveedorId, estado);
        } else {
            result = cuentaPagarService.listar(pageable, empresaId);
        }
        
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CuentaPagarDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        CuentaPagarDto dto = cuentaPagarService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Cuenta por pagar encontrada", false, dto), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CuentaPagarDto>> crear(@Valid @RequestBody CreateCuentaPagarDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CuentaPagarDto created = cuentaPagarService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Cuenta por pagar creada", false, created), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CuentaPagarDto>> actualizar(@PathVariable Long id, @RequestBody CreateCuentaPagarDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CuentaPagarDto updated = cuentaPagarService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Cuenta por pagar actualizada", false, updated), HttpStatus.OK);
    }

    // Endpoints de Abonos
    @PostMapping("/{id}/abonos")
    public ResponseEntity<ApiResponse<AbonoPagarDto>> registrarAbono(@PathVariable Long id, @Valid @RequestBody AbonoPagarDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        AbonoPagarDto created = cuentaPagarService.registrarAbono(id, dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Abono registrado", false, created), HttpStatus.CREATED);
    }

    @GetMapping("/{id}/abonos")
    public ResponseEntity<ApiResponse<List<AbonoPagarDto>>> listarAbonos(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<AbonoPagarDto> abonos = cuentaPagarService.listarAbonos(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado de abonos", false, abonos), HttpStatus.OK);
    }

    @DeleteMapping("/{cuentaId}/abonos/{abonoId}")
    public ResponseEntity<ApiResponse<Void>> eliminarAbono(@PathVariable Long cuentaId, @PathVariable Long abonoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        cuentaPagarService.eliminarAbono(cuentaId, abonoId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Abono eliminado", false, null), HttpStatus.OK);
    }
    
    // Endpoints de Resumen
    @GetMapping("/resumen")
    public ResponseEntity<ApiResponse<CuentaPagarResumenDto>> obtenerResumen(
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) String estado) {
        Integer empresaId = securityUtils.getEmpresaId();
        CuentaPagarResumenDto resumen = cuentaPagarService.obtenerResumen(empresaId, fechaDesde, fechaHasta, proveedorId, estado);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Resumen de cuentas por pagar", false, resumen), HttpStatus.OK);
    }
    
    @GetMapping("/vencidas")
    public ResponseEntity<ApiResponse<List<CuentaPagarTableDto>>> obtenerVencidas() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<CuentaPagarTableDto> vencidas = cuentaPagarService.obtenerVencidas(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Cuentas vencidas", false, vencidas), HttpStatus.OK);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        byte[] pdf = cuentaPdfService.generarFacturaCuentaPagar(id, empresaId);
        return respuestaPdf(pdf, "cuenta_pagar_" + id + ".pdf");
    }

    @GetMapping("/abonos/{abonoId}/pdf")
    public ResponseEntity<byte[]> descargarAbonoPdf(@PathVariable Long abonoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        byte[] pdf = cuentaPdfService.generarReciboCajaPagar(abonoId, empresaId);
        return respuestaPdf(pdf, "comprobante_egreso_" + abonoId + ".pdf");
    }

    private ResponseEntity<byte[]> respuestaPdf(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
